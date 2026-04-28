# CodeAtlas Task List

## 1. 基础工程

- [x] 初始化后端服务工程。
- [x] 初始化分析 worker 工程。
- [x] 配置 JDK 25 LTS 运行环境。
- [x] 使用 Gradle 初始化 multi-project 工程。
- [x] 配置 Gradle Java Toolchains，固定 Java 25。
- [x] 配置 Gradle 统一依赖版本管理。
- [x] 配置 Gradle 后端、worker、analyzers、graph、ai、mcp、ui 模块。
- [x] 建立项目、模块、扫描任务、snapshot、报告的数据模型。
- [x] 建立文件 hash 和增量扫描机制。
- [x] 建立统一日志、任务状态、失败重试和超时机制。
- [x] 建立核心契约测试：symbolId、factKey、evidenceKey、confidence、snapshot/tombstone。
- [x] 建立开源依赖 license review 清单，重点检查 Spoon、Tai-e、Neo4j、Jasper、JetHTMLParser/Jericho。
- [x] 设置 license gate：未通过 review 的依赖不得进入默认发行包。

## 2. Neo4j 图谱底座

- [x] 设计 Neo4j labels、relationship types、constraints、indexes。
- [x] 设计 role label/property 规则，避免 Controller/Service/Dao 与 Class/Method 重复建节点。
- [x] 实现 `symbolId` 生成器，覆盖重载、构造器、静态初始化块、内部类、泛型擦除、bridge/synthetic 方法。
- [x] 规范 source signature 与 JVM descriptor 的映射。
- [x] 规范 module/sourceRoot/path 大小写和分隔符归一化。
- [x] 实现 `factKey` 和 `evidenceKey` 生成器。
- [x] 实现 Project、Module、File、Class、Method、Field 基础节点写入。
- [x] 实现 JspPage、JspForm、JspInput、SqlStatement、DbTable、DbColumn 节点写入。
- [x] 实现批量 upsert 节点和关系。
- [x] 实现 evidence、confidence、sourceType、snapshotId、analysisRunId、scopeKey 属性。
- [x] 实现同一 fact 多 evidence 合并。
- [x] 实现 confidence 聚合规则。
- [x] 实现 active fact 查询视图。
- [x] 实现图谱增量更新、旧 snapshot 隔离和 tombstone 策略。
- [x] 实现 snapshot diff 查询。
- [x] 实现 Neo4j caller/callee 查询 Cypher。
- [x] 实现 Neo4j impact path 查询 Cypher 模板。

## 3. Git 与快速索引

- [x] 使用 JGit 读取仓库、commit、branch、diff、changed files。
- [x] 解析 Maven 多模块结构。
- [x] 解析 Gradle `settings.gradle(.kts)` 和基础 source sets。
- [x] 评估 Gradle Tooling API，用于复杂 Gradle 项目增强。
- [ ] 使用 ASM/ClassGraph 快速扫描 class、annotation、继承、实现、资源文件。
- [x] 建立文件到符号的快速索引。
- [x] 建立 changed file -> candidate symbol 映射。
- [x] 从 unified diff 文本定位 changed files。
- [x] 建立 changed symbol -> impacted entrypoint 初步查询链路。

## 4. Java 源码分析

- [x] 接入 Spoon 作为 Java 源码 AST 主引擎。
- [x] 支持 noClasspath 模式。
- [x] 提取 class/interface/enum/annotation。
- [x] 提取 method/constructor/field/annotation。
- [x] 提取 direct method invocation。
- [x] 提取 extends/implements。
- [x] 提取 line number、file path、method signature。
- [x] 将 Spoon `CtMethod` 映射到统一 `symbolId`。
- [ ] 提取构造器、静态初始化块、内部类、匿名类和 lambda 的稳定标识。
- [ ] 标记 synthetic/bridge/source-only/jvm-only 方法。
- [ ] 保留 JavaParser 作为可选快速扫描器，不作为主事实源。
- [ ] 使用 JDT 或 Spoon 内部 JDT 能力作为绑定解析兜底。

## 5. JSP 分析

