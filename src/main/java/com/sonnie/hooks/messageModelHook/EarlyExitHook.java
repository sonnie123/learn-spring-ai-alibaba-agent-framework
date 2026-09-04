package com.sonnie.hooks.messageModelHook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

@HookPositions({HookPosition.BEFORE_MODEL})
public class EarlyExitHook extends MessagesModelHook {

    @Override
    public String getName() {
        return "EARLY_EXIT_HOOK";
    }

    @Override
    public List<JumpTo> canJumpTo() {
        return List.of(JumpTo.end);
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        // 检查退出条件，如果满足则提前退出
        if (shouldExit(previousMessages)) {
            List<Message> list = List.of(AssistantMessage.builder()
                    .content("风险发言，请重新描述。")
                    .build());
            return new AgentCommand(JumpTo.end, list);
        }
        return new AgentCommand(previousMessages);
    }

    private boolean shouldExit(List<Message> messages) {
        for (Message message : messages) {
            if (message instanceof UserMessage) {
                String text = ((UserMessage) message).getText();
                if (text.contains("缝纫机")) {
                    return true;
                }
            }
        }
        return false;
    }
}
