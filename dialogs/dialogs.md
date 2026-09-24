<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

## T0 · 第 1 轮

### 我

【会话守则】
- 不要 git push；不要修改 /Users/binwu/OOR/katas/commons-csv 里的任何东西。
- 每次回复我之后（包括停下等我回答时），把本轮对话完整追加到 without/dialogs/dialogs.md 末尾：标题"## T0 · 第 N 轮"，下面"### 我"原样贴出我的消息，"### Agent"贴出你回复我的全文（工具调用细节只写结论）。文件不存在就新建。
- 遇到构建或运行的坑，先自己排查修复，再往 without/guide.md 的「踩坑记录」追加一条：环节 · 现象（原始报错摘一行）→ 原因 → 解决。
- 环节结束时一次性 git commit（Conventional Commits；subject 说清意图，body 写为什么这样做以及结果数字），打指定 tag，然后告诉我：commit 哈希、tag、mvn test 结果（run/fail/error/skip）、下一环节是什么。

【本环节：T0 克隆 + 基线】
当前目录是我的智能体工作目录（它本身不是 git 仓库）：本机`/Users/binwu/OOR/my-lab/commons-csv-approved-scenarios-by-claude-code-2026-09-24`。
1. git clone /Users/binwu/OOR/katas/commons-csv without，进入 without，从当前 HEAD 新建分支 feature/required-headers。
2. 在 without 里运行一次完整的 mvn test，记下基线结果（run/fail/error/skip 和耗时）。如果依赖下载失败或很慢，自行解决（例如只在本仓库内配置镜像，不改我的全局 ~/.m2），并记入踩坑。
3. 新建 without/guide.md 骨架，只写这些标题：「0. 环境与基线」（填入 JDK、Maven、agent 名称与模型、基线 mvn test 结果）、「1. 隐含假设」、「2. 测试清单」、「3. 如何运行测试」、「4. 故障注入与失败原因」、「5. 踩坑记录」、「6. 环节与 tag」。
4. 新建 without/dialogs/dialogs.md。
5. 新增这些文件后，再跑一次 mvn test，确认构建仍然通过；不通过就修好，并记入踩坑。
6. 提交并打 tag tdd-t0-baseline。

### Agent

克隆和分支已就绪（master `66a83820`，分支 feature/required-headers）。基线 mvn test：924 run / 0 fail / 0 error / 11 skip，BUILD SUCCESS，约 5 s。环境：OpenJDK 21.0.11、Maven 3.9.16。依赖无下载问题，无踩坑。guide.md 骨架和本文件已建。踩到 1 个坑：新增 .md 后 mvn test 被 RAT 许可证检查拦截（UNAPPROVED 2），已给两个文件加 ASF 许可证头修复，记入 guide.md 踩坑。修复后重跑 mvn test、提交并打 tag tdd-t0-baseline，哈希与最终结果见本轮聊天回复。

## T1 · 第 1 轮

### 我

【会话守则】
- 当前目录 = 本仓库根（git） = `/Users/binwu/OOR/my-lab/commons-csv-approved-scenarios-by-claude-code-2026-09-24/without`。不要 git push；不要修改 /Users/binwu/OOR/katas/commons-csv。
- 每次回复我之后（包括停下等我回答时），把本轮对话完整追加到 dialogs/dialogs.md 末尾：标题"## <环节编号> · 第 N 轮"，下面"### 我"原样贴出我的消息，"### Agent"贴出你回复我的全文（工具调用细节只写结论）。
- 遇到构建或运行的坑，先自己排查修复，再往 guide.md 的「踩坑记录」追加一条：环节 · 现象（原始报错摘一行）→ 原因 → 解决。
- 环节结束时一次性 git commit（Conventional Commits；subject 说清意图，body 写为什么这样做以及结果数字），打指定 tag，然后告诉我：commit 哈希、tag、mvn test 结果、下一环节是什么。

【本环节：T1（TDD 红）】
需求（这就是你能拿到的全部需求）：
> 我在维护 Apache Commons CSV（本机棕地仓库）。我们对账时，上游 CSV 应当包含若干固定列。我想给 CSVFormat.Builder 加一个 setRequiredHeaders(String...)：解析时一旦发现表头缺少这些列，就在读第一条记录之前抛出一个清楚说明缺了哪些列的错误，而不是等业务代码调用 record.get("currency") 时才报 Mapping for currency not found。

