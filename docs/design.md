# CodeAtlas Design

本文是 CodeAtlas 的唯一手写设计源。其他 docs 都应从本文重新整理生成，不能与本文形成另一套事实口径。

文档阅读约定：

- `MUST` 表示实现、验收或安全正确性必须满足的规则。
- `SHOULD` 表示默认应采用的设计，只有 benchmark、license、兼容性或部署约束证明不适合时才偏离。
- `MAY` 表示可选增强，不进入 MVP 或生产门禁。
- “事实”指由 analyzer、配置解析、字节码扫描、图谱提交或用户确认产生的结构化 `FactRecord`；AI 输出默认不是事实。
- “候选”指有证据但尚未达到确定事实门槛的关系、路径、项目边界或功能范围。
- “报告”指面向用户展示的查询结果或规划产物，可以包含确定事实、候选、盲区、风险说明和 AI 摘要，但必须区分来源和置信度。

格式约定：

- 每个能力章节优先按“目标 -> 输入 -> 处理 -> 输出 -> 降级/边界 -> 验收口径”组织。
- 图谱和 API 契约用稳定英文标识；解释文本使用中文。
- 低层代码事实使用 `symbolId`，影响流边界使用 `flowId`，导入审查和功能规划产物使用 `artifactId`。
- 所有性能承诺必须绑定 benchmark profile；没有 profile 的性能数字只作为目标，不作为验收结论。

## 1. 项目定位

CodeAtlas 是面向现有 Java 代码库的静态分析、需求探索、修改规划、影响分析与验证平台。核心目标不是做一个普通代码扫描器，也不是只生成 Git diff 报告，而是把用户的需求、bug、设计片段、字段、页面、接口、方法或 DB 变更意图，映射到现有代码库中的修改点、影响范围、测试范围和验证结果。

CodeAtlas 用尽量智能、尽量快速的方式回答开发者、维护者、测试人员和评审者每天最关心的问题：

- 我想修改一个功能，都需要改哪里？
- 我想新增一个功能，应该参考哪里、落在哪里？
- 需求或 bug 描述不完整时，应该先看哪些入口、页面、服务、SQL 和测试？
- 这里是谁调用的？
- 这个变量从哪里来、到哪里去？
- 我改这里会影响什么？
- DB 表或字段改了，会影响哪些代码和入口？
- 这次变更风险在哪里，应该测什么、用什么数据测？
- 改完以后是否覆盖了原计划，还有哪些遗漏和新增影响？

平台重点面向企业 Java 程序和新旧混合系统。Java Web 不是唯一对象，而是首批高价值适配域；batch、定时任务、CLI/main 方法、消息消费者、shell/运维脚本启动的 Java 程序也应进入同一套影响分析模型。完整蓝图覆盖 Java 源码/字节码、Spring、Struts1、Seasar2、JSP、HTML/JavaScript 客户端行为、MyBatis/SQL、配置、shell 启动入口、代码库关系刷新、调用关系、代码影响、DB 双向影响、变量影响、需求探索、功能修改规划、新增功能规划、测试计划和 AI 辅助解释。MVP 必须先闭环 Spring/Struts1/JSP/HTML 表单/静态客户端请求/MyBatis 的首批最小影响链路，同时把入口模型设计为可扩展到 batch/shell/message/CLI；Seasar2 在 MVP 中只做 dicon/component discovery、配置证据采集和 `POSSIBLE` 候选关系，不要求输出确定性影响路径，也不作为 MVP 影响报告阻塞项。Seasar2 确定性链路进入增强阶段。

CodeAtlas 的核心产品目标是围绕“变更意图到代码修改闭环”工作：先持续维护代码库关系事实库，再按用户意图进行需求探索、修改规划、影响分析、测试计划、测试数据建议和修改后验证。使用者可以熟悉代码，也可以不熟悉代码；平台不以新手辅助为唯一定位。Git commit、Git diff、文件 hash 变化和导入更新首先用于刷新关系事实库和发布新 snapshot；它们不是默认报告触发器。报告和规划由用户的问题、需求或显式查询触发。

设计原则：

- 静态分析和图谱产生事实。
- AI 负责解释、总结、辅助检索和编排，不直接生成最终事实。
- 先刷新事实库，再按用户意图查询；先给可用的需求探索/影响/测试规划，再后台补充深度变量流和数据流。
- 自研层只做可解释、可测试、可增量的业务影响流；复杂别名、多态、深层切片和安全污点交给外部侧车补强和校验。
- 所有结论必须带证据路径、置信度和来源。

首批用户场景：

| 场景 | 用户输入 | CodeAtlas 输出 | 必须区分 |
| --- | --- | --- | --- |
| 需求探索与改动规划 | 需求、bug 描述、设计书片段、页面/字段/菜单名、自然语言问题 | 需求理解、候选入口、必须修改、建议检查、可能相关、测试计划、测试数据、待确认问题 | 静态事实、AI 归纳、用户确认、盲区 |
| 功能修改 | 功能描述、入口、页面、接口、历史提交或关键词 | 必须关注、建议检查、可能相关、无需修改但要回归、建议测试 | 确定范围、弱证据候选、AI 建议 |
| 新增功能 | 功能描述、目标模块、相似功能线索 | 推荐参照、建议新增、建议复用、风险检查、测试建议 | 可复用事实、规划 artifact、人工确认项 |
| 影响分析 | 方法、类、文件、显式 diff 查询、入口或功能范围 | 受影响入口、调用方、SQL、DB、页面、batch/job、测试建议 | 已确认影响、候选影响、截断路径 |
| 变量影响 | 变量、参数、DTO/Form 字段、request 参数、JSP/HTML input、JS 请求参数、job 参数 | 来源、流向、下游代码、SQL 参数、DB 字段、返回值或页面展示 | 方法内、跨方法、跨框架边界 |
| DB 变更影响 | 表、字段、SQL statement | 读写 SQL、Mapper/DAO、Service、入口、报表、页面、batch/job | 读影响、写影响、展示影响 |
| 修改后验证 | 用户修改后的文件变化、Git diff 或本地变更 | 关系库刷新、计划覆盖检查、新增影响路径、遗漏项、更新后的测试建议 | refresh 结果、查询报告、未覆盖计划 |
| 代码库刷新/导入审查 | 本地文件夹、上传归档、Git pull/commit/diff、文件变化 | 项目清单、项目状态、关系事实刷新、依赖关系、不可分析盲区、建议分析计划 | 导入成功、项目可识别、可完整分析、refresh 成功 |
| PR/CI 增强 | PR metadata、commit range、显式策略 | 可选影响摘要、policy 状态、review 提示 | 事实刷新、报告 artifact、阻断策略 |

非目标：

- 不做运行时 APM、在线链路追踪或生产流量采集。
- 不默认执行被分析项目的构建脚本、单元测试、shell 或业务代码。
- 不承诺完整替代通用指针分析、全程序切片、漏洞扫描平台或自动重构平台。
- 不把 AI 输出当作确定事实；AI 只能解释、排序、提出候选和总结盲区。

## 2. 总体架构

### 2.1 架构总览

```text
Import / Upload / Git
  -> Workspace Profiler & Import Review
  -> Analysis Planner
  -> Analysis Work Queue

Analyzer Workers
  -> Source Analyzer: Spoon
  -> Java Project/Navigation Adapter: Eclipse JDT Core / JDT LS compatible index
  -> Bytecode Analyzer: ASM / ClassGraph / ProGuardCORE
  -> JSP/Web Client Analyzer: Jasper + JSP token/form extractor + HTML/JS static client analyzer
  -> SQL Analyzer: JSqlParser + MyBatis SqlVariantEvaluator
  -> Config / Shell / Framework Adapters
  -> Optional Sidecars: Joern / CodeQL / Tabby / WALA / SootUp-Heros / Tai-e

Fact Pipeline
  -> Symbol Canonicalizer
  -> Fact & Evidence Builder
  -> Validate & Atomic Write
  -> Active Snapshot View

Storage & Index
  -> FactStore / GraphStore
     -> Neo4j production backend
     -> InMemory dev/CI backend
  -> Evidence Store
  -> Symbol Index
  -> CodeNavigationIndex (LSIF/LSP-like definition/reference/hover)
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

总体架构不按单条同步流水线设计。导入、分析、提交、查询和 AI 解释是五个不同阶段，之间通过结构化事实、证据包和 snapshot 边界连接。

核心原则：

- Analyzer worker 只产出 facts、evidence 和 diagnostics，不直接把结论写进 active graph。
- Impact Flow Engine 分成两个位置：分析阶段的 Flow Fact Builder 生成 Call/Data/Variable/Feature 候选事实；查询阶段的 Impact Query Service 基于 committed FactStore、缓存和 evidence 生成报告。
- 所有事实写入必须经过 Symbol Canonicalizer、Fact & Evidence Builder 和原子校验-写入，成功后才进入 Active Snapshot View。写入失败时旧事实完整保留。
- AI / Agent 只在 evidence pack 之后参与摘要、排序、解释、候选建议和不确定项说明，不参与事实提交。
- Feature Change Planning 和 Feature Addition Planning 是 Query & Planning Services，不是低层 analyzer；它们可以持久化分析产物，但不能覆盖底层代码事实。
- JVM cache、vector index 和 FFM 都是派生索引；committed `FactRecord/Evidence` 才是事实源，生产部署通常落在 Neo4j。

核心分层：

- Workspace Profiler & Import Review：负责本地文件夹、上传归档、Git 来源的 workspace 盘点、项目边界识别、文件能力分级、导入风险和用户确认。
- Analysis Planner：把 `ImportReviewReport`、Git diff、changed scopes、用户选择和历史 snapshot 转成 analyzer task graph，决定哪些任务走快路径、哪些进入后台深度层。
- Analysis Work Queue：解耦前台请求和 analyzer worker，支持超时、重试、优先级、任务取消和 worker 失败隔离。
- Analyzer Workers：包含 Spoon source analyzer、ASM/ClassGraph/ProGuardCORE bytecode analyzer、Jasper JSP analyzer、HTML/JS static client analyzer、JSqlParser SQL analyzer、config/shell/framework adapters，以及 Joern/Tabby 等可选侧车。
- Fact Pipeline：统一做 symbol 归一化、factKey/evidenceKey 生成、schema 校验、原子写入（同一事务内 upsert + tombstone），失败自动回滚，避免半更新 facts 暴露给查询层。
- Storage & Index：FactStore/GraphStore 保存 active facts、核心符号和影响边；生产默认实现为 Neo4j，dev/CI 可使用 InMemoryFactStore。Evidence Store 保存可展开证据和 snippet metadata；Symbol Index、JVM cache、vector index、FFM 都从 committed facts 派生。
- Query & Planning Services：提供代码影响、DB 影响、变量影响、功能修改规划、新增功能规划、报告构建和 evidence pack 构建，不直接运行重型 analyzer。
- AI / Interface Layer：REST、MCP、UI 和 AI summary/ranking/explanation 都通过 Query & Planning Services 访问数据，不暴露任意 Cypher、SQL、文件读取或 shell 执行。

### 2.2 项目导入与导入审查

CodeAtlas 的导入对象不是直接等同于一个 `Project`。一次本地文件夹选择或上传归档首先形成 `ImportWorkspace`，其中可以包含 0 到多个 Java 项目，也可能混入 C、COBOL、shell-only、文档、无关样例或不可分析内容。导入设计必须区分“导入成功”“项目可识别”“项目可完整分析”和“分析结论可信”。

导入来源：

- `LOCAL_FOLDER`：用户选择本地文件夹，CodeAtlas 只读扫描；可配置是否复制到 managed workspace。
- `UPLOADED_ARCHIVE`：用户上传 zip/tar.gz 等归档，CodeAtlas 解压到受控 workspace。
- 后续增强可支持 `GIT_REPOSITORY`，但 Git URL 不是 MVP 导入前提。

导入模式：

- `DIRECT_IMPORT`：用户已确认分析范围，直接上传或选择目标目录。CodeAtlas 只执行基础安全、可读性和最低限度结构诊断，然后进入画像和分析。
- `ASSISTED_IMPORT_REVIEW`：用户上传大目录、多项目、不完整或混合语言 workspace 时，CodeAtlas 先盘点并输出 Import Review Report，让用户确认项目边界、依赖和不可分析风险。

无论哪种模式，CodeAtlas 都不得默认执行导入项目内的 Gradle/Maven/Ant/shell 脚本。构建文件和脚本默认只做静态读取；需要执行构建时，必须进入独立 worker、超时、只读沙箱和用户确认。

Workspace 盘点必须覆盖：

- 文件能力分级：每个文件在进入 analyzer 前必须先归类为 L1 到 L5，决定后续是结构化分析、半结构化分析、边界识别、仅 inventory 还是跳过。
- 候选项目边界：Gradle/Maven/Ant-like/Eclipse/IDE-only/source-only/unknown legacy layout。
- Ant-like 工程不能只依赖 `build.xml` 文件名识别，必须结合 XML 根节点、Ant task、shell 中的 `ant -f`/`-buildfile` 调用和老式目录布局判断。
- Eclipse/IDE-only 工程通过 `.project`、`.classpath`、`.settings/`、`src/`、`WebRoot/`、`WebContent/`、`WEB-INF/`、`lib/` 等线索识别；缺少构建系统不等于不可分析。
- 入口线索：main 方法、Spring/Struts/JSP、HTML form、JavaScript 静态 HTTP 请求、scheduler/message listener、shell 中的 `java -jar`/`java <mainClass>`、Ant/Maven/Gradle 启动脚本。
- 非 Java 项目：C、COBOL、native library、JCL/copybook、Makefile/CMakeLists 等必须进入 inventory 和 diagnostics，不得静默忽略。

文件能力分级：

| 等级 | 能力 | 典型文件 | 主要产出 | 默认置信度边界 |
| --- | --- | --- | --- | --- |
| L1 | 结构化分析 | `.java`, `.class`, `.jar`, `.xml`, `.properties`, `.yml`, `.yaml`, `.sql`, `.jsp`, `.jspx`, `.tag`, `.tagx`, web root 内或被页面引用的 `.html`, `.htm`, `.js` | `Class`, `Method`, `JspPage`, `HtmlPage`, `ClientRequest`, `CALLS`, `ROUTES_TO`, `SUBMITS_TO`, `CALLS_HTTP`, `BINDS_TO`, `READS_COLUMN`, `WRITES_COLUMN`, `USES_CONFIG` | 可到 `CERTAIN`，缺 classpath、上下文或定位不完整时降级；JS 动态语义默认不高于 `LIKELY/POSSIBLE` |
| L2 | 半结构化分析 | `.sh`, `.bat`, `.cmd`, `.ps1`, `.gradle`, `pom.xml`, Ant-like XML, `.classpath`, `.project`, 独立 `.html`, `.htm`, `.js`, `.css` | `ShellScript`, `ExternalCommand`, `EntryPoint`, `ClientNavigation`, `ScriptResource`, `ProjectDependency`, `SourceRoot`, `ConfigKey` | 通常为 `LIKELY/POSSIBLE`，静态明确时可为 `CERTAIN` |
| L3 | 入口/边界识别 | `.exe`, `.dll`, `.so`, `.a`, `.lib`, `.c`, `.h`, `.cpp`, `.hpp`, `.cbl`, `.cob`, copybook, `.jcl` | `BOUNDARY_ONLY Project`, `NativeLibrary`, `BoundarySymbol`, `ExternalCommand`, `analysisBoundary` | 不高于 `POSSIBLE/LIKELY`，不输出内部确定链路 |
| L4 | 仅 inventory | `.txt`, `.md`, `.csv`, `.json`, 未被 web root 或页面引用链确认的 `.html`, `.css`, `.js`, images, PDF, Office 文档 | 文件存在、hash、大小、可能用途、后续可选解析线索 | 默认不产生影响事实 |
| L5 | 不支持/跳过 | 超大文件、未知二进制、损坏文件、加密压缩包、路径非法文件、无法解码文件 | `ImportDiagnostic`, blind spot | 不产生影响事实，必须进入 diagnostics |

文件能力分级结果必须进入 `ImportReviewReport` 和后续 analysis coverage。CodeAtlas 必须告诉用户哪些文件被结构化分析、哪些只是边界识别、哪些被跳过。HTML/JS 的能力分级依赖上下文：位于 web root、被 JSP/HTML 引用、被 Struts/Spring view resolver 指向或被 script/include 链引用时，可以进入 Web Client Analyzer；孤立 HTML/JS 默认只能 inventory 或半结构化分析。某些 L4 文件例如 JSON/CSV/HTML/JS 在特定项目中可能是配置、数据字典或前端入口，后续 adapter 可以按证据升级到 L2/L1，但不能在没有解析器契约时静默生成确定事实。

L3 边界符号盘点：

- `BOUNDARY_ONLY` 不等于“完全无信息”。L3 文件应尽量提取可见边界符号，例如 native library 名称、JNI 导出名、C header function、COBOL program id、copybook 名称、JCL step、shell 外部命令和可执行文件名。
- 边界符号使用 `BoundarySymbol`、`NativeLibrary`、`ExternalCommand` 或更具体的后续 adapter 节点表示，默认只生成 `POSSIBLE/LIKELY` 候选关系。
- Java 与边界符号的连接必须来自明确 evidence，例如 `System.loadLibrary`、JNI naming convention、shell 命令、配置文件、Makefile/CMake/JCL 引用或用户确认。
- L3 analyzer 不得宣称理解 C/COBOL/native 内部链路，也不得把边界关系提升为确定 Java 调用；报告必须显示 `analysisBoundary=NATIVE|EXTERNAL|COBOL_BOUNDARY|C_BOUNDARY`。

候选项目状态：

```text
READY
  结构清楚，源码/配置/入口足够，能进行主线分析。

PARTIAL
  可以降级分析，但缺依赖、缺源码、缺配置、classpath 不完整或入口不完整。

BOUNDARY_ONLY
  识别到非 Java、native、shell-only 或外部可执行内容，只作为边界/外部依赖/调用目标建模。

UNSUPPORTED
  识别到项目或语言，但当前没有对应 analyzer，不能分析内部链路。

BROKEN
  文件损坏、编码失败、压缩包异常、路径非法或构建描述不可解析。

UNKNOWN
  像项目或入口，但证据不足，需要用户确认。
```

导入审查报告 `ImportReviewReport` 应包含：

- workspace 总览：来源、文件数、总大小、候选项目数、各状态数量、建议导入模式。
- 项目清单：每个候选项目的语言、构建线索、框架线索、入口线索、状态、置信度和证据文件。
- 项目关系：`DEPENDS_ON`、shell 调用、native 边界、共享 jar/lib、共享表/文件等候选关系，必须带 confidence。
- 覆盖能力：Java 源码、字节码、JSP/Struts、HTML/JS 客户端行为、MyBatis/SQL、shell、C/native、COBOL、依赖解析的覆盖状态。
- 盲区和风险：缺失依赖、无法解析脚本、动态 shell、unsupported language、缺源码、未知项目边界。
- 建议用户确认事项：include/exclude 项目、标记共享库、补充依赖、指定 source root/lib/web root/scripts、忽略无关目录。
- 推荐分析计划：建议运行哪些 analyzer，哪些项目只能降级分析，哪些只建边界节点。

如果用户选择 `ASSISTED_IMPORT_REVIEW`，存在 `PARTIAL`、`BOUNDARY_ONLY`、`UNSUPPORTED`、`BROKEN` 或 `UNKNOWN` 项目时，正式分析前必须展示警告并获得用户确认。确认内容作为 `AnalysisScopeDecision` 进入分析元数据和最终报告。`DIRECT_IMPORT` 不强制导入审查，但如果发现严重问题，例如没有 Java 文件、压缩包不安全、主体明显是非 Java 或路径不可读，仍必须阻止或警告。

报告必须区分：

```text
未发现影响
  当前已覆盖分析范围内没有找到影响证据。

无法分析影响
  导入不完整、语言不支持、依赖缺失、脚本动态或用户排除范围导致没有覆盖。
```

导入审查与 AI 的关系：

- CodeAtlas 程序负责扫描目录、解析构建线索、提取入口线索并构建结构化 `WorkspaceProfileEvidence`。
- AI Provider 只接收 evidence pack，返回受约束的 `WorkspaceProfile` / `ProjectProfile` 建议，例如项目类型、边界、优先分析 scope、风险点和候选关系。
- CodeAtlas 必须校验 AI 输出，只允许引用已存在的文件、目录、模块和证据；AI 建议不得直接写入确定性图谱事实。
- 用户确认和 CodeAtlas analyzer 验证后，候选关系才能进入图谱，并按 `CERTAIN/LIKELY/POSSIBLE` 保留来源和证据。

用户确认事实治理：

- 用户确认默认产生 `USER_CONFIRMED` evidence，而不是无条件覆盖 analyzer 事实。
- 只有具备 project 写权限的用户才能把候选提升为用户确认事实；确认记录必须包含 userId、reason、confirmedAt、scope、原 candidate artifact/evidence 和过期规则。
- 用户确认事实必须绑定 snapshot 或 schema version。后续分析发现相关 source file、配置、SQL、DB schema 或入口 scope 变化时，确认事实必须标记 `staleAgainstSnapshot=true`，不得继续作为 `CERTAIN` 路径使用。
- 用户可以撤销确认；撤销只 tombstone 对应 `USER_CONFIRMED` relation family，不删除 analyzer 事实和原始 evidence。
- analyzer 的新 `CERTAIN` evidence 与用户确认冲突时，报告必须显示冲突并要求复核，不能静默按任一侧覆盖。
- AI-assisted candidate 不能仅凭用户一次点击提升为底层代码事实；必须保留为 planning/report artifact，或在 analyzer/配置/图谱证据补齐后再进入 active graph。

### 2.3 端到端执行流

CodeAtlas 的主流程必须把“输入文件”和“用户看到的结论”拆开，中间每一步都保留可审计的中间产物。典型流程如下：

```text
1. ImportWorkspace
   用户选择本地文件夹或上传归档，系统生成 workspaceId 和原始文件 inventory。

2. Workspace Profile
   Workspace Profiler 读取文件树、构建线索、入口线索、依赖线索和不可分析内容。

3. Import Review
   输出 ImportReviewReport；如存在 PARTIAL/BOUNDARY_ONLY/UNSUPPORTED/BROKEN/UNKNOWN，用户确认 AnalysisScopeDecision。

4. Refresh Planning
   Analysis Planner 根据确认范围、历史 snapshot、文件 hash、Git diff、Git commit/pull 或本地文件变化生成关系刷新用的 AnalysisRun。Git diff 在这里的主要职责是 changed scope detection，不默认生成报告。

5. Analyzer Execution
Worker 并行运行 Spoon/Jasper/Web Client Analyzer/JSqlParser/ASM/ClassGraph/framework/shell adapter，必要时触发可选侧车。

6. Fact Staging
   Analyzer 只写 staging facts、evidence 和 diagnostics；不直接影响 active graph。

7. Commit
   原子写入在 FactStore 同一事务内完成校验、upsert 和 tombstone，发布 committed snapshot；失败回滚旧事实无损。小项目/dev/CI 可使用 `InMemoryFactStore`，生产和多用户部署默认使用 Neo4j；无论后端如何，`FactRecord/Evidence + committed snapshot` 才是事实源。

8. Derived Index Build
   成功 commit 后重建受影响 Symbol Index、JVM cache、Vector chunk、可选 FFM index。

9. Active Snapshot Ready
   最新 committed snapshot、SymbolIndex、CodeNavigationIndex 和缓存可供后续需求探索、影响查询和测试规划使用。旧报告标记 stale/upgrade available，但不会因为代码刷新自动替换用户当前问题的报告。

10. Requirement / Query / Planning
    用户输入需求、bug、设计片段、页面/字段/菜单名、自然语言问题或显式 symbol/db 查询。Query Services pin snapshot/cache，AI 先把输入转成 `RequirementInput/ChangeIntent/ExplorationPlan`，再由 CodeAtlas 执行结构化检索和图谱查询，生成影响报告、修改计划、测试计划或规划 artifact。

11. AI Explanation
    AI 只消费 evidence pack 和结构化查询结果，生成摘要、排序、候选解释、风险说明、测试建议和测试数据建议。

12. UI / REST / MCP
    返回结构化结果、证据路径、置信度、盲区、pending/deep 状态和可继续展开入口。
