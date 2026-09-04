package com.sonnie.text2sql.controller;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.sonnie.text2sql.config.SecurityProperties;
import com.sonnie.text2sql.entity.ChatRequest;
import com.sonnie.text2sql.entity.ChatResponse;
import com.sonnie.text2sql.security.PromptInjectionDetector;
import com.sonnie.text2sql.security.ResponseValidator;
import com.sonnie.text2sql.security.SecurityAuditLogger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SQL Agent REST API 控制器
 *
 * 处理用户与 SQL Agent 的交互请求
 *
 * 安全防护集成:
 * - Layer 1: 输入验证 (PromptInjectionDetector) - 在请求到达 AI 前拦截
 * - Layer 3: 响应验证 (ResponseValidator) - 在响应返回用户前拦截
 *
 * API 端点:
 * POST /api/sql/chat - 与 SQL Agent 对话
 *
 * 请求格式:
 * {
 *   "message": "查询用户的数量",
 *   "threadId": "可选的会话ID"
 * }
 *
 * 响应格式:
 * {
 *   "response": "查询结果...",
 *   "threadId": "会话ID",
 *   "success": true/false
 * }
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sql")
public class SqlAgentController {

    /** SQL Agent 实例 */
    private final ReactAgent sqlAgent;

    /** Layer 1: Prompt 注入检测器 */
    private final PromptInjectionDetector promptInjectionDetector;

    /** Layer 3: 响应验证器 */
    private final ResponseValidator responseValidator;

    /** Layer 4: 审计日志记录器 */
    private final SecurityAuditLogger auditLogger;

    /** 安全配置属性 */
    private final SecurityProperties securityProperties;

    private static final Logger logger = LoggerFactory.getLogger(SqlAgentController.class);

    /**
     * 处理用户聊天请求
     *
     * 处理流程:
     * 1. 验证用户输入 (Layer 1)
     * 2. 调用 ReactAgent 处理请求
     * 3. 验证 AI 响应 (Layer 3)
     * 4. 返回处理结果
     *
     * @param request 聊天请求,包含用户消息和可选的会话ID
     * @return ChatResponse 包含 AI 响应和状态
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        logger.info("Received chat request: {}", request.getMessage());

        // 生成或使用传入的会话ID,用于追踪对话
        String threadId = request.getThreadId();
        if (threadId == null || threadId.isEmpty()) {
            threadId = UUID.randomUUID().toString();
        }

        // ==================== Layer 1: 输入验证 ====================
        // 在用户输入到达 AI 之前,检测潜在的 Prompt 注入攻击
        // 如果检测到攻击,直接拒绝请求,不会调用 AI
        if (securityProperties.isEnabled() && securityProperties.isEnableInputValidation()) {
            PromptInjectionDetector.ValidationResult inputResult =
                    promptInjectionDetector.validate(request.getMessage());

            // 验证失败: 输入包含可疑模式
            if (!inputResult.passed()) {
                String errorMsg = "Request blocked due to security policy. Please rephrase your query.";
                logger.warn("Input validation blocked request: {}", inputResult.violations());

                // 记录安全事件
                auditLogger.logInputBlocked(request.getMessage(),
                        String.join("; ", inputResult.violations()), threadId);

                return new ChatResponse(errorMsg, threadId, false);
            }
        }

        try {
            // ==================== 调用 AI Agent ====================
            // 构建运行配置,包含会话ID以支持多轮对话
            RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

            // 调用 ReactAgent 处理请求
            // ReactAgent 会自动调用相关工具: list_tables -> get_schema -> check_query -> execute_query
            NodeOutput result = sqlAgent.invokeAndGetOutput(request.getMessage(), config).orElse(null);

            // 从 Agent 输出中提取响应内容
            String rawResponse = extractResponse(result);

            // ==================== Layer 3: 响应验证 ====================
            // 验证 AI 响应是否包含敏感信息,必要时进行脱敏
            if (securityProperties.isEnabled() && securityProperties.isEnableResponseValidation()) {
                ResponseValidator.ValidationResult responseResult =
                        responseValidator.validateResponse(rawResponse);

                // 响应验证失败(严重问题)
                if (!responseResult.passed()) {
                    auditLogger.logSuspiciousBehavior(
                            "Response validation failed: " + String.join("; ", responseResult.warnings()),
                            threadId);
                    return new ChatResponse(
                            "An error occurred processing your request.", threadId, false
                    );
                }

                // 使用脱敏后的响应
                rawResponse = responseResult.sanitizedResponse();
            }

            logger.info("Agent response: {}", rawResponse);
            return new ChatResponse(rawResponse, threadId, true);
        }
        catch (Exception e) {
            // 异常处理: 记录错误并返回友好消息
            logger.error("Error processing chat request", e);
            auditLogger.logSuspiciousBehavior(
                    "Agent exception: " + e.getMessage(),
                    threadId);
            return new ChatResponse("Sorry, an error occurred: " + e.getMessage(), threadId, false);
        }
    }

    /**
     * 从 ReactAgent 的输出中提取响应文本
     *
     * ReactAgent 返回 NodeOutput,包含执行状态和输出
     * 尝试按优先级获取响应:
     * 1. "output" 字段 - 通常包含最终输出
     * 2. "messages" 字段 - 消息列表,返回最后一条
     * 3. 状态对象的字符串表示 - 最后兜底
     *
     * @param result ReactAgent 返回的 NodeOutput
     * @return 提取的响应文本
     */
    private String extractResponse(NodeOutput result) {
        if (result == null) {
            return "No response generated.";
        }

        OverAllState state = result.state();

        // 优先级1: 尝试获取 "output" 字段(通常包含最终结果)
        Optional<Object> output = state.value("output");
        if (output.isPresent()) {
            return String.valueOf(output.get());
        }

        // 优先级2: 尝试获取 "messages" 字段,返回最后一条消息
        Optional<List<AbstractMessage>> messages = state.value("messages");
        if (messages.isPresent() && !messages.get().isEmpty()) {
            List<AbstractMessage> msgList = messages.get();
            return msgList.get(msgList.size() - 1).getText();
        }

        // 优先级3: 最后兜底,返回状态的字符串表示
        return state.toString();
    }
}
