package model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 鉴权比较样本数据模型
 * 封装一条完整的鉴权比较记录，支持动态数量的鉴权对象。
 * 使用 Map 存储各鉴权对象的报文数据（如 Original / Unauthorized / User1 / User2 ...）。
 */
public class CompareSampleModel {

    private int id;
    private String method;
    private String url;

    /** 鉴权对象名称 -> 报文数据的映射 */
    private final Map<String, MessageDataModel> messageDataMap;

    public CompareSampleModel() {
        this.messageDataMap = new LinkedHashMap<>();
    }

    public CompareSampleModel(int id, String method, String url) {
        this.id = id;
        this.method = method;
        this.url = url;
        this.messageDataMap = new LinkedHashMap<>();
    }

    /** 获取记录编号 */
    public int getId() {
        return id;
    }

    /** 设置记录编号 */
    public void setId(int id) {
        this.id = id;
    }

    /** 获取 HTTP 方法 */
    public String getMethod() {
        return method;
    }

    /** 设置 HTTP 方法 */
    public void setMethod(String method) {
        this.method = method;
    }

    /** 获取请求 URL */
    public String getUrl() {
        return url;
    }

    /** 设置请求 URL */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * 存储指定鉴权对象的报文数据
     *
     * @param authName 鉴权对象名称（如 "Original" / "Unauthorized" / "User1"）
     * @param data     报文数据
     */
    public void putMessageData(String authName, MessageDataModel data) {
        messageDataMap.put(authName, data);
    }

    /**
     * 获取指定鉴权对象的报文数据
     *
     * @param authName 鉴权对象名称
     * @return 报文数据，不存在返回 null
     */
    public MessageDataModel getMessageData(String authName) {
        return messageDataMap.get(authName);
    }

    /**
     * 移除指定鉴权对象的报文数据
     *
     * @param authName 鉴权对象名称
     */
    public void removeMessageData(String authName) {
        messageDataMap.remove(authName);
    }

    /**
     * 获取所有鉴权对象名称
     *
     * @return 鉴权对象名称集合
     */
    public Set<String> getAuthNames() {
        return Set.copyOf(messageDataMap.keySet());
    }

    /**
     * 获取指定鉴权对象的包长度
     *
     * @param authName 鉴权对象名称
     * @return 包长度，无数据返回 0
     */
    public int getLengthByAuthName(String authName) {
        MessageDataModel data = messageDataMap.get(authName);
        return data != null ? data.getLength() : 0;
    }
}

