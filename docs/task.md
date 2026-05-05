# CodeAtlas 任务清单

> 本文由 `docs/design.md` 生成。`docs/design.md` 是唯一事实源；本文是派生实现清单。

状态标记：

- `[ ]`：未开始
- `[design]`：设计已确定，等待实现
- `[proto]`：原型或部分实现
- `[x]`：完成

## 0. 文档治理

- [ ] 保持 `docs/design.md` 作为唯一事实源。
- [ ] 每次 design 需求变化后重新生成 `docs/plan.md` 和 `docs/task.md`。
- [ ] 确保派生文档不引入 design 中不存在的新事实。
- [ ] 确保所有性能承诺都绑定命名 benchmark profile。

## 1. 工程基线

- [x] 创建 Java 25 + Gradle multi-project 工程。
- [x] 增加 graph、analyzers、worker、server、UI、AI、MCP 模块。
- [x] 配置 Gradle Java Toolchains 使用 Java 25。
- [x] server 模块使用 Spring Boot + Spring MVC 承载 `/api/v1` REST 契约。
- [x] 增加后端 health endpoint。
- [x] 增加 React + TypeScript + Vite 前端骨架。
- [x] 增加本地开发说明。
- [x] 增加 CI 任务：后端测试、前端 build、文档检查。

## 2. 图谱模型和身份标识

- [x] 定义核心节点：Project、Module、SourceFile、Class、Method、Field、JspPage、JspInclude、JspTag、JspExpression、JspScriptlet、HtmlPage、HtmlForm、HtmlInput、HtmlLink、ScriptResource、DomEventHandler、ClientRequest、JspForm、JspInput、ApiEndpoint、ActionPath、EntryPoint、Schedule、CronTrigger、MessageQueue、MessageTopic、DomainEvent、MessageListener、BatchJob、CliCommand、ShellScript、ExternalCommand、SqlStatement、SqlParameter、DbSchema、DbTable、DbColumn、ConfigKey、ReportDefinition、ReportField、FeatureSeed、FeatureScope、ChangeItem、SavedQuery、WatchSubscription、ReviewComment、PolicyViolation、ExportArtifact、NativeLibrary、BoundarySymbol。
- [x] 定义 MVP 关系：DECLARES、CALLS、ROUTES_TO、SUBMITS_TO、BINDS_TO、READS_TABLE、WRITES_TABLE、INCLUDES、FORWARDS_TO、READS_PARAM、WRITES_PARAM、PASSES_PARAM、USES_CONFIG、USES_TAGLIB、RENDERS_INPUT、READS_MODEL_ATTR、READS_REQUEST_PARAM、READS_SESSION_ATTR、WRITES_MODEL_ATTR、WRITES_REQUEST_ATTR、WRITES_SESSION_ATTR、READS_FIELD、WRITES_FIELD、CONTAINS、LOADS_SCRIPT、NAVIGATES_TO、HANDLES_DOM_EVENT、CALLS_HTTP、INVOKES、SCHEDULES、TRIGGERS、PUBLISHES_TO、CONSUMES_FROM、HAS_PARAM、CALLS_COMMAND、READS_COLUMN、WRITES_COLUMN、HAS_VARIANT、GUARDED_BY、MAPS_TO_COLUMN、MATCHES、WATCHES、REQUIRES_CHANGE、SUGGESTS_REVIEW、REQUIRES_TEST、EXPORTS_SYMBOL、REFERENCES_SYMBOL。
- [x] 定义边界和增强关系：CALLS_NATIVE、HAS_NATIVE_BOUNDARY、AUTO_BINDS_TO、INTERCEPTS、CONFIGURES_PROPERTY、SUMMARIZES、COMMENTS_ON、VIOLATES_POLICY、SUPPRESSED_BY、EXPORTED_AS、REFLECTS_TO。
- [x] 实现 Neo4j constraints 和 indexes。
- [x] 实现 SymbolKind registry。
- [x] 实现 SymbolId parser 和 normalizer。
- [x] 实现 Java method SymbolId，使用 erased JVM descriptor。
- [x] 实现 JSP/XML/SQL/report identities，使用 source root 和相对路径。
- [proto] 实现 Flow Identity 和 Artifact Identity 契约。
- [x] 实现 provisional symbol 处理。
- [x] 实现 resolved symbol 的 alias merge 或 redirect。
- [x] 增加 SymbolId round-trip 测试。
- [x] 增加非法 SymbolId validation 测试。

