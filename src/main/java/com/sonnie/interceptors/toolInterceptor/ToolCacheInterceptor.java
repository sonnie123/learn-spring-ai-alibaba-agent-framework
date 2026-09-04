package com.sonnie.interceptors.toolInterceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;

public class ToolCacheInterceptor extends ToolInterceptor {
    private final long ttlMs;
    private final RedissonClient redissonClient;

    public ToolCacheInterceptor(long ttlMs, RedissonClient redissonClient) {
        this.ttlMs = ttlMs;
        this.redissonClient = redissonClient;
    }

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        // 检查缓存
        ToolCallResponse cache = checkCache(request);
        if (cache != null) {
            return ToolCallResponse.of(
                    request.getToolCallId(),
                    cache.getToolName(),
                    cache.getResult());
        }
        // 调用工具
        ToolCallResponse response = handler.call(request);
        // 缓存结果
        cache(request, response);
        return response;
    }

    @Override
    public String getName() {
        return "TOOL_CACHE_INTERCEPTOR";
    }

    private String generateCacheKey(ToolCallRequest request) {
        return "tool_cache:" + request.getToolName() + ":" + request.getArguments();
    }

    private ToolCallResponse checkCache(ToolCallRequest request) {
        // 检查缓存
        String cacheKey = generateCacheKey(request);
        RBucket<ToolCallResponse> bucket = redissonClient.getBucket(cacheKey);
        ToolCallResponse cached = bucket.get();
        if (cached != null) {
            System.out.println(">>命中缓存: 【" + cacheKey + "】");
            System.out.println(">>缓存结果: 【" + cached.getResult() + "】");
            return cached;
        }
        return null;
    }

    private void cache(ToolCallRequest request, ToolCallResponse response) {
        RBucket<ToolCallResponse> bucket = redissonClient.getBucket(generateCacheKey(request));
        bucket.set(response, Duration.ofMillis(ttlMs));
    }
}