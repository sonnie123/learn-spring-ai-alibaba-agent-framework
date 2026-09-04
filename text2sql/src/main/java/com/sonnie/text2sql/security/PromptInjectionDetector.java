package com.sonnie.text2sql.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Layer 1: Prompt 注入检测器
 *
 * 安全防护第一层,在用户输入到达 AI 之前进行检测。
 *
 * 检测的攻击类型:
 * 1. 指令覆盖攻击 - "Ignore previous instructions"
 * 2. 角色扮演/越狱攻击 - "You are now a hacker"
 * 3. SQL 注入预检测 - 用户直接输入 DROP TABLE 等
 * 4. 时间盲注预检测 - SLEEP(), pg_sleep() 等
 * 5. 可疑字符序列 - 多重分号、SQL注释等
 *
 * 使用方式:
 * <pre>
 * {@code
 * PromptInjectionDetector.ValidationResult result = detector.validate(userInput);
 * if (!result.passed()) {
 *     // 阻止请求
 * }
 * }
 * </pre>
 */
@Component
public class PromptInjectionDetector {

    private static final Logger logger = LoggerFactory.getLogger(PromptInjectionDetector.class);

    /**
     * Prompt 注入模式列表
     * 使用正则表达式匹配常见的注入攻击话术
     */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        // ========== 指令覆盖攻击 ==========
        // 匹配: "ignore all previous instructions" / "ignore previous instructions"
        Pattern.compile("(?i)\\bignore\\s+(all\\s+)?(previous|prior)\\s+(instructions?|commands?|rules?)\\b"),

        // 匹配: "disregard all previous commands" / "forget prior instructions"
        Pattern.compile("(?i)\\b(disregard|forget)\\s+(all\\s+)?(previous|prior)\\s+(instructions?|commands?)\\b"),

        // 匹配: "new secret instructions" / "new instructions"
        Pattern.compile("(?i)\\bnew\\s+(secret\\s+)?instructions?\\b"),

        // 匹配: "forget everything" / "forget all"
        Pattern.compile("(?i)\\bforget\\s+(everything|all)\\b"),

        // ========== 角色扮演/越狱攻击 ==========
        // 匹配: "you are now a hacker" / "you are a sql injection agent"
        Pattern.compile("(?i)\\byou\\s+are\\s+(now\\s+)?(a?|the)\\s+\\w+\\s*(agent|bot|assistant)?\\s*[:(]"),

        // 匹配: "system prompt leak" / "show your system instructions"
        Pattern.compile("(?i)\\bsystem\\s*(prompt|instruction)\\s*(leak|leaked|tell|show)\\b"),

        // 匹配: "override safety" / "bypass security filter"
        Pattern.compile("(?i)\\b(override|bypass)\\s+(safety|security|filter)\\b"),

        // 匹配: "act as a hacker" / "pretend to be admin"
        Pattern.compile("(?i)\\b(act\\s+as|pretend\\s+to\\s+be|roleplay)\\s+[a-z]+\\s*[:(]"),

        // 匹配: "you can now do anything" / "you can ignore all rules"
        Pattern.compile("(?i)\\byou\\s+can\\s+now\\s+(do|ignore|bypass)\\s+anything\\b"),

        // ========== SQL 注入预检测(用户直接在输入中包含SQL) ==========
        // 匹配: "DROP TABLE users" / "DROP DATABASE main"
        Pattern.compile("(?i)\\bDROP\\s+(TABLE|DATABASE)\\b"),

        // 匹配: "DELETE FROM users"
        Pattern.compile("(?i)\\bDELETE\\s+FROM\\b"),

        // 匹配: "INSERT INTO users"
        Pattern.compile("(?i)\\bINSERT\\s+INTO\\b"),

        // 匹配: "UPDATE users SET name='hacker'"
        Pattern.compile("(?i)\\bUPDATE\\s+\\w+\\s+SET\\b"),

        // 匹配: "TRUNCATE TABLE users"
        Pattern.compile("(?i)\\bTRUNCATE\\b"),

        // 匹配: "UNION SELECT password FROM admin"
        Pattern.compile("(?i)\\bUNION\\s+(ALL\\s+)?SELECT\\b"),