- [ ] 接入 Apache Jasper。
- [x] 建立 WebAppContext 模型。
- [ ] 解析 web root、WEB-INF/web.xml、Servlet/JSP API 版本和容器 profile。
- [ ] 构建 JSP classpath：WEB-INF/classes、WEB-INF/lib、Maven/Gradle dependencies、编译输出目录。
- [ ] 构建 TaglibRegistry：TLD 文件、jar 内 TLD、web.xml taglib 映射。
- [x] 实现 include resolver，支持静态 include、jsp:include、相对路径和 context path。
- [x] 实现 encoding resolver，支持 page directive、web.xml、BOM 和项目默认编码。
- [x] 解析 JSP directive：page、include、taglib。
- [x] 解析 JSP action：include、forward、param、useBean。
- [x] 解析 EL 表达式。
- [x] 解析 scriptlet 和 expression。
- [x] 解析 JSTL 常见标签。
- [ ] 解析 Struts taglib：html:form、html:text、bean:write、logic:iterate。
- [x] 解析 Struts taglib MVP：html:form、html:text、html:hidden、html:password、html:checkbox、html:select。
- [ ] 解析 Spring form tag。
- [ ] 调研并接入 JetHTMLParser 或 Jericho 作为容错解析器。
- [x] 提取 form/action/input/select/textarea。
- [x] 建立 JSP input -> request parameter 关系。
- [ ] 评估 Jasper 生成 Servlet + SMAP 回映射方案。
- [x] 明确不使用 jsoup 直接解析 JSP。
- [ ] Jasper 失败时记录缺失上下文并降级到 JetHTMLParser/Jericho，相关关系标记为 `POSSIBLE` 或 `LIKELY`。

## 6. 框架适配

- [x] Spring MVC：解析 Controller、RequestMapping、Service、Repository、Autowired。
- [x] Spring MVC MVP：解析 Controller/RestController 与 RequestMapping/GetMapping/PostMapping 等入口边。
- [x] Spring Bean：解析构造注入、字段注入、Resource、Qualifier。
- [ ] Spring 任务：识别 Scheduled、Async，后续接入事件/MQ。
- [x] Struts1：解析 struts-config.xml。
- [x] Struts1：建立 path -> Action -> ActionForm -> Forward。
- [x] Struts1：建立 JSP form action -> Action path。
- [x] Seasar2：解析 dicon component。
- [x] Seasar2：识别命名约定 binding。
- [x] Seasar2：识别 service/dao/interceptor 基础关系。
- [x] Seasar2 MVP 只输出 discovery/candidate 结果，确定性影响链路放到增强阶段。
- [x] 为所有框架关系标注 confidence 和 evidence。

## 7. SQL 与数据访问

- [x] 解析 MyBatis Mapper interface。
- [x] 解析 MyBatis XML statement id。
- [ ] 使用 JSqlParser 解析 SQL。
- [x] 提取 table、column、where condition、read/write 类型。
- [x] 提取 SQL table 和 read/write 类型的 MVP 候选结果。
- [x] 建立 Mapper method -> SqlStatement 关系。
- [x] 建立 SqlStatement -> DbTable/DbColumn 关系。
- [x] 建立 SqlStatement -> DbTable 关系。
- [x] 确保 MVP 最小链路包含 Mapper 方法 `Method -[:BINDS_TO]-> SqlStatement`。
- [x] 支持动态 SQL 的保守解析和 `POSSIBLE` 标注。
- [ ] 后续支持 JPA Entity -> table 映射。

## 8. 调用关系与变量追踪

- [x] 构建 direct call graph。
- [x] 构建 caller/callee 反向索引。
- [x] 构建 JVM primitive adjacency cache，作为 MVP 快速路径缓存。
- [x] 实现 JVM primitive adjacency cache 按 `projectId + snapshotId + relationGroup` 分片。
- [x] 实现缓存失效和 Neo4j 查询回退。
- [x] 记录 cache node count、edge count、heap bytes、hit ratio、P95 query time。
- [x] 支持接口方法到实现方法候选关系。
- [x] 实现方法内 def-use 链。
- [x] 追踪局部变量赋值、参数传递、return 来源。
- [x] 识别 `request.getParameter`、`getAttribute`、`setAttribute`。
- [x] 识别 ActionForm 字段绑定。
- [x] 识别 getter/setter 简单传播。
- [ ] 实现 Controller/Action -> Service -> DAO 参数传播。
- [x] 实现 JSP input -> request parameter -> Java parameter 链路。
- [ ] 输出变量来源和流向证据路径。

