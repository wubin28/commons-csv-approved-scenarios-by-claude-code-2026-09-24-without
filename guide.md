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
- 最终全量 `mvn test`：Tests run 942，Failures 0，Errors 0，Skipped 11，BUILD SUCCESS；比基线多 18 次执行（17 个测试方法，其中 T11 参数化执行 2 次），原有测试与跳过数不变。

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

最终覆盖核对：H1→T01；H2→T01、T02；H3→T01；H4→T04、T05；H5→T06、T07；H6→T09；H7→T08；H8→T12、T13；H9→T10、T11；H10→T03；H11→T14；H12→T15、T16；H13→T17。T01–T17 均被对应的故障注入打红，但以下子句只有部分直接覆盖：H1 断言 `CSVException` 类型，未另断言它继承 `IOException`；H3 的 T01 只证实 `CSVFormat.parse(...)` 在读记录前抛错，未直接调用 `CSVParser.builder().get()`；H8 的 T12 测了清除后 getter 和解析，未单独断言从未设置过时的 getter；H11 的 T14 检查复制、相等与相等对象的哈希一致，不能单凭该断言证明不同 `requiredHeaders` 必有不同哈希。其余子句有直接断言；这些边界不应被理解为完整证明。

## 2. 测试清单

全部位于 `src/test/java/org/apache/commons/csv/RequiredHeadersTest.java`（纯 JUnit 5 断言，无 approval/快照）。17 个方法、18 次执行；T11 由 `@ValueSource` 执行两次。

| 编号 | 测试方法名 | 验证的行为 | 依据的假设 |
| --- | --- | --- | --- |
| T01 | `testMissingColumnFailsBeforeFirstRecord` | 首行缺 `currency` 时 `parse()` 即抛 `CSVException`，信息含缺列及实际表头 | H1、H2、H3 |
| T02 | `testMultipleMissingColumnsReportedInDeclaredOrder` | 一次报出所有缺列，按声明顺序 | H2 |
| T03 | `testAllPresentWithExtraColumnsInAnyOrder` | 必需列齐全时允许多余列和不同顺序，按列名读到 `CNY` | H10 |
| T04 | `testExplicitHeaderMissingColumn` | 显式表头缺列也报错 | H4 |
| T05 | `testExplicitHeaderCheckedEvenIfSkippedLineHasColumn` | 跳过的首行虽有 `currency`，仍以缺列的显式表头为准 | H4 |
| T06 | `testIgnoreHeaderCaseMatchesAnyCase` | 开启忽略大小写后混合大小写表头可读取 | H5 |
| T07 | `testCaseSensitiveByDefault` | 默认大小写敏感，报出 `id` 和 `currency` 缺失 | H5 |
| T08 | `testEmptyInputReportsAllMissing` | 空输入报全部必需列，实际表头 `[]` | H7 |
| T09 | `testRequiredHeadersWithoutHeaderFailsAtBuild` | 无表头配置在 `Builder.get()` 抛 `IllegalArgumentException` | H6 |
| T10 | `testNullRequiredNameFailsAtBuild` | 必需列名含 `null` 时构建失败 | H9 |
| T11 | `testBlankRequiredNameFailsAtBuild` | `""` 和 `" "` 分别构建失败 | H9 |
| T12 | `testEmptyOrNullDisablesCheck` | 无参或 `null` 清除设置，getter 为空且解析正常 | H8 |
| T13 | `testGetterReturnsDefensiveCopy` | setter 与 getter 均防御性拷贝 | H8 |
| T14 | `testCopyAndEquality` | `builder()` 复制属性，`equals`/`hashCode` 计入属性 | H11 |
| T15 | `testTrimmedHeaderSatisfiesRequirement` | 开启 trim 后带空格表头可匹配 | H12 |
| T16 | `testUntrimmedHeaderComparedExactly` | 未开启 trim 时前导空格不匹配，错误信息保留空格 | H12 |
| T17 | `testPrinterIgnoresRequiredHeaders` | 打印仍输出表头和记录 | H13 |

## 3. 如何运行测试

在仓库根执行。以下三条各自独立，可逐条复制；故障注入脚本要求 `git status --porcelain` 为空，且 `src/main` 没有暂存或未暂存改动。

```sh
mvn test -Dtest=RequiredHeadersTest -Dsurefire.failIfNoSpecifiedTests=false
```

预期：`Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`（RequiredHeadersTest），`BUILD SUCCESS`。Surefire 可按类、参数化调用分行显示；以最终汇总和 `target/surefire-reports/TEST-org.apache.commons.csv.RequiredHeadersTest.xml` 为准。

