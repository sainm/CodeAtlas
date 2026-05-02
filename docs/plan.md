# CodeAtlas 计划

> 本文由 `docs/design.md` 生成。`docs/design.md` 是唯一事实源；本文是派生执行计划。

## 1. 路线图

CodeAtlas 采用“先闭环、再加深、再加固”的路线。MVP 首先要交付一条有实际业务价值、带证据、可解释的 Java 影响分析闭环；深度分析、离堆加速和自动修改类工作流都放在后续增强阶段。

```text
导入 / Git diff
  -> workspace 审查与分析规划
  -> analyzer worker
  -> fact/evidence staging
  -> committed active graph
  -> 影响 / 变量 / DB / 功能规划报告
  -> REST、UI、MCP、AI 解释
```

MVP 分期：

- MVP-0：工程基线、图谱模型、SymbolId、fact/evidence、snapshot 语义。
- MVP-1：导入审查和最小 Java Web 分析链路。
- MVP-2：committed graph、增量分析、Git diff 影响报告。
- MVP-3：变量追踪和 SQL/table 影响。
- MVP-4a：REST 查询契约和可视化前端。
- MVP-4b：AI 摘要和 Code Graph RAG v1。
- MVP-4c：只读 MCP 和受控 Agent。
- 生产加固：生产级写入语义、benchmark 靶机、报表适配器、native 边界、字段级数据影响。
- 增强阶段：Tai-e 深度 worker、FFM OffHeapGraphIndex、架构治理和迁移辅助。

## 2. MVP-0：基线和核心契约

目标：

- 建立 Java 25 + Gradle multi-project 工程基线。
- 定义 graph schema、SymbolId、confidence、sourceType、evidence、snapshot、tombstone 和 active view 语义。
- 明确 AI、cache、vector index 和 FFM 都是 committed facts 之上的派生层。

交付物：

- graph、analyzers、worker、server、UI、AI、MCP 等 Gradle 模块。
- Neo4j schema 契约、constraints 和 indexes。
- SymbolId parser、normalizer、registry、alias、provisional 和 validation。
- FactRecord、Evidence、Materialized Edge、snapshot、analysis run、scope run、staging、commit、rollback、active view、tombstone ownership 契约。
- React + TypeScript + Vite 前端骨架。

验收：

- health endpoint 可启动。
- Neo4j 能创建并查询 Project、Class、Method、JspPage、SqlStatement、DbTable 示例节点。
- Java method SymbolId 使用 erased JVM descriptor。
- JSP/XML/SQL/report identity 使用 source root + 相对路径，不包含本机绝对路径。
- provisional symbol 后续能通过 alias merge 或 redirect 解析。
- 失败的 analysis run 不暴露半写入 facts。
- current snapshot 查询不返回 tombstoned facts。

## 3. MVP-1：导入审查和最小业务链路

目标：

- 将本地目录或上传归档转换为经过审查的分析范围。
- 建立可支撑影响报告的最小 Spring/Struts1/JSP/HTML/MyBatis/SQL/Java direct-call 链路。

交付物：

- Workspace profiler 和 ImportReviewReport。
- 文件能力等级 L1-L5，以及 READY、PARTIAL、BOUNDARY_ONLY、UNSUPPORTED、BROKEN 项目状态。
- AnalysisScopeDecision，用于记录 assisted import 中用户确认的 include/exclude、共享库、依赖补充、source root/lib/web root/scripts 和忽略目录。
- Analysis planner 和 work queue。
- Java source analyzer，使用 Spoon，并明确 no-classpath fallback 边界。
- Bytecode analyzer，在源码缺失时使用 ASM/ClassGraph/ProGuardCORE。
- Spring adapter，覆盖 controller、mapping、service、injection hints 和 routes。
- Struts1 adapter，覆盖 config、module、action、form、forward、Tiles、Validator、plugin、message resource。
- JSP analyzer，基于 WebAppContext，优先 Jasper，失败时 tolerant parser fallback，覆盖 form、input、directive、taglib、include、forward、EL、scriptlet request parameter facts。
- HTML/JS static client analyzer，只覆盖稳定 form、静态 link、静态 request 和 resource reference。
- MyBatis XML/interface analyzer 和 JSqlParser/JDBC SQL extraction。
- Seasar2 discovery/candidate adapter，MVP 中只输出 POSSIBLE confidence。

最小关系契约：

