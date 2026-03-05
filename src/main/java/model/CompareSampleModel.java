package model;

/**
 * 鉴权比较样本数据模型
 * 封装一条完整的鉴权比较记录，包含原始请求、低权限请求和未授权请求的报文数据。
 */
public class CompareSampleModel {

    private int id;
    private String method;
    private String url;
    private MessageDataModel original;
    private MessageDataModel lowPrivilege;
    private MessageDataModel unauth;

    public CompareSampleModel() {
    }

    public CompareSampleModel(int id, String method, String url,
                              MessageDataModel original,
                              MessageDataModel lowPrivilege,
                              MessageDataModel unauth) {
        this.id = id;
        this.method = method;
        this.url = url;
        this.original = original;
        this.lowPrivilege = lowPrivilege;
        this.unauth = unauth;
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

    /** 获取原始请求报文数据 */
    public MessageDataModel getOriginal() {
        return original;
    }

    /** 设置原始请求报文数据 */
    public void setOriginal(MessageDataModel original) {
        this.original = original;
    }

    /** 获取低权限请求报文数据 */
    public MessageDataModel getLowPrivilege() {
        return lowPrivilege;
    }

    /** 设置低权限请求报文数据 */
    public void setLowPrivilege(MessageDataModel lowPrivilege) {
        this.lowPrivilege = lowPrivilege;
    }

    /** 获取未授权请求报文数据 */
    public MessageDataModel getUnauth() {
        return unauth;
    }

    /** 设置未授权请求报文数据 */
    public void setUnauth(MessageDataModel unauth) {
        this.unauth = unauth;
    }

    /**
     * 获取指定鉴权类型的包长度
     *
     * @param type 鉴权类型: "original" / "lowPrivilege" / "unauth"
     * @return 包长度，无数据返回 0
     */
    public int getLengthByType(String type) {
        MessageDataModel data = getDataByType(type);
        return data != null ? data.getLength() : 0;
    }

    /**
     * 根据类型获取对应的 MessageDataModel
     *
     * @param type 鉴权类型
     * @return 对应的报文数据，不存在返回 null
     */
    public MessageDataModel getDataByType(String type) {
        return switch (type) {
            case "original" -> original;
            case "lowPrivilege" -> lowPrivilege;
            case "unauth" -> unauth;
            default -> null;
        };
    }
}

