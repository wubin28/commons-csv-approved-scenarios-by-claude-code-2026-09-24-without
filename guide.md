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

# guide

## 0. 环境与基线

- JDK：OpenJDK 21.0.11 LTS
- Maven：3.9.16
- Agent：Claude Code，模型 Sonnet 5（claude-sonnet-5）
- 分支：feature/required-headers（从 master `66a83820` 新建）
- 基线 `mvn test`：Tests run 924，Failures 0，Errors 0，Skipped 11，BUILD SUCCESS，耗时约 5 s

## 1. 隐含假设

需求只有一段话，以下为 T1 自行做出的假设（不向需求方澄清）：

- H1 · 异常类型用 `CSVException`（`IOException` 子类）。理由：缺列是"输入数据不合格"，`CSVException` 自 1.12 起就是"invalid input"的专用异常；`parse()` 本来就声明 `throws IOException`，调用方现有 catch 无需改动。备选是 `IllegalArgumentException`（解析器对缺名/重名表头用它），但那更像"调用方传参错误"。
- H2 · 错误信息一次列出**全部**缺失列，按 `setRequiredHeaders` 声明顺序，并附实际表头，格式固定为 `Missing required header name(s) [currency, amount] in header [id, note]`。理由：对账排查时一眼看到缺什么、实际有什么；一次报全避免"修一列再报一列"。
- H3 · 检查发生在创建 `CSVParser` 时（`CSVFormat.parse(...)` / `CSVParser.builder().get()` 抛出），即读第一条数据记录之前。理由：需求原话"在读第一条记录之前"；表头本就在构造器 `createHeaders()` 里解析。
- H4 · 检查对象是"解析器实际使用的表头"：`setHeader()` 时是文件首行；`setHeader("a","b")` 显式表头时是显式表头（即使 `setSkipHeaderRecord(true)` 跳过的首行里有该列也不算）。理由：`record.get(name)` 按的就是这份映射，检查要与它一致才能消除 "Mapping for currency not found"。
- H5 · 名称比较遵循 `ignoreHeaderCase`：默认大小写敏感，开启后不敏感。理由：与 `record.get(name)` 的匹配规则一致。
- H6 · 设置了必需列却完全没有配置表头（`getHeader() == null`）是配置错误，在 `Builder.get()` 抛 `IllegalArgumentException`。理由：这种格式每次解析必然失败，越早暴露越好；与 `validate()` 现有的配置校验风格一致。
- H7 · 输入为空（`setHeader()` 但没有任何行）视为表头为空，所有必需列都缺失，照样抛 H1 的异常。理由：空文件对对账来说同样是上游不合格。
- H8 · `setRequiredHeaders()` 无参或传 `null` 表示关闭检查（也可清除之前的设置），默认关闭；`getRequiredHeaders()` 永不返回 `null`，关闭时返回空数组；设置与读取都做防御性拷贝（与 `setHeader` / `getHeader` 一致）。
- H9 · 必需列名中出现 `null`、空串或空白串，在 `Builder.get()` 抛 `IllegalArgumentException`。理由：这样的名字永远无法满足，属于配置错误。
- H10 · 只要求"包含"：多余列、列顺序不同都不报错。
- H11 · `requiredHeaders` 是 `CSVFormat` 的一部分：`format.builder()` 会复制它，`equals` / `hashCode` 计入它。理由：与其他所有 Builder 属性一致，否则 `copy()`（解析器内部就调用）会把设置丢掉。
- H12 · 不额外做空白规整：比较的是解析后表头值；开启 `setTrim(true)` 时表头已被去空格因而匹配，未开启时 `" currency"` ≠ `"currency"`。理由：与 `record.get(name)` 行为一致，不引入第二套规则。
- H13 · 打印（`CSVPrinter`）忽略该设置。理由：需求只针对解析；与 `setAllowMissingColumnNames` 等"仅解析用"属性一致。

## 2. 测试清单

全部位于 `src/test/java/org/apache/commons/csv/RequiredHeadersTest.java`（纯 JUnit 5 断言，无 approval/快照）：

