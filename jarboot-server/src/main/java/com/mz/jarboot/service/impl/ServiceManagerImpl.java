package com.mz.jarboot.service.impl;

import com.mz.jarboot.api.constant.CommonConst;
import com.mz.jarboot.api.constant.TaskLifecycle;
import com.mz.jarboot.api.event.JarbootEvent;
import com.mz.jarboot.api.event.Subscriber;
import com.mz.jarboot.api.event.TaskLifecycleEvent;
import com.mz.jarboot.api.exception.JarbootRunException;
import com.mz.jarboot.api.pojo.JvmProcess;
import com.mz.jarboot.api.pojo.ServiceInstance;
import com.mz.jarboot.api.pojo.ServiceSetting;
import com.mz.jarboot.base.AgentManager;
import com.mz.jarboot.common.JarbootException;
import com.mz.jarboot.common.notify.AbstractEventRegistry;
import com.mz.jarboot.common.notify.NotifyReactor;
import com.mz.jarboot.common.utils.StringUtils;
import com.mz.jarboot.common.utils.VMUtils;
import com.mz.jarboot.constant.NoticeLevel;
import com.mz.jarboot.event.*;
import com.mz.jarboot.task.AttachStatus;
import com.mz.jarboot.task.TaskRunCache;
import com.mz.jarboot.api.service.ServiceManager;
import com.mz.jarboot.utils.*;
import com.mz.jarboot.ws.WebSocketManager;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * 服务管理
 * @author majianzheng
 */
