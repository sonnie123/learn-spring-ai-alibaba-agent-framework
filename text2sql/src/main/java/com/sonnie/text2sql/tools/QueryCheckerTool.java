package com.sonnie.text2sql.tools;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.function.BiFunction;

/**
 * SQL 查询语法检查工具
 *
 * ReactAgent 在执行 SQL 之前调用此工具来验证 SQL 语法的正确性。
 *
 * 工作原理:
 * - 使用 LLM (大语言模型) 来分析 SQL 查询
 * - 检查常见语法错误、列名/表名错误、潜在问题等
 * - 这是对 SqlSecurityValidator (Layer 2) 的补充 - LLM 可以发现规则无法覆盖的问题
 *
 * 与其他工具的关系:
 * - list_tables -> get_schema -> check_query -> execute_query
 * - check_query 是倒数第二关,发现的问题可以在 execute_query 之前修复
 *
 * 注意:
 * - 此工具使用 LLM 进行检查,结果可能不完全可靠
 * - 真正的安全防护在 Layer 2 (SqlSecurityValidator) 和 ExecuteQueryTool
 */
@Component
public class QueryCheckerTool implements BiFunction<QueryCheckerTool.Request, ToolContext, String> {

    private static final Logger logger = LoggerFactory.getLogger(QueryCheckerTool.class);

    /** LLM 模型,用于分析 SQL 语法 */
    private final MiniMaxChatModel chatModel;

    /**
     * SQL 检查提示词模板
     *
     * 指导 LLM 如何检查 SQL:
     * 1. 语法错误
     * 2. 列名/表名错误(如果有上下文)
     * 3. 字符串值缺少引号
     * 4. JOIN 条件错误
     * 5. GROUP BY 问题
     * 6. SQL 注入漏洞
     *
     * 返回格式:
     * - 正确: "VALID: The query appears to be correct."
     * - 有问题: "ISSUES FOUND:" + 问题列表
     */
    private static final String CHECK_PROMPT_TEMPLATE = """
            You are a SQL query validator. Check the following SQL query for common mistakes:

            ```sql
            %s
            ```

            Check for:
            1. Syntax errors
            2. Incorrect column or table names (if context is provided)
            3. Missing quotes around string values
            4. Incorrect JOIN conditions
            5. GROUP BY clause issues
            6. Any potential SQL injection vulnerabilities

            If the query looks correct, respond with exactly:
            "VALID: The query appears to be correct."

            If there are issues, respond with:
            "ISSUES FOUND:" followed by a numbered list of problems and suggested fixes.

            Keep your response concise.
            """;

    /**
     * 构造函数
     *
     * @param chatModel LLM 模型,用于 SQL 语法检查
     */
    public QueryCheckerTool(MiniMaxChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 检查 SQL 查询的语法和潜在问题
     *
     * 调用流程:
     * 1. 将 SQL 填入提示词模板
     * 2. 调用 LLM 进行分析
     * 3. 返回 LLM 的检查结果
     *
     * 返回值示例:
     * - "VALID: The query appears to be correct."
     * - "ISSUES FOUND:\n1. Table 'users' does not have a column named 'age'. Did you mean 'ages'?"
     *
     * @param request 包含待检查 SQL 的请求
     * @param toolContext 工具上下文(未使用)
     * @return LLM 的检查结果
     */
    @Override
    public String apply(Request request, ToolContext toolContext) {
        logger.info("========== Query Checker Tool Start ==========");
        logger.info("Query to check: {}", request.query());

        try {
            // 构建提示词
            String promptText = String.format(CHECK_PROMPT_TEMPLATE, request.query());
            Prompt prompt = new Prompt(promptText);

            // 调用 LLM 进行检查
            String result = chatModel.call(prompt).getResult().getOutput().getText();

            logger.info("Check result: {}", result);
            logger.info("========== Query Checker Tool End ==========");

            return result;
        }
        catch (Exception e) {
            // LLM 调用失败
            logger.error("Error checking query", e);
            return "Error checking query: " + e.getMessage();
        }
    }

    /**
     * 创建 Spring AI 的 ToolCallback
     *
     * @return ToolCallback 实例
     */
    public ToolCallback toolCallback() {
        return FunctionToolCallback.builder("check_query", this)
            .description("Validates a SQL query for common mistakes before execution. "
                    + "Use this tool to double-check your query before running it with execute_query. "
                    + "The tool will identify syntax errors, potential issues, and suggest fixes.")
            .inputType(Request.class)
            .build();
    }

    /**
     * 请求记录
     *
     * @param query 待检查的 SQL 查询
     */
    @JsonClassDescription("Request to check a SQL query for errors")
    public record Request(
            @JsonProperty(value = "query", required = true)
            @JsonPropertyDescription("The SQL query to validate for common mistakes")
            String query) {
    }
}