- T01 · 首行表头缺一列时 `parse()` 本身即抛 `CSVException`，信息为 `... [currency] in header [id, amount]`。（H1、H2、H3）
- T02 · 缺多列时一次全部列出，顺序同声明顺序。（H2）
- T03 · 必需列齐全、另有多余列且顺序打乱时正常解析，`get("currency")` 可用。（H10）
- T04 · 显式 `setHeader("id","amount")` 缺 `currency` 同样抛出。（H4）
- T05 · 显式表头 + 跳过首行时，按显式表头检查，跳过行里有该列也不算。（H4）
- T06 · `ignoreHeaderCase(true)` 时 `ID,Currency,AMOUNT` 满足 `id,currency,amount`。（H5）
- T07 · 默认大小写敏感，`ID,Currency` 不满足 `id,currency`，并报这两列缺失。（H5）
- T08 · 空输入报所有必需列缺失，实际表头为 `[]`。（H7）
- T09 · 未配置表头却设必需列，`get()` 抛 `IllegalArgumentException`。（H6）
- T10 · 必需列名含 `null`，`get()` 抛 `IllegalArgumentException`。（H9）
- T11 · 必需列名含 `""` 或 `" "`（参数化两例），`get()` 抛 `IllegalArgumentException`。（H9）
- T12 · 无参 / `null` 关闭检查（并清除之前的值），getter 返回空数组，解析正常。（H8）
- T13 · setter 与 getter 都是防御性拷贝。（H8）
- T14 · `builder()` 复制该属性；`equals`/`hashCode` 计入该属性。（H11）
- T15 · `setTrim(true)` 时 `" id , currency "` 满足要求。（H12）
- T16 · 未 trim 时 `" currency"` 不满足，信息里原样显示实际表头。（H12）
- T17 · 打印忽略必需列设置，照常输出表头与记录。（H13）

## 3. 如何运行测试

- 只跑本特性：`mvn test -Dtest=RequiredHeadersTest -Dsurefire.failIfNoSpecifiedTests=false`
- 全量：`mvn test`（含 apache-rat 许可证检查，新增文件需带 ASF 头）
- 项目全部检查：`mvn`（pom 的 defaultGoal：`clean verify apache-rat:check japicmp:cmp spotbugs:check pmd:check pmd:cpd-check javadoc:javadoc checkstyle:check`），首次需联网下载打包/静态检查插件

## 4. 故障注入与失败原因

## 5. 踩坑记录

- T0 · 新增 guide.md、dialogs/dialogs.md 后 `mvn test` 失败：`[ERROR] Unexpected count for UNAPPROVED, limit is [0,0].  Count: 2` → apache-rat-plugin 的 rat-check 在 test 之前的阶段运行，要求每个文件带 ASF 许可证头，新增的两个 .md 没有 → 在两个文件顶部加 ASF 许可证头（HTML 注释，渲染不可见），不改 pom.xml；后续新增文件同理需带头，dialogs.md 只往末尾追加。
- T2 · 首次跑项目全部检查 `mvn`（defaultGoal）失败于打包阶段：`Could not transfer artifact org.apache.commons:commons-lang3:jar:3.18.0 from/to central (https://repo.maven.apache.org/maven2): Remote host terminated the handshake` → `mvn test` 用不到 jar/spotbugs/pmd 等插件，本地仓库里没有它们的依赖，首次需下载，与 Maven Central 的 TLS 握手偶发中断（网络抖动，非代码问题）→ 原样重跑一次 `mvn` 即下载成功，BUILD SUCCESS；未改 pom、未配镜像。

## 6. 环节与 tag

- T0 克隆 + 基线 · tag `tdd-t0-baseline`：924 run / 0 fail / 0 error / 11 skip，约 5 s。
- T1 TDD 红 · tag `tdd-t1-red`：RequiredHeadersTest 18 run / 13 fail / 0 error（全是断言失败，无编译错误）/ 5 绿；全量 942 run / 13 fail / 0 error / 11 skip，BUILD FAILURE（预期），约 5.5 s。
- T2 TDD 绿 · tag `tdd-t2-green`：RequiredHeadersTest 18 run / 0 fail / 0 error；全量 `mvn test` 942 run / 0 fail / 0 error / 11 skip，BUILD SUCCESS，约 5.4 s（对比基线 924：+18 新用例，原有 924 全绿，skip 不变）；项目全部检查 `mvn`（defaultGoal）BUILD SUCCESS，checkstyle 0、spotbugs 0，PMD/CPD、japicmp、javadoc、jacoco 均通过。未改测试。
- T3 TDD 重构 · tag `tdd-t3-refactor`：4 项不改行为的重构（测试提取 `assertMissingHeaders` 辅助方法、T12 局部变量改名、`CSVFormat.equals` 断行对齐、`CSVParser.checkRequiredHeaders` 局部变量改名），每步后 RequiredHeadersTest 18 run / 0 fail / 0 error；全量 `mvn test` 942 run / 0 fail / 0 error / 11 skip，BUILD SUCCESS，约 6 s，与 T2 一致；`checkstyle:check` 无违规。测试方法名未改，测试清单无需更新。
