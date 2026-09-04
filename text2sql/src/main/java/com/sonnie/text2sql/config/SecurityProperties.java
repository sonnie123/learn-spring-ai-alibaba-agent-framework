package com.sonnie.text2sql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 安全防护配置属性类
 *
 * 通过 application.yaml 中的 sql-agent.security.* 配置项来控制安全防护行为。
 *
 * 配置示例 (application.yaml):
 * <pre>
 * sql-agent:
 *   security:
 *     enabled: true                    # 总开关
 *     strictMode: false                # 是否严格模式(阻止而非警告)
 *     enableInputValidation: true      # Layer 1: 输入验证开关
 *     enableQueryValidation: true       # Layer 2: SQL验证开关
 *     enableResponseValidation: true    # Layer 3: 响应验证开关
 *     enableAuditLogging: true          # Layer 4: 审计日志开关
 *     maxInputLength: 10000            # 最大输入长度
 *     maxAuditTrailSize: 10000         # 审计日志缓存大小
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "sql-agent.security")
public class SecurityProperties {

    /**
     * 安全防护总开关
     * - true: 启用所有已开启的安全检查
     * - false: 禁用所有安全检查(不推荐,仅用于调试)
     */
    private boolean enabled = true;

    /**
     * 严格模式开关
     * - false(默认): 检测到威胁时仅记录日志并警告
     * - true: 检测到威胁时直接阻止请求
     */
    private boolean strictMode = false;

    /**
     * Layer 1: 输入验证开关
     * 启用后,用户输入会经过 Prompt 注入检测
     * 阻止 "Ignore previous instructions" 等攻击话术
     */
    private boolean enableInputValidation = true;

    /**
     * Layer 2: SQL 验证开关
     * 启用后,AI 生成的 SQL 会经过增强安全检查
     * 阻止 UNION、时间盲注、文件操作等危险 SQL
     */
    private boolean enableQueryValidation = true;

    /**
     * Layer 3: 响应验证开关
     * 启用后,AI 响应会经过敏感信息检测
     * 防止密码、API密钥等敏感数据泄露
     */
    private boolean enableResponseValidation = true;

    /**
     * Layer 4: 审计日志开关
     * 启用后,所有安全事件会记录到 SECURITY_AUDIT logger
     * 用于安全审计和事件追溯
     */
    private boolean enableAuditLogging = true;

    /**
     * 用户输入最大长度限制
     * 超过此长度的输入会被拒绝,防止缓冲区溢出或资源耗尽
     */
    private int maxInputLength = 10000;

    /**
     * 审计日志缓存最大条数
     * 内存中缓存的安全事件数量,超过后旧事件会被丢弃
     */
    private int maxAuditTrailSize = 10000;

    // ==================== Getter/Setter 方法 ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    public boolean isEnableInputValidation() {
        return enableInputValidation;
    }

    public void setEnableInputValidation(boolean enableInputValidation) {
        this.enableInputValidation = enableInputValidation;
    }

    public boolean isEnableQueryValidation() {
        return enableQueryValidation;
    }

    public void setEnableQueryValidation(boolean enableQueryValidation) {
        this.enableQueryValidation = enableQueryValidation;
    }

    public boolean isEnableResponseValidation() {
        return enableResponseValidation;
    }

    public void setEnableResponseValidation(boolean enableResponseValidation) {
        this.enableResponseValidation = enableResponseValidation;
    }

    public boolean isEnableAuditLogging() {
        return enableAuditLogging;
    }

    public void setEnableAuditLogging(boolean enableAuditLogging) {
        this.enableAuditLogging = enableAuditLogging;
    }

    public int getMaxInputLength() {
        return maxInputLength;
    }

    public void setMaxInputLength(int maxInputLength) {
        this.maxInputLength = maxInputLength;
    }

    public int getMaxAuditTrailSize() {
        return maxAuditTrailSize;
    }

    public void setMaxAuditTrailSize(int maxAuditTrailSize) {
        this.maxAuditTrailSize = maxAuditTrailSize;
    }
}