```

查询不能跨步骤读取未提交状态。用户在 UI 看到的任何影响路径，都必须能追溯到 committed `FactRecord`、`Evidence` 或明确标记的 candidate artifact。

关系刷新与报告查询必须分离：

- Repository refresh 由导入、Git pull/commit、Git diff、文件 hash 变化或用户手动刷新触发，目标是更新 FactStore active view、navigation index、symbol index 和缓存。
- Impact query / requirement planning 由用户问题、需求、bug、设计书片段、页面/字段/菜单名或显式 API 调用触发，目标是生成报告和规划 artifact。
- refresh 成功后可以让旧报告 stale，也可以为 watch/subscription 产生 notification candidate，但不能默认把每次 Git 提交解释成一次用户要看的影响报告。
- PR/CI impact report 是可选产品工作流，消费 refresh 后的 snapshot 和显式 diff/query，不是核心关系刷新流程的前置条件。

### 2.4 关键对象生命周期

| 对象 | 产生阶段 | 主键 | 是否进入 active graph | 生命周期 |
| --- | --- | --- | --- | --- |
| `ImportWorkspace` | 导入 | `workspaceId` | 否，作为容器元数据 | 随导入记录保留，可归档 |
| `Project` | 画像/用户确认 | `projectId` / `projectKey` | 是 | 随 snapshot 演进 |
| `ImportReviewReport` | 导入审查 | `artifactId` | 否 | 绑定 workspace 和 review run |
| `AnalysisScopeDecision` | 用户确认 | `artifactId` | 否 | 绑定 workspace、project 和 analysisRun |
| `AnalysisRun` | 分析计划 | `analysisRunId` | 否 | 记录整体分析状态 |
| `ScopeRun` | analyzer 执行 | `scopeRunId` | 否 | tombstone 和重试边界 |
| `FactRecord` | staging/commit | `factKey` | 是，提交后参与 active view | 使用 snapshot interval 管理 |
| `Evidence` | analyzer 输出 | `evidenceKey` | 可在 Neo4j 或 Evidence Store | 与 fact/artifact 可追溯绑定 |
| `Materialized Edge` | commit 后 | `factKey` 或 edge id | 是 | 可重建查询边 |
| `FeatureSeed` / `FeatureScope` | Feature Planner | `artifactId` | 默认否 | staleAgainstSnapshot 管理 |
| `ChangePlanReport` | Feature Planner / Report Builder | `artifactId` | 否 | 可被用户保存或废弃 |
| `VectorChunk` | derived index | `chunkHash` + `snapshotId` | 否 | 随 snapshot/stale lifecycle 更新 |

### 2.5 失败和降级语义

失败不能只有“报错”一种形态。系统需要把失败影响范围、是否可继续查询、是否需要用户确认描述清楚。

| 状态 | 含义 | 用户可见行为 | 是否阻塞查询 |
| --- | --- | --- | --- |
| `FAILED_IMPORT` | workspace 无法读取、归档损坏或安全检查失败 | 阻止分析，展示修复建议 | 是 |
| `PARTIAL_PROFILE` | 画像完成但存在未知项目、缺依赖或 unsupported language | 展示 Import Review 警告 | 否，需确认 |
| `SCOPE_FAILED` | 某 analyzer/scope 失败 | 报告 pending/partial，并展示失败 scope | 否 |
| `STAGING_REJECTED` | fact/evidence/schema 校验失败 | 不发布新 facts，保留旧 active view | 否 |
| `COMMIT_FAILED` | 提交或 active view 切换失败 | 回滚到上一 snapshot | 否 |
| `CACHE_BUILD_FAILED` | 派生缓存构建失败 | 回退 committed FactStore active facts；生产通常为 Neo4j | 否 |
| `QUERY_TRUNCATED` | 查询超深、超时、热点节点或 fanout 过大 | 返回截断报告和继续展开入口 | 否 |
| `AI_UNAVAILABLE` | AI 服务不可用或输出校验失败 | 返回结构化事实和非 AI 摘要 | 否 |

## 3. 开源基础选型

### 3.1 首选组合

```text
MVP Core: Spoon + ASM/ClassGraph + CodeAtlas Impact Flow Engine + Apache Jasper + 内置 JSP token/form 容错提取器 + 内置 HTML/JS static client analyzer + JSqlParser + FactStore（dev/CI 可用 InMemory，生产默认 Neo4j）+ JVM InMemoryGraphCache
MVP Intelligence/Interface: Code Graph RAG + Agent + REST; MCP optional integration surface
Bytecode Enhancement: ProGuardCORE
Deep Sidecars: Joern + Tabby + Fraunhofer CPG POC
Research/Optional: WALA + SootUp/Heros + Tai-e + Doop + FFM OffHeapGraphIndex
```

### 3.2 详细选型

| 能力 | 主选 | 备选/辅助 | 用途 |
| --- | --- | --- | --- |
| Java 源码 AST | Spoon | JavaParser、Eclipse JDT Core | Spoon 做主源码模型；MVP 快速路径只对 changed scope 和缓存失效 scope 运行 Spoon，不做全项目同步重扫；JavaParser 做轻量快速扫描，JDT 做 project model、classpath、binding、diagnostics 和缺失类型识别兜底 |
| 代码导航索引 | CodeNavigationIndex | JDT LS / LSIF-like adapter | 持久化 definition/reference/hover/document symbol 等导航事实，服务 UI 下钻、候选选择和 evidence 定位；它是导航索引，不是影响路径事实源 |
| 业务影响流 | CodeAtlas Impact Flow Engine | Joern、WALA、SootUp/Heros、Tai-e 仅作增强候选 | 默认回答代码变更、DB 变更、变量影响、功能修改和新增功能落点；Method Summary 是内部摘要机制 |
| 字节码轻量扫描 | ASM、ClassGraph | ProGuardCORE | 快速 classpath、注解、继承实现、jar 元数据扫描；MVP 中 ProGuardCORE 只作为可选 class 结构读取补强，不产出确定性 method summary 或 data-flow fact |
| 深度 CPG / slicing | Joern | Fraunhofer CPG、Plume | 离线深度 slicing、复杂 data-flow/path evidence、CPG 设计参考；默认不进入 PR 快速关键路径 |
| 安全污点链路 | CodeQL / Tabby sidecar | Semgrep taint 可后续评估 | 危险 sink、反序列化、命令执行、SQL 注入等安全链路增强；不作为业务影响主事实源，结果进入 security candidate/report artifact |
| 高级静态分析研究 | WALA | SootUp/Heros、Tai-e、Doop | 指针分析、多态、跨方法数据流、taint analysis 的 POC/增强 worker；按 license、稳定性和 benchmark 决定是否接入 |
| JSP 解析 | Apache Jasper | 内置 JSP token/form 容错提取器；第三方 HTML fallback 仅作后续可选评估 | Jasper 负责 JSP 服务端语义；容错提取器只提取 form/input/tag/html 结构，不作为确定 Java 语义来源 |
| HTML/JS 客户端行为 | 内置 Web Client Analyzer | standalone HTML 可评估 jsoup；JS AST parser/Tree-sitter 作为增强评估 | 静态提取 HTML form/input/link/script、`fetch/XMLHttpRequest/$.ajax/axios`、`form.submit`、`location.href` 和请求参数；不执行 JS，不启动业务页面 |
| SQL 解析 | JSqlParser + MyBatis dynamic SQL preprocessor + SqlVariantEvaluator |  | 解析 SQL 表、字段、where 条件和读写类型；动态 SQL 先做 XML/OGNL 分支建模和 bounded variant evaluation，再交给 SQL parser；超预算或无法求值时降级为 `POSSIBLE/PARTIAL` |
| Git diff | JGit | 原生 git 命令 | 读取 PR、commit、diff、历史变更 |
| Shell/脚本入口 | 轻量 token parser | shellcheck parser、PowerShell parser 可后续评估 | MVP 只提取显式 Java 启动、脚本参数、配置引用和外部命令候选；复杂动态脚本标为 `POSSIBLE` |
| 图数据库 | Neo4j | Apache AGE 可后续评估 | 代码知识图谱、路径查询、可视化 |
| Neo4j 可选插件 | 默认不依赖 APOC/GDS | APOC、Neo4j Graph Data Science | 只作为部署可选增强；路径查询和架构指标必须有纯 Cypher/JVM cache fallback |
| JVM 内存缓存 | primitive adjacency cache |  | MVP 阶段 caller/callee 和影响路径加速 |
| JVM 外内存 | JDK FFM API | mmap、RocksDB 可辅助 | benchmark 驱动增强，处理超大调用边、bitmap、frontier queue、临时索引 |
| 自动迁移/修复 | OpenRewrite |  | 后期做旧框架迁移和规则修复 |
| 向量/RAG | Neo4j Vector Index | pgvector、OpenSearch、Qdrant | 语义搜索和 Code Graph RAG |
| 外部工具协议 | REST API | MCP 可选 | REST 是 CodeAtlas 默认服务接口；MCP 是对 AI/IDE/Agent 的可选标准集成面，不引入新的分析语义 |
| 可视化前端 | React + TypeScript + Vite + Semi Design | TanStack Table、AG Grid Community、Cytoscape.js、React Flow、Monaco Editor/CodeMirror、lucide-react | 项目总览、影响报告、调用图、变量影响、DB 影响、功能规划、JSP/Web Client 链路和 AI 问答 |

### 3.3 依赖分层

| 层级 | 默认进入发行包 | 示例 | 进入条件 |
| --- | --- | --- | --- |
| MVP runtime | 是 | Spoon、ASM、ClassGraph、Apache Jasper、内置 Web Client Analyzer、JSqlParser、FactStore SPI、JGit、Semi Design | license review 通过，性能和功能 fixture 通过；生产发行包应包含 Neo4j driver |
| MVP optional runtime | 按部署开关 | ProGuardCORE、Neo4j Vector Index | 缺源码/向量召回场景需要，且可关闭 |
| Deep sidecar | 否，独立 worker | Joern、Tabby、Fraunhofer CPG POC | 独立进程、资源隔离、结果可降级 |
| Research adapter | 否 | WALA、SootUp/Heros、Tai-e、Doop | license、维护状态、benchmark 和准确率证明 |
| Performance plugin | 否 | FFM OffHeapGraphIndex | Neo4j/JVM cache benchmark 证明瓶颈和收益 |
| Migration assistant | 否 | OpenRewrite | 进入修复/迁移产品线后再启用 |

默认发行包只包含能支撑主路径闭环的依赖。任何 deep sidecar 或 research adapter 都不能成为“影响报告可用”的前置条件。

### 3.4 选型约束

- JSP 容错结构提取先使用内置轻量 token/form 提取器，后续再按 license、维护状态和解析质量评估第三方 HTML fallback。
- JSP 不直接使用 jsoup 解析。jsoup 适合 HTML，不适合原始 JSP，只能作为已退化为近似 HTML 片段后的辅助 fallback。
- HTML/JS 客户端行为默认使用静态 analyzer。MVP 不执行 JavaScript、不驱动浏览器、不访问真实后端；只解析源码中可见的 form、link、script 引用、静态 URL、HTTP method 和参数名。动态拼接、事件委托、运行时 DOM 修改、前端框架 runtime 行为必须降级为 `POSSIBLE` 并记录 `analysisBoundary=CLIENT_JS_DYNAMIC`。
- Spoon 应作为 Java 源码分析主引擎，而不是只用 JavaParser。
- CodeAtlas Impact Flow Engine 是默认业务影响主事实源；外部深度分析器不能直接覆盖它产出的 `CERTAIN` facts，只能补充候选、路径证据和降级说明。
- Impact Flow Engine 不追求替代 Joern/WALA/SootUp/Tai-e 的完整指针分析或全程序数据流；它只覆盖确定性强、可解释、可增量、可用 fixture 和 benchmark 验证的业务传播规则。
- Joern 优先作为深度 CPG/slicing 侧车；Tabby 只用于安全污点链路；Fraunhofer CPG 先作为设计参考和 POC，不作为默认运行时依赖。
- CodeQL 可以作为安全/污点 sidecar 评估和接入，输出 security finding、taint path 和 evidence artifact；它不参与业务影响主路径，也不写入确定性业务 `CALLS/BINDS_TO` facts。
- WALA、SootUp/Heros、Tai-e、Doop 都不进入 MVP 关键路径，只有在 license、稳定性、速度和准确率 benchmark 闭环后才接入增强 worker。
- ProGuardCORE 不替代 Spoon；MVP 中它只补强缺源码、只有 class/jar/war 的 class 结构读取。字节码 method summary、data-flow fallback 和缺源码确定性影响链路属于增强能力，未通过 benchmark 和 fixture 前不得作为 supported 结论。
- MVP 不把 Joern、Tabby、WALA、SootUp/Heros、Tai-e、Doop 和 FFM 放入关键路径，先用 Spoon、ASM/ClassGraph、Impact Flow Engine、Jasper、Web Client Analyzer、JSqlParser、FactStore（生产 profile 为 Neo4j）打通业务影响闭环。
- FactStore committed facts 是事实源；生产和多用户部署默认使用 Neo4j 作为代码图谱主存储，dev/CI/小项目可以使用 InMemoryFactStore。JVM 缓存和 FFM 都是高速旁路索引，不是事实源。
- APOC/GDS 不进入 MVP 必需依赖。企业部署启用 APOC/GDS 前必须有 procedure allow-list、安全审计、版本兼容和无插件 fallback；不能把 `apoc.*` 或 `gds.*` 写成唯一查询路径。

### 3.5 Spoon 使用边界

- 全量导入或缓存冷启动时，Spoon 可以按 module/source root 分批构建源码模型。
- PR/commit 快速报告路径不得默认全项目运行 Spoon；只能对 changed Java file、受影响的同源文件、缓存 miss 文件运行 Spoon。
- 10 到 30 秒初版报告主要依赖上一 snapshot 的 active facts、符号索引、FactStore 查询和 JVM primitive adjacency cache；生产 profile 中通常是 Neo4j 查询。
- Spoon 产物缓存保存归一化 symbol、source range、direct call、annotation、field、method summary 和 file hash，不长期持有 `CtElement` 或完整 AST。

## 4. 图谱模型与 Neo4j 落地

Neo4j 是 CodeAtlas 生产部署的代码知识图谱主存储；dev/CI 可以使用同一语义的 InMemoryFactStore。以下图谱模型对两种后端都成立，Neo4j 是生产落地和查询优化的默认实现。

### 4.1 图谱模型分层

图谱模型分为四层，避免把所有概念都塞进同一种节点和关系：

| 层 | 主要对象 | 作用 | 是否作为默认查询事实 |
| --- | --- | --- | --- |
| 代码结构层 | `Project`、`Module`、`SourceFile`、`Class`、`Method`、`Field` | 表示源码、字节码和资源的静态结构 | 是 |
| 框架语义层 | `ApiEndpoint`、`ActionPath`、`JspPage`、`SqlStatement`、`Bean`、`ConfigKey` | 把 Spring/Struts/JSP/MyBatis/XML 等框架语义接到代码结构 | 是 |
| 影响流层 | `EntryPoint`、`ParamSlot`、`ReturnSlot`、`MethodSummary`、`RequestParameter`、`DbColumn` | 支撑代码影响、DB 影响和变量影响查询 | 是 |
| 规划/审查层 | `FeatureSeed`、`FeatureScope`、`ChangePlanReport`、`ImportReviewReport` | 表示用户问题、规划结果、导入审查和报告 artifact | 默认否 |

实现上可以都落在 Neo4j，但查询层必须知道每层的生命周期和置信度边界。规划/审查层不能反向覆盖代码结构层和框架语义层事实。

### 4.2 典型节点

```text
Project
ImportWorkspace
Module
SourceFile
Package
Class
Interface
Method
Field
Annotation
Bean
EntryPoint
ApiEndpoint
ActionPath
BatchJob
ScheduledTask
CliCommand
MainMethod
MessageListener
MessageQueue
MessageTopic
DomainEvent
Schedule
CronTrigger
JobParameter
ShellScript
ExternalCommand
JspPage
JspForm
JspInput
JspTag
JspInclude
JspForward
JspScriptlet
JspExpression
TagLibrary
TagBoundary
HtmlPage
HtmlForm
HtmlInput
HtmlLink
ScriptResource
DomEventHandler
ClientRequest
ClientNavigation
HtmlRenderSlot
ClientInitData
Action
FormBean
FormField
Controller
Service
Dao
Mapper
SqlStatement
SqlParameter
MethodSummary
ParamSlot
ReturnSlot
RequestParameter
RequestAttribute
SessionAttribute
ModelAttribute
DataSource
DbSchema
DbTable
DbColumn
DbIndex
DbConstraint
DbView
ReportDefinition
ReportField
NativeLibrary
BoundarySymbol
ReflectionCandidate
SqlVariant
SqlBranchCondition
ConfigKey
GitCommit
PullRequest
TestCase
CiCheckRun
SavedQuery
WatchSubscription
NotificationRule
ReviewThread
ReviewComment
PolicyRule
PolicyViolation
RiskWaiver
ExportArtifact
ArchitectureHealthReport
ArchitectureMetric
HotspotCandidate
RequirementInput
ChangeIntent
ExplorationPlan
ExplorationResult
FeatureSeed
FeatureScope
ModificationPlan
ChangePlanReport
ChangeItem
RegressionSuggestion
TestPlan
TestDataSuggestion
PostChangeValidationReport
ImportDiagnostic
ImportReviewReport
AnalysisScopeDecision
```

### 4.3 节点建模规则

- `Class`、`Interface`、`Method` 是 Java 代码的事实节点。
- `ImportWorkspace` 表示一次本地文件夹或上传归档导入得到的原始容器；它可以包含多个互相依赖或完全无关的 `Project`。
- `Project` 表示 workspace 内可独立分析、独立出报告或作为依赖/边界建模的 Java 程序、库、工具或非 Java 相邻项目；项目状态可为 `READY/PARTIAL/BOUNDARY_ONLY/UNSUPPORTED/BROKEN/UNKNOWN`。
- `EntryPoint` 是所有可从外部触发 Java 程序执行的统一抽象，Web、batch、scheduler、CLI/main、message listener 和 shell 启动脚本都是入口发现器，不是另一套影响分析主干。
- `Controller`、`Service`、`Dao` 优先作为 `Class` 或 `Method` 的 role label/property，不创建与 `Class` 重复的独立节点。
- Struts1 `Action` 表示框架 action mapping 或 action class role；实际 Java 调用仍落到 `Method`。
- Spring handler 使用 `ApiEndpoint -[:ROUTES_TO]-> Method` 表示，并同时可作为 `EntryPoint` 的一种类型；目标方法保留 Java `Method` 节点。
- JSP 表单使用 `JspForm -[:SUBMITS_TO]-> ApiEndpoint/ActionPath` 表示，不直接跳到 Java 方法，避免路由解析和页面解析耦合；它也是发现 Web 入口的证据。
- JSP tag/include/scriptlet/EL 等使用 `JspTag`、`JspInclude`、`JspForward`、`JspScriptlet`、`JspExpression` 和 `TagLibrary` 表示；无法解析的 custom tag 使用 `TagBoundary`，不能伪装成确定调用。
- JSP/HTML/Struts 表单字段使用 `JspInput`、`HtmlInput`、`RequestParameter`、`FormBean`、`FormField` 和 `ParamSlot` 建模，区分页面结构、请求参数、ActionForm 字段和 Java 方法入参。
- 独立 HTML 页面、JSP 内的 HTML 片段和外链 JS 由 Web Client Analyzer 建模。`HtmlPage/HtmlForm/HtmlInput/HtmlLink/ScriptResource/DomEventHandler/ClientRequest` 表示浏览器侧结构和静态请求线索，不等于后端 `Method`。
- `ClientRequest` 表示静态可见的浏览器侧提交、导航或 HTTP 请求。它只能路由到 `ApiEndpoint/ActionPath`，不得直接跳到 Java 方法；运行时拼接、事件委托、前端框架动态路由必须标记 `CLIENT_JS_DYNAMIC`。
- batch、scheduler、message listener、CLI/main 方法使用 `EntryPoint -[:INVOKES]-> Method` 或具体入口节点到 `Method` 的 `INVOKES/ROUTES_TO` 关系表示。
- 异步和调度不是同步调用。MQ/topic/event/schedule 使用 `MessageQueue`、`MessageTopic`、`DomainEvent`、`Schedule`、`CronTrigger`、`JobParameter` 建模，并通过 `PUBLISHES_TO/CONSUMES_FROM/SCHEDULES/TRIGGERS` 表达边界。
- shell 脚本使用 `ShellScript -[:INVOKES]-> MainMethod/CliCommand/BatchJob`、`ShellScript -[:CALLS_COMMAND]-> ExternalCommand` 和 `ShellScript -[:USES_CONFIG]-> ConfigKey` 表示；动态拼接、条件分支或无法解析的命令只能作为 `POSSIBLE` 候选事实。
- MyBatis 使用 `Method -[:BINDS_TO]-> SqlStatement` 表示 Mapper 方法到 SQL statement。
- MyBatis 动态 SQL 使用 `SqlStatement`、`SqlVariant`、`SqlBranchCondition` 表示，避免把一个运行时拼装 SQL 误写成单一确定 SQL。
- Impact Flow 的边界变量使用 `ParamSlot`、`ReturnSlot`、`RequestParameter`、`RequestAttribute`、`SessionAttribute`、`ModelAttribute`、`SqlParameter` 表示；方法内部局部变量默认只进入 `MethodSummary` 和 evidence，不作为主图节点。
- 数据库对象使用 `DataSource`、`DbSchema`、`DbTable`、`DbColumn`、`DbIndex`、`DbConstraint`、`DbView` 表示，SQL statement 到表字段的读写关系单独建边。
- 报表资源使用 `ReportDefinition`、`ReportField` 表示，默认作为 adapter 产出的候选或增强 facts，不能替代 SQL/DB 主事实。
- JNI/native 边界使用 `NativeLibrary` 或 `ConfigKey` 表示，影响路径遇到 native 边界时返回 boundary，而不是静默截断或误建同步调用。
- 反射调用使用 `ReflectionCandidate` 和候选关系表示。`Class.forName`、`Method.invoke`、`Constructor.newInstance`、Spring/XML/properties class name 等线索不能直接伪装成确定 `CALLS`。
- `BoundarySymbol` 用于 C/C++/COBOL/native/JCL/shell 等非 Java 边界中可见的导出名、程序名、命令名或文件级符号；它只帮助解释和候选连接，不表示 CodeAtlas 理解其内部实现。
- `FeatureSeed` 表示用户输入的功能描述、关键词、接口、页面、类、表、字段或历史提交线索；它是检索种子，不是确定事实。
- `FeatureScope` 表示一组被证据归并到同一功能的入口、代码、SQL、DB、配置和测试候选；它必须保留 seed、match reason、confidence 和 evidence。
- `RequirementInput` 表示用户提供的原始需求、bug 描述、设计书片段、页面/字段/菜单名、截图 OCR 文本或补充说明；它是用户意图来源，不是事实。
- `ChangeIntent` 表示 AI 从 `RequirementInput` 中提取的业务对象、动作、字段、入口、约束、非目标和未知项；它必须保留原文引用和不确定项。
- `ExplorationPlan` 表示为了理解需求而计划执行的关键词检索、symbol search、navigation 查询、图谱查询、DB 影响查询、变量追踪、历史提交和测试检索。
- `ExplorationResult` 表示探索后得到的候选入口、页面、Action/Controller、Service、Mapper/DAO、SQL、表字段、配置、测试、证据和盲区。
- `ModificationPlan` / `ChangePlanReport` / `ChangeItem` / `RegressionSuggestion` 用于回答“修改/新增功能需要改哪里、查哪里、测哪里”，不直接替代底层代码事实。
- `TestPlan` 表示建议测试入口、测试场景、断言、回归范围、前置数据和执行方式。
- `TestDataSuggestion` 表示正常数据、边界数据、异常数据、历史兼容数据、DB 初始数据、页面/API/batch 输入样例和预期结果；它必须标注来源和人工准备项。
- `PostChangeValidationReport` 表示用户修改代码后，基于新 snapshot 和原 `ModificationPlan/TestPlan` 检查哪些必须修改项已覆盖、哪些遗漏、是否出现新增影响路径和测试计划是否需要更新。
- `SavedQuery`、`WatchSubscription`、`NotificationRule`、`ReviewThread`、`ReviewComment`、`PolicyRule`、`PolicyViolation`、`RiskWaiver`、`ExportArtifact` 是产品工作流 artifact，不参与底层 active graph 影响路径搜索。
- `CiCheckRun` 表示外部 CI/PR 集成的一次报告投递或检查结果，它引用 `ImpactReport/ChangePlanReport`，不产生新的代码事实。

产品工作流 artifact 使用 `artifactId`，不能作为 `CALLS/BINDS_TO/READS_COLUMN/WRITES_COLUMN` 的 source/target。它们可以引用 facts、reports、paths、evidence 和 symbols，但不能改变底层 confidence、priority 或 active view。

数据库 schema 事实来源：

- `DbTable/DbColumn/DbView/DbIndex/DbConstraint` 不能只从 SQL 使用处反推为确定 schema。确定 schema 事实必须来自 DDL、migration 文件、数据库导出 schema、MyBatis resultMap/配置、JPA annotation、可验证的数据字典或用户确认。
- SQL parser 发现的 table/column 默认先作为 `SQL_REFERENCED_DB_OBJECT` evidence；只有能绑定到 schema source 或用户确认时，才升级为 canonical `DbTable/DbColumn`。
- 当只有 SQL 文本证据而没有 schema source 时，DB impact 可以报告 read/write/display 候选，但 column-level 结论不得高于 `LIKELY`；存在 `select *`、别名未解、动态 SQL 或多 datasource 时必须降级到 table-level 或 `POSSIBLE`。
- DDL/migration 删除、重命名和类型变更必须建模为 schema change artifact，并与受影响 SQL/Mapper/入口路径分开展示；不得把“字段在 SQL 中出现”直接解释为“字段真实存在且类型已知”。
- 视图、同义词、存储过程和跨 schema 引用在 MVP 中默认是 boundary/candidate；除非有解析器和 schema evidence，否则不得生成确定 column-level 传递影响。
- 跨项目共享数据库时，schema identity 以 `datasourceKey + schemaKey` 为边界；同名表字段不能跨 datasource 自动合并。

MyBatis `SqlVariantEvaluator`：

- `SqlVariantEvaluator` 只做离线、静态、预算受控的 MyBatis XML 动态 SQL 展开；不得连接真实数据库，不得执行 mapper Java 代码，不得调用用户函数。
- `<if>/<choose>/<when>/<otherwise>/<trim>/<where>/<set>/<foreach>/<include>` 生成有限 `SqlVariant`，每个 variant 必须保留 branch condition、参数名、include 展开版本、unresolved OGNL 和 completeness。
- OGNL 只允许解析可见属性路径、常量比较、布尔组合和集合空/非空候选；方法调用、运行时对象、动态表名、动态列名、`${}` 文本替换和 provider SQL 默认降级为 `POSSIBLE/PARTIAL`。
- variant 数量、foreach 展开、include 深度和解析耗时必须有 profile 预算；超预算时停止展开并标记 `analysisBoundary=MYBATIS_DYNAMIC_LIMIT`。
- `SqlVariantEvaluator` 输出的是 SQL parser 输入和 evidence，不直接提升 DB schema 事实；column-level 仍需绑定 schema source。

### 4.4 典型关系

```text
CONTAINS
DECLARES
DEPENDS_ON
CALLS
IMPLEMENTS
EXTENDS
INJECTS
ROUTES_TO
INVOKES
SUBMITS_TO
CALLS_HTTP
NAVIGATES_TO
LOADS_SCRIPT
HANDLES_DOM_EVENT
PUBLISHES_TO
CONSUMES_FROM
SCHEDULES
TRIGGERS
BINDS_TO
BINDS_PARAM
AUTO_BINDS_TO
CONFIGURES_PROPERTY
INTERCEPTS
HAS_PARAM
RETURNS
SUMMARIZES
CALLS_COMMAND
READS_PARAM
WRITES_PARAM
READS_REQUEST_PARAM
WRITES_REQUEST_ATTR
READS_SESSION_ATTR
WRITES_SESSION_ATTR
READS_MODEL_ATTR
WRITES_MODEL_ATTR
READS_FIELD
WRITES_FIELD
READS_TABLE
WRITES_TABLE
READS_COLUMN
WRITES_COLUMN
MAPS_TO_COLUMN
CALLS_NATIVE
HAS_NATIVE_BOUNDARY
REFLECTS_TO
HAS_VARIANT
GUARDED_BY
EXPORTS_SYMBOL
REFERENCES_SYMBOL
USES_CONFIG
USES_TAGLIB
INCLUDES
RENDERS_INPUT
FORWARDS_TO
MATCHES
REQUIRES_CHANGE
SUGGESTS_REVIEW
REQUIRES_TEST
CHANGED_IN
IMPACTS
COVERED_BY
HAS_DIAGNOSTIC
CONFIRMED_SCOPE
WATCHES
NOTIFIES
COMMENTS_ON
ACKNOWLEDGES
VIOLATES_POLICY
SUPPRESSED_BY
EXPORTED_AS
```

### 4.5 MVP 最小关系契约

```text
ApiEndpoint -[:ROUTES_TO]-> Method
ActionPath -[:ROUTES_TO]-> Method
JspForm -[:SUBMITS_TO]-> ApiEndpoint/ActionPath
JspPage -[:USES_TAGLIB]-> TagLibrary
JspPage/JspInclude -[:INCLUDES]-> JspPage
JspForm -[:RENDERS_INPUT]-> JspInput
JspInput -[:BINDS_TO]-> RequestParameter/FormField/ParamSlot
JspTag/JspExpression -[:READS_MODEL_ATTR|READS_REQUEST_PARAM|READS_SESSION_ATTR]-> ModelAttribute/RequestParameter/SessionAttribute
JspTag/JspScriptlet -[:WRITES_MODEL_ATTR|WRITES_REQUEST_ATTR|WRITES_SESSION_ATTR]-> ModelAttribute/RequestAttribute/SessionAttribute
JspScriptlet/JspExpression -[:CALLS|READS_FIELD|WRITES_FIELD]-> Method/Field
HtmlPage/JspPage -[:CONTAINS]-> HtmlForm/HtmlInput/HtmlLink/ScriptResource
HtmlPage/JspPage -[:LOADS_SCRIPT]-> ScriptResource
HtmlForm -[:RENDERS_INPUT]-> HtmlInput
HtmlInput/JspInput -[:BINDS_TO]-> RequestParameter/FormField/ParamSlot
HtmlLink -[:NAVIGATES_TO]-> HtmlPage/ApiEndpoint/ActionPath
DomEventHandler -[:HANDLES_DOM_EVENT]-> HtmlForm/HtmlInput/HtmlLink
DomEventHandler -[:SUBMITS_TO|CALLS_HTTP|NAVIGATES_TO]-> ClientRequest/ApiEndpoint/ActionPath
ClientRequest -[:CALLS_HTTP|SUBMITS_TO]-> ApiEndpoint/ActionPath
EntryPoint -[:INVOKES|ROUTES_TO]-> Method
Schedule/CronTrigger -[:SCHEDULES|TRIGGERS]-> EntryPoint/Method
Method -[:PUBLISHES_TO]-> MessageQueue/MessageTopic/DomainEvent
MessageListener -[:CONSUMES_FROM]-> MessageQueue/MessageTopic/DomainEvent
MessageListener/BatchJob/CliCommand -[:HAS_PARAM]-> JobParameter
ShellScript -[:INVOKES|CALLS_COMMAND|USES_CONFIG]-> MainMethod/CliCommand/BatchJob/ExternalCommand/ConfigKey
Method -[:CALLS]-> Method
Method -[:BINDS_TO]-> SqlStatement
DiconComponent -[:AUTO_BINDS_TO|INTERCEPTS|CONFIGURES_PROPERTY]-> Class/Interface/Method/ConfigKey
DataSource -[:CONTAINS]-> DbSchema
DbSchema -[:CONTAINS]-> DbTable
DbTable -[:CONTAINS]-> DbColumn
SqlStatement -[:READS_TABLE|WRITES_TABLE]-> DbTable
SqlStatement -[:READS_COLUMN|WRITES_COLUMN]-> DbColumn
SqlStatement -[:HAS_PARAM]-> SqlParameter
ReportDefinition -[:READS_TABLE|READS_COLUMN|USES_CONFIG]-> DbTable/DbColumn/ConfigKey
ReportDefinition -[:CONTAINS]-> ReportField
ReportField -[:MAPS_TO_COLUMN]-> DbColumn
Method -[:CALLS_NATIVE|HAS_NATIVE_BOUNDARY]-> NativeLibrary/ConfigKey
Method -[:HAS_PARAM]-> ParamSlot
Method -[:RETURNS]-> ReturnSlot
MethodSummary -[:SUMMARIZES]-> Method
FeatureSeed -[:MATCHES]-> EntryPoint/Method/SqlStatement/DbTable/DbColumn/ConfigKey/SourceFile
FeatureScope -[:CONTAINS]-> EntryPoint/Method/SqlStatement/DbTable/DbColumn/ConfigKey/TestCase
ChangeItem -[:REQUIRES_CHANGE|SUGGESTS_REVIEW|REQUIRES_TEST]-> EntryPoint/Method/SqlStatement/DbTable/DbColumn/ConfigKey/TestCase
SavedQuery -[:MATCHES|WATCHES]-> EntryPoint/Method/SqlStatement/DbTable/DbColumn/ConfigKey/FeatureScope
WatchSubscription -[:WATCHES]-> SavedQuery/EntryPoint/Method/DbTable/DbColumn/FeatureScope
ReviewComment -[:COMMENTS_ON]-> Evidence/FactRecord/ChangePlanReport/PathEdge
PolicyViolation -[:VIOLATES_POLICY]-> PolicyRule
PolicyViolation -[:SUPPRESSED_BY]-> RiskWaiver
ExportArtifact -[:EXPORTED_AS]-> ChangePlanReport
Method/ConfigKey -[:REFLECTS_TO]-> ReflectionCandidate/Class/Method/Field
SqlStatement -[:HAS_VARIANT]-> SqlVariant
SqlVariant -[:GUARDED_BY]-> SqlBranchCondition
SqlVariant -[:READS_COLUMN|WRITES_COLUMN]-> DbColumn
NativeLibrary/Project -[:EXPORTS_SYMBOL]-> BoundarySymbol
Method/ShellScript/ConfigKey -[:REFERENCES_SYMBOL]-> BoundarySymbol/NativeLibrary/ExternalCommand
```

### 4.6 关系方向约定

关系方向约定：

| 关系族 | 正向含义 | 反向查询用途 |
| --- | --- | --- |
| `CONTAINS/DECLARES` | 容器到成员 | 从符号回到模块、文件、项目 |
| `CALLS/INVOKES/ROUTES_TO/SUBMITS_TO/CALLS_HTTP` | 触发者到被触发目标 | 从变更方法找入口、页面、客户端请求和外部触发点 |
| `NAVIGATES_TO/LOADS_SCRIPT/HANDLES_DOM_EVENT` | 浏览器侧页面、资源和事件绑定方向 | 从后端入口回溯页面、脚本和触发控件 |
| `BINDS_TO/BINDS_PARAM/HAS_PARAM/RETURNS` | 代码或参数到绑定对象 | 从 SQL/DB/变量回溯上游代码 |
| `READS_COLUMN/WRITES_COLUMN` | SQL 到 DB 字段 | 从 DB 字段找 SQL、Mapper、Service 和入口 |
| `HAS_VARIANT/GUARDED_BY` | 动态 SQL 到变体和分支条件 | 展开或解释 MyBatis 动态 SQL 候选路径 |
| `REFLECTS_TO` | 反射 call site 或配置字符串到候选目标 | 展示反射候选影响，不等同确定调用 |
| `EXPORTS_SYMBOL/REFERENCES_SYMBOL` | 边界文件导出符号、Java/脚本/配置引用边界符号 | 解释 native/C/COBOL/shell 等外部边界 |
| `READS_* / WRITES_*` | 代码或页面表达式到请求/会话/model/字段 | 变量来源、流向和展示影响 |
| `PUBLISHES_TO/CONSUMES_FROM/SCHEDULES/TRIGGERS` | 异步或调度声明方向 | 查找可能触发链路，必须显示 async/scheduled boundary |
| `MATCHES/REQUIRES_CHANGE/SUGGESTS_REVIEW/REQUIRES_TEST` | planning artifact 到候选对象 | 展示规划结果，不参与底层确定影响路径 |
| `WATCHES/NOTIFIES/COMMENTS_ON/ACKNOWLEDGES/VIOLATES_POLICY/SUPPRESSED_BY/EXPORTED_AS` | 产品工作流 artifact 到报告、证据或规则 | 协作、通知、CI 和治理，不参与底层代码影响路径 |

查询服务可以反向遍历关系，但报告必须保留原始关系方向，避免把“被路由到”“被调用”“被 SQL 读写”混成同一种影响。

### 4.7 统一属性

- `identityId`：统一身份 ID；代码、资源和 DB 对象使用 `symbolId`，影响流边界节点使用 `flowId`，功能规划和导入审查产物使用 `artifactId`。
- `sourceType`：`SPOON`、`IMPACT_FLOW`、`METHOD_SUMMARY_FLOW`、`ASM`、`CLASSGRAPH`、`PROGUARDCORE`、`JASPER`、`JSP_TOKEN`、`HTML_TOKEN`、`JS_STATIC`、`SPRING`、`STRUTS`、`XML`、`SQL`、`MYBATIS_DYNAMIC`、`REFLECTION`、`BOUNDARY_SYMBOL`、`SHELL`、`GIT`、`JOERN`、`TABBY`、`WALA`、`SOOTUP_HEROS`、`TAI_E`、`AI_ASSISTED` 等。
- `confidence`：`CERTAIN`、`LIKELY`、`POSSIBLE`、`UNKNOWN`。
- `evidence`：文件路径、行号、XML path、JSP tag、HTML selector、JS call expression、SQL id、commit id。
- `projectId`、`moduleId`、`snapshotId`：用于多项目和增量分析。

### 4.8 统一身份 ID 示例

```text
method://shop/_root/src/main/java/com.foo.OrderService#cancelOrder(Ljava/lang/Long;)V
class://shop/_root/src/main/java/com.foo.OrderService
jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/order/edit.jsp
jsp-input://shop/_root/src/main/webapp/WEB-INF/jsp/order/edit.jsp#form[/order/save.do:post:42:0]:input[userId:text:43:0]
html-page://shop/_root/src/main/webapp/static/user/edit.html
html-input://shop/_root/src/main/webapp/static/user/edit.html#form[/api/users:post:12:0]:input[userId:text:14:0]
script-resource://shop/_root/src/main/webapp/static/js/user-edit.js
client-request://shop/_root/src/main/webapp/static/js/user-edit.js#fetch[/api/users:post:42:0]
sql-statement://shop/_root/src/main/resources/com/foo/OrderMapper.xml#com.foo.OrderMapper.selectById
sql-variant://shop/_root/src/main/resources/com/foo/OrderMapper.xml#com.foo.OrderMapper.selectById:variant[where-phone-present]
db-table://shop/mainDs/public/orders
db-column://shop/mainDs/public/orders#order_id
param-slot://shop/_root/src/main/java/com.foo.OrderService#cancelOrder(Ljava/lang/Long;)V:param[0:Ljava/lang/Long;]
sql-param://shop/_root/src/main/resources/com/foo/OrderMapper.xml#com.foo.OrderMapper.selectById:param[id:BIGINT]
reflection-candidate://shop/_root/src/main/java/com.foo.Factory#create(Ljava/lang/String;)Ljava/lang/Object;#class-for-name[42:0]
boundary-symbol://shop/_root/native/liblegacy.so#Java_com_acme_Legacy_calc
feature-seed://shop/run-20260502-001/6f4a9c
config-key://shop/_root/src/main/resources/application.yml#spring.datasource.url
entrypoint://shop/batch/src/main/java/com.foo.batch.InvoiceJob#main([Ljava/lang/String;)V
shell-script://shop/_root/scripts/run-invoice-job.sh
```

### 4.9 Neo4j 查询与可选插件策略

Neo4j 是生产默认图谱事实存储，但 CodeAtlas 的正确性不能依赖特定插件或企业版功能；dev/CI 后端也必须遵守同一 FactStore active view 语义。

- 生产 Neo4j 查询路径必须使用受控 Cypher、参数化查询、JVM InMemoryGraphCache 和明确的 path search cost budget；FactStore SPI 必须提供等价 query service，不能让后端差异改变报告语义。
- APOC 可以作为可选路径遍历增强，例如热点图、bounded subgraph、去重遍历和运维诊断；启用前必须配置 procedure allow-list、版本兼容、审计和超时。
- Neo4j Graph Data Science 可以作为架构健康指标增强，例如 centrality、community、PageRank、cycle 相关指标；它不能成为影响报告 MVP 的前置条件。
- 所有 APOC/GDS 查询必须有纯 Cypher 或 JVM cache fallback。fallback 结果可能慢或标记 `PARTIAL/TRUNCATED`，但不能返回语义不同的路径。
- MCP、REST 和 UI 不得暴露任意 Cypher/APOC/GDS 调用；只能调用 CodeAtlas 注册的 query service。
- 插件生成的 metric 或候选路径默认是 derived artifact，不能覆盖 committed facts、confidence 或 evidence。

## 5. SymbolId 与跨引擎映射契约

CodeAtlas 需要同时管理代码符号、影响流节点、分析产物、事实和证据。`symbolId` 只用于稳定代码/资源/DB 对象；变量影响边界和功能规划产物不得强行塞进 `symbolId`。

### 5.1 身份类型

```text
symbolId
  稳定代码、资源、配置、SQL、DB 对象和外部边界对象身份。

