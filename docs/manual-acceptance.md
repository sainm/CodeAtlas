# CodeAtlas Current Manual Acceptance Checklist

本文档用于现阶段人工验收。目标不是验收最终完整产品，而是确认当前 MVP 基线已经具备可构建、可分析、可查询、可展示的核心能力。

## 0. 验收前提

- [ ] 本地已安装 JDK 25，并且 `java -version` 可见。
- [ ] 当前目录为 `D:\source\CodeAtlas`。
- [ ] 当前分支为 `main`。
- [ ] `git status --short` 输出为空，或只包含本次验收文档相关变更。
- [ ] 可以访问 Gradle Wrapper：`.\gradlew.bat --version`。

## 1. 构建与测试

执行：

```powershell
.\gradlew.bat build --no-daemon
```

验收点：

- [ ] 构建结果为 `BUILD SUCCESSFUL`。
- [ ] `codeatlas-graph` 测试通过。
- [ ] `codeatlas-analyzers` 测试通过。
- [ ] `codeatlas-ai` 测试通过。
- [ ] `codeatlas-mcp` 测试通过。
- [ ] `codeatlas-server` 测试通过。
- [ ] `codeatlas-worker` 测试通过。
- [ ] `codeatlas-ui:npmBuild` 通过。

## 2. 工程结构

验收点：

- [ ] 根工程使用 Gradle multi-project。
- [ ] `settings.gradle` 包含以下模块：
  - `codeatlas-graph`
  - `codeatlas-analyzers`
  - `codeatlas-ai`
  - `codeatlas-mcp`
  - `codeatlas-server`
  - `codeatlas-worker`
  - `codeatlas-ui`
- [ ] Java group/package 使用 `org.sainm.codeatlas`。
- [ ] `docs/design.md`、`docs/plan.md`、`docs/task.md` 存在。
- [ ] `docs/license-review.md` 存在。
- [ ] `.gitignore` 已忽略 build、node_modules、dist、IDE、本地 env、日志等文件。

## 3. 图谱模型与 Neo4j 查询

验收点：

- [ ] `SymbolId` 能稳定表达 Project、Module、File、Class、Method、Field、JSP、SQL、Table、Column、RequestParameter。
- [ ] `FactKey` 和 `EvidenceKey` 具备稳定 value。
- [ ] `GraphFact` 包含 `confidence`、`sourceType`、`snapshotId`、`analysisRunId`、`scopeKey`。
- [ ] 同一 fact 支持多 evidence 合并。
- [ ] snapshot/tombstone 能隔离旧关系。
- [ ] AI 辅助事实不能标记为 `CERTAIN`。
- [ ] Neo4j schema 能生成 labels、relationship types、constraints、indexes。
- [ ] Neo4j caller/callee query plan 能生成 Cypher。
- [ ] Neo4j impact path query plan 包含 `CALLS`、`IMPLEMENTS`、`INJECTS`、`BRIDGES_TO`、`ROUTES_TO`、`SUBMITS_TO`、`BINDS_TO`、`READS_TABLE`、`WRITES_TABLE`。

## 4. Java 源码分析

验收点：

- [ ] Spoon noClasspath 模式可解析 Java 25 源码。
- [ ] 可提取 class、interface、enum、annotation。
- [ ] 可提取 method、constructor、field。
- [ ] 可提取 direct method invocation。
- [ ] 可提取 extends/implements。
- [ ] 可输出 line number、file path、method signature。
- [ ] 可生成统一 `symbolId`。
- [ ] 可建立接口方法到实现方法候选关系。

## 5. Spring 分析

验收点：

- [ ] 可识别 `@Controller`、`@RestController`。
- [ ] 可识别 `@RequestMapping`、`@GetMapping`、`@PostMapping` 等入口。
- [ ] 可生成 API endpoint -> handler method 的 `ROUTES_TO` 关系。
- [ ] 可识别 `@Service`、`@Repository`、`@Component`。
- [ ] 可识别字段注入：`@Autowired`、`@Resource`、`@Inject`。
- [ ] 可识别构造器注入。
- [ ] 可提取 `@Qualifier`、`@Resource(name=...)`、`@Named` 的 qualifier。
- [ ] 可生成 class -> dependency class 的 `INJECTS` 关系。
- [ ] Spring 关系都带 `confidence` 和 `evidence`。

## 6. Struts1 与 Seasar2 分析

Struts1 验收点：

- [ ] 可解析 `struts-config.xml`。
- [ ] 可建立 Action path -> Action class 的 `ROUTES_TO` 关系。
- [ ] 可建立 Action path -> ActionForm 的 `BINDS_TO` 关系。
- [ ] 可建立 Action path -> Forward JSP 的 `FORWARDS_TO` 关系。
- [ ] 可建立 JSP form action -> Action path 的 `SUBMITS_TO` 关系。
- [ ] 可识别 ActionForm 字段绑定为 request parameter -> field。

