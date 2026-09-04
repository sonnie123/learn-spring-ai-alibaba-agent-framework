package com.sonnie.text2sql.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.sonnie.text2sql.tools.ExecuteQueryTool;
import com.sonnie.text2sql.tools.GetSchemaTool;
import com.sonnie.text2sql.tools.ListTablesTool;
import com.sonnie.text2sql.tools.QueryCheckerTool;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.sonnie.text2sql.constant.SqlAgentConstant.SQL_AGENT_SYSTEM_PROMPT;

@Configuration
@RequiredArgsConstructor
public class SqlAgentConfiguration {

    private final MiniMaxChatModel chatModel;
    private final RedissonClient redissonClient;

    private final ListTablesTool listTablesTool;
    private final GetSchemaTool getSchemaTool;
    private final QueryCheckerTool queryCheckerTool;
    private final ExecuteQueryTool executeQueryTool;

    @Bean
    public ReactAgent sqlAgent() throws GraphStateException {
        return ReactAgent.builder()
                .name("sql-agent")
                .model(chatModel)
                .saver(RedisSaver.builder().redisson(redissonClient).build())
                .description(SQL_AGENT_SYSTEM_PROMPT)
                .tools(listTablesTool.toolCallback(),
                        getSchemaTool.toolCallback(),
                        queryCheckerTool.toolCallback(),
                        executeQueryTool.toolCallback())
                .build();
    }
}