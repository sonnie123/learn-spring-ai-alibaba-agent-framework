package com.sonnie.hooks.agentHook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;


@HookPositions({HookPosition.BEFORE_AGENT, HookPosition.AFTER_AGENT})
public class TimeConsumingStatisticsHook extends AgentHook {
    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        System.out.println(">>Agent开始执行<<");
        // 可以初始化资源、记录开始时间等
        return CompletableFuture.completedFuture(Map.of("start_time", System.currentTimeMillis()));
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
        System.out.println(">>Agent执行完成<<");
        // 可以清理资源、计算执行时间等
        Optional<Object> startTime = state.value("start_time");
        if (startTime.isPresent()) {
            long duration = System.currentTimeMillis() - (Long) startTime.get();
            System.out.println("执行耗时: 【" + duration + "】ms");
        }
        return CompletableFuture.completedFuture(Map.of());
    }


    @Override
    public String getName() {
        return "TIME_CONSUMING_STATISTICS_HOOK";
    }
}
