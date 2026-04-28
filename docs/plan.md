# CodeAtlas Plan

## 1. 总体路线

CodeAtlas 采用“先闭环、再加深、再增强”的路线。MVP 的目标不是覆盖所有高级静态分析能力，而是快速打通最小业务影响链路：

```text
Git diff
  -> 变更符号
  -> Spring/Struts/JSP/MyBatis/Java direct call 最小链路
  -> Neo4j + JVM 内存索引
  -> 影响路径报告
  -> AI/UI/MCP 展示与解释
```

总体目标：

```text
10 到 30 秒给出初版变更影响报告
先用 Neo4j + JVM primitive adjacency cache
Java 25 + Gradle 作为工程基线
提供可视化前端查看图谱、链路、报告和问答
Tai-e 和 FFM 放到 benchmark 驱动增强阶段
```

MVP 分期：

```text
MVP-0: 工程骨架、图谱 schema、symbolId 规范、evidence/confidence 契约
MVP-1: Java/Spring/Struts/JSP/MyBatis 最小链路，不做 Tai-e，不做 FFM
MVP-2: Git diff -> 变更符号 -> Neo4j/内存索引影响路径报告
MVP-3: 基础变量追踪和 SQL/table 影响
MVP-4a: 可视化前端和 REST 查询接口
MVP-4b: AI 摘要和 Code Graph RAG v1
MVP-4c: MCP 只读接口和受控 Agent
Enhancement: Tai-e 深度分析、FFM OffHeapGraphIndex、OpenRewrite 迁移增强
```

## 2. MVP-0: 工程骨架与核心契约

目标：

- 建立 Java 25 + Gradle multi-project 工程骨架。
- 建立 Neo4j 图谱 schema、统一 symbolId 规范。
- 明确 evidence、confidence、sourceType、snapshot、tombstone 和增量图谱语义。
- 建立可视化前端工程骨架。

交付物：

- 后端服务骨架。
- 分析 worker 骨架。
- React + TypeScript + Vite 前端骨架。
- Neo4j 连接、constraints、indexes、基础 upsert 能力。
- Symbol ID 生成器和归一化测试。
- Fact/evidence 数据契约。
- Snapshot/tombstone 增量写入契约。
- Gradle Java Toolchains 固定 Java 25。

验收：

- 工程能启动健康检查。
- 能连接 Neo4j。
- 能写入和查询一个 Project、Class、Method、JspPage、SqlStatement 示例节点。
- 同一 symbol 从不同 analyzer 输入能归一到同一 `symbolId`。
- 当前 snapshot 查询不会返回已 tombstone 的旧关系。
- 前端能启动空白项目页或健康页。

暂不做：

- 完整静态分析。
- Tai-e。
- FFM。
- AI 自动问答。
- UI 复杂图谱交互。

## 3. MVP-1: 最小业务链路

目标：

- 前置最小入口链路，确保影响报告有业务价值。
- 打通五类 MVP 必需边：Spring RequestMapping、Struts action、JSP form/action、Java direct call、MyBatis statement。

核心能力：

- Spoon 解析 Java 类、方法、字段、注解、direct method invocation。
- Spring 识别 `@Controller`、`@RestController`、`@RequestMapping`、`@Service`、`@Autowired`。
- Struts1 解析 `struts-config.xml`、Action、ActionForm、ActionForward。
- JSP 通过 WebAppContext + Jasper 解析 directive、taglib、EL、scriptlet、include、forward。
- JetHTMLParser/Jericho 容错提取 JSP form、input、select、textarea。
- MyBatis 解析 Mapper interface、XML namespace、statement id。
- JSqlParser 基础识别 SQL table 和读写类型。

必须写入的最小关系：

```text
ApiEndpoint -[:ROUTES_TO]-> Method
ActionPath -[:ROUTES_TO]-> Method
JspForm -[:SUBMITS_TO]-> ApiEndpoint/ActionPath
Method -[:CALLS]-> Method
Method -[:BINDS_TO]-> SqlStatement
SqlStatement -[:READS_TABLE|WRITES_TABLE]-> DbTable
```

交付物：

- Java source analyzer v1。
- Spring adapter v1。
- Struts1 adapter v1。
- WebAppContext + JSP analyzer v1。
- MyBatis/JSqlParser analyzer v1。
- Neo4j 写入和查询 API。
- Seasar2 dicon/component discovery v0，仅输出候选节点和 `POSSIBLE` 关系，不进入 MVP 报告验收。

