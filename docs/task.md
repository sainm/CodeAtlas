# CodeAtlas Task List

## 0. Recent Progress Memory

- [x] JSP: semantic analysis now records parserSource/parserName/fallbackReason so Jasper, Jasper+tokenizer merge, and tokenizer fallback are distinguishable.
- [x] Graph Query: added active-facts caller/callee report endpoints with confidence and evidenceKeys for UI/Agent/MCP consumption.
- [x] MCP: graph caller/callee tool contracts now describe evidence-carrying active-facts results and support maxDepth.
- [x] Query UX: added `/api/query/plan?q=...` for deterministic natural-language intent routing to existing read-only APIs.
- [x] Query UX: frontend now shows intent, endpoint, required parameters, relation types, evidence path, and evidence table.
- [x] Query UX: server responses include CORS headers for local frontend development.
- [x] Query UX: added `/api/query/views` result view contracts for impact, variable trace, JSP flow, graph, SQL/table, and symbol picker.
- [x] Query UX: `/api/query/views?name=VIEW_NAME` filters to one result display contract.
- [x] Query UX: frontend loads `/api/query/views` and displays active primary/evidence field contracts with fallback.
- [x] Query UX: frontend JSON button toggles raw query plan, result view contract, path preview, and evidence preview.
- [x] Query UX: natural-language planner prioritizes explicit action caller/impact questions over generic Struts/JSP action flow matching.
- [x] Query UX: frontend Execute button calls planned read-only endpoints, renders returned paths/evidence first, and falls back to local previews when no server result is available.
- [x] Query UX: frontend loads symbol candidates after planning and lets the user click a candidate to fill `symbolId` or `changedSymbol`.
- [x] Query UX: frontend result summary shows path count, confidence, risk, evidence row count, truncated state, and an expand action.
- [x] Query UX: frontend loads report assistant summary/test suggestions and labels whether it is AI-assisted or static fallback.
- [x] API: query endpoints return structured `400 bad_request` JSON for missing/invalid required parameters.
- [x] API: browser CORS preflight `OPTIONS` requests return allow-origin and allow-method headers for local frontend calls.
- [x] MCP: added read-only `query.plan` tool descriptor for natural-language query planning.
- [x] MCP: added read-only `query-view` resource descriptor for result display contracts.
- [x] MCP: added read-only report assistant summary tool descriptor for risk explanation and test suggestions.
- [x] MCP: tool planning now supports request principal context, project allow-lists, fixed-window rate limiting, and redacted audit events.
- [x] Security: AI API keys can be stored as AES-GCM encrypted payloads and decrypted only at runtime from a local master secret.
- [x] RAG: added embedding provider contracts, deterministic local fallback embeddings, and an in-memory semantic summary index for testable recall.
- [x] RAG: added hybrid search that combines exact symbol search, vector recall, and active graph-fact expansion with evidence keys.
- [x] RAG/API: added `/api/rag/semantic-search` for exact symbol search, semantic recall, graph-neighbor expansion, and evidence-key results.
- [x] RAG/API: semantic search now also recalls historical impact report summaries from the report store.
- [x] JSP: Jasper fallback analysis now exposes missing context diagnostics and caps fallback JSP relation confidence at `LIKELY`.
- [x] Bytecode: ASM-style class scanning now covers jar classes, annotations, inheritance/implementation, calls, fields, Spring endpoints, JPA hints, and config resources.
- [x] JSP: added SMAP mapping assessment contract to classify generated-servlet line mapping as candidate, deferred, or blocked.
- [x] Java: JavaParser 3.28.0 is available as an optional fast scanner with separate `JAVAPARSER_FAST` evidence, not a primary fact source.
- [x] Java: Spoon binding analysis now has a no-classpath fallback wrapper with explicit mode and fallback reason for legacy unresolved dependencies.
- [x] JSP: Jericho HTML Parser 3.4 is integrated as the tolerant fallback parser merged with tokenizer results; script/style false positives are filtered.
- [x] UI: Variable Trace View now supports source/sink/all direction switching with per-direction path counts.
- [x] UI: AI Q&A view now shows the answer draft beside matched evidence nodes and evidence keys.
- [x] UI: added a backend analysis status panel for fast static analysis, deep supplement, AI/RAG explanation, and FFM acceleration states.
- [x] UI: Graph Explorer now exposes caller, callee, combined, and entry-to-SQL query modes with depth/limit controls and node refocus expansion.
- [x] UI: added a project overview page with capability cards and guided low-threshold entry points.
- [x] UI/Open source: selected Ant Design as the enterprise component baseline, integrated Cytoscape.js graph canvas, and integrated React Flow path rendering with Vite manual chunks.
- [x] Query UX: project overview is now a backend result-view contract and natural-language route for overview/status questions.
- [x] API/UI: added `/api/project/overview` and wired the overview page to backend capabilities, analysisStatus, and entrypoints.
- [x] UI: project overview now renders backend capabilities and entrypoints with static fallback only when the endpoint has not been loaded.
- [x] MCP/Agent: exposed `project.overview` as a read-only orientation tool for dashboard and code-question agent workflows.
- [x] RAG/Neo4j: added a first-version Neo4j Vector Index contract for summary schema, embedding upsert, and vector query statements.
- [x] License: Tai-e review recorded as GPL-3.0/LGPL-3.0, optional enhancement worker only, not default distribution.
- [x] Tai-e: added independent JVM launch-plan contract with Tai-e jar, classpath/app-classpath, input classes, analyses, heap, output directory, and timeout metadata.
- [x] Tai-e: added controlled analysis profiles for call graph `cg`, pointer analysis `pta`, and `pta` with `taint-config`.
- [x] Tai-e: added signature mapper from Tai-e/Soot-style method and field signatures into CodeAtlas `symbolId` descriptors.
- [x] Tai-e: added Neo4j importer for Tai-e call edges as `TAI_E` evidenced `CALLS` facts.
- [x] Tai-e: optional worker supervisor now enforces timeout semantics and degrades failures without blocking fast MVP analysis.
- [x] Tai-e: impact JSON/Markdown marks Tai-e path steps as `DEEP_SUPPLEMENT`, separate from regular static facts.
- [x] Tai-e: sample compatibility verifier checks classpath inputs and Tai-e output signature mapping.
- [x] FFM: added CSR/CSC off-heap graph index with compressed int node/edge ids, caller/callee queries, bounded BFS, confined/shared arena factories, mmap read-only mapping, and benchmark coverage.
- [x] FFM: integrated with Impact Analysis through an explicit benchmark-driven router; default Fast Impact path remains JVM active-facts unless FFM is recommended.
- [x] RAG: evaluated pgvector, OpenSearch, and Qdrant as optional vector backends while keeping Neo4j Vector Index as v1 default.
- [x] RAG/API: added `/api/rag/answer-draft` to produce a static evidence-backed answer draft from RAG results.
- [x] MCP/Agent: exposed `rag.answerDraft` as a read-only tool for code-question workflows.
- [x] MCP: added `code-question` prompt that instructs clients to use `rag.answerDraft` and answer only from evidence.
- [x] Security: HTTP APIs now support project allow-list enforcement with 403 responses for forbidden projectId values.
- [x] Query UX: natural-language code questions such as "explain/find related code" route to `RAG_SEMANTIC_SEARCH` with `RAG_SEARCH_VIEW`.
- [x] Security: AI request audit events record provider/model/task and redacted prompts without persisting API keys.
- [x] Agent: added read-only agent tool registry and bounded profiles for impact analysis, variable trace, and code question agents.
- [x] Agent: added tool call guard that rejects out-of-profile tools, non-read-only tools, and calls over the profile step limit.
- [x] Agent: added answer/evidence contract so agent output can carry summary, findings, evidence, confidence, sourceType, and truncation state.
- [x] Agent: added structured in-memory audit log events for allowed/denied tool-call decisions.
- [x] Agent: added concrete ImpactAnalysisAgent, VariableTraceAgent, and CodeQuestionAgent profile-bound contracts with evidence validation.
- [x] SQL: MyBatis XML table extraction now uses JSqlParser first and falls back to conservative parsing for dynamic SQL.
- [x] JSP: WebAppContext now derives JSP version from web.xml servlet version, reads jsp-property-group page encoding, and maps TLD internal uri values.
- [x] JSP: tolerant semantic extractor now records taglib directive prefix, uri/tagdir, resolved location, confidence, and line number.
- [x] JSP: WebAppContext classpath candidates include WEB-INF/classes, WEB-INF/lib jars, Gradle build outputs, and Maven target/classes.
- [x] JSP: WebAppContext scans WEB-INF/lib jar files for META-INF/*.tld and maps internal TLD uri values to jar!/entry locations.
- [x] JSP: WebAppContext scans WEB-INF/tags .tag/.tagx files and tolerant semantic extraction records declared tagdir custom tag actions.
- [x] JSP: JspFormAnalyzer emits `JSP_PAGE -USES_CONFIG-> CONFIG_KEY` facts for taglib directives and resolved tagdir custom tag files.
- [x] JSP: scriptlet and JSP expression Java code with `request.getParameter/getAttribute/setAttribute` emits request parameter read/write facts.
- [x] JSP: Apache Jasper is wired as the preferred semantic parser for standard JSP nodes, with tokenizer fallback when legacy project context is incomplete.
- [x] JSP: tokenizer-based form/action extraction skips script/style blocks so JavaScript and CSS string literals do not create false Struts/JSP tag facts.
- [x] JSP: removed regex-based JavaScript URL inference; client-side navigation requires a future JS parser/AST integration before graph facts are emitted.
- [x] JSP: Struts1 tag semantics are centralized in `StrutsJspTagAdapter` for `html:*`, `bean:*`, `logic:*`, and `tiles:*` tag recognition.
- [x] JSP/Struts1 Tiles: static `tiles:insert definition` emits `USES_CONFIG`, and static JSP `tiles:put value/page` emits `INCLUDES`.
- [x] JSP/Struts1: static `html:link paramId` emits link parameter `WRITES_PARAM` facts to `_request` and static link targets.
- [x] JSP/Struts1: form parameter extraction covers `html:file`, `html:image`, and `html:reset` in addition to text/hidden/select/submit controls.
- [x] JSP/Struts1: `logic:redirect` emits `FORWARDS_TO`, while `logic:forward name` emits `USES_CONFIG` to the named Struts forward.
- [x] JSP/Struts1: `html:option`, `html:options`, and `html:optionsCollection` are parsed; dynamic option sources emit `USES_CONFIG`.
- [x] JSP/Struts1: `bean:write` and common `logic:*` condition tags emit JSP page `READS_PARAM` facts for bean/property references.
- [x] JSP: EL expressions emit conservative `READS_PARAM` facts for simple variables and property chains such as `${currentUser.email}`.
- [x] JSP: EL scope prefixes `pageScope/requestScope/sessionScope/applicationScope` are normalized away for bean/property read facts.
- [x] JSP/Spring: `form:form` and `form:* path` inputs are parsed, and `form:options items` emits option source `USES_CONFIG` facts.
- [x] Struts1/JSP: static include directive and static `jsp:include` create `JSP_PAGE -INCLUDES-> JSP_PAGE`.
- [x] Struts1/JSP: static `jsp:forward page` creates `JSP_PAGE -FORWARDS_TO-> JSP_PAGE/ACTION_PATH`.
- [x] Impact analysis: `INCLUDES` participates in JSP backend-flow query plans and fast upstream traversal.
- [x] Impact API: parse `symbolId` values for method, JSP, action path, request parameter, SQL/table/config, not only methods.
- [x] Impact API: included JSP changes can report parent JSP impact through `INCLUDES`.
- [x] Impact API: `/api/impact/analyze-diff` maps unified diff changed files to active evidence symbols and emits Fast Impact Report.
- [x] Impact Report: each path step now carries confidence, sourceType, and evidenceKeys in JSON/Markdown output.
- [x] Impact Report: JSON/Markdown affected symbol categories no longer guess Java layer names; Java roles must come from graph facts.
- [x] Impact Report: Fast report regression verifies tombstoned old snapshot relations do not leak into new paths/evidence/affected symbols.
- [x] Performance: added benchmark result contracts for Neo4j query templates and JVM primitive adjacency cache P95/heap estimates.
- [x] Performance: added benchmark-driven FFM activation policy requiring edge count, P95 latency, and heap estimate thresholds before recommending off-heap graph work.
- [x] Impact Report: added Deep Impact Report supplementer and `/api/impact/deep-supplement` to merge deeper active-fact paths/evidence into an existing Fast Report.
- [x] Performance: Fast Impact Report has a 30-second regression guard on a synthetic 250-edge chain.
- [x] Variable trace: sink query includes `READS_PARAM`, `BINDS_TO`, and `COVERED_BY` so Struts DynaActionForm and Validator destinations are visible.
- [x] Variable trace: added active-facts source/sink path reports with confidence, sourceTypes, qualifiers, and evidenceKeys for request parameters.
- [x] Variable trace: JSP sources now expand from request parameter to JSP input, form, and page through JSP-only `DECLARES` edges.
- [x] Variable trace: added combined `/api/variables/trace/report` endpoint and UI/query-plan default so source and sink paths load in one request.
- [x] Variable trace: JSON reports now include displayName, symbolKindLabel, directionLabel, parameterDisplayName, and endpointDisplayName for low-threshold UI/MCP/Agent rendering.
- [x] Variable trace: sink paths now continue through downstream `PASSES_PARAM` and SQL table effects after a Java method reads the request parameter.
- [x] Variable trace backend: Spoon emits `PASSES_PARAM` facts when a request-derived local variable or method parameter is passed as a method argument, reducing reliance on generic `CALLS` expansion.
- [x] Variable trace backend: method-local alias chains now propagate through local definitions and assignments, covering `request.getParameter -> a -> b -> c -> service.save(c)` and `methodParam -> alias -> dao.insert(alias)`.
- [x] Variable trace backend: alias propagation is evaluated in source order, so assignments after a call do not retroactively taint earlier arguments.
- [x] Variable trace backend: expression reads now propagate known sources through simple transformations such as `raw.trim()`, `String.valueOf(raw)`, concatenation, and direct expression arguments.
- [x] Variable trace backend: Struts ActionForm getter reads through ActionForm aliases now propagate as request parameter sources into downstream business method arguments.
- [x] Variable trace backend: Struts DynaActionForm `get("property")` / `getString("property")` reads through ActionForm aliases now propagate into downstream business method arguments.
- [x] Variable trace graph: request-derived argument flows also create caller `READS_PARAM` facts, so sink queries can start from the request parameter and reach Action -> Service paths.
- [x] Variable trace evidence now carries Java source file paths for request parameter reads/writes and method argument flows instead of `_unknown`.
- [x] MCP/Agent: added combined `variable.trace` read-only tool so AI workflows can fetch source and sink evidence in one step.
- [x] JSP flow: added active-facts backend-flow path reports from JSP_PAGE through Struts route, Java call, SQL/table, config, and evidence-carrying relations.
- [x] Sample: added Spring MVC fixture and project-level regression for Controller -> Service -> Repository -> Mapper -> SQL/table chain.
- [x] Security: MCP read-only tool planning now rejects raw Cypher/SQL/statement-style arguments even when the tool itself is allowed.
- [x] Java/Binary: added class/jar indexing so ordinary Java source calls can connect to business code packaged in WEB-INF/lib jars.
- [x] Java/Binary: class/jar analysis now extracts bytecode method invocation edges from `invokevirtual`, `invokespecial`, `invokestatic`, and `invokeinterface`, so business jar internals can join the call graph.
- [x] Java/Binary: method nodes now carry code-origin metadata (`source`, `jvm`, `source+jvm`) plus `sourceOnly`, `jvmOnly`, `synthetic`, and `bridge` flags for cross-engine symbol merging.
- [x] Java/Binary: classfile access flags now distinguish CLASS, INTERFACE, ENUM, and ANNOTATION nodes for business jars.
- [x] Java/Binary: classfile analysis now records class-level annotation names from runtime-visible and runtime-invisible annotation attributes for business jars.
- [x] Java/Binary: classfile analysis now emits field nodes and class -> field `DECLARES` facts for business jars.
- [x] Java/Binary/JPA: jar-contained entity classes can emit default `Entity -> table` and `field -> column` `BINDS_TO` facts from classfile annotations, skipping static and transient fields.
- [x] Java/Binary/JPA: classfile annotation string values are read for jar-contained `@Table(name/value)` and `@Column(name/value)` / `@JoinColumn(name)` mapping names.
- [x] Java/Binary/Spring: jar-contained Controller/RestController method annotations now emit API endpoint `ROUTES_TO` facts for common Spring mapping annotations.
- [x] Struts1/Java Binary: Struts action routing now uses jar-contained method nodes and binary inheritance facts, so config actions can route to Action#execute inside business jars.
- [x] Struts1/Java Binary: DispatchAction configured with `parameter="method"` can route to matching Struts-signature methods inside business jars.
- [x] Struts1/Java Binary: jar-contained ActionForm subclasses now bind non-static classfile fields to request parameters for variable trace entrypoints.
- [x] Struts1: standard action mappings now link action path to Action#execute, allowing JSP -> Action method -> business layer paths.
- [x] Struts1: action routing now follows indirect Action/DispatchAction/LookupDispatchAction inheritance chains, including inherited `execute` and inherited `getKeyMethodMap` mappings.
- [x] Struts1: LookupDispatchAction `getKeyMethodMap()` extraction now uses Spoon AST invocation/literal analysis instead of source regex.
- [x] Struts1: custom ActionMapping configuration is modeled from `<action-mappings type="...">` and per-action `className`, producing `USES_CONFIG` evidence to the custom mapping classes.
- [x] Struts1: Action `mapping.findForward("name")` calls now create method-to-forward config facts, and struts-config forward names link to configured JSP/action/tiles targets.
- [x] Struts1: direct `new ActionForward("/path.jsp")` and `new ActionForward("/path.do")` constructors now create method-to-JSP/action forward facts.
- [x] Struts1: named `new ActionForward("name", "/path.jsp", redirect)` constructors use the path argument, not the forward name, when creating navigation facts.
- [x] Struts1: `response.sendRedirect("/path.do")` and `new ActionRedirect("/path.jsp")` now create method-to-action/JSP navigation facts.
- [x] SQL: added JDBC SQL literal analysis for prepareStatement/executeQuery/executeUpdate/execute/addBatch, producing method -> SQL -> table facts.
- [x] SQL/JDBC: PreparedStatement setter calls such as `ps.setString(1, name)` now create `Method -PASSES_PARAM-> SqlStatement` facts with parameter index and variable name.
- [x] SQL/JPA: `@Entity/@Table/@Column` analysis now creates entity class -> table and field -> column `BINDS_TO` facts, skipping static and transient fields.
- [x] Struts1/plugin: plugin XML properties are resolved as initialization/import configuration sources with XML entries and possible table effects.
- [x] Java/common: stateless static support classes are classified as utility nodes by structure, and static utility calls are marked in the call graph.
- [x] UI: added business-friendly Chinese labels for relation types, symbols, evidence, confidence, risk, and default path display for non-system users.
- [x] UI: lowered entry barrier with common-question templates and collapsed advanced technical parameters by default.
- [x] UI: variable trace paths are grouped into "值从哪里来" and "值去了哪里", with friendly JSP input/form/page and request parameter names.
- [x] Query UX: added `/api/query/resolve` to turn business words such as request parameters, JSP paths, action paths, and table names into suggested executable symbols.
- [x] Rules: scattered Java naming heuristics were reduced by centralizing legacy layer conventions and removing name-based entrypoint/report classification.
- [x] Code quality: reduced long analyzer method signatures by introducing context records for Spoon Java and Spring bean analysis.
- [x] Code quality: Java annotation values for Spring MVC, Spring injection qualifiers, and MyBatis annotation SQL now use Spoon annotation value APIs instead of regex over annotation source text.
- [x] Security: Agent/MCP write-capable tool calls require explicit `confirmWrite=true` and `confirmationIntent=ALLOW_WRITE`; unconfirmed writes are denied before execution.

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
- [x] 使用 ASM/ClassGraph 快速扫描 class、annotation、继承、实现、资源文件。
- [x] 使用轻量 class/jar 索引建立业务 jar class/method 节点，后续再以 ASM/ClassGraph 增强 annotation 和字节码调用边。
- [x] 轻量 class/jar 索引识别 interface、enum、annotation 类型节点。
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
- [x] 提取构造器、静态初始化块、内部类、匿名类和 lambda 的稳定标识。
- [x] 标记 synthetic/bridge/source-only/jvm-only 方法。
- [x] 保留 JavaParser 作为可选快速扫描器，不作为主事实源。
- [x] 使用 JDT 或 Spoon 内部 JDT 能力作为绑定解析兜底。

## 5. JSP 分析

- [x] 接入 Apache Jasper。
- [x] 建立 WebAppContext 模型。
- [x] 解析 web root、WEB-INF/web.xml、Servlet/JSP API 版本和容器 profile。
- [x] 构建 JSP classpath：WEB-INF/classes、WEB-INF/lib、Maven/Gradle dependencies、编译输出目录。
- [x] 构建 TaglibRegistry：TLD 文件、jar 内 TLD、web.xml taglib 映射。
- [x] 实现 include resolver，支持静态 include、jsp:include、相对路径和 context path。
- [x] 实现 encoding resolver，支持 page directive、web.xml、BOM 和项目默认编码。
- [x] 解析 JSP directive：page、include、taglib。
- [x] 解析 JSP action：include、forward、param、useBean。
- [x] 解析 EL 表达式。
- [x] 解析 scriptlet 和 expression。
- [x] 解析 JSTL 常见标签。
- [x] 解析 Struts taglib：html:form、html:text、bean:write、logic:iterate。
- [x] 解析 Struts taglib MVP：html:form、html:text、html:hidden、html:password、html:checkbox、html:select。
- [x] 解析 Struts taglib 扩展：html:textarea、html:radio、html:multibox、bean:write、logic:iterate。
- [x] 记录 Struts JSP tag 语义：html:form -> SUBMITS_TO，html input property -> WRITES_PARAM；logic:iterate 暂不建模控制流。
- [x] 解析 Spring form tag。
- [x] 调研并接入 JetHTMLParser 或 Jericho 作为容错解析器。
- [x] 提取 form/action/input/select/textarea。
- [x] 建立 JSP input -> request parameter 关系。
- [x] 评估 Jasper 生成 Servlet + SMAP 回映射方案。
- [x] 明确不使用 jsoup 直接解析 JSP。
- [x] Jasper 失败时记录缺失上下文并降级到现有 tolerant JSP parser，相关关系标记为 `POSSIBLE` 或 `LIKELY`。

## 6. 框架适配

- [x] Spring MVC：解析 Controller、RequestMapping、Service、Repository、Autowired。
- [x] Spring MVC MVP：解析 Controller/RestController 与 RequestMapping/GetMapping/PostMapping 等入口边。
- [x] Spring Bean：解析构造注入、字段注入、Resource、Qualifier。
- [x] Spring 任务：识别 Scheduled、Async，后续接入事件/MQ。
- [x] Struts1：解析 struts-config.xml。
- [x] Struts1：建立 path -> Action -> ActionForm -> Forward。
- [x] Struts1：建立 JSP form action -> Action path。
- [x] Struts1：解析 web.xml ActionServlet 的 config 和 config/<module-prefix>，并将 module prefix 合并到 Action path。
- [x] Struts1：ActionServlet 未声明 config 时按 `/WEB-INF/struts-config.xml` 默认配置处理。
- [x] Struts1：支持同一 struts-config 被多个 config/<module-prefix> 引用并分别生成模块化 Action path。
- [x] Struts1：支持没有 type 的 forward-only action，并将 action forward 建成 FORWARDS_TO。
- [x] Struts1：module 内 local/global forward 和 exception 的 .do 目标继承当前 module prefix。
- [x] Struts1：DispatchAction parameter 存在时，将 action path 连接到符合 Struts 方法签名的候选 METHOD，置信度为 LIKELY。
- [x] Struts1：解析 LookupDispatchAction#getKeyMethodMap() 的简单 Map.put 字面量映射，输出 keyed METHOD 路由候选。
- [x] Struts1/JSP：静态 html:link action/page 生成 JSP_PAGE 到 ACTION_PATH/JSP_PAGE 的 FORWARDS_TO。
- [x] Struts1/JSP：logic:redirect 生成 JSP_PAGE 到 ACTION_PATH/JSP_PAGE 的 FORWARDS_TO。
- [x] Struts1/JSP：logic:forward name 生成 JSP_PAGE 到 Struts forward 配置名的 USES_CONFIG。
- [x] Struts1/JSP：html:options/optionsCollection 的 collection/name/property 生成 JSP_PAGE 到 option source 的 USES_CONFIG。
- [x] Struts1/JSP：bean:write 和 logic 条件标签的 name/property 生成 JSP_PAGE 到 bean/property 引用的 READS_PARAM。
- [x] Struts1/JSP：静态 html:link paramId 生成 JSP link 参数到 request parameter 和目标 action/JSP 的 WRITES_PARAM。
- [x] Struts1/JSP：html:submit/html:cancel/html:button/html:image/html:reset 的 property 生成 JSP_INPUT 和 WRITES_PARAM，用于 DispatchAction 参数追踪。
- [x] Struts1：解析 plug-in、set-property、TilesPlugin、ValidatorPlugIn 等插件配置。
- [x] Struts1：解析自定义 plug-in 读取的 XML 初始化配置，并将 XML 条目建模为 CONFIG_KEY。
- [x] Struts1：解析 controller、processorClass、multipartClass、inputForward、maxFileSize 等控制器配置。
- [x] Struts1：解析自定义 ActionMapping：`action-mappings type` 和单个 `action className`。
- [x] Struts1：解析 global-forwards、message-resources、global/action exception。
- [x] Struts1：解析 DynaActionForm、form-property，并建立 request parameter 绑定。
- [x] Struts1：解析 Tiles definitions、definition extends、put value JSP。
- [x] Struts1：解析 Validator XML、field depends，并建立 request parameter 覆盖关系。
- [x] Struts1：将 plug-in、controller、message-resources、exception、Tiles、Validator 映射到 CONFIG_KEY 关系，保留 evidence 和 confidence。
- [x] Seasar2：解析 dicon component。
- [x] Seasar2：识别命名约定 binding。
- [x] Seasar2：识别 service/dao/interceptor 基础关系。
- [x] Seasar2：识别 dicon include、property injection、aspect/interceptor 候选配置关系，置信度保持 `POSSIBLE`。
- [x] Seasar2 MVP 只输出 discovery/candidate 结果，确定性影响链路放到增强阶段。
- [x] 为所有框架关系标注 confidence 和 evidence。

## 7. SQL 与数据访问

- [x] 解析 MyBatis Mapper interface。
- [x] 解析 MyBatis XML statement id。
- [x] 解析 JDBC 直写 SQL 字面量和常量拼接。
- [x] 使用 JSqlParser 解析 SQL。
- [x] 提取 table、column、where condition、read/write 类型。
- [x] 提取 SQL table 和 read/write 类型的 MVP 候选结果。
- [x] 建立 Mapper method -> SqlStatement 关系。
- [x] 建立 SqlStatement -> DbTable/DbColumn 关系。
- [x] 建立 SqlStatement -> DbTable 关系。
- [x] 建立 JDBC method -> SqlStatement -> DbTable 关系。
- [x] 建立 JDBC PreparedStatement 参数绑定关系：method -> SqlStatement，qualifier 包含参数位置和变量名。
- [x] 确保 MVP 最小链路包含 Mapper 方法 `Method -[:BINDS_TO]-> SqlStatement`。
- [x] 支持动态 SQL 的保守解析和 `POSSIBLE` 标注。
- [x] 支持 JDBC 部分动态拼接 SQL 的保守解析和 `POSSIBLE` 标注。
- [x] 后续支持 JPA Entity -> table 映射。

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
- [x] 追踪方法内局部变量别名链，例如 `a = request.getParameter("x"); b = a; c = b; service.save(c)`。
- [x] 识别 `request.getParameter`、`getAttribute`、`setAttribute`。
- [x] 识别 ActionForm 字段绑定。
- [x] 识别 getter/setter 简单传播。
- [x] 实现 Controller/Action -> Service -> DAO 参数传播。
- [x] 实现 JSP input -> request parameter -> Java parameter 链路。
- [x] 输出变量来源和流向证据路径。
- [x] 变量追踪证据包含 Java 文件路径和行号，便于定位变量在各文件中的位置。

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
- [x] 关联受影响 JSP、API、Action、Controller、Service、DAO、Mapper、SQL、table。
- [x] 为每条影响路径计算 confidence。
- [x] 输出 Fast Impact Report。
- [x] 输出 Deep Impact Report 增量补充。
- [x] 支持 Markdown/JSON 报告格式。
- [x] 支持“为什么受影响”的路径解释。
- [x] 验证删除关系后不会从旧 snapshot 残留到新报告。

## 10. Tai-e 深度分析（增强阶段）

- [x] 完成 Tai-e license review。
- [x] 用样例项目验证 Tai-e 输入 classpath 和输出 signature 可映射性。
- [x] 建立 Tai-e worker 独立 JVM。
- [x] 准备 classpath 和 compiled classes 输入。
- [x] 配置 call graph analysis。
- [x] 配置 pointer analysis。
- [x] 配置 taint analysis source/sink。
- [x] 实现 Tai-e signature -> CodeAtlas symbolId 映射。
- [x] 将 Tai-e 分析结果导入 Neo4j。
- [x] 给 Tai-e worker 设置超时、内存上限和失败降级。
- [x] 在报告中区分 Spoon 事实和 Tai-e 深度补强。
- [x] 确保 Tai-e 失败不影响 MVP 影响报告。

## 11. FFM OffHeapGraphIndex（benchmark 驱动增强阶段）

- [x] 建立 Neo4j 查询 P95 耗时 benchmark。
- [x] 建立 JVM primitive adjacency cache heap 占用 benchmark。
- [x] 定义启用 FFM 的规模阈值：edge 数、P95 查询耗时、heap 压力。
- [x] 设计 CSR/CSC 数据格式。
- [x] 设计 node id 和 edge id 压缩编码。
- [x] 使用 `MemorySegment` 存储 offsets、targets、edgeTypes。
- [x] 使用 `Arena.ofConfined()` 管理任务级临时内存。
- [x] 使用 `Arena.ofShared()` 管理只读共享索引。
- [x] 使用 `FileChannel.map(..., Arena)` 支持 mmap 持久缓存。
- [x] 实现 caller/callee 查询。
- [x] 实现多跳 BFS。
- [x] 实现 visited bitmap 和 frontier queue。
- [x] 与 Impact Analysis Engine 集成。
- [x] 增加 heap 使用和查询耗时 benchmark。
- [x] 只有 benchmark 证明收益后，才将 FFM 接入默认影响分析路径。

## 12. RAG 与向量检索

- [x] 设计 MethodSummary、ClassSummary、JspPageSummary、SqlStatementSummary、ImpactReportSummary。
- [x] 接入 embedding provider。
- [x] 首版使用 Neo4j Vector Index。
- [x] 实现精确符号检索 + 向量召回 + 图事实扩展（MVP 内存契约，后续接 Neo4j Vector Index）。
- [x] 实现源码/JSP/XML/SQL evidence pack 构建。
- [x] 支持自然语言代码问答的检索规划与证据召回（答案生成继续走 Agent/AI 增强）。
- [x] 支持相似代码和历史报告召回（MVP 内存契约）。
- [x] 评估 pgvector、OpenSearch、Qdrant 作为后续扩展。

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

- [x] 实现 Tool Registry。
- [x] 实现 ImpactAnalysisAgent。
- [x] 实现 VariableTraceAgent。
- [x] 实现 CodeQuestionAgent。
- [x] 定义工具调用步数限制、超时、审计日志。
- [x] 禁止 Agent 调用任意 Cypher。
- [x] 禁止 Agent 任意读取文件。
- [x] 让 Agent 只调用封装后的内部工具。
- [x] 所有 Agent 结果输出 evidence、confidence、sourceType。

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
- [x] 暴露 `rag.answerDraft`。
- [x] 暴露 `report.getImpactReport`。
- [x] 实现 MCP Resources：symbol、jsp、table、report。
- [x] 实现 MCP Prompts：impact-review、variable-trace、jsp-flow-analysis、code-question、test-recommendation。
- [x] 增加 project 权限、限流、脱敏、审计。

## 16. UI 与报告

- [x] 使用 React + TypeScript + Vite 初始化 `codeatlas-ui`。
- [x] 配置前端 Gradle 集成任务，支持统一 build。
- [x] 选择企业级组件库，默认 Ant Design 或同类方案。
- [x] 接入 Cytoscape.js 用于代码图谱探索。
- [x] 接入 React Flow 或同类库用于影响路径/变量流线性展示。
- [x] 项目总览页。
- [x] 变更影响报告页。
- [x] 方法调用链查询页。
- [x] 变量来源/流向查询页。
- [x] JSP 到后端链路查询页。
- [x] SQL/table 影响查询页。
- [x] 全局问答式搜索框，支持自然语言输入。
- [x] 意图识别结果展示：symbol search、caller/callee、变量来源/流向、影响分析、JSP 链路、SQL/table 影响。
- [x] 候选符号选择器：类型、qualified name/path、文件、行号、模块、最近变更信息。
- [x] 答案摘要区：确定路径数、可能路径数、主要来源/流向/影响点、风险等级、后台分析状态。
- [x] 纵向证据路径视图：每条边展示 edge type、confidence、sourceType。
- [x] 证据列表表格：文件、行号、证据类型、证据片段、分析器来源。
- [x] AI 风险摘要展示。
- [x] 证据路径展示。
- [x] 置信度标识。
- [x] 后台深度分析状态展示。
- [x] Graph Explorer 支持 caller/callee、入口到 SQL 链路、节点展开和深度限制。
- [x] Impact Report 支持风险等级、建议测试、AI 摘要、证据路径。
- [x] Variable Trace View 支持来源/流向双向切换。
- [x] JSP Flow View 支持 JSP -> Action/Controller -> Service -> DAO/Mapper -> SQL/table。
- [x] Symbol Search 支持 class/method/JSP/SQL/table/config 统一搜索。
- [x] AI Q&A 页面要求答案旁展示证据节点和路径。
- [x] UI 对所有大图查询展示 `truncated` 状态和继续展开入口。
- [x] AI 摘要旁显示“基于 N 条静态分析证据生成”。
- [x] 提供原始 JSON 明细查看入口。

## 17. 测试与样例工程

- [x] 创建 Spring MVC 样例项目。
- [x] 创建 Struts1 + JSP 样例项目。
- [x] 样例覆盖 Struts1 module config、多 struts-config、plug-in、controller、DynaActionForm、Tiles、Validator、JSP tag。
- [x] 创建 Seasar2 dicon 样例片段。
- [x] 创建 MyBatis XML 样例。
- [x] 创建 JSP form -> Action -> Service -> Mapper -> SQL 样例链路。
- [x] 测试 Java 方法调用解析。
- [x] 测试 JSP input 到 request parameter。
- [x] 测试 ActionForm 字段绑定。
- [x] 测试 SQL table/column 识别。
- [x] 测试 Git diff 影响报告。
- [x] 测试问答式查询存在多个候选时先显示候选列表。
- [x] 测试变量追踪结果按摘要、路径、证据、明细展示。
- [x] 测试 AI 关闭时结构化结果仍能展示。
- [x] 测试 tombstone 后旧边不会出现在 current snapshot 查询中。
- [x] 测试同一 fact 多 evidence 合并和 confidence 聚合。
- [x] 测试 AI 关闭和开启两种模式。
- [x] 测试 Neo4j 查询性能。
- [x] 测试 JVM primitive adjacency cache 内存和速度。
- [x] 增强阶段测试 FFM 图索引内存和速度。

## 18. 安全与治理

- [x] 源码片段脱敏。
- [x] API key 加密存储。
- [x] AI 请求日志脱敏。
- [x] MCP 工具白名单。
- [x] 禁止任意数据库查询暴露。
- [x] 禁止无确认写操作。
- [x] 项目级访问控制。
- [x] 审计 Agent 和 MCP 调用。
- [x] 标记 AI_ASSISTED 结果，禁止混淆为确定事实。

## 19. MVP 验收清单

- [x] 能扫描一个混合 Java Web 项目。
- [x] 能识别 Spring Controller 和 Struts1 Action。
- [x] 能解析 JSP form/input/action。
- [x] 能解析 Struts1 module prefix、plug-in、controller、Tiles、Validator、DynaActionForm 和 JSP tag 语义。
- [x] 能解析 Java class/method/call。
- [x] 能解析 MyBatis XML 和 SQL table。
- [x] 能写入 Neo4j 图谱。
- [x] 能稳定生成 symbolId、factKey、evidenceKey。
- [x] 能处理 snapshot/tombstone，避免旧关系残留。
- [x] 能打通 Spring RequestMapping、Struts action、JSP form/action、method direct call、MyBatis statement 五类最小边。
- [x] Seasar2 只要求 dicon/component discovery，不要求确定性影响链路。
- [x] 能从 Git diff 输出影响报告。
- [x] 能通过 REST API 查询 symbol、impact report、path query、variable trace、jsp flow。
- [x] 能通过问答式搜索触发符号检索、变量追踪、影响分析、JSP 链路、SQL/table 影响。
- [x] 查询结果能按答案摘要、证据路径、证据列表、图谱与明细展示。
- [x] 查询结果默认以业务友好文案展示，原始 symbolId/JSON 作为下钻细节保留。
- [x] 能展示 JSP -> Action/Controller -> Service -> DAO/SQL/table 路径。
- [x] 能追踪 request parameter 的来源和流向。
- [x] 能给每条影响路径标注 evidence 和 confidence。
- [x] 能在 10 到 30 秒输出初版报告。
- [x] 能用 AI 生成风险摘要和测试建议。
- [x] 能通过 MCP 只读查询核心分析能力。
- [x] 项目使用 Java 25 + Gradle 构建。
- [x] 能通过可视化前端查看项目概览、影响报告、调用路径、变量流和 JSP 链路。
- [x] Tai-e 和 FFM 不作为 MVP 验收前置条件。
