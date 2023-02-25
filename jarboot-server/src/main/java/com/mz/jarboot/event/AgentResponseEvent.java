package com.mz.jarboot.event;

import com.mz.jarboot.api.event.JarbootEvent;
import com.mz.jarboot.common.protocol.CommandResponse;
import javax.websocket.Session;

/**
 * @author majianzheng
 */
public class AgentResponseEvent implements JarbootEvent {
    private final String serviceName;
    private final String sid;
    private final CommandResponse response;
    private final Session session;

    public AgentResponseEvent(String serviceName, String sid, CommandResponse response, Session session) {
        this.serviceName = serviceName;
        this.sid = sid;
        this.response = response;
        this.session = session;
    }

    public String getServiceName() {
        return this.serviceName;
    }

    public String getSid() {
        return this.sid;
    }

    public CommandResponse getResponse() {
        return this.response;
    }

    public Session getSession() {
        return this.session;
    }
}