```text
ApiEndpoint -[:ROUTES_TO]-> Method
ActionPath -[:ROUTES_TO]-> Method
JspForm -[:SUBMITS_TO]-> ApiEndpoint/ActionPath
JspPage -[:USES_TAGLIB|INCLUDES]-> TagLibrary/JspPage
JspForm -[:RENDERS_INPUT]-> JspInput
JspInput -[:BINDS_TO]-> RequestParameter/FormField/ParamSlot
JspTag/JspExpression -[:READS_MODEL_ATTR|READS_REQUEST_PARAM|READS_SESSION_ATTR]-> ModelAttribute/RequestParameter/SessionAttribute
JspTag/JspScriptlet -[:WRITES_MODEL_ATTR|WRITES_REQUEST_ATTR|WRITES_SESSION_ATTR]-> ModelAttribute/RequestAttribute/SessionAttribute
HtmlPage/JspPage -[:CONTAINS|LOADS_SCRIPT]-> HtmlForm/HtmlInput/HtmlLink/ScriptResource
HtmlForm -[:RENDERS_INPUT]-> HtmlInput
HtmlInput -[:BINDS_TO]-> RequestParameter/FormField/ParamSlot
HtmlLink -[:NAVIGATES_TO]-> HtmlPage/ApiEndpoint/ActionPath
DomEventHandler -[:SUBMITS_TO|CALLS_HTTP|NAVIGATES_TO]-> ClientRequest/ApiEndpoint/ActionPath
ClientRequest -[:CALLS_HTTP|SUBMITS_TO]-> ApiEndpoint/ActionPath
EntryPoint -[:INVOKES|ROUTES_TO]-> Method
Schedule/CronTrigger -[:SCHEDULES|TRIGGERS]-> EntryPoint/Method
MessageListener/BatchJob/CliCommand -[:HAS_PARAM]-> JobParameter
ShellScript -[:INVOKES|CALLS_COMMAND|USES_CONFIG]-> MainMethod/CliCommand/BatchJob/ExternalCommand/ConfigKey
Method -[:CALLS]-> Method
Method -[:BINDS_TO]-> SqlStatement
DataSource -[:CONTAINS]-> DbSchema
DbSchema -[:CONTAINS]-> DbTable
DbTable -[:CONTAINS]-> DbColumn
SqlStatement -[:READS_TABLE|WRITES_TABLE]-> DbTable
SqlStatement -[:READS_COLUMN|WRITES_COLUMN|HAS_PARAM]-> DbColumn/SqlParameter
ReportDefinition -[:READS_TABLE|READS_COLUMN|USES_CONFIG]-> DbTable/DbColumn/ConfigKey
ReportField -[:MAPS_TO_COLUMN]-> DbColumn
FeatureSeed -[:MATCHES]-> EntryPoint/Method/SqlStatement/DbTable/DbColumn/ConfigKey/SourceFile
FeatureScope -[:CONTAINS]-> EntryPoint/Method/SqlStatement/DbTable/DbColumn/ConfigKey/TestCase
ChangeItem -[:REQUIRES_CHANGE|SUGGESTS_REVIEW|REQUIRES_TEST]-> EntryPoint/Method/SqlStatement/DbTable/DbColumn/ConfigKey/TestCase
SavedQuery -[:MATCHES|WATCHES]-> EntryPoint/Method/SqlStatement/DbTable/DbColumn/ConfigKey/FeatureScope
Method -[:CALLS_NATIVE|HAS_NATIVE_BOUNDARY]-> NativeLibrary/ConfigKey
NativeLibrary/Project -[:EXPORTS_SYMBOL]-> BoundarySymbol
Method/ShellScript/ConfigKey -[:REFERENCES_SYMBOL]-> BoundarySymbol/NativeLibrary/ExternalCommand
```

验收：

- 能从 JSP form 追踪到 Action/Controller。
- 能从 JSP/HTML form 或静态 JS ClientRequest 追踪到 Action/Controller。
- 能从 Action/Controller 反查 JSP/API/Web Client entrypoint。
- 能展示 JSP/API -> Action/Controller -> Service -> DAO/Mapper -> SQL/table 最短路径。
- 保留 EntryPoint 抽象，以便接入 batch、main、scheduler、message 和 shell 入口。
- 缺失 TLD/classpath 时降级为 POSSIBLE，而不是伪造 CERTAIN facts。
- Seasar2 不阻塞 MVP 报告，也不进入确定性影响路径。

