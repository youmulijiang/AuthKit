package model;

/**
 * HTTP 报文数据模型
 * 封装单个 HTTP 请求/响应的核心数据，供 UI 展示和 diff 比较使用。
 */
public class MessageDataModel {

    private String request;
    private String response;
    private int statusCode;
    private int length;
    private String hash;

    public MessageDataModel() {
    }

    public MessageDataModel(String request, String response, int statusCode, int length, String hash) {
        this.request = request;
        this.response = response;
        this.statusCode = statusCode;
        this.length = length;
        this.hash = hash;
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
    public String getHash() {
        return hash;
    }

    /** 设置响应体哈希值 */
    public void setHash(String hash) {
        this.hash = hash;
    }
}

