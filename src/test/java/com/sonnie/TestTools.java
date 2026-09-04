package com.sonnie;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.AgentTool;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.sonnie.constant.CommonConstant;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

@SpringBootTest
public class TestTools {
    @Test
    public void testFunctionTools(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
        ReactAgent agent = ReactAgent.builder()
                .model(chatModel)
                .name("test-agent")
                .systemPrompt("You are a helpful assistant with access to weather tools.")
                .instruction(CommonConstant.AI_ASSISTANT_INSTRUCTION)
                .tools(FunctionToolCallback
                        .builder("obtain_weather", new WeatherFunction())
                        .description("Obtain weather information based on the specified region")
                        .inputType(WeatherFunction.WeatherInput.class)
                        .build())
                .build();
        System.out.println(agent.call("北京天气怎么样").getText());
    }


    @Test
    public void testMethodTools(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
        ReactAgent agent = ReactAgent.builder()
                .model(chatModel)
                .name("test-agent")
                .methodTools(new IntroductionTool(), new CalculatorTool())
                .build();
        AssistantMessage response = agent.call("请自我介绍，并告诉我895加721等于几");
        System.out.println(response.getText());
    }

    @Test
    public void testToolCallbackProvider(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
        ToolCallbackProvider toolCallbackProvider = new ToolCallbackProvider() {
            @Override
            @NotNull
            public ToolCallback[] getToolCallbacks() {
                return new ToolCallback[]{
                        FunctionToolCallback.builder("获取天气", new WeatherFunction())
                                .description("根据指定区域获取天气信息")
                                .inputType(WeatherFunction.WeatherInput.class)
                                .build(),
                        FunctionToolCallback.builder("计算器", new CalculatorFunction())
                                .description("执行基本的算术运算")
                                .inputType(CalculatorFunction.CalculatorInput.class)
                                .build()
                };
            }
        };

        ReactAgent agent = ReactAgent.builder()
                .model(chatModel)
                .name("test-agent")
                .toolCallbackProviders(toolCallbackProvider)
                .build();
        AssistantMessage response = agent.call("明儿上海天气咋样？");
        System.out.println(response.getText());
    }

    @Test
    public void TestToolNamesAndResolver(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
        FunctionToolCallback<WeatherFunction.WeatherInput, String> weatherTool = FunctionToolCallback.builder("获取天气", new WeatherFunction())
                .description("根据指定区域获取天气信息")
                .inputType(WeatherFunction.WeatherInput.class)
                .build();
        FunctionToolCallback<CalculatorFunction.CalculatorInput, Integer> calculatorTool = FunctionToolCallback.builder("计算器", new CalculatorFunction())
                .description("执行基本的算术运算")
                .inputType(CalculatorFunction.CalculatorInput.class)
                .build();
        StaticToolCallbackResolver resolver = new StaticToolCallbackResolver(List.of(weatherTool, calculatorTool));

        ReactAgent agent = ReactAgent.builder()
                .model(chatModel)
                .name("multi-tool-agent")
                .description("多工具助手")
                .instruction("你是一个多工具助手，请根据用户问题使用相应的工具。")
                .toolNames("获取天气", "计算器")
                .resolver(resolver)
                .build();
        AssistantMessage response = agent.call("明儿上海天气咋样？另外35乘3等于几？");
        System.out.println(response.getText());
    }

    @Test
    public void testCombinedToolProvision(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
        ToolCallback weatherTool = FunctionToolCallback.builder("获取天气", new WeatherFunction())
                .description("根据指定区域获取天气信息")
                .inputType(WeatherFunction.WeatherInput.class)
                .build();

        ToolCallbackProvider toolCallbackProvider = new ToolCallbackProvider() {
            @Override
            @NotNull
            public ToolCallback[] getToolCallbacks() {
                return new ToolCallback[]{
                        FunctionToolCallback.builder("计算器", new CalculatorFunction())
                                .description("执行基本的算术运算")
                                .inputType(CalculatorFunction.CalculatorInput.class)
                                .build()
                };
            }
        };

        ReactAgent agent = ReactAgent.builder()
                .model(chatModel)
                .name("multi-tool-agent")
                .description("多工具助手")
                .instruction("你是一个多工具助手，请根据用户问题使用相应的工具。")
                .toolCallbackProviders(toolCallbackProvider)
                .tools(weatherTool)
                .methodTools(new IntroductionTool())
                .build();
        AssistantMessage response = agent.call("你是谁？今天深圳天咋样？1加92等于多少");
        System.out.println(response.getText());
    }

