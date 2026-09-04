package com.sonnie;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;

@SpringBootTest
public class TestWorkflow {
//
//    private ReactAgent requirementAgent, architectureAgent, implementationAgent, deliveryAgent;
//
//    @BeforeEach
//    public void initAgents(@Autowired MiniMaxChatModel chatModel) {
//        requirementAgent = ReactAgent.builder()
//                .model(chatModel)
//                .name("requirement-agent")
//                .description("需求分析师")
//                .instruction("你是一个资深的需求分析师")
//                .outputKey("requirement_analysis")
//                .build();
//        architectureAgent = ReactAgent.builder()
//                .model(chatModel)
//                .name("architecture-agent")
//                .description("架构设计师")
//                .instruction("你是一个资深的架构设计师，你要根据{requirement_analysis}中的需求分析结果，设计出一个符合需求的架构")
//                .outputKey("architecture_design")
//                .build();
//        implementationAgent = ReactAgent.builder()
//                .model(chatModel)
//                .name("implementation-agent")
//                .description("开发工程师")
//                .instruction("你是一个资深的开发工程师,你要根据{architecture_design}中的架构设计结果，实现出一个符合需求的系统或是其他产品")
//                .outputKey("implementation_design")
//                .build();
//        deliveryAgent = ReactAgent.builder()
//                .model(chatModel)
//                .name("delivery-agent")
//                .description("交付工程师")
//                .instruction("你是一个资深的交付工程师,你要根据{implementation_design}中的实现设计结果，交付一个符合需求的系统或是其他产品")
//                .outputKey("delivery_plan")
//                .build();
//
//    }
//
//    @Test
//    public void testSequentialAgent() throws GraphRunnerException {
//        String bizRequest = "帮我生成一个网页，网页上有一个标题为《我是sonnie》的段落";
//        SequentialAgent workflow = SequentialAgent.builder()
//                .name("project_workflow")
//                .subAgents(List.of(requirementAgent, architectureAgent, implementationAgent, deliveryAgent))
//                .hooks(
//                        // 通过 Hook 优雅注入企业级风控逻辑
//                        new AgentHook() {
//                            public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
//                                state.value("requirement_analysis").ifPresent(content -> {
//                                    if (content.toString().contains("FAIL"))
//                                        throw new RuntimeException("需求无法实现");
//                                });
//                                return CompletableFuture.completedFuture(Map.of());
//                            }
//
//                            @Override
//                            public String getName() {
//                                return "project_workflow_hook";
//                            }
//                        })
//                .build();
//        Optional<OverAllState> result = workflow.invoke(bizRequest);
//        if (result.isPresent()) {
//            OverAllState state = result.get();
//            state.value("requirement_analysis").ifPresent(System.out::println);
//            state.value("architecture_design").ifPresent(System.out::println);
//            state.value("implementation_design").ifPresent(System.out::println);
//            state.value("delivery_plan").ifPresent(System.out::println);
//        }
//    }
}