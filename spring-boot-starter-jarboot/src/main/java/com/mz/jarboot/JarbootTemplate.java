package com.mz.jarboot;

import com.mz.jarboot.api.constant.TaskLifecycle;
import com.mz.jarboot.api.event.Subscriber;
import com.mz.jarboot.api.event.TaskLifecycleEvent;
import com.mz.jarboot.api.event.WorkspaceChangeEvent;
import com.mz.jarboot.api.pojo.GlobalSetting;
import com.mz.jarboot.api.pojo.JvmProcess;
import com.mz.jarboot.api.pojo.ServiceInstance;
import com.mz.jarboot.api.pojo.ServiceSetting;
import com.mz.jarboot.api.service.ServiceManager;
import com.mz.jarboot.api.service.SettingService;
import com.mz.jarboot.client.ClientProxy;
import com.mz.jarboot.client.ServiceManagerClient;
import com.mz.jarboot.client.SettingClient;
import com.mz.jarboot.client.command.CommandExecutorFactory;
import com.mz.jarboot.client.command.CommandExecutorService;
import com.mz.jarboot.client.command.CommandResult;
import com.mz.jarboot.client.command.NotifyCallback;
import com.mz.jarboot.common.utils.StringUtils;

import java.util.List;
import java.util.concurrent.Future;

/**
 * Jarboot客户端操作
 * @author majianzheng
 */
public class JarbootTemplate implements JarbootOperator {
    private final ServiceManager serviceManager;
    private final SettingService settingService;
    private final JarbootConfigProperties properties;
    private ClientProxy clientProxy;
    private CommandExecutorService executor;

    public JarbootTemplate(JarbootConfigProperties properties) {
        this.properties = properties;
        this.buildProxy();
        settingService = new SettingClient(this.clientProxy);
        serviceManager = new ServiceManagerClient(this.clientProxy);
    }

    public ServiceManager getServiceManager() {
        return serviceManager;
    }

    public SettingService getSettingService() {
        return settingService;
    }

    private synchronized void buildProxy() {
        if (null == clientProxy) {
            String addr = properties.getServerAddr();
            if (StringUtils.isEmpty(addr)) {
                addr = "127.0.0.1:9899";
            }
            clientProxy = ClientProxy.Factory
                    .createClientProxy(
                            addr,
                            properties.getUsername(),
                            properties.getPassword());
        }
    }

    @Override
    public String getServiceIdByName(String service) {
        return serviceManager.getService(service).getSid();
    }

    @Override
    public ServiceInstance getService(String serviceName) {
        return serviceManager.getService(serviceName);
    }

    @Override
    public List<JvmProcess> getJvmProcesses() {
        return serviceManager.getJvmProcesses();
    }

    @Override
    public Future<CommandResult> execute(String serviceId, String cmd, NotifyCallback callback) {
        return executorInstance().execute(serviceId, cmd, callback);
    }

    @Override
    public void forceCancel(String serviceId) {
        executorInstance().forceCancel(serviceId);
    }

    @Override
    public ServiceSetting getServiceSetting(String serviceName) {
        return settingService.getServiceSetting(serviceName);
    }

    @Override
    public GlobalSetting getGlobalSetting() {
        return settingService.getGlobalSetting();
    }

    @Override
    public void registerTaskLifecycleSubscriber(String serviceName, TaskLifecycle lifecycle, Subscriber<TaskLifecycleEvent> subscriber) {
        serviceManager.registerSubscriber(serviceName, lifecycle, subscriber);
    }

    @Override
    public void deregisterTaskLifecycleSubscriber(String serviceName, TaskLifecycle lifecycle, Subscriber<TaskLifecycleEvent> subscriber) {
        serviceManager.deregisterSubscriber(serviceName, lifecycle, subscriber);
    }

    @Override
    public void registerWorkspaceChangeSubscriber(Subscriber<WorkspaceChangeEvent> subscriber) {
        settingService.registerSubscriber(subscriber);
    }

    @Override
    public void deregisterWorkspaceChangeSubscriber(Subscriber<WorkspaceChangeEvent> subscriber) {
        settingService.deregisterSubscriber(subscriber);
    }

    public CommandExecutorService executorInstance() {
        CommandExecutorService local = executor;
        if (null == local) {
            synchronized (this) {
                local = executor;
                if (null == local) {
                    executor = local = CommandExecutorFactory
                            .createCommandExecutor(this.clientProxy, StringUtils.EMPTY);
                }
            }
        }
        return local;
    }
}
