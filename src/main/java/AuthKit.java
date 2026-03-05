import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import utils.ApiUtils;
import utils.LogUtils;

public class AuthKit implements BurpExtension {
    @Override
    public void initialize(MontoyaApi montoyaApi) {
        // 初始化全局 API 访问点
        ApiUtils.INSTANCE.init(montoyaApi);

        // 设置插件名称
        montoyaApi.extension().setName("AuthKit");

        LogUtils.INSTANCE.info("AuthKit 插件加载成功");
    }
}
