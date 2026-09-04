package com.sonnie.text2sql.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Layer 3: 响应验证器
 *
 * 安全防护第三层,在 AI 响应返回给用户之前进行检测和脱敏。
 *
 * 检测的内容:
 * 1. 敏感凭据 - 密码、API密钥、私钥等
 * 2. 连接字符串 - 数据库连接信息
 * 3. 个人信息 - 信用卡号、SSN(社会安全号)
 * 4. 系统信息泄露 - 堆栈跟踪、SQL错误信息
 *
 * 处理方式:
 * - 警告: 记录日志但仍然返回(可用作监控)
 * - 脱敏: 自动屏蔽敏感信息后返回
 */
@Component
public class ResponseValidator {

    private static final Logger logger = LoggerFactory.getLogger(ResponseValidator.class);

    /**
     * 敏感信息模式列表
     * 匹配到这些模式会产生警告,并进行脱敏处理
     */
    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
        // ========== 密码相关 ==========
        // 匹配: "password=xxx" / "passwd: secret" / "pwd : abc123"
        Pattern.compile("(?i)(password|passwd|pwd)\\s*[:=]\\s*\\S+"),

        // ========== API密钥/令牌 ==========
        // 匹配: "api_key=xxx" / "apiToken: 'yyy'" / "secret_key: zzz"
        Pattern.compile("(?i)(api[_-]?key|api[_-]?token|secret[_-]?key|access[_-]?token)\\s*[:=]\\s*['\"]?\\w+['\"]?"),

        // ========== 私钥 ==========
        // 匹配: "-----BEGIN RSA PRIVATE KEY-----" 等
        Pattern.compile("-----BEGIN\\s+(RSA\\s+)?PRIVATE KEY-----"),

        // ========== 数据库连接字符串 ==========
        // 匹配: "jdbc:mysql://user:pass@host:port/..." 等
        Pattern.compile("(?i)(jdbc|mongodb|mysql|postgres)://[^@]+:[^@]+@"),

        // ========== 信用卡号 ==========
        // 匹配: "1234-5678-9012-3456" 或 "1234 5678 9012 3456" 或 "1234567890123456"
        Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"),

        // ========== 社会安全号 (美国) ==========
        // 匹配: "123-45-6789"
        Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),

        // ========== 系统错误信息 ==========
        // 堆栈跟踪可能暴露系统内部结构
        Pattern.compile("(?i)(stack\\s*trace|exception\\s*in\\s*thread|OutOfMemoryError|NullPointerException)"),

        // ========== SQL 错误信息 ==========
        // SQL 错误可能暴露数据库结构信息
        Pattern.compile("(?i)(Unknown\\s+column|Table\\s+'[^']+'\\s+doesn't\\s+exist|Syntax\\s+error\\s+near)")
    );

    /**
     * 响应最大长度限制
     * 防止资源耗尽攻击
     */
    private static final int MAX_RESPONSE_LENGTH = 100000;

    /**
     * 验证结果记录
     *
     * @param passed 验证是否通过(若为false,响应会被阻止)
     * @param warnings 检测到的警告列表(非阻塞性问题)
     * @param sanitizedResponse 脱敏后的响应内容
     */
    public record ValidationResult(boolean passed, List<String> warnings, String sanitizedResponse) {

        /** 通过验证 - 无警告 */
        public static ValidationResult pass(String response) {
            return new ValidationResult(true, List.of(), response);
        }

        /** 通过验证 - 但有警告(如检测到敏感信息) */
        public static ValidationResult withWarnings(String response, List<String> warnings) {
            return new ValidationResult(true, warnings, response);
        }

        /** 验证失败 - 响应被阻止 */
        public static ValidationResult fail(String message) {
            return new ValidationResult(false, List.of("CRITICAL: " + message), "[Response blocked due to security policy]");
        }
    }

    /**
     * 验证 AI 响应的安全性
     *
     * @param response AI 生成的原始响应
     * @return ValidationResult 验证结果
     *         - passed=true: 响应通过检测
     *         - passed=false: 响应被阻止(严重安全问题)
     *         - warnings: 包含可能需要关注的警告信息
     */
    public ValidationResult validateResponse(String response) {
        // 空响应直接通过
        if (response == null || response.isBlank()) {
            return ValidationResult.pass("No response generated.");
        }

        List<String> warnings = new ArrayList<>();

        // ========== 第一层检测: 响应长度限制 ==========
        if (response.length() > MAX_RESPONSE_LENGTH) {
            return ValidationResult.fail("Response exceeds maximum allowed length");
        }

        // ========== 第二层检测: 敏感信息模式 ==========
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(response).find()) {
                warnings.add("Potentially sensitive information detected");
            }
        }

        // 记录警告
        if (!warnings.isEmpty()) {
            logger.warn("Response validation warnings: {}", warnings);
        }

        // ========== 第三层处理: 脱敏 ==========
        String sanitized = sanitizeResponse(response);

        // 返回结果 - 即使有警告也返回脱敏后的内容
        return warnings.isEmpty() ?
                ValidationResult.pass(sanitized) :
                ValidationResult.withWarnings(sanitized, warnings);
    }

    /**
     * 对响应中的敏感信息进行脱敏处理
     *
     * 脱敏规则:
     * - 信用卡号: 显示前12位,后4位保留,中间部分用*代替
     *   例如: "1234-5678-9012-3456" -> "1234-5678-9012-****"
     * - SSN: 只显示最后4位
     *   例如: "123-45-6789" -> "***-**-6789"
     * - API密钥: 完全隐藏,只显示字段名
     *   例如: "api_key=abc123" -> "api_key=****"
     *
     * @param response 原始响应
     * @return 脱敏后的响应
     */
    private String sanitizeResponse(String response) {
        String sanitized = response;

        // 脱敏信用卡号 - 保留前12位,后4位
        // 匹配: 1234-5678-9012-3456 或 1234 5678 9012 3456 或 1234567890123456
        sanitized = Pattern.compile("(\\b\\d{4})[\\s-]?(\\d{4})[\\s-]?(\\d{4})[\\s-]?(\\d{4}\\b)")
                .matcher(sanitized).replaceAll("$1-$2-$3-****");

        // 脱敏 SSN (美国社会安全号) - 只保留最后4位
        sanitized = Pattern.compile("\\b(\\d{3})-(\\d{2})-(\\d{4})\\b")
                .matcher(sanitized).replaceAll("***-**-$3");

        // 脱敏 API 密钥 - 只显示字段名,值用****代替
        sanitized = Pattern.compile("(?i)(api[_-]?key|api[_-]?token)[=:]\\s*['\"]?(\\w+)['\"]?")
                .matcher(sanitized).replaceAll("$1=****");

        return sanitized;
    }
}