规则：
- 不要向我提任何澄清问题。遇到歧义，自己做最合理的假设，每条假设编号（H1、H2……）写进 guide.md「1. 隐含假设」，写明理由，然后继续。
- 先读源码，弄清 CSVFormat.Builder、CSVFormat、CSVParser 的现有结构和测试风格，再动手。
- 只按传统 TDD 写 JUnit 测试，不使用 approved scenarios / approval testing / 快照式测试。

步骤：
1. 在 guide.md「2. 测试清单」列出你打算写的全部测试（编号 T01、T02……；每条一句话，写清要验证的行为以及它依据哪条假设）。
2. 在 src/test/java/org/apache/commons/csv/RequiredHeadersTest.java 里把清单上的测试全部写出来，风格与本项目现有测试保持一致。
3. 只在 src/main 里加让测试能编译的最小签名桩（方法存在，但不实现行为）。
4. 运行这个测试类，确认失败的是"行为断言"，而不是编译错误；在 dialogs 里报告哪些测试红、哪些意外地绿，并解释意外绿的原因。
5. 提交并打 tag tdd-t1-red。

### Agent

T1（TDD 红）完成。

源码结论：Builder 属性 → `CSVFormat(Builder)` 构造器复制 → `validate()` 做配置校验；`CSVParser` 构造器里 `createHeaders()` 解析表头（首行或显式表头），缺名/重名表头抛 `IllegalArgumentException`；输入非法用 `CSVException`（IOException 子类）。测试风格：JUnit 5、包私有类与方法、`assertThrows` + `getMessage()` 精确比对、`StringReader` 输入。

假设 H1–H13 已写入 guide.md「1. 隐含假设」。要点：
- H1 缺列抛 `CSVException`；H2 信息固定为 `Missing required header name(s) [缺失列…] in header [实际表头…]`，一次列全、按声明顺序；H3 在创建 parser 时抛（读第一条记录之前）。
- H4 检查解析器实际使用的表头（显式表头优先于被跳过的首行）；H5 遵循 `ignoreHeaderCase`；H12 不额外 trim，遵循 `setTrim`。
- H6 设了必需列却没配置表头 → `get()` 抛 `IllegalArgumentException`；H9 必需列名 null/空白 → 同样在 `get()` 抛。
- H7 空输入 = 全部缺失；H8 无参/null 关闭检查，getter 永不返回 null，双向防御拷贝；H10 多余列与顺序无关；H11 `builder()`/`equals`/`hashCode` 计入该属性；H13 打印忽略该设置。

测试清单 T01–T17 已写入 guide.md「2. 测试清单」，全部实现在 `RequiredHeadersTest.java`（T11 参数化 2 例，共 18 个用例）。

桩：`CSVFormat.Builder#setRequiredHeaders(String...)` 只 `return this`（不保存值），`CSVFormat#getRequiredHeaders()` 恒返回空数组；未改 `CSVParser`。

运行 `mvn test -Dtest=RequiredHeadersTest`：18 run / 13 fail / 0 error。编译通过，13 个失败全部是行为断言失败（"Expected CSVException/IllegalArgumentException to be thrown, but nothing was thrown" 或 "array lengths differ"），没有编译错误。

红（13）：T01、T02、T04、T05、T07、T08、T09、T10、T11×2、T13、T14、T16。

绿（5）：T03、T06、T12、T15、T17。原因：这 5 条都是"不应抛异常/行为照旧"的正向用例。桩什么也不检查，解析照常成功，所以断言通过；T12 断言 getter 返回空数组，而桩恰好恒返回空数组；T17 断言打印不受影响，桩本来就不影响打印。它们不是测试写错，而是回归护栏——防止将来实现过严（如误伤大小写不敏感、trim、多余列、关闭检查、打印）。它们在红阶段"绿"是预期内的，不能证明行为已实现，真正驱动实现的是那 13 条红测试。

全量 `mvn test`：942 run / 13 fail / 0 error / 11 skip，BUILD FAILURE（预期；失败仅在 RequiredHeadersTest，原有 924 条全部通过）。本环节无构建踩坑。

提交并打 tag `tdd-t1-red`，commit 哈希见本轮聊天回复。下一环节：T2（TDD 绿）——实现检查让 18 条全绿，且不破坏原有 924 条。