```sh
mvn test
```

预期：全量合计 942 run / 0 failure / 0 error / 11 skipped，`BUILD SUCCESS`；包括 apache-rat 许可证检查，新增文件需带 ASF 头。

```sh
bash fault-injection/run-fault-injection.sh
```

预期：`Baseline: RequiredHeadersTest PASS`；01–17 每行 `Match?` 均为 `yes`；末行 `Total: 17 mutations; 0 mismatches`；退出码 0，`src/main` 无注入残留。脚本内部对每个变异单独执行新测试；它不会把原有 `fault-injection/last-run.log` 改写。若需保存重跑输出，先重定向到仓库外临时文件，避免预先弄脏工作区。

项目全部检查另可运行 `mvn`：pom 的 defaultGoal 为 `clean verify apache-rat:check japicmp:cmp spotbugs:check pmd:check pmd:cpd-check javadoc:javadoc checkstyle:check`；首次需联网下载打包/静态检查插件。预期 `BUILD SUCCESS`，`RAT Unapproved: 0`，`You have 0 Checkstyle violations.`；verify 阶段同样执行全量测试（942/0/0/11）。

## 4. 故障注入与失败原因

- T4 的 17 个变异位于 `fault-injection/`，分别针对 T01–T17；完整的事前预测见 `expected.md`，实际失败集合与一致性见 `last-run.log`。每个 patch 只改 `src/main` 的一处，运行器逐个应用、执行 `RequiredHeadersTest` 并还原。
- 干净工作区重跑时，先把脚本输出重定向到仓库外临时文件，运行完成后再比对或复制到 `fault-injection/last-run.log`；直接重定向到已跟踪的日志会先使工作区变脏。
- 首轮报告提取器错误地把 Surefire 自闭合的成功 `<testcase/>` 与后续失败节点连在一起，导致 16 个假不一致；修正提取器后，仅 14 号预测不全。14 号移除 `Builder(CSVFormat)` 中的必需列复制，不仅使 T14 失败，还因解析器内部复制格式，让 T01、T02、T04、T05、T07、T08、T16 的缺列检查失效。已补全 `expected.md`，无需修改测试。

下列代码块逐字摘自各 `.patch` 的 diff hunk。行号指 `src/test/java/org/apache/commons/csv/RequiredHeadersTest.java` 在 T5 开始时的版本。`assertMissingHeaders` 在第 51 行用 `assertThrows(CSVException.class, () -> format.parse(...))` 校验异常类型与抛出时机，在第 52 行用 `assertEquals(expectedMessage, e.getMessage())` 校验完整报文；下文“第 51/52 行”均指这两条断言。实际变红集合见 `last-run.log`，17/17 与预测一致。

### 01 · 跳过必需列检查

```diff
@@ -672,3 +672,3 @@
             }
-            checkRequiredHeaders(headerMap, headerRecord);
+            // Required header checks are deferred until record access.
         }
```

变红：T01、T02、T04、T05、T07、T08、T16。七个输入都缺列，`parse()` 不再抛错，故均在第 51 行失败：T01 的 `id,amount` 缺 `currency`（第 65 行）；T02 的 `id,note` 缺 `currency,amount,date`（第 72 行）；T04 的显式 `id,amount` 缺 `currency`（第 86 行）；T05 跳过行虽有 `currency`，显式 `id,amount` 仍缺它（第 93 行）；T07 的 `ID,Currency,amount` 大小写不匹配（第 107 行）；T08 空输入缺全部列（第 114 行）；T16 的 `id, currency` 含前导空格（第 180 行）。没有异常可供第 52 行比较。

### 02 · 只报告首个缺列

```diff
@@ -598,3 +598,3 @@
             if (!headerMap.containsKey(requiredHeader)) {
-                missingHeaders.add(requiredHeader);
+                if (missingHeaders.isEmpty()) { missingHeaders.add(requiredHeader); }
             }
```

变红：T02、T07、T08。三者都在第 52 行失败：T02 的 `id,note` 应列出 `[currency, amount, date]`（第 72 行），实际只剩 `currency`；T07 的 `ID,Currency,amount` 应列出 `[id, currency]`（第 107 行），实际只剩 `id`；T08 的空输入应列出 `[id, currency, amount]`（第 114 行），实际只剩 `id`。第 51 行仍能捕获 `CSVException`。

### 03 · 错拒额外列

```diff
@@ -601,3 +601,3 @@
         }
-        if (!missingHeaders.isEmpty()) {
+        if (!missingHeaders.isEmpty() || format.getRequiredHeaders().length > 0 && headerRecord != null && headerRecord.length > format.getRequiredHeaders().length) {
             throw new CSVException("Missing required header name(s) %s in header %s", missingHeaders,
```