## 9. 影响分析

- [x] 从 Git diff 定位变更文件。
- [x] 从变更文件定位变更 class/method/JSP/SQL/config。
- [x] 从变更 method 反查 caller。
- [x] 从入口正查下游 callee。
- [x] 使用 Neo4j + JVM primitive adjacency cache 查询影响路径。
- [x] 实现 impact query REST API。
- [x] 实现 Impact Report 读取 REST API。
- [x] 实现 caller/callee/path query REST 查询计划 API。
- [x] 定义 Impact Report JSON schema：entrypoint、changedSymbol、path、confidence、evidenceList、sourceTypeList、riskLevel、reason、truncated。
- [ ] 关联受影响 JSP、API、Action、Controller、Service、DAO、Mapper、SQL、table。
- [x] 为每条影响路径计算 confidence。
- [x] 输出 Fast Impact Report。
- [ ] 输出 Deep Impact Report 增量补充。
- [x] 支持 Markdown/JSON 报告格式。
- [x] 支持“为什么受影响”的路径解释。
- [ ] 验证删除关系后不会从旧 snapshot 残留到新报告。

## 10. Tai-e 深度分析（增强阶段）

- [ ] 完成 Tai-e license review。
- [ ] 用样例项目验证 Tai-e 输入 classpath 和输出 signature 可映射性。
- [ ] 建立 Tai-e worker 独立 JVM。
- [ ] 准备 classpath 和 compiled classes 输入。
- [ ] 配置 call graph analysis。
- [ ] 配置 pointer analysis。
- [ ] 配置 taint analysis source/sink。
- [ ] 实现 Tai-e signature -> CodeAtlas symbolId 映射。
- [ ] 将 Tai-e 分析结果导入 Neo4j。
- [ ] 给 Tai-e worker 设置超时、内存上限和失败降级。
- [ ] 在报告中区分 Spoon 事实和 Tai-e 深度补强。
- [ ] 确保 Tai-e 失败不影响 MVP 影响报告。

## 11. FFM OffHeapGraphIndex（benchmark 驱动增强阶段）

- [ ] 建立 Neo4j 查询 P95 耗时 benchmark。
- [ ] 建立 JVM primitive adjacency cache heap 占用 benchmark。
- [ ] 定义启用 FFM 的规模阈值：edge 数、P95 查询耗时、heap 压力。
- [ ] 设计 CSR/CSC 数据格式。
- [ ] 设计 node id 和 edge id 压缩编码。
- [ ] 使用 `MemorySegment` 存储 offsets、targets、edgeTypes。
- [ ] 使用 `Arena.ofConfined()` 管理任务级临时内存。
- [ ] 使用 `Arena.ofShared()` 管理只读共享索引。
- [ ] 使用 `FileChannel.map(..., Arena)` 支持 mmap 持久缓存。
- [ ] 实现 caller/callee 查询。
- [ ] 实现多跳 BFS。
- [ ] 实现 visited bitmap 和 frontier queue。
- [ ] 与 Impact Analysis Engine 集成。
- [ ] 增加 heap 使用和查询耗时 benchmark。
- [ ] 只有 benchmark 证明收益后，才将 FFM 接入默认影响分析路径。

## 12. RAG 与向量检索

- [x] 设计 MethodSummary、ClassSummary、JspPageSummary、SqlStatementSummary、ImpactReportSummary。
- [ ] 接入 embedding provider。
- [ ] 首版使用 Neo4j Vector Index。
- [ ] 实现精确符号检索 + 向量召回 + Neo4j 图扩展。
- [x] 实现源码/JSP/XML/SQL evidence pack 构建。
- [ ] 支持自然语言代码问答。
- [ ] 支持相似代码和历史报告召回。
- [ ] 评估 pgvector、OpenSearch、Qdrant 作为后续扩展。

