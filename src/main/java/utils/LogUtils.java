package utils;

import burp.api.montoya.logging.Logging;

/**
 * 日志工具类（枚举单例）
 * 包装 Montoya Logging API，支持在代码层控制日志的开启与关闭。
 * 使用方式：LogUtils.INSTANCE.info("消息") / LogUtils.INSTANCE.error("错误")
 */
public enum LogUtils {

    /** 单例实例 */
    INSTANCE;

    /** 日志开关，默认开启 */
    private volatile boolean enabled = true;

    /** 调试日志开关，默认关闭 */
    private volatile boolean debugEnabled = false;

    /**
     * 获取 Montoya Logging 实例（从 ApiUtils 中获取）
     *
     * @return Logging 实例
     */
    private Logging logging() {
        return ApiUtils.INSTANCE.api().logging();
    }

    /**
     * 开启日志
     */
    public void enable() {
        this.enabled = true;
    }

    /**
     * 关闭日志
     */
    public void disable() {
        this.enabled = false;
    }

    /**
     * 设置日志开关状态
     *
     * @param enabled true 开启，false 关闭
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取日志开关状态
     *
     * @return 是否开启
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * 开启调试日志
     */
    public void enableDebug() {
        this.debugEnabled = true;
    }

    /**
     * 关闭调试日志
     */
    public void disableDebug() {
        this.debugEnabled = false;
    }

    /**
     * 设置调试日志开关状态
     *
     * @param debugEnabled true 开启，false 关闭
     */
    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    /**
     * 输出普通信息日志到 Burp Output 面板
     *
     * @param message 日志内容
     */
    public void info(String message) {
        if (enabled) {
            logging().logToOutput("[INFO] " + message);
        }
    }

    /**
     * 输出带格式化参数的信息日志
     *
     * @param format 格式字符串
     * @param args   参数
     */
    public void info(String format, Object... args) {
        if (enabled) {
            logging().logToOutput("[INFO] " + String.format(format, args));
        }
    }

    /**
     * 输出调试日志（需要 debugEnabled 为 true）
     *
     * @param message 日志内容
     */
    public void debug(String message) {
        if (enabled && debugEnabled) {
            logging().logToOutput("[DEBUG] " + message);
        }
    }

    /**
     * 输出带格式化参数的调试日志
     *
     * @param format 格式字符串
     * @param args   参数
     */
    public void debug(String format, Object... args) {
        if (enabled && debugEnabled) {
            logging().logToOutput("[DEBUG] " + String.format(format, args));
        }
    }

    /**
     * 输出错误日志到 Burp Error 面板
     *
     * @param message 错误信息
     */
    public void error(String message) {
        if (enabled) {
            logging().logToError("[ERROR] " + message);
        }
    }

    /**
     * 输出带异常的错误日志到 Burp Error 面板
     *
     * @param message   错误信息
     * @param throwable 异常对象
     */
    public void error(String message, Throwable throwable) {
        if (enabled) {
            logging().logToError("[ERROR] " + message + " | " + throwable.getMessage());
            logging().logToError(throwable);
        }
    }
}
