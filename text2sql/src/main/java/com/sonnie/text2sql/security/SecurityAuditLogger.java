package com.sonnie.text2sql.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Layer 4: 安全审计日志记录器
 *
 * 安全防护第四层,记录所有安全相关事件用于审计和追溯。
 *
 * 日志输出:
 * - 使用独立的 logger 名 "SECURITY_AUDIT"
 * - 可在 logback-spring.xml 中单独配置输出位置和格式
 *
 * 日志级别:
 * - WARN: 常规阻止事件(输入验证、SQL验证失败)
 * - ERROR: 严重的攻击尝试(如确认的注入攻击)
 *
 * 使用方式:
 * - 在 logback-spring.xml 中添加:
 *   <logger name="SECURITY_AUDIT" level="WARN" additivity="false">
 *       <appender>...</appender>
 *   </logger>
 */
@Component
public class SecurityAuditLogger {

    /**
     * 专用的安全审计 logger
     * 可以在 logback 配置中单独控制输出
     */
    private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY_AUDIT");

    /**
     * 安全事件类型枚举
     * 用于分类和标识不同的安全事件
     */
    public enum EventType {
        /** 用户输入被阻止 (Layer 1 触发) */
        INPUT_BLOCKED,
        /** SQL 查询被阻止 (Layer 2 触发) */
        QUERY_BLOCKED,
        /** SQL 执行被阻止 */
        EXECUTION_BLOCKED,
        /** 可疑行为检测到 */
        SUSPICIOUS_BEHAVIOR,
        /** 确认的注入攻击尝试 */
        INJECTION_ATTEMPT
    }

    /**
     * 记录用户输入被阻止的事件
     * 通常在 Layer 1 (PromptInjectionDetector) 检测到攻击时调用
     *
     * @param input 用户输入的内容(会被截断)
     * @param reason 阻止的原因
     * @param threadId 线程/会话ID,用于追踪
     */
    public void logInputBlocked(String input, String reason, String threadId) {
        securityLogger.warn("SECURITY_EVENT: type=INPUT_BLOCKED reason={} threadId={} input={}",
                reason, threadId, truncate(input));
    }

    /**
     * 记录 SQL 查询被阻止的事件
     * 在 Layer 2 (SqlSecurityValidator) 检测到危险 SQL 时调用
     *
     * @param query 被阻止的 SQL 查询
     * @param reason 阻止的原因
     * @param threadId 线程/会话ID
     */
    public void logQueryBlocked(String query, String reason, String threadId) {
        securityLogger.warn("SECURITY_EVENT: type=QUERY_BLOCKED reason={} threadId={} query={}",
                reason, threadId, truncate(query));
    }

    /**
     * 记录 SQL 执行被阻止的事件
     * 当 DML 模式检测或其他执行时检查失败时调用
     *
     * @param query 被阻止的 SQL 查询
     * @param reason 阻止的原因
     * @param threadId 线程/会话ID
     */
    public void logExecutionBlocked(String query, String reason, String threadId) {
        securityLogger.warn("SECURITY_EVENT: type=EXECUTION_BLOCKED reason={} threadId={} query={}",
                reason, threadId, truncate(query));
    }

    /**
     * 记录可疑行为事件
     * 当检测到异常但不足以完全阻止时调用
     *
     * @param description 可疑行为的描述
     * @param threadId 线程/会话ID
     */
    public void logSuspiciousBehavior(String description, String threadId) {
        securityLogger.warn("SECURITY_EVENT: type=SUSPICIOUS_BEHAVIOR description={} threadId={}",
                description, threadId);
    }

    /**
     * 记录确认的注入攻击尝试
     * 使用 ERROR 级别,因为这是明确确认的攻击行为
     *
     * @param payload 攻击载荷内容
     * @param threadId 线程/会话ID
     */
    public void logInjectionAttempt(String payload, String threadId) {
        securityLogger.error("SECURITY_EVENT: type=INJECTION_ATTEMPT payload={} threadId={}",
                truncate(payload), threadId);
    }

    /**
     * 截断过长的输入,防止日志膨胀
     * 超过200字符的内容会被截断并添加标记
     *
     * @param input 原始输入
     * @return 截断后的输入,如果原输入为null则返回"null"
     */
    private String truncate(String input) {
        if (input == null) return "null";
        if (input.length() <= 200) return input;
        return input.substring(0, 200) + "...[TRUNCATED]";
    }
}