flowId
  Impact Flow 内部边界节点身份，例如参数槽、返回值槽、request/session/model 属性、SQL 参数和 MethodSummary。

artifactId
  分析产物身份，例如 FeatureSeed、FeatureScope、ChangePlanReport、ImportReviewReport。

factKey
  关系事实身份，由 source identity、target identity、relation type 和 qualifier 生成。

evidenceKey
  证据身份，由 analyzer、scope、文件路径、source range、XML/JSP/SQL path 或外部工具证据位置生成。
```

跨引擎映射要求：

- Spoon、ASM/ClassGraph、ProGuardCORE、Jasper、XML、SQL、Impact Flow、Neo4j、RAG 和深度侧车都必须先映射到 `symbolId`、`flowId` 或 `artifactId`，再生成 `factKey`。
- `symbolId` 是主图 canonical identity；`flowId` 是可重建的影响流边界 identity；`artifactId` 是 query/run 维度的分析产物 identity。
- `FeatureSeed`、`FeatureScope`、`ChangePlanReport`、`ImportReviewReport` 默认使用 `artifactId`，不能作为代码符号参与 `CALLS/BINDS_TO/MAPS_TO_COLUMN` 等确定事实。
- `factKey` 和 `evidenceKey` 不等于 Neo4j 内部 ID，不得依赖数据库自增 ID、写入顺序或线程顺序。

### 5.2 SymbolKind Registry

每个可写入图谱的 kind 必须在 `SymbolKind Registry` 中注册。新增 kind 不能只临时发明 scheme，必须声明以下契约：

```text
kind
identityType: symbolId | flowId | artifactId
parser grammar
required context
canonical fields
display fields
allowed relations
confidence boundary
merge / alias rules
snapshot behavior
```

典型规则：

- `Class`、`Method`、`Field`、`JspPage`、`JspForm`、`JspTag`、`HtmlPage`、`HtmlForm`、`HtmlLink`、`ScriptResource`、`ClientRequest`、`SqlStatement`、`SqlVariant`、`DataSource`、`DbSchema`、`DbTable`、`DbColumn`、`DbView`、`NativeLibrary`、`BoundarySymbol` 使用 `symbolId`。
- `ParamSlot`、`ReturnSlot`、`RequestParameter`、`RequestAttribute`、`SessionAttribute`、`ModelAttribute`、`SqlParameter`、`SqlBranchCondition`、`MethodSummary`、`HtmlInput`、`JspInput`、`DomEventHandler`、`HtmlRenderSlot`、`ClientInitData`、`ReflectionCandidate` 使用 `flowId`。
- `FeatureSeed`、`FeatureScope`、`ChangePlanReport`、`ArchitectureHealthReport`、`ArchitectureMetric`、`HotspotCandidate`、`ImportReviewReport`、`AnalysisScopeDecision` 使用 `artifactId`。
- `flowId` 可以参与 `READS_PARAM/WRITES_PARAM/BINDS_PARAM/MAPS_TO_COLUMN` 等 Impact Flow 事实，但不能伪装成 Java method/class。
- `artifactId` 可以参与 `MATCHES/CONTAINS/REQUIRES_CHANGE/SUGGESTS_REVIEW/REQUIRES_TEST` 等 planning 关系，但不能覆盖 canonical symbol facts。

### 5.3 Canonical SymbolId 基础格式

```text
{kind}://{projectKey}/{moduleKey}/{sourceRootKey}/{ownerQualifiedName}#{memberName}{descriptor}
```

规则：

- `projectKey` 使用项目内稳定 key，不使用本地绝对路径。
- `moduleKey` 使用 Maven/Gradle module path 归一化结果；单模块项目使用 `_root`。
- `sourceRootKey` 使用 `src/main/java`、`src/main/webapp`、`WEB-INF/classes` 等归一化相对路径。
- 文件路径统一使用 `/`，大小写按仓库真实路径保存；比较时在 Windows workspace 中额外保存 `normalizedPathLower` 作为辅助 key。
- Java owner 使用二进制名归一化内部类，例如 `com.foo.Outer$Inner`。
- 方法重载使用 JVM descriptor 作为最终判别，例如 `#cancelOrder(Ljava/lang/Long;)V`。
- 源码展示可保留 source signature，但跨引擎 upsert 必须使用 erased JVM descriptor。
- 泛型统一按擦除类型归一化；泛型原文存入 display metadata。
- 构造器使用 `#<init>(...)V`。
- 静态初始化块使用 `#<clinit>()V`。
- lambda、匿名类、局部类使用编译器/源码位置生成的 synthetic symbol，并标记 `synthetic=true`。
- bridge/synthetic 方法保留独立节点，但通过 `BRIDGES_TO` 或 `SYNTHETIC_OF` 关联到源码方法。
- XML/JSP/HTML/JS/SQL 节点使用逻辑路径 + 局部 id，例如 `jsp-input://{project}/{module}/src/main/webapp/WEB-INF/jsp/order/edit.jsp#form[{stableFormKey}]:input[{stableInputKey}]`、`client-request://{project}/{module}/src/main/webapp/static/js/order.js#fetch[{stableRequestKey}]`。
- MyBatis statement 使用 namespace + statement id；SQL 片段和动态 SQL 节点必须带 XML path 和 statement id。
- `SqlVariant` 的 identity 基于所属 `SqlStatement`、规范化 SQL skeleton、动态分支签名、include 展开版本和 analyzer schema 生成；不能使用扫描顺序作为 variant key。
- `BoundarySymbol` 的 identity 基于 project/module/sourceRoot、边界文件相对路径、符号名、符号类型和导出来源生成；同名 native export、COBOL program id、shell command 不得跨 project 或文件自动合并。

### 5.6 CodeNavigationIndex 身份与边界

`CodeNavigationIndex` 保存 LSIF/LSP-like 的导航事实，用于 UI 跳转、候选选择、证据定位和语义搜索 seed，不作为影响路径事实源。

导航对象：

- `DefinitionSpan`：符号定义位置，绑定 `symbolId`、source file、range 和 analyzer source。
- `ReferenceSpan`：符号引用位置，绑定 `symbolId`、source file、range、read/write/call/import 等 reference kind。
- `HoverInfo`：类型、签名、doc 摘要和 deprecation 信息，只用于展示。
- `DocumentSymbol`：文件内结构树，服务 UI outline 和候选选择。

规则：

- JDT/JDT LS、Spoon、ASM/ClassGraph 和后续 LSIF-like adapter 可以共同产生 navigation records，但必须先映射到 canonical `symbolId`。
- navigation records 可以帮助用户选择候选、打开 evidence 和定位源码；不得直接生成 `CALLS/BINDS_TO/READS_COLUMN/WRITES_COLUMN` 等影响事实。
- navigation index 与 FactStore snapshot 绑定，查询必须 pin `snapshotId`；source file hash 变化后，对应 navigation shard 标记 stale。
- navigation record 缺 classpath、binding 失败或符号歧义时，必须标记 `navigationConfidence=PARTIAL`，不能被 Feature Planner 当作确定修改范围。

映射要求：

- Spoon 负责 source signature、源码行号和 sourceRoot。
- Impact Flow Engine 负责代码影响、DB 双向影响和变量影响的底层 facts；其中 Method Summary 子模块负责源码级参数流、返回值流、字段流、DTO/Form/Map/request/session/model 传播和 SQL 参数流。Feature Planner 是 Query & Planning Services，消费 Impact Flow facts、历史提交、搜索和 AI 候选，输出 `FeatureSeed/FeatureScope/ChangePlanReport` artifact，不能覆盖底层代码事实。所有影响事实必须绑定 source evidence、framework evidence、SQL evidence 或 bytecode fallback evidence。
- ASM/ClassGraph 负责 JVM descriptor、字节码结构和轻量调用线索；MVP 中 ProGuardCORE 只作为可选 class 结构读取补强。字节码 method summary fallback 属于增强能力，未通过 fixture 和 benchmark 前只能产出候选证据。字节码结果必须映射回 canonical `symbolId`。
- Joern、Tabby、WALA、SootUp/Heros、Tai-e、Doop 等深度侧车结果必须映射回 canonical `symbolId`；无法映射时只能作为独立候选 evidence，不能写入确定关系。
- Jasper 生成的 servlet 方法必须通过 JSP path、SMAP、行号或 generated source metadata 映射回 JSP 节点。
- Web Client Analyzer 负责 HTML/JSP 内联客户端结构、外链 JS 静态请求、DOM 事件和浏览器侧参数线索；它只能映射到 `HtmlPage/JspPage/ScriptResource/ClientRequest/RequestParameter` 等 identity，不能把浏览器侧函数伪装成 Java `Method`。
- MyBatis Dynamic SQL Preprocessor 负责把 mapper statement、XML include、动态标签、OGNL 条件和参数映射拆成 `SqlStatement/SqlVariant/SqlBranchCondition/SqlParameter`；动态分支不能直接覆盖静态 SQL 事实，必须保留 variant completeness 和 branch evidence。
- Reflection Resolver 只能把反射线索映射为 `ReflectionCandidate` 或 `REFLECTS_TO` 候选；除非后续由 Spoon、ASM、框架配置或人工确认形成非反射证据，否则不得把候选目标提升为确定 `CALLS`。
- Boundary Symbol Inventory 负责 C/C++、COBOL、native library、shell/JCL 等 L3 文件的可见符号盘点；它只生成 `BoundarySymbol/NativeLibrary/ExternalCommand` 和边界候选关系，不声明非 Java 内部调用图。
- 无法稳定映射的符号不得覆盖确定节点，只能以 `POSSIBLE` confidence 写入候选关系。

### 5.4 Flow Identity 格式

Impact Flow 只把边界变量和可复用摘要实体化。方法内部普通 local variable 的 def-use 默认保存在 `MethodSummary` 和 evidence 中，不进入主图节点，避免图谱膨胀。

```text
method-summary://{projectKey}/{moduleKey}/{sourceRootKey}/{ownerBinaryName}#{methodName}{descriptor}@{summaryVersion}
param-slot://{projectKey}/{moduleKey}/{sourceRootKey}/{ownerBinaryName}#{methodName}{descriptor}:param[{index}:{erasedType}]
return-slot://{projectKey}/{moduleKey}/{sourceRootKey}/{ownerBinaryName}#{methodName}{descriptor}:return[{erasedType}]
request-param://{projectKey}/{moduleKey}/{scopeKey}#{name}
session-attr://{projectKey}/{moduleKey}/{scopeKey}#{name}
model-attr://{projectKey}/{moduleKey}/{scopeKey}#{name}
html-input://{projectKey}/{moduleKey}/{sourceRootKey}/{htmlRelativePath}#form[{stableFormKey}]:input[{stableInputKey}]
dom-event-handler://{projectKey}/{moduleKey}/{sourceRootKey}/{scriptRelativePath}#event[{selector}:{eventName}:{line}:{ordinal}]
sql-param://{projectKey}/{moduleKey}/{sourceRootKey}/{namespaceOrFile}#{statementIdOrHash}:param[{nameOrIndex}:{jdbcType?}]
sql-branch-condition://{projectKey}/{moduleKey}/{sourceRootKey}/{namespaceOrFile}#{statementIdOrHash}:branch[{xmlPathHash}:{conditionHash}]
reflection-candidate://{projectKey}/{moduleKey}/{sourceRootKey}/{ownerBinaryName}#{methodName}{descriptor}:reflect[{sourceRange}:{expressionHash}]
```

规则：

- Flow id 的 method/sql 部分必须能 round-trip 还原到已解析的 canonical `symbolId`，不得使用 display name 或嵌套未转义 symbolId。
- `ParamSlot` 使用 0-based index 和 erased type；未解析类型使用 `U`，confidence 不得高于 `LIKELY`。
- `ReturnSlot` 对 `void` 方法不创建；构造器返回对象初始化效果用 field/receiver flow fact 表达。
- `RequestParameter`、`RequestAttribute`、`SessionAttribute`、`ModelAttribute` 的 `scopeKey` 可以是入口 path、framework scope、module 或 `_unknown`；动态 name 只能产生 `POSSIBLE` 候选。
- `HtmlInput` 与 `JspInput` 都可以绑定到 `RequestParameter`，但必须保留原始页面、form key、selector/name/id 和 source range；JS 运行时新增的 input 只能用 `DomEventHandler/ClientRequest` 候选表达。
- `DomEventHandler` 只表示静态可见的事件绑定和 handler 位置，不承诺完整浏览器执行路径；selector 或 handler 无法稳定解析时必须标记 `CLIENT_JS_DYNAMIC`。
- `SqlParameter` 优先使用 MyBatis parameter name、JDBC index 或 named placeholder；动态 SQL 中无法稳定定位的参数必须保留 XML path/source range。
- `SqlBranchCondition` 必须保存 XML path、OGNL/test 表达式 hash、条件种类和所属 `SqlVariant`；无法解释的表达式使用 `UNRESOLVED_OGNL` 边界，不能静默丢弃。
- `ReflectionCandidate` 必须保存 callsite method、source range、反射 API 类型、字符串来源、候选目标集合和 resolver status；候选目标不得写成 Java method 的 canonical 替代 id。
- `method-summary` 的 `summaryVersion` 来自 method file hash、analyzer version 和 summary schema version，summary 可重建，不作为长期业务事实主键。

### 5.5 Artifact Identity 格式

分析产物使用 `artifactId`，默认绑定 `analysisRunId`、`queryId` 或用户保存版本。它们可以持久化用于历史报告和复盘，但不能作为代码符号参与确定调用/绑定事实。

```text
feature-seed://{projectKey}/{analysisRunIdOrQueryId}/{seedHash}
feature-scope://{projectKey}/{scopeVersion}/{scopeHash}
change-plan://{projectKey}/{analysisRunIdOrQueryId}/{planId}
import-review://{workspaceId}/{analysisRunId}
analysis-scope-decision://{workspaceId}/{analysisRunId}/{decisionId}
architecture-health://{projectKey}/{snapshotId}/{scopeHash}/{reportVersion}
architecture-metric://{projectKey}/{snapshotId}/{scopeHash}#{metricName}:{metricScopeHash}
hotspot-candidate://{projectKey}/{snapshotId}/{scopeHash}#{identityHash}:{reasonHash}
```

规则：

- `seedHash` 基于 normalized user text、selected candidates、locale 和 project scope 生成，不包含原始绝对路径。
- `FeatureScope` 默认是 candidate artifact；只有用户确认或保存为命名功能后，才能标记为 `curated=true`。
- AI 可以生成 `FeatureSeed` 和 `FeatureScope` 候选排序，但不能直接生成确定 `curated FeatureScope`。
- `ChangePlanReport` 是查询/分析产物，内部 `ChangeItem` 指向 canonical symbols 或 flow nodes，并带 evidence keys。
- `ArchitectureHealthReport`、`ArchitectureMetric` 和 `HotspotCandidate` 是派生指标 artifact，必须绑定 committed snapshot、metric version 和输入 fact manifest；它们可用于排序和风险提示，但不能提升底层 path confidence。
- artifact 可以过期。底层 symbol/fact tombstone 后，报告必须显示 stale status，而不是静默复用旧结论。

### 5.6 SymbolId 归一化算法

归一化必须是纯函数：同一输入上下文和同一符号语义必须稳定生成同一个 `symbolId`。算法不得依赖本机绝对路径、临时目录、扫描顺序、线程顺序或数据库自增 ID。

输入上下文：

```text
ProjectContext:
  projectKey
  workspaceRoot
  caseSensitivity
  vcsRoot

ModuleContext:
  moduleKey
  moduleRoot
  buildSystem
  sourceRoots[]
  webRoots[]
  classOutputRoots[]
  jarCoordinates?

SourceContext:
  physicalPath
  sourceRootKey
  relativePath
  fileHash
  encoding

DatabaseContext:
  datasourceKey
  schemaKey
  databaseNameCasePolicy
  defaultSchema

RunContext:
  analysisRunId
  queryId?
  snapshotId
  artifactVersion?

SymbolRawInput:
  kind
  analyzer
  owner/name/path
  memberName?
  sourceSignature?
  jvmDescriptor?
  localId?
  sourceRange?
  generatedSource?
```

通用流程：

```text
1. resolveProjectKey(input)
2. resolveModuleKey(input.path, build metadata)
3. resolveSourceRootKey(input.path, module source roots/web roots/class outputs)
4. normalizePath(input.path)
5. resolveIdentityType(input.kind)
6. normalizeKind(input.kind)
7. normalizeOwner(kind, analyzer-specific owner)
8. normalizeMember(kind, memberName/localId)
9. normalizeDescriptor(kind, sourceSignature, bytecode descriptor, classpath)
10. normalizeDatabaseContext(kind, datasource/schema/table/column)
11. normalizeFragment(kind, local JSP/XML/SQL/flow/artifact id)
12. assemble canonical identity id
13. attach display metadata and aliases
14. validate parse(identity id) round-trip
```

路径归一化：

- `physicalPath` 先转为仓库相对路径；不允许把 `D:\...`、`/home/...` 等绝对路径写入 `symbolId`。
- 分隔符统一为 `/`。
- 删除 `.`、解析非逃逸的 `..`，但不得跨出 `workspaceRoot/moduleRoot`。
- URL/path 中的 `#`、`?`、空格、中文和其他非安全字符必须 percent-encode；展示名另存 metadata，不直接改变 canonical id。
- 仓库真实大小写保留在 `symbolId`；在 Windows/macOS case-insensitive 环境额外保存 `normalizedPathLower` 用于查重和告警。
- JAR 内 class/resource 默认使用统一 SymbolId 语法，`sourceRootKey=WEB-INF/lib/{jarName}`，后续路径为 jar entry，例如 `class://shop/_root/WEB-INF/lib/vendor.jar/com.vendor.web.JarDispatchAction`。如需单独表达 jar entry，可新增正式 kind `jar-entry://{projectKey}/{moduleKey}/{sourceRootKey}/{entryPath}`，不得使用绕过 parser 的临时自定义 scheme。

project/module/sourceRoot 规则：

- `projectKey` 必须来自配置、仓库名或导入时分配的稳定 key；禁止使用 display name。
- `moduleKey` 使用 Gradle/Maven module path，例：`_root`、`app-web`、`services/order-service`。同名 module 冲突时加稳定父路径，不加随机后缀。
- `sourceRootKey` 使用逻辑根：`src/main/java`、`src/main/resources`、`src/main/webapp`、`WEB-INF/classes`、`WEB-INF/lib/{jarName}`、`db`、`reports`。
- 无法归入已知 source root 时使用 `_unknown/{stableRelativeParent}`，confidence 不得高于 `LIKELY`。

Java class/interface/enum/annotation：

```text
{kind}://{projectKey}/{moduleKey}/{sourceRootKey}/{binaryName}
```

- `binaryName` 使用 JVM binary name：`com.foo.Outer$Inner`。
- 源码输入从 package + nested type path 生成 binary name。
- 字节码输入从 internal name `com/foo/Outer$Inner` 转换。
- 匿名类、局部类优先使用字节码 binary name；源码阶段无法稳定得到编译名时使用 `synthetic-symbol://...` 候选，并在字节码阶段用 `SYNTHETIC_OF` 对齐。

Java method/constructor/static initializer：

```text
method://{projectKey}/{moduleKey}/{sourceRootKey}/{ownerBinaryName}#{memberName}{erasedJvmDescriptor}
```

- 普通方法使用真实方法名。
- 构造器使用 `#<init>(...)V`。
- 静态初始化块使用 `#<clinit>()V`。
- descriptor 必须是 erased JVM descriptor。例：`(Ljava/lang/String;I)Z`。
- `void` 返回 `V`；数组使用 JVM 数组描述符；内部类参数使用 `Lpkg/Outer$Inner;`。
- 泛型、varargs、注解、参数名只进 display metadata，不进入 canonical descriptor。
- 若源码 no-classpath 无法解析类型，使用 source text 生成 provisional descriptor，并标记 `descriptorStatus=UNRESOLVED`；后续 classpath/bytecode 解析成功后必须通过 alias 合并到 resolved symbol。
- `descriptorStatus=UNRESOLVED` 的 method 不得作为 `CERTAIN CALLS/BINDS_TO/MAPS_TO_COLUMN` 事实主键，只能产生 `POSSIBLE/LIKELY` 候选；resolved 后旧 provisional id 必须设置 `canonicalReplacement`。
- bridge/synthetic method 保留独立 `symbolId`，并增加 `BRIDGES_TO` 或 `SYNTHETIC_OF` 指向源码方法。

Java field：

```text
field://{projectKey}/{moduleKey}/{sourceRootKey}/{ownerBinaryName}#{fieldName}:{erasedJvmTypeDescriptor}
```

- 字段类型 descriptor 参与 canonical id，解决同名字段在字节码增强或语言特性下的冲突。
- 若类型无法解析，使用 `:U` 作为 unresolved type marker，并记录 display type；此类 field 不得产生确定字段级 DB 映射，只能作为候选等待 resolved。

JSP page/form/input/tag：

```text
jsp-page://{projectKey}/{moduleKey}/{sourceRootKey}/{jspRelativePath}
jsp-form://{projectKey}/{moduleKey}/{sourceRootKey}/{jspRelativePath}#form[{stableFormKey}]
jsp-input://{projectKey}/{moduleKey}/{sourceRootKey}/{jspRelativePath}#form[{stableFormKey}]:input[{stableInputKey}]
jsp-tag://{projectKey}/{moduleKey}/{sourceRootKey}/{jspRelativePath}#tag[{prefix}:{name}:{line}:{ordinal}]
```

- `jspRelativePath` 必须相对 web root。
- form key 优先使用 `action + method + lineStart + ordinal`，action 动态时使用 `lineStart + ordinal`。
- input key 优先使用 `name/property/path + type + lineStart + ordinal`。
- Struts `html:*` 的 `property`、Spring form `path`、HTML `name` 都归一为 input logical name，但原属性名必须保存在 metadata。
- include/tagfile 产生的 symbol 必须保留 physical source JSP/tag file，而不是全部归到入口 JSP。

HTML page/form/input/script/client request：

```text
html-page://{projectKey}/{moduleKey}/{sourceRootKey}/{htmlRelativePath}
html-form://{projectKey}/{moduleKey}/{sourceRootKey}/{htmlRelativePath}#form[{stableFormKey}]
html-input://{projectKey}/{moduleKey}/{sourceRootKey}/{htmlRelativePath}#form[{stableFormKey}]:input[{stableInputKey}]
script-resource://{projectKey}/{moduleKey}/{sourceRootKey}/{scriptRelativePath}
client-request://{projectKey}/{moduleKey}/{sourceRootKey}/{sourceRelativePath}#request[{kind}:{method}:{urlHash}:{line}:{ordinal}]
```

- `htmlRelativePath` 和 `scriptRelativePath` 必须相对 web root、static root 或已确认 source root。
- `HtmlForm/HtmlInput` 的 key 规则与 `JspForm/JspInput` 保持一致；JSP 内原生 HTML 控件可以产生 `JspInput` 或 `HtmlInput`，但同一 source range 只能有一个 canonical input identity。
- `ClientRequest` 的 `kind` 包括 `form-submit`、`fetch`、`xhr`、`jquery-ajax`、`axios`、`location`、`window-open` 等；method 和 URL 动态时使用 hash + boundary，不得伪造确定 endpoint。
- 外链 JS 必须通过 `HtmlPage/JspPage -[:LOADS_SCRIPT]-> ScriptResource` 绑定到页面上下文；无法绑定页面上下文的 JS 只能产出低置信候选。

Action/API/config：

```text
api-endpoint://{projectKey}/{moduleKey}/{sourceRootKey}/{httpMethod}:{normalizedPath}
action-path://{projectKey}/{moduleKey}/{sourceRootKey}/{modulePrefix}{actionPath}
config-key://{projectKey}/{moduleKey}/{sourceRootKey}/{configRelativePath}#{xmlOrPropertyPath}
```

- URL path 必须以 `/` 开头，去除重复 `/`，保留 path variable 形态。
- Spring endpoint method 缺失时用 `ANY`。
- Struts module prefix 是 action path 的一部分，root module 不加 `_root` 前缀。
- XML path 使用稳定结构路径和关键属性，例如 `/struts-config/action-mappings/action[@path='/user/save']`。

SQL/table/column：

```text
sql-statement://{projectKey}/{moduleKey}/{sourceRootKey}/{namespaceOrFile}#{statementIdOrHash}
datasource://{projectKey}/{datasourceKey}
db-schema://{projectKey}/{datasourceKey}/{schemaKey}
db-table://{projectKey}/{datasourceKey}/{schemaKey}/{tableName}
db-column://{projectKey}/{datasourceKey}/{schemaKey}/{tableName}#{columnName}
db-index://{projectKey}/{datasourceKey}/{schemaKey}/{tableName}#{indexName}
db-constraint://{projectKey}/{datasourceKey}/{schemaKey}/{tableName}#{constraintName}
db-view://{projectKey}/{datasourceKey}/{schemaKey}/{viewName}
```

- MyBatis 使用 `namespace#statementId`，动态 SQL fragment 追加 XML path。
- JDBC literal SQL 没有 statement id 时，使用 `normalizedSql + ownerMethodSymbolId + sourceRange + datasourceKey` 生成 hash，并在 metadata 保存调用方法、源码位置和原始 SQL 片段 hash。
- `datasourceKey` 来自配置属性、JNDI name、MyBatis environment、Spring datasource bean、连接串脱敏 hash 或用户确认；未知时使用 `_unknown`，相关 DB 影响 confidence 不得高于 `LIKELY`。
- `schemaKey` 来自 SQL 显式 schema、连接配置 default schema、migration context 或用户确认；缺失时使用 `_default` 或 `_unknown`。
- table/column 名按数据库实际大小写策略保存；比较时另存 normalized database name。
- 跨项目共享数据库时，多个 project 的 `DbTable/DbColumn` 可以指向同一个 datasource/schema identity；不得用 moduleKey 隐式区分数据库对象。

report/native/synthetic：

```text
report-definition://{projectKey}/{moduleKey}/{sourceRootKey}/{reportRelativePath}#{reportId?}
report-field://{projectKey}/{moduleKey}/{sourceRootKey}/{reportRelativePath}#{fieldName}
native-library://{projectKey}/{moduleKey}/{sourceRootKey}/{libraryNameOrPath}
synthetic-symbol://{projectKey}/{moduleKey}/{sourceRootKey}/{owner}#{stableSyntheticKey}
```

- report id 缺失时使用文件相对路径作为主身份。
- native library 名来自 `System.loadLibrary` 时保留逻辑库名；来自 `System.load` 时归一化为项目相对路径或 external marker。
- synthetic key 必须来自稳定源码位置、生成来源或字节码名，不得使用扫描序号。例如 lambda 可使用 `lambda:{ownerMethod}:{line}:{column}:{ordinalInLine}`，匿名类可使用 `anonymous:{line}:{column}:{superType}`，局部类优先使用字节码名并保留源码位置 metadata。

### 5.7 Provisional、alias 与冲突处理

- 每个 canonical `symbolId` 可以有多个 alias：source signature、Soot/Joern/Tabby/WALA/Tai-e signature、JVM internal name、display path、legacy id。
- alias 只用于解析输入和合并，不作为图谱主键。
- 两个 analyzer 生成不同 canonical id 但 alias 指向同一 JVM descriptor 时，合并到 resolved canonical id，并记录 `MERGED_ALIAS` evidence。
- 两个符号 canonical id 相同但 source range/owner/type 冲突时，不直接覆盖；创建 conflict record，等待人工或后续 analyzer 消歧。
- unresolved descriptor 后续 resolved 时，旧 provisional id 设置 `canonicalReplacement`，查询层应跳转到 resolved id，但历史 evidence 保留原 id。
- 侧车工具的 signature 只能作为 alias 或 candidate evidence；不能因为 Joern/Tabby/WALA/SootUp/Tai-e 路径更完整就覆盖 Spoon/XML/JSP/SQL 产出的 `CERTAIN` canonical symbol。
- alias 合并必须限定在同一 project/datasource/module 上下文内。跨项目或跨 datasource 的同名类、表、字段不得自动合并。
- `canonicalReplacement` 只表示查询跳转和历史 evidence 归并，不表示旧 provisional facts 自动升级为 `CERTAIN`；旧 facts 必须由对应 analyzer 在新 run 中重新 emit 或 tombstone。

### 5.8 合法性校验

