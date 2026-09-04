package com.sonnie.config;

import com.alibaba.cloud.ai.dashscope.agent.DashScopeAgentOptions;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.extension.tools.filesystem.EditFileTool;
import com.alibaba.cloud.ai.graph.agent.extension.tools.filesystem.WriteFileTool;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.hook.shelltool.ShellToolAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.tools.ShellTool2;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import com.sonnie.tools.DeleteFileTool;
import com.sonnie.tools.MyListFilesTool;
import com.sonnie.tools.PythonTool;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@Configuration
public class AgentConfig {
    @Bean
    public RedisSaver redisSaver(RedissonClient redissonClient) {
        return new RedisSaver.Builder()
                .redisson(redissonClient)
                .build();
    }

    @Bean
    public ReactAgent sonnieAgent(MiniMaxChatModel chatModel, RedisSaver redisSaver) {
        return ReactAgent.builder()
                .name("sonnie")
                .model(chatModel)
                .systemPrompt("你是一个有帮助的AI助手，专注于提供技术解决方案和建议。")
                .chatOptions(DashScopeAgentOptions.builder().enableThinking(true).build())
                .instruction("""
                        在回答问题时，请：
                        1. 保持专业、友好的语气
                        2. 首先理解用户的核心需求
                        3. 分析可能的技术方案
                        4. 提供清晰的建议和理由
                        5. 如果需要更多信息，主动询问用户
                        """)
                .saver(redisSaver)
                .build();
    }

    @Bean
    public Agent myAgent(MiniMaxChatModel chatModel) {
        HumanInTheLoopHook humanReviewHook = HumanInTheLoopHook.builder()
//                当 LLM 想要调用 delete_file 工具时，不会立即执行，而是先把请求挂起，等待人工确认
                .approvalOn("delete_file", ToolConfig
                        .builder()
//                        向用户展示需要确认的问题
                        .description("确认删除吗？")
                        .build())
                .build();

        return ReactAgent.builder()
                .name("my_agent")
                .model(chatModel)
                .saver(new MemorySaver())
                .tools(MyListFilesTool.createListFilesToolCallback(MyListFilesTool.DESCRIPTION),
                        DeleteFileTool.createDeleteFileToolCallback(DeleteFileTool.DESCRIPTION))
                .hooks(humanReviewHook)
                .systemPrompt("你是一个有帮助的AI助手")
                .build();
    }

    @Bean
    public Agent agent(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
        SkillRegistry registry = ClasspathSkillRegistry.builder()
                .classpathPath(".agents/skills")
                .build();
        SkillsAgentHook skillsHook = SkillsAgentHook.builder()
                .skillRegistry(registry)
                .autoReload(true)
//                渐进式工具 Tool 披露，实现按需暴露，激活后该技能的工具在会话后续轮次中仍可用
                .groupedTools(Map.of(
                        "weather", List.of(FunctionToolCallback
                                .builder("get_weather", new WeatherFunction())
                                .description("根据指定区域获取天气信息")
                                .inputType(WeatherFunction.WeatherInput.class)
                                .build())
                ))
                .build();
        ShellToolAgentHook shellHook = ShellToolAgentHook.builder()
                .shellTool2(ShellTool2.builder(System.getProperty("user.dir")).build())
                .build();
        return ReactAgent.builder()
                .name("小A")
                .model(chatModel)
                .saver(new MemorySaver())
                .tools(
//                        这个地方设置的工具无论用不用的到都会占用上下文
                        PythonTool.createPythonToolCallback(PythonTool.DESCRIPTION),
                        MyListFilesTool.createListFilesToolCallback(MyListFilesTool.DESCRIPTION),
                        WriteFileTool.createWriteFileToolCallback(WriteFileTool.DESCRIPTION),
                        EditFileTool.createEditFileToolCallback(EditFileTool.DESCRIPTION)
                )
                .hooks(List.of(skillsHook, shellHook))
                .enableLogging(true)
                .build();
    }

    static class WeatherFunction implements BiFunction<WeatherFunction.WeatherInput, ToolContext, String> {

        @Override
        public String apply(WeatherFunction.WeatherInput request, ToolContext toolContext) {
            return request.region + "晴，50度";
        }

        public record WeatherInput(String region) {
        }
    }
}
