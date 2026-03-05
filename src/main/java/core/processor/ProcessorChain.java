package core.processor;

import burp.api.montoya.http.message.requests.HttpRequest;
import model.AuthUserModel;

import java.util.List;

/**
 * 处理器责任链
 * 按序执行所有启用的处理器，将原始请求逐步变换为最终请求。
 */
public class ProcessorChain {

    private final List<RequestProcessor> processors;

    /**
     * 构造处理器链
     *
     * @param processors 处理器列表（按执行顺序排列）
     */
    public ProcessorChain(List<RequestProcessor> processors) {
        this.processors = List.copyOf(processors);
    }

    /**
     * 执行处理器链，按序对请求进行变换
     *
     * @param request 原始请求
     * @param user    鉴权用户配置
     * @return 经过所有启用处理器变换后的最终请求
     */
    public HttpRequest execute(HttpRequest request, AuthUserModel user) {
        HttpRequest current = request;
        for (RequestProcessor processor : processors) {
            if (processor.isEnabled(user)) {
                current = processor.process(current, user);
            }
        }
        return current;
    }
}