## 13. AI 与报告生成

- [x] 设计 AI Provider 抽象。
- [x] 支持系统级 AI 配置：provider、baseUrl、apiKey、model、embeddingModel、timeout。
- [x] 支持项目级 AI 开关和源码片段发送策略。
- [x] 实现源码和配置脱敏。
- [x] 实现 PR 影响摘要 prompt。
- [x] 实现风险解释 prompt。
- [x] 实现测试建议 prompt。
- [x] 实现调用路径解释 prompt。
- [x] AI 失败时报告降级为纯静态分析输出。
- [x] AI 输出必须引用 evidence path。

## 14. Agent 编排

- [ ] 实现 Tool Registry。
- [ ] 实现 ImpactAnalysisAgent。
- [ ] 实现 VariableTraceAgent。
- [ ] 实现 CodeQuestionAgent。
- [ ] 定义工具调用步数限制、超时、审计日志。
- [ ] 禁止 Agent 调用任意 Cypher。
- [ ] 禁止 Agent 任意读取文件。
- [ ] 让 Agent 只调用封装后的内部工具。
- [ ] 所有 Agent 结果输出 evidence、confidence、sourceType。

## 15. MCP 扩展层

- [x] 实现 MCP 工具白名单和只读 Registry 契约。
- [x] 定义 MCP Tool/Resource/Prompt 名称契约。
- [x] 实现只读 MCP Server。
- [x] 暴露 `symbol.search`。
- [x] 暴露 `graph.findCallers`。
- [x] 暴露 `graph.findCallees`。
- [x] 暴露 `graph.findImpactPaths`。
- [x] 暴露 `variable.traceSource`。
- [x] 暴露 `variable.traceSink`。
- [x] 暴露 `jsp.findBackendFlow`。
- [x] 暴露 `impact.analyzeDiff`。
- [x] 暴露 `rag.semanticSearch`。
- [x] 暴露 `report.getImpactReport`。
- [x] 实现 MCP Resources：symbol、jsp、table、report。
- [x] 实现 MCP Prompts：impact-review、variable-trace、jsp-flow-analysis、test-recommendation。
- [ ] 增加 project 权限、限流、脱敏、审计。

## 16. UI 与报告

- [x] 使用 React + TypeScript + Vite 初始化 `codeatlas-ui`。
- [x] 配置前端 Gradle 集成任务，支持统一 build。
- [ ] 选择企业级组件库，默认 Ant Design 或同类方案。
- [ ] 接入 Cytoscape.js 用于代码图谱探索。
- [ ] 接入 React Flow 或同类库用于影响路径/变量流线性展示。
- [ ] 项目总览页。
- [x] 变更影响报告页。
- [x] 方法调用链查询页。
- [x] 变量来源/流向查询页。
- [x] JSP 到后端链路查询页。
- [x] SQL/table 影响查询页。
- [x] 全局问答式搜索框，支持自然语言输入。
- [ ] 意图识别结果展示：symbol search、caller/callee、变量来源/流向、影响分析、JSP 链路、SQL/table 影响。
- [ ] 候选符号选择器：类型、qualified name/path、文件、行号、模块、最近变更信息。
- [x] 答案摘要区：确定路径数、可能路径数、主要来源/流向/影响点、风险等级、后台分析状态。
- [x] 纵向证据路径视图：每条边展示 edge type、confidence、sourceType。
- [x] 证据列表表格：文件、行号、证据类型、证据片段、分析器来源。
- [ ] AI 风险摘要展示。
- [x] 证据路径展示。
- [x] 置信度标识。
- [ ] 后台深度分析状态展示。
- [ ] Graph Explorer 支持 caller/callee、入口到 SQL 链路、节点展开和深度限制。
- [ ] Impact Report 支持风险等级、建议测试、AI 摘要、证据路径。
- [ ] Variable Trace View 支持来源/流向双向切换。
- [ ] JSP Flow View 支持 JSP -> Action/Controller -> Service -> DAO/Mapper -> SQL/table。
- [x] Symbol Search 支持 class/method/JSP/SQL/table/config 统一搜索。
- [ ] AI Q&A 页面要求答案旁展示证据节点和路径。
- [ ] UI 对所有大图查询展示 `truncated` 状态和继续展开入口。
- [ ] AI 摘要旁显示“基于 N 条静态分析证据生成”。
- [ ] 提供原始 JSON 明细查看入口。

