/**
 * 定义来自后端的WebSocket事件类型
 * @author majianzheng
 */
export enum MSG_EVENT {
    NOTICE,
    STD_PRINT,  //控印制台打字符串（不换行）
    BACKSPACE,  //控制台退格
    SERVER_STATUS, //服务状态改变
    WORKSPACE_CHANGE, //工作空间变更
    JVM_PROCESS_CHANGE, //java进程变化事件
    GLOBAL_LOADING, //全局Loading提示
}
