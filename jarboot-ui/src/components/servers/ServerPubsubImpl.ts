import {MsgData, WsManager} from "@/common/WsManager";
import {MSG_EVENT} from "@/common/EventConst";
import {JarBootConst} from "@/common/JarBootConst";
import Logger from "@/common/Logger";

/**
 * 服务订阅发布实现
 */
const TOPIC_SPLIT = '\r';

enum PUB_TOPIC {
    CMD_END="commandEnd",
    RENDER_JSON = "renderJson",
    QUICK_EXEC_CMD = "quickExecCmd",
    RECONNECTED = "reconnected",
    WORKSPACE_CHANGE = "workspaceChange"
}

class ServerPubsubImpl implements PublishSubmit {
    private handlers = new Map<string, Set<(data: any) => void>>();

    constructor() {
        WsManager.addMessageHandler(MSG_EVENT.CONSOLE_LINE, this._console);
        WsManager.addMessageHandler(MSG_EVENT.CONSOLE_PRINT, this._print);
        WsManager.addMessageHandler(MSG_EVENT.BACKSPACE, this._backspace);
        WsManager.addMessageHandler(MSG_EVENT.BACKSPACE_LINE, this._backspaceLine);
        WsManager.addMessageHandler(MSG_EVENT.RENDER_JSON, this._renderCmdJsonResult);
        WsManager.addMessageHandler(MSG_EVENT.CMD_END, this._commandEnd);
        WsManager.addMessageHandler(MSG_EVENT.WORKSPACE_CHANGE, this._workspaceChange);
        WsManager.addMessageHandler(WsManager.RECONNECTED_EVENT, this._onReconnected);
    }

    private static genTopicKey(namespace: string, event: string) {
        return `${namespace}${TOPIC_SPLIT}${event}`;
    }

    public publish(namespace: string, event: string, data?: any): void {
        const key = ServerPubsubImpl.genTopicKey(namespace, event);
        let sets = this.handlers.get(key);
        if (sets?.size) {
            sets.forEach(handler => handler && handler(data));
        }
    }

    public submit(namespace: string, event: string, handler: (data: any) => void): void {
        const key = ServerPubsubImpl.genTopicKey(namespace, event);
        let sets = this.handlers.get(key);
        if (sets?.size) {
            sets.add(handler);
        } else {
            sets = new Set<(data: any) => void>();
            sets.add(handler);
            this.handlers.set(key, sets);
        }
    }

    public unSubmit(namespace: string, event: string, handler: (data: any) => void): void {
        const key = ServerPubsubImpl.genTopicKey(namespace, event);
        const sets = this.handlers.get(key);
        if (sets?.size) {
            sets.delete(handler);
            if (sets.size === 0) {
                this.handlers.delete(key);
            }
        }
    }

    private _console = (data: MsgData) => {
        this.publish(data.sid, JarBootConst.APPEND_LINE, data.body);
    }

    private _print = (data: MsgData) => {
        this.publish(data.sid, JarBootConst.PRINT, data.body);
    }

    private _backspace = (data: MsgData) => {
        this.publish(data.sid, JarBootConst.BACKSPACE, data.body);
    }

    private _backspaceLine = (data: MsgData) => {
        this.publish(data.sid, JarBootConst.BACKSPACE_LINE, data.body);
    }

    private _commandEnd = (data: MsgData) => {
        this.publish(data.sid, PUB_TOPIC.CMD_END, data.body);
    }

    private _workspaceChange = (data: MsgData) => {
        this.publish("", PUB_TOPIC.WORKSPACE_CHANGE, data.body);
        Logger.log(`工作空间已经被修改，服务列表将会被刷新！`);
    }

    private _onReconnected = (data: MsgData) => {
        this.publish('', PUB_TOPIC.RECONNECTED, data.body);
        Logger.log(`重新连接服务成功，服务列表将会被刷新！`);
    }

    private _renderCmdJsonResult = (data: MsgData) => {
        if ('{' !== data.body[0]) {
            //不是json数据时，使用console
            Logger.warn(`当前非JSON数据格式！`, data);
            this._console(data);
            return;
        }
        const body = JSON.parse(data.body);
        this.publish(data.sid, PUB_TOPIC.RENDER_JSON, body);
    }
}

const pubsub: PublishSubmit = new ServerPubsubImpl();

export {pubsub, PUB_TOPIC};
