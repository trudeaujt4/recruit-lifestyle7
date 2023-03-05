package com.mz.jarboot.event;

import com.mz.jarboot.constant.NoticeLevel;

/**
 * @author majianzheng
 */
public class BroadcastMessageEvent extends AbstractMessageEvent {
    public BroadcastMessageEvent(String sid) {
        this.sid = sid;
    }

    public BroadcastMessageEvent body(String body) {
        this.body = body;
        return this;
    }

    public BroadcastMessageEvent body(String text, NoticeLevel level) {
        this.noticeBody(text, level);
        return this;
    }

    public BroadcastMessageEvent type(FrontEndNotifyEventType type) {
        this.type = type;
        return this;
    }
}