验收：

- 能查询一个 JSP 提交到哪个 Action/Controller。
- 能查询一个 Action/Controller 被哪些 JSP/API 调用。
- 能展示 JSP/API -> Action/Controller -> Service -> Mapper -> SQL/table 的最短路径。
- 缺失 TLD/classpath 时，JSP 仍能通过容错解析输出 `POSSIBLE` 关系。

暂不做：

- Tai-e 指针分析。
- FFM。
- Seasar2 确定性影响链路。
- 复杂 JavaScript 动态 URL。
- 完整跨方法变量追踪。

## 4. MVP-2: Git Diff 影响路径报告

目标：

- 基于 Git diff 快速输出初版影响报告。
- 使用 Neo4j + JVM primitive adjacency cache，暂不上 FFM。

核心能力：

- JGit 读取 commit、branch、diff、changed files。
- changed file -> changed symbol 定位。
- method/JSP/XML/SQL/config 变更分类。

Gradle Tooling API 判断：

- MVP 先使用 `settings.gradle(.kts)` 的轻量 include 解析和标准 source root 探测。
- 暂不把 Gradle Tooling API 放入默认扫描路径，避免老项目插件执行、副作用、下载依赖和 daemon 兼容性拖慢首版扫描。
- 对复杂 Gradle 项目，增强阶段再以独立 worker、超时、只读模式接入 Tooling API，失败时回退轻量解析。
- 从变更 method 反查 caller。
- 从入口正查下游链路。
- 使用 JVM 内存邻接表缓存热点 caller/callee 边。
- 输出受影响 JSP、API、Action、Controller、Service、DAO、Mapper、SQL、table。
- 每条影响路径带 evidence、confidence、sourceType。

交付物：

- Impact Analysis Engine v1。
- JVM InMemoryGraphCache v1，包含分片、失效和 Neo4j 回退。
- PR Impact Report JSON。
- Impact query REST API v1。
- 基础影响报告页面和 Markdown 输出。

验收：

- 小型 PR 能在 10 到 30 秒生成初版影响报告。
- 报告包含影响入口、路径、风险等级和建议测试。
- 前端能展示影响入口、路径、证据和置信度。
- 删除或修改关系后，报告不会展示旧 snapshot 的残留边。
- AI 关闭时报告仍可生成。

暂不做：

- Deep taint。
- FFM。
- OpenRewrite 自动修复。
- 多服务链路。

## 5. MVP-3: 基础变量追踪与 SQL/table 影响

目标：

- 支持方法内和基础跨方法变量追踪。
- 打通 JSP input、request parameter、ActionForm、Service 参数、SQL 参数。

核心能力：

- 方法内 def-use。
- `request.getParameter` 来源追踪。
- JSP input name 到后端参数映射。
- ActionForm 字段绑定。
- getter/setter 简单传播。
- Controller/Action -> Service -> DAO/Mapper 参数传播。
- MyBatis SQL 参数绑定。
- SQL/table 影响查询。

交付物：

- Variable Trace Engine v1。
- SQL/table impact query。
- 变量来源/流向查询接口。
- Variable Trace View 前端页面。

验收：

- 能回答一个 request 参数从哪个 JSP 字段来。
- 能回答一个 JSP 字段最终流向哪些 Service/DAO/SQL。
- 能回答一个 SQL/table 被哪些入口影响。
- 能在报告中展示变量路径和 SQL/table 证据。

暂不做：

- 完整对象别名分析。
- 完整动态 SQL 解析。
- 复杂集合、反射和多线程传播。

## 6. MVP-4a: 可视化前端和 REST 查询接口

目标：

- 先把影响报告和图谱路径给人看清楚，再接 AI 和 MCP。

核心能力：

- Project Dashboard。
- Impact Report。
- Graph Explorer。
- Variable Trace View。
- JSP Flow View。
- Symbol Search。
- Candidate Picker。
- Evidence Panel。
- REST API：symbol search、impact report、path query、variable trace、jsp flow。

交付物：

- 可视化前端 v1。
- REST Query API v1。
- 报告 JSON schema。

验收：

- 前端能展示影响入口、路径、证据、置信度和截断提示。
- 查询结果按“答案摘要、证据路径、证据列表、图谱与明细”四层展示。
- 搜索词匹配多个符号时，先显示候选列表再执行追踪。
- Graph Explorer 默认限制深度和节点数。
- UI 展示的所有路径都能回溯到 Neo4j evidence。

