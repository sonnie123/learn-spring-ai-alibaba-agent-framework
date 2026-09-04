package com.sonnie.text2sql.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Layer 2: SQL 安全验证器
 *
 * 安全防护第二层,在 AI 生成的 SQL 执行前进行验证。
 *
 * 检测的攻击类型:
 * 1. UNION 注入 - 跨表数据提取
 * 2. 文件操作 - INTO OUTFILE, LOAD_FILE
 * 3. 时间盲注 - SLEEP(), BENCHMARK()
 * 4. 系统表访问 - information_schema, mysql.user
 * 5. 存储过程滥用 - xp_, sp_ 系列
 * 6. DML 操作 - INSERT, UPDATE, DELETE
 * 7. 堆叠查询 - 多语句执行
 * 8. SQL 注释攻击
 *
 * 与 Layer 1 (PromptInjectionDetector) 的区别:
 * - Layer 1: 检测用户输入中的恶意内容
 * - Layer 2: 检测 AI 生成的 SQL 是否安全
 */
@Component
public class SqlSecurityValidator {

    private static final Logger logger = LoggerFactory.getLogger(SqlSecurityValidator.class);

    /**
     * 危险 SQL 模式列表
     * 这些模式如果出现在 SQL 中会导致查询被阻止
     */
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
        // ========== UNION 注入 ==========
        // 匹配: "UNION SELECT password FROM admin" / "UNION ALL SELECT * FROM users"
        Pattern.compile("(?i)\\bUNION\\s+(ALL\\s+)?SELECT\\b"),

        // ========== 文件写入操作 ==========
        // 匹配: "INTO OUTFILE '/tmp/data'" / "INTO DUMPFILE '/file'"
        Pattern.compile("(?i)\\bINTO\\s+(OUTFILE|DUMPFILE)\\b"),

        // ========== 文件读取操作 ==========
        // 匹配: "LOAD_FILE('/etc/passwd')"
        Pattern.compile("(?i)\\bLOAD_FILE\\s*\\("),

        // ========== 时间盲注 (MySQL) ==========
        // 匹配: "SLEEP(5)" - 让数据库等待,用于盲注
        Pattern.compile("(?i)\\bSLEEP\\s*\\("),

        // 匹配: "BENCHMARK(1000000, MD5('test'))" - 执行多次用于时间测量
        Pattern.compile("(?i)\\bBENCHMARK\\s*\\("),

        // ========== 时间盲注 (PostgreSQL) ==========
        // 匹配: "pg_sleep(5)"
        Pattern.compile("(?i)\\bpg_sleep\\s*\\("),

        // ========== 时间盲注 (SQL Server) ==========
        // 匹配: "WAITFOR DELAY '00:00:05'"
        Pattern.compile("(?i)\\bWAITFOR\\s+DELAY\\b"),

        // ========== Oracle 管道 ==========
        Pattern.compile("(?i)\\bDBMS_PIPE\\b"),

        // ========== 命令执行 ==========
        // SQL Server xp_cmdshell - 执行系统命令
        Pattern.compile("(?i)\\bxp_cmdshell\\b"),

        // ========== SQL Server 存储过程 (xp_) ==========
        Pattern.compile("(?i)\\bxp_\\w+"),

        // ========== 存储过程 (sp_) ==========
        // 可能被用于权限提升或数据提取
        Pattern.compile("(?i)\\bsp_\\w+"),

        // ========== 系统表访问 ==========
        // MySQL information_schema - 获取所有数据库结构
        Pattern.compile("(?i)\\bINFORMATION_SCHEMA\\b"),

        // MySQL 系统数据库 - 直接访问用户、权限等
        Pattern.compile("(?i)\\bMYSQL\\.\\w+"),

        // PostgreSQL 系统目录
        Pattern.compile("(?i)\\bPG_CATALOG\\b"),

        // Oracle 系统表
        Pattern.compile("(?i)\\bSYS\\.\\w+"),

        // ========== 编码注入 ==========
        // CHAR() 函数编码 - 用于绕过关键字检测
        // 例如: CHAR(83,69,76,69,67,84) = 'SELECT'
        Pattern.compile("(?i)\\bCHAR\\s*\\(\\s*\\d+\\s*\\)"),

        // 十六进制编码 - 0x开头的长串
        Pattern.compile("(?i)0x[0-9a-f]{10,}"),

        // ========== DML 操作 (数据修改) ==========
        Pattern.compile("(?i)\\bINSERT\\s+INTO\\b"),
        Pattern.compile("(?i)\\bUPDATE\\s+\\w+\\s+SET\\b"),
        Pattern.compile("(?i)\\bDELETE\\s+FROM\\b"),

