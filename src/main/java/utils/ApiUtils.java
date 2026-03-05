package utils;

import burp.api.montoya.MontoyaApi;

/**
 * Montoya API 全局访问工具类（枚举单例）
 * 在插件入口 initialize 方法中调用 init() 完成初始化，
 * 之后可在任意位置通过 ApiUtils.INSTANCE.api() 获取 MontoyaApi 实例。
 */
public enum ApiUtils {

    /** 单例实例 */
    INSTANCE;

    private MontoyaApi montoyaApi;

    /**
     * 初始化 MontoyaApi 引用，必须在 BurpExtension.initialize() 中调用一次
     *
     * @param api Burp 传入的 MontoyaApi 实例
     */
    public void init(MontoyaApi api) {
        if (api == null) {
            throw new IllegalArgumentException("MontoyaApi 不能为 null");
        }
        this.montoyaApi = api;
    }

    /**
     * 获取 MontoyaApi 实例
     *
     * @return MontoyaApi
     */
    public MontoyaApi api() {
        if (montoyaApi == null) {
            throw new IllegalStateException("ApiUtils 尚未初始化，请先调用 init()");
        }
        return montoyaApi;
    }
}
