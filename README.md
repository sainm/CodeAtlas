# CodeAtlas

CodeAtlas 是一个面向 Java 企业项目的静态分析与变更影响分析平台。它的目标不是做普通代码扫描器，而是用可追溯、可增量、可解释的方式回答开发者日常最关心的问题：

- 这里是谁调用的？
- 这个变量从哪里来、到哪里去？
- 我改这里会影响什么？
- DB 表或字段改了，会影响哪些代码、页面、入口和测试？
- 我想修改或新增一个功能，应该关注哪些文件、链路和相似实现？

完整设计以 [docs/design.md](docs/design.md) 为唯一手写事实源。README 只是仓库入口摘要；当 README、plan、task 或 spec 与 design 冲突时，以 `docs/design.md` 为准。

## 项目定位

CodeAtlas 首批重点面向企业 Java 程序和新旧混合系统。Java Web 是首批高价值适配域，但不是产品边界；batch、定时任务、CLI/main 方法、消息消费者、shell/运维脚本启动的 Java 程序也会进入同一套影响分析模型。

MVP 目标先闭环这些主线：

- Spring RequestMapping
- Struts1 action
- JSP/HTML form/action
- 静态 JavaScript ClientRequest
- Java direct call
- MyBatis statement
- DB table/column read/write/display impact
- `EntryPoint` 抽象，用于后续接入 batch、main、scheduler、message、shell 入口

Seasar2 在 MVP 中只做 dicon/component discovery、配置证据采集和 `POSSIBLE` 候选关系，不作为确定性影响路径验收阻塞项。

## 设计原则

- 静态分析和图谱产生事实。
- AI 负责解释、总结、辅助检索、排序和编排，不直接生成最终事实。
- 先快后深：先返回初版影响报告，再后台补充深度变量流和数据流。
- 所有结论必须带证据路径、置信度和来源。
- Analyzer worker 只产出 facts、evidence 和 diagnostics，不直接写 active graph。
- 查询只读取 committed snapshot，不能读取未提交 staging facts。
- AI candidate 必须与确定事实分离，默认不参与确定性影响路径。

## 总体架构

```text
Import / Upload / Git
  -> Workspace Profiler & Import Review
  -> Analysis Planner
  -> Analysis Work Queue

Analyzer Workers
  -> Source Analyzer: Spoon
  -> Bytecode Analyzer: ASM / ClassGraph / ProGuardCORE
  -> JSP/Web Client Analyzer: Jasper + JSP token/form extractor + HTML/JS static client analyzer
  -> SQL Analyzer: JSqlParser
  -> Config / Shell / Framework Adapters
  -> Optional Sidecars: Joern / Tabby / WALA / SootUp-Heros / Tai-e

Fact Pipeline
  -> Symbol Canonicalizer
  -> Fact & Evidence Builder
  -> Staging Store
  -> Commit Coordinator
  -> Active Snapshot View

Storage & Index
  -> Neo4j GraphStore
  -> Evidence Store
  -> Symbol Index
  -> JVM InMemoryGraphCache
  -> Vector Index
  -> FFM OffHeapGraphIndex optional

Query & Planning Services
  -> Impact Query Service
  -> DB Impact Service
  -> Variable Impact Service
  -> Feature Change Planner
  -> Feature Addition Planner
  -> Report Builder
  -> Evidence Pack Builder

AI / Interface
  -> AI Summary / Ranking / Explanation
  -> REST / MCP
  -> Visual Frontend
```

## 核心能力

| 场景 | 输入 | 输出 | 关键约束 |
| --- | --- | --- | --- |
| 代码变更影响 | Git diff、方法、类、文件 | 受影响入口、调用方、SQL、DB、页面、batch/job、测试建议 | 区分已确认影响、候选影响和截断路径 |
| DB 变更影响 | 表、字段、SQL statement | 读写 SQL、Mapper/DAO、Service、入口、报表、页面、batch/job | 区分 read/write/display/test impact |
| 变量影响 | 变量、参数、DTO/Form 字段、request 参数、JSP/HTML input、JS 请求参数、job 参数 | 来源、流向、下游代码、SQL 参数、DB 字段、返回值或页面展示 | 区分方法内、跨方法和跨框架边界 |
| 功能修改 | 功能描述、入口、页面、接口、历史提交或关键词 | 必须关注、建议检查、可能相关、无需修改但要回归、建议测试 | 区分确定范围、弱证据候选和 AI 建议 |
| 新增功能 | 功能描述、目标模块、相似功能线索 | 推荐参照、建议新增、建议复用、风险检查、测试建议 | 区分可复用事实、规划 artifact 和人工确认项 |
| 导入审查 | 本地文件夹或上传归档 | 项目清单、项目状态、依赖关系、不可分析盲区、建议分析计划 | 区分导入成功、项目可识别、可完整分析 |