- `symbolId`、`flowId`、`artifactId` 必须能被 parser 无损解析出 kind、identityType、projectKey、必要上下文和 local fragment。
- canonical id 不允许包含未转义空白、反斜杠、控制字符。
- Java method 必须有 descriptor；没有 descriptor 的 method id 只能作为 search candidate，不能作为确定 CALLS/BINDS_TO 事实主键。
- JSP/XML/SQL/report symbol 必须带文件 sourceRoot 和相对路径；DB symbol 必须带 datasourceKey 和 schemaKey。
- `artifactId` 必须带 run/query/version 上下文；不能被误用为 canonical code symbol。
- 所有 upsert 前必须执行 `parse -> normalize -> assemble` round-trip 测试。
- 每个新增 kind 必须先注册 SymbolKind Registry，并通过 id collision、case sensitivity、percent encoding 和 alias merge 测试。

## 6. 增量图谱语义

增量图谱语义是 CodeAtlas 的正确性边界，不只是性能优化。它必须防止旧关系残留、半更新事实暴露、缓存和 Neo4j 不一致、侧车候选覆盖主事实，以及未触碰 scope 在新 snapshot 中丢失。

### 6.1 Snapshot / AnalysisRun / ScopeRun

三层生命周期必须分开：

```text
Snapshot
  一个可查询的 committed active view。

AnalysisRun
  一次分析计划或一批 analyzer task 的生命周期。

ScopeRun
  某个 analyzer 对某个 scope + relationFamily 的本次输出。
```

状态机：

```text
AnalysisRun: PLANNED -> RUNNING -> STAGED -> COMMITTED
           |-> FAILED
           |-> ROLLED_BACK

ScopeRun: PLANNED -> RUNNING -> STAGED -> COMMITTED
        |-> FAILED
        |-> SKIPPED
```

- `AnalysisRun` 负责整体任务状态和报告状态；`ScopeRun` 负责 tombstone、重试和局部提交边界。
- 一个 `AnalysisRun` 可以包含多个 analyzer、多个 scope 和多个 relation family。部分 `ScopeRun` 失败不得污染已 committed active view。
- `Snapshot` 只暴露 committed facts。`STAGED` facts 不参与当前查询。
- 查询当前 snapshot 时必须使用区间语义：`validFromSnapshot <= currentSnapshot && (validToSnapshot is null || validToSnapshot > currentSnapshot)`。不能只按 `snapshotId == currentSnapshot` 过滤，否则会丢失未触碰 scope 沿用的旧事实。

### 6.2 FactRecord / Evidence / Materialized Edge

图谱写入采用双层模型：

```text
Materialized Edge
  高频查询直接边，例如 CALLS、ROUTES_TO、BINDS_TO、READS_COLUMN。

FactRecord
  审计事实，保存 factKey、relationType、owner、生命周期、聚合 confidence/priority。

Evidence
  证据记录，保存 analyzer、scope、source range、XML/JSP/SQL path、snippet hash、外部工具证据位置。
```

核心字段：

```text
factKey
sourceIdentityId
targetIdentityId
relationType
qualifier
projectId
snapshotId
analysisRunId
scopeRunId
analyzerId
scopeKey
relationFamily
schemaVersion
active
validFromSnapshot
validToSnapshot
tombstone
evidenceKey
confidence
priority
sourceType
```

规则：

- `factKey` 是同一事实的稳定 key，由 source identity、target identity、relation type、qualifier 组成；identity 可以是 `symbolId`、`flowId` 或 `artifactId`。
- `evidenceKey` 是同一证据的稳定 key，由 analyzer、scope、文件路径、行号/XML path/JSP tag/SQL id、外部工具证据位置和 evidence schema version 组成。
- 同一 `factKey` 可以有多个 evidence；查询默认展示 materialized edge 的聚合结果，也可展开 `FactRecord -> Evidence`。
- Materialized Edge 是可重建的查询加速边；FactRecord/Evidence 是审计和增量生命周期来源。
- Evidence 可以保存在 Neo4j 或外部 Evidence Store，但必须能通过 `evidenceKey` 从报告和 UI 追溯。

### 6.3 Active View 与 Commit

增量写入流程（原子写入模型）：

```text
1. 计算 changed scopes 和 relation families
2. 对每个 ScopeRun 运行对应 analyzer
3. analyzer emit facts + evidence + diagnostics
4. 校验：identity round-trip、source/target 存在性、relation 合法性、confidence boundary
5. 原子写入：在同一事务内 upsert 新 facts，tombstone 同一 owner 下未重新 emit 的旧 facts
6. 写入成功后重建受影响缓存
```

校验项（同前，无变化）。

Active View 规则：

- 校验失败的 facts 不参与当前查询，不进入 active view。
- 原子写入失败时（事务回滚），旧 active facts 完整保留，不暴露半更新结果。
- `Snapshot` diff 通过比较两个 snapshot 的 active fact set 产生新增、删除和变化。
- 未触碰的 scope 不做删除，沿用上一 snapshot 的 active facts。

### 6.4 Tombstone Ownership

Tombstone 在同一原子写入事务内完成，owner tuple：

```text
projectId
analyzerId
scopeKey
relationFamily
previousScopeRunId
currentScopeRunId
```

规则：

- 只能 tombstone 当前 owner tuple 下，上一 committed ScopeRun 产出但本次未重新 emit 的旧 facts。
- 不能跨 analyzer、跨 scope、跨 relationFamily 清理。
- 同一 analyzer/scope 只重跑 `CALLS` 时，不得 tombstone `BINDS_TO`、`MAPS_TO_COLUMN`、Flow facts 或 planning artifacts。
- 侧车 ScopeRun 没有输出时，不得删除主 analyzer 事实。
- AI_ASSISTED 的 tombstone 只影响 AI candidate relation family。
- 原子写入失败时 tombstone 自动回滚，旧事实无损。

### 6.5 Confidence / Priority 聚合

- confidence 聚合使用确定性来源优先：`CERTAIN > LIKELY > POSSIBLE > UNKNOWN`；`AI_ASSISTED` 不能提升确定性事实，只能附加候选解释。
- 如果多个分析器给出同一事实，保留所有 evidence，最终 confidence 取最高确定性，但报告中必须能看到来源列表。
- confidence 聚合不等于简单投票。聚合顺序为：确定性等级优先，其次 analyzer priority，其次 evidence count 只用于排序展示，不提升等级。
- analyzer priority 只用于同等级排序，默认顺序为：`SPOON/IMPACT_FLOW/METHOD_SUMMARY_FLOW/JASPER/JSP_SMAP/XML/SQL/JPA/ASM/CLASSGRAPH/PROGUARDCORE/JAVAPARSER_FAST/JOERN/TABBY/WALA/SOOTUP_HEROS/TAI_E/AI_ASSISTED`。其中深度侧车可补充路径证据和候选事实，但不能覆盖已有更确定的源码/XML/JSP/SQL/Impact Flow facts；`AI_ASSISTED` 永远不能产生确定性 graph fact。
- 同一 `factKey` 同时存在 `CERTAIN` 和 `POSSIBLE` evidence 时，聚合 fact 可显示 `CERTAIN`，但 UI/报告必须保留所有 evidence 的 sourceType/confidence，不能隐藏低置信来源。
- 冲突事实不能靠 confidence 自动合并。例如同一 JSP/HTML form action 或 ClientRequest 映射到两个不同 ActionPath 时，应保留两条 fact，并在报告中显示歧义。
- 图谱关系和路径必须同时保留 `confidence` 与 `priority`。`confidence` 表示事实可靠性，只能由证据、解析器契约和确定性规则提升；`priority` 表示分析和展示排序，可综合入口类型、路径距离、变更距离、历史风险、证据数量、用户关注范围和 AI 排序建议。
- `priority` 不得反向提升 `confidence`。例如 AI 认为某条动态 shell 路径“很值得关注”，只能提高展示优先级，不能把 `POSSIBLE` 升级为 `CERTAIN`。
- 路径分数应分开计算：`pathConfidence` 使用保守聚合，优先暴露路径中的最低置信边和边界；`pathPriority` 用于排序，优先展示高风险、短路径、入口明确、证据丰富或用户关注范围内的路径。

### 6.6 Artifact 生命周期

`FeatureSeed`、`FeatureScope`、`ChangePlanReport`、`ImportReviewReport`、`SavedQuery`、`WatchSubscription`、`ReviewComment`、`PolicyViolation`、`RiskWaiver`、`ExportArtifact` 等 artifact 不套用代码事实的 tombstone 规则。

- artifact 绑定 `artifactId + analysisRunId/queryId + snapshotId`。
- artifact 不参与 active graph 主查询，除非用户保存或确认为 curated scope。
- 底层 facts 变化后，artifact 设置 `staleAgainstSnapshot`，报告必须提示过期，不得静默复用旧结论。
- AI 生成的 artifact 只能是 candidate，不能直接产生确定修改项。
- 产品工作流 artifact 可以跨 snapshot 保留，但必须显示其创建时的 `snapshotId` 和当前 `staleAgainstSnapshot`。
- `RiskWaiver` 必须有 scope、owner、reason、expiry 和 evidence/report 引用；过期后不能继续压制风险或 CI gate。
- `ReviewComment` 和 `ReviewThread` 只表达人工讨论，不提升或降低 fact confidence。

### 6.7 Cache Rebuild

- 任何缓存、symbol index、vector index、FFM index 重建都发生在 commit 成功之后。
- 缓存失败必须回退 FactStore active facts；生产部署通常回退 Neo4j，不能影响报告正确性。
- JVM InMemoryGraphCache 只缓存当前 active snapshot 的 primitive 邻接表。
- 增量分析 tombstone 或新增关系后，只重建受影响分片。
- cache rebuild 必须记录 input snapshot、relationFamily、edge count、duration、failure reason。

### 6.8 Schema 演进与迁移

图谱、事实、证据、身份和报告 schema 都必须显式版本化。CodeAtlas 不能假设所有历史数据都使用当前代码版本。

版本对象：

| 对象 | 版本字段 | 迁移要求 |
| --- | --- | --- |
| `symbolId/flowId/artifactId` | `identitySchemaVersion` | 新 parser 必须兼容旧 ID，无法兼容时通过 alias/canonicalReplacement 迁移 |
| `FactRecord` | `factSchemaVersion` | 新增 relation/qualifier 时必须有回填或忽略策略 |
| `Evidence` | `evidenceSchemaVersion` | 旧 evidence 必须仍能在报告中展示，缺字段时标记降级原因 |
| `Materialized Edge` | `edgeSchemaVersion` | 可从 FactRecord/Evidence 重建，不能作为唯一历史来源 |
| `MethodSummary` | `summarySchemaVersion` | 版本变化必须触发 relationFamily invalidation 和 summary 重建 |
| `Report Artifact` | `artifactSchemaVersion` | 旧报告可读但可能 stale，不自动升级为当前结论 |
| Derived Index | `cacheBuildId + schemaVersion` | schema 不匹配时丢弃并重建 |

迁移规则：

- schema 变更必须先定义兼容策略：`READ_OLD`、`REBUILD_DERIVED`、`BACKFILL_FACTS`、`DROP_AND_REANALYZE_SCOPE`。
- 任何 schema migration 不能直接修改 active view；必须通过新的 `AnalysisRun/ScopeRun` 或受控 migration run 发布。
- migration run 必须记录输入 snapshot、目标 schema version、影响 relationFamily、迁移数量、失败数量和回滚策略。
- 不能在查询过程中隐式迁移大量 facts；查询最多做轻量兼容读取，重迁移必须后台运行。
- schema 版本不兼容时，报告返回 `PARTIAL` 或 `STALE_SCHEMA`，不能伪装为完整新版本结果。

## 7. JSP、HTML/JS 与旧框架设计

JSP 是老系统分析的一等能力，尤其是 Struts1 和 Seasar2 系统。但 JSP 不能只按服务端模板处理：真实页面通常同时包含 HTML DOM、原生表单、链接、内联 JavaScript、外链 JavaScript、Ajax/fetch、jQuery、运行时修改 form action 和参数拼装。CodeAtlas 必须把 Web 页面拆成服务端 JSP 语义、HTML 结构语义和 JavaScript 客户端行为三层，再汇入同一套影响图谱。

### 7.1 WebAppContext 与容器 profile

JSP 解析需要先建立 `WebAppContext`，否则 Jasper 成功率和证据定位都不可控。`WebAppContext` 包含：

- web root：例如 `src/main/webapp`、`WebRoot`、`WebContent`。
- `WEB-INF/web.xml`：servlet、filter、listener、welcome-file、JSP config、Struts `ActionServlet` mapping。
- classpath：`WEB-INF/classes`、`WEB-INF/lib`、Maven/Gradle dependencies、Eclipse `.classpath`、编译输出目录。
- servlet/JSP API namespace：`javax.servlet` 或 `jakarta.servlet`，不得混用。
- JSP/Servlet spec version：例如 JSP 1.2/2.0/2.1/2.2/2.3、Servlet 2.3/2.4/2.5/3.x/4.x/5.x。
- container family/profile：Tomcat 6/7/8/9/10、Resin、WebLogic、WebSphere、UnknownLegacy。
- Jasper profile：`TOMCAT_LEGACY_JAVAX`、`TOMCAT_8_9_JAVAX`、`TOMCAT_10_JAKARTA`、`VENDOR_COMPAT`、`TOKEN_ONLY`。
- TLD/taglib registry：taglib URI 到 TLD 文件、jar 内 TLD、web.xml taglib 映射、自定义 tag file。
- include resolver：静态 include、`jsp:include`、相对路径和 context path 解析。
- static resource resolver：`script src`、`link href`、HTML/JSP 相对路径、context path、Struts/Spring view path 到真实文件的解析。
- client route resolver：静态 form action、anchor href、`fetch/XMLHttpRequest/$.ajax/axios` URL、`location.href/window.open` 的候选 endpoint 解析。
- encoding resolver：page directive、web.xml、文件 BOM、项目默认编码。
- framework configs：`struts-config.xml`、Tiles、Validator、Seasar2 `dicon`、Spring XML/context。

Jasper 选择规则：

- MVP 可以先通过 Jasper `JspC` adapter 作为 preferred pre-parse 入口；当 `javax.servlet`/`jakarta.servlet` API namespace 或 JSP/Servlet 依赖不匹配导致 class loading、linkage 或执行失败时，必须记录诊断并降级到 tolerant token fallback，不得中断 JSP 事实提取。
- 后续增强必须加入 Jasper runtime probe，显式识别 `javax.servlet` 与 `jakarta.servlet` 可用性、JSP/Servlet API 版本和可用 Jasper profile。MVP 可以先选择当前 analyzer classpath 中匹配的 Jasper profile；真正同时携带多套 `javax`/`jakarta` runtime 时，必须通过 isolated Jasper profile classloader 按项目上下文选择。
- Isolated Jasper profile classloader 由 analyzer 配置提供 profile classpath，按 `WebAppContext` 推断出的项目 API namespace 选择 `TOMCAT_10_JAKARTA` 或 `TOMCAT_8_9_JAVAX`；未配置或无法链接时必须记录诊断并回退到当前 classpath probe 或 `TOKEN_ONLY`。
- 默认按 `servletApiNamespace + jspSpecVersion + container family` 选择最接近的 isolated Jasper profile。
- `javax.servlet` 老工程不得用 Tomcat 10/Jakarta profile 强行解析；profile 不匹配时先尝试 legacy javax profile，再降级到 `TOKEN_ONLY`。
- Resin/WebLogic/WebSphere 等 vendor profile 只做兼容解析。vendor 特有 tag、classloader 行为或容器扩展无法确认时，必须记录 diagnostics，不得伪装成 Tomcat 确定语义。
- 每个 `WebAppContext` 使用独立 analyzer classloader 或独立 worker。解析 JSP 不能执行项目初始化、listener、filter、Spring context、数据库连接或业务代码。
- 自定义 tag handler 可以读取 class metadata 和 TLD，但不得调用 handler 生命周期方法。无法解析的 tag handler 生成 `TagBoundary` 和 `POSSIBLE` 候选。

### 7.2 Jasper 失败与置信度边界

Jasper 解析失败时必须记录 `JspDiagnostic`，至少包含 `reasonCode`、缺失上下文、Jasper profile、API namespace、classpath 摘要、失败文件和 fallback 策略。

常见 `reasonCode`：

```text
MISSING_WEB_ROOT
MISSING_WEB_XML
API_NAMESPACE_MISMATCH
JSP_SPEC_UNSUPPORTED
TLD_UNRESOLVED
CUSTOM_TAG_UNRESOLVED
INCLUDE_UNRESOLVED
STATIC_RESOURCE_UNRESOLVED
CLIENT_REQUEST_DYNAMIC
CLIENT_EVENT_UNRESOLVED
ENCODING_FAILED
GENERATED_SERVLET_COMPILE_FAILED
VENDOR_EXTENSION_UNSUPPORTED
```

fallback 使用内置 JSP token/form 容错提取器，但置信度必须分层：

- 结构事实可确定时可以为 `CERTAIN`：`JspPage`、`JspForm`、`JspInput`、`JspTag`、`JspInclude`、source range、form/input 属性。
- 语义关系默认不得为 `CERTAIN`：`SUBMITS_TO`、`ROUTES_TO`、`CALLS`、`BINDS_TO`、`READS_REQUEST_PARAM`、`WRITES_MODEL_ATTR`、`MAPS_TO_COLUMN`。
- 静态 form action、静态 include、显式 taglib URI、显式 Struts action path 可到 `LIKELY`，只有对应配置和路径解析同时成功时才可提升。
- 动态 action、脚本拼接、JavaScript 组装 URL、运行时 include、无法解析 custom tag 时只能是 `POSSIBLE`，并标记 `analysisBoundary=JSP_DYNAMIC`、`CLIENT_JS_DYNAMIC`、`CLIENT_EVENT_UNRESOLVED` 或更具体的 boundary。

MVP JSP 定位边界：

- MVP 阶段可以基于 Jasper 节点、JSP 原文件 token/tag 位置和容错解析结果提供 JSP path、tag、粗粒度 line evidence。
- SMAP parser（`JasperSmapParser`）已实现，从 Jasper 生成的 servlet Java 文件中解析 `*S JSP`/`*F`/`*L`/`*E` stratum block，输出 `JspLineMapping`（jspPath、jspLineStart/End、generatedLineStart/End）。
- SMAP 缺失时记录 `SMAP_MISSING_CONFIDENCE_DOWNGRADE` 诊断；include/tagfile/custom-tag 展开产生的多文件映射通过 SMAP file ID 保留为 candidate lists。
- SMAP 的 `generatedLineEnd` 由 `outputStart + inputLineCount * outputIncrement - 1` 计算得出，确保生成的 Java 行号范围正确。

### 7.3 双通道分析与 generated servlet 边界

JSP 分析采用双通道：

```text
JSP 原文件
  -> Apache Jasper 解析 JSP 语义
  -> Jasper 生成 Servlet Java
  -> Spoon/Impact Flow Engine 分析 generated servlet
  -> SMAP / generated source metadata / token range 映射回 JSP identity

JSP 原文件
  -> 内置 JSP token/form 容错提取
  -> 提取 form/input/select/textarea/taglib/html 结构

JSP/HTML/JS 资源
  -> Web Client Analyzer 静态分析
  -> 提取 HTML form/input/link/script、DOM event、client request
  -> 解析到 ApiEndpoint/ActionPath 候选
```

generated servlet 是 `DerivedJspArtifact`，不是业务代码 canonical symbol：

- generated servlet class/method 不注册为普通 `Class`/`Method`，不作为用户检索结果、入口节点或影响链路终点展示。
- Spoon/Impact Flow Engine 从 generated servlet 发现的调用、变量和返回值，必须先映射回 `JspPage`、`JspTag`、`JspScriptlet`、`JspExpression` 或 `JspInclude` identity，才能 emit JSP 归属事实。
- 映射成功的事实以 JSP identity 作为 source，evidence 同时保留 JSP 原始位置和 generated servlet 位置。
- 映射失败的 generated servlet 结果只能作为 candidate evidence 或 diagnostic，不能产生 `CERTAIN` `CALLS`、`BINDS_TO`、`READS_COLUMN`、`WRITES_COLUMN`。
- 同一 JSP 原始 token 和 generated servlet 证据生成的关系必须通过 `factKey` 去重，不能因为双通道重复写边。

### 7.4 JSP/Web Client 构造到图谱事实契约

JSP analyzer 和 Web Client Analyzer 的输出必须是稳定节点、关系和 evidence，不只是“识别到标签”或“识别到请求字符串”。

| 构造 | 节点 | 关系 | 置信度边界 | Evidence |
| --- | --- | --- | --- | --- |
| `page` directive | `JspPage`, `ConfigKey` | `USES_CONFIG` | 可 `CERTAIN` | JSP path、directive range、attribute |
| 静态 include | `JspInclude`, `JspPage` | `INCLUDES` | 路径解析成功可 `CERTAIN` | include path、resolved path、range |
| `jsp:include` / `jsp:forward` | `JspInclude` / `JspForward`, `JspPage` / `ActionPath` | `INCLUDES`, `ROUTES_TO` | 静态目标 `LIKELY`，动态目标 `POSSIBLE` | tag range、param list、boundary |
| `taglib` directive | `TagLibrary`, `JspTag` | `USES_TAGLIB` | URI/TLD resolved 可 `CERTAIN` | taglib URI、TLD path、prefix |
| Struts `html:form` / HTML `form` / Spring `form:form` | `JspForm`, `ActionPath` / `ApiEndpoint` | `SUBMITS_TO` | 静态 action + config resolved 可 `LIKELY/CERTAIN`，动态 action `POSSIBLE` | form range、action、method、resolved target |
| input/select/textarea / Struts `property` / Spring `path` | `JspInput`, `RequestParameter`, `ParamSlot` | `RENDERS_INPUT`, `BINDS_TO` | 原始结构可 `CERTAIN`，后端绑定依赖配置 | input range、logical name、original attribute |
| 原生 HTML 页面 | `HtmlPage`, `HtmlForm`, `HtmlInput`, `HtmlLink` | `CONTAINS`, `RENDERS_INPUT`, `NAVIGATES_TO` | web root/引用链明确时结构可 `CERTAIN`，目标路由按解析结果降级 | HTML path、selector、attribute、range |
| 外链或内联 script | `ScriptResource`, `DomEventHandler` | `LOADS_SCRIPT`, `HANDLES_DOM_EVENT` | 静态引用可 `CERTAIN`，事件 handler 目标默认 `LIKELY/POSSIBLE` | script path、selector、eventName、range |
| EL / JSTL 读 | `ModelAttribute`, `RequestParameter`, `SessionAttribute` | `READS_MODEL_ATTR`, `READS_REQUEST_PARAM`, `READS_SESSION_ATTR` | 明确 scope 可 `LIKELY`，隐式 scope `POSSIBLE` | expression range、scope、name |
| JSTL `c:set` / scriptlet 写 | `ModelAttribute`, `RequestAttribute`, `SessionAttribute` | `WRITES_MODEL_ATTR`, `WRITES_REQUEST_ATTR`, `WRITES_SESSION_ATTR` | scriptlet 简单赋值可 `LIKELY`，复杂代码 `POSSIBLE` | tag/scriptlet range、assigned name |
| scriptlet / expression | `JspScriptlet`, `JspExpression`, `Method` | `CALLS`, `READS_FIELD`, `WRITES_FIELD` | 必须映射回 JSP identity；复杂代码按 Java analyzer 降级 | JSP range、generated range、mappingStatus |
| JavaScript ajax/fetch/XMLHttpRequest/$.ajax/axios | `ClientRequest`, `ApiEndpoint` / `ActionPath` | `SUBMITS_TO`, `CALLS_HTTP` | 静态 URL 和 method 可 `LIKELY`，与后端 endpoint 解析成功可提升；动态拼接 `POSSIBLE` | JS range、URL expression、method、payload keys、dynamic boundary |
| JS 改写 form 或 navigation | `DomEventHandler`, `ClientRequest`, `HtmlForm` | `HANDLES_DOM_EVENT`, `SUBMITS_TO`, `NAVIGATES_TO` | 静态赋值可 `LIKELY`，运行时 DOM 或复杂分支 `POSSIBLE` | handler range、selector、assigned action/href |

所有 JSP/Web Client facts 必须包含 source path、source range、`mappingStatus`、`sourceType`、`confidence`、`evidenceKey`。JSP facts 额外保留 `jspPath`；HTML/JS facts 额外保留 selector、attribute、script path、URL expression 和 dynamic boundary。include/tagfile/static resource 产生的 symbol 必须保留 physical source file，不能全部归到入口 JSP。

### 7.5 Web Client Analyzer 支持边界

Web Client Analyzer 负责浏览器侧静态结构和请求线索，不执行页面、不执行 JavaScript，也不依赖真实后端响应。它的目标是把“页面上怎么提交、脚本可能请求哪里、输入字段如何变成 request 参数”接入影响路径。

MVP 支持子集：

- HTML/JSP 中的 `form`、`input`、`select`、`textarea`、`button`、`a`、`script src`、内联 script 和常见 data-* 属性。
- 静态 `action`、`method`、`href`、`src`、`name`、`id`、`type`、`value`、`onclick/onsubmit` 属性。
- 常见 JS 请求形式：`fetch(url, options)`、`XMLHttpRequest.open(method, url)`、`$.ajax({url, type/method, data})`、`axios.get/post/request`。
- 常见提交和跳转：`form.submit()`、`element.click()` 的直接 handler、`form.action = ...`、`location.href = ...`、`window.open(...)`。
- 常见取值：`document.getElementById(...).value`、`querySelector(...).value`、jQuery `$('...').val()`、`serialize()`、`FormData(form)`。
- JSP EL 注入 JS 字符串或参数时，只把注入位置建为 `JspExpression -> ClientRequest` 或 `JspExpression -> RequestParameter` 候选，不执行表达式。

输出规则：

- 原生 HTML/JSP 结构节点可 `CERTAIN`：`HtmlPage`、`HtmlForm`、`HtmlInput`、`HtmlLink`、`ScriptResource`。
- 静态 form submit、href、script 引用可以到 `LIKELY/CERTAIN`，但只有路由配置、context path 和后端 endpoint 同时解析成功时，才能把 `ClientRequest -[:CALLS_HTTP|SUBMITS_TO]-> ApiEndpoint/ActionPath` 提升为确定关系。
- JS 请求默认 `LIKELY/POSSIBLE`。只有 URL、method、参数名、触发控件和页面上下文都静态可解析，且后端 endpoint 匹配唯一时，才允许提升。
- 动态 URL 拼接、运行时 DOM 创建、复杂条件分支、前端框架 runtime router、异步 callback 链、minified/bundled JS source map 缺失时，必须标记 `analysisBoundary=CLIENT_JS_DYNAMIC` 或 `CLIENT_BUNDLE_UNMAPPED`。
- Web Client Analyzer 不能把 JS 函数建模为 Java `Method`，也不能把浏览器侧事件当作同步 Java 调用。跨浏览器到后端只能通过 `ClientRequest`、`ApiEndpoint`、`ActionPath` 过渡。

增强阶段：

- source map 还原 bundled/minified JS。
- TypeScript/ES module import graph、前端路由配置、Vue/React/Angular 模板和组件事件。
- 浏览器录制或无副作用动态 trace，但必须由用户确认，并在隔离环境运行；动态 trace 只能补充 evidence，不覆盖静态确定事实。

### 7.6 Struts1 支持边界

Struts1 链路目标：

```text
JSP/HTML form/input or JS ClientRequest
  -> ActionPath
  -> FormBean / ActionForm / DynaActionForm
  -> Action method
  -> Service
  -> DAO/Mapper
  -> SQL/table
  -> ActionForward / Tiles / JSP
```

MVP 支持子集：

- 从 `web.xml` 识别 `ActionServlet` mapping、Struts config init-param、module prefix。
- 从 `struts-config.xml` 解析 `action-mappings`、`form-beans`、`global-forwards`、local forwards、`controller`、plugin、message resources。
- 支持 `Action.execute()` / `perform()` 的确定入口绑定。
- 支持 `html:form action`、原生 HTML `form action` 和静态 JS `ClientRequest` 到 `ActionPath`，并处理 module prefix 和 servlet mapping。
- 支持 `ActionForm`、`DynaActionForm` 和 JSP `html:* property` 到 `RequestParameter/FormField` 的绑定。
- 支持 `DispatchAction` / `LookupDispatchAction` / `MappingDispatchAction` 的候选方法分派；只有 `parameter` 名称和静态提交值可解析时，才绑定到具体 action method。
- 支持 local/global forwards 到 JSP path；Tiles definition 和 Validator XML 作为 view/validation evidence，不默认生成业务调用事实。

降级规则：

- 动态 action、JavaScript 改写 action、运行时 forward、custom RequestProcessor、未知 plugin、复杂 DispatchAction 参数、LookupDispatchAction resource key 无法解析时，相关链路最多 `POSSIBLE`，并显示 JSP 或 Web Client analyzer 的 boundary。
- ActionPath 到 Action class 可确定，但具体方法不可确定时，图谱保留到 Action class 或 entry method boundary，并标记 `dispatchMethod=UNKNOWN`。
- Validator 只能产生字段校验和风险 evidence，不表示字段一定进入 Service/SQL。
- Tiles 只能产生 view composition 和返回页面候选；如果无法解析 definition chain，必须显示 `analysisBoundary=TILES_UNRESOLVED`。

### 7.7 Seasar2 支持边界

Seasar2 在 MVP 中只做 discovery、配置证据采集和 candidate facts，不要求确定性影响路径闭环。

MVP 产出：

- `DiconComponent`、`DiconInclude`、`DiconProperty`、`DiconAspect`、`DiconInterceptor`、`DiconNamespace`。
- 显式 `class`、`interface`、`component` 声明可作为结构事实；影响链路关系默认只生成 `POSSIBLE/LIKELY` candidate。
- `AUTO_BINDS_TO`、`INTERCEPTS`、`CONFIGURES_PROPERTY`、`INCLUDES` 必须带 dicon path、XML path、naming convention、autoBinding 模式和降级原因。
- Seasar2 candidate 不得删除或覆盖 Spoon/XML/JSP/SQL/Impact Flow 产出的确定事实。

增强阶段才允许提升的链路：

```text
dicon component
  -> Java class/interface
  -> naming convention binding
  -> service/dao
  -> interceptor/aspect
  -> SQL/table
```

提升条件至少包括：Seasar2 fixture 覆盖、命名约定可测试、dicon include chain 完整、class/interface 可解析、SQL/DAO 证据可追溯、误报样例可解释。

### 7.8 Spring 链路

Spring 链路仍按主线 Java/Spring analyzer 处理：

```text
@RequestMapping
  -> Controller
  -> Service
  -> Repository/Mapper
  -> SQL/table
```

### 7.9 Report Adapter

企业 Java 迁移里，报表定义经常直接依赖 SQL、表、字段和固定格式输出。CodeAtlas 把报表资源作为可插拔 Framework Adapter，而不是普通 XML inventory。

首批目标：

- 支持 Interstage List Creator、WingArc1st SVF 等报表资源的可插拔解析。
- 识别常见报表定义文件，例如 PSF、PMD、BIP、SVF XML、报表布局 XML、字段定义 XML。
- 提取报表名、报表字段、绑定 SQL、表、列、参数、数据源配置。
- 建立 `ReportDefinition -[:READS_TABLE|READS_COLUMN|USES_CONFIG]-> DbTable/DbColumn/ConfigKey`，以及 `ReportField -[:MAPS_TO_COLUMN]-> DbColumn`。
- 支持查询“改了数据库字段 -> 哪些报表可能受影响”。

报表解析失败时，生成 `ReportDefinition` 候选节点和 `POSSIBLE` evidence，记录文件路径、XML path/字段名、解析器名称和失败原因；不得假装确定。

