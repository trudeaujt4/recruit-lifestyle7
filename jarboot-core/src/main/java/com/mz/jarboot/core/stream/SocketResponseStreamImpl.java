package com.mz.jarboot.core.stream;

import com.mz.jarboot.core.basic.SingletonCoreFactory;
import com.mz.jarboot.core.ws.WebSocketClient;

/**
 * 小数据量传输通过WebSocket
 * @author jianzhengma
 */
public class SocketResponseStreamImpl implements ResponseStream {
    private WebSocketClient client;
    public SocketResponseStreamImpl() {
        client = SingletonCoreFactory.getInstance().getSingletonClient();
    }
    @Override
    public void write(String data) {
        client.sendText(data);
    }
}