## 4. MVP-2：增量图谱和 Git Diff 影响

目标：

- 基于 Git diff、committed active facts、changed-scope analysis 和 JVM cache fallback，快速生成初版影响报告。

交付物：

- JGit reader，读取 branch、commit、diff、changed files 和 changed hunks。
- Changed file 到 changed symbol 的 resolver。
- Scope-aware incremental scan planner。
- Staging store、batch commit coordinator、active view switch 和 rollback 语义。
- Neo4j graph writer/query service。
- JVM primitive adjacency cache，支持 caller/callee、invalidation、relation group 和 Neo4j fallback。
- Impact analysis engine 和 report builder。
- JSON 和 Markdown 影响报告。

验收：

- 小型 PR 初版影响报告在命名 benchmark profile 下目标为 10 到 30 秒。
- 报告包含 affected entrypoints、paths、risk level、suggested tests、evidence、confidence、sourceType 和 truncation state。
- 删除或变更的关系不会从旧 snapshot 泄漏到新报告。
- AI 关闭时仍能生成完整静态报告。

## 5. MVP-3：变量追踪和 SQL/Table 影响

目标：

- 在 MVP 深度内追踪 request parameter、JSP input、form field、Java variable、service/DAO argument、SQL parameter 和 DB table。

交付物：

- 方法内 def-use 和 alias propagation。
- Request parameter read/write facts。
- JSP input -> request parameter mapping。
- ActionForm/DynaActionForm field binding。
- Getter/setter 简单传播。
- Controller/Action -> Service -> DAO/Mapper 参数传播。
- MyBatis/JDBC SQL parameter binding。
- SQL/table 和 DB impact reports。
- Variable trace source/sink/all query contracts。

验收：

- 能把 request parameter 向上追踪到 JSP input，并向下追踪到 Java/SQL sink。
- 能展示 JSP field 下游影响到哪些 Service/DAO/SQL/table。
- 能展示 table 或 field 变更影响哪些 read/write code entrypoints。
- Variable trace 输出 evidence paths 和 confidence。

## 6. MVP-4a：REST 和可视化前端

目标：

- 让用户无需阅读 raw SymbolId 或 JSON，也能理解图谱和影响结果。

交付物：

- REST APIs：使用 `/api/v1` 版本化，覆盖 workspaces、projects、import-reviews、analysis-runs、snapshots、symbols、impact、variables、db-impact、features、architecture-health、reports、evidence、saved-queries、subscriptions、review-threads、policies、ci-checks、exports、admin，以及 project overview、query planning、result-view contracts。
- Project Dashboard。
- Task Workbench：首页统一输入框、常用任务入口、最近报告、结果摘要、下一步动作和右侧证据/覆盖/盲区面板。
- Impact Report View。
- DB Impact View。
- Graph Explorer。
- Variable Trace View。
- JSP/Web Client Flow View。
- SQL/Table Path View：从入口、方法、Mapper 或 SQL 出发查看 SQL/table/column 路径下钻，不替代 DB Impact 的 read/write/display/test 分组报告。
- Feature Change/Add Plan View。
- Architecture Health View。
- Symbol Search 和 Candidate Picker。
- Evidence Panel。

验收：

- 查询结果按 summary、evidence paths、evidence list、graph/detail 组织。
- 默认项目首页是 Project Dashboard + Task Workbench 组合页；Dashboard 提供项目概览，Task Workbench 是主操作区。
- 首页能从 Git diff、DB 表/字段、JSP/HTML 页面、变量名、Java symbol 或自然语言功能描述进入对应任务。
- 搜索结果有歧义时先显示 candidates。
- 默认视图不暴露 raw SymbolId，raw JSON 和 SymbolId 只作为下钻详情。
- 大图查询有 depth/limit 限制，并展示 truncation。
- 右侧 Evidence Panel 在报告、变量追踪、DB 影响、JSP/Web Client 链路和图谱探索中复用，统一展示 confidence、sourceType、boundary、evidence 和分析覆盖。
- 查询类 API pin 到 committed snapshot；长任务使用 async job；列表接口提供分页、排序和最大 limit；错误响应结构化。
- 删除、清理、重建索引、触发深度分析等写入类管理操作必须有幂等 key 或 `confirm=true`，并写审计日志。
- 每条展示路径都能回溯到 evidence。