## 8. 调用关系与影响流

### 8.1 调用关系分层

调用关系需要分层建模：

- 确定调用：源码或字节码中直接调用。
- 多态调用：接口、抽象类、继承实现、指针分析候选。
- 程序入口：Web URL/JSP/HTML form/JavaScript ClientRequest/Action/Controller、batch job、main/CLI、scheduler、message listener、shell 启动脚本。
- DI 调用：Spring Bean、Seasar2 component、XML bean。
- 配置驱动调用：XML、YAML、properties、dicon、struts-config。
- 异步和外部编排：MQ、事件、定时任务、shell 调用和外部命令；MVP 可先识别明确 Java 启动命令，复杂动态脚本作为 `POSSIBLE` 或 `analysisBoundary=SHELL_DYNAMIC`。
- 浏览器侧触发：HTML form/link、DOM event、fetch/XMLHttpRequest/jQuery/axios 到 ApiEndpoint/ActionPath。
- 数据访问：method -> SQL -> table/column。

### 8.2 Impact Flow Engine 职责

Impact Flow Engine 是 CodeAtlas 的核心业务影响层。它不是要自研一个完整通用静态分析器，而是把企业 Java 变更分析中最常见、最可解释、最可测试的传播关系做成一套可增量刷新的事实图。

它优先产出三类底层 facts：

1. Code Change Impact facts：修改某段代码，会影响哪些调用方、入口、JSP/HTML/JS 页面触发点、batch/job、消息消费者、SQL、DB 表字段和测试。
2. DB Change Impact facts：修改某张表或某个字段，会影响哪些 SQL、Mapper/DAO、Service、入口、页面、客户端请求、batch/job 和测试。
3. Variable Impact facts：某个变量、参数、DTO 字段、request 参数、JSP/HTML input、JS 请求参数或 batch 参数会影响哪些代码、SQL、DB 字段、返回值和页面展示。

Feature Change Planning 和 Feature Addition Planning 不属于底层 Flow facts。它们由 Feature Planner 消费 Impact Flow facts、符号检索、历史提交、用户输入、AI 候选和人工确认，输出 `FeatureSeed/FeatureScope/ChangePlanReport` artifact。

Impact Flow Engine 分为五个子图：

- Call Impact Graph：method/class/config/entrypoint 之间的调用、依赖、入口归因、异步边界和调度边界。
- Web Client Impact Graph：JSP/HTML 页面、表单、输入、链接、script、DOM event、ClientRequest 到后端 endpoint/action 的浏览器侧触发关系。
- Data Impact Graph：`Method -[:BINDS_TO]-> SqlStatement -[:READS_COLUMN|WRITES_COLUMN]-> DbColumn` 的有向数据影响。
- Variable Impact Graph：local variable、param、field、DTO/Form property、Map key、request/session/model、JSP/HTML input、JS 请求参数、CLI/job 参数的传播。
- Method Summary：把方法内部变量传播压缩为可复用摘要，供变量影响、DB 影响和 Feature Planner 复用。

Impact Flow Engine 的输入输出：

| 输入 | 来源 | 用途 |
| --- | --- | --- |
| `symbolId` / `flowId` | Symbol Canonicalizer、framework adapter | 统一引用代码、资源、DB 和变量边界 |
| `FactRecord` | Spoon、Jasper、Web Client Analyzer、XML、SQL、ASM/ClassGraph | 构建调用、路由、客户端请求、SQL、字段和入口关系 |
| `MethodSummary` cache | 上一 snapshot 或本次 ScopeRun | 快速回答变量和 DB 影响 |
| `changeSet` | JGit、用户选择、上传 diff | 限定快速查询范围 |
| `AnalysisScopeDecision` | 导入审查用户确认 | 避免进入用户排除或不可分析范围 |

| 输出 | 是否进入 active graph | 说明 |
| --- | --- | --- |
| Call/Data/Variable facts | 是，需通过 staging | 底层影响查询事实 |
| Method Summary facts | 是，作为摘要事实 | 可被重建，不保存完整 AST |
| Candidate facts | 可进入 candidate relation family | 必须标记 confidence、sourceType 和 stale 规则 |
| FeatureScope/ChangePlanReport | 否，planning artifact | 由 Feature Planner 产生，不覆盖底层 facts |
| Blind spot / boundary | 否，diagnostics/report 字段 | 说明无法确认或需要人工确认 |

### 8.3 Method Summary 契约

Method Summary MVP 内容（精简范围，确定性规则匹配）：

- `paramSources`：入参、request/session/model/Map key、JSP/HTML input、CLI/job 参数来源。
- `assignFlows`：局部变量赋值、DTO/Form 直接 getter/setter 传播（不追踪对象图遍历，不追踪集合元素）。
- `callFlows`：caller argument 到 callee parameter 的直接映射（按位置/类型/注解匹配）。
- `returnFlows`：return value 与参数、字段的简单来源关系（不跨方法内联分析）。
- `receiverFlows`：仅追踪 `this.xxx` 字段访问和直接变量 receiver；不做对象图遍历或 builder/fluent call 追踪。
- `fieldSideEffects`：方法对 field、DTO/Form property、entity property 的直接读写（不追踪 indirect mutation through aliases）。
- `scopeWrites`：request/session/model attributes 的 set/remove/put 写入。
- `sqlBindings`：Java 参数/DTO 字段到 Mapper parameter、SQL placeholder 的显式绑定（基于 MyBatis `@Param`、XML `#{param}` 等静态可解析映射）。
- `exceptionAndBranchBoundary`：标记 try-catch/条件分支/循环边界点，不展开异常流或分支内数据流分析。
- `confidence` 和 `evidence`：每条 flow fact 必须带 source range、framework evidence、SQL id 或 bytecode fallback evidence。

MVP 明确排除（进入 Tai-e 深度层）：
- `collectionFlows`：集合元素、数组索引、Map key/value 的传播——需要别名分析。
- `sqlResultFlows`：SQL 结果列→DTO/entity field→JSP expression 的反向追踪——需要指针分析。
- `receiverFlows` 的高级部分：builder/fluent call、对象图遍历——需要调用图精度。
- 框架级对象转换（MapStruct、Orika、BeanUtils.copyProperties）——需要反射或注解分析。

Method Summary 的实现粒度：

- 一个 Java `Method` 至少对应一个 `MethodSummary`；重载方法必须使用 descriptor 区分。
- summary 只保存跨方法需要复用的摘要，不保存完整局部变量图、AST 节点或 parser 对象。
- 局部变量默认通过 evidence 和 summary edge 表达；只有跨边界对象（ParamSlot、ReturnSlot、SqlParameter、DbColumn）进入主图节点。
- summary schema 变更触发 scope-level invalidation，不复用旧 summary。
- 未覆盖场景标记 `analysisBoundary=METHOD_SUMMARY_LIMITED`，confidence 降为 `LIKELY/POSSIBLE`，供深度层补充。

### 8.4 自研边界和降级规则

Impact Flow Engine 的自研边界：

- 只做确定性强、规则清晰、能用 fixture 覆盖的业务传播，例如参数传递、getter/setter、DTO/Form 字段、Mapper 参数、SQL table/column、request/session/model、JSP/HTML form、静态 ClientRequest、batch arg。
- 对复杂别名、多态、反射、动态 SQL、动态 shell、动态 JavaScript 和不完整 classpath 只产生 `LIKELY/POSSIBLE` 候选，并暴露 blind spot。
- 每条传播规则必须有 fixture、误报/漏报样例和 benchmark；不能通过规则堆叠静默扩大确定性。
- 开源侧车结果与 CodeAtlas 结果不一致时，生成 conflict/candidate，不自动覆盖确定事实。

Web Client 规则：

- 浏览器侧关系使用 `SUBMITS_TO/CALLS_HTTP/NAVIGATES_TO/LOADS_SCRIPT/HANDLES_DOM_EVENT`，不得直接创建 `CALLS` 到 Java method。
- HTML/JSP 静态结构可以作为确定 evidence；JS handler 和请求只在 URL、method、参数名、selector、页面上下文都可追溯时提升。
- 影响路径跨浏览器到后端时必须经过 `ClientRequest -> ApiEndpoint/ActionPath -> Method`，报告中显示客户端 evidence 和后端 routing evidence。
- 动态 JS、bundled/minified 且缺 source map、运行时路由和前端框架组件行为默认进入 candidate family，不污染 active graph 的确定路径。

反射规则：

- 识别 `Class.forName`、`ClassLoader.loadClass`、`Method.invoke`、`Constructor.newInstance`、`ServiceLoader`、BeanUtils、PropertyDescriptor、Spring/XML/properties class name、枚举/字符串驱动工厂等反射线索。
- 反射线索先生成 `ReflectionCandidate`，记录 callsite、字符串来源、候选 class/member、classpath 命中、配置 evidence 和 resolver status。
- `REFLECTS_TO` 默认属于 candidate relation family，confidence 为 `POSSIBLE`；当字符串常量、classpath、配置和唯一目标同时成立时可提升到 `LIKELY`。
- 反射候选不得直接写成确定 `CALLS`。只有非反射 analyzer 或人工确认给出独立 evidence 后，才能由对应 analyzer 产生确定调用或绑定事实。
- 影响路径经过反射候选时必须设置 `analysisBoundary=REFLECTION`，并在报告中展示候选目标数量和未解析原因。

异步、消息和调度规则：

- 同步 Java 调用使用 `CALLS/INVOKES/ROUTES_TO`，异步边界使用 `PUBLISHES_TO/CONSUMES_FROM/SCHEDULES/TRIGGERS`，不得把消息发布误建成同步 `CALLS`。
- 静态注解、XML、properties、cron 表达式、队列/topic 名称明确时可到 `LIKELY/CERTAIN`；动态 topic、运行时拼接和外部调度平台只能是 `POSSIBLE`。
- 影响路径跨异步边界时必须设置 `analysisBoundary=ASYNC` 或 `analysisBoundary=SCHEDULED`，报告中区分“可能触发”和“同步调用”。

### 8.5 JNI/native 边界

JNI/native 边界规则：

- `NativeMethodFactMapper` 扫描 `JavaSourceAnalysisResult`，识别带 `native` 修饰符的方法和 `System.load/loadLibrary` 调用。
- Java `native` method → `HAS_NATIVE_BOUNDARY` → `boundary-symbol://{project}/{module}/{sourceRoot}/{qualifiedName}#{methodName}{signature}@NATIVE`
- Java `native` method → `CALLS_NATIVE` → `native-library://{project}/{module}/native/{libraryName}`
- `System.load/loadLibrary` 调用 → `CALLS_NATIVE` → `native-library://{project}/{module}/native/System_{load|loadLibrary}`
- `JavaSourcePipeline` 将 `JavaSourceFactMapper` 和 `NativeMethodFactMapper` 组合，一次分析产出全部事实。
- 影响路径遇到 native 边界时返回 `analysisBoundary=NATIVE`、`requiresManualReview=true`；native 边界不复用 `truncated`。

Boundary Symbol Inventory：

- L3 文件可以输出边界库存：JNI export、C/C++ header function、native library name、COBOL program id、copybook name、JCL step、shell executable、外部命令和配置引用。
- `NativeLibrary/Project -[:EXPORTS_SYMBOL]-> BoundarySymbol` 表示可见导出或声明；`Method/ShellScript/ConfigKey -[:REFERENCES_SYMBOL]-> BoundarySymbol/NativeLibrary/ExternalCommand` 表示 Java 或脚本侧引用。
- `EXPORTS_SYMBOL/REFERENCES_SYMBOL` 不能表达内部执行路径，只能帮助报告指出“这里跨出 CodeAtlas 当前分析能力边界”。
- C/C++、COBOL、JCL 和外部命令默认返回 `analysisBoundary=C_BOUNDARY|COBOL_BOUNDARY|EXTERNAL|NATIVE`，需要用户确认或后续 adapter 才能提升为更具体的影响路径。

### 8.6 Data Impact Graph 有向语义

Data Impact Graph 有向语义：

- `Method -[:BINDS_TO]-> SqlStatement` 表示 Java 方法或 Mapper 方法绑定到 SQL。
- `ClientRequest -[:SUBMITS_TO|CALLS_HTTP]-> ApiEndpoint/ActionPath -[:ROUTES_TO]-> Method` 是浏览器侧输入进入后端数据访问的唯一主路径。
- `SqlStatement -[:READS_COLUMN|WRITES_COLUMN]-> DbColumn` 表示 SQL 对字段的读写；表级关系只用于字段未知或 SQL parser 降级。
- `SqlParameter -[:BINDS_PARAM]-> ParamSlot/Field/RequestParameter` 表示 Java 值绑定到 SQL placeholder。
- 查询结果回流使用 Method Summary 的 `sqlResultFlows`，连接 `DbColumn -> DTO/entity field -> ReturnSlot/ModelAttribute/JspExpression/HtmlRenderSlot/ClientInitData`。
- affected rows、generated keys、stored procedure out parameter 必须单独标记；不能当成普通 column read。

MyBatis 动态 SQL 部分求值：

- `SqlStatement` 表示 mapper 的逻辑 statement；动态 SQL 不把所有运行时路径压扁成一条确定 SQL。
- Preprocessor 展开 `<include>`、`<where>`、`<set>`、`<trim>`、`<choose>`、`<if>`、`<foreach>` 等结构，生成多个 `SqlVariant`；每个 variant 绑定 `SqlBranchCondition`、参数映射、规范化 SQL skeleton、XML path 和 evidence。
- `SqlVariant` 必须带 `variantCompleteness=COMPLETE|PARTIAL|UNPARSEABLE`、`dynamicBoundary`、`unresolvedOgnl[]`、`unresolvedInclude[]` 和 `parserStatus`。
- JSqlParser 只解析已经规范化的 variant SQL。解析成功时输出 `SqlVariant -[:READS_COLUMN|WRITES_COLUMN]-> DbColumn`；字段不可判定时降级到 table-level impact 或 candidate relation。
- 动态 SQL 的列级事实默认不高于 `LIKELY`；存在 unresolved OGNL、动态 `${}`、动态表名、动态列名或 vendor function 未解析时只能是 `POSSIBLE/PARTIAL`。
- 当所有可解析 variant 都一致读写同一 table/column，且没有 unresolved dynamic boundary 时，可以聚合到所属 `SqlStatement` 的稳定读写关系；聚合 fact 必须保留 variant evidence 列表。

### 8.7 路径搜索和剪枝策略

路径搜索和剪枝策略：

- `pathConfidence` 使用保守聚合，默认取路径中最低 confidence，并保留每条边的 sourceType/evidenceKey。
- `pathPriority` 用于排序，综合入口距离、变更距离、边类型权重、历史风险、用户关注范围、测试覆盖、fanout 惩罚和 AI ranking 建议；不得提升 `pathConfidence`。
- 默认边权优先级：`ROUTES_TO/SUBMITS_TO/CALLS_HTTP/INVOKES/CALLS/BINDS_TO/READS_COLUMN/WRITES_COLUMN` 高于 AI candidate 和深度侧车 candidate；跨 `ASYNC/SHELL_DYNAMIC/NATIVE/C_BOUNDARY/COBOL_BOUNDARY/EXTERNAL/REFLECTION/MYBATIS_DYNAMIC/JSP_DYNAMIC/CLIENT_JS_DYNAMIC` boundary 需要降权并显示边界。
- 查询必须有 `maxDepth`、`maxNodes`、`maxPaths`、`maxFanoutPerNode`、`timeoutMs` 和 cycle guard。热点节点例如 BaseService、CommonDAO、通用 util、全局 interceptor 必须触发 fanout cap，并返回 `truncated=true` 与 `truncationReason=HOT_NODE|DEPTH|TIMEOUT|FANOUT`。
- 关系族不同的搜索要分阶段进行：先找入口和直接数据影响，再扩展候选/侧车/AI 边；默认报告不得把候选边和确定边混在同一等级展示。

### 8.8 Variable Impact 分层

Variable Impact 分三层：

1. 方法内影响：
   - 局部变量 def-use。
   - 参数传递。
   - 赋值、return 来源。
   - `request.getParameter`。
   - getter/setter 简单传播。

2. 跨方法影响：
   - Controller/Action/main/batch/message handler 参数到 Service。
   - Service 参数到 DAO/Mapper。
   - DTO/Form 字段传播。
   - JSP/HTML input 和 JS payload key 到 request parameter。
   - SQL 参数绑定。

3. 影响切片：
   - 页面字段、JS 请求参数、命令行参数或 job 参数最终影响哪些 SQL。
   - request 参数最终流向哪些 Service/DAO。
   - DB 字段最终展示在哪些 JSP/API，或被哪些 batch/shell 入口读写。
   - 修改一个方法、字段、参数后影响哪些入口和数据路径。

### 8.9 Feature Planner 报告分层

Feature Planner 报告分层：

Feature Planner 是 planning/report artifact 层，不声称新分析能力。它消费 `FeatureSeed`、Impact Flow facts、历史提交、符号/向量搜索、用户确认和 AI 候选，产出 `FeatureScope/ChangePlanReport` artifact。它不写入 `CALLS/BINDS_TO/MAPS_TO_COLUMN` 等底层确定事实，功能边界推断标记为 AI-assisted candidate。

Feature Change Planning 报告分层：

- 必须关注：确定属于该功能且变更风险高的入口、代码、SQL、DB、配置或测试。
- 建议检查：有调用/数据关系，但功能归属不完全确定。
- 可能相关：命名、历史提交、AI 候选、弱证据推断相关。
- 无需修改但需要回归：被影响但通常不需要改代码的入口、路径或测试点。

Feature Addition Planning 报告分层：

- 推荐参照：相似入口、页面、Service、Mapper、SQL、DB、测试和历史提交。
- 建议新增：API/JSP/Action/Controller、Service、DTO/Form、Mapper/DAO、SQL、table/column、migration、配置、权限、测试。
- 建议复用：现有校验器、事务边界、异常码、返回结构、公共查询、权限模型。
- 风险检查：公共 Service 扩展、枚举/switch、DB 约束、老数据兼容、batch/job、shell、配置和权限影响。

### 8.10 修改后验证与测试数据

修改后验证不是重新生成一份默认 Git diff 报告，而是把用户修改后的新 snapshot 与原 `ModificationPlan/TestPlan` 对照：

```text
Original RequirementInput / ChangeIntent
  -> ModificationPlan + TestPlan
  -> 用户修改代码
  -> Repository Refresh 发布新 snapshot
  -> PostChangeValidationReport
```

`PostChangeValidationReport` 必须包含：

- 已覆盖：原计划中必须修改项、建议检查项和测试项是否被本次变更触达。
- 未覆盖：原计划要求关注但 diff 未触及、也没有用户 waiver 的项。
- 新增影响：本次修改引入的新入口、SQL、DB、JSP/HTML、batch/job 或配置影响。
- 风险残留：仍为 partial/candidate、被截断、动态边界、缺 classpath 或需要人工确认的项。
- 测试计划更新：新增或删除的测试场景、测试数据和回归范围。

`TestDataSuggestion` 生成规则：

- 正常数据来自需求描述、现有测试、示例页面输入、SQL where 条件和历史 fixture。
- 边界数据来自 Java/JSP/HTML 校验、DB column 长度/非空/唯一约束、枚举/switch、正则和错误码。
- 异常数据覆盖空值、非法格式、越界长度、重复值、权限不足、状态不允许、缺失依赖和动态边界。
- 历史兼容数据覆盖旧 DB 记录、老格式字段、空字段、迁移前数据和批量导入数据。
- 每条测试数据必须标注 `source=REQUIREMENT|CODE_VALIDATION|DB_SCHEMA|SQL|JSP_HTML|HISTORY_TEST|AI_INFERRED|USER_CONFIRMED`；`AI_INFERRED` 默认需要人工确认，不能作为确定业务规则。

### 8.11 外部深度工具定位

外部深度工具只作为侧车：

- Joern：离线深度 CPG/slicing，补充复杂 data-flow/path evidence，结果默认不高于 `LIKELY`，除非能被 CodeAtlas facts 验证。
- ProGuardCORE：MVP 只用于缺源码 class/jar/war 的 class 结构读取补强；字节码 method summary fallback 是增强能力，只能作为候选证据，不能作为 supported 确定链路。
- Tabby：安全污点链路增强，重点覆盖危险 sink、反序列化、命令执行、SQL 注入等，不作为业务影响主事实源。
- WALA、SootUp/Heros、Tai-e、Doop：研究和增强候选，必须通过 license、稳定性、速度、准确率 benchmark 后才能进入可选 worker。

### 8.12 示例

代码影响示例：

```text
问题：改 UserService.update 会影响什么？

UserService.update()
  <- CALLS UserUpdateAction.execute()
     confidence=CERTAIN sourceType=SPOON evidenceKey=ev-java-221
  <- ROUTES_TO /user/update.do
     confidence=CERTAIN sourceType=XML evidenceKey=ev-struts-118
  <- SUBMITS_TO user_edit.jsp/html form or ClientRequest
     confidence=LIKELY sourceType=JASPER|HTML_TOKEN|JS_STATIC evidenceKey=ev-web-044
  -> CALLS UserDao.update()
     confidence=CERTAIN sourceType=SPOON evidenceKey=ev-java-236
  -> BINDS_TO UserMapper.update SQL
     confidence=CERTAIN sourceType=XML evidenceKey=ev-mybatis-031
  -> WRITES_COLUMN users.user_id
     confidence=CERTAIN sourceType=SQL evidenceKey=ev-sql-031
```

DB 影响示例：

```text
问题：修改 users.phone 字段会影响哪些代码？

users.phone
  <- WRITES_COLUMN UserMapper.updatePhone SQL
     confidence=CERTAIN sourceType=SQL evidenceKey=ev-sql-081
  <- BINDS_TO UserDao.updatePhone()
     confidence=CERTAIN sourceType=XML evidenceKey=ev-mybatis-081
  <- CALLS UserService.updatePhone()
     confidence=CERTAIN sourceType=SPOON evidenceKey=ev-java-382
  <- ROUTES_TO /user/updatePhone.do
     confidence=CERTAIN sourceType=SPRING evidenceKey=ev-spring-019
  <- SUBMITS_TO user_edit.jsp/html input[name=phone] or fetch('/api/user/phone')
     confidence=LIKELY sourceType=JASPER|HTML_TOKEN|JS_STATIC evidenceKey=ev-web-090 analysisBoundary=JSP_DYNAMIC|CLIENT_JS_DYNAMIC
```

变量影响示例：

```text
user_edit.jsp/html input[name=userId]
  -> SUBMITS_TO /user/update.do
     confidence=LIKELY sourceType=JASPER|HTML_TOKEN|JS_STATIC evidenceKey=ev-web-044
  -> ROUTES_TO UserUpdateAction.execute()
     confidence=CERTAIN sourceType=XML evidenceKey=ev-struts-118
  -> READS_REQUEST_PARAM request.getParameter("userId")
     confidence=CERTAIN sourceType=SPOON evidenceKey=ev-java-140
  -> BINDS_PARAM UserService.update(param[0])
     confidence=CERTAIN sourceType=METHOD_SUMMARY_FLOW evidenceKey=ev-flow-219
  -> BINDS_TO UserMapper.update
     confidence=CERTAIN sourceType=XML evidenceKey=ev-mybatis-031
  -> WRITES_COLUMN users.user_id
     confidence=CERTAIN sourceType=SQL evidenceKey=ev-sql-031

pathConfidence=LIKELY
truncated=false
```

功能修改规划示例：

```text
问题：我要修改“用户手机号校验”，都需要改哪里？

必须关注：
  UserController.updatePhone()
  UserService.validatePhone()
  UserMapper.updatePhone
  users.phone
  user/edit.jsp or user-edit.html input[name=phone]

建议检查：
  AdminUserController.updateUser()
  UserImportJob
  import-user-batch.sh

建议测试：
  用户资料修改、后台用户编辑、批量导入用户、老数据兼容
```

新增功能规划示例：

```text
问题：我要新增“手机号登录”功能，应该怎么落？

推荐参照：
  EmailLoginController.loginByEmail()
  PasswordLoginService.authenticate()

建议新增：
  /login/phone API
  PhoneLoginRequest DTO
  AuthService.loginByPhone()
  UserMapper.selectByPhone()
  手机验证码校验配置

风险检查：
  users.phone 唯一性、空手机号老数据、登录失败次数限制、后台修改手机号联动
```

## 9. 智能与快速设计

主要目标是智能和快速，因此采用快慢双层分析。

### 9.1 关系刷新快速层

关系刷新快速层：

- 基于 Git diff、Git commit/pull、文件 hash 或导入变化定位变更文件和符号。
- 基于 ClassGraph/ASM/JGit/缓存做秒级索引。
- 基于上一 snapshot 的 active facts 和 symbol index 直接定位变更符号，不等待全量源码模型重建。
- 仅对 changed Java/JSP/HTML/JS/XML/SQL 文件和缓存失效 scope 做增量 Spoon/Jasper/Web Client Analyzer/JSqlParser 分析。
- 对 changed Java 和受影响调用边运行 CodeAtlas Impact Flow Engine，刷新 method summary、变量影响、代码影响、DB 影响和 SQL 参数绑定；未通过 staging 校验的结果不得进入 active graph。
- 缺源码或只有 jar/class 的 scope 可用 ASM/ClassGraph 读取 classpath、注解、继承和轻量调用线索；ProGuardCORE 在 MVP 中只补强 class 结构读取，不生成 supported bytecode summary。
- 成功 commit 后重建受影响 SymbolIndex、CodeNavigationIndex、JVM InMemoryGraphCache 和派生索引。
- 关系刷新不默认生成用户报告；它只发布新的 active snapshot，并把受影响的历史报告、SavedQuery、WatchSubscription 标记 stale 或生成 notification candidate。

关系刷新快速层的判断标准不是“所有 analyzer 都跑完”，而是“能基于 changed scope 安全发布一个有证据、可降级、可继续补充的新 snapshot”。任何还没有通过 ScopeRun/staging 校验的内容，只能成为 pending/partial diagnostics，不能写进 active graph。

### 9.2 查询快速层

查询快速层：

- 基于当前 committed snapshot、FactStore active view、SymbolIndex、CodeNavigationIndex 和 JVM InMemoryGraphCache 回答用户问题。
- 对需求、bug 或设计片段，先由 AI 生成 `ChangeIntent` 和 `ExplorationPlan`，再由 CodeAtlas 执行受控检索和图谱查询。
- 在定义清楚的 benchmark profile 下，10 到 30 秒内生成初版需求探索、影响报告、修改计划或测试计划；超出预算时返回 partial/truncated/pending 状态，而不是伪装成完整报告。
- 后台深度层可补充更完整的变量流、候选范围、测试数据和盲区说明，但不能改变已发布 snapshot 的事实语义。

### 9.3 快速报告 completeness

快速报告 completeness 标记：

- `HOT_CACHE`：完全使用当前 committed snapshot 和已构建缓存。
- `PARTIAL_BINDING`：classpath、动态 SQL、JSP include、配置绑定或深度变量流不完整。
- `CACHE_MISS`：命中 FactStore active facts，但派生缓存缺失或过期。
- `STALE_SCOPE`：存在受影响 scope 尚未重新分析，报告必须展示 pending scope。
- `FALLBACK_ONLY`：只能使用 ASM/ClassGraph/tokenizer/静态配置 fallback。

SLA 验收 profile 必须记录：数据规模、FactStore backend、硬件/OS/JDK、Neo4j 配置（如适用）、冷/热缓存、并发数、最大变更文件数、最大路径深度、最大节点数、P95/P99、timeout、降级策略和 sample repository id。

快速报告输出状态：

| 状态 | 含义 | UI 展示 |
| --- | --- | --- |
| `COMPLETE_FAST` | 快速层所需 scope 都命中 committed facts 或已完成增量 commit | 展示初版报告，可等待深度增强 |
| `PARTIAL_FAST` | 有部分 scope pending、fallback 或候选边 | 展示可用路径和待补充范围 |
| `TRUNCATED_FAST` | 受 timeout、maxDepth、maxNodes、hot node 限制 | 展示截断原因和继续展开入口 |
| `PENDING_DEEP` | 快速报告可用，后台深度层仍在运行 | 展示 deep job 状态 |
| `FAILED_FAST` | 快速查询失败但旧 snapshot 可用 | 展示错误、旧报告或建议重新分析 |

### 9.4 FactStore 抽象与容量感知

FactStore（`FactStore` 接口）抽象事实存储后端，支持内存（`InMemoryFactStore`）和 Neo4j 两种实现：

- `InMemoryFactStore` 是开发、CI、小 fixture 和本地单用户试用的默认后端，使用 `ReadWriteLock` 实现 single-writer coordination；它可以作为 MVP 功能验收后端，但不能作为生产规模、并发或恢复能力验收结论。
- Neo4j 是多用户、大规模和生产部署的默认后端。生产验收、权限隔离、审计恢复和性能 SLA 必须在 Neo4j profile 上单独通过。
- `StoreSizing` 在单项目 active facts 超过 50 万时自动告警推荐切换；生产部署不得忽略该告警。
- `InMemoryFactStore` 在超过 500 万 active facts 时拒绝写入，强制要求切换到外部后端。
- 所有查询方法自动过滤 `active=false` 和 `tombstone=true` 的事实。Upsert 按五元组去重。
- `BatchOperationSupport` 提供按 factKey 稳定排序和指数退避重试。

### 9.5 ImpactAnalysisService 编排

`ImpactAnalysisService` 是影响分析的高级编排入口：

- `analyzeDiff(projectId, snapshotId, changedSymbolIds, maxDepth, maxPaths)` → `FastImpactReport`：对每个 changed symbol 同时执行 caller traversal 和 downstream traversal，合并路径、收集 DB 影响、构建路径详情和受影响符号列表。
- `traceVariable(…)` → `VariableTraceReport`：combined mode 变量溯源。
- `findWebBackendFlow(…)` → `WebBackendFlowSearchResult`：JSP → Action → Service → DAO → SQL 链路。
- `findReportImpact(…)` → `ReportImpactResult`：DB column → affected report 影响查询。
- `checkArchitecture(paths, rules)` → `ArchitectureCheckResult`：架构规则违规检测。
- dev/CI 默认使用 `InMemoryFactStore`；服务端和生产部署默认使用 Neo4j，并可通过 `ImpactAnalysisService.using(factStore)` 显式选择后端。

### 9.6 深度层

深度层：

- Spoon 完整源码模型，按后台任务补全缓存 miss、绑定解析和更大范围调用关系。
- Impact Flow Engine 后台补全更大范围跨方法变量影响、SQL/table/column 双向影响、功能范围候选、更多框架配置和 DTO/Form/Map/request/session/model 传播。
- Joern 作为优先深度 CPG/slicing 侧车，补充复杂路径 evidence；Tabby 作为安全污点侧车；Fraunhofer CPG 先作为 POC 和设计参考。
- WALA、SootUp/Heros、Tai-e、Doop 仅作为研究/可选增强 worker，不进入默认深度层。
- Jasper 生成 Servlet 后回映射 JSP。
- 在 benchmark profile 下，1 到 3 分钟内后台补充深度报告。

### 9.7 深度补充合并语义

深度补充合并语义：

- 深度层创建新的 `AnalysisRun/ScopeRun`，成功 commit 后产生新的 active snapshot 或追加 committed evidence。
- 原快速报告 artifact 保留，若底层 facts 变化则设置 `staleAgainstSnapshot`，UI 展示“有深度补充可用”。
- 深度结果只能提升有静态证据支持的 confidence；候选/侧车/AI evidence 不得覆盖确定 facts。
- 深度失败时快速报告继续可用，报告增加 `deepStatus=FAILED|TIMEOUT|PARTIAL` 和失败原因。

