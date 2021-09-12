package com.mz.jarboot.config;

import com.mz.jarboot.auth.annotation.Permission;
import com.mz.jarboot.base.PermissionsCache;
import com.mz.jarboot.event.NoticeEnum;
import com.mz.jarboot.ws.WebSocketManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

@Configuration
public class JarBootConfig {
    @Autowired
    private ApplicationContext ctx;
    @Autowired
    private PermissionsCache permissionsCache;

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(1024*8);
        container.setMaxTextMessageBufferSize(1024*8);
        return container;
    }
    @Bean("taskExecutor")
    public ExecutorService createExecutorService() {
        BlockingQueue<Runnable> taskBlockingQueue = new ArrayBlockingQueue<>(128);
        return new ThreadPoolExecutor(8, 32,
                32L, TimeUnit.SECONDS, taskBlockingQueue, (Runnable r, ThreadPoolExecutor executor) ->
            //线程池忙碌拒绝策略
            WebSocketManager.getInstance().notice("服务器忙碌中，请稍后再试！", NoticeEnum.WARN));
    }

    @PostConstruct
    public void init() {
        Map<String, Object> controllers = ctx.getBeansWithAnnotation(Permission.class);
        Set<Class<?>> classes = new HashSet<>();
        controllers.forEach((k, v) -> classes.add(v.getClass()));
        permissionsCache.initClassMethod(classes);
    }
}
