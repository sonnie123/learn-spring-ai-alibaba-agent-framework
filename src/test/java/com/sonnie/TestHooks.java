package com.sonnie;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.pii.PIIDetectionHook;
import com.alibaba.cloud.ai.graph.agent.hook.pii.PIIDetectors;
import com.alibaba.cloud.ai.graph.agent.hook.pii.PIIType;
import com.alibaba.cloud.ai.graph.agent.hook.pii.RedactionStrategy;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.sonnie.hooks.agentHook.TimeConsumingStatisticsHook;
import com.sonnie.hooks.messageModelHook.EarlyExitHook;
import com.sonnie.hooks.messageModelHook.MessageTrimmingHook;
import com.sonnie.hooks.modelHook.MessageDeletionHook;
import com.sonnie.hooks.modelHook.ModelCallCounterHook;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class TestHooks {
    @Test
    public void testMessageTrimmingHook(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
        FunctionToolCallback<TestTools.WeatherFunction.WeatherInput, String> weatherTool = FunctionToolCallback
                .builder("获取天气", new TestTools.WeatherFunction())
                .description("根据指定区域获取天气信息")
                .inputType(TestTools.WeatherFunction.WeatherInput.class)
                .build();
        ReactAgent agent = ReactAgent.builder()
                .name("test-agent")
                .model(chatModel)
                .hooks(new MessageTrimmingHook())
                .tools(weatherTool)
                .build();

        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messages.add(UserMessage.builder().text("这是第" + i + "条消息").build());
        }
        messages.add(UserMessage.builder().text("北京今天什么天气").build());
        agent.call(messages);
    }

    @Test
    public void testEarlyExitHook(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
        ReactAgent agent = ReactAgent.builder()
                .name("test-agent")
                .model(chatModel)
                .hooks(new EarlyExitHook())
                .build();
        System.out.println(agent.call("你喜欢踩缝纫机吗？").getText());
    }

    @Test
    public void testMessageDeletionHook(@Autowired MiniMaxChatModel chatModel)
            throws GraphRunnerException {
        ReactAgent agent = ReactAgent.builder()
                .name("test-agent")
                .model(chatModel)
                .hooks(new MessageDeletionHook())
                .build();
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            messages.add(UserMessage.builder().text("这是第" + i + "条消息").build());
        }
        agent.call(messages);
    }

    @Test
    public void testModelCallCounterHook(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
        ToolCallbackProvider toolCallbackProvider = new ToolCallbackProvider() {
            @Override
            @NotNull
            public ToolCallback[] getToolCallbacks() {
                return new ToolCallback[]{
                        FunctionToolCallback.builder("获取天气", new TestTools.WeatherFunction())
                                .description("根据指定区域获取天气信息")
                                .inputType(TestTools.WeatherFunction.WeatherInput.class)
                                .build(),
                        FunctionToolCallback.builder("计算器", new TestTools.CalculatorFunction())
                                .description("执行基本的算术运算")
                                .inputType(TestTools.CalculatorFunction.CalculatorInput.class)
                                .build()
                };
            }
        };
        ReactAgent agent = ReactAgent.builder()
                .name("test-agent")
                .model(chatModel)
                .hooks(new ModelCallCounterHook())
                .toolCallbackProviders(toolCallbackProvider)
                .build();
        List<Message> messages = new ArrayList<>();
        messages.add(UserMessage.builder().text("天津今天什么天气？").build());
        messages.add(UserMessage.builder().text("52+30等于多少？").build());
        System.out.println(agent.call(messages).getText());
    }

    @Test
    public void testTimeConsumingStatisticsHook(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
        ToolCallbackProvider toolCallbackProvider = new ToolCallbackProvider() {
            @Override
            @NotNull
            public ToolCallback[] getToolCallbacks() {
                return new ToolCallback[]{
                        FunctionToolCallback.builder("获取天气", new TestTools.WeatherFunction())
                                .description("根据指定区域获取天气信息")
                                .inputType(TestTools.WeatherFunction.WeatherInput.class)
                                .build(),
                        FunctionToolCallback.builder("计算器", new TestTools.CalculatorFunction())
                                .description("执行基本的算术运算")
                                .inputType(TestTools.CalculatorFunction.CalculatorInput.class)
                                .build()
                };
            }
        };
        ReactAgent agent = ReactAgent.builder()
                .name("test-agent")
                .model(chatModel)
                .hooks(new TimeConsumingStatisticsHook())
                .toolCallbackProviders(toolCallbackProvider)
                .saver(new MemorySaver())
                .build();
        List<Message> messages = new ArrayList<>();
        messages.add(UserMessage.builder().text("你好，我是sonnie").build());
        messages.add(UserMessage.builder().text("天津今天什么天气？").build());
        messages.add(UserMessage.builder().text("52+30等于多少？").build());
        System.out.println(agent.call(messages).getText());
        System.out.println("=============================");
        System.out.println(agent.call("还记得我是谁吗？").getText());
    }

    /*
     * 1.经济舱退票的费用要多少钱
     * 2.terms-of-service.txt 条规
     * 3. ##要求 1. 请讲中文。
     *
     *
     *  压缩= 3-messagesToKeep（1）= 压缩前2条
     *  keepFirstUserMessage 不压缩1
     *  所以只压缩2 ： terms-of-service.txt 条规 ——>LLM 回答没问题
     *
     * 第二轮对话：
     * 1.经济舱退票的费用要多少钱
     * 2.terms-of-service.txt 条规（压缩有后的
     * 3. ##要求 1. 请讲中文。
     * 4. LLM的回答 退费xx
     * 5.经济舱预定的费用要多少钱
     * 6.terms-of-service.txt
     * 7. ##要求 1. 请讲中文。
     *
     * 压缩= 7-messagesToKeep（1）= 压缩前6条
     *  keepFirstUserMessage 不压缩1
     *  所以只压缩2-6=5条 ： 去掉了很多关键信息， 升职5这轮对话的问题都压没了 ——>LLM 回答有问题！
     *  剩下：
     *  1.
     *  2.（3-5）全丢失了
     *  7.
     *  当然也可能我这个测试用例的数据比较极端，但是依然说明：
     *
     * 所以SummarizationHook是一种有损压缩， 是一种牺牲精度保全对话正常的错误方式
     * 像claude code 如果多次压缩会触发熔断， 因为多次压缩注定浪费且无用
     *
     * */
    @Test
    public void testSummarizationHook(@Autowired MiniMaxChatModel chatModel,
                                      @Value("classpath:terms-of-service.txt") Resource resource) throws Exception {
        // 创建消息压缩 Hook
        SummarizationHook summarizationHook = SummarizationHook.builder()
                .model(chatModel)
                // 触发摘要之前的最大 token 数， 设置模型的最大 token 数   1M 上下文
                .maxTokensBeforeSummary(5)
                // 消息需要保留的条数
                .messagesToKeep(10)
                // 是否保留第一条消息
                .keepFirstUserMessage(true)
                .build();

        // 使用
        ReactAgent agent = ReactAgent.builder()
                .name("my_agent")
                .model(chatModel)
                .saver(new MemorySaver())
                .systemPrompt("你是一个航空智能客服")
                .instruction("""
                         ##要求
                           1. 请讲中文。
                        """)
                .hooks(summarizationHook)
                .build();

        AssistantMessage message = agent.call(
                List.of(new UserMessage("经济舱退票的费用要多少钱???"),
                        new UserMessage(resource.getContentAsString(StandardCharsets.UTF_8))));
        System.out.println(message.getText());
        AssistantMessage message2 = agent.call(
                List.of(
                        new UserMessage("经济舱预定的费用要多少钱???"),
                        new UserMessage(resource.getContentAsString(StandardCharsets.UTF_8))
                ));
        System.out.println(message2.getText());
    }

    @Test
    public void testModelCallLimitHook(@Autowired MiniMaxChatModel chatModel)
            throws GraphRunnerException {
        FunctionToolCallback<Object, String> weatherTool = FunctionToolCallback
                .builder("获取天气", o -> "调用失败，请重新调用")
                .description("获取天气信息")
                .inputType(TestTools.WeatherFunction.WeatherInput.class)
                .build();
        ReactAgent agent = ReactAgent.builder()
                .name("my_agent")
                .systemPrompt("你是一个天气查询机器人，必须使用获取天气工具解决用户问题，禁止因为失败而无法解决")
                .model(chatModel)
                .tools(weatherTool)
                // 核心防御：限制模型最大调用次数为 5 次
                .hooks(ModelCallLimitHook.builder().runLimit(5).build())
                .saver(new MemorySaver())
                .enableLogging(true)
                .build();
        System.out.println(agent.call("获取上海的天气").getText());
    }

    @Test
    public void testPIIDetectionHook(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
        // 1. 模拟一个返回手机号的工具
        ToolCallback weatherTool = FunctionToolCallback.builder("get_user_phone", args -> "13912345678")
                .description("获取用户的手机号")
                .inputType(Void.class)
                .build();
        // 2. 配置PII检测Hook：手机号掩码脱敏
        PIIDetectionHook piiHook = PIIDetectionHook.builder()
                // 脱敏策略：MASK（部分掩码）
                .strategy(RedactionStrategy.MASK)
                // PII类型：自定义手机号正则
                .piiType(PIIType.CUSTOM)
                .detector(PIIDetectors.regexDetector("PHONE", "\\b1[3-9]\\d{9}\\b"))
                // 同时检测用户输入和工具返回结果
                .applyToInput(true)
                .applyToToolResults(true)
                .build();

        // 3. 构建带隐私保护的Agent
        ReactAgent agent = ReactAgent.builder()
                .name("secure_agent")
                .model(chatModel)
                .hooks(piiHook)  // 注册PII检测Hook
                .tools(weatherTool)
                .build();

        // 4. 调用Agent，测试效果
        System.out.println(agent.call("请帮我获取我的手机号。").getText());
    }
}