## 3. Fact、Evidence、Snapshot 和提交语义

- [x] 定义 FactRecord 契约。
- [x] 定义 Evidence 契约和 evidence key。
- [x] 定义 Materialized Edge 契约。
- [x] 定义 confidence 等级：CERTAIN、LIKELY、POSSIBLE、UNKNOWN。
- [x] 定义 sourceType 和 analyzer source metadata。
- [x] 实现 AnalysisRun 和 ScopeRun 状态机：PLANNED、RUNNING、STAGED、COMMITTED、FAILED、ROLLED_BACK。
- [x] 实现 staging store。
- [x] 确保 staging facts 不参与 current active queries。
- [x] 实现 active view commit。
- [x] 实现 rollback，并保留 previous active facts。
- [x] 实现基于 analyzer 和 scope 的 tombstone ownership。
- [x] 实现 commit 后 cache rebuild 触发。
- [x] 增加测试：失败 analysis 不暴露半写入 facts。
- [x] 增加测试：tombstoned relations 不出现在 current reports。

## 4. 导入审查和 Workspace Profiling

- [x] 支持 LOCAL_FOLDER 导入源。
- [x] 支持 UPLOADED_ARCHIVE 导入源。
- [x] 建模 DIRECT_IMPORT 模式。
- [x] 建模 ASSISTED_IMPORT_REVIEW 模式。
- [x] 实现 file inventory，包含 hash、size、path 和 decode diagnostics。
- [x] 将文件分类为 L1 structured、L2 semi-structured、L3 boundary、L4 inventory、L5 skipped。
- [x] 识别 Gradle、Maven、Ant-like、Eclipse/IDE-only、source-only 和 unknown legacy layouts。
- [x] 识别 web root、WEB-INF、WebRoot、WebContent、source roots、classpath candidates、resource roots。
- [x] 识别 entrypoint 线索：main methods、Spring/Struts/JSP、HTML forms、static JS requests、scheduler/message listeners、shell Java commands。
- [x] 将非 Java 项目和 native/COBOL/C/JCL 文件记录为 inventory 或 boundary diagnostics。
- [x] 生成 ImportReviewReport，包含 project candidates、capability levels、blind spots 和 recommended analysis scopes。
- [x] ImportReviewReport 包含 workspace 总览、项目清单、项目关系、覆盖能力、盲区风险、建议用户确认事项和推荐分析计划。
- [x] 在 ASSISTED_IMPORT_REVIEW 下，存在 PARTIAL、BOUNDARY_ONLY、UNSUPPORTED、BROKEN 或 UNKNOWN 项目时，正式分析前展示警告并要求用户确认。
- [x] 将用户确认的 include/exclude 项目、共享库标记、补充依赖、source root/lib/web root/scripts 和忽略目录记录为 AnalysisScopeDecision。
- [x] 将 AnalysisScopeDecision 写入分析元数据和最终报告，确保不可分析范围、用户排除范围和降级范围可审计。
- [x] DIRECT_IMPORT 遇到无 Java 文件、压缩包不安全、主体明显非 Java 或路径不可读时阻止或警告。
- [x] 增加 mixed workspace import review 测试。

## 5. 分析规划和 Work Queue

- [x] 将 ImportReviewReport、Git diff、selected scopes 和 existing snapshots 转换为 analyzer task graph。
- [x] 优先处理 changed scopes 和 cache-miss scopes，支撑 fast reports。
- [x] 将 background deep tasks 与 fast report tasks 分开调度。
- [x] 增加 task timeout、retry、cancellation 和 worker failure isolation。
- [x] 确保默认不执行被分析项目的 build scripts。
- [x] 增加 degraded 和 partial project analysis 的 planning 测试。

