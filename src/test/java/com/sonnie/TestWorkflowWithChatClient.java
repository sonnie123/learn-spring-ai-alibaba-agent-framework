package com.sonnie;

import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;

public class TestWorkflowWithChatClient {
    private final ChatClient chatClient;

    // 实战场景的中文提示词
    private static final String REQUIREMENT_ANALYSIS_PROMPT = """
            你是一个资深的需求分析师，请分析以下业务需求：
            
            需求描述: {input}
            
            请从以下角度进行分析：
            1. 核心业务目标
            2. 主要功能模块
            3. 技术难点识别
            4. 风险评估
            
            如果需求无法实现直接回复"FAIL"。
            """;

    private static final String ARCHITECTURE_DESIGN_PROMPT = """
            你是一个系统架构师，基于以下需求分析，设计系统架构：
            
            需求分析: {input}
            
            请设计：
            1. 系统整体架构
            2. 技术栈选择
            3. 数据库设计要点
            4. 接口设计规范
            5. 部署架构建议
            
            请提供完整的架构设计方案。
            """;

    private static final String IMPLEMENTATION_PLAN_PROMPT = """
            你是一个项目经理，基于以下架构设计，制定实施计划：
            
            架构设计: {input}
            
            请制定：
            1. 开发阶段划分
            2. 人员配置建议
            3. 时间节点规划
            4. 质量保证措施
            5. 风险应对策略
            
            请提供详细的项目实施计划。
            """;

    private static final String DELIVERY_CHECKLIST_PROMPT = """
            你是一个交付经理，基于以下实施计划，制定交付清单：
            
            实施计划: {input}
            
            请制定：
            1. 开发完成标准
            2. 测试验收标准
            3. 部署上线清单
            4. 运维监控要求
            5. 用户培训计划
            
            请以清晰的表格形式输出交付清单。
            """;

    public TestWorkflowWithChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public void process(String businessRequirement) {
        List<String> processSteps = new ArrayList<>();
        System.out.println("=== 开始项目全流程处理 ===");

        // 步骤1: 需求分析
        System.out.println("步骤1: 业务需求分析");
        // 请求大模型
        String currentOutput1 = chatClient.prompt()
                .user(u -> u.text(REQUIREMENT_ANALYSIS_PROMPT).param("input", businessRequirement))
                .call()
                .content();

        // ==== Gate 逻辑 ====
        if (currentOutput1 != null && currentOutput1.contains("FAIL")) {
            System.out.println("【流程终止】：需求无法实现，流程提前退出。");
            return; // 提前返回，不进行后续步骤
        }
        System.out.println("需求分析完成:" + currentOutput1);
        // 步骤2: 架构设计
        System.out.println("步骤2: 系统架构设计");
        String currentOutput2 = chatClient.prompt()
                .user(u -> {
                    if (currentOutput1 != null) {
                        u.text(ARCHITECTURE_DESIGN_PROMPT).param("input", currentOutput1);
                    }
                })
                .call()
                .content();
        System.out.println("架构设计完成:" + currentOutput2);

        // 步骤3: 实施计划
        System.out.println("步骤3: 项目实施规划");
        String currentOutput3 = chatClient.prompt()
                .user(u -> {
                    if (currentOutput2 != null) {
                        u.text(IMPLEMENTATION_PLAN_PROMPT).param("input", currentOutput2);
                    }
                })
                .call()
                .content();
        System.out.println("实施计划完成:" + currentOutput3);

        // 步骤4: 交付清单
        System.out.println("步骤4: 交付清单制定");
        String currentOutput4 = chatClient.prompt()
                .user(u -> {
                    if (currentOutput3 != null) {
                        u.text(DELIVERY_CHECKLIST_PROMPT).param("input", currentOutput3);
                    }
                })
                .call()
                .content();
        System.out.println("交付清单完成:" + currentOutput4);

        System.out.println("=== 项目全流程处理完成 ===");

    }

    public record ProjectDeliverable(String finalDeliverable, List<String> processSteps) {
    }

}
