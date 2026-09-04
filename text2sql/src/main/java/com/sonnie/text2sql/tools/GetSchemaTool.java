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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * 获取表结构工具
 *
 * ReactAgent 调用此工具获取指定表的详细信息,包括:
 * - 列名和数据类型
 * - 是否可为空
 * - 默认值
 * - 示例数据 (前3行)
 *
 * 在 SQL Agent 工作流中的位置:
 * 1. list_tables - 获取所有表名
 * 2. get_schema (本工具) - 获取表结构
 * 3. check_query - 验证 SQL 语法
 * 4. execute_query - 执行查询
 *
 * 为什么需要这个工具:
 * - AI 知道有哪些表后,还需要知道每个表有什么列
 * - 列名、数据类型等信息对于生成正确 SQL 至关重要
 * - 示例数据帮助 AI 理解数据格式
 *
 * 安全措施:
 * - 表名通过 sanitizeTableName() 进行白名单校验
 * - 防止 SQL 注入
 */
@Component
public class GetSchemaTool implements BiFunction<GetSchemaTool.Request, ToolContext, String> {

    private static final Logger logger = LoggerFactory.getLogger(GetSchemaTool.class);

    /**
     * 示例数据行数
     * 返回表的前 N 行数据,帮助 AI 理解数据格式
     */
    private static final int SAMPLE_ROWS = 3;

    /** JDBC 模板,用于执行数据库查询 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造函数
     *
     * @param jdbcTemplate 数据库操作模板
     */
    public GetSchemaTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 获取一个或多个表的结构信息
     *
     * 支持同时查询多个表,用逗号分隔表名。
     * 每个表的输出包括:
     * 1. 表名
     * 2. 所有列的详细信息
     * 3. 前3行示例数据
     *
     * @param request 包含表名的请求 (逗号分隔)
     * @param toolContext 工具上下文(未使用)
     * @return 表结构信息字符串
     */
    @Override
    public String apply(Request request, ToolContext toolContext) {
        logger.info("========== Get Schema Tool Start ==========");
        logger.info("Tables requested: {}", request.tables());

        try {
            // 解析并过滤空表名
            List<String> tableNames = Arrays.stream(request.tables().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

            // 无效输入检查
            if (tableNames.isEmpty()) {
                return "No table names provided. Please specify table names separated by commas.";
            }

            StringBuilder result = new StringBuilder();

            // 逐个获取表结构
            for (String tableName : tableNames) {
                result.append(getTableSchema(tableName));
                result.append("\n\n");
            }

            logger.info("========== Get Schema Tool End ==========");
            return result.toString().trim();
        }
        catch (Exception e) {
            logger.error("Error getting schema", e);
            return "Error getting schema: " + e.getMessage();
        }
    }

    /**
     * 获取单个表的详细结构
     *
     * 输出格式:
     * Table: users
     * Columns:
     * - id (bigint, NOT NULL)
     * - name (varchar(100), NOT NULL)
     * - email (varchar(255), NULL)
     *
     * [3 sample rows]
     *
     * @param tableName 表名
     * @return 表结构字符串
     */
    private String getTableSchema(String tableName) {
        StringBuilder sb = new StringBuilder();

        try {
            // 从 information_schema.COLUMNS 获取列信息
            // 使用 ANSI SQL 标准,兼容多种数据库
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                    "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT, "
                    + "CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE "
                    + "FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? "
                    + "ORDER BY ORDINAL_POSITION",
                    sanitizeTableName(tableName));

            // 表不存在或无列
            if (columns.isEmpty()) {
                sb.append("Table '").append(tableName).append("' not found or has no columns.\n");
                sb.append("Make sure the table exists by calling list_tables first.");
                return sb.toString();
            }

            // 输出表名
            sb.append("Table: ").append(tableName).append("\n");
            sb.append("Columns:\n");

            // 输出每个列的详细信息
            for (Map<String, Object> col : columns) {
                String colName = String.valueOf(col.get("COLUMN_NAME"));
                String dataType = String.valueOf(col.get("DATA_TYPE"));
                String nullable = "YES".equals(col.get("IS_NULLABLE")) ? "NULL" : "NOT NULL";
                String defaultVal = col.get("COLUMN_DEFAULT") != null
                    ? " DEFAULT " + col.get("COLUMN_DEFAULT") : "";

                // 构建类型字符串 (考虑长度、精度)
                String typeStr = buildTypeString(dataType,
                    col.get("CHARACTER_MAXIMUM_LENGTH"),
                    col.get("NUMERIC_PRECISION"),
                    col.get("NUMERIC_SCALE"));

                // 格式化输出: 列名 (类型, NULL约束 默认值)
                sb.append("- ").append(colName)
                    .append(" (").append(typeStr).append(", ").append(nullable).append(defaultVal).append(")\n");
            }

            sb.append("\n");

            // 获取示例数据
            List<Map<String, Object>> sampleRows = jdbcTemplate
                .queryForList("SELECT * FROM " + sanitizeTableName(tableName) + " LIMIT " + SAMPLE_ROWS);

            if (!sampleRows.isEmpty()) {
                sb.append("/* ").append(SAMPLE_ROWS).append(" rows from ").append(tableName).append(" table:\n");

                // 表头
                String header = String.join("\t", sampleRows.get(0).keySet());
                sb.append(header).append("\n");

                // 数据行
                for (Map<String, Object> row : sampleRows) {
                    String rowStr = row.values()
                        .stream()
                        .map(v -> v == null ? "NULL" : String.valueOf(v))
                        .collect(Collectors.joining("\t"));
                    sb.append(rowStr).append("\n");
                }
                sb.append("*/");
            }
        }
        catch (Exception e) {
            sb.append("Error getting schema for table '")
                .append(tableName)
                .append("': ")
                .append(e.getMessage())
                .append("\n");
            sb.append("Make sure the table exists by calling list_tables first.");
        }

        return sb.toString();
    }