## 6. Java Source 和 Bytecode 分析

- [x] 集成 Spoon 作为 primary Java source analyzer。
- [x] 实现 no-classpath fallback，并明确 confidence downgrade。
- [x] 提取 classes、methods、fields、annotations、source locations 和 direct method invocations。
- [x] 输出 Method -CALLS-> Method direct call facts。
- [x] 输出 class/member DECLARES facts。
- [x] 实现 changed-scope Java analysis。
- [x] 实现基于 file hash 的 Spoon incremental cache。
- [x] 确保 cache 不持久化完整 CtElement 或 AST objects。
- [x] 集成 ASM/ClassGraph/ProGuardCORE bytecode scanning。
- [x] 提取 jar classes、methods、fields、annotations、inheritance 和 bytecode method calls。
- [x] 合并 source 和 JVM origins，同时保留 sourceOnly/jvmOnly flags。
- [x] 增加 source-only、jar-only、source+jvm symbol merging 测试。

## 7. Spring、Struts1、JSP、HTML/JS 和 Seasar2

- [x] 解析 Spring Controller、RestController、RequestMapping variants、Service 和 injection hints。
- [x] 输出 ApiEndpoint -ROUTES_TO-> Method。
- [x] 解析 Struts1 struts-config modules、actions、forms、forwards、exceptions、message resources、plugins、Tiles、Validator、controller config、DynaActionForm、DispatchAction、LookupDispatchAction。
- [x] 输出 ActionPath -ROUTES_TO-> Method。
- [x] 在有静态 evidence 时输出 method-to-forward 和 JSP/action navigation facts。
- [x] 从 web.xml、servlet version、page encodings、TLD、WEB-INF/lib jars、tag files、classpath candidates 和 web roots 构建 WebAppContext。
- [x] 使用 Jasper 作为 preferred JSP semantic parser。
- [x] 增加 Jasper runtime probe，区分 `javax.servlet`/`jakarta.servlet` API namespace 并选择当前 classpath 匹配的 Jasper profile。
- [x] 增加 isolated Jasper profile classloader，支持同时携带 `javax.servlet`/`jakarta.servlet` runtime 并按项目上下文选择。
- [x] 在 Jasper 或 context 不完整时使用 tolerant parser fallback。
- [x] 提取 JSP directive、taglib、include、forward、form、input、select、textarea、EL、scriptlet request parameter reads/writes 和 tag actions。
- [x] 输出 JspForm -SUBMITS_TO-> ApiEndpoint/ActionPath。
- [x] 输出 JSP input 和 request parameter facts。
- [x] 实现 HTML static forms、static links、static client request candidates 和 resource references。
- [x] 输出 HtmlPage/JspPage -CONTAINS-> HtmlForm/HtmlInput/HtmlLink/ScriptResource。
- [x] 输出 HtmlPage/JspPage -LOADS_SCRIPT-> ScriptResource。
- [x] 输出 HtmlForm -RENDERS_INPUT-> HtmlInput。
- [x] 输出 HtmlInput/JspInput -BINDS_TO-> RequestParameter/FormField/ParamSlot。
- [x] 输出 HtmlLink -NAVIGATES_TO-> HtmlPage/ApiEndpoint/ActionPath。
- [x] 输出 DomEventHandler -SUBMITS_TO/CALLS_HTTP/NAVIGATES_TO-> ClientRequest/ApiEndpoint/ActionPath。
- [x] 输出 ClientRequest -CALLS_HTTP/SUBMITS_TO-> ApiEndpoint/ActionPath。
- [x] 建模 EntryPoint，并为 Spring/Struts/JSP/HTML/JS、batch、main、scheduler、message、shell 入口保留统一入口抽象。
- [x] 禁止在没有 AST-backed evidence 时推断 dynamic JavaScript URL。
- [x] 解析 Seasar2 dicon components、includes、properties、aspects、interceptors 和 naming candidates。
- [x] MVP 中 Seasar2 facts 只作为 POSSIBLE discovery/candidate 输出。
- [x] 增加 Spring MVC、Struts1/JSP、Tiles、Validator、DynaActionForm、tag files、fallback JSP parsing、Seasar2 discovery 测试。