变红：T03。`amount,note,currency,id` 含全部必需列且多一列 `note`（第 79 行）；`firstValue` 在第 56 行创建解析器时错误抛出 `CSVException`，第 79 行的 `assertEquals("CNY", ...)` 无法完成。其他测试未使用“必需列齐全且多余列”的输入。

### 04 · 忽略显式表头

```diff
@@ -672,3 +672,3 @@
             }
-            checkRequiredHeaders(headerMap, headerRecord);
+            if (formatHeader.length == 0) { checkRequiredHeaders(headerMap, headerRecord); }
         }
```

变红：T04、T05。T04 输入 `1,10`，显式表头 `id,amount` 缺 `currency`（第 85–86 行）；T05 跳过首行 `id,currency,amount`，显式表头仍是缺 `currency` 的 `id,amount`（第 92–93 行）。二者的 `parse()` 都因跳过检查而正常返回，违反第 51 行的 `assertThrows`。

### 05 · 跳过首行时信任显式表头

```diff
@@ -672,3 +672,3 @@
             }
-            checkRequiredHeaders(headerMap, headerRecord);
+            if (formatHeader.length == 0 || !format.getSkipHeaderRecord()) { checkRequiredHeaders(headerMap, headerRecord); }
         }
```

变红：T05。第 92–93 行显式 `id,amount` 配 `setSkipHeaderRecord(true)`，跳过的输入首行虽含 `currency`，有效映射仍缺它；检查被跳过，第 51 行期待的 `CSVException` 没出现。T04 未启用 skip，故不红。

### 06 · 忽略大小写开关失效

```diff
@@ -609,3 +609,3 @@
         return format.getIgnoreHeaderCase() ?
-                new TreeMap<>(String.CASE_INSENSITIVE_ORDER) :
+                new LinkedHashMap<>() :
                 new LinkedHashMap<>();
```

变红：T06。第 99–100 行启用 `ignoreHeaderCase`，输入 `ID,Currency,AMOUNT` 本应匹配 `id,currency,amount`；精确匹配映射使解析时抛 `CSVException`，第 100 行的 `assertEquals("CNY", ...)` 无法完成。其余测试未同时启用该开关和混合大小写表头。

### 07 · 默认错误地忽略大小写

```diff
@@ -610,3 +610,3 @@
                 new TreeMap<>(String.CASE_INSENSITIVE_ORDER) :
-                new LinkedHashMap<>();
+                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
     }
```

变红：T07。第 107 行输入 `ID,Currency,amount`，默认配置应报缺 `id,currency`；不敏感映射将二者当作已存在，`parse()` 返回，违反第 51 行的 `assertThrows`。T06 本就开启开关，故仍绿。

### 08 · 空输入跳过检查

```diff
@@ -672,3 +672,3 @@
             }
-            checkRequiredHeaders(headerMap, headerRecord);
+            if (headerRecord != null) { checkRequiredHeaders(headerMap, headerRecord); }
         }
```

变红：T08。第 114 行传空字符串，实际表头为 `[]`、应报三列全缺；`headerRecord == null` 使检查跳过，第 51 行期待的 `CSVException` 没出现。其他缺列测试都有非空表头。

### 09 · 允许无表头配置

```diff
@@ -2677,3 +2677,3 @@
         if (requiredHeaders != null) {
-            if (headers == null) {
+            if (headers == null && requiredHeaders.length == 0) {
                 throw new IllegalArgumentException("Required header names " + Arrays.toString(requiredHeaders) + " need a header, use setHeader().");
```

变红：T09。第 120 行只设 `requiredHeaders("id")` 而没调用 `setHeader`，`get()` 不再抛 `IllegalArgumentException`，直接违反该行的 `assertThrows`。其他测试都配了表头。

### 10 · 允许 null 必需列名

```diff
@@ -2681,3 +2681,3 @@
             for (final String requiredHeader : requiredHeaders) {
-                if (isBlank(requiredHeader)) {
+                if (requiredHeader != null && isBlank(requiredHeader)) {
                     throw new IllegalArgumentException("A required header name is null or blank in " + Arrays.toString(requiredHeaders));
```

变红：T10。第 126 行传 `"id", null`，构建时跳过 null 校验，违反该行的 `assertThrows(IllegalArgumentException.class, ...)`。T11 的空白字符串仍由 `isBlank` 拦截。

### 11 · 允许空白必需列名

