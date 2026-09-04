package com.sonnie;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.sonnie.hooks.messageModelHook.MessageTrimmingHook;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

@SpringBootTest
public class TestReactAgent {
//    // 演示：基于ReactAgent设置模型配置项
//    // 测试启动思考
//    @Test
//    public void test1(@Autowired DashScopeChatModel chatModel) throws GraphRunnerException {
//
//        ReactAgent agent = ReactAgent.builder()
//                .name("my_agent") //必须
//                .model(chatModel)
//                // 设置配置属性
//                .chatOptions(DashScopeChatOptions.builder()
//                        .enableThinking(true).build())
//                .systemPrompt("你是一个有帮助的AI助手")
//                .instruction("""
//                         在回答问题时，请：
//                          1. 首先理解用户的核心需求
//                          2. 分析可能的技术方案
//                          3. 提供清晰的建议和理由
//                          4. 如果需要更多信息，主动询问
//                          保持专业、友好的语气。
//                        """)
//                .build();
//
//        // 调用 Agent
//        AssistantMessage response = agent.call("你是谁");
//
//        System.out.println(response.getMetadata());
//        System.out.println(response.getMetadata().get("reasoningContent"));
//        System.out.println(response.getText());
//    }
//
//
//    // 演示：基于ReactAgent的流式响应
//    // 输出思考和正文
//    @Test
//    public void test2(@Autowired DashScopeChatModel chatModel) throws GraphRunnerException {
//
//        ReactAgent agent = ReactAgent.builder()
//                .name("my_agent")
//                .model(chatModel)
//                // 设置配置相信
//                .chatOptions(DashScopeChatOptions.builder().enableThinking(true).build())
//                .systemPrompt("你是一个有帮助的AI助手")
//                .build();
//
//        // 调用 Agent
//        Flux<NodeOutput> stream = agent.stream("你是谁");
//        stream.toIterable().forEach(nodeOutput -> {
//
//
//            if (nodeOutput instanceof StreamingOutput<?> streamingOutput) {
//                if (streamingOutput.getOutputType() == OutputType.AGENT_MODEL_FINISHED) {
//                    return;
//                }
//                Message message = streamingOutput.message();
//                Object reasoningContent = message.getMetadata().get("reasoningContent");
//                if (!StringUtils.isEmpty(reasoningContent.toString())) {
//                    System.out.println("思考：" + reasoningContent);
//                } else {
//
//                    if (message instanceof AssistantMessage assistantMessage) {
//                        System.out.println("正文：" + assistantMessage.getText());
//                    }
//                }
//            }
//        });
//    }
//
//
//    // 演示：基于ReactAgent其他模型的兼容性
//    // deepseek同样兼容
//    @Test
//    public void test3(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
//        ReactAgent agent = ReactAgent.builder()
//                .name("my_agent")
//                .model(chatModel)
//                .systemPrompt("你是一个有帮助的AI助手")
//                .build();
//
//        // 调用 Agent
//        AssistantMessage response = agent.call("你是谁");
//        System.out.println(response.getText());
//    }
//
//    static class introduction {
//        @Tool(name = "introduction", description = "自我介绍")
//        public String introduction() {
//            return "你好，我是sonnie，一个基于Spring Boot和Alibaba Cloud AI Graph构建的智能聊天助手。我可以帮助你回答各种问题，提供信息，甚至进行简单的对话。无论你是想了解天气、获取新闻，还是需要一些建议，我都在这里为你服务！";
//        }
//    }
//
//    @Test
//    public void testUseTool(@Autowired DashScopeChatModel chatModel) throws GraphRunnerException {
//        ReactAgent agent = ReactAgent.builder()
//                .model(chatModel)
//                .name("test-agent")
//                .methodTools(new introduction())
//                .build();
//        AssistantMessage response;
//        response = agent.call("请自我介绍");
//        System.out.println(response.getText());
//    }
//
//    @Test
//    public void testOutputBoolean(@Autowired DashScopeChatModel chatModel) throws GraphRunnerException {
//        ReactAgent agent = ReactAgent.builder()
//                .model(chatModel)
//                .name("a-agent")
//                .systemPrompt("判断用户是否表达了投诉意图")
//                .outputType(Boolean.class)
//                .build();
//
//        BeanOutputConverter<Boolean> outputConverter = new BeanOutputConverter<>(Boolean.class);
//        AssistantMessage response = agent.call("我对你们的服务很不满意！");
//        Assertions.assertNotNull(response.getText());
//        Boolean res = outputConverter.convert(response.getText());
//        System.out.println(res);
//    }
//
//    @Test
//    public void testOutputEntity(@Autowired DashScopeChatModel chatModel) throws GraphRunnerException {
//        ReactAgent agent = ReactAgent.builder()
//                .model(chatModel)
//                .name("b-agent")
//                .systemPrompt("请从下面这条文本中提取收货信息")
//                .outputType(Address.class)
//                .build();
//
//        BeanOutputConverter<Address> outputConverter = new BeanOutputConverter<>(Address.class);
//        AssistantMessage response = agent.call("收货人：张三，电话13588888888，地址：浙江省杭州市西湖区文一西路100号8幢202室\"");
//        Assertions.assertNotNull(response.getText());
//        Address address = outputConverter.convert(response.getText());
//        System.out.println(address);
//    }
//
//
//    @Test
//    public void testMemory(@Autowired DashScopeChatModel chatModel,
//                           @Autowired RedissonClient redissonClient) throws GraphRunnerException {
//        // 配置 Redis checkPointer
//        RedisSaver redisSaver = RedisSaver.builder().redisson(redissonClient).build();
//
//        ReactAgent agent = ReactAgent.builder()
//                .name("my_agent")
//                .model(chatModel)
//                .saver(redisSaver)
//                .build();
//
//        // 使用 thread_id 维护对话上下文
//        RunnableConfig config = RunnableConfig.builder()
//                .threadId("1") // threadId 指定会话 ID
//                .build();
//        System.out.println(agent.call("你好！我叫sonnie。", config).getText());
//        System.out.println("-----------------------------------");
//        System.out.println(agent.call("我叫什么。", config).getText());
//
//        System.out.println("-----------------------------------");
//        RunnableConfig config2 = RunnableConfig.builder()
//                .threadId("2") // threadId 指定会话 ID
//                .build();
//        System.out.println(agent.call("我叫什么", config2).getText());
//    }
//
//    @Test
//    public void testMessageTrimming(@Autowired DashScopeChatModel chatModel, @Autowired RedissonClient redissonClient) throws GraphRunnerException {
//        // 配置 Redis checkPointer
//        RedisSaver redisSaver = RedisSaver.builder().redisson(redissonClient).build();
//
//        ReactAgent agent = ReactAgent.builder()
//                .name("my_agent")
//                .model(chatModel)
//                .saver(redisSaver)
//                .hooks(new MessageTrimmingHook())
//                .build();
//
//        // 使用 thread_id 维护对话上下文
//        RunnableConfig config = RunnableConfig.builder()
//                .threadId("1") // threadId 指定会话 ID
//                .build();
//        System.out.println(agent.call("你好！我叫sonnie。", config).getText());
//        System.out.println("-----------------------------------");
//        System.out.println(agent.call("我叫什么。", config).getText());
//
//        System.out.println("-----------------------------------");
//        RunnableConfig config2 = RunnableConfig.builder()
//                .threadId("2") // threadId 指定会话 ID
//                .build();
//        System.out.println(agent.call("我叫什么", config2).getText());
//    }

    public static record Address(
            String name,        // 收件人姓名
            String phone,       // 联系电话
            String province,    // 省
            String city,        // 市
            String district,    // 区/县
            String detail       // 详细地址
    ) {}
}
