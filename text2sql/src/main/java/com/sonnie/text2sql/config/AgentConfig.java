package com.sonnie.text2sql.config;

import com.alibaba.cloud.ai.dashscope.agent.DashScopeAgentOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {
    @Bean
    public MemorySaver memorySaver() {
        return new MemorySaver();
    }

    @Bean
    public ReactAgent sonnieAgent(MiniMaxChatModel chatModel, MemorySaver memorySaver) {
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
                .saver(memorySaver)
                .build();
    }
}