## 8. SQL、数据库和字段级数据影响

- [x] 解析 MyBatis mapper interfaces。
- [x] 解析 MyBatis XML namespace 和 statement id。
- [x] 关联 mapper methods 到 SqlStatement。
- [x] 使用 JSqlParser 提取 SQL table。
- [x] 为 dynamic SQL 增加 conservative fallback。
- [x] 解析 JDBC SQL literals 和简单 constant concatenation。
- [x] 输出 SqlStatement -READS_TABLE/WRITES_TABLE-> DbTable。
- [x] 输出 JDBC method -> SqlStatement -> DbTable。
- [x] 输出 PreparedStatement parameter binding facts。
- [x] 增加 JPA entity table 和 field column mapping。
- [x] 增加字段级 MAPS_TO_COLUMN、READS_COLUMN、WRITES_COLUMN。
- [x] 增加 DB table 和 column impact queries。
- [x] 增加 MyBatis、JDBC、dynamic SQL、JPA、table impact、field impact 测试。

## 9. 影响分析

- [x] 使用 JGit 读取 Git branch、commit、diff、changed files 和 hunks。
- [x] 将 changed files 解析到 class、method、JSP、SQL、config、table symbols。
- [x] 实现从 changed method 出发的 caller traversal。
- [x] 实现从 entrypoints 出发的 downstream traversal。
- [x] 实现 JSP/API -> Action/Controller -> Service -> DAO/Mapper -> SQL/table path search。
- [ ] 默认使用 Neo4j active facts 作为事实源。
- [x] 增加 JVM primitive adjacency cache，服务热点 caller/callee edges。
- [x] 实现按 projectId、snapshotId、relation group 分片的 cache。
- [x] 实现 cache invalidation 和 Neo4j fallback。
- [x] 构建 Fast Impact Report JSON。
- [x] 构建 Markdown report exporter。
- [x] 报告包含 affected symbols、path steps、risk、confidence、sourceType、evidenceKeys、truncation 和 suggested tests。
- [x] 增加 benchmark profile 下 10 到 30 秒 fast report 回归 guard。
- [x] 增加 deleted relationships、changed snapshots、AI disabled reports、truncation 测试。

## 10. 变量追踪

- [x] 实现 method-local def-use。
- [x] 按源码顺序传播 simple local aliases。
- [x] 追踪 request.getParameter、getAttribute、setAttribute。
- [x] 追踪 ActionForm 和 DynaActionForm property reads。
- [x] 追踪 getter/setter 简单传播。
- [x] 为 request-derived arguments 输出 PASSES_PARAM。
- [x] 追踪 Controller/Action -> Service -> DAO/Mapper parameter flow。
- [x] 追踪 JSP input -> request parameter -> Java parameter source paths。
- [x] 追踪 Java parameter -> SQL parameter/table sink paths。
- [x] 增加 variable trace source、sink 和 combined reports。
- [x] 增加 alias chains、assignment order、string transformations、ActionForm、JSP sources、SQL sinks 测试。

## 11. REST API 和 Query Planning

