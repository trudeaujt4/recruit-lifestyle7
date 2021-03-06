package com.mz.jarboot.service.impl;

import com.mz.jarboot.api.event.JarbootEvent;
import com.mz.jarboot.api.event.Subscriber;
import com.mz.jarboot.api.event.WorkspaceChangeEvent;
import com.mz.jarboot.api.service.ServiceManager;
import com.mz.jarboot.base.AgentManager;
import com.mz.jarboot.api.constant.CommonConst;
import com.mz.jarboot.common.CacheDirHelper;
import com.mz.jarboot.common.JarbootThreadFactory;
import com.mz.jarboot.common.notify.AbstractEventRegistry;
import com.mz.jarboot.common.notify.FrontEndNotifyEventType;
import com.mz.jarboot.common.notify.NotifyReactor;
import com.mz.jarboot.common.utils.StringUtils;
import com.mz.jarboot.event.*;
import com.mz.jarboot.task.TaskRunCache;
import com.mz.jarboot.api.pojo.ServiceInstance;
import com.mz.jarboot.api.pojo.ServiceSetting;
import com.mz.jarboot.service.TaskWatchService;
import com.mz.jarboot.utils.MessageUtils;
import com.mz.jarboot.utils.PropertyFileUtils;
import com.mz.jarboot.utils.SettingUtils;
import com.mz.jarboot.utils.TaskUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @author majianzheng
 */