### 9.8 性能策略

性能策略：

- 增量扫描，基于 file hash 和 snapshot。
- 建立 dependency invalidation graph，记录 source file、resource file、classpath jar、TLD/taglib、JSP include、Struts config、Spring XML、MyBatis mapper/resultMap、properties、datasource/config key 到 analyzer scope 和 relationFamily 的依赖。
- 任一依赖变化时，只失效受影响 scope；无法计算影响范围时，把相关 module/web context 标为 `STALE_SCOPE`，并降级快速报告完整性。
- Spoon 增量缓存基于 `projectId + moduleKey + sourceRootKey + relativePath + fileHash`；缓存内容为 symbol summary、source range、annotation/direct call/field/method facts，不缓存完整 AST。
- 变更文件优先走轻量符号定位；只有需要刷新 Java facts 时才启动 Spoon changed-scope 分析。
- 按 module/source root/package 分批处理。
- 分析结果批量写 FactStore；生产后端为 Neo4j。写入后及时释放 AST/IR。
- MVP 阶段使用 JVM primitive adjacency cache 缓存热点 caller/callee 边。
- Joern、Tabby、WALA、SootUp/Heros、Tai-e、Doop 等侧车必须独立 worker 运行，设置独立 `-Xmx`、超时、失败隔离和结果降级；侧车失败不得阻塞快速报告。
- benchmark 证明瓶颈后再将高频调用边导出到 FFM OffHeapGraphIndex。
- Neo4j 配置 heap、page cache 和索引，避免大查询拖慢 PR 报告。

### 9.9 MVP 影响查询契约

MVP 影响查询契约：

- 输入是 `projectId`、`snapshotId`、`changeSet`，其中 `changeSet` 包含变更文件、变更符号、变更类型。
- 对 Java method 变更，先沿 `CALLS` 反向找调用方，再向上追溯 `INVOKES`、`ROUTES_TO`、`SUBMITS_TO` 找入口。
- 对 JSP 变更，直接以 `JspPage/JspForm/JspInput` 为入口，向下追踪 `SUBMITS_TO`、`ROUTES_TO`、`CALLS`、`BINDS_TO`、`READS_TABLE/WRITES_TABLE`。
- 对 batch/main/message/scheduler 变更，以对应 `EntryPoint` 或 `Method` 为入口，沿 `INVOKES/CALLS/BINDS_TO/READS_TABLE/WRITES_TABLE` 向下追踪。
- 对 shell 脚本变更，优先解析显式 `java -jar`、`java <mainClass>`、Gradle/Maven 启动和脚本引用配置；无法静态确认的动态命令返回 `POSSIBLE` 候选和 `analysisBoundary=SHELL_DYNAMIC`。
- 对 SQL/table 变更，沿 `BINDS_TO`、`CALLS`、`INVOKES`、`ROUTES_TO`、`SUBMITS_TO` 反向找入口。
- 对 DB column 变更，先找 `READS_COLUMN/WRITES_COLUMN/MAPS_TO_COLUMN` SQL statement，再反向映射 Mapper/DAO/Service/entrypoint；`ReportImpactQueryEngine.findAffectedReports(dbColumnId)` 精确匹配 column ID 或还原 table ID，同时标出读影响、写影响、展示影响和测试建议。
- `ReportImpactQueryEngine.findAffectedColumns(reportDefinitionId)` 反向查询：从报告定义出发，找到其依赖的 SQL → column/table facts。
- 对变量/参数/DTO 字段查询，以 `Variable Impact Graph` 和 method summary 为主，输出下游代码、SQL、DB 字段、返回值、页面或外部输出。
- 对已有功能修改，Feature Planner 先由 `FeatureSeed` 检索候选范围，再消费 Impact Flow facts、历史提交、搜索和 AI 候选，产出 `FeatureScope` 和 `ChangePlanReport` artifact。
- 对新增功能，Feature Planner 查找相似功能和架构落点，再输出推荐参照、建议新增、建议复用、风险检查和测试建议。
- 默认限制路径深度和返回节点数，超限时返回 `truncated=true` 和继续展开提示。
- 报告必须返回 `entrypoint`、`changedSymbol`、`path`、`confidence`、`evidenceList`、`sourceTypeList`、`riskLevel`、`reason`。
- 功能规划报告必须返回 `featureSeed`、`featureScope`、`requiredChanges`、`suggestedReviews`、`possibleRelated`、`regressionSuggestions`、`uncertainties` 和 evidence keys。

### 9.10 JVM 内存缓存契约

MVP 内存缓存契约：

- JVM InMemoryGraphCache 只缓存当前 active snapshot 的 primitive 邻接表。
- 缓存内容来自 FactStore active facts，不作为事实源。
- 缓存按 `projectId + snapshotId + relationGroup + cacheBuildId` 分片。
- 增量分析 tombstone 或新增关系后，只重建受影响分片。
- 缓存失效时必须回退到 FactStore 查询；生产部署通常回退 Neo4j，不能影响报告正确性。
- 查询开始时必须 pin 到同一个 committed `snapshotId`、Active View interval 和 `cacheBuildId`。cache miss 回退 FactStore 时也必须使用相同 snapshot 区间查询，不能混用新旧 facts。
- commit 与查询并发时，查询继续使用已 pin 的 snapshot；新 snapshot 只对后续查询可见。
- 缓存指标至少包含 node count、edge count、heap bytes、hit ratio、P95 query time。

## 10. FFM OffHeapGraphIndex

FFM 使用 JDK 22+ Foreign Function & Memory API，建议运行平台使用 JDK 25 LTS。被分析项目仍可为 Java 6/7/8。

FFM 是 benchmark 驱动的增强能力，不进入 MVP 关键路径。MVP 先使用 FactStore（生产 profile 为 Neo4j）+ JVM primitive adjacency cache；只有同时满足“规模/性能触发条件”和“收益证明条件”时才启用 FFM。

`FfmGraphFormat` 定义了 CSR/CSC off-heap 图格式设计：

- Header（64 字节）：version、nodeCount、edgeCount、各区域偏移量。
- Node Table：identityId 字符串池偏移量 + 长度。
- CSR Index：每个 source node 的边偏移 + 计数。
- CSC Index：每个 target node 的边偏移 + 计数。
- Edge Table：targetIndex + edgeTypeOrdinal。
- `EdgeType` 枚举覆盖 CALLS/INVOKES/READS_TABLE/WRITES_TABLE/BINDS_TO/ROUTES_TO/HAS_NATIVE_BOUNDARY/CALLS_NATIVE/PASSES_PARAM/READS_COLUMN/WRITES_COLUMN。
- `ActivationPolicy` 要求绑定命名 benchmark profile 才路由到 FFM。

### 10.1 启用条件

规模/性能触发条件满足任一项即可：

- 单项目 active edge 数达到百万级以上。
- Neo4j 多跳路径查询 P95 连续 benchmark 超过影响报告 SLA。
- JVM adjacency cache 对 heap 造成稳定压力，例如缓存常驻占用超过为 graph cache 分配的 heap budget。
- 影响路径搜索中 visited set/frontier queue 成为主要内存热点。

收益证明条件必须全部满足：

- 同一数据集、同一查询模板下，FFM P95 明显优于 JVM primitive adjacency cache。
- FFM heap 占用明显低于 JVM cache。
- FFM 结果和 FactStore active facts 校验一致。
- FFM 构建/加载失败时可以无损回退到 FactStore/JVM cache；生产部署通常回退 Neo4j。

### 10.2 Build manifest

FFM build manifest 必须包含：

```text
projectId
snapshotId
cacheBuildId
relationGroup
schemaVersion
sourceFactCount
edgeCount
nodeCount
inputFactHash
indexChecksum
createdAt
```

### 10.3 发布规则

FFM 发布规则：

- 构建在临时目录完成，manifest 和 index checksum 校验通过后才原子 swap 为可读版本。
- 查询期必须 pin `snapshotId + cacheBuildId + relationGroup`；FFM miss 或 checksum 失败时回退同一 snapshot 的 JVM cache/FactStore active facts。
- 旧 FFM index 只能在没有查询引用后清理；半构建文件不得被 mmap。
- FFM 与 JVM cache 一样是派生索引，不能改变 confidence、priority 或 fact 生命周期。

### 10.4 数据结构

FFM 不保存复杂对象，只保存 primitive 和扁平结构：

```text
typedNodeId -> outgoing edge ids
typedNodeId -> incoming edge ids
edgeType[]
relationGroup[]
direction[]
confidence[]
priority[]
evidenceKeyOffset[]
visitedBitmap
impactFrontierQueue
```

推荐压缩结构：

```text
forward_offsets: long[nodeCount + 1]
forward_targets: long[edgeCount]
forward_edge_types: short[edgeCount]
forward_relation_groups: short[edgeCount]
forward_confidence: byte[edgeCount]
forward_priority: int[edgeCount]

reverse_offsets: long[nodeCount + 1]
reverse_sources: long[edgeCount]
reverse_edge_types: short[edgeCount]
reverse_relation_groups: short[edgeCount]
```

### 10.5 覆盖范围

覆盖范围：

- MVP 后的首个 FFM 版本只加速 `CALLS/INVOKES/ROUTES_TO/SUBMITS_TO/BINDS_TO/READS_COLUMN/WRITES_COLUMN` 等影响路径 relationGroup。
- `FeatureSeed/FeatureScope/ChangePlanReport` artifact 不进入 FFM 主路径索引；Feature Planner 可以引用 FFM 查询结果，但 artifact 生命周期仍按第 6.6 节处理。
- 如果只启用 caller/callee FFM，必须在 manifest 中标记 `relationGroup=CALL_GRAPH_ONLY`，DB/JSP/变量路径仍回退 JVM cache/FactStore；生产部署通常回退 Neo4j。

### 10.6 使用方式

使用方式：

- `Arena.ofConfined()`：单个分析任务临时内存，任务结束关闭。
- `Arena.ofShared()`：多线程共享只读索引。
- `FileChannel.map(..., Arena)`：大图索引落盘并 mmap，重启后快速加载。

### 10.7 边界

边界：

- FactStore committed facts 是事实源；生产部署通常由 Neo4j 承载。
- FFM 是可重建的高速缓存。
- 不把 Spoon `CtElement`、Joern/Tabby/WALA/SootUp/Tai-e IR、AI 文本或业务 DTO 放入 FFM。

## 11. RAG、Agent 与 MCP

CodeAtlas 需要 RAG，但不是普通文档 RAG，而是 Code Graph RAG。

### 11.1 Vector backend decision

- MVP default: Neo4j Vector Index, because vector recall must stay close to graph facts, `symbolId`, snapshot scope, and evidence keys.
- pgvector: optional lightweight backend for deployments that already operate PostgreSQL.
- OpenSearch: optional backend when full-text code search and vector search should share one search platform.
- Qdrant: optional dedicated vector backend when vector throughput, payload filtering, or independent scaling becomes important.
- 派生文档可以从本节整理更细的 backend 对比，但本文仍是选型事实源。

### 11.2 向量索引生命周期

向量索引生命周期：

- chunk 来源只能是 committed facts 的 evidence pack、脱敏源码片段、历史报告摘要、设计文档摘要和用户确认的 curated artifact。
- embedding 写入前必须完成源码脱敏、snippet 长度预算、project 权限标签、`snapshotId`、`evidenceKey`、`sourceIdentityId`、`chunkHash` 和 `staleAgainstSnapshot` 标记。
- 向量召回必须先做 project allow-list 和 snapshot filter，再返回候选；不得跨 project、datasource 或未授权 workspace 召回片段。
- 底层 fact tombstone 或 artifact stale 后，对应 chunk 必须删除、失效或标记 stale；默认查询不返回 stale chunk，除非用户请求历史报告。
- 向量结果只能作为 recall candidate，必须经 FactStore active facts、symbolId 或 evidenceKey 回查后才进入回答证据包。

```text
用户问题
  -> 意图识别
  -> 精确符号检索
  -> 向量语义召回
  -> Neo4j 图扩展
  -> 源码/JSP/HTML/JS/XML/SQL 证据收集
  -> AI 生成解释
```

### 11.3 RAG 适用边界

向量适合：

- 自然语言问答。
- 语义代码搜索。
- 相似代码发现。
- 老代码理解。
- 历史报告和设计文档召回。

向量不负责：

- 最终调用关系。
- 变量影响事实。
- SQL 表字段影响事实。
- 功能范围确定事实。
- JSP 到 Action 的确定链路。

### 11.4 Agent 定位

Agent 定位：

```text
Agent 是 CodeAtlas 内部的任务编排层，不是分析引擎，也不是事实判断层。
```

Agent 位于 Query/Planning Services、AI Summary、Report Builder 和可选 MCP facade 之上。它负责把用户目标拆成多个受控步骤，调用已有服务，管理状态、超时、重试、候选确认和结果聚合。Agent 不直接扫代码、不写图谱 facts、不执行 Cypher、不执行 shell、不改代码、不绕过 staging/commit。

系统内角色分工：

```text
Analyzer / Graph / Impact Flow
  -> 产生事实

Query / Planning Services
  -> 基于 committed facts 做确定性查询和 planning artifact

AI
  -> 理解、摘要、候选、解释、排序

Agent
  -> 编排任务流程、管理状态、聚合结果、发起必要追问

MCP
  -> 可选外部访问协议层
```

Agent 的价值是让系统更会“办事”，不是让系统更会“造事实”。没有 Agent 时，UI/REST 仍可以手动调用各个查询服务；有 Agent 后，用户可以只给出目标，由 Agent 组合查询、规划、证据和报告。

MVP Agent：

- `ImpactAnalysisAgent`：PR 影响分析。
- `DbImpactAgent`：表/字段变更到 SQL、代码和入口的双向影响分析。
- `VariableImpactAgent`：变量来源、流向和影响范围。
- `FeatureChangePlanAgent`：已有功能修改范围和回归建议。
- `FeatureAdditionPlanAgent`：新增功能参照、落点、风险和测试建议。
- `CodeQuestionAgent`：自然语言代码问答。

Agent 标准流程：

```text
1. Receive Goal
   接收用户目标、diff、符号、DB 字段、变量、功能描述或自然语言问题。

2. Build Task Context
   固定 projectId、snapshotId、datasourceKey、用户权限、预算、是否允许 AI 候选、是否允许深度补充。

3. Select Plan
   根据 intent 选择调用 query、planning、report、RAG、AI summary 或导入审查服务。

4. Execute Steps
   按顺序或并行调用白名单内部服务；每一步只读 committed facts 或创建 planning/report artifact。

5. Handle Ambiguity
   多项目、多 datasource、多同名符号或候选过多时，向用户追问或返回 Candidate Picker。

6. Merge Results
   合并确定路径、候选路径、盲区、pending scopes、deep job 状态、风险和测试建议。

7. Produce Artifact
   输出 ImpactReport、VariableImpactReport、DbImpactReport 或 ChangePlanReport。

8. Continue / Update
   快速报告先返回，深度层完成后生成补充 artifact，并标记原报告 stale/upgrade available。
```

典型编排示例：

```text
用户：我要修改“用户手机号校验”，都需要改哪里？

FeatureChangePlanAgent
  -> AI/keyword 生成 FeatureSeed 候选
  -> symbol.search 查入口、页面、Service、Mapper、DB 字段
  -> feature planner 生成 FeatureScope 候选
  -> graph.findImpactPaths 查确定路径
  -> db.findCodeImpacts 查 users.phone 影响
  -> variable.findImpacts 查 phone 参数和 DTO 字段传播
  -> report builder 合并 required/suggested/possible/test/uncertainties
  -> AI summary 生成解释
  -> ChangePlanReport artifact
```

Agent 状态机：

```text
CREATED -> PLANNING -> WAITING_FOR_USER?
        -> RUNNING_FAST -> FAST_READY
        -> RUNNING_DEEP? -> COMPLETED
        |-> PARTIAL
        |-> FAILED
        |-> CANCELLED
```

Agent 状态字段至少包含：

```text
agentRunId
agentType
projectId
snapshotId
queryId?
reportArtifactId?
status
currentStep
pendingQuestions[]
pendingScopes[]
deepJobIds[]
partialResults[]
warnings[]
errors[]
cost
createdAt
updatedAt
```

Agent 必须遵守的边界：

- 不直接访问 Neo4j driver、Evidence Store 文件路径、工作区文件系统或 analyzer 内部 API。
- 不写 `FactRecord`、`Materialized Edge`、`ScopeRun`、`Snapshot` 或 cache。
- 不执行项目构建、测试、shell、数据库 SQL 或任意外部命令。
- 不把 AI 输出升级为确定事实；AI 只能影响候选、排序、解释和 planning artifact。
- 不跨 project/datasource 自动合并候选；自动选择必须满足第 13 节的候选上下文门槛。
- 不因某一步失败而伪装完整结果；必须返回 `PARTIAL/FAILED/TRUNCATED/PENDING` 和原因。
- 不绕过权限、snapshot pinning、evidence redaction、cost budget 和审计日志。

Agent 与 MCP 的区别：

| 项 | Agent | MCP |
| --- | --- | --- |
| 系统位置 | CodeAtlas 内部任务编排层 | 可选外部协议层 |
| 主要作用 | 组合查询、规划、AI 摘要、报告和追问 | 让 IDE/外部 Agent 调用 CodeAtlas |
| 是否新增事实 | 否 | 否 |
| 是否核心必需 | 否，但能显著提升体验 | 否，是集成面 |
| 默认接口 | UI/REST 内部调用 | 外部 AI/IDE/Agent 调用 |

Agent 验收口径：

- 同一输入和同一 snapshot 下，Agent 结果可回放，结构化结果稳定。
- 用户拒绝 AI 或 AI 超时时，Agent 仍能返回非 AI 结构化报告。
- 多候选场景不会自动选错 project/module/datasource。
- 快速报告和深度补充的状态、pending scope、stale artifact 能正确展示。
- Agent 失败不会污染 active graph，不会触发事实 tombstone，不会隐藏已可用的结构化结果。

### 11.5 MCP 定位和工具列表

MCP 定位：

```text
MCP 是 CodeAtlas 的可选外部集成面。
```

MCP 本身不提供新的分析能力，不生成图谱事实，不访问底层 Neo4j/Cypher，不读取任意文件，不执行 shell，也不修改代码。它只把 CodeAtlas 已有的 query、planning、report 和 evidence 能力，以受控、结构化、可审计的方式暴露给外部 AI Agent、IDE 和企业自动化流程。

CodeAtlas 核心能力必须在没有 MCP 的情况下完整可用：

```text
CodeAtlas Core
  -> Import / Static Analysis / Graph / Impact Flow / Evidence / Reports / AI Summary
  -> REST / UI

Optional MCP
  -> Whitelisted read-only facade over Core query and planning services
  -> Stable schemas for IDE / Agent integration
  -> Security, budget, audit, snapshot pinning
```

MCP 的价值是让外部工具复用 CodeAtlas 的分析结果，而不是让外部工具绕过 CodeAtlas 自己做分析。外部 Agent 通过 MCP 拿到结构化路径、证据和状态后，可以继续追问、展开路径、请求报告或生成改动计划，但不能通过 MCP 直接写图谱、执行命令或读取项目文件。

MCP 分层：

| 层 | 职责 | 禁止事项 |
| --- | --- | --- |
| Public MCP Tools | 面向 IDE/外部 Agent 的只读工具、稳定 schema、限流和审计 | 不接受任意 Cypher/SQL/文件路径/shell |
| Internal Tool Facade | 把 MCP tool 映射到 CodeAtlas query/planning/report/evidence service | 不绕过权限、snapshot pinning 或 cost budget |
| Core Services | Impact Query、DB Impact、Variable Impact、Feature Planner、Report Builder | 不暴露内部未提交 facts |
| Graph/Fact Storage | Neo4j、Evidence Store、cache、vector index | 不直接对 MCP 开放 |

第一版 MCP 只读。MVP 建议暴露最小稳定工具：

```text
symbol.search
graph.findImpactPaths
variable.findImpacts
db.findCodeImpacts
impact.analyzeDiff
report.getImpactReport
```

增强工具在 schema 稳定、权限和成本预算完成后再开放：

```text
graph.findCallers
graph.findCallees
variable.traceSource
variable.traceSink
feature.planChange
feature.planAddition
jsp.findBackendFlow
rag.semanticSearch
```

禁止开放的工具类型：

```text
file.readArbitrary
graph.runCypher
db.runSql
shell.run
code.modify
facts.write
snapshot.commit
cache.rebuild
```

### 11.6 MCP 工具级契约

MCP 工具返回稳定 JSON，不返回只适合人阅读的自由文本。所有自然语言摘要都必须放在可选字段中，结构化字段必须足够让外部 Agent 继续展开、过滤和追问。

MCP 工具级契约：

```text
input:
  projectId
  snapshotId?
  symbolId? / flowId? / artifactId?
  queryText?
  changeSet?
  maxDepth
  limit
  timeoutMs

output:
  requestId
  toolName
  projectId
  snapshotId
  cacheBuildId?
  status: COMPLETE | PARTIAL | TRUNCATED | PENDING | ERROR
  results[]
  paths[]
  candidates[]
  artifacts[]
  evidenceKeys[]
  warnings[]
  errors[]
  partial:
    pendingScopes[]
    staleScopes[]
    completeness[]
  truncation:
    truncated
    truncationReason?
    maxDepth?
    maxNodes?
    maxPaths?
  confidence:
    pathConfidence?
    confidenceBoundary?
    candidateOnly?
  cost:
    expandedNodes
    expandedEdges
    evidenceSnippets
    elapsedMs
    cacheHit
  audit:
    principal
    policyDecision
    redactionApplied
```

路径边 schema：

```text
PathEdge
  sourceIdentityId
  targetIdentityId
  relationType
  direction
  confidence
  priority
  sourceType
  evidenceKey
  analysisBoundary?
```

重点工具：

- `symbol.search` 返回候选符号、identityType、project/module/datasource 上下文、confidence 和 evidence key；多候选时不得自动替用户选择。
- `graph.findImpactPaths` 返回路径边数组，每条边包含 relationType、confidence、priority、sourceType、evidenceKey 和 boundary。
- `impact.analyzeDiff` 返回 fast report status、pending scopes、truncated 状态和 deep supplement job id。
- `db.findCodeImpacts` 返回 read/write/display/test impact 分组，必须区分 table 级降级和 column 级确定影响。
- `variable.findImpacts` 返回 source/sink/downstream impacts，并标记 method summary、JSP、SQL、async、shell 或 native boundary。
- `report.getImpactReport` 只读取已存在报告 artifact 或当前 snapshot 查询结果，不触发重型 analyzer。
- `feature.planChange` / `feature.planAddition` 只返回 planning artifact，不返回可直接执行的修改；必须包含 `uncertainties` 和 evidence keys。
- `rag.semanticSearch` 返回 recall candidates，不返回确定事实；调用方必须继续使用 symbol/graph/report 工具构建 evidence pack。

### 11.7 MCP 与 REST/UI 的关系

REST 是 CodeAtlas 平台默认接口，UI、worker callback、内部自动化和普通企业集成都可以只使用 REST。MCP 是可选协议层，主要服务 AI/IDE/Agent 标准接入。

```text
UI
  -> REST
  -> CodeAtlas Core

External Agent / IDE
  -> MCP
  -> Internal Tool Facade
  -> CodeAtlas Core
```

REST 和 MCP 必须共享同一套 query/planning/report service、权限模型、snapshot pinning、evidence redaction 和 cost budget。MCP 不能有一套独立业务逻辑，避免 UI、REST 和 MCP 对同一问题返回不同语义。

### 11.8 外部 Agent 可用性要求

MCP 是给外部 Agent/IDE 调用的，因此输出必须便于二次推理和继续调用：

```text
Agent step 1: symbol.search
  -> candidates[]

Agent step 2: graph.findImpactPaths(candidate.symbolId)
  -> paths[] + evidenceKeys[] + status

Agent step 3: report.getImpactReport(reportId or query)
  -> summary + impactedItems + uncertainties

Agent step 4: external Agent writes its own plan
  -> CodeAtlas facts remain unchanged
```

Agent 不能依赖自然语言摘要解析关键字段。所有关键判断必须存在结构化字段：

```text
status
snapshotId
identityId
relationType
confidence
evidenceKey
analysisBoundary
truncated
pendingScopes
cost
```

### 11.9 安全要求

安全要求：

- MCP 默认只读；MVP 不开放写工具。
- 禁止任意 Cypher。
- 禁止任意 SQL。
- 禁止任意文件读取。
- 禁止任意 shell。
- 禁止直接写 graph facts、staging facts、snapshot 或 cache。
- 工具白名单。
- project allow-list 和 datasource 边界。
- snapshot pinning。
- 返回结果限量。
- evidence snippet 脱敏。
- 全量审计日志。
- per-tool cost budget。
- 写操作即使未来加入，也必须人工确认。

### 11.10 实现约束

实现约束：

- MCP tool 参数只能是结构化参数，例如 `projectId`、`snapshotId`、`symbolId`、`maxDepth`、`limit`、`queryText`。不得接受原始 Cypher、SQL、文件路径通配符或 shell 命令。
- project 级权限在 tool dispatch 前检查，拒绝时返回结构化 forbidden error，并写审计日志。
- 所有查询开始时 pin 到 committed `snapshotId` 和可用 `cacheBuildId`；cache miss 回退也必须使用同一 snapshot 区间。
- 源码脱敏在 evidence pack 构建阶段执行，默认移除密钥、token、password、连接串、个人信息样式字段，并限制单段 snippet 字数和总字符预算。
- 审计日志记录 `requestId`、`principal`、`projectId`、`toolName`、参数摘要、结果数量、是否脱敏、耗时、拒绝原因；不记录完整源码片段、API key 或大段 prompt。
- 所有工具必须有 per-tool `maxDepth`、`limit` 和 timeout 上限。超过上限返回 `truncated=true` 或 structured error。
- 每个工具必须声明 cost budget：最大展开节点、最大路径数、最大 evidence snippets、最大 token/字符预算和 timeout；超过预算返回 `status=TRUNCATED|PARTIAL`。
- MCP server 必须是 thin facade：只做 schema validation、authz、budget、audit、dispatch 和 response shaping；不得直接访问 Neo4j driver、Evidence Store 文件路径或 analyzer worker 内部 API。
- 写操作即使未来加入，也必须同时满足工具白名单、显式 `confirmWrite=true`、人工确认意图和审计记录；MVP 不开放写工具。

### 11.11 MCP 发布口径

MCP 是可选集成能力。发布口径必须区分：

| 口径 | 含义 |
| --- | --- |
| Core without MCP | CodeAtlas UI/REST 完整可用，能导入、分析、查询、生成报告 |
| MCP local/dev | 本地开发或受限试用，允许连接 IDE/Agent，但只读、限量、审计 |
| MCP production | 完成 project allow-list、脱敏、审计、snapshot pinning、cost budget、tool schema regression 后对外开放 |

MCP 不作为核心分析闭环的前置条件。没有 MCP 时，CodeAtlas 仍必须能完成导入、分析、图谱提交、影响查询、AI 摘要和 UI 展示。

## 12. AI 使用原则

AI 不直接生成最终图谱事实。AI 可以参与分析过程，但它是被 CodeAtlas 调用的辅助分析服务，不是项目扫描主体。CodeAtlas runtime 负责收集证据、校验 AI 输出、执行 analyzer，并决定哪些事实进入图谱。

### 12.1 AI 职责

AI 负责：

- 需求理解：把不完整需求、bug 描述、设计书片段、页面/字段/菜单名或截图 OCR 文本整理成 `RequirementInput` 和 `ChangeIntent`，并保留原文引用、假设和未知项。
- 探索计划编排：根据 `ChangeIntent` 生成 `ExplorationPlan`，决定要搜索哪些关键词、页面、Struts Action、Controller、Service、Mapper/DAO、SQL、表字段、配置、历史提交和测试。
- 项目画像：基于 `WorkspaceProfileEvidence` / `ProjectProfileEvidence` 判断项目类型、入口形态、优先分析 scope 和导入风险。
- 分析计划建议：建议运行 Java/Spring/Struts/JSP/MyBatis/shell/batch 等 analyzer 的顺序和范围。
- 候选事实生成：对反射、动态 SQL、shell 参数拼接、命名约定、旧框架约定和缺失 classpath 场景生成 `POSSIBLE/LIKELY` 候选。
- 候选排序和消歧：对多入口、多路径、多项目依赖、脚本启动候选进行排序，帮助快速报告先展示高价值路径。
- 功能种子识别：把“修改用户手机号校验”“新增手机号登录”这类自然语言转成候选关键词、入口、页面、类、方法、SQL、表字段、配置和历史提交。
- 功能范围归纳：基于 evidence pack 对 `FeatureSeed` 和 `FeatureScope` 做摘要、排序和不确定项说明。
- 修改计划生成：把探索结果和影响路径整理成 `ModificationPlan`，区分必须修改、建议检查、可能相关、无需修改但建议回归、无法判断需确认。
- 影响报告摘要。
- 变更计划摘要：把确定事实、候选事实和盲区整理成必须关注、建议检查、可能相关、建议新增、建议复用和建议测试。
- 风险解释。
- 测试计划和测试数据建议：生成 `TestPlan` 和 `TestDataSuggestion`，覆盖入口、场景、前置数据、输入样例、断言、回归范围和人工准备项。
- 修改后验证解释：基于用户修改后的新 snapshot 和原计划生成 `PostChangeValidationReport` 摘要，指出覆盖项、遗漏项、新增影响和测试计划更新。
- Review 关注点。
- 自然语言问答。
- 架构规则的辅助生成。

AI 输出的所有候选事实必须通过 `AiCandidateStagingValidator` 的 schema 校验（identity 存在性、evidenceKey 引用、confidence boundary、project/snapshot 权限、allowed relation type），然后进入 `AI_ASSISTED_CANDIDATE` relation family。AI 不能直接写入 `CALLS/INVOKES/BINDS_TO` 等静态分析 relation family。AI candidate 必须携带 `createdFromEvidencePackId`、`expiresAt` 或 `staleAgainstSnapshot`，证据变化时自动标记 stale。

### 12.2 AI 输入

AI 输入必须是证据包：

```text
Workspace/Profile inventory
Git diff
变更符号
Neo4j 路径
RequirementInput / ChangeIntent / ExplorationPlan
FeatureSeed / FeatureScope 候选
源码/JSP/HTML/JS/XML/SQL/shell 片段
置信度
历史缺陷/提交
测试覆盖
```

证据包由 CodeAtlas 构建，不由用户或 AI 自由拼接。用户可以提供功能描述、变更意图、候选入口、设计书片段、bug 描述、页面/字段/菜单名或排除范围，但这些输入必须先转成 `RequirementInput`、`ChangeIntent`、`FeatureSeed`、`AnalysisScopeDecision` 或 query parameter，再和 CodeAtlas 已知 evidence 合并。

AI 需求探索流程：

```text
RequirementInput
  -> AI Requirement Interpreter
  -> ChangeIntent + OpenQuestions
  -> ExplorationPlan
  -> CodeAtlas 执行 symbol/navigation/graph/db/variable/history/test 查询
  -> ExplorationResult + EvidencePack
  -> AI Modification Planner
  -> Static Validation
  -> ModificationPlan + ImpactReport + TestPlan + TestDataSuggestion
```

AI 可以在证据不足时主动提出最少必要问题，但不得以“缺设计书”为理由停止探索。默认策略是先基于当前 snapshot 找候选范围，再把仍然影响修改正确性的未知项放入 `OpenQuestions`。