暂不做：

- AI Q&A。
- MCP。
- Agent。

## 7. MVP-4b: AI 摘要和 Code Graph RAG v1

目标：

- 在事实图谱和影响报告闭环之后，补充智能解释。

核心能力：

- AI Provider 抽象和系统/项目级配置。
- 源码片段脱敏。
- AI 生成影响摘要、风险解释、测试建议。
- Code Graph RAG v1：精确符号检索 + Neo4j 图扩展 + 少量向量召回。
- AI Q&A。
- 意图识别支持 symbol search、caller/callee、变量来源/流向、影响分析、JSP 链路、SQL/table 影响。

交付物：

- AI Summary Generator。
- Code Graph RAG service v1。
- AI Q&A 页面。

验收：

- AI 摘要必须引用 evidence path。
- AI 关闭时所有报告和 UI 查询仍可工作。
- AI 回答不能脱离 evidence pack。
- AI 摘要旁显示“基于 N 条静态分析证据生成”。

暂不做：

- AI 直接判断最终影响范围。
- Agent。
- MCP。

## 8. MVP-4c: MCP 只读接口和受控 Agent

目标：

- 把已封装的查询能力开放给 IDE 和外部 AI 工具。

核心能力：

- 只读 MCP Server。
- ImpactAnalysisAgent、VariableTraceAgent、CodeQuestionAgent。
- Tool Registry。
- 工具调用审计、限流、脱敏。

交付物：

- MCP server v1。
- Agent Orchestrator v1。
- MCP tools/resources/prompts。

验收：

- 外部 MCP 客户端能查询调用方、影响路径、变量来源、JSP 链路。
- Agent 不能执行任意 Cypher 或任意文件读取。
- 所有 Agent 输出都包含 evidence、confidence、sourceType。

暂不做：

- 自动改代码。
- 无人工确认的写操作。

## 9. Enhancement-1: Tai-e 深度分析 Worker

进入条件：

- MVP 影响报告闭环稳定。
- Spoon direct call 和基础变量追踪无法满足多态/跨方法数据流需求。
- 已完成 Tai-e license review 和样例项目可行性验证。

目标：

- 引入 Tai-e 作为深度调用图和数据流分析 worker。

核心能力：

- 编译产物/classpath 准备。
- Tai-e 独立 worker JVM。
- 指针分析和 on-the-fly call graph。
- 多态调用候选补强。
- taint/source-sink 配置。
- Tai-e method signature 与 Spoon symbolId 映射。

验收：

- Tai-e 失败不影响 MVP 影响报告。
- 深度分析能补充多态调用和跨方法变量流。
- 输出结果能映射回源码文件和行号。

## 10. Enhancement-2: FFM OffHeapGraphIndex

进入条件：

- 单项目 active edge 数达到百万级以上，或 Neo4j/JVM cache P95 查询无法满足 SLA。
- 已有 heap 占用、P95 查询耗时、Neo4j 查询计划和 JVM cache benchmark。
- 证明 FFM 在目标数据规模上明显优于 JVM primitive adjacency cache。

目标：

- 降低 JVM heap 压力。
- 加速高频调用链和影响路径查询。

核心能力：

- 从 Neo4j 或分析结果导出调用图。
- 构建 CSR/CSC 压缩邻接表。
- 使用 JDK FFM `MemorySegment` 存储 primitive 图结构。
- 支持 caller/callee、多跳 BFS、visited bitmap、frontier queue。
- 支持 mmap 落盘和重载。

验收：

- 大图路径搜索不产生大量 Java 对象。
- JVM heap 占用稳定。
- 常见影响路径查询比纯 Neo4j 和 JVM cache 更快。

## 11. Enhancement-3: 规则、迁移和工程化增强

目标：

- 从影响分析平台扩展到架构治理和自动迁移建议。

核心能力：

- 架构规则：Controller 不能直接调用 DAO、跨层调用检查等。
- OpenRewrite recipe 生成和执行前预览。
- 测试推荐和覆盖率集成。
- 历史缺陷、owner、变更频率纳入风险评分。
- UI 图谱探索和报告归档增强。

验收：

- 能发现架构违规路径。
- 能生成迁移建议而不自动执行。
- 能把历史风险纳入 PR 报告。
