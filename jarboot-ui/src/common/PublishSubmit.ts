/**
 * 事件订阅、发布接口
 * @author majianzheng
 */
interface PublishSubmit {
    /**
     * 订阅事件
     * @param namespace 命名空间
     * @param event 事件名
     * @param handler 事件处理
     */
    submit: (namespace: string, event: string, handler: (data: any) => void) => void;

    /**
     * 反订阅事件
     * @param namespace 命名空间
     * @param event 事件名
     * @param handler 事件处理
     */
    unSubmit: (namespace: string, event: string, handler: (data: any) => void) => void;

    /**
     * 发布事件
     * @param namespace 命名空间
     * @param event 事件名
     * @param data 事件参数
     */
    publish: (namespace: string, event: string, data?: any) => void;
}
