package com.sonnie.interceptors.modelInterceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

public class ContentModerationInterceptor extends ModelInterceptor {

    private static final List<String> BLOCKED_WORDS = List.of("吴签", "缝纫机");

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        // 检查输入
        for (Message msg : request.getMessages()) {
            String content = msg.getText().toLowerCase();
            for (String blocked : BLOCKED_WORDS) {
                if (content.contains(blocked)) {
                    return ModelResponse.of(AssistantMessage.builder().content("检测到敏感内容，请重新输入").build());
                }
            }
        }

        // 执行模型调用
        ModelResponse response = handler.call(request);

        // 检查输出
        String output = response.getMessage().toString();
        for (String blocked : BLOCKED_WORDS) {
            if (output.contains(blocked)) {
                // 清理输出
                output = output.replaceAll(blocked, "【已过滤】");
                return ModelResponse.of(AssistantMessage.builder().content(output).build());
            }
        }
        return response;
    }

    @Override
    public String getName() {
        return "CONTENT_MODERATION_INTERCEPTOR";
    }
}