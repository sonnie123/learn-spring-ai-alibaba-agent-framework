/*
 * Copyright 2026-2027 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sonnie.text2sql.tools;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.sonnie.text2sql.security.SecurityAuditLogger;
import com.sonnie.text2sql.security.SqlSecurityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SQL 查询执行工具
 *
 * ReactAgent 调用此工具来执行 SQL 查询并返回结果。
 *
 * 安全机制:
 * 1. DML 检查 - 阻止 INSERT/UPDATE/DELETE/DROP 等危险操作
 * 2. Layer 2 验证 (SqlSecurityValidator) - 增强安全检查,阻止 UNION、时间盲注等
 * 3. 自动 LIMIT - 防止查询过多数据
 *
 * 调用流程:
 * 1. ReactAgent 决定需要执行查询
 * 2. 调用此 toolCallback
 * 3. 执行安全检查
 * 4. 通过 JdbcTemplate 执行查询
 * 5. 格式化结果返回
 *
 * 注意: 这是 SQL Agent 的最后一道防线,前面的工具(list_tables, get_schema)
 * 已经提供了表结构信息来帮助 AI 生成正确查询
 */
@Component
public class ExecuteQueryTool implements BiFunction<ExecuteQueryTool.Request, ToolContext, String> {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteQueryTool.class);

    /**
     * DML (Data Manipulation Language) 模式
     * 匹配以 INSERT, UPDATE, DELETE, DROP, CREATE, ALTER, TRUNCATE, REPLACE 开头的 SQL
     *
     * 注意: 只匹配语句开头,整个语句中的危险模式由 Layer 2 (SqlSecurityValidator) 检测
     */
    private static final Pattern DML_PATTERN = Pattern
        .compile("^\\s*(INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|TRUNCATE|REPLACE)\\s+", Pattern.CASE_INSENSITIVE);

    /** JDBC 模板,用于执行数据库查询 */
    private final JdbcTemplate jdbcTemplate;

    /** Layer 2: SQL 安全验证器 */
    private final SqlSecurityValidator sqlSecurityValidator;

    /** Layer 4: 审计日志记录器 */
    private final SecurityAuditLogger auditLogger;

    /**
     * 单次查询返回的最大行数
     * 可通过配置 sql-agent.max-results 修改
     */
    @Value("${sql-agent.max-results:10}")
    private int maxResults;

    /**
     * 构造函数
     *
     * @param jdbcTemplate 数据库操作模板
     * @param sqlSecurityValidator SQL 安全验证器
     * @param auditLogger 审计日志记录器
     */
    public ExecuteQueryTool(JdbcTemplate jdbcTemplate,
                            SqlSecurityValidator sqlSecurityValidator,
                            SecurityAuditLogger auditLogger) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlSecurityValidator = sqlSecurityValidator;
        this.auditLogger = auditLogger;
    }

    /**
     * 执行 SQL 查询
     *
     * 这是 ReactAgent 调用的主要方法,执行以下步骤:
     * 1. 安全检查: DML 语句检测
     * 2. 安全检查: Layer 2 增强验证
     * 3. 自动添加 LIMIT
     * 4. 执行查询
     * 5. 格式化结果
     *
     * @param request 包含 SQL 查询的请求
     * @param toolContext 工具上下文,包含会话信息
     * @return 格式化的查询结果或错误消息
     */
    @Override
    public String apply(Request request, ToolContext toolContext) {
        logger.info("========== Execute Query Tool Start ==========");
        logger.info("Query: {}", request.query());

        String query = request.query().trim();
        String threadId = getThreadId(toolContext);

        // ==================== 安全检查 1: DML 语句 ====================
        // 检查 SQL 是否以危险关键词开头
        // 这是基础检查,阻止明显的危险操作
        if (DML_PATTERN.matcher(query).find()) {
            String errorMsg = "Error: DML statements (INSERT, UPDATE, DELETE, DROP, etc.) are not allowed. "
                    + "This agent only supports SELECT queries for safety.";
            logger.warn(errorMsg);
            auditLogger.logExecutionBlocked(query, "DML statement detected", threadId);
            return errorMsg;
        }

        // ==================== 安全检查 2: Layer 2 增强验证 ====================
        // 使用 SqlSecurityValidator 进行更深入的检查
        // 包括: UNION、时间盲注、文件操作、系统表访问等
        SqlSecurityValidator.ValidationResult sqlValidation =
                sqlSecurityValidator.validateQuery(query);

        // Layer 2 检查失败
        if (!sqlValidation.passed()) {
            String errorMsg = "Error: Query blocked due to security policy. "
                    + String.join("; ", sqlValidation.violations());
            logger.warn("SQL validation blocked query: {}", sqlValidation.violations());
            auditLogger.logQueryBlocked(query, String.join("; ", sqlValidation.violations()), threadId);
            return errorMsg;
        }

        try {
            // ==================== 执行查询 ====================
            // 自动添加 LIMIT 防止查询过多数据
            String limitedQuery = addLimitIfNeeded(query);

            // 使用 JdbcTemplate 执行查询
            List<Map<String, Object>> results = jdbcTemplate.queryForList(limitedQuery);

            // 处理空结果
            if (results.isEmpty()) {
                logger.info("Query returned no results");
                return "Query executed successfully. No results found.";
            }

            // 格式化结果为可读表格
            String resultStr = formatResults(results);
            logger.info("Query returned {} rows", results.size());
            logger.info("========== Execute Query Tool End ==========");

            return resultStr;
        }
        catch (Exception e) {
            // 查询执行失败
            logger.error("Error executing query", e);
            return "Error executing query: " + e.getMessage()
                    + "\n\nPlease check your query syntax and try again. "
                    + "Use get_schema to verify table and column names.";
        }
    }

    /**
     * 从工具上下文获取会话ID
     * 用于审计日志追踪
     *
     * @param toolContext 工具上下文
     * @return 会话ID,如果获取不到则返回 "unknown"
     */
    private String getThreadId(ToolContext toolContext) {
        if (toolContext != null && toolContext.getContext() != null) {
            Object threadId = toolContext.getContext().get("threadId");
            if (threadId != null) {
                return threadId.toString();
            }
        }
        return "unknown";
    }

    /**
     * 如果查询没有 LIMIT 子句,自动添加
     *
     * 这是防止查询返回过多数据的保护措施
     * 可以通过配置 sql-agent.max-results 修改默认限制(默认10条)
     *
     * @param query 原始 SQL 查询
     * @return 添加了 LIMIT 的查询
     */
    private String addLimitIfNeeded(String query) {
        String lowerQuery = query.toLowerCase();

        // 检查是否已包含 LIMIT
        if (!lowerQuery.contains(" limit ") && !lowerQuery.contains("\nlimit ")) {
            // 移除末尾的分号
            if (query.endsWith(";")) {
                query = query.substring(0, query.length() - 1);
            }
            // 添加 LIMIT
            return query + " LIMIT " + maxResults;
        }
        return query;
    }

    /**
     * 格式化查询结果为可读的表格形式
     *
     * 输出格式:
     * <pre>
     * column1 | column2 | column3
     * -----------------------
     * value1 | value2 | value3
     * value4 | value5 | value6
     *
     * (2 row(s) returned)
     * </pre>
     *
     * @param results 查询结果列表
     * @return 格式化的字符串
     */
    private String formatResults(List<Map<String, Object>> results) {
        if (results.isEmpty()) {
            return "No results found.";
        }

        StringBuilder sb = new StringBuilder();

        // 获取列名
        List<String> columns = results.get(0).keySet().stream().toList();

        // 表头
        sb.append(String.join(" | ", columns)).append("\n");

        // 分隔线
        sb.append("-".repeat(columns.stream().mapToInt(String::length).sum() + (columns.size() - 1) * 3)).append("\n");

        // 数据行
        for (Map<String, Object> row : results) {
            String rowStr = columns.stream()
                .map(col -> row.get(col) == null ? "NULL" : String.valueOf(row.get(col)))
                .collect(Collectors.joining(" | "));
            sb.append(rowStr).append("\n");
        }

        // 行数统计
        sb.append("\n(").append(results.size()).append(" row(s) returned)");

        return sb.toString();
    }

    /**
     * 创建 Spring AI 的 ToolCallback
     * 用于将本工具注册到 ReactAgent
     *
     * @return ToolCallback 实例
     */
    public ToolCallback toolCallback() {
        return FunctionToolCallback.builder("execute_query", this)
            .description("Executes a SQL SELECT query against the database and returns the results. "
                    + "IMPORTANT: Only SELECT queries are allowed for safety. "
                    + "DML statements (INSERT, UPDATE, DELETE, DROP) will be rejected. "
                    + "Always use check_query to validate your query before execution. "
                    + "Results are limited to " + maxResults + " rows by default.")
            .inputType(Request.class)
            .build();
    }

    /**
     * 请求记录
     * 定义了 AI 调用此工具时需要提供的参数
     */
    @JsonClassDescription("Request to execute a SQL query")
    public record Request(
            @JsonProperty(value = "query", required = true)
            @JsonPropertyDescription("The SQL SELECT query to execute. Only SELECT statements are allowed.")
            String query) {
    }

}
