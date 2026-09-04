package com.sonnie.controller;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent")
public class TestAgentController {

    private final ReactAgent sonnieAgent;

    @GetMapping("/chat")
    public String chat(@RequestParam String message) throws GraphRunnerException {
        AssistantMessage response = sonnieAgent.call(message);
        return response.getText();
    }

    @GetMapping("/chat/{threadId}")
    public String chatWithMemory(@PathVariable String threadId, @RequestParam String message) throws GraphRunnerException {
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        AssistantMessage response = sonnieAgent.call(message, config);
        return response.getText();
    }
}
