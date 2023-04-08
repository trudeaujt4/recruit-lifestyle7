package com.mz.jarboot;

import com.mz.jarboot.api.constant.TaskLifecycle;
import com.mz.jarboot.api.event.Subscriber;
import com.mz.jarboot.api.event.TaskLifecycleEvent;
import com.mz.jarboot.api.event.WorkspaceChangeEvent;
import com.mz.jarboot.api.pojo.GlobalSetting;
import com.mz.jarboot.api.pojo.JvmProcess;
import com.mz.jarboot.api.pojo.ServiceInstance;
import com.mz.jarboot.api.pojo.ServiceSetting;
import com.mz.jarboot.client.command.CommandResult;
import com.mz.jarboot.client.command.NotifyCallback;

import java.util.List;
import java.util.concurrent.Future;

/**
 * @author majianzheng
 */
public interface JarbootOperator {
    /**
     * 根据服务名获取service id
     * @param service 服务名
     * @return service id
     */
    String getServiceIdByName(String service);

    /**
     * 获取服务信息
     * @param serviceName 服务名称
     * @return 服务信息 {@link ServiceInstance}
     */
    ServiceInstance getService(String serviceName);

    /**
     * 获取未被服务管理的JVM进程信息
     * @return jvm进程信息
     */
    List<JvmProcess> getJvmProcesses();

    /**
     * 执行命令
     * @param serviceId service id
     * @param cmd command
     * @param callback callback
     * @return command result future
     */
    Future<CommandResult> execute(String serviceId, String cmd, NotifyCallback callback);

    /**
     * 获取服务配置
     * @param serviceName 服务路径
     * @return 配置信息
     */
    ServiceSetting getServiceSetting(String serviceName);

    /**
     * 获取全局配置
     * @return 配置
     */
    GlobalSetting getGlobalSetting();

    /**
     * 注册事件处理
     * @param serviceName 服务名称
     * @param lifecycle 任务生命周期 {@link TaskLifecycle}
     * @param subscriber 任务处理 {@link Subscriber}
     */
    void registerTaskLifecycleSubscriber(String serviceName,
                            TaskLifecycle lifecycle,
                            Subscriber<TaskLifecycleEvent> subscriber);

    /**
     * 反注册事件处理
     * @param serviceName 服务名称
     * @param lifecycle 任务生命周期 {@link TaskLifecycle}
     * @param subscriber 任务处理 {@link Subscriber}
     */
    void deregisterTaskLifecycleSubscriber(String serviceName,
                              TaskLifecycle lifecycle,
                              Subscriber<TaskLifecycleEvent> subscriber);

    /**
     * 注册工作空间修改事件处理
     * @param subscriber 任务处理 {@link Subscriber}
     */
    void registerWorkspaceChangeSubscriber(Subscriber<WorkspaceChangeEvent> subscriber);

    /**
     * 反注册工作空间修改事件处理
     * @param subscriber 任务处理 {@link Subscriber}
     */
    void deregisterWorkspaceChangeSubscriber(Subscriber<WorkspaceChangeEvent> subscriber);
}