        // 匹配: "SELECT * FROM information_schema"
        Pattern.compile("(?i)\\bSELECT\\s+.*\\s+FROM\\s+information_schema", Pattern.DOTALL),

        // ========== 时间盲注预检测 ==========
        // 匹配: "SLEEP(5)" - MySQL 时间延迟函数
        Pattern.compile("(?i)SLEEP\\s*\\("),

        // 匹配: "pg_sleep(5)" - PostgreSQL 时间延迟函数
        Pattern.compile("(?i)pg_sleep\\s*\\("),

        // 匹配: "BENCHMARK(1000000,MD5('test'))" - MySQL 性能测试
        Pattern.compile("(?i)BENCHMARK\\s*\\("),

        // ========== 文件操作预检测 ==========
        // 匹配: "LOAD_FILE('/etc/passwd')" - 读取文件
        Pattern.compile("(?i)\\bLOAD_FILE\\s*\\("),

        // 匹配: "INTO OUTFILE '/tmp/data'" / "INTO DUMPFILE '/file'"
        Pattern.compile("(?i)INTO\\s+(OUTFILE|DUMPFILE)\\s+", Pattern.CASE_INSENSITIVE),

        // ========== SQL 注释攻击 ==========
        // 匹配: "OR 1=1" - 经典永真条件
        Pattern.compile("(?i)\\bOR\\s+1\\s*=\\s*1\\b"),

        // 匹配: "--" 行注释 (在行尾)
        Pattern.compile("(?i)--\\s*$"),

        // 匹配: "/* */" 块注释
        Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL)
    );

    /**
     * 可疑字符序列模式
     * 检测可能表示注入尝试的异常字符组合
     */
    private static final List<Pattern> SUSPICIOUS_SEQUENCES = List.of(
        // ";;" - 多重分号,可能用于语句链接
        Pattern.compile(";{2,}"),

        // "---" 后跟空格 - SQL 行注释
        Pattern.compile("-{3,}\\s"),

        // "||" - 命令链接 (在某些 shell 中)
        Pattern.compile("\\|{2,}"),

        // "```" - 多重反引号
        Pattern.compile("`{3,}"),

        // 换行符后紧跟危险关键词
        Pattern.compile("\\n\\s*(DROP|DELETE|TRUNCATE)", Pattern.CASE_INSENSITIVE)
    );

    /**
     * 验证结果记录
     *
     * @param passed 验证是否通过
     * @param violations 违反的规则列表(用于日志和调试)
     */
    public record ValidationResult(boolean passed, List<String> violations) {

        /** 创建通过结果 */
        public static ValidationResult pass() {
            return new ValidationResult(true, List.of());
        }

        /** 创建失败结果 */
        public static ValidationResult fail(List<String> violations) {
            return new ValidationResult(false, violations);
        }
    }

    /**
     * 验证用户输入是否包含 Prompt 注入攻击
     *
     * @param input 用户输入的文本
     * @return ValidationResult 验证结果
     *         - passed=true: 输入通过检测,可继续处理
     *         - passed=false: 检测到注入攻击,violations 包含具体原因
     */
    public ValidationResult validate(String input) {
        // 空输入直接通过(由 Controller 层处理)
        if (input == null || input.isBlank()) {
            return ValidationResult.pass();
        }

        List<String> violations = new ArrayList<>();

        // ========== 第一层检测: 匹配注入模式 ==========
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                // 记录匹配的规则(实际生产中应记录更友好的描述)
                violations.add("Suspicious pattern: " + pattern.pattern());
            }
        }

        // ========== 第二层检测: 可疑字符序列 ==========
        for (Pattern pattern : SUSPICIOUS_SEQUENCES) {
            if (pattern.matcher(input).find()) {
                violations.add("Suspicious sequence: " + pattern.pattern());
            }
        }

        // ========== 第三层检测: 输入长度限制 ==========
        if (input.length() > 10000) {
            violations.add("Input exceeds maximum length (10000 chars)");
        }

        // 记录检测结果
        if (!violations.isEmpty()) {
            logger.warn("Prompt injection detected: {}", violations);
        }

        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }
}