Seasar2 验收点：

- [ ] 可解析 `.dicon` component。
- [ ] 可输出 service、dao、interceptor 的候选关系。
- [ ] Seasar2 关系按 MVP 规则标为 discovery/candidate，不作为确定性深度影响链路。

## 7. JSP 分析

验收点：

- [ ] 已建立 `WebAppContext` 模型。
- [ ] 可提取 JSP directive：`page`、`include`、`taglib`。
- [ ] 可提取 JSP action：`jsp:include`、`jsp:forward`、`jsp:param`、`jsp:useBean`。
- [ ] 可提取 EL 表达式。
- [ ] 可提取 scriptlet 和 expression。
- [ ] 可提取 JSTL 常见标签。
- [ ] 可提取 HTML form/action/input/select/textarea。
- [ ] 可提取 Struts taglib MVP：`html:form`、`html:text`、`html:hidden`、`html:password`、`html:checkbox`、`html:select`。
- [ ] 可提取 Struts taglib 扩展：`html:textarea`、`html:radio`、`html:multibox`、`bean:write`、`logic:iterate`。
- [ ] 可建立 JSP input -> request parameter 关系。
- [ ] include resolver 支持静态 include、`jsp:include`、相对路径、context path。
- [ ] encoding resolver 支持 BOM、`pageEncoding`、`contentType charset`、项目默认编码。
- [ ] 明确不使用 jsoup 直接解析 JSP。

## 8. MyBatis 与 SQL 分析

验收点：

- [ ] 可解析 MyBatis XML mapper namespace。
- [ ] 可解析 XML statement id：`select`、`insert`、`update`、`delete`。
- [ ] 可解析 MyBatis Mapper interface。
- [ ] 可建立 Mapper method -> SQL statement 的 `BINDS_TO` 关系。
- [ ] 可建立 Java 精确方法 symbol -> XML statement `_unknown` 方法 symbol 的 `BRIDGES_TO` 关系。
- [ ] 可解析 MyBatis 注解 SQL：`@Select`、`@Insert`、`@Update`、`@Delete`。
- [ ] 可提取 table read/write。
- [ ] 可提取 column MVP，包括 select columns、insert columns、update set columns、where columns。
- [ ] 可建立 SQL statement -> DbTable/DbColumn 的 `READS_TABLE` 或 `WRITES_TABLE` 关系。
- [ ] 动态 SQL 的相关关系降为 `POSSIBLE`。

## 9. 变量追踪

验收点：

- [ ] 可提取方法参数事件。
- [ ] 可提取局部变量定义事件。
- [ ] 可提取赋值事件。
- [ ] 可提取变量 read/write 事件。
- [ ] 可提取 return 来源。
- [ ] 可识别 `request.getParameter`。
- [ ] 可识别 `request.getAttribute`。
- [ ] 可识别 `request.setAttribute`。
- [ ] 可识别 getter 简单传播：`getX() -> return x`。
- [ ] 可识别 setter 简单传播：`setX(v) -> this.x = v`。
- [ ] 可建立 JSP input -> request parameter -> Java method 的基础链路。
- [ ] 可建立 ActionForm field 与 request parameter 的绑定候选。

## 10. Git Diff 与影响分析

验收点：

- [ ] JGit 可读取 branch、commit、changed files、diff。
- [ ] unified diff parser 可定位 changed files。
- [ ] changed file 可映射 candidate symbols。
- [ ] 可从 changed symbol 反查 caller。
- [ ] 可从入口正查下游 callee。
- [ ] 可使用 Neo4j query plan 和 JVM adjacency cache 进行影响路径查询。
- [ ] Fast Impact Report 可输出 JSON。
- [ ] Fast Impact Report 可输出 Markdown。
- [ ] 每条影响路径包含 confidence。
- [ ] 每条影响路径可以解释“为什么受影响”。
- [ ] REST API 可触发基础 impact analyze。

## 11. REST API 手工验收

启动后端服务或使用现有测试中的 HTTP server 构造方式。

建议人工检查 endpoint：

- [ ] `GET /health` 返回 `{"status":"ok"}`。
- [ ] `GET /api/symbols/search?q=save` 返回 symbol search JSON。
- [ ] `GET /api/reports/{reportId}` 返回 impact report JSON。
- [ ] `GET /api/graph/callers?...` 返回 caller Cypher query plan。
- [ ] `GET /api/graph/callees?...` 返回 callee Cypher query plan。
- [ ] `GET /api/graph/impact-paths/query?...` 返回 impact path Cypher query plan。
- [ ] `GET /api/impact/analyze?...` 返回 impact report JSON。
- [ ] `GET /api/variables/trace-source?...` 返回 `WRITES_PARAM` query plan。
- [ ] `GET /api/variables/trace-sink?...` 返回 `READS_PARAM` query plan。
- [ ] `GET /api/jsp/backend-flow/query?...` 返回 JSP backend flow query plan。