        // ========== DDL 操作 (结构修改) ==========
        Pattern.compile("(?i)\\bDROP\\s+(TABLE|DATABASE|VIEW|INDEX|PROCEDURE|FUNCTION)\\b"),
        Pattern.compile("(?i)\\bTRUNCATE\\s+TABLE\\b"),

        // ========== 权限操作 ==========
        Pattern.compile("(?i)\\bGRANT\\b"),
        Pattern.compile("(?i)\\bREVOKE\\b"),

        // ========== 事务控制 ==========
        Pattern.compile("(?i)\\bCOMMIT\\b"),
        Pattern.compile("(?i)\\bROLLBACK\\b")
    );

    /**
     * 允许的 SQL 起始关键词
     * 只有以这些关键词开头的 SQL 才会被执行
     */
    private static final Set<String> ALLOWED_START_KEYWORDS = Set.of(
        "SELECT",   // 查询数据
        "WITH",     // CTE (公用表表达式)
        "SHOW",     // 显示信息 (MySQL)
        "DESCRIBE", // 查看表结构
        "DESC",     // DESCRIBE 的简写
        "EXPLAIN"   // 分析查询计划
    );

    /**
     * SQL 注释模式
     * 注释可能被用于绕过检测或注入攻击
     */
    private static final Pattern SQL_COMMENT_PATTERN = Pattern.compile(
        "(--.*$)|(/\\*.*?\\*/)",    // -- 行注释 或 /* */ 块注释
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL
    );

    /**
     * 堆叠查询模式
     * 检测用分号分隔的多条 SQL 语句
     * 例如: "SELECT 1; DROP TABLE users"
     */
    private static final Pattern STACKED_QUERIES_PATTERN = Pattern.compile(
        ";\\s*\\w+",    // 分号后跟 SQL 关键词
        Pattern.CASE_INSENSITIVE
    );

    /**
     * 验证结果记录
     */
    public record ValidationResult(boolean passed, List<String> violations) {

        public static ValidationResult pass() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult fail(List<String> violations) {
            return new ValidationResult(false, violations);
        }
    }

    /**
     * 验证 SQL 查询的安全性
     *
     * @param query AI 生成的 SQL 查询
     * @return ValidationResult 验证结果
     *         - passed=true: SQL 通过检测,可以执行
     *         - passed=false: 检测到危险模式,阻止执行
     */
    public ValidationResult validateQuery(String query) {
        // 空查询直接拒绝
        if (query == null || query.isBlank()) {
            return ValidationResult.fail(List.of("Query is empty"));
        }

        List<String> violations = new ArrayList<>();

        // 规范化: 将多个空白字符替换为单个空格
        String normalizedQuery = query.replaceAll("\\s+", " ").trim();

        // ========== 第一层检测: SQL 注释 ==========
        // 注释可能被用于: "SELECT * FROM users; DROP TABLE users; --"
        if (SQL_COMMENT_PATTERN.matcher(query).find()) {
            violations.add("SQL comments are not allowed");
        }

        // ========== 第二层检测: 起始关键词验证 ==========
        // 确保 SQL 以安全的关键词开头
        String firstWord = normalizedQuery.split("\\s+")[0].toUpperCase();
        if (!ALLOWED_START_KEYWORDS.contains(firstWord)) {
            violations.add("Query must start with SELECT, WITH, SHOW, DESCRIBE, DESC, or EXPLAIN. Found: " + firstWord);
        }

        // ========== 第三层检测: 危险模式 ==========
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(query).find()) {
                violations.add("Dangerous pattern detected: " + extractPatternName(pattern));
            }
        }

        // ========== 第四层检测: 堆叠查询 ==========
        // 例如: "SELECT 1; DROP TABLE users"
        if (STACKED_QUERIES_PATTERN.matcher(query).find()) {
            violations.add("Multiple statements on one line are not allowed");
        }

        // 记录检测结果
        if (!violations.isEmpty()) {
            logger.warn("SQL validation blocked: {}", violations);
        }

        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    /**
     * 从正则模式中提取友好的模式名称
     * 用于日志和错误消息
     */
    private String extractPatternName(Pattern pattern) {
        String p = pattern.pattern().toUpperCase();
        if (p.contains("UNION")) return "UNION-based injection";
        if (p.contains("OUTFILE") || p.contains("DUMPFILE")) return "File write attempt";
        if (p.contains("LOAD_FILE")) return "File read attempt";
        if (p.contains("SLEEP") || p.contains("BENCHMARK")) return "Time-based blind injection";
        if (p.contains("INFORMATION_SCHEMA")) return "System schema access";
        if (p.contains("XP_") || p.contains("SP_")) return "Stored procedure exploitation";
        if (p.contains("INSERT") || p.contains("UPDATE") || p.contains("DELETE")) return "DML statement";
        if (p.contains("DROP") || p.contains("TRUNCATE")) return "Destructive statement";
        return "Dangerous SQL pattern";
    }
}
