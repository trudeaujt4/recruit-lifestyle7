package com.mz.jarboot.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.mz.jarboot.api.constant.CommonConst;
import com.mz.jarboot.api.event.Subscriber;
import com.mz.jarboot.api.event.WorkspaceChangeEvent;
import com.mz.jarboot.api.pojo.GlobalSetting;
import com.mz.jarboot.api.pojo.ServiceSetting;
import com.mz.jarboot.api.service.SettingService;
import com.mz.jarboot.client.utlis.HttpMethod;
import com.mz.jarboot.common.utils.ApiStringBuilder;
import com.mz.jarboot.client.utlis.ClientConst;
import com.mz.jarboot.client.utlis.ResponseUtils;
import com.mz.jarboot.common.utils.JsonUtils;
import com.mz.jarboot.common.utils.StringUtils;
import okhttp3.FormBody;

/**
 * @author majianzheng
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class SettingClient implements SettingService {
    private final ClientProxy clientProxy;

    /**
     * 服务管理客户端构造
     * @param host 服务地址
     * @param user 用户名
     * @param password 登录密码
     */
    public SettingClient(String host, String user, String password) {
        if (null == user || null == password) {
            this.clientProxy = ClientProxy.Factory.createClientProxy(host);
        } else {
            this.clientProxy = ClientProxy.Factory.createClientProxy(host, user, password);
        }
    }

    /**
     * 获取服务配置
     *
     * @param serviceName 服务路径
     * @return 配置信息
     */
    @Override
    public ServiceSetting getServiceSetting(String serviceName) {
        ApiStringBuilder asb = new ApiStringBuilder(CommonConst.SETTING_CONTEXT, "/serverSetting");
        final String api = asb.add(CommonConst.SERVICE_NAME_PARAM, serviceName).build();
        String response = this.clientProxy.reqApi(api, StringUtils.EMPTY, HttpMethod.GET);
        JsonNode result = ResponseUtils.parseResult(response, api);
        return JsonUtils.treeToValue(result, ServiceSetting.class);
    }

    /**
     * 提交服务配置
     *
     * @param setting 配置
     */
    @Override
    public void submitServiceSetting(ServiceSetting setting) {
        final String api = CommonConst.SETTING_CONTEXT + "/serverSetting";
        String body = JsonUtils.toJsonString(setting);
        String response = this.clientProxy.reqApi(api, body, HttpMethod.POST);
        JsonNode jsonNode = JsonUtils.readAsJsonNode(response);
        ResponseUtils.checkResponse(api, jsonNode);
    }

    /**
     * 获取全局配置
     *
     * @return 配置
     */
    @Override
    public GlobalSetting getGlobalSetting() {
        final String api = CommonConst.SETTING_CONTEXT + "/globalSetting";
        String response = this.clientProxy.reqApi(api, StringUtils.EMPTY, HttpMethod.GET);
        JsonNode result = ResponseUtils.parseResult(response, api);
        return JsonUtils.treeToValue(result, GlobalSetting.class);
    }

    /**
     * 提交全局配置
     *
     * @param setting 配置
     */
    @Override
    public void submitGlobalSetting(GlobalSetting setting) {
        final String api = CommonConst.SETTING_CONTEXT + "/globalSetting";
        String body = JsonUtils.toJsonString(setting);
        String response = this.clientProxy.reqApi(api, body, HttpMethod.POST);
        JsonNode jsonNode = JsonUtils.readAsJsonNode(response);
        ResponseUtils.checkResponse(api, jsonNode);
    }

    /**
     * 获取vm options
     *
     * @param serviceName 服务路径
     * @param file 文件
     * @return vm
     */
    @Override
    public String getVmOptions(String serviceName, String file) {
        final String api = new ApiStringBuilder(CommonConst.SETTING_CONTEXT, "/vmoptions")
                .add(CommonConst.SERVICE_NAME_PARAM, serviceName)
                .add(ClientConst.FILE_PARAM, file)
                .build();
        String response = this.clientProxy.reqApi(api, StringUtils.EMPTY, HttpMethod.GET);
        JsonNode result = ResponseUtils.parseResult(response, api);
        return result.asText(StringUtils.EMPTY);
    }

    /**
     * 保存vm options
     *
     * @param serviceName  服务
     * @param file    文件
     * @param content 文件内容
     */
    @Override
    public void saveVmOptions(String serviceName, String file, String content) {
        final String api = CommonConst.SETTING_CONTEXT + "/vmoptions";
        FormBody.Builder builder = new FormBody.Builder();
        builder
                .add(CommonConst.SERVICE_NAME_PARAM, serviceName)
                .add(ClientConst.FILE_PARAM, file)
                .add(ClientConst.CONTENT_PARAM, content);
        String response = this.clientProxy.reqApi(api, HttpMethod.POST, builder.build());
        JsonNode jsonNode = JsonUtils.readAsJsonNode(response);
        ResponseUtils.checkResponse(api, jsonNode);
    }

    @Override
    public void registerSubscriber(Subscriber<WorkspaceChangeEvent> subscriber) {
        final String topic = this.clientProxy.createTopic(subscriber.subscribeType());
        this.clientProxy.registerSubscriber(topic, subscriber);
    }

    @Override
    public void deregisterSubscriber(Subscriber<WorkspaceChangeEvent> subscriber) {
        final String topic = this.clientProxy.createTopic(subscriber.subscribeType());
        this.clientProxy.deregisterSubscriber(topic, subscriber);
    }
}