```diff
@@ -2681,3 +2681,3 @@
             for (final String requiredHeader : requiredHeaders) {
-                if (isBlank(requiredHeader)) {
+                if (requiredHeader == null) {
                     throw new IllegalArgumentException("A required header name is null or blank in " + Arrays.toString(requiredHeaders));
```

变红：T11。第 131 行分别提供 `""` 与 `" "`，第 133 行两次 `assertThrows` 都因 `get()` 返回而失败；T10 的 null 仍被拦截。脚本按测试方法名去重，因此汇总只有一个 T11。

### 12 · 无参或 null 无法清除

```diff
@@ -870,3 +870,3 @@
         public Builder setRequiredHeaders(final String... requiredHeaders) {
-            this.requiredHeaders = requiredHeaders == null || requiredHeaders.length == 0 ? null : requiredHeaders.clone();
+            this.requiredHeaders = requiredHeaders == null || requiredHeaders.length == 0 ? this.requiredHeaders : requiredHeaders.clone();
             return this;
```

变红：T12。第 139–140 行先设置 `currency`，再分别无参或传 `null`；旧值残留，第 141、142 行的空数组 `assertArrayEquals` 先后失败。即使继续解析 `id\n1\n`，第 143、144 行的 `assertEquals("1", firstValue(...))` 也会因缺 `currency` 而失败；JUnit 在首个失败处停止本方法。

### 13 · getter 暴露内部数组

```diff
@@ -2019,3 +2019,3 @@
     public String[] getRequiredHeaders() {
-        return requiredHeaders != null ? requiredHeaders.clone() : Constants.EMPTY_STRING_ARRAY;
+        return requiredHeaders != null ? requiredHeaders : Constants.EMPTY_STRING_ARRAY;
     }
```

变红：T13。第 150–153 行先改输入数组首项，证明 setter 已复制，首个 `assertArrayEquals` 仍通过；第 154 行再把 getter 返回数组第二项改为 `changed`，第 155 行的 `assertArrayEquals([id,currency], ...)` 发现内部值被改写。

### 14 · Builder 复制时丢失必需列

```diff
@@ -301,3 +301,3 @@
             this.recordSeparator = csvFormat.recordSeparator;
-            this.requiredHeaders = csvFormat.requiredHeaders;
+            this.requiredHeaders = null;
             this.skipHeaderRecord = csvFormat.skipHeaderRecord;
```

变红：T14，以及 T01、T02、T04、T05、T07、T08、T16。T14 的 `format.builder().get()` 丢失 `currency`，第 163 行的 `assertArrayEquals([currency], copy.getRequiredHeaders())` 首先失败；第 164–166 行还约束相等性和哈希，但执行不到。解析器内部也通过 Builder 复制格式，故其余七项的检查被关闭，均违反第 51 行的 `assertThrows`：T01 的 `id,amount`（第 65 行）、T02 的 `id,note`（第 72 行）、T04 的显式 `id,amount`（第 86 行）、T05 的显式 `id,amount` 与跳过行（第 93 行）、T07 的大小写不同列（第 107 行）、T08 的空输入（第 114 行）、T16 的前导空格列（第 180 行）。这是复制路径造成的连带失败，不是七处独立缺陷。

### 15 · trim 后仍拒绝有效表头

```diff
@@ -597,3 +597,3 @@
         for (final String requiredHeader : format.getRequiredHeaders()) {
-            if (!headerMap.containsKey(requiredHeader)) {
+            if (!headerMap.containsKey(requiredHeader) || format.getTrim()) {
                 missingHeaders.add(requiredHeader);
```

变红：T15。第 172–173 行开启 trim，输入 ` id , currency ` 解析后已可匹配；额外条件仍把必需列标缺，`firstValue` 在第 56 行构造解析器时抛错，第 173 行 `assertEquals("CNY", ...)` 不能完成。其他测试未启用 trim。

### 16 · 未 trim 却接受前导空格

```diff
@@ -597,3 +597,3 @@
         for (final String requiredHeader : format.getRequiredHeaders()) {
-            if (!headerMap.containsKey(requiredHeader)) {
+            if (!headerMap.containsKey(requiredHeader) && !headerMap.containsKey(" " + requiredHeader)) {
                 missingHeaders.add(requiredHeader);
```

变红：T16。第 180 行输入 `id, currency`，第二列实际名字为 `" currency"`；变异把它算作 `currency`，`parse()` 不抛错，违反第 51 行的 `assertThrows`。T15 已开启 trim，其表头正常匹配。

### 17 · 打印时错误地隐藏表头

