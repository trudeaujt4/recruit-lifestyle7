package com.mz.jarboot.event;

import com.mz.jarboot.api.constant.CommonConst;
import com.mz.jarboot.api.event.JarbootEvent;
import com.mz.jarboot.common.notify.FrontEndNotifyEventType;
import com.mz.jarboot.common.protocol.NotifyType;
import com.mz.jarboot.common.utils.StringUtils;

/**
 * 前端消息推送事件
 * @author majianzheng
 */
public abstract class AbstractMessageEvent implements JarbootEvent {
    protected String sid = StringUtils.EMPTY;
    protected FrontEndNotifyEventType type;
    protected String body = StringUtils.EMPTY;

    /**
     * 前端交互协议封装
     * @return 封装后内容
     */
    public String message() {
        //使用\r作为分隔符
        return new StringBuilder()
                .append(sid)
                .append(StringUtils.CR)
                .append(type.ordinal())
                .append(StringUtils.CR)
                .append(body)
                .toString();
    }

    /**
     * 创建Notice消息体
     * @param text 消息内容
     * @param level 消息级别
     */
    protected void noticeBody(String text, NotifyType level) {
        if (StringUtils.isEmpty(text) || null == level) {
            return;
        }
        type = FrontEndNotifyEventType.NOTIFY;
        //协议格式：level(0, 1, 2) + 逗号, + 消息内容
        body = level.ordinal() + CommonConst.COMMA_SPLIT + text;
    }
}