## 7. MVP-4b：AI 摘要和 RAG

目标：

- 在静态 evidence 可用之后再引入 AI；AI 只负责解释、排序、摘要、候选建议和盲区说明。

交付物：

- AI provider abstraction 和 runtime config。
- API key protection 和 prompt/source redaction。
- Evidence pack builder。
- AI report assistant，生成 impact summary、risk explanation、test suggestions。
- AI candidate staging 语义：只进入 `AI_ASSISTED_CANDIDATE` relation family 或 planning artifact，默认不参与确定性影响路径。
- AI candidate 生命周期：携带 `createdFromEvidencePackId`、`expiresAt` 或 `staleAgainstSnapshot`，底层 evidence 变化后自动 stale。
- Code Graph RAG v1，包含 exact symbol search、graph expansion、vector recall、historical impact report recall。
- Static answer draft fallback。

验收：

- AI answer 必须引用 evidence paths。
- AI output 标记为 AI assisted，不得被当作事实。
- AI candidate 不能清理或覆盖 Spoon/XML/JSP/SQL/Impact Flow 静态 facts。
- AI 关闭时 static reports、UI queries、MCP tools 仍可工作。

## 8. MVP-4c：只读 MCP 和受控 Agent

目标：

- 向 IDE 和外部 AI 工具开放受控查询能力，同时禁止 raw database、filesystem 或 shell access。

交付物：

- 只读 MCP server。
- Tool、resource、prompt registries。
- ImpactAnalysisAgent、DbImpactAgent、VariableImpactAgent、FeatureChangePlanAgent、FeatureAdditionPlanAgent、CodeQuestionAgent。
- Tool guard、project allow-list、rate limiting、redacted audit logging、step limits、source budget controls。

验收：

- MCP client 能查询 symbol search、callers、callees、impact paths、DB impact、variable impact、JSP flow、feature plan、reports 和 RAG answer drafts。
- Agent 只能调用 profile 允许的只读工具。
- Raw Cypher、SQL statement、file glob、shell command 和写操作会被拒绝。
- Agent 能输出 ImpactReport、DbImpactReport、VariableImpactReport 或 ChangePlanReport，并在快速报告后正确标记 deep supplement、pending scopes、stale/upgrade available。
- Agent output 包含 evidence、confidence、sourceType 和 truncation state。

## 9. 生产加固

目标：

- 将 MVP 闭环加固为企业可落地系统：写入可预期、confidence 可追踪、性能由 benchmark 驱动、不支持边界清晰。

交付物：

- Jasper SMAP parser，以及原始 JSP/generated servlet 映射。
- 字段级 `MAPS_TO_COLUMN`、`READS_COLUMN`、`WRITES_COLUMN`。
- 报表 adapter plugin layer，覆盖 Interstage List Creator、WingArc1st SVF、PSF、PMD、BIP、SVF XML、layout XML、field definition XML。
- JNI/native boundary modeling。
- 生产级 Neo4j batch write 和 single-writer coordination。
- Confidence aggregation rules。
- 小型、中型现代 Java/Spring/MyBatis、中型遗留 Struts1/JSP 三类 benchmark fixtures。

验收：

- include/tagfile/custom-tag JSP 映射保留 candidates，并对歧义 evidence 降级。
- 有 evidence 时，DB column 变更能追踪到 Java fields、JSP forms、SQL 和 reports。
- Native boundary 明确要求人工确认，但不阻塞其它非 native path branch。
- Batch failure 后 previous active view 仍可查询。
- 性能结论绑定 named benchmark profiles。

## 10. 增强阶段

Tai-e deep worker：

- 可选独立 JVM worker，用于 call graph、pointer analysis、taint analysis 和 deep supplement facts。
- 不得阻塞 MVP reports。
- 必须将 Tai-e signatures 映射回 CodeAtlas SymbolId，并标记 deep supplement source。

FFM OffHeapGraphIndex：

- 只有 benchmark thresholds 证明优于 Neo4j + JVM cache 时才启用。
- 使用 CSR/CSC compressed graph structures、MemorySegment、mmap、bounded BFS 和 primitive queues/bitmaps。

治理和迁移：

- Architecture rule checks。
- OpenRewrite recipe proposal 和 preview。
- 基于 evidence、history、ownership、change frequency 的 test recommendations。
- Report archiving 和 collaboration enhancements。
