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

## 2. 测试清单

## 3. 如何运行测试

## 4. 故障注入与失败原因

## 5. 踩坑记录

- T0 · 新增 guide.md、dialogs/dialogs.md 后 `mvn test` 失败：`[ERROR] Unexpected count for UNAPPROVED, limit is [0,0].  Count: 2` → apache-rat-plugin 的 rat-check 在 test 之前的阶段运行，要求每个文件带 ASF 许可证头，新增的两个 .md 没有 → 在两个文件顶部加 ASF 许可证头（HTML 注释，渲染不可见），不改 pom.xml；后续新增文件同理需带头，dialogs.md 只往末尾追加。

## 6. 环节与 tag

- T0 克隆 + 基线 · tag `tdd-t0-baseline`：924 run / 0 fail / 0 error / 11 skip，约 5 s。
