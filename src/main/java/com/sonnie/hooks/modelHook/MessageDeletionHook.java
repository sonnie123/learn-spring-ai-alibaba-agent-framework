package com.sonnie.hooks.modelHook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.state.RemoveByHash;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@HookPositions({HookPosition.BEFORE_MODEL})
public class MessageDeletionHook extends ModelHook {

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        Optional<Object> messagesOpt = state.value("messages");
        if (messagesOpt.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        // 构建新的消息列表，保持原顺序
        List<Message> messages = (List<Message>) messagesOpt.get();
        List<Object> newMessages = new ArrayList<>();
        for (Message msg : messages) {
            // 根据条件决定保留或删除
            if (shouldKeep(msg)) {
                // 保留消息
                newMessages.add(msg);
            } else {
                // 标记删除
                newMessages.add(RemoveByHash.of(msg));
            }
        }
        return CompletableFuture.completedFuture(Map.of("messages", newMessages));
    }

    private boolean shouldKeep(Message message) {
        if (message instanceof UserMessage) {
            String text = ((UserMessage) message).getText();
            return !text.contains("3");
        }
        return true;
    }

    @Override
    public String getName() {
        return "MESSAGE_DELETION_HOOK";
    }
}