## 17. 测试与样例工程

- [ ] 创建 Spring MVC 样例项目。
- [ ] 创建 Struts1 + JSP 样例项目。
- [ ] 创建 Seasar2 dicon 样例片段。
- [ ] 创建 MyBatis XML 样例。
- [ ] 创建 JSP form -> Action -> Service -> Mapper -> SQL 样例链路。
- [ ] 测试 Java 方法调用解析。
- [x] 测试 JSP input 到 request parameter。
- [x] 测试 ActionForm 字段绑定。
- [x] 测试 SQL table/column 识别。
- [ ] 测试 Git diff 影响报告。
- [ ] 测试问答式查询存在多个候选时先显示候选列表。
- [ ] 测试变量追踪结果按摘要、路径、证据、明细展示。
- [x] 测试 AI 关闭时结构化结果仍能展示。
- [x] 测试 tombstone 后旧边不会出现在 current snapshot 查询中。
- [x] 测试同一 fact 多 evidence 合并和 confidence 聚合。
- [ ] 测试 AI 关闭和开启两种模式。
- [ ] 测试 Neo4j 查询性能。
- [ ] 测试 JVM primitive adjacency cache 内存和速度。
- [ ] 增强阶段测试 FFM 图索引内存和速度。

## 18. 安全与治理

- [x] 源码片段脱敏。
- [ ] API key 加密存储。
- [ ] AI 请求日志脱敏。
- [x] MCP 工具白名单。
- [ ] 禁止任意数据库查询暴露。
- [ ] 禁止无确认写操作。
- [ ] 项目级访问控制。
- [ ] 审计 Agent 和 MCP 调用。
- [x] 标记 AI_ASSISTED 结果，禁止混淆为确定事实。

## 19. MVP 验收清单

- [x] 能扫描一个混合 Java Web 项目。
- [x] 能识别 Spring Controller 和 Struts1 Action。
- [x] 能解析 JSP form/input/action。
- [x] 能解析 Java class/method/call。
- [x] 能解析 MyBatis XML 和 SQL table。
- [x] 能写入 Neo4j 图谱。
- [x] 能稳定生成 symbolId、factKey、evidenceKey。
- [x] 能处理 snapshot/tombstone，避免旧关系残留。
- [x] 能打通 Spring RequestMapping、Struts action、JSP form/action、method direct call、MyBatis statement 五类最小边。
- [ ] Seasar2 只要求 dicon/component discovery，不要求确定性影响链路。
- [ ] 能从 Git diff 输出影响报告。
- [x] 能通过 REST API 查询 symbol、impact report、path query、variable trace、jsp flow。
- [ ] 能通过问答式搜索触发符号检索、变量追踪、影响分析、JSP 链路、SQL/table 影响。
- [ ] 查询结果能按答案摘要、证据路径、证据列表、图谱与明细展示。
- [ ] 能展示 JSP -> Action/Controller -> Service -> DAO/SQL/table 路径。
- [ ] 能追踪 request parameter 的来源和流向。
- [ ] 能给每条影响路径标注 evidence 和 confidence。
- [ ] 能在 10 到 30 秒输出初版报告。
- [ ] 能用 AI 生成风险摘要和测试建议。
- [ ] 能通过 MCP 只读查询核心分析能力。
- [x] 项目使用 Java 25 + Gradle 构建。
- [ ] 能通过可视化前端查看项目概览、影响报告、调用路径、变量流和 JSP 链路。
- [ ] Tai-e 和 FFM 不作为 MVP 验收前置条件。