@Component
public class TaskWatchServiceImpl implements TaskWatchService, Subscriber<ServiceFileChangeEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final int MIN_MODIFY_WAIT_TIME = 3;
    private static final int MAX_MODIFY_WAIT_TIME = 600;

    @Autowired
    private TaskRunCache taskRunCache;
    @Autowired
    private ServiceManager serverMgrService;
    @Autowired
    private AbstractEventRegistry eventRegistry;

    private final String jarbootHome = System.getProperty(CommonConst.JARBOOT_HOME);

    @Value("${jarboot.file-shake-time:5}")
    private long modifyWaitTime;
    @Value("${jarboot.after-start-exec:}")
    private String afterStartExec;
    @Value("${jarboot.services.enable-auto-start-after-start:false}")
    private boolean enableAutoStartServices;
    private String curWorkspace;
    private boolean starting = false;
    private final ThreadFactory threadFactory = JarbootThreadFactory
            .createThreadFactory("jarboot-tws", true);
    private Thread monitorThread = threadFactory.newThread(this::initPathMonitor);

    /** ??????????????????????????????????????????????????? */
    private final LinkedBlockingQueue<String> modifiedServiceQueue = new LinkedBlockingQueue<>(1024);

    @Override
    public void init() {
        if (starting) {
            return;
        }
        if (modifyWaitTime < MIN_MODIFY_WAIT_TIME || modifyWaitTime > MAX_MODIFY_WAIT_TIME) {
            modifyWaitTime = 5;
        }
        starting = true;
        curWorkspace = SettingUtils.getWorkspace();
        //?????????????????????
        this.monitorThread.start();

        //?????????????????????
        NotifyReactor.getInstance().registerSubscriber(this);

        //attach???????????????????????????
        this.attachRunningServer();

        if (enableAutoStartServices) {
            logger.info("Auto starting services...");
            serverMgrService.oneClickStart();
        }

        //??????????????????
        if (StringUtils.isNotEmpty(afterStartExec)) {
            threadFactory
                    .newThread(() -> TaskUtils.startTask(afterStartExec, null, jarbootHome))
                    .start();
        }
        //????????????????????????????????????
        NotifyReactor.getInstance().registerSubscriber(new Subscriber<WorkspaceChangeEvent>() {
            @Override
            public void onEvent(WorkspaceChangeEvent event) {
                eventRegistry.receiveEvent(eventRegistry.createTopic(WorkspaceChangeEvent.class), event);
                changeWorkspace(event.getWorkspace());
            }

            @Override
            public Class<? extends JarbootEvent> subscribeType() {
                return WorkspaceChangeEvent.class;
            }
        });
    }

    /**
     * ??????????????????
     *
     * @param workspace ????????????
     */
    private void changeWorkspace(String workspace) {
        MessageUtils.globalEvent(FrontEndNotifyEventType.WORKSPACE_CHANGE);
        //?????????????????????
        monitorThread.interrupt();
        try {
            //?????????????????????????????????
            monitorThread.join(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (monitorThread.isAlive()) {
            logger.error("???????????????????????????????????????");
            MessageUtils.error("????????????????????????????????????????????????Jarboot?????????");
            return;
        }
        this.curWorkspace = workspace;
        this.monitorThread = threadFactory.newThread(this::initPathMonitor);
        this.monitorThread.start();
    }

    @Override
    public void onEvent(ServiceFileChangeEvent event) {
        //?????????
        HashSet<String> services = new HashSet<>();
        try {
            String serviceName;
            //???????????????????????????????????????????????????????????????????????????????????????
            while (null != (serviceName = modifiedServiceQueue.poll(modifyWaitTime, TimeUnit.SECONDS))) {
                services.add(serviceName);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        //?????????jar?????????????????????????????????jar?????????????????????????????????
        List<String> list = services.stream().filter(this::checkJarUpdate).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(list)) {
            final String msg = "????????????????????????????????????????????????????????????...";
            MessageUtils.info(msg);
            serverMgrService.restartService(list);
        }
    }

    @Override
    public Executor executor() {
        return TaskUtils.getTaskExecutor();
    }

    @Override
    public Class<? extends JarbootEvent> subscribeType() {
        return ServiceFileChangeEvent.class;
    }

    private void attachRunningServer() {
        List<ServiceInstance> runningServers = taskRunCache.getServiceList();
        if (CollectionUtils.isEmpty(runningServers)) {
            return;
        }
        runningServers.forEach(this::doAttachRunningServer);
    }

    /**
     * ????????????????????????
     */
    private void initPathMonitor() {
        //???????????????jar???????????????????????????????????????????????????
        storeCurFileModifyTime();
        //??????????????????
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            //?????????????????????
            final Path monitorPath = Paths.get(curWorkspace);
            //???path??????????????????????????????
            monitorPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            //????????????
            pathWatchMonitor(watchService);
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            MessageUtils.error("???????????????????????????" + ex.getMessage());
        } catch (InterruptedException ex) {
            //????????????
            Thread.currentThread().interrupt();
        }
    }

    /**
     * ????????????????????????
     */
    private void storeCurFileModifyTime() {
        File[] serverDirs = taskRunCache.getServiceDirs();
        if (null == serverDirs || serverDirs.length <= 0) {
            return;
        }
        File recordDir = CacheDirHelper.getMonitorRecordDir();
        if (!recordDir.exists() && !recordDir.mkdirs()) {
            logger.error("??????record?????????{}????????????", recordDir.getPath());
            return;
        }
        //???????????????record??????
        File[] recordFiles = recordDir.listFiles();
        HashMap<String, File> recordFileMap = new HashMap<>(16);
        if (null != recordFiles && recordFiles.length > 0) {
            for (File recordFile : recordFiles) {
                recordFileMap.put(recordFile.getName(), recordFile);
            }
        }
        for (File serverDir : serverDirs) {
            Collection<File> files = FileUtils
                    .listFiles(serverDir, new String[]{CommonConst.JAR_FILE_EXT}, true);
            if (!CollectionUtils.isEmpty(files)) {
                File recordFile = getRecordFile(serverDir.getPath());
                if (null != recordFile) {
                    recordFileMap.remove(recordFile.getName());
                    Properties properties = new Properties();
                    files.forEach(jarFile -> properties.put(genFileHashKey(jarFile),
                            String.valueOf(jarFile.lastModified())));
                    PropertyFileUtils.storeProperties(recordFile, properties);
                }
            }
        }
        if (!recordFileMap.isEmpty()) {
            recordFileMap.forEach((k, v) -> FileUtils.deleteQuietly(v));
        }
    }

    private File getRecordFile(String serverPath) {
        File recordFile = CacheDirHelper.getMonitorRecordFile(SettingUtils.createSid(serverPath));
        if (!recordFile.exists()) {
            try {
                if (!recordFile.createNewFile()) {
                    logger.warn("createNewFile({}) failed.", recordFile.getPath());
                    return null;
                }
            } catch (IOException e) {
                logger.warn(e.getMessage(), e);
                return null;
            }
        }
        return recordFile;
    }

    private void pathWatchMonitor(WatchService watchService) throws InterruptedException {
        for (;;) {
            final WatchKey key = watchService.take();
            for (WatchEvent<?> watchEvent : key.pollEvents()) {
                handlePathEvent(watchEvent);
            }
            if (!key.reset()) {
                logger.error("???????????????????????????");
                MessageUtils.error("????????????????????????????????????Jarboot?????????");
                break;
            }
        }
    }

    private void handlePathEvent(WatchEvent<?> watchEvent) throws InterruptedException {
        final WatchEvent.Kind<?> kind = watchEvent.kind();
        if (kind == StandardWatchEventKinds.OVERFLOW) {
            return;
        }
        String service = watchEvent.context().toString();
        //???????????????????????????
        if (kind == StandardWatchEventKinds.ENTRY_CREATE ||
                kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            final String path = curWorkspace + File.separator + service;
            String sid= SettingUtils.createSid(path);
            //?????????????????????
            if (!AgentManager.getInstance().isOnline(sid)) {
                //????????????????????????????????????
                return;
            }
            ServiceSetting setting = PropertyFileUtils.getServiceSettingByPath(path);
            if (Boolean.TRUE.equals(setting.getJarUpdateWatch()) && Objects.equals(sid, setting.getSid())) {
                //???????????????????????????
                modifiedServiceQueue.put(service);
                NotifyReactor.getInstance().publishEvent(new ServiceFileChangeEvent());
            }
        }
        //????????????
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            logger.info("????????????????????????????????????????????????{}", watchEvent.context());
        }
    }

    private String genFileHashKey(File jarFile) {
        String path = jarFile.getPath();
        return String.format("hash.%d", path.hashCode());
    }

    private boolean checkJarUpdate(String path) {
        File serverDir = new File(path);
        Collection<File> files = FileUtils.listFiles(serverDir, new String[]{CommonConst.JAR_FILE_EXT}, true);
        if (CollectionUtils.isEmpty(files)) {
            return false;
        }
        File recordFile = getRecordFile(path);
        if (null == recordFile) {
            return false;
        }
        Properties recordProps = PropertyFileUtils.getProperties(recordFile);
        boolean updateFlag = false;
        for (File file : files) {
            String key = genFileHashKey(file);
            String value = recordProps.getProperty(key, "-1");
            long lastModifyTime = Long.parseLong(value);
            if (lastModifyTime != file.lastModified()) {
                recordProps.setProperty(key, String.valueOf(file.lastModified()));
                updateFlag = true;
            }
        }
        if (updateFlag) {
            //??????jar???????????????????????????
            PropertyFileUtils.storeProperties(recordFile, recordProps);
        }
        return updateFlag;
    }

    private void doAttachRunningServer(ServiceInstance server) {
        if (AgentManager.getInstance().isOnline(server.getSid())) {
            //?????????????????????
            return;
        }
        TaskUtils.attach(server.getSid());
    }
}