```diff
@@ -2168,3 +2168,3 @@
     public CSVPrinter print(final Appendable out) throws IOException {
-        return new CSVPrinter(out, this);
+        return new CSVPrinter(out, requiredHeaders == null ? this : builder().setSkipHeaderRecord(true).get());
     }
```

变红：T17。第 186 行配置显式表头 `id` 与必需列 `currency`，第 188–190 行打印记录；变异设置跳过表头，实际输出只剩记录，第 191 行的 `assertEquals("id\r\n1\r\n", out.toString())` 失败。其他测试均走解析路径，不受打印改动影响。

## 5. 踩坑记录

| 环节 | 现象 | 原因 | 解决 | 怎样提前避开 |
| --- | --- | --- | --- | --- |
| T0 | 新增 `guide.md`、`dialogs/dialogs.md` 后 `mvn test` 报 `[ERROR] Unexpected count for UNAPPROVED, limit is [0,0].  Count: 2` | apache-rat 的 rat-check 在 test 前执行；两个新文件缺 ASF 许可证头 | 在两个 `.md` 顶部添加 ASF 头（HTML 注释）；未改 pom | 新增任何文件时先检查同类文件许可证头；`dialogs.md` 后续只往末尾追加 |
| T2 | 首次跑 `mvn` 报 `Could not transfer artifact org.apache.commons:commons-lang3:jar:3.18.0 from/to central (https://repo.maven.apache.org/maven2): Remote host terminated the handshake` | 完整默认目标需下载本地尚无的打包/静态检查依赖，Maven Central TLS 握手偶发中断 | 原样重跑 `mvn` 后 BUILD SUCCESS；未改 pom、未配镜像 | 首次完整构建先预留依赖下载时间；遇握手错误先重试并区分网络与代码失败 |
| T4 | 首轮注入报告 16/17 个集合不一致，多个变异总把 T05 记为失败 | Surefire 成功用例为自闭合 `<testcase/>`；旧正则跨节点读到后续失败并误配方法名 | 提取器同时识别自闭合与成对节点，重跑后假不一致消失 | 写 XML 提取器时用同时含成功、失败节点的报告验证边界，或使用 XML 解析器 |
| T4 | 14 号变异除 T14 外又打红 7 个缺列测试 | `Builder(CSVFormat)` 丢失属性时，解析器内部的格式复制也丢失检查 | 补全 `expected.md` 的失败集合，重跑达 17/17 一致；测试无需改 | 预测变异影响时沿调用链查所有复制入口，不只看直接调用 `builder()` 的测试 |
| T4 | 严格重跑报 `ERROR: working tree must be clean before fault injection.` | 输出重定向到已跟踪 `last-run.log`，shell 在脚本启动前截断文件 | 预备运行恢复日志；严格运行重定向到仓库外临时文件，退出码 0，输出与记录一致 | 脚本需干净工作区时，把日志重定向到仓库外，运行后再复制或比对 |

## 6. 环节与 tag

| tag | commit | 做了什么 | `mvn test` 结果 |
| --- | --- | --- | --- |
| `tdd-t0-baseline` | `4d728ec3` | 克隆到 `feature/required-headers`；建立指南和对话记录；修正 ASF 许可证头 | 924 run / 0 fail / 0 error / 11 skip，BUILD SUCCESS，约 5 s |
| `tdd-t1-red` | `422d4d43` | 写 17 个测试方法、18 次执行，建立预期失败基线 | 全量 942 run / 13 fail / 0 error / 11 skip，BUILD FAILURE（预期）；新测试 18 run / 13 fail / 0 error / 5 pass，约 5.5 s |
| `tdd-t2-green` | `39f14354` | 实现 Builder 属性、校验、解析器缺列检查；未改测试；默认目标 `mvn` 亦通过 | 942 run / 0 fail / 0 error / 11 skip，BUILD SUCCESS，约 5.4 s；比基线 +18 |
| `tdd-t3-refactor` | `908bb734` | 提取测试断言辅助方法、改 T12 变量名、调整 `equals` 断行与解析器变量名；`checkstyle:check` 通过 | 942 run / 0 fail / 0 error / 11 skip，BUILD SUCCESS，约 6 s |
| `tdd-t4-fault-injection` | `fb5522dd` | 17 个 patch 覆盖 T01–T17；预测与实际 17/17 一致，无注入残留 | 942 run / 0 fail / 0 error / 11 skip，BUILD SUCCESS |
| `tdd-t5-guide` | 本环节提交（以 `git rev-parse tdd-t5-guide` 查看） | 补全假设覆盖、测试清单、命令、17 个变异解释、踩坑和环节索引 | 942 run / 0 fail / 0 error / 11 skip，BUILD SUCCESS |