AI 项目画像流程：

```text
Workspace Profiler
  -> 文件树、构建文件、source root、classpath 线索、框架配置、入口线索、不可分析项
  -> WorkspaceProfileEvidence

AI Provider
  -> 读取受限 evidence pack
  -> 输出 ProjectProfileCandidate：项目类型、边界、依赖候选、入口候选、风险、建议 analyzer 顺序

CodeAtlas Validator
  -> 校验路径存在、project/module 上下文、confidence boundary、用户权限和 schema
  -> 生成 ImportReviewReport 候选段落

User Confirmation / Analyzer Verification
  -> 用户确认 include/exclude、source root、lib/web root、项目关系
  -> analyzer 验证后才产生图谱事实
```

AI 在画像阶段不能自己读取磁盘、调用 shell、执行构建或访问任意仓库。它只看 CodeAtlas 给出的 evidence pack；如果 evidence 不足，AI 只能返回“需要用户确认/需要补充依赖/需要运行某 analyzer”的建议。

### 12.3 AI 输出

AI 输出必须包含：

- 结论。
- 证据路径。
- 置信度。
- 不确定项说明。
- 引用的文件、模块、symbol 或 evidence key；不得引用 evidence pack 中不存在的路径。

### 12.4 AI 输出约束

AI 输出约束：

- AI 候选默认不得高于 `LIKELY`，不能单独生成 `CERTAIN` graph fact。
- AI 候选必须带 `sourceType=AI_ASSISTED`、reason、evidence keys 和 `requiresStaticConfirmation` 标记。
- CodeAtlas 必须对 AI 返回的 analyzer plan、project boundary、dependency candidate 和 entrypoint candidate 做 schema 校验与存在性校验。
- AI 可以建议功能种子和功能范围排序，但不得把“看起来像同一个功能”直接升级为确定 `FeatureScope`；确定范围必须来自符号匹配、图谱路径、历史提交、用户确认或 analyzer evidence。
- 能被静态 analyzer、配置解析、字节码扫描或用户确认验证的候选，才可升级为更高置信事实；无法验证的候选必须保留为 `POSSIBLE` 或进入 blind spots。

### 12.5 AI candidate 写入语义

AI candidate 写入语义：

- AI 候选只能进入 `relationFamily=AI_ASSISTED_CANDIDATE` 或 planning artifact，不写入静态分析 relation family。
- AI 候选必须走 staging schema 校验：identity 存在性、evidenceKey 引用、confidence boundary、project/snapshot 权限和 allowed relation type。
- 默认影响路径查询不展开 AI candidate；只有用户打开“包含 AI 候选”或 Feature Planner 需要排序时才使用。
- AI candidate 必须带 `createdFromEvidencePackId`、`expiresAt` 或 `staleAgainstSnapshot`。底层 evidence 变化后自动 stale，不参与 active 确定路径。
- AI candidate 的 tombstone 只清理 AI candidate family，不能删除 Spoon/XML/JSP/SQL/Impact Flow facts。

### 12.6 AI 能力分层

AI 能力按风险和可验证性分为三层。实现时必须先做 Query AI，再做 Planning AI，最后才允许接入 Candidate AI。

| 层级 | 定位 | 典型任务 | 可行性 | 是否进入 MVP |
| --- | --- | --- | --- | --- |
| Query AI | 查询理解和结果解释 | intent 识别、候选排序、报告摘要、风险解释、测试建议 | 高 | 是 |
| Planning AI | 规划和归纳 | 项目画像建议、分析计划建议、功能修改规划、新增功能落点、FeatureScope 摘要 | 中高 | MVP 可做受限版 |
| Candidate AI | 弱证据候选发现 | 反射、动态 SQL、shell 拼接、旧框架约定、缺 classpath 场景下的候选关系 | 中 | 增强阶段，MVP 默认关闭或只读展示 |

Query AI 不改变图谱，只改变用户如何找到和理解结果。Planning AI 产出 artifact，不覆盖底层事实。Candidate AI 可以生成 `POSSIBLE/LIKELY` 候选，但必须独立 relation family、独立过期策略、默认不参与确定路径查询。

### 12.7 AI 可行性边界

AI 能力的可行性按“是否有结构化证据、是否能被 CodeAtlas 校验、失败后是否会污染事实链”判断。

高可行能力：

- 自然语言转 intent，例如把“这个字段影响哪里”识别为 `DB_CHANGE_IMPACT`。
- 需求/bug/设计片段转 `ChangeIntent` 和 `ExplorationPlan`，例如从“修改手机号校验”提取字段、页面、后端校验、DB 和测试方向。
- 多候选排序，例如同名类、同名表、多个项目或多个 datasource 时，根据上下文推荐候选。
- 结果摘要，例如把路径、证据、盲区和风险整理成可读报告。
- 测试建议，例如根据受影响入口、SQL、JSP、batch/job、历史测试生成回归建议。
- 功能修改和新增功能规划，例如归纳“必须关注、建议检查、可能相关、建议新增、建议复用、建议测试”。
- 测试数据建议，例如根据校验规则、DB 约束、JSP input、SQL where 条件、历史测试和需求描述生成正常/边界/异常/历史兼容数据。

中等可行能力：

- 项目画像建议，例如识别 Struts1 + JSP + Ant-like 老项目、Eclipse-only 项目或混合 workspace。
- 分析计划建议，例如推荐先跑 Java/Spring/Struts/JSP/MyBatis/shell/batch analyzer 的顺序和范围。
- 动态或弱证据候选，例如反射字符串、动态 SQL 片段、shell 参数拼接、命名约定和旧框架约定。
- FeatureScope 聚合，例如把相似入口、页面、Service、Mapper、SQL、DB 字段和历史提交聚成候选功能范围。

低可行或禁止能力：

- AI 直接生成 `CALLS`、`BINDS_TO`、`READS_COLUMN`、`WRITES_COLUMN` 等确定图谱事实。
- AI 自己读取完整项目、扫描磁盘、执行构建或执行 shell。
- AI 直接决定 active graph commit、tombstone、snapshot 发布或缓存重建。
- AI 进入 10 到 30 秒快速报告的关键路径，作为底层查询加速手段。
- AI 把“看起来相关”的功能范围升级为“必须修改”。
- AI 生成不能追溯来源的测试数据并当作确定业务规则。

### 12.8 AI 对准确率和速度的作用

AI 对准确率的贡献主要是提高召回、排序和解释质量，不是替代静态事实生成。

```text
准确率来源
  -> analyzer facts: Spoon / Jasper / XML / SQL / ASM / Impact Flow
  -> identity and schema: symbolId / flowId / artifactId / relation contract
  -> evidence validation: evidenceKey / source range / confidence boundary
  -> graph lifecycle: staging / ScopeRun / commit / active view
  -> benchmark and fixtures

AI 增益
  -> 更好的候选召回
  -> 更好的候选排序
  -> 更清楚的盲区说明
  -> 更贴近业务语言的功能归纳
  -> 更可读的风险和测试建议
```

AI 对速度的贡献主要是减少人的理解时间，不是减少底层图谱查询时间。快速报告的工程速度仍来自上一 snapshot、Symbol Index、FactStore active view、JVM InMemoryGraphCache、changed scope 增量刷新和查询剪枝；生产部署通常由 Neo4j 承载 active view。AI 摘要可以异步生成；如果 AI 超时，结构化报告必须先返回。

规则：

- 10 到 30 秒初版报告不得依赖 AI 完成；AI 超时只能使 `aiSummary.status=PENDING|FAILED`，不能使结构化影响报告失败。
- AI 可以对结果排序，但排序前必须已有结构化 candidates、paths 或 evidence pack。
- AI 可以提示“可能漏掉动态 SQL/shell/反射影响”，但不能因为模型猜测而提升事实 confidence。
- AI 摘要必须标注基于多少条静态证据、多少条候选证据和多少个 blind spot。

### 12.9 AI 输出验收

AI 能力必须有独立验收，不与 analyzer 正确性混在一起。

| 能力 | 验收指标 | 失败处理 |
| --- | --- | --- |
| Intent 识别 | 常见问题集准确率、错误 intent 可纠正 | 回退候选列表和手动选择 |
| 候选排序 | top-k 命中率、同名冲突误选率 | 不自动选中，要求用户确认 |
| 报告摘要 | 是否引用存在的 evidenceKey，是否遗漏 critical risk | 返回结构化报告，隐藏 AI 摘要或标记失败 |
| 项目画像建议 | project boundary、entrypoint、analyzer plan 的人工确认通过率 | 保留为 Import Review 候选，不进入图谱事实 |
| 功能规划 | required/suggested/possible 分类准确率、用户接受率 | 标记 `uncertainties`，不生成确定修改项 |
| AI candidate | 静态 analyzer 后续验证通过率、误报率、过期清理率 | 默认不参与确定路径，过期后自动 stale |

AI 输出必须支持回放测试。每次 AI 调用保存 `evidencePackId`、prompt template version、model/provider、input hash、output schema version、validation result 和脱敏后的错误原因。回放测试只使用 evidence pack，不重新扫描项目。

## 13. 检索与结果展示契约

CodeAtlas 的主入口应该是问答式检索，但结果不是聊天文本，而是可点击、可追溯、可展开的证据化分析报告。

### 13.1 检索入口

检索入口分四类：

- 问答式主入口：用户用自然语言提问，例如“userId 从哪里来，到哪里去”“改 UserService.update 会影响什么”“我要修改手机号校验要改哪里”“我要新增手机号登录怎么做”。
- 精确检索入口：用户搜索类、方法、JSP、HTML 页面、JS 请求、SQL、表、字段、配置、shell/job 入口，例如 `OrderService`、`/user/update.do`、`input[name=userId]`、`fetch('/api/user')`、`users.user_id`、`run-invoice-job.sh`。
- 图谱入口：用户从某个节点出发，查看调用方、被调用方、变量来源、变量流向、影响范围或展开一层。
- 功能规划入口：用户输入一个业务功能名、变更目标或新增功能描述，CodeAtlas 先构造 `FeatureSeed`，再生成 `FeatureScope` 和 `ChangePlanReport`。
- 架构健康入口：用户查看 module/package/class/method 的热点、循环依赖、fanout、边界风险、动态解析风险和可维护性指标，生成 `ArchitectureHealthReport`。

### 13.2 问答式检索流程

问答式检索流程：

```text
用户问题
  -> intent 识别
  -> symbol / db / feature candidate 检索
  -> 用户选择候选符号或功能范围，只有上下文完整且唯一高置信候选才可自动选中
  -> 确定性查询：FactStore active view / Impact Flow Index / JVM cache（生产通常为 Neo4j）
  -> evidence pack
  -> AI 摘要解释
  -> 结构化结果展示
```

### 13.3 Intent

首批 intent：

```text
SYMBOL_SEARCH
FIND_CALLERS
FIND_CALLEES
TRACE_VARIABLE_SOURCE
TRACE_VARIABLE_SINK
TRACE_VARIABLE_BOTH
IMPACT_ANALYSIS
CODE_CHANGE_IMPACT
DB_CHANGE_IMPACT
VARIABLE_IMPACT
FEATURE_CHANGE_PLANNING
FEATURE_ADDITION_PLANNING
JSP_FLOW
WEB_CLIENT_FLOW
SQL_TABLE_IMPACT
CONFIG_IMPACT
ARCHITECTURE_HEALTH
```

### 13.4 多候选处理

多候选处理：

- 搜索词匹配多个符号时，先显示候选列表，不直接追踪。
- 候选至少展示 symbol type、qualified name/path、文件、行号、projectKey、moduleKey、datasourceKey、snapshotId、identityType、confidence 和最近变更信息。
- 自动选中必须满足：同一 project/workspace、同一 datasource 或不涉及 datasource、identityType 明确、confidence 达到 `CERTAIN` 或唯一 `LIKELY` 且无同名冲突。
- 用户选择候选后再执行来源/流向/影响查询。

### 13.5 结果展示结构

结果展示固定为四层：

1. 答案摘要：显示找到几条确定路径、几条可能路径、主要来源/流向/影响点、需要修改/检查/测试的数量、风险等级、后台分析状态、completeness 和 snapshotId。
2. 证据路径：纵向链路为主，例如 `JSP/HTML input -> ClientRequest -> Action/Controller -> Service -> Mapper -> SQL -> Table`，每条边展示 edge type、confidence、sourceType、evidenceKey 和 analysisBoundary。
3. 证据列表：表格展示文件、行号、证据类型、证据片段、分析器来源。
4. 图谱与明细：可展开图谱、候选项、不确定项、原始 JSON、继续展开入口。

默认展示不得要求用户先理解 raw `symbolId`、`flowId`、`artifactId` 或原始 JSON。低层 ID 和 raw JSON 必须保留为下钻明细，供高级用户、调试和外部集成核对；面向普通用户的第一层展示应使用业务友好的名称、路径摘要、风险、证据数量和下一步动作。多候选时必须先显示 Candidate Picker，不能跨 project/module/datasource 自动选择。

结构化结果最小 schema：

```text
AnalysisResult
  requestId
  projectId
  snapshotId
  cacheBuildId?
  status: COMPLETE | PARTIAL | TRUNCATED | PENDING | ERROR
  intent
  selectedCandidate?
  summary
  paths[]
  impactedItems[]
  evidence[]
  uncertainties[]
  pendingScopes[]
  cost
  aiSummary?

Path
  pathId
  pathConfidence
  pathPriority
  truncated
  truncationReason?
  edges[]

PathEdge
  sourceIdentityId
  targetIdentityId
  relationType
  direction
  confidence
  priority
  sourceType
  evidenceKey
  analysisBoundary?
```

报告必须把 `status`、`pathConfidence`、`truncated` 和 `analysisBoundary` 作为一等字段，不得只藏在自然语言摘要里。

### 13.6 结果示例

变量影响结果示例：

```text
问题：userId 从哪里来，到哪里去？

摘要：
- 找到 3 条确定来源，2 条确定流向，1 条可能流向。
- 主要来源：/WEB-INF/jsp/user/edit.jsp 或 static/user/edit.html input[name=userId]
- 主要流向：UserMapper.update -> users.user_id

路径：
edit.jsp:42 input[name=userId]
  -> SUBMITS_TO /user/update.do
  -> ROUTES_TO UserUpdateAction.execute()
  -> READS_REQUEST_PARAM request.getParameter("userId")
  -> BINDS_PARAM UserService.update(param[0])
  -> BINDS_TO UserMapper.update
  -> WRITES_COLUMN users.user_id

每条边：
  confidence=CERTAIN|LIKELY
  sourceType=JASPER|JSP_TOKEN|HTML_TOKEN|JS_STATIC|XML|SPOON|METHOD_SUMMARY_FLOW|SQL
  evidenceKey=...
  analysisBoundary?=JSP_DYNAMIC|CLIENT_JS_DYNAMIC|ASYNC|SHELL_DYNAMIC|NATIVE
```

Web Client 影响结果示例：

```text
问题：页面上点击保存用户会调用哪里？

路径：
edit.html:14 input[name=userId]
  -> HANDLES_DOM_EVENT #saveButton click user-edit.js:42
  -> CALLS_HTTP fetch('/api/users', POST)
  -> ROUTES_TO UserController.save(UserDto)
  -> CALLS UserService.save(UserDto)
  -> BINDS_TO UserMapper.insert
  -> WRITES_COLUMN users.user_id

边界：
- user-edit.js:42 URL 为静态字符串，method 为 POST，confidence=LIKELY。
- payload 中 userId 来自 querySelector('[name=userId]').value。
```

影响分析结果示例：

```text
问题：改 UserService.update 会影响什么？

摘要：
- 风险：中
- 受影响入口：/user/update.do、/WEB-INF/jsp/user/edit.jsp
- 受影响数据：users、user_log
- 建议测试：UserUpdateActionTest、UserServiceTest、user/edit.jsp 表单回归
```

DB 影响结果示例：

```text
问题：users.phone 字段修改会影响哪些代码？

摘要：
- 风险：高
- 受影响 SQL：UserMapper.selectByPhone、UserMapper.updatePhone
- 受影响入口：/login/phone、/user/updatePhone.do、UserImportJob
- 建议检查：唯一索引、老数据空值、批量导入格式、后台用户编辑
```

功能修改规划结果示例：

```text
问题：我要修改“用户手机号校验”，需要改哪里？

必须关注：
- UserService.validatePhone()
- UserController.updatePhone()
- users.phone
- user/edit.jsp 或 user-edit.html input[name=phone]

建议检查：
- AdminUserController.updateUser()
- UserImportJob
- import-user-batch.sh

建议测试：
- 用户资料修改、后台用户编辑、批量导入用户、手机号重复校验
```

新增功能规划结果示例：

```text
问题：我要新增“手机号登录”

推荐参照：
- EmailLoginController.loginByEmail()
- PasswordLoginService.authenticate()

建议新增：
- /login/phone API
- PhoneLoginRequest DTO
- AuthService.loginByPhone()
- UserMapper.selectByPhone()

风险检查：
- users.phone 唯一性、老用户空手机号、登录失败次数限制、后台手机号修改联动
```

### 13.7 展示限制

展示限制：

- 默认不展示全量大图，只展示当前查询相关路径。
- 默认最多 50 个节点、6 跳路径；超限返回 `truncated=true`。
- 每个结果都必须能展开 evidence。
- AI 摘要旁必须显示“基于 N 条静态分析证据生成”。
- AI 关闭时仍显示结构化结果、路径和证据。

### 13.8 Saved Query、订阅与协作

查询和报告可以被保存、订阅和协作，但这些能力属于产品工作流层，不改变底层 facts。

Saved Query：

- 用户可以保存 symbol search、impact query、DB impact query、variable impact query、Feature Planner query。
- `SavedQuery` 必须保存 query schema、project scope、datasource scope、默认 snapshot policy、maxDepth/limit、是否包含 AI candidate。
- 保存的查询默认在最新 committed snapshot 上重跑；如果用户要求复现历史结果，必须 pin 到历史 `snapshotId`。
- Saved Query 结果变化时生成新的 report artifact，不覆盖历史报告。

Watch Subscription：

- 用户可以 watch 方法、入口、JSP、DB 表/字段、FeatureScope、SavedQuery 或风险规则。
- 新 snapshot 发布后，如果 watched 对象相关 facts、paths、riskLevel、policy status 或 report status 变化，则生成 notification candidate。
- 通知默认只包含摘要、risk level、report link 和 artifact id，不直接发送源码 snippet。
- 订阅必须遵守 project allow-list 和 datasource boundary；用户失去权限后停止通知。

协作评论：

- 用户可以对报告、路径、证据、候选项、不确定项、PolicyViolation 添加 `ReviewThread/ReviewComment`。
- 评论只表达人工讨论和决策，不提升或降低 fact confidence。
- 评论必须绑定 `snapshotId/reportArtifactId/evidenceKey/pathId`；底层 facts 变化后显示 stale 状态。
- 支持 `ACKNOWLEDGED/NEEDS_ACTION/RESOLVED` 等轻量状态，但不自动修改代码或图谱 facts。

导出：

- 报告可导出为 JSON、Markdown、HTML、CSV；CI/PR 场景可导出精简 summary。
- 导出必须使用同一 redaction policy，不能绕过 UI/REST/MCP 的脱敏和权限。
- `ExportArtifact` 记录 format、createdBy、sourceReportArtifactId、snapshotId、redactionApplied 和 expiry。

### 13.9 架构健康指标

架构健康指标是派生分析报告，不是新的底层事实来源。它消费 committed graph facts、report artifacts、历史查询统计和可选 GDS/APOC/JVM 图算法结果，输出 `ArchitectureHealthReport`、`ArchitectureMetric` 和 `HotspotCandidate`。

首批指标：

- 结构指标：module/package fan-in/fan-out、cycle candidate、layer violation、orphan entrypoint、公共 util/service/DAO 热点。
- 影响指标：高 fanout 方法、DB table/column 高影响范围、JSP/HTML/JS 到后端路径密度、batch/shell/message 入口覆盖度。
- 风险指标：反射候选数量、动态 SQL unresolved rate、native/C/COBOL/external boundary 数量、fallback-only scope、classpath 缺失 scope。
- 可维护性指标：变更热点、历史报告中反复出现的高风险节点、测试覆盖缺口候选、过期 SavedQuery/FeatureScope。

约束：

- 每个 `ArchitectureHealthReport` 必须绑定 `projectKey`、`moduleKey?`、`snapshotId`、`cacheBuildId?`、`metricVersion`、输入 relation groups 和 cost budget。
- 指标只影响排序、风险提示和 Feature Planner 的建议优先级，不得提升 `pathConfidence`，也不得替代 evidence path。
- GDS/APOC 可加速 PageRank、centrality、community detection、cycle/path sampling；没有插件时必须有 JVM/Cypher fallback。
- 指标结果必须标记 `staleAgainstSnapshot`；底层 snapshot 更新后，旧 report 只能用于历史对比，不能作为当前影响分析依据。
- UI 展示必须把“健康指标”和“影响路径”分开，避免用户把高中心性误解成确定变更影响。

## 14. 运行平台、构建与可视化前端

### 14.1 运行平台

CodeAtlas 自身运行平台和工程基线：

- 后端和分析 worker 使用 Java 25 LTS。
- 构建系统使用 Gradle，采用 multi-project 结构。
- 使用 Gradle Java Toolchains 固定 Java 25 编译/运行基线。
- CodeAtlas 自身 REST server 使用 Spring Boot + Spring MVC，统一承载 `/api/v1` REST 契约、结构化错误、validation、CORS、审计和后续 async job API；不得使用临时自定义 HTTP server 作为正式服务栈。
- 被分析项目不受 CodeAtlas 运行 JDK 限制，首批需要支持 Java 6/7/8 风格老项目以及现代 Java 项目。
- 后端服务、分析 worker、MCP server、前端应用在 Gradle 中分模块管理。

配置与硬编码约束：

- 生产代码不得硬编码 workspace path、projectKey、moduleKey、端口、URL、数据库名、schema/table/column、datasource、框架 profile、analyzer 阈值、timeout、path depth、allow-list、模型名、token budget、cache size、Neo4j procedure 或外部命令路径。
- 默认值必须来自配置对象、环境变量、project settings、workspace metadata、SymbolKind Registry、Relation Registry、Analyzer Capability Descriptor、benchmark profile 或安全策略。
- 测试 fixture 可以在 `test/resources` 或 fixture builder 中使用样例值，但不能把样例值泄漏成生产默认。
- 如果某个实现确实需要临时常量或 fallback 默认值，必须先经过用户确认，并在代码中隔离为可配置项，附带注释说明原因、风险和移除条件。
- 文档、样例和测试中的字符串只表达契约示例；实现时不得直接复制为隐式规则。

### 14.2 建议工程模块

建议工程模块：

```text
codeatlas-symbols
codeatlas-facts
codeatlas-analyzer-api
codeatlas-import
codeatlas-fact-pipeline
codeatlas-storage-core
codeatlas-storage-memory
codeatlas-storage-neo4j
codeatlas-navigation
codeatlas-evidence
codeatlas-query
codeatlas-metrics
codeatlas-planning
codeatlas-api
codeatlas-security
codeatlas-observability
codeatlas-workflow
codeatlas-integrations
codeatlas-server
codeatlas-worker
codeatlas-analyzers
codeatlas-ai
codeatlas-mcp
codeatlas-ui
```

### 14.3 模块边界

模块边界：

- `codeatlas-symbols`：`symbolId/flowId/artifactId` parser、normalizer、SymbolKind Registry。
- `codeatlas-facts`：FactRecord、Evidence、confidence、priority、relation schema 和 staging DTO。
- `codeatlas-analyzer-api`：analyzer worker 输入输出、ScopeRun、diagnostics、capability contract。
- `codeatlas-import`：workspace profiler、import review、file capability、project boundary。
- `codeatlas-fact-pipeline`：fact/evidence builder、staging 校验、commit coordinator、tombstone ownership。
- `codeatlas-storage-core`：FactStore SPI、active view query contract、snapshot/tombstone contract、store sizing 和 shared test kit。
- `codeatlas-storage-memory`：InMemoryFactStore，服务 dev/CI、小 fixture、本地单用户试用和 query contract 回归。
- `codeatlas-storage-neo4j`：Neo4j schema、writer、active view query、transaction/retry，作为生产默认 FactStore 实现。
- `codeatlas-navigation`：CodeNavigationIndex、JDT/JDT LS adapter、LSIF-like import/export、definition/reference/hover/document symbol query。
- `codeatlas-evidence`：snippet store、脱敏、evidence pack builder、vector chunk source。
- `codeatlas-query`：impact、DB、variable、JSP flow 查询和 path scoring。
- `codeatlas-metrics`：架构健康指标、热点候选、循环依赖候选、动态风险统计和 metric report artifact。
- `codeatlas-planning`：Feature Planner、ChangePlanReport、RegressionSuggestion artifact。
- `codeatlas-api`：REST DTO、OpenAPI schema、分页、错误模型、异步 job API 和 API versioning。
- `codeatlas-security`：principal、project allow-list、datasource boundary、redaction policy、audit event model。
- `codeatlas-observability`：metrics、structured logs、trace spans、health checks、benchmark result sink。
- `codeatlas-workflow`：SavedQuery、WatchSubscription、NotificationRule、ReviewThread、PolicyRule、RiskWaiver、ExportArtifact。
- `codeatlas-integrations`：Git provider webhook、PR/CI check adapter、notification channel adapter、report export adapter。
- `codeatlas-analyzers`：Spoon/Jasper/Web Client Analyzer/JSqlParser/ASM/ClassGraph/shell/framework adapters。

依赖方向必须保持单向：

```text
symbols -> facts -> analyzer-api
symbols/facts -> storage-core -> storage-memory/storage-neo4j
symbols/facts/evidence/navigation -> query -> planning
symbols/facts/evidence/query -> metrics
api/server/mcp/ai/evidence -> security
import -> analyzer-api -> analyzers
server -> api/import/query/metrics/planning/ai/mcp/security/observability
workflow -> query/planning/security/observability
integrations -> api/workflow/security/observability
worker/fact-pipeline/query -> observability
worker -> analyzer-api/analyzers/fact-pipeline
ui -> server REST/MCP facade
```

`codeatlas-analyzers` 不应直接依赖 `codeatlas-server` 或 `codeatlas-ui`；`codeatlas-ai` 不应直接写 `storage-core` 或任何存储后端；`codeatlas-query` 不应调用 analyzer worker 重新分析，只能读取 committed facts、缓存、navigation index 和 evidence；`codeatlas-metrics` 不应写底层 facts，只能输出 metric/report artifact。`codeatlas-integrations` 只能通过 API/workflow 调用能力，不能直接访问 analyzer、FactStore backend、Evidence Store 或工作区文件。

### 14.4 可视化前端

可视化前端是一等产品能力，不只是报告展示。首版前端建议使用 React + TypeScript + Vite，主组件库使用 Semi Design；复杂表格优先 TanStack Table，需要更强内置 grid 能力时评估 AG Grid Community；图谱展示使用 Cytoscape.js，流程路径展示使用 React Flow，代码和证据片段展示使用 Monaco Editor 或 CodeMirror，图标使用 lucide-react。

默认项目首页采用“Project Dashboard + Change Workbench 组合页”。Dashboard 是概览区域，展示项目状态、覆盖能力、入口点、最近报告、盲区和推荐下一步；Change Workbench 是首页主操作区，优先回答“我要按这个需求/问题改代码，应该从哪里开始、改哪里、影响哪里、怎么测”，通过统一输入和快捷任务把用户带到需求探索、修改计划、影响报告、测试计划、DB 影响、变量追踪、JSP/Web Client 链路或相似实现检索。图谱探索是下钻能力，不作为默认第一屏。

Change Workbench 布局：

- 左侧任务导航：常用任务、最近报告、保存查询。
- 中央工作区：需求/bug/设计片段输入框、快捷任务按钮、探索结果摘要、修改计划、测试计划、下一步动作和主要路径预览。
- 右侧上下文面板：证据摘要、分析覆盖、盲区提醒、当前 snapshot、AI 状态。

统一输入支持需求、bug 描述、设计书片段、页面/字段/菜单名、DB 表/字段、JSP/HTML 页面、Java symbol、变量名和自然语言功能描述。Git diff 或本地变更默认作为关系刷新和修改后验证输入；只有用户显式选择“分析这次变更影响”时才生成 diff 影响报告。首批快捷任务包括粘贴需求探索、规划功能修改、新增功能落点、生成测试计划/测试数据、修改后验证、刷新关系库、查 DB 影响、查变量流向、查 JSP/Web Client 链路和找相似实现。

首版前端视图：

- Import Review：导入后展示 workspace profile、候选项目、能力覆盖、盲区、secret/config 风险、建议 scope 和需要用户确认的 `AnalysisScopeDecision`；存在 `PARTIAL/BOUNDARY_ONLY/UNSUPPORTED/BROKEN/UNKNOWN` 时必须先处理该视图，不能直接进入确定性报告。
- Project Dashboard：项目概览、扫描状态、模块数量、风险热区；作为默认首页的概览区域，与 Change Workbench 同屏展示。
- Change Workbench：需求/bug/设计片段输入、快捷任务、最近报告、探索摘要、修改计划、测试计划、下一步动作、证据/覆盖/盲区面板。
- Requirement Exploration View：展示 `RequirementInput`、`ChangeIntent`、`ExplorationPlan`、候选入口、候选代码、SQL/DB/页面/测试线索、OpenQuestions 和证据。
- Modification Plan View：展示必须修改、建议检查、可能相关、无需修改但回归、无法判断需确认、风险残留和建议改动顺序。
- Test Plan View：展示测试入口、测试场景、测试数据、断言、前置数据、回归范围和人工准备项。
- Post-change Validation View：用户修改后基于新 snapshot 对照原计划展示覆盖项、遗漏项、新增影响和测试计划更新。
- Impact Report：显式变更影响查询或 PR/CI 增强场景下的影响报告、风险等级、建议测试、AI 摘要。
- Graph Explorer：Neo4j 路径查询结果可视化，支持 caller/callee、入口到 SQL 链路。
- Variable Impact View：变量来源、流向、受影响代码、JSP/HTML input、JS payload key、request parameter、SQL 参数和 DB 字段路径。
- Web Client Flow View：HTML/JSP 表单、输入、链接、JS 请求、DOM event 到后端 endpoint/action 的路径和动态边界。
- DB Impact View：表/字段到 SQL、Mapper/DAO、Service、入口、页面和 batch/job 的反向影响。
- SQL/Table Path View：从入口、方法、Mapper 或 SQL 出发查看到 SQL/table/column 的路径下钻；它是路径探索视图，不替代 DB Impact 的 read/write/display/test 分组报告。
- Feature Planning View：已有功能修改和新增功能落点规划，展示必须关注、建议检查、可能相关、建议新增、建议复用和建议测试。
- Architecture Health View：展示 module/package/class/method 热点、循环依赖候选、fanout、动态风险、边界节点和历史风险趋势。
- JSP Flow View：JSP -> Action/Controller -> Service -> DAO/Mapper -> SQL/table。
- Symbol Search：类、方法、JSP、SQL、表字段统一搜索。
- AI Q&A：自然语言查询，回答必须展示证据路径。
- Candidate Picker：搜索词存在多个候选时，先选择符号再执行追踪。
- Evidence Panel：展示路径对应源码、JSP、XML、SQL 证据。
- Saved Queries：保存常用影响查询、DB 查询、变量查询和功能规划查询。
- Watch & Notifications：订阅符号、DB 字段、FeatureScope、风险规则或 SavedQuery 变化。
- Review Workspace：围绕报告路径、证据、不确定项和 policy violation 进行评论、确认和关闭。
- CI/Policy Dashboard：查看 PR check、policy violation、waiver、pending deep report 和历史趋势。