- [x] 所有 REST API 使用 `/api/v1` 版本前缀；breaking change 进入 `/api/v2`。
- [x] 增加 workspaces API。
- [x] 增加 projects API。
- [x] 增加 import-reviews API。
- [x] 增加 analysis-runs API。
- [x] 增加 snapshots API。
- [x] 增加 project overview endpoint。
- [x] 增加 symbol search endpoint。
- [x] 增加 caller/callee/path query endpoints。
- [x] 增加 impact analysis 和 report endpoints。
- [x] 增加 variable trace endpoint。
- [x] 增加 db-impact endpoint，覆盖 table/column 到 SQL、Mapper/DAO、Service、入口的 read/write/display/test 分组。
- [x] 增加 features endpoint，覆盖 feature.planChange、feature.planAddition 和 ChangePlanReport artifact。
- [x] 增加 architecture-health endpoint，输出热点、循环依赖候选、动态解析风险和边界风险。
- [x] 增加 JSP backend flow endpoint。
- [x] 增加 SQL/table impact endpoint。
- [x] 增加 reports endpoint。
- [x] 增加 evidence endpoint。
- [x] 增加 saved-queries endpoint。
- [x] 增加 subscriptions endpoint。
- [x] 增加 review-threads endpoint。
- [x] 增加 policies endpoint。
- [x] 增加 ci-checks endpoint。
- [x] 增加 exports endpoint。
- [x] 增加 admin endpoint。
- [x] 增加 natural-language deterministic query planning endpoint。
- [x] 增加 result view contract endpoint。
- [x] 查询类 API 默认只读并 pin 到 committed snapshotId；未传 snapshot 时使用 latest committed snapshot，并在响应中返回实际 snapshotId。
- [x] 长任务使用 async job，提交请求返回 jobId/reportArtifactId，前端通过轮询或订阅状态获取结果。
- [x] 所有列表接口提供分页、排序和最大 limit，超限返回结构化错误。
- [x] 对 invalid 或 missing parameters 返回结构化 400 errors。
- [x] 错误响应包含 requestId、code、message、details、retryable 和 status。
- [x] 删除项目、清理 workspace、重建索引、触发深度分析等写入类管理操作必须使用幂等 key 或明确的 confirm=true 参数。
- [x] 增加 project allow-list authorization 和 403 responses。
- [x] 增加 local frontend development CORS support。
- [x] 为每个 read-only query contract 增加测试。

## 12. UI

- [x] 采用“任务工作台优先”的首页信息架构，避免默认从图谱或 raw SymbolId 开始。
- [x] 构建 project dashboard，展示 capability cards、analysis status、entrypoints、blind spots，并作为默认项目首页的概览区域。
- [x] 构建 Task Workbench 三列布局：左侧常用任务/最近报告，中间统一输入和结果摘要，右侧证据/覆盖/盲区面板。
- [x] 默认项目首页采用 Project Dashboard + Task Workbench 组合页，Task Workbench 是主操作区，Dashboard 不作为竞争首页。
- [x] 构建 global query/search input。
- [x] 统一输入支持 Git diff、DB 表/字段、JSP/HTML 页面、Java symbol、变量名和自然语言功能描述。
- [x] 构建快捷任务按钮：分析 Git Diff、查 DB 影响、查变量流向、查 JSP/Web Client 链路、规划功能修改、找相似实现。
- [x] 构建结果摘要区，展示风险、影响入口、SQL/table、建议测试、Fast/Deep 状态和下一步动作。
- [x] 构建 symbol candidate picker。
- [x] 多候选时 Candidate Picker 先展示 project/module/datasource、类型、路径、行号、confidence 和 evidence key，禁止自动跨范围选择。
- [x] 构建 impact report page。
- [x] 构建 graph explorer，支持 caller、callee、combined、entry-to-SQL modes。
- [x] 构建 variable trace view，支持 source、sink、all modes。
- [x] 构建 JSP/Web Client flow view，展示 JSP/HTML/ClientRequest -> Action/Controller -> Service -> DAO/Mapper -> SQL/table。
- [x] 构建 SQL/Table Path View，用于从入口、方法、Mapper 或 SQL 下钻到 SQL/table/column 路径。
- [x] 构建 DB Impact View，区分 read/write/display/test impact。
- [x] 明确 DB Impact View 是表/字段变更报告页，SQL/Table Path View 是路径探索页，避免两套页面重复表达同一职责。
- [x] 构建 Feature Change/Add Plan View，展示必须关注、建议检查、可能相关、建议测试和不确定项。
- [x] 构建 Architecture Health View，展示热点、循环依赖候选、动态风险和边界风险。
- [x] 构建 evidence panel，展示 file、line、evidence type、snippet metadata、analyzer、sourceType、confidence、raw details。
- [x] Evidence Panel 统一显示 analysisBoundary、pending/stale/deep supplement 状态和分析覆盖。
- [x] 默认页面隐藏 raw SymbolId 和 raw JSON，只在下钻详情中展示。
- [x] 对大结果展示 truncation 和 continuation affordances。
- [x] 将 raw JSON 作为下钻详情，而不是 primary view。
- [x] 为主流程增加 UI tests 或 browser smoke checks。