@Service
public class ServiceManagerImpl implements ServiceManager, Subscriber<ServiceOfflineEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String STARTED_MSG = "\033[96;1m%s\033[0m started cost \033[91;1m%.3f\033[0m second.\033[5m✨\033[0m";
    private static final String STOPPED_MSG = "\033[96;1m%s\033[0m stopped cost \033[91;1m%.3f\033[0m second.";

    @Value("${jarboot.after-server-error-offline:}")
    private String afterServerErrorOffline;

    @Autowired
    private TaskRunCache taskRunCache;
    @Autowired
    private AbstractEventRegistry eventRegistry;

    @Override
    public List<ServiceInstance> getServiceList() {
        return taskRunCache.getServiceList();
    }

    /**
     * 获取服务信息
     *
     * @param serviceName 服务名称
     * @return 服务信息 {@link ServiceInstance}
     */
    @Override
    public ServiceInstance getService(String serviceName) {
        return taskRunCache.getService(FileUtils.getFile(SettingUtils.getWorkspace(), serviceName));
    }

    /**
     * 一键重启，杀死所有服务进程，根据依赖重启
     */
    @Override
    public void oneClickRestart() {
        if (this.taskRunCache.hasStartingOrStopping()) {
            // 有任务在中间态，不允许执行
            WebSocketManager.getInstance().notice("存在未完成的任务，请稍后重启", NoticeLevel.INFO);
            return;
        }
        //获取所有的服务
        List<String> paths = taskRunCache.getServicePathList();
        //同步控制，保证所有的都杀死后再重启
        if (!CollectionUtils.isEmpty(paths)) {
            //启动服务
            this.restartService(paths);
        }
    }

    /**
     * 一键启动，根据依赖重启
     */
    @Override
    public void oneClickStart() {
        if (this.taskRunCache.hasStartingOrStopping()) {
            // 有任务在中间态，不允许执行
            WebSocketManager.getInstance().notice("存在未完成的任务，请稍后启动", NoticeLevel.INFO);
            return;
        }
        List<String> paths = taskRunCache.getServicePathList();
        //启动服务
        this.startService(paths);
    }

    /**
     * 一键停止，杀死所有服务进程
     */
    @Override
    public void oneClickStop() {
        if (this.taskRunCache.hasStartingOrStopping()) {
            // 有任务在中间态，不允许执行
            WebSocketManager.getInstance().notice("存在未完成的任务，请稍后停止", NoticeLevel.INFO);
            return;
        }
        List<String> paths = taskRunCache.getServicePathList();
        //启动服务
        this.stopService(paths);
    }

    /**
     * 启动服务
     *
     * @param serviceNames 服务列表，字符串格式：服务path
     */
    @Override
    public void startService(List<String> serviceNames) {
        if (CollectionUtils.isEmpty(serviceNames)) {
            return;
        }

        //在线程池中执行，防止前端请求阻塞超时
        TaskUtils.getTaskExecutor().execute(() -> this.startServer0(serviceNames));
    }

    private void startServer0(List<String> services) {
        //获取服务的优先级启动顺序
        final Queue<ServiceSetting> priorityQueue = PropertyFileUtils.parseStartPriority(services);
        ArrayList<ServiceSetting> taskList = new ArrayList<>();
        ServiceSetting setting;
        while (null != (setting = priorityQueue.poll())) {
            taskList.add(setting);
            ServiceSetting next = priorityQueue.peek();
            if (null != next && !next.getPriority().equals(setting.getPriority())) {
                //同一级别的全部取出
                startServerGroup(taskList);
                //开始指定下一级的启动组，此时上一级的已经全部启动完成，清空组
                taskList.clear();
            }
        }
        //最后一组的启动
        startServerGroup(taskList);
    }

    /**
     * 同一级别的一起启动
     * @param s 同级服务列表
     */
    private void startServerGroup(List<ServiceSetting> s) {
        if (CollectionUtils.isEmpty(s)) {
            return;
        }
        CountDownLatch countDownLatch = new CountDownLatch(s.size());
        s.forEach(setting ->
                TaskUtils.getTaskExecutor().execute(() -> {
                    try {
                        this.startSingleService(setting);
                    } finally {
                        countDownLatch.countDown();
                    }
                }));

        try {
            //等待全部启动完成
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 根据服务配置，启动单个服务
     * @param setting 服务配置
     */
    @Override
    public void startSingleService(ServiceSetting setting) {
        String server = setting.getName();
        String sid = setting.getSid();
        // 已经处于启动中或停止中时不允许执行开始，但是开始中时应当可以执行停止，用于异常情况下强制停止
        if (this.taskRunCache.isStopping(sid)) {
            WebSocketManager.getInstance().notice("服务" + server + "正在停止", NoticeLevel.INFO);
            return;
        }
        if (AgentManager.getInstance().isOnline(sid)) {
            //已经启动
            WebSocketManager.getInstance().upgradeStatus(sid, CommonConst.RUNNING);
            WebSocketManager.getInstance().notice("服务" + server + "已经是启动状态", NoticeLevel.INFO);
            return;
        }
        if (!this.taskRunCache.addStarting(sid)) {
            WebSocketManager.getInstance().notice("服务" + server + "正在启动中", NoticeLevel.INFO);
            return;
        }
        try {
            //设定启动中，并发送前端让其转圈圈
            NotifyReactor
                    .getInstance()
                    .publishEvent(new TaskLifecycleEvent(setting, TaskLifecycle.PRE_START));
            //记录开始时间
            long startTime = System.currentTimeMillis();
            //开始启动进程
            TaskUtils.startService(server, setting);
            //记录启动结束时间，减去判定时间修正

            double costTime = (System.currentTimeMillis() - startTime)/1000.0f;
            //服务是否启动成功
            if (AgentManager.getInstance().isOnline(sid)) {
                WebSocketManager
                        .getInstance()
                        .sendConsole(sid, String.format(STARTED_MSG, server, costTime));
                NotifyReactor
                        .getInstance()
                        .publishEvent(new TaskLifecycleEvent(setting, TaskLifecycle.AFTER_STARTED));
            } else {
                //启动失败
                NotifyReactor
                        .getInstance()
                        .publishEvent(new TaskLifecycleEvent(setting, TaskLifecycle.START_FAILED));
                WebSocketManager.getInstance().notice("启动服务" + server + "失败！", NoticeLevel.ERROR);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            WebSocketManager.getInstance().notice(e.getMessage(), NoticeLevel.ERROR);
            WebSocketManager.getInstance().printException(sid, e);
        } finally {
            this.taskRunCache.removeStarting(sid);
        }
    }

    /**
     * 停止服务
     *
     * @param serviceNames 服务列表，字符串格式：服务path
     */
    @Override
    public void stopService(List<String> serviceNames) {
        if (CollectionUtils.isEmpty(serviceNames)) {
            return;
        }

        //在线程池中执行，防止前端请求阻塞超时
        TaskUtils.getTaskExecutor().execute(() -> this.stopServer0(serviceNames));
    }

    @Override
    public List<JvmProcess> getJvmProcesses() {
        ArrayList<JvmProcess> result = new ArrayList<>();
        Map<String, String> vms = VMUtils.getInstance().listVM();
        vms.forEach((pid, v) -> {
            if (AgentManager.getInstance().isLocalService(pid)) {
                return;
            }
            JvmProcess process = new JvmProcess();
            process.setSid(pid);
            process.setPid(pid);
            process.setAttached(AgentManager.getInstance().isOnline(pid));
            process.setFullName(v);
            //解析获取简略名字
            process.setName(TaskUtils.parseCommandSimple(v));
            result.add(process);
        });
        AgentManager.getInstance().remoteProcess(result);
        return result;
    }

    @Override
    public void attach(String pid) {
        if (StringUtils.isEmpty(pid)) {
            throw new JarbootException("pid is empty!");
        }
        Object vm = null;
        WebSocketManager.getInstance().debugProcessEvent(pid, AttachStatus.ATTACHING);
        try {
            vm = VMUtils.getInstance().attachVM(pid);
            String args = SettingUtils.getAttachArgs();
            VMUtils.getInstance().loadAgentToVM(vm, SettingUtils.getAgentJar(), args);
        } catch (Exception e) {
            WebSocketManager.getInstance().printException(pid, e);
        } finally {
            if (null != vm) {
                VMUtils.getInstance().detachVM(vm);
            }
        }
    }

    @Override
    public void deleteService(String serviceName) {
        String path = SettingUtils.getServicePath(serviceName);
        String sid = SettingUtils.createSid(path);
        if (this.taskRunCache.isStartingOrStopping(sid)) {
            throw new JarbootRunException(serviceName + "在停止中或启动中，不可删除！");
        }
        if (AgentManager.getInstance().isOnline(sid)) {
            throw new JarbootRunException(serviceName + "正在运行，不可删除！");
        }
        WebSocketManager.getInstance().globalLoading(serviceName, serviceName + "删除中...");
        TaskUtils.getTaskExecutor().execute(() -> {
            try {
                FileUtils.deleteDirectory(FileUtils.getFile(path));
                WebSocketManager
                        .getInstance()
                        .createGlobalEvent(StringUtils.SPACE, StringUtils.EMPTY, FrontEndNotifyEventType.WORKSPACE_CHANGE);
                WebSocketManager.getInstance().notice("删除" + serviceName + "成功！", NoticeLevel.INFO);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                WebSocketManager
                        .getInstance()
                        .notice("删除" + serviceName + "失败！" + e.getMessage(), NoticeLevel.ERROR);
            } finally {
                WebSocketManager.getInstance().globalLoading(serviceName, StringUtils.EMPTY);
            }
        });
    }

    /**
     * 注册事件处理
     *
     * @param serviceName 服务名称
     * @param lifecycle   任务生命周期 {@link TaskLifecycle}
     * @param subscriber  任务处理 {@link Subscriber}
     */
    @Override
    public void registerSubscriber(String serviceName,
                                   TaskLifecycle lifecycle,
                                   Subscriber<TaskLifecycleEvent> subscriber) {
        final String topic = eventRegistry.createLifecycleTopic(serviceName, lifecycle);
        eventRegistry.registerSubscriber(topic, subscriber);
    }

    /**
     * 反注册事件处理
     *
     * @param serviceName 服务名称
     * @param lifecycle   任务生命周期 {@link TaskLifecycle}
     * @param subscriber  任务处理 {@link Subscriber}
     */
    @Override
    public void deregisterSubscriber(String serviceName,
                                     TaskLifecycle lifecycle,
                                     Subscriber<TaskLifecycleEvent> subscriber) {
        final String topic = eventRegistry.createLifecycleTopic(serviceName, lifecycle);
        eventRegistry.deregisterSubscriber(topic, subscriber);
    }

    private void stopServer0(List<String> paths) {
        //获取服务的优先级顺序，与启动相反的顺序依次终止
        final Queue<ServiceSetting> priorityQueue = PropertyFileUtils.parseStopPriority(paths);
        ArrayList<ServiceSetting> taskList = new ArrayList<>();
        ServiceSetting setting;
        while (null != (setting = priorityQueue.poll())) {
            taskList.add(setting);
            ServiceSetting next = priorityQueue.peek();
            if (null != next && !next.getPriority().equals(setting.getPriority())) {
                //同一级别的全部取出
                stopServerGroup(taskList);
                //开始指定下一级的启动组，此时上一级的已经全部启动完成，清空组
                taskList.clear();
            }
        }
        //最后一组的启动
        stopServerGroup(taskList);
    }

    private void stopServerGroup(List<ServiceSetting> s) {
        if (CollectionUtils.isEmpty(s)) {
            return;
        }
        CountDownLatch countDownLatch = new CountDownLatch(s.size());
        s.forEach(server ->
                TaskUtils.getTaskExecutor().execute(() -> {
                    try {
                        this.stopSingleServer(server);
                    } finally {
                        countDownLatch.countDown();
                    }
                }));

        try {
            //等待全部终止完成
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void stopSingleServer(ServiceSetting setting) {
        String server = setting.getName();
        String sid = setting.getSid();
        if (!this.taskRunCache.addStopping(sid)) {
            WebSocketManager.getInstance().notice("服务" + server + "正在停止中", NoticeLevel.INFO);
            return;
        }
        try {
            //发送停止中消息
            NotifyReactor
                    .getInstance()
                    .publishEvent(new TaskLifecycleEvent(setting, TaskLifecycle.PRE_STOP));
            //记录开始时间
            long startTime = System.currentTimeMillis();
            TaskUtils.killService(sid);
            //耗时
            double costTime = (System.currentTimeMillis() - startTime)/1000.0f;
            //停止成功
            if (AgentManager.getInstance().isOnline(sid)) {
                NotifyReactor
                        .getInstance()
                        .publishEvent(new TaskLifecycleEvent(setting, TaskLifecycle.STOP_FAILED));
                WebSocketManager.getInstance().notice("停止服务" + server + "失败！", NoticeLevel.ERROR);
            } else {
                WebSocketManager.getInstance().sendConsole(sid, String.format(STOPPED_MSG, server, costTime));
                NotifyReactor
                        .getInstance()
                        .publishEvent(new TaskLifecycleEvent(setting, TaskLifecycle.AFTER_STOPPED));
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            WebSocketManager.getInstance().notice(e.getMessage(), NoticeLevel.ERROR);
            WebSocketManager.getInstance().printException(sid, e);
        } finally {
            this.taskRunCache.removeStopping(sid);
        }
    }

    /**
     * 重启服务
     *
     * @param serviceNames 服务列表，字符串格式：服务path
     */
    @Override
    public void restartService(List<String> serviceNames) {
        //获取终止的顺序
        TaskUtils.getTaskExecutor().execute(() -> {
            //先依次终止
            stopServer0(serviceNames);
            //再依次启动
            startServer0(serviceNames);
        });
    }

    @Override
    public void onEvent(ServiceOfflineEvent event) {
        String serviceName = event.getServiceName();
        String sid = event.getSid();
        //检查进程是否存活
        String pid = TaskUtils.getPid(sid);
        if (!pid.isEmpty()) {
            //检查是否处于中间状态
            if (taskRunCache.isStopping(sid)) {
                //处于停止中状态，此时不做干预，守护只针对正在运行的进程
                return;
            }
            //尝试重新初始化代理客户端
            TaskUtils.attach(sid);
            return;
        }
        //获取是否开启了守护
        ServiceSetting temp = PropertyFileUtils.getServerSettingBySid(sid);
        //检测配置更新
        final ServiceSetting setting = null == temp ? null : PropertyFileUtils.getServerSetting(temp.getName());

        TaskLifecycleEvent lifecycleEvent = null == setting ?
                new TaskLifecycleEvent(SettingUtils.getWorkspace(), sid, serviceName, TaskLifecycle.EXCEPTION_OFFLINE)
                :
                new TaskLifecycleEvent(setting, TaskLifecycle.EXCEPTION_OFFLINE);

        NotifyReactor.getInstance().publishEvent(lifecycleEvent);

        if (StringUtils.isNotEmpty(afterServerErrorOffline)) {
            String cmd = afterServerErrorOffline + StringUtils.SPACE + serviceName;
            TaskUtils.getTaskExecutor().execute(() -> TaskUtils.startTask(cmd, null, null));
        }

        final SimpleDateFormat sdf = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss] ");
        String s = sdf.format(new Date());
        if (null != setting && Boolean.TRUE.equals(setting.getDaemon())) {
            WebSocketManager.getInstance().notice(String.format("服务%s于%s异常退出，即将启动守护启动！", serviceName, s)
                    , NoticeLevel.WARN);
            //启动
            TaskUtils.getTaskExecutor().execute(() -> this.startSingleService(setting));
        } else {
            WebSocketManager.getInstance().notice(String.format("服务%s于%s异常退出，请检查服务状态！", serviceName, s)
                    , NoticeLevel.WARN);
        }
    }

    @PostConstruct
    public void init() {
        NotifyReactor.getInstance().registerSubscriber(this);
    }

    @Override
    public Class<? extends JarbootEvent> subscribeType() {
        return ServiceOfflineEvent.class;
    }
}