前端交互原则：

- 图谱视图默认展示关键路径，不一次性铺满全项目大图。
- 首页优先展示需求探索、修改计划、测试计划和摘要，不以图谱或 raw SymbolId 作为默认第一屏。
- 项目未完成 Import Review 或 scope 决策已 stale 时，首页必须显示覆盖和盲区警告，并把确定性查询降级为 partial/candidate。
- 每条边展示 edge type、confidence、sourceType 和 evidence。
- AI 摘要旁边必须展示可点击证据。
- 大图查询默认限制深度和节点数，必要时提示用户继续展开。

### 14.5 REST API 契约

REST 是 CodeAtlas 默认对外接口，UI、企业自动化、普通系统集成和内部服务都应优先使用 REST。MCP 是 REST/Query/Planning 能力的可选协议 facade，不是另一套业务实现。

REST API 分组：

```text
/api/v1/workspaces
/api/v1/projects
/api/v1/import-reviews
/api/v1/analysis-runs
/api/v1/snapshots
/api/v1/symbols
/api/v1/impact
/api/v1/variables
/api/v1/db-impact
/api/v1/features
/api/v1/architecture-health
/api/v1/reports
/api/v1/evidence
/api/v1/saved-queries
/api/v1/subscriptions
/api/v1/review-threads
/api/v1/policies
/api/v1/ci-checks
/api/v1/exports
/api/v1/admin
```

REST 规则：

- API 必须版本化，首版使用 `/api/v1`；breaking change 进入 `/api/v2`，不能静默改变字段语义。
- 查询类 API 默认只读，必须 pin 到 committed `snapshotId`；未传 snapshot 时使用当前 latest committed snapshot，并在响应里返回实际 `snapshotId`。
- 长任务使用 async job：提交请求返回 `jobId/reportArtifactId`，前端轮询或订阅状态；不得让 HTTP 请求承担重型 analyzer 生命周期。
- 所有列表接口必须有分页、排序和最大 limit；默认 limit 保守，超限返回结构化错误。
- 所有报告类响应复用第 13 节 `AnalysisResult` schema；REST 和 MCP 不得对同一结果定义两套字段。
- 错误响应必须结构化，至少包含 `requestId`、`code`、`message`、`details?`、`retryable`、`status`。
- 写入类管理操作，例如删除项目、清理 workspace、重建索引、触发深度分析，必须使用幂等 key 或明确的 `confirm=true` 参数，并写审计日志。

### 14.6 权限、安全与数据隔离

安全边界必须覆盖 UI、REST、MCP、Agent、AI、worker 和 Evidence Store，不能只在 MCP 层处理。

主体和权限：

| 概念 | 说明 |
| --- | --- |
| `principal` | 当前用户、系统任务或服务账号 |
| `workspaceRole` | `OWNER/MAINTAINER/VIEWER` 等 workspace 级角色 |
| `projectAllowList` | 用户可访问的 project 集合 |
| `datasourceBoundary` | 用户可访问或可展示的 datasource/schema/table 范围 |
| `operationPolicy` | 是否允许导入、分析、查询、导出、删除、重建缓存、调用 AI |

安全规则：

- 所有 UI/REST/MCP/Agent 请求在进入 query/planning/report 前必须完成 project 权限检查。
- 导入阶段必须先执行 secret/config 敏感项分类，至少识别 password、token、secret、key、credential、JDBC URL、云访问密钥、证书/私钥、`.env` 和常见个人信息字段。分类结果进入 evidence metadata 和 redaction policy，不得作为普通源码片段直接进入 AI、vector index、外部导出或通知。
- Evidence snippet 默认脱敏，移除 key、token、password、连接串、个人信息样式字段；脱敏失败时不返回 snippet，只返回 evidence metadata。
- AI evidence pack 必须使用同一套 redaction policy 和 project allow-list，禁止跨 project/datasource 合并上下文。
- 上传归档必须做路径穿越、压缩炸弹、超大文件、非法路径、编码异常和加密文件检测。
- 默认不执行被分析项目内的构建、测试、shell 或业务代码；需要执行时必须进入单独沙箱、超时、只读挂载和用户确认。
- 审计日志必须记录导入、用户确认、查询、报告导出、AI 调用、MCP 调用、删除和管理操作。
- 审计日志不得保存完整源码片段、密钥、token、连接串或大段 prompt。

### 14.7 可观测性与运维

CodeAtlas 需要从第一版开始记录能解释“为什么慢、为什么漏、为什么结果不同”的运行数据。

指标分类：

| 类别 | 指标 |
| --- | --- |
| 导入 | 文件数、大小、L1-L5 分布、unsupported/broken 数量、profile 耗时 |
| 分析 | analyzer 耗时、ScopeRun 成功/失败数、staging rejected 数、diagnostic 分类 |
| 提交 | fact count、evidence count、batch size、deadlock retry、commit duration、rollback count |
| 查询 | P50/P95/P99、cache hit ratio、expanded nodes/edges、truncated count、hot node count |
| 缓存 | cacheBuildId、edge count、heap bytes、build duration、failure reason |
| AI/Agent | evidence pack size、AI latency、validation failure、agent step latency、partial/failed count |
| 安全 | forbidden count、redaction count、audit event count、export count |

日志和 trace：

- 每个用户请求、Agent run、AnalysisRun、ScopeRun、Commit、CacheBuild、AI call 都必须有 `requestId` 或 `runId`。
- worker 日志必须结构化，包含 analyzerId、scopeKey、relationFamily、snapshotId、duration、status。
- 查询 trace 必须能解释 truncation：`TIMEOUT/DEPTH/FANOUT/HOT_NODE/CACHE_MISS/STALE_SCOPE`。
- 生产环境必须提供 health check：server、worker queue、Neo4j、Evidence Store、cache build、AI provider、MCP server。

### 14.8 数据保留、删除与恢复

CodeAtlas 会保存源码片段、证据、报告、向量 chunk 和图谱事实，因此需要清晰的数据生命周期。

数据类型：

| 数据 | 默认策略 |
| --- | --- |
| Managed workspace 源文件 | 按 workspace/project 保留，可配置只读引用或受控复制 |
| Evidence snippet | 脱敏后保存，受 retention policy 控制 |
| FactRecord / Materialized Edge | 随 snapshot 生命周期保留，历史 snapshot 可配置压缩或归档 |
| Report Artifact | 用户保存的报告保留；临时报告可按 TTL 清理 |
| Vector chunk | 从 evidence/report 派生，随 tombstone/stale/权限变化删除或失效 |
| JVM/FFM cache | 可重建，不纳入长期备份要求 |
| Audit log | 按合规要求保留，不能包含完整源码或敏感 token |

删除和恢复规则：

- 删除 project/workspace 必须同时清理 managed files、facts、evidence、reports、vector chunks 和派生缓存；审计日志保留元数据。
- 删除不能只 tombstone active facts，还必须处理 Evidence Store 和 vector index，避免旧源码片段被召回。
- Neo4j facts、Evidence Store 和 report artifacts 的备份必须使用同一 snapshot watermark，避免恢复后 evidenceKey 指向不存在片段。
- Derived indexes 不需要备份，恢复后从 committed facts 重建。
- 用户请求历史报告时，系统必须标注其 `snapshotId`、`staleAgainstSnapshot` 和可用 evidence 状态。

### 14.9 PR/CI 集成与规则门禁

PR/CI 集成是可选产品能力，目标是把 CodeAtlas 的影响报告带入代码评审流程。它不替代 CI 构建，也不直接改代码。

PR/CI 流程：

```text
Git provider webhook / CI job
  -> CodeAtlas impact.analyzeDiff
  -> fast ImpactReport
  -> Policy evaluation
  -> CiCheckRun
  -> optional deep supplement
  -> update report link / check summary
```

集成规则：

- Git provider adapter 只接收 PR metadata、commit range、diff 和 repository identity；不得直接信任外部传入的 projectId。
- CI check 输出必须引用 `reportArtifactId` 和 `snapshotId`，不能只输出自然语言。
- 初版 fast report 可先返回 `PENDING_DEEP`，CI check 可配置为 `informational` 或按 policy 决定是否阻断。
- 深度补充完成后可更新 `CiCheckRun` 状态，但不能修改历史 fast report artifact。
- 外部 PR 评论默认只发摘要和链接，不粘贴大段源码或完整 evidence snippet。

Policy Rule：

- `PolicyRule` 消费 report artifact、riskLevel、pathConfidence、truncated、pendingScopes、changed files、impacted DB columns、test suggestions 和 user scope。
- policy 可以产生 `PolicyViolation`，但不能改变底层 facts 或 confidence。
- 典型规则：
  - 高风险 DB 写影响但没有测试建议时标记 warning。
  - 影响受保护表/字段时要求人工确认。
  - 报告 `TRUNCATED` 或 `PARTIAL_BINDING` 时禁止宣称低风险。
  - 只有 AI candidate 支撑的路径不得作为阻断性 violation。
  - 命中 native/shell/async boundary 时要求 review acknowledgement。
- 规则必须版本化，`PolicyViolation` 记录 rule version、input report artifact、decision、severity、evidence keys 和 createdAt。

Risk Waiver：

- 用户可以对 `PolicyViolation` 创建 `RiskWaiver`，但必须填写 reason、owner、expiry、scope 和关联 report/evidence。
- waiver 只压制 policy gate，不删除 violation，不修改底层事实。
- waiver 过期、scope 不匹配或底层 impacted path 变化时自动失效。

### 14.10 通知与外部导出

Notification 和 export 只传播报告结果，不传播未授权源码。

通知渠道：

- MVP 可先实现 in-app notification。
- Email、Slack、Teams、Webhook 等外部渠道作为 adapter 后续接入。
- 通知内容默认包含 project、riskLevel、summary、report link、changed scope、policy status 和 stale/pending 状态。
- 通知不得包含未脱敏源码片段、secret、连接串或跨 project 内容。

导出格式：

| 格式 | 用途 | 约束 |
| --- | --- | --- |
| JSON | API、CI、回放测试 | 保留完整结构化字段 |
| Markdown | PR 评论、人工评审 | 只输出摘要、路径和 evidence link |
| HTML | 离线报告 | 必须脱敏，标注 snapshotId 和 stale 状态 |
| CSV | 清单导出 | 只导出 impacted items，不导出源码片段 |

所有外部通知和导出都必须写审计日志，并记录 redaction policy version。

## 15. 验收目标

### 15.1 MVP 降级矩阵

所有声明必须绑定以下四级状态之一。未在 `supported` 列中的能力不得作为 MVP 验收结论。

| 能力域 | supported（确定性结果） | degraded（降级但可用） | candidate-only（仅候选） | unsupported（不做） |
|--------|----------------------|---------------------|----------------------|-------------------|
| **Java 调用图** | direct call（Spoon）、CALLS/INVOKES 边、class/method/field DECLARES | no-classpath fallback 时 confidence→LIKELY | 反射调用、bridge method 消歧 | 全程序指针分析调用图 |
| **Spring/Struts** | RequestMapping、Struts action mapping、ROUTES_TO 边 | 多 profile、XML 配置缺失时降级 | Seasar2 dicon（POSSIBLE 候选） | Spring Boot auto-config 推断 |
| **JSP** | 静态 tag/form/input/include 提取、Jasper 单 profile 解析 | Jasper 不可用时 token fallback | taglib 自定义 tag 内部逻辑 | 多容器 profile 精确解析、Tiles 完整布局 |
| **HTML/JS** | 静态 form action、anchor href、fetch/XHR/axios 字面量 URL | JS 字面量提取（不追踪变量拼接） | 动态 URL、框架 router、回调链 | bundled/minified JS 语义分析 |
| **MyBatis** | mapper interface→XML statement 匹配、静态 SQL table/column 提取 | dynamic SQL conservative fallback（标记保守） | OGNL 条件语义、foreach 全展开 | #{param}→Java 变量全自动绑定 |
| **SQL** | JSqlParser 成功解析的 table/column | SQL 解析失败时 regex fallback | 存储过程、DDL 影响 | 多方言完整语义 |
| **变量追踪** | 直接参数透传、getter/setter 链、显式 SQL binding | 跨方法标记 PASSES_PARAM（含降级） | collection flows、DTO 框架转换 | sqlResultFlows（SQL→DTO→JSP 反向） |
| **DB 影响** | table read/write、column read/write、绑定的 SQL statement | column 级未解析时降级到 table 级 | 存储过程参数→列映射 | 跨 DB 实例事务影响 |
| **Native 边界** | native method 识别、System.load/loadLibrary、边界标记 | — | JNI 内部参数映射 | C/C++/COBOL 内部调用图 |
| **Git diff** | JGit diff→changed file→class/method/JSP/SQL/table 符号 | 删除文件降级标记 | 重命名/移动文件消歧 | merge commit 自动 parent 选择 |
| **影响报告** | caller+downstream BFS、affected symbols、DB impact、truncation 标记 | 路径截断/partial/pending 状态 | AI 汇总的风险解释 | AI 生成的确定性事实 |
| **Feature Planner** | 基于 Impact Query + symbol search + 历史报告 + AI 摘要生成 ChangePlanReport | — | 功能边界推断 | 新分析能力（不写 CALLS/BINDS_TO） |
| **外部侧车** | — | — | Joern/Tabby（depth CPG/slicing） | WALA/SootUp/Tai-e/Doop/FFM（不进 MVP） |
| **存储** | FactStore active view、snapshot/tombstone、InMemoryFactStore（dev/CI/小项目验收）和 Neo4j（生产验收） | 50万 facts 告警推荐切换；缓存失败回退 FactStore | — | 用派生缓存/FFM/vector 作为事实源 |
| **API/MCP** | REST /api/v1、分页/错误/审计、MCP tool schema 复用 REST/result schema | MCP 只读 tools 可选开放；部分 endpoint 返回 stub | — | 写操作 MCP tools；把 MCP 当作新的分析语义 |
| **AI** | evidence-backed answer draft、影响摘要、风险解释、测试建议 | AI 不可用时 static fallback | AI 生成的候选关系 | AI 写入确定事实 |

### 15.2 降级标记在报告中的展示

每种降级能力在报告中必须携带以下标记：

- `analysisBoundary`：标注分析停止原因（NATIVE、CLIENT_JS_DYNAMIC、METHOD_SUMMARY_LIMITED、SHELL_DYNAMIC 等）。
- `confidence`：强制降级（CERTAIN→LIKELY→POSSIBLE），不得在降级场景下声明 CERTAIN。
- `coverage`：每个 scope 的 covered/partial/blind 比例。
- `requiresManualReview`：native、反射、动态 SQL 等不可自动确认为 true。

### 15.3 10-30 秒 SLA 延迟预算

SLA 仅在绑定 benchmark profile 时生效。各阶段目标 P95：

| 阶段 | 目标 P95 | 前置条件 |
|------|---------|---------|
| changed-scope analysis (Spoon/Jasper/JSqlParser) | 5s | 变更文件 ≤ 20 个 |
| SQL/JSP parse & fact extraction | 3s | 已有 snapshot cache 命中 |
| validate & atomic write | 2s | 事实数 ≤ 10,000 |
| cache rebuild | 3s | JVM primitive adjacency cache |
| FactStore / traversal query（生产 profile 为 Neo4j） | 5s | BFS depth ≤ 5, max paths ≤ 100 |
| report render | 2s | Markdown/JSON 输出 |
| **总计** | **20s** | |

超预算时返回 `truncated/pending` 状态，不伪装完整报告。未绑定 benchmark profile 时不得引用 SLA 数字。

### 15.4 MVP 核心链路验收

MVP 必须打通的确定性链路（全部为 supported 级别）：

- JSP/HTML form → Action/Controller → Service → DAO/Mapper → SQL/table
- Repository refresh → changed scope detection → incremental facts commit → latest active snapshot
- DB table/column → SQL statement → Mapper/DAO → Service → entrypoint（区分 read/write/display）
- 变量/参数 → getter/setter 链 → SQL 参数
- 显式 diff impact query → FastImpactReport（含 changed/affected symbols、paths、DB impacts、truncation）；该能力消费最新 snapshot，不作为每次 Git 提交的默认产物
- 能用 AI 生成简洁风险摘要和测试建议。
- 能通过 REST 对外提供只读分析能力；MCP 作为可选集成面，在安全门禁完成后开放给 IDE/Agent。
- 能通过可视化前端查看影响报告、调用路径、变量影响、DB 影响、功能修改/新增规划、JSP/Web Client 链路和入口到 SQL/table 链路。
- Joern、Tabby、WALA、SootUp/Heros、Tai-e、Doop、Fraunhofer CPG POC、FFM 不作为 MVP 验收前置条件。

以下能力属于 MVP 展示/工作流增强，不阻塞核心分析闭环验收；只有被对应 milestone 明确纳入时才作为发布门禁：

- 基础 `ArchitectureHealthReport`：展示热点节点、循环依赖候选、动态解析风险和边界风险；高级图算法不作为 MVP 前置。
- 保存查询、导出结构化报告、报告路径/证据协作评论。
- PR/CI、通知订阅、policy gate 和 waiver。
- MCP 对 IDE/Agent 开放；REST 是默认对外接口。

### 15.5 验收输出物

| 输出物 | 验收层级 | 验收标准 |
| --- | --- | --- |
| Import Review Report | core | 能列出项目、状态、能力覆盖、不可分析盲区和用户确认项 |
| Baseline Graph Snapshot | core | 能按 active view 查询 committed facts，未触碰 scope 不丢失 |
| Impact Report | core | 包含入口、路径、confidence、evidence、risk、pending/truncated 状态 |
| Variable Impact Report | core | 能展示来源、流向、SQL/DB/页面影响和边界 |
| DB Impact Report | core | 能区分 read/write/display/test impact |
| Web Client Flow Report | core | 能展示 HTML/JSP 控件、JS 请求、ClientRequest、后端 endpoint/action 和动态边界 |
| ChangePlanReport | core | 能区分必须关注、建议检查、可能相关、建议测试和不确定项 |
| REST Result | core | REST 返回结构化 schema，不返回不稳定自然语言文本作为唯一结果 |
| UI Evidence View | core | 每个结论能点击展开源码/JSP/HTML/JS/XML/SQL 证据 |
| ArchitectureHealthReport | enhancement | 能展示热点、循环依赖候选、动态风险和边界风险，且不提升底层事实置信度 |
| MCP Result | enhancement | MCP 复用 REST/result schema 作为可选对外集成面，不引入新分析语义 |
| Workflow Artifact | enhancement | SavedQuery、ReviewComment、ExportArtifact 能引用 report/snapshot/evidence，且不改变底层 facts |

### 15.6 不作为 MVP 完整承诺

- 不承诺对所有 Struts1 生态特性、Seasar2 自动绑定、vendor JSP 容器行为、复杂 JavaScript runtime 和动态 shell 进行确定性解析。
- 不承诺没有 classpath、缺源码、缺配置的项目能输出完整影响路径。
- 不承诺 AI 候选能自动成为确定修改范围。
- 不承诺快速报告覆盖深层变量别名、反射、多态和全程序数据流；这些属于深度层或侧车增强。
- 不承诺动态 SQL 穷举所有运行时分支；MVP 至少输出 `SqlVariant`、partial/candidate 状态和 unresolved boundary。
- 不承诺反射候选自动成为确定调用；默认需要非反射证据或人工确认才能提升。
- 不承诺 C/C++、COBOL、JCL、native library 或外部命令内部调用图；MVP 只做边界符号盘点和风险提示。
- 不承诺首版完整支持 Git provider 深度集成、外部通知渠道、复杂 policy DSL 或自动 PR 评论；这些是产品工作流增强。

### 15.7 质量与测试矩阵

MVP 验收不能只看单次 demo，需要建立可重复的 fixture、golden report 和性能 profile。

| 测试类型 | 目标 | 最小覆盖 |
| --- | --- | --- |
| Identity tests | `symbolId/flowId/artifactId` 可解析、归一化、无碰撞 | Java method、JSP、HTML/JS、SQL、DB、flow、artifact、SqlVariant、ReflectionCandidate、BoundarySymbol、alias merge |
| Analyzer fixture | 单 analyzer 输出稳定 facts/evidence | Spoon、Jasper、JSP token、HTML token、JS static、Struts XML、Spring、MyBatis dynamic SQL、SQL、reflection、boundary symbol、shell |
| Graph invariant tests | 图谱关系合法、方向正确、confidence 不越界 | relation allowed-by-kind、source/target existence、evidence completeness |
| Incremental tests | staging/commit/tombstone/active view 正确 | 未触碰 scope 沿用、scope 失败不污染、relationFamily tombstone |
| Query golden tests | 同一 fixture 输出稳定报告 | code impact、DB impact、variable impact、JSP flow、Web Client flow、feature planning |
| Security tests | 权限、脱敏、越权和禁止工具有效 | project allow-list、datasource boundary、snippet redaction、MCP/REST forbidden |
| Workflow tests | 产品工作流 artifact 不污染事实 | SavedQuery rerun、ReviewComment stale、PolicyViolation/RiskWaiver、Export redaction |
| AI/Agent replay tests | AI/Agent 输出可回放且不污染事实 | evidencePack replay、intent、candidate ranking、summary validation |
| Architecture metric tests | 架构健康指标可复现且不污染事实 | fanout、cycle candidate、hotspot、dynamic risk、staleAgainstSnapshot、GDS/APOC fallback |
| Performance tests | 快速报告和深度补充有 profile | 冷/热缓存、P95/P99、truncated/partial、cache hit ratio |
| Recovery tests | 失败后可回退 | commit failed、cache build failed、worker failed、Neo4j transient deadlock |

Golden report 必须保存结构化 JSON，而不是只保存自然语言摘要。自然语言可以变化，但 `paths[]`、`evidenceKeys[]`、`confidence`、`status`、`truncated`、`pendingScopes[]` 等字段必须稳定。

准确率验收必须绑定标注数据集。MVP 最低门槛：

- supported 链路的 path-level precision 不得低于 0.85；低于门槛的能力必须降级为 degraded 或 candidate-only。
- supported 链路的 recall 不得低于 0.70；无法覆盖的范围必须进入 blind/partial，不得展示为“未发现影响”。
- DB table-level impact precision 不得低于 0.90；column-level impact precision 不得低于 0.80，达不到时必须降级到 table-level。
- 用户可见的 `CERTAIN` 路径不得来自 AI-only、dynamic JS-only、reflection-only、dynamic SQL-only 或用户未确认的 candidate-only evidence。
- 每个能力域的 false positive、false negative 样例必须保存在 `EvaluationSampleSet`，并随 schema/analyzer 变更回归。

## 16. 开放增强与生产门禁

主设计应写在第 2 到 15 节；本节只保留尚未完成、会影响发布口径的门禁和可选增强。已进入主线的图谱模型、JSP/HTML/JS/Struts/Seasar2、增量语义、FFM、MCP、RAG 和 AI 规则不在这里重复。

### 16.1 生产门禁

以下能力在 MVP 阶段明确标记为 candidate-only 或 unsupported，不作为生产门禁阻塞项。前提是报告、UI、REST/MCP 和导出都能正确展示 degraded/candidate/unsupported、confidence、coverage、requiresManualReview 和 analysisBoundary；如果降级标记缺失或把候选展示为 supported，则必须阻塞发布：

- JSP 多容器解析：Jasper 单 profile 已可用；Resin/WebLogic/WebSphere 等 vendor profile 降级为 token fallback（candidate-only）。
- MyBatis Dynamic SQL OGNL：`<if>/<foreach>` 生成 conservative fallback table access；不承诺 OGNL 语义求值（candidate-only）。
- 动态 JS URL 拼接：只提取字面量；模板字面量、变量拼接、框架 router 标记 `CLIENT_JS_DYNAMIC`（degraded）。
- 跨方法 DTO 字段追踪：`collectionFlows`、`sqlResultFlows` 移出 MVP，进入 Tai-e 深度层（unsupported）。
- 字节码数据流：ProGuardCORE 只用于 class 结构读取，不用于方法摘要（unsupported）。
- 依赖失效图：classpath 变更 → 整个 project 标记 `STALE_SCOPE`；不实现传递闭包计算（unsupported）。
- Shell 脚本：只提取字面量 `java -jar/className`；变量展开/命令替换标记 `SHELL_DYNAMIC`（degraded）。

已进入主线的能力按 §15.1 降级矩阵分级验收。

生产硬化门禁（已完成原型，待硬化验证）：

- JSP SMAP 精确定位：`JasperSmapParser` 原型已完成，include/tagfile 多来源映射和解引用循环防扩仍需硬化。
- Web Client 精确定位：bundled/minified JS source map、前端框架组件事件不是 MVP 确定性承诺。
- 增量写入：原子写入已实现（`InMemoryFactStore`），生产级 Neo4j 事务写入和 rollback 待验证。
- 性能 SLA：10-30 秒仅在被 benchmark profile 绑定时生效（§15.3），profile 必须包含完整参数。
- MCP/Agent 安全：project allow-list、脱敏、审计日志、限流已完成原型；tool cost budget 待补齐。
- REST/权限：project allow-list、分页、结构化错误已完成；datasource 级权限待补齐。
- Schema 演进：identity/fact/evidence/report/cache schema version、兼容读取和 migration run 规则必须实现，避免升级后历史数据不可读。
- RAG/Vector：脱敏、project 权限、snapshot/stale 生命周期和召回 benchmark 完成前，不得作为生产事实来源。
- 产品工作流：PR/CI、通知、导出、评论、policy gate 和 waiver 必须只消费 report/facts，不得写入底层事实；外部通知和导出必须完成脱敏和审计。
- License gate：默认发行包中的运行时、前端和构建依赖必须完成 allow/deny/conditional review，并打包必要 notices。

### 16.2 架构规则与测试推荐

`ArchitectureRule` + `ArchitectureRuleChecker` 提供可编程架构规则检查：

- `ArchitectureRule.noLayerViolation(id, fromLayer, toLayer, description)` 检测直接跨层依赖（路径深度=1，即 2 节点直接边）。
- `ArchitectureRule.of(id, name, description, severity, predicate, remediation)` 支持自定义规则。
- `RuleSeverity`：ERROR、WARNING、INFO；`ArchitectureViolation` 包含 ruleId、severity、description、remediation、path。
- `ArchitectureRuleChecker` 对 `List<ImpactPath>` 批量检查，输出 `ArchitectureCheckResult`（按 ruleId 分组，含 violationCount 和 hasErrors）。

`TestRecommendationContext` 将历史风险、归属和变更频率纳入测试推荐排序：

- `TestRecommendationContext.of(riskScores, ownership, changeFrequency)` 构造上下文。
- `prioritizeTests(suggestedTests, paths)` 按路径上各 symbol 的 riskScore + changeCount 总和降序排列。

### 16.3 外部侧车策略

Joern/CodeQL/Tabby/WALA/SootUp/Tai-e/Doop **不进 MVP 关键路径**，也不进默认发行包：

- Joern：作为 depth CPG/slicing 候选侧车，独立 worker 运行，结果进入 candidate family。
- CodeQL、Tabby：作为安全/污点侧车，独立 worker 运行，结果进入 security report artifact 或 candidate family；不得覆盖业务影响主事实。
- Tai-e：`TaieAnalysisProfile` 定义配置骨架（analysisType、timeout、maxHeapBytes、SignatureMapping），但实际 worker 不入 MVP。
- WALA、SootUp/Heros、Doop：研究参考，不入仓库。

侧车启用条件：license review 通过 + benchmark profile 证明收益 + 独立 `-Xmx`/超时/失败隔离。

### 16.4 Feature Planner 定位

Feature Planner 是 planning/report artifact 层，**不声称具有新分析能力**：

- 消费：Impact Query results、历史报告、symbol/vector search、AI 摘要、用户输入。
- 产出：`ChangePlanReport` artifact（必须关注/建议检查/可能相关/建议测试/不确定项）。
- 不写入 `CALLS/BINDS_TO/MAPS_TO_COLUMN` 等 graph fact。
- 功能边界推断标记为 AI-assisted candidate，不进 active graph 确定路径。

### 16.5 可选增强
- FFM OffHeapGraphIndex：只在 Neo4j/JVM cache benchmark 证明瓶颈和收益后启用，且必须保持无损回退。
- APOC/GDS 增强：只作为路径遍历、图算法和架构健康指标加速；必须有 allow-list、审计、timeout、版本兼容和无插件 fallback。
- 架构健康高级分析：PageRank、community detection、复杂 cycle clustering、长期趋势和团队级 dashboard 作为增强；MVP 只要求基础热点和边界风险报告。
- Report Adapter 扩展：更多报表格式和 vendor 资源解析器按 adapter 加入，默认以 `POSSIBLE/LIKELY` evidence 逐步提升。
- PR/CI 集成：Git provider webhook、CI check、PR 评论和 policy gate 可按 adapter 增强，默认不作为核心分析闭环前置条件。
- 通知与协作：外部通知渠道、团队评论工作流、risk waiver 审批和 dashboard 趋势分析作为产品增强。
- OpenRewrite/迁移规则：只作为后续修复和迁移建议能力，不影响当前影响分析闭环。
- JavaScript AST：client-side ajax/fetch/navigation 的确定链路等待 JS parser/AST 集成，MVP 只保留候选或 boundary。

### 16.6 Benchmark Profile 框架

`BenchmarkProfile` + `BenchmarkRegistry` 提供命名 benchmark profile 和测量记录机制：

- `BenchmarkProfile.define(name, targetDescription, targetP95, targetMaxHeapBytes)` 定义一个 profile。
- `BenchmarkRegistry.defaults()` 预注册 `small-fixture`（5s P95, 256MB）和 `impact-report`（30s P95, 512MB）。
- `BenchmarkProfile.record(measurement)` 记录一次测量（P95、avg、heap、fact/edge/path count、environment），`meetsTarget()` 检查是否达标。
- 所有性能敏感变更（FFM、Tai-e、cache strategy）必须在 code path 中引用 named benchmark profile 并通过 target 验证。

`EvaluationSampleSet` 用于误报/漏报评估：

- `define(name, description, expectedPositivePaths, expectedNegativeScopes, optionalFalsePositiveSeeds)` 定义标注数据集。`expectedPositivePaths` 是可验收召回的 ground truth，`expectedNegativeScopes` 是不应报告影响的范围，`optionalFalsePositiveSeeds` 用于保留历史误报样例。
- `evaluate(reportedPaths)` 必须先按 scope 过滤只计算 in-scope 结果，再输出 `EvaluationResult(totalReported, truePositive, falsePositive, falseNegative, trueNegativeScopeCount, precision, recall)`。
- 如果没有 `expectedPositivePaths`，只能计算 smoke/golden 稳定性，不能声明 recall 达标。

### 16.7 Benchmark 靶机

- 小型样例：仓库内 Spring MVC、Struts1 + JSP、Seasar2、MyBatis/JDBC fixture。
- 中型项目：类似 RuoYi 的 Spring/MyBatis 项目，并补充至少一个 batch/main 或脚本启动入口样例。
- 遗留靶机：Struts1/JSP fixture，覆盖 ActionForm、DispatchAction、LookupDispatchAction、Tiles、Validator、plugin、JSP tag、JDBC。
- 指标：扫描耗时、图谱写入耗时、Neo4j 查询 P95/P99、JVM cache heap、路径报告耗时、误报/漏报样例、partial/truncated 降级次数。