    @Test
    public void testAgentAsTools(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
        ReactAgent writerAgent = ReactAgent.builder()
                .name("full_typed_writer")
                .model(chatModel)
                .description("完整类型化的写作工具")
                .instruction("根据结构化输入（topic、wordCount、style）创作文章，并返回结构化输出（title、content、characterCount）。")
                .inputType(ArticleRequest.class)
                .outputType(ArticleOutput.class)
                .build();

        ReactAgent reviewerAgent = ReactAgent.builder()
                .name("typed_reviewer")
                .model(chatModel)
                .description("完整类型化的评审工具")
                .instruction("对文章进行评审，返回评审意见（comment、approved、suggestions）。")
                .outputType(ReviewOutput.class)
                .build();

        ReactAgent orchestratorAgent = ReactAgent.builder()
                .name("orchestrator")
                .model(chatModel)
                .instruction("协调写作和评审流程。先调用写作工具创作文章，然后调用评审工具进行评审。")
                .tools(
                        AgentTool.getFunctionToolCallback(writerAgent),
                        AgentTool.getFunctionToolCallback(reviewerAgent)
                )
                .build();

        Optional<OverAllState> result = orchestratorAgent.invoke("请写一篇关于友谊的散文，约100字，需要评审");
        result.ifPresent(state -> {
            // 拿所有 ReAct 轮次（user / assistant / tool response）
            List<Message> allMessages = (List<Message>) state.value("messages").orElse(List.of());
            allMessages.forEach(m -> System.out.println(m.getMessageType() + ": " + m.getText()));

//  //           拿最终回复（等价于 call()）
//            String text = state.value("messages")
//                    .map(msgs -> {
//                        List<Message> list = (List<Message>) msgs;
//                        return list.get(list.size() - 1).getText();
//                    })
//                    .orElse("");
//            System.out.println(text);

//             ③ 拿思考链（DashScope 的 reasoning_content 等）
//            state.<List<Message>>value("messages").ifPresent(msgs -> {
//                for (Message m : msgs) {
//                    if (m instanceof AssistantMessage am) {
//                        Object reasoningContent = am.getMetadata().get("reasoningContent");
//                        System.out.println(reasoningContent.toString());
//                    }
//                }
//            });

//             拿自定义 outputKey（builder.outputKey(...) 设置的）
//            Object requirement = state.value("xxx").orElse(null);
        });
    }

    @Test
    public void testToolContext(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
        ReactAgent agent = ReactAgent.builder()
                .model(chatModel)
                .name("multi-tool-agent")
                .description("多工具助手")
                .instruction("你是一个多工具助手，请根据用户问题使用相应的工具。")
                .tools(FunctionToolCallback
                        .builder("计算器", new CalculatorFunction())
                        .description("执行基本的算术运算")
                        .inputType(CalculatorFunction.CalculatorInput.class)
                        .build())
                .build();

        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(Thread.currentThread().getName())
                .addMetadata(CommonConstant.CURRENT_USER_ID, getCurrentUserId())
                .build();

        Flux<Message> response = agent.streamMessages("52减21等于多少？", runnableConfig);
        response.toIterable().forEach(m -> {
            String text = m.getText();
            if (StringUtils.isNoneEmpty(text)) {
                System.out.println(text);
            }
        });
    }

    public String getCurrentUserId() {
        return "001";
    }

    static class IntroductionTool {
        @Tool(name = "introduction", description = "自我介绍")
        public String introduction() {
            return "你好，我是sonnie，一个基于Spring Boot和Alibaba Cloud AI Graph构建的智能聊天助手。我可以帮助你回答各种问题，提供信息，甚至进行简单的对话。无论你是想了解天气、获取新闻，还是需要一些建议，我都在这里为你服务！";
        }
    }

    static class CalculatorTool {
        @Tool(description = "两数相加")
        public String add(
                @ToolParam(description = "第一个数") int a,
                @ToolParam(description = "第二个数") int b) {
            return String.valueOf(a + b);
        }

        @Tool(description = "两数相乘")
        public String multiply(
                @ToolParam(description = "第一个数") int a,
                @ToolParam(description = "第二个数") int b) {
            return String.valueOf(a * b);
        }
    }

    static class CalculatorFunction implements BiFunction<CalculatorFunction.CalculatorInput, ToolContext, Integer> {

        @Override
        public Integer apply(CalculatorFunction.CalculatorInput request, ToolContext toolContext) {

//            RunnableConfig runnableConfig = (RunnableConfig) toolContext.getContext().get(ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY);
//            OverAllState overAllState = (OverAllState) toolContext.getContext().get(ToolContextConstants.AGENT_STATE_CONTEXT_KEY);
//            String userId = (String) runnableConfig.metadata(CommonConstant.CURRENT_USER_ID).orElse(null);
//            System.out.println("User ID: " + userId);

            switch (request.operation) {
                case "add" -> {
                    return request.num1 + request.num2;
                }
                case "multiply" -> {
                    return request.num1 * request.num2;
                }
                case "subtract" -> {
                    return request.num1 - request.num2;
                }
                case "divide" -> {
                    return request.num1 / request.num2;
                }
                default -> {
                    return Integer.MIN_VALUE;
                }
            }
        }


        public record CalculatorInput(String operation, Integer num1, Integer num2) {
        }
    }

    static class WeatherFunction implements BiFunction<WeatherFunction.WeatherInput, ToolContext, String> {

        @Override
        public String apply(WeatherFunction.WeatherInput request, ToolContext toolContext) {
            return request.region + "晴，50度";
        }

        public record WeatherInput(String region) {
        }
    }

    /**
     * @param topic     主题
     * @param wordCount 字数
     * @param style     风格
     */
    public record ArticleRequest(String topic, int wordCount, String style) {
    }

    @Data
    static class ArticleOutput {
        // 文章标题
        private String title;
        // 文章内容
        private String content;
        // 字数
        private int characterCount;
    }

    @Data
    static class ReviewOutput {
        // 评审意见
        private String comment;
        // 是否通过
        private boolean approved;
        // 建议
        private List<String> suggestions;
    }

}