## 12. MCP 手工验收

验收点：

- [ ] 只读 MCP server 能初始化 read-only capabilities。
- [ ] MCP tool registry 暴露：
  - `symbol.search`
  - `graph.findCallers`
  - `graph.findCallees`
  - `graph.findImpactPaths`
  - `variable.traceSource`
  - `variable.traceSink`
  - `jsp.findBackendFlow`
  - `impact.analyzeDiff`
  - `rag.semanticSearch`
  - `report.getImpactReport`
- [ ] MCP resources 暴露：`symbol`、`jsp`、`table`、`report`。
- [ ] MCP prompts 暴露：`impact-review`、`variable-trace`、`jsp-flow-analysis`、`test-recommendation`。
- [ ] MCP 工具白名单生效。
- [ ] MCP 不暴露任意 Cypher 执行。
- [ ] MCP 不暴露写操作。

## 13. AI 能力手工验收

验收点：

- [ ] AI provider 抽象存在。
- [ ] 支持系统级 AI 配置：provider、baseUrl、apiKey、model、embeddingModel、timeout。
- [ ] 支持项目级 AI 开关和源码片段发送策略。
- [ ] 源码和配置片段可脱敏。
- [ ] 可生成 PR 影响摘要 prompt。
- [ ] 可生成风险解释 prompt。
- [ ] 可生成测试建议 prompt。
- [ ] 可生成调用路径解释 prompt。
- [ ] AI 关闭或失败时，系统仍能返回纯静态分析结构化结果。
- [ ] AI 输出必须引用 evidence path。
- [ ] AI 辅助结果不得标记为确定事实。

## 14. 前端 UI 手工验收

启动：

```powershell
cd D:\source\CodeAtlas\codeatlas-ui
npm run dev -- --host 127.0.0.1 --port 5173
```

打开：

```text
http://127.0.0.1:5173
```

验收点：

- [ ] 页面能正常打开，无白屏。
- [ ] 页面中文显示正常，无乱码。
- [ ] 左侧包含：影响报告、变量追踪、JSP 链路、图谱探索、SQL/Table。
- [ ] 顶部有问答式搜索框。
- [ ] 切换左侧视图时，标题、说明和查询计划会变化。
- [ ] 页面展示证据路径。
- [ ] 页面展示证据列表。
- [ ] 证据列表展示 type、file、line、confidence、evidence text。
- [ ] 页面展示 certainty/likely/possible 等置信度标签。
- [ ] 页面展示查询指标：确定路径、可能路径、P95 查询、截断状态。
- [ ] 页面在窄屏下布局不明显重叠。

## 15. 文档与任务记忆

验收点：

- [ ] `docs/design.md` 描述整体设计、架构、图谱模型、AI 位置、JSP/框架支持、性能策略。
- [ ] `docs/plan.md` 描述分阶段路线和 MVP 顺序。
- [ ] `docs/task.md` 是当前任务记忆清单。
- [ ] `docs/task.md` 已勾选项与当前代码能力基本一致。
- [ ] Tai-e、FFM、Jasper、JetHTMLParser/Jericho、JSqlParser 等增强项没有被误作为 MVP 必选验收项。
- [ ] `samples/struts1-jsp` 存在，并覆盖 web.xml ActionServlet、多 struts-config、JSP form、ActionForm、Action、Service、Mapper、MyBatis XML。
- [ ] `samples/struts1-jsp` 覆盖 Struts plug-in：TilesPlugin、ValidatorPlugIn、`set-property`。
- [ ] `samples/struts1-jsp` 覆盖 Struts controller：`processorClass`、`inputForward`、`maxFileSize`。

## 16. 现阶段不验收范围

以下内容属于增强阶段或后续集成阶段，当前不作为通过/失败标准：

- [ ] Apache Jasper 真正接入和 JSP -> Servlet + SMAP 回映射。
- [ ] JetHTMLParser 或 Jericho 真实接入。
- [ ] Tai-e 深度调用图、指针分析、taint analysis。
- [ ] FFM off-heap graph index。
- [ ] JSqlParser 完整 SQL AST 解析。
- [ ] Neo4j Vector Index / embedding provider / 完整 RAG。
- [ ] Agent 真实多步编排执行。
- [ ] Cytoscape.js / React Flow 真实图形化交互。
- [ ] 企业级组件库替换。
- [ ] 完整权限、限流、审计、API key 加密存储。

## 17. 验收结论

- [ ] 通过：当前基线可作为 CodeAtlas MVP 继续开发基础。
- [ ] 有条件通过：核心构建通过，但存在需记录的验收问题。
- [ ] 不通过：构建失败或核心链路不可用。

验收人：

验收日期：

问题记录：

- 