## 13. AI 和 RAG

- [x] 定义 AI provider abstraction。
- [x] 实现 system 和 project AI configuration。
- [x] 加密保存 API keys。
- [x] 对 source snippets 和 prompt logs 脱敏。
- [x] 构建带 source budgets 的 evidence packs。
- [x] 实现 AI impact summary、risk explanation、test suggestion prompts。
- [x] 确保 AI output 引用 evidence paths。
- [x] 定义 AI candidate relation family：AI_ASSISTED_CANDIDATE。
- [x] AI candidate 只能进入 AI_ASSISTED_CANDIDATE relation family 或 planning artifact，不写入静态分析 relation family。
- [x] AI candidate 必须走 staging schema 校验：identity 存在性、evidenceKey 引用、confidence boundary、project/snapshot 权限和 allowed relation type。
- [x] 默认影响路径查询不展开 AI candidate；只有用户打开“包含 AI 候选”或 Feature Planner 需要排序时才使用。
- [x] AI candidate 必须携带 createdFromEvidencePackId、expiresAt 或 staleAgainstSnapshot。
- [x] 底层 evidence 变化后自动将 AI candidate 标记 stale，不参与 active 确定路径。
- [x] AI candidate tombstone 只清理 AI candidate family，不能删除 Spoon/XML/JSP/SQL/Impact Flow facts。
- [x] 在 AI disabled 或 failure 时提供 static fallback。
- [x] 实现 embedding provider abstraction。
- [x] 实现 Neo4j Vector Index v1 contract。
- [x] 实现 exact symbol search + vector recall + graph expansion hybrid search。
- [x] 召回 historical impact report summaries。
- [x] 实现 evidence-backed answer draft。
- [x] 增加 AI disabled mode、redaction、evidence citation、hybrid search 测试。

## 14. MCP 和 Agents

- [x] 实现 read-only MCP server。
- [x] 定义 MCP tool names、resources、prompts。
- [x] 暴露 symbol.search、graph.findCallers、graph.findCallees、graph.findImpactPaths、impact.analyzeDiff、db.findCodeImpacts、variable.findImpacts、jsp.findBackendFlow、feature.planChange、feature.planAddition、rag.semanticSearch、rag.answerDraft、report.getImpactReport、project.overview。
- [x] 实现 ImpactAnalysisAgent profile。
- [x] 实现 DbImpactAgent profile，输出 DbImpactReport，并区分 table 级降级和 column 级确定影响。
- [x] 实现 VariableTraceAgent profile。
- [x] 实现 VariableImpactAgent profile，输出变量来源、流向和影响范围。
- [x] 实现 FeatureChangePlanAgent profile，输出已有功能修改范围、回归建议和 ChangePlanReport。
- [x] 实现 FeatureAdditionPlanAgent profile，输出新增功能候选参照、落点建议、风险和测试建议。
- [x] 实现 CodeQuestionAgent profile。
- [x] 实现 Agent 状态机：CREATED、PLANNING、WAITING_FOR_USER、RUNNING_FAST、FAST_READY、RUNNING_DEEP、COMPLETED、PARTIAL、FAILED、CANCELLED。
- [x] Agent 状态字段包含 agentRunId、agentType、projectId、snapshotId、queryId、reportArtifactId、status、currentStep、pendingQuestions、pendingScopes、deepJobIds、partialResults、warnings、errors、cost、createdAt、updatedAt。
- [x] Agent 多候选场景返回 Candidate Picker 或追问，不跨 project/module/datasource 自动选择。
- [x] Agent 快速报告先返回，深度层完成后生成补充 artifact，并标记原报告 stale/upgrade available。
- [x] Agent 失败返回 PARTIAL/FAILED/TRUNCATED/PENDING 和原因，不隐藏已可用结构化结果。
- [x] 实现 tool call guard。
- [x] 拒绝 out-of-profile tools。
- [x] 拒绝 non-read-only tools。
- [x] 拒绝 raw Cypher、SQL、file glob、shell command 和 statement-style arguments。
- [x] dispatch 前执行 project allow-list。
- [x] 增加 fixed-window rate limiting。
- [x] 增加 redacted audit log，记录 requestId、principal、projectId、toolName、parameter summary、result count、redaction state、duration、rejection reason。
- [x] 确保 agent output 包含 evidence、confidence、sourceType 和 truncation。

