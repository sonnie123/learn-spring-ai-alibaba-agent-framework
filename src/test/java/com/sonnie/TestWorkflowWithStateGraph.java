package com.sonnie;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeActionWithConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.sonnie.constant.CommonConstant;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

@SpringBootTest
public class TestWorkflowWithStateGraph {
//    @Autowired
//    private MiniMaxChatModel chatModel;
//
//    public StateGraph createStateGraph() throws GraphStateException {
//        KeyStrategyFactory keyStrategyFactory = () -> {
//            Map<String, KeyStrategy> map = new HashMap<>();
//            map.put("message", new AppendStrategy());
//            map.put("requirement_analysis", new ReplaceStrategy());
//            map.put("architecture_design", new ReplaceStrategy());
//            map.put("implementation_plan", new ReplaceStrategy());
//            map.put("delivery_checklist", new ReplaceStrategy());
//            return map;
//        };
//
//        ReactAgent requirementAgent = ReactAgent.builder()
//                .name("requirement")
//                .model(chatModel)
//                .instruction(CommonConstant.REQUIREMENT_AGENT_PROMPT)
//                .outputKey("requirement_analysis")
//                .build();
//
//        ReactAgent architectureAgent = ReactAgent.builder()
//                .name("architecture")
//                .model(chatModel)
//                .instruction(CommonConstant.ARCHITECTURE_AGENT_PROMPT)
//                .outputKey("architecture_design")
//                .build();
//
//        ReactAgent implementationAgent = ReactAgent.builder()
//                .name("implementation")
//                .model(chatModel)
//                .instruction(CommonConstant.IMPLEMENTATION_AGENT_PROMPT)
//                .outputKey("implementation_plan")
//                .build();
//
//        ReactAgent deliveryAgent = ReactAgent.builder()
//                .name("delivery")
//                .model(chatModel)
//                .instruction(CommonConstant.DELIVERY_AGENT_PROMPT)
//                .outputKey("delivery_checklist")
//                .build();
//
//        return new StateGraph("project_workflow", keyStrategyFactory)
//                // 节点定义
//                .addNode(requirementAgent.name(), requirementAgent.asNode(true, false))
////                .addNode("requirement", node_async((state, config) -> {
////
////                    // 查数据库....  if      .....
////                    // ...
////                    return Map.of("requirement_analysis", result);
////                }))
//                .addNode(architectureAgent.name(), architectureAgent.asNode(true, false))
//                .addNode(implementationAgent.name(), implementationAgent.asNode(true, false))
//                .addNode(deliveryAgent.name(), deliveryAgent.asNode(true, false))
//                // 边定义
//                .addEdge(START, "requirement")
//                // 条件边：需求分析后若包含FAIL则结束，否则继续架构设计
//                .addConditionalEdges("requirement",
//                        AsyncEdgeActionWithConfig.edge_async((state, config) ->
//                                state.value("requirement_analysis", String.class)
//                                        .filter(s -> s.contains("FAIL"))
//                                        .map(s -> "END")
//                                        .orElse("to_architecture")),
//                        Map.of("to_architecture", "architecture", "END", END))
//                .addEdge("architecture", "implementation")
//                .addEdge("implementation", "delivery")
//                .addEdge("delivery", END);
//    }
//
//    // 调用示例（非流式）
//    public void process(String bizRequirement) throws GraphStateException {
//        StateGraph graph = createStateGraph();
//        var compiled = graph.compile();
//        compiled.invoke(Map.of("input", bizRequirement))
//                .ifPresent(state -> {
//                    System.out.println("需求分析: " + state.value("requirement_analysis").orElse(""));
//                    System.out.println("架构设计: " + state.value("architecture_design").orElse(""));
//                    System.out.println("实施计划: " + state.value("implementation_plan").orElse(""));
//                    System.out.println("交付清单: " + state.value("delivery_checklist").orElse(""));
//                });
//    }
//
//    // 流式输出示例
//    public void processStreaming(String bizRequirement) throws GraphStateException {
//        StateGraph graph = createStateGraph();
//        var compiled = graph.compile();
//        Flux<NodeOutput> flux = compiled.stream(Map.of("input", bizRequirement));
//
//        flux.doOnNext(output -> {
//                    String agentName = output.agent() != null ? output.agent() : "(no agent)";
//                    String nodeName = output.node() != null ? output.node() : "(no node)";
//
//                    if (Objects.equals(nodeName, START)) {
//                        System.out.printf("\n——————[%s开始]——————\n", agentName);
//                    }
//
//                    if (output instanceof StreamingOutput streamingOutput) {
//                        Message message = streamingOutput.message();
//                        if (message instanceof AssistantMessage assistantMessage) {
//                            // 检查是否为 Thinking 消息
//                            Object reasoningContent = assistantMessage.getMetadata().get("reasoningContent");
//                            if (reasoningContent != null && !reasoningContent.toString().isEmpty()) {
//                                System.out.print("[Thinking] " + reasoningContent);
//                            } else {
//                                // 普通模型响应（增量内容）
//                                System.out.print(assistantMessage.getText());
//                            }
//                        }
//                    }
//                }
//        ).blockLast(); // ⚠️ 重要：必须阻塞直到大模型说完，才能把完整结果交给下一个节点！;
//    }
}