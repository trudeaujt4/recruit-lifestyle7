package com.mz.jarboot.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint("/jarboot-agent/ws")
@RestController
public class WebSocketAgentServer {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketAgentServer.class);

    /**
     * 连接建立成功调用的方法*/
    @OnOpen
    public void onOpen(Session session) {

    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose( Session session) {

    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息*/
    @OnMessage
    public void onBinaryMessage(byte[] message, Session session) {
        // Do nothing
    }

    @OnMessage
    public void onTextMessage(String message, Session session) {
        // Do nothing
    }

    /**
     * 连接异常
     * @param session 会话
     * @param error 错误
     */
    @OnError
    public void onError(Session session, Throwable error) {

        logger.error(error.getMessage(), error);
    }
}