## 15. 生产加固

- [ ] 实现 Jasper generated servlet SMAP parser。
- [ ] 在 evidence 中保存 jspPath、jspLineStart、jspLineEnd、generatedServletPath、generatedLineStart、generatedLineEnd。
- [ ] 保留 include/tagfile/custom-tag SMAP candidate lists。
- [ ] 在 SMAP missing 或 ambiguous 时降低 confidence。
- [ ] 设计 ReportDefinition、ReportField、ReportParameter nodes。
- [ ] 增加 report resource evidence keys。
- [ ] 增加 Interstage List Creator parser interface。
- [ ] 增加 WingArc1st SVF parser interface。
- [ ] 通过 plugin adapters 解析 PSF、PMD、BIP、SVF XML、layout XML、field definition XML。
- [ ] 实现 DB column -> affected report query。
- [ ] 识别 Java native methods。
- [ ] 识别 System.load 和 System.loadLibrary。
- [ ] 建模 NativeLibrary 和 native boundaries。
- [ ] 在 native paths 标记 analysisBoundary=NATIVE 和 requiresManualReview=true。
- [ ] 当 native branch 停止时，其它 non-native path branches 仍继续搜索。
- [ ] 实现按 projectId、snapshotId、analyzerId、scopeKey 分组的 production batch upsert。
- [ ] 使用 file 作为 source/JSP/XML/SQL 最小 scope。
- [ ] 使用 JAR 或 module 作为 classpath cache 最小 scope。
- [ ] 对同一 projectId + snapshotId 增加 single-writer coordination。
- [ ] 增加 stable batch ordering 和 deadlock retry with backoff。
- [ ] 实现 confidence aggregation：CERTAIN > LIKELY > POSSIBLE > UNKNOWN。
- [ ] 确保 analyzer priority 和 evidence count 不提升 confidence。
- [ ] 将 conflicting facts 保留为 separate candidates。

## 16. Benchmark

- [ ] 创建 small fixture benchmark。
- [ ] 创建 medium Spring/MyBatis open-source benchmark target。
- [ ] 创建 medium Struts1/JSP legacy benchmark fixture。
- [ ] 测量 scan time。
- [ ] 测量 graph write time。
- [ ] 测量 Neo4j query P95。
- [ ] 测量 JVM cache heap。
- [ ] 测量 impact report latency。
- [ ] 增加 false-positive 和 false-negative sample set。
- [ ] 默认启用 FFM、Tai-e 或 cache strategy 变化前，必须有 benchmark evidence。

## 17. 可选深度增强

- [ ] 完成 Tai-e license review 和 distribution decision。
- [ ] 增加 independent Tai-e JVM worker。
- [ ] 准备 Tai-e classpath 和 compiled classes inputs。
- [ ] 配置 call graph、pointer analysis、taint analysis profiles。
- [ ] 将 Tai-e signatures 映射到 CodeAtlas SymbolId。
- [ ] 将 Tai-e facts 作为 deep supplement facts 导入。
- [ ] 强制 Tai-e timeout、heap limit 和 failure degradation。
- [ ] 设计 FFM CSR/CSC graph format。
- [ ] 实现 MemorySegment offsets、targets、edgeTypes。
- [ ] 实现 mmap read-only reload。
- [ ] 实现基于 FFM index 的 caller/callee 和 bounded BFS。
- [ ] 只有 benchmark activation policy 推荐时才路由到 FFM。
- [ ] 增加 architecture rule checks。
- [ ] 增加 OpenRewrite recipe proposal 和 preview。
- [ ] 将 historical risk、ownership、change frequency 纳入 test recommendations。