## 导入与分析边界

CodeAtlas 的导入对象不是直接等同于一个项目。一次本地文件夹选择或上传归档会先形成 `ImportWorkspace`，其中可能包含 0 到多个 Java 项目，也可能混入 C、COBOL、shell-only、文档、样例或不可分析内容。

导入审查需要识别：

- 候选项目边界和项目状态：`READY`、`PARTIAL`、`BOUNDARY_ONLY`、`UNSUPPORTED`、`BROKEN`、`UNKNOWN`
- 文件能力分级：结构化分析、半结构化分析、边界识别、仅 inventory、跳过
- 入口线索：main、Spring/Struts/JSP、HTML form、静态 JS HTTP 请求、scheduler/message listener、shell 启动命令
- 不可分析盲区：缺依赖、缺源码、动态 shell、unsupported language、native/COBOL 边界

在 `ASSISTED_IMPORT_REVIEW` 模式下，如果存在非完整可分析项目，正式分析前必须获得用户确认，并把确认内容作为 `AnalysisScopeDecision` 写入分析元数据和最终报告。

## 前端体验

首版前端采用“Project Dashboard + Task Workbench 组合页”：

- Dashboard 作为概览区域，展示项目状态、覆盖能力、入口点、最近报告、盲区和推荐下一步。
- Task Workbench 是首页主操作区，优先回答“今天要分析什么”。
- 统一输入支持 Git diff、DB 表/字段、JSP/HTML 页面、Java symbol、变量名和自然语言功能描述。
- 快捷任务包括分析 Git diff、查 DB 影响、查变量流向、查 JSP/Web Client 链路、规划功能修改、找相似实现。
- 图谱探索是下钻能力，不作为默认第一屏。
- 默认隐藏 raw `symbolId` 和 raw JSON，只在证据详情中展示。

## 当前仓库状态

当前提交是重置版基线，保留设计文档和最小 Gradle 多模块骨架，用于按 `docs/design.md` 重新实现平台能力。

已存在模块：

```text
codeatlas-symbols
codeatlas-facts
codeatlas-graph
codeatlas-analyzers
codeatlas-worker
codeatlas-server
codeatlas-ai
codeatlas-mcp
codeatlas-ui
```

当前 Java 模块只包含最小占位类和基础健康检查测试。后续实现应按 [docs/plan.md](docs/plan.md) 和 [docs/task.md](docs/task.md) 逐步推进。

## 本地构建

要求：

- JDK 25
- Gradle wrapper 已随仓库提交

常用命令：

```powershell
.\gradlew.bat test
```

如果本机默认 JDK 不是 25，可以显式指定：

```powershell
$env:JAVA_HOME='D:\jdks\jdk-25.0.2+10'
.\gradlew.bat test --no-daemon
```

Gradle Java Toolchains 已配置使用 Java 25：

```properties
org.gradle.java.installations.auto-detect=true
org.gradle.java.installations.paths=D:/jdks/jdk-25.0.2+10
```

## 文档入口

- [docs/design.md](docs/design.md)：唯一手写设计源。
- [docs/plan.md](docs/plan.md)：从 design 派生的实施计划。
- [docs/task.md](docs/task.md)：从 design/plan 派生的任务清单。
- [docs/superpowers/specs/2026-05-02-codeatlas-ui-workbench-design.md](docs/superpowers/specs/2026-05-02-codeatlas-ui-workbench-design.md)：Task Workbench UI 派生规格。

## 非目标

- 不做运行时 APM、在线链路追踪或生产流量采集。
- 不默认执行被分析项目的构建脚本、单元测试、shell 或业务代码。
- 不承诺完整替代通用指针分析、全程序切片、漏洞扫描平台或自动重构平台。
- 不把 AI 输出当作确定事实。
