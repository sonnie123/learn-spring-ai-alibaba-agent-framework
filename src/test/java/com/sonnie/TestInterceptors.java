package com.sonnie;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.sonnie.hooks.messageModelHook.MessageTrimmingHook;
import com.sonnie.interceptors.modelInterceptor.ContentModerationInterceptor;
import com.sonnie.interceptors.toolInterceptor.ToolCacheInterceptor;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class TestInterceptors {
    @Test
    public void testContentModerationInterceptor(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
        ReactAgent agent = ReactAgent.builder()
                .name("test-agent")
                .model(chatModel)
                .interceptors(new ContentModerationInterceptor())
                .build();
        System.out.println(agent.call("吴签喜欢踩缝纫机").getText());
    }

    @Test
    public void testToolCacheInterceptor(@Autowired MiniMaxChatModel chatModel, @Autowired RedissonClient redissonClient) throws GraphRunnerException {
        FunctionToolCallback<TestTools.WeatherFunction.WeatherInput, String> weatherTool = FunctionToolCallback
                .builder("获取天气", new TestTools.WeatherFunction())
                .description("根据指定区域获取天气信息")
                .inputType(TestTools.WeatherFunction.WeatherInput.class)
                .build();
        ReactAgent agent = ReactAgent.builder()
                .name("test-agent")
                .model(chatModel)
                .interceptors(new ToolCacheInterceptor(60 * 60 * 1000, redissonClient))
                .tools(weatherTool)
                .build();
        System.out.println(agent.call("天津今天什么天气").getText());
        System.out.println("==================");
        System.out.println(agent.call("天津今天什么天气").getText());
        System.out.println(agent.call("天津今天什么天气").getText());
    }
}
