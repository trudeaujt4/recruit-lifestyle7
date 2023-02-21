package com.mz.jarboot.ws;

import com.mz.jarboot.api.event.JarbootEvent;
import com.mz.jarboot.api.event.Subscriber;
import com.mz.jarboot.api.exception.JarbootRunException;
import com.mz.jarboot.common.notify.AbstractEventRegistry;
import com.mz.jarboot.common.notify.NotifyReactor;
import com.mz.jarboot.common.utils.JsonUtils;
import com.mz.jarboot.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * @author majianzheng
 */
@ServerEndpoint("/jarboot/public/event/ws")
@Component
@SuppressWarnings("all")
public class JarbootEventServer implements AbstractEventRegistry {
    private static final Logger logger = LoggerFactory.getLogger(JarbootEventServer.class);

    /** 服务端本地的订阅 */
    private static final Map<String, Set<Subscriber>> LOCAL_SUBS = new ConcurrentHashMap<>(16);
    /** 客户端接口的订阅 */
    private static final Map<String, Set<Session>> CLIENT_SUBS = new ConcurrentHashMap<>(16);

    @Override
    public void registerSubscriber(String topic, Subscriber subscriber) {
        checkTopic(topic);
        checkSubscriber(subscriber);
        LOCAL_SUBS.compute(topic, (k, v) -> {
            if (null == v) {
                v = new HashSet<>(16);
            }
            v.add(subscriber);
            return v;
        });
    }

    @Override
    public void deregisterSubscriber(String topic, Subscriber subscriber) {
        checkTopic(topic);
        checkSubscriber(subscriber);
        LOCAL_SUBS.computeIfPresent(topic, (k, v) -> {
            v.remove(subscriber);
            return v;
        });
    }

    @Override
    public void receiveEvent(String topic, JarbootEvent event) {
        Set<Subscriber> subs = LOCAL_SUBS.getOrDefault(topic, null);
        if (null != subs && !subs.isEmpty()) {
            subs.forEach(sub -> {
                Executor executor = sub.executor();
                //执行本地事件
                final Runnable job = () -> sub.onEvent(event);
                if (null == executor) {
                    job.run();
                } else {
                    executor.execute(job);
                }
            });
        }

        Set<Session> clientSubs = CLIENT_SUBS.getOrDefault(topic, null);
        if (null == clientSubs || clientSubs.isEmpty()) {
            return;
        }
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            byte[] clsBytes = event.getClass().getCanonicalName().getBytes(StandardCharsets.UTF_8);
            byte[] body = JsonUtils.toJsonBytes(event);
            stream.write(topic.getBytes(StandardCharsets.UTF_8));
            stream.write(SPLIT);
            stream.write(clsBytes);
            stream.write(SPLIT);
            stream.write(body);
            byte[] buf = stream.toByteArray();
            clientSubs.forEach(session -> NotifyReactor
                    .getInstance()
                    .publishEvent(new MessageSenderEvent(session, buf)));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息*/
    @OnMessage
    public void onBinaryMessage(byte[] message, Session session) {
        if (message.length - 1 < 0) {
            return;
        }
        String topic = new String(message, 1, message.length - 1, StandardCharsets.UTF_8);
        if (0 == message[0]) {
            CLIENT_SUBS.computeIfPresent(topic, (k, v) -> {
                v.remove(session);
                return v;
            });
        } else {
            CLIENT_SUBS.compute(topic, (k, v) -> {
                if (null == v) {
                    v = new HashSet<>(16);
                }
                v.add(session);
                return v;
            });
        }
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(Session session) {
        CLIENT_SUBS.forEach((k, v) -> {
            if (null == v) {
                return;
            }
            v.remove(session);
        });
    }

    /**
     * 连接异常
     * @param session 会话
     * @param error 错误
     */
    @OnError
    public void onError(Session session, Throwable error) {
        logger.warn(error.getMessage(), error);
        this.onClose(session);
    }

    private void checkTopic(String topic) {
        if (StringUtils.isEmpty(topic)) {
            throw new JarbootRunException("topic is empty.");
        }
    }

    private void checkSubscriber(Subscriber subscriber) {
        if (null == subscriber) {
            throw new JarbootRunException("subscriber is null.");
        }
    }
}