    /**
     * 表名白名单校验
     *
     * 安全措施: 只允许字母、数字、下划线,且不能以数字开头
     * 这是防止 SQL 注入的第一道防线
     *
     * @param tableName 待校验的表名
     * @return 校验通过的表名
     * @throws IllegalArgumentException 表名格式不合法
     */
    private String sanitizeTableName(String tableName) {
        // 白名单正则: 只能包含字母、数字、下划线,且以字母或下划线开头
        if (!tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }
        return tableName;
    }

    /**
     * 构建数据类型字符串
     *
     * 根据数据类型决定是否需要显示长度/精度:
     * - varchar(255) - 字符类型需要长度
     * - decimal(10,2) - 数值类型需要 precision 和 scale
     * - int - 不需要长度
     *
     * @param dataType 基础数据类型
     * @param charMaxLength 字符最大长度 (varchar 的长度)
     * @param numericPrecision 数值精度 (总位数)
     * @param numericScale 数值刻度 (小数位数)
     * @return 格式化后的类型字符串
     */
    private String buildTypeString(String dataType, Object charMaxLength, Object numericPrecision, Object numericScale) {
        // 字符类型: varchar(255)
        if (charMaxLength != null) {
            return dataType + "(" + charMaxLength + ")";
        }
        // 数值类型带小数: decimal(10,2)
        else if (numericPrecision != null && numericScale != null) {
            return dataType + "(" + numericPrecision + "," + numericScale + ")";
        }
        // 数值类型无小数: decimal(10)
        else if (numericPrecision != null) {
            return dataType + "(" + numericPrecision + ")";
        }
        // 其他类型: int, datetime 等
        return dataType;
    }

    /**
     * 创建 Spring AI 的 ToolCallback
     *
     * @return ToolCallback 实例
     */
    public ToolCallback toolCallback() {
        return FunctionToolCallback.builder("get_schema", this)
            .description("Gets the schema (column names, types, nullable) and sample rows for specified tables. "
                    + "Use this tool to understand the structure of tables before writing queries. "
                    + "Input should be a comma-separated list of table names. "
                    + "Example: 'users, orders, products'")
            .inputType(Request.class)
            .build();
    }

    /**
     * 请求记录
     */
    @JsonClassDescription("Request to get schema for specified tables")
    public record Request(
            @JsonProperty(value = "tables", required = true)
            @JsonPropertyDescription("Comma-separated list of table names to get schema for. "
                    + "Example: 'users, orders, products'")
            String tables) {
    }
}
