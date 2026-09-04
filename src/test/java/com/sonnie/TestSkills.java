package com.sonnie;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.extension.tools.filesystem.EditFileTool;
import com.alibaba.cloud.ai.graph.agent.extension.tools.filesystem.WriteFileTool;
import com.alibaba.cloud.ai.graph.agent.hook.shelltool.ShellToolAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.tools.ShellTool2;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.sonnie.tools.MyListFilesTool;
import com.sonnie.tools.PythonTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

@SpringBootTest
public class TestSkills {
    @Test
    public void testFileSystemSkillRegistry(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
        FileSystemSkillRegistry skillRegistry = FileSystemSkillRegistry.builder()
                .projectSkillsDirectory("src/main/resources/.agents/skills")
                .build();
        SkillsAgentHook skillsAgentHook = SkillsAgentHook.builder()
                .skillRegistry(skillRegistry)
                .build();
        ReactAgent agent = ReactAgent.builder()
                .name("skills-agent")
                .model(chatModel)
                .saver(new MemorySaver())
                .hooks(skillsAgentHook)
                .build();

        System.out.println(agent.call("请介绍你有哪些技能").getText());
    }

    @Test
    public void testClasspathSkillRegistry(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
        ClasspathSkillRegistry skillRegistry = ClasspathSkillRegistry.builder()
                .classpathPath(".agents/skills")
                .build();
        SkillsAgentHook skillsAgentHook = SkillsAgentHook.builder()
                .skillRegistry(skillRegistry)
                .build();
        ReactAgent agent = ReactAgent.builder()
                .name("skills-agent")
                .model(chatModel)
                .saver(new MemorySaver())
                .hooks(skillsAgentHook)
                .build();

        System.out.println(agent.call("请介绍你有哪些技能").getText());
    }

    @Test
    public void testSkills(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
// 1. 技能注册表：从 classpath:skills 加载（如 src/main/resources/skills/）
        SkillRegistry registry = ClasspathSkillRegistry.builder()
                .classpathPath(".agents/skills")
                .build();

// 2. Skills Hook：注册 read_skill 工具并注入技能列表到系统提示
        SkillsAgentHook skillsHook = SkillsAgentHook.builder()
                .skillRegistry(registry)
                .build();

// 3. Shell Hook：提供 Shell 命令执行（工作目录可指定，如当前工程目录）
        ShellToolAgentHook shellHook = ShellToolAgentHook.builder()
                .shellTool2(ShellTool2.builder(System.getProperty("user.dir")).build())
                .build();

// 4. 构建 Agent：同时挂载 Skills Hook、Shell Hook 和 Python 工具
        ReactAgent agent = ReactAgent.builder()
                .name("skills-integration-agent")
                .model(chatModel)
                .saver(new MemorySaver())
                .tools(
                        PythonTool.createPythonToolCallback(PythonTool.DESCRIPTION),
                        MyListFilesTool.createListFilesToolCallback(MyListFilesTool.DESCRIPTION),
                        WriteFileTool.createWriteFileToolCallback(WriteFileTool.DESCRIPTION),
                        EditFileTool.createEditFileToolCallback(EditFileTool.DESCRIPTION)
                )
                .hooks(List.of(skillsHook, shellHook))
                .enableLogging(true)
                .build();

        System.out.println(agent.call("请从F:\\file\\learn\\projects\\JAVA\\learn-spring-ai-alibaba-agent-framework\\src\\main\\resources\\Nginx 安装.pdf文件中提取关键信息。").getText());
    }

    @Test
    public void testGroupedTools(@Autowired MiniMaxChatModel chatModel) throws GraphRunnerException {
        SkillRegistry registry = ClasspathSkillRegistry.builder()
                .classpathPath(".agents/skills")
                .build();
        SkillsAgentHook skillsHook = SkillsAgentHook.builder()
                .skillRegistry(registry)
                .autoReload(true)
//                渐进式工具 Tool 披露，实现按需暴露，激活后该技能的工具在会话后续轮次中仍可用
                .groupedTools(Map.of(
                        "weather", List.of(FunctionToolCallback
                                .builder("get_weather", new TestTools.WeatherFunction())
                                .description("根据指定区域获取天气信息")
                                .inputType(TestTools.WeatherFunction.WeatherInput.class)
                                .build())
                ))
                .build();
        ShellToolAgentHook shellHook = ShellToolAgentHook.builder()
                .shellTool2(ShellTool2.builder(System.getProperty("user.dir")).build())
                .build();
        ReactAgent agent = ReactAgent.builder()
                .name("skills-agent")
                .model(chatModel)
                .saver(new MemorySaver())
                .tools(
//                        这个地方设置的工具无论用不用的到都会占用上下文
                        PythonTool.createPythonToolCallback(PythonTool.DESCRIPTION),
                        MyListFilesTool.createListFilesToolCallback(MyListFilesTool.DESCRIPTION),
                        WriteFileTool.createWriteFileToolCallback(WriteFileTool.DESCRIPTION),
                        EditFileTool.createEditFileToolCallback(EditFileTool.DESCRIPTION)
                )
                .hooks(List.of(skillsHook, shellHook))
                .enableLogging(true)
                .build();

        System.out.println(agent.call("北京今天天气怎么样？").getText());
    }
}
