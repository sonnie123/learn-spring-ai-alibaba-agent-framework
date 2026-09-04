---
name: weather
description: 查询指定地区的天气信息。当用户问"天气怎么样"、"今天天气"、"明天天气"时使用此技能获取天气数据。
---

# Weather Skill

当用户询问天气时：
1. 调用 `read_skill` 工具，参数 `skill_name="weather"` 激活本技能
2. 激活成功后调用 `get_weather` 工具，传入 `region` 参数（例如 "北京"、"上海"）
3. 根据工具返回结果向用户报告天气情况