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

import java.util.List;
import java.util.function.BiFunction;

/**
 * 列出数据库表工具
 *
 * ReactAgent 调用此工具获取当前数据库中所有可用的表名。
 *
 * 在 SQL Agent 工作流中的位置:
 * 1. list_tables (本工具) - 获取所有表名
 * 2. get_schema - 获取表结构
 * 3. check_query - 验证 SQL 语法
 * 4. execute_query - 执行查询
 *
 * 技术实现:
 * - 使用 ANSI SQL 标准的 information_schema 查询
 * - TABLE_TYPE = 'BASE TABLE' 排除视图 (views)
 * - 支持 MySQL, PostgreSQL, SQL Server 等主流数据库
 *
 * 为什么需要这个工具:
 * - AI 不能自己臆测数据库中有什么表
 * - 必须先通过此工具了解可用表,才能正确生成 SQL
 */
@Component
public class ListTablesTool implements BiFunction<ListTablesTool.Request, ToolContext, String> {

    private static final Logger logger = LoggerFactory.getLogger(ListTablesTool.class);

    /** JDBC 模板,用于执行数据库查询 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造函数
     *
     * @param jdbcTemplate 数据库操作模板
     */
    public ListTablesTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 获取所有数据库表名
     *
     * 执行流程:
     * 1. 查询 information_schema.TABLES 获取当前数据库的所有表
     * 2. 过滤只保留 BASE TABLE (排除视图)
     * 3. 返回逗号分隔的表名列表
     *
     * SQL 查询说明:
     * - TABLE_SCHEMA = DATABASE() 表示当前数据库
     * - TABLE_TYPE = 'BASE TABLE' 过滤掉视图
     * - ORDER BY TABLE_NAME 保证结果有序
     *
     * @param request 请求对象(此工具不需要参数)
     * @param toolContext 工具上下文(未使用)
     * @return 逗号分隔的表名列表,如 "users, orders, products"
     */
    @Override
    public String apply(Request request, ToolContext toolContext) {
        logger.info("========== List Tables Tool Start ==========");

        try {
            // 查询当前数据库中的所有用户表
            // 使用 information_schema (ANSI SQL 标准),兼容多种数据库
            List<String> tables = jdbcTemplate.queryForList(
                    "SELECT TABLE_NAME FROM information_schema.TABLES "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' "
                    + "ORDER BY TABLE_NAME",
                    String.class);

            // 无表时返回友好消息
            if (tables.isEmpty()) {
                logger.info("No tables found in the database");
                return "No tables found in the database.";
            }

            // 格式化为逗号分隔的字符串
            String result = String.join(", ", tables);
            logger.info("Found {} tables: {}", tables.size(), result);
            logger.info("========== List Tables Tool End ==========");

            return result;
        }
        catch (Exception e) {
            // 查询失败
            logger.error("Error listing tables", e);
            return "Error listing tables: " + e.getMessage();
        }
    }

    /**
     * 创建 Spring AI 的 ToolCallback
     *
     * @return ToolCallback 实例
     */
    public ToolCallback toolCallback() {
        return FunctionToolCallback.builder("list_tables", this)
            .description("Lists all available tables in the database. "
                    + "Use this tool first to understand what tables are available before querying. "
                    + "Returns a comma-separated list of table names.")
            .inputType(Request.class)
            .build();
    }

    /**
     * 请求记录
     *
     * 注意: 此工具不需要实际参数,但 Spring AI 要求提供 Request 类型
     * 传递时使用空字符串即可
     */
    @JsonClassDescription("Request to list all database tables")
    public record Request(
            @JsonProperty(value = "dummy", required = false)
            @JsonPropertyDescription("Dummy parameter, not used. Just pass an empty string.")
            String dummy) {
    }
}
