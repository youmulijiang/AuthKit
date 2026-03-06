package model;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

/**
 * HTTP 报文数据模型
 * 封装单个 HTTP 请求/响应的核心数据，供 UI 展示和 diff 比较使用。
 * 同时持有 Montoya 原始对象引用，供 HttpRequestEditor / HttpResponseEditor 使用。
 */
public class MessageDataModel {

    private String request;
    private String response;
    private int statusCode;
    private int length;
    private int hash;

    /** 响应 attributes 个数 */
    private int attributeCount;
    /** annotations notes 内容 */
    private String note;
    /** 鉴权风险评分 0~100 */
    private int rank;

    /** Montoya 原始请求对象（供编辑器使用） */
    private HttpRequest httpRequest;
    /** Montoya 原始响应对象（供编辑器使用） */
    private HttpResponse httpResponse;

    public MessageDataModel() {
    }

    public MessageDataModel(String request, String response, int statusCode, int length, int hash) {
        this.request = request;
        this.response = response;
        this.statusCode = statusCode;
        this.length = length;
        this.hash = hash;
    }

    public MessageDataModel(String request, String response, int statusCode, int length, int hash,
                            HttpRequest httpRequest, HttpResponse httpResponse) {
        this.request = request;
        this.response = response;
        this.statusCode = statusCode;
        this.length = length;
        this.hash = hash;
        this.httpRequest = httpRequest;
        this.httpResponse = httpResponse;
    }

    /** 获取请求报文 */
    public String getRequest() {
        return request;
    }

    /** 设置请求报文 */
    public void setRequest(String request) {
        this.request = request;
    }

    /** 获取响应报文 */
    public String getResponse() {
        return response;
    }

    /** 设置响应报文 */
    public void setResponse(String response) {
        this.response = response;
    }

    /** 获取响应状态码 */
    public int getStatusCode() {
        return statusCode;
    }

    /** 设置响应状态码 */
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    /** 获取响应体长度 */
    public int getLength() {
        return length;
    }

    /** 设置响应体长度 */
    public void setLength(int length) {
        this.length = length;
    }

    /** 获取响应体哈希值 */
    public int getHash() {
        return hash;
    }

    /** 设置响应体哈希值 */
    public void setHash(int hash) {
        this.hash = hash;
    }

    /** 获取响应 attributes 个数 */
    public int getAttributeCount() {
        return attributeCount;
    }

    /** 设置响应 attributes 个数 */
    public void setAttributeCount(int attributeCount) {
        this.attributeCount = attributeCount;
    }

    /** 获取 annotations notes */
    public String getNote() {
        return note;
    }

    /** 设置 annotations notes */
    public void setNote(String note) {
        this.note = note;
    }

    /** 获取鉴权风险评分 */
    public int getRank() {
        return rank;
    }

    /** 设置鉴权风险评分 */
    public void setRank(int rank) {
        this.rank = rank;
    }

    /** 获取 Montoya 原始请求对象 */
    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    /** 设置 Montoya 原始请求对象 */
    public void setHttpRequest(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
    }

    /** 获取 Montoya 原始响应对象 */
    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    /** 设置 Montoya 原始响应对象 */
    public void setHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
    }
}

