<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# T4 故障注入预测

先写于首次故障注入运行之前。预期集合按测试方法名去重；T11 两次参数化调用任一次失败均记该方法，运行日志另列实际调用。

| 变异编号 | 改了什么 | 针对的测试 | 预期失败的完整测试方法集合 | 预期理由 |
| --- | --- | --- | --- | --- |
| 01 | 跳过解析时的必需列检查 | `testMissingColumnFailsBeforeFirstRecord` | `testMissingColumnFailsBeforeFirstRecord,testMultipleMissingColumnsReportedInDeclaredOrder,testExplicitHeaderMissingColumn,testExplicitHeaderCheckedEvenIfSkippedLineHasColumn,testCaseSensitiveByDefault,testEmptyInputReportsAllMissing,testUntrimmedHeaderComparedExactly` | 所有缺列输入都不再抛错 |
| 02 | 只收集首个缺列 | `testMultipleMissingColumnsReportedInDeclaredOrder` | `testMultipleMissingColumnsReportedInDeclaredOrder,testCaseSensitiveByDefault,testEmptyInputReportsAllMissing` | 多个缺列时异常清单被截断 |
| 03 | 拒绝含额外列的有效表头 | `testAllPresentWithExtraColumnsInAnyOrder` | `testAllPresentWithExtraColumnsInAnyOrder` | 齐全但有额外列时错误地抛错 |
| 04 | 只检查文件表头 | `testExplicitHeaderMissingColumn` | `testExplicitHeaderMissingColumn,testExplicitHeaderCheckedEvenIfSkippedLineHasColumn` | 显式表头缺列被漏检 |
| 05 | 跳过首行时信任显式表头 | `testExplicitHeaderCheckedEvenIfSkippedLineHasColumn` | `testExplicitHeaderCheckedEvenIfSkippedLineHasColumn` | 显式表头且跳过首行时漏检 |
| 06 | 忽略大小写开关失效 | `testIgnoreHeaderCaseMatchesAnyCase` | `testIgnoreHeaderCaseMatchesAnyCase` | 启用忽略大小写后仍用精确匹配 |
| 07 | 默认改为不区分大小写 | `testCaseSensitiveByDefault` | `testCaseSensitiveByDefault` | 本应缺失的大小写不同列被接受 |
| 08 | 空输入跳过必需列检查 | `testEmptyInputReportsAllMissing` | `testEmptyInputReportsAllMissing` | 空表头不再报告所有缺列 |
| 09 | 允许无表头却配置必需列 | `testRequiredHeadersWithoutHeaderFailsAtBuild` | `testRequiredHeadersWithoutHeaderFailsAtBuild` | 构建时配置错误未抛出 |
| 10 | 允许 null 必需列名 | `testNullRequiredNameFailsAtBuild` | `testNullRequiredNameFailsAtBuild` | null 名称绕过构建校验 |
| 11 | 允许空白必需列名 | `testBlankRequiredNameFailsAtBuild` | `testBlankRequiredNameFailsAtBuild` | 两次参数化调用的空白名称均绕过校验 |
| 12 | 无参或 null 无法清除配置 | `testEmptyOrNullDisablesCheck` | `testEmptyOrNullDisablesCheck` | 旧必需列残留 |
| 13 | getter 暴露内部数组 | `testGetterReturnsDefensiveCopy` | `testGetterReturnsDefensiveCopy` | 调用者可修改格式对象的内部状态 |
| 14 | builder 复制时丢失必需列 | `testCopyAndEquality` | `testCaseSensitiveByDefault,testCopyAndEquality,testEmptyInputReportsAllMissing,testExplicitHeaderCheckedEvenIfSkippedLineHasColumn,testExplicitHeaderMissingColumn,testMissingColumnFailsBeforeFirstRecord,testMultipleMissingColumnsReportedInDeclaredOrder,testUntrimmedHeaderComparedExactly` | 副本缺少该属性；解析器内部也复制格式，多个缺列检查随之失效 |
| 15 | trim 后仍拒绝有效表头 | `testTrimmedHeaderSatisfiesRequirement` | `testTrimmedHeaderSatisfiesRequirement` | 已去空白的列被误判缺失 |
| 16 | 未开启 trim 却接受前导空格 | `testUntrimmedHeaderComparedExactly` | `testUntrimmedHeaderComparedExactly` | 错误地把空格前缀列算作匹配 |
| 17 | 有必需列时打印不输出表头 | `testPrinterIgnoresRequiredHeaders` | `testPrinterIgnoresRequiredHeaders` | 解析配置错误地影响打印 |
