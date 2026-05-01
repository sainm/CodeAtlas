# CodeAtlas Design

## 1. 项目定位

CodeAtlas 是一个面向 Java 企业项目的静态分析与变更影响分析平台。核心目标不是做一个普通代码扫描器，而是用尽量智能、尽量快速的方式回答开发者每天最关心的问题：

- 这里是谁调用的？
- 这个变量从哪里来、到哪里去？
- 我改这里会影响什么？
- 这次变更风险在哪里，应该测什么？

平台重点面向老旧 Java Web 系统和新旧混合系统。完整蓝图覆盖 Spring、Struts1、Seasar2、JSP、MyBatis/SQL、Git diff、调用关系、变量追踪和 AI 辅助解释；MVP 必须先闭环 Spring/Struts1/JSP/MyBatis 的最小影响链路，Seasar2 先做 dicon/component 发现和候选关系，不作为 MVP 影响报告阻塞项。

设计原则：

- 静态分析和图谱产生事实。
- AI 负责解释、总结、辅助检索和编排，不直接生成最终事实。
- 先快后深，先给初版影响报告，再后台补充深度变量流和数据流。
- 所有结论必须带证据路径、置信度和来源。

## 2. 总体架构

```text
Git / Java / JSP / XML / SQL / Config
  -> Ingestion & Fast Index
  -> Source Analysis
  -> Framework Adapters
  -> Neo4j Code Knowledge Graph
  -> JVM InMemoryGraphCache
  -> Code Graph RAG
  -> Agent Orchestrator
  -> UI / Report / REST / MCP

Benchmark-driven Enhancement:
  -> Tai-e Bytecode & Dataflow Analysis
  -> FFM OffHeapGraphIndex
```

核心分层：

- Ingestion & Fast Index：负责项目导入、Git diff、文件 hash、Maven/Gradle 模块、classpath、快速符号索引。
- Source Analysis：以 Spoon 为主，解析 Java 源码结构、类、方法、字段、注解、源码行号。
- Bytecode & Dataflow Analysis：以 Tai-e 为增强引擎，负责高级调用图、指针分析、多态、跨方法数据流和 taint analysis，不进入 MVP 关键路径。
- Framework Adapters：适配 Spring、Struts1、Seasar2、JSP、MyBatis、JPA、配置文件和旧框架约定。
- Neo4j Code Knowledge Graph：代码事实图谱主存储，保存节点、关系、证据、置信度。
- JVM InMemoryGraphCache：MVP 阶段的调用边邻接表缓存，服务快速 caller/callee 和影响路径搜索。
- FFM OffHeapGraphIndex：benchmark 证明 Neo4j/JVM 内存缓存不足后再启用的 JVM 外内存高速索引。
- Code Graph RAG：Neo4j 图谱、向量召回、符号检索和源码证据结合。
- Agent Orchestrator：受控编排工具，不直接判断事实。
- MCP Extension Layer：对 IDE、外部 AI Agent 和工具开放标准能力。
- Visual Frontend：面向开发者的图谱、链路、报告和问答可视化界面。

## 3. 开源基础选型

首选组合：

```text
MVP Core: Spoon + Apache Jasper + JetHTMLParser/Jericho + JSqlParser + Neo4j + JVM InMemoryGraphCache
MVP Intelligence/Interface: Code Graph RAG + Agent + MCP
Benchmark Enhancement: Tai-e + FFM OffHeapGraphIndex
```

详细选型：

| 能力 | 主选 | 备选/辅助 | 用途 |
| --- | --- | --- | --- |
| Java 源码 AST | Spoon | JavaParser、Eclipse JDT Core | Spoon 做主源码模型，JavaParser 做轻量快速扫描，JDT 做绑定/编译兜底 |
| 高级调用图/数据流 | Tai-e | WALA、SootUp | 指针分析、多态、跨方法数据流、taint analysis，增强阶段接入 |
| 字节码轻量扫描 | ASM、ClassGraph |  | 快速 classpath、注解、继承实现、jar 元数据扫描 |
| JSP 解析 | Apache Jasper | JetHTMLParser / Jericho | Jasper 负责 JSP 语义，辅助解析器用于容错提取 form/input/tag |
| SQL 解析 | JSqlParser |  | 解析 SQL 表、字段、where 条件和读写类型 |
| Git diff | JGit | 原生 git 命令 | 读取 PR、commit、diff、历史变更 |
| 图数据库 | Neo4j | Apache AGE 可后续评估 | 代码知识图谱、路径查询、可视化 |
| JVM 内存缓存 | primitive adjacency cache |  | MVP 阶段 caller/callee 和影响路径加速 |
| JVM 外内存 | JDK FFM API | mmap、RocksDB 可辅助 | benchmark 驱动增强，处理超大调用边、bitmap、frontier queue、临时索引 |
| 自动迁移/修复 | OpenRewrite |  | 后期做旧框架迁移和规则修复 |
| 向量/RAG | Neo4j Vector Index | pgvector、OpenSearch、Qdrant | 语义搜索和 Code Graph RAG |
| 外部工具协议 | MCP | REST API | 对 AI/IDE/Agent 标准开放能力 |
| 可视化前端 | React + TypeScript + Vite | Cytoscape.js、React Flow、Ant Design | 项目总览、影响报告、调用图、变量流、JSP 链路和 AI 问答 |

重要修正：

- JSP 不直接使用 jsoup 解析。jsoup 适合 HTML，不适合原始 JSP。
- Spoon 应作为 Java 源码分析主引擎，而不是只用 JavaParser。
- Tai-e 可以优先于 WALA/SootUp 作为高级静态分析引擎。
- MVP 不把 Tai-e 和 FFM 放入关键路径，先用 Spoon/Jasper/JSqlParser/Neo4j 打通业务影响闭环。
- Neo4j 是代码图谱主存储，JVM 缓存和 FFM 都是高速旁路索引，不是事实源。

## 4. Neo4j 图谱模型

Neo4j 是 CodeAtlas 的代码知识图谱主存储。

典型节点：

```text
Project
Module
SourceFile
Package
Class
Interface
Method
Field
Annotation
Bean
ApiEndpoint
ActionPath
JspPage
JspForm
JspInput
Action
Controller
Service
Dao
Mapper
SqlStatement
DbTable
DbColumn
ConfigKey
GitCommit
PullRequest
TestCase
```

节点建模规则：

- `Class`、`Interface`、`Method` 是 Java 代码的事实节点。
- `Controller`、`Service`、`Dao` 优先作为 `Class` 或 `Method` 的 role label/property，不创建与 `Class` 重复的独立节点。
- Struts1 `Action` 表示框架 action mapping 或 action class role；实际 Java 调用仍落到 `Method`。
- Spring handler 使用 `ApiEndpoint -[:ROUTES_TO]-> Method` 表示，目标方法保留 Java `Method` 节点。
- JSP 表单使用 `JspForm -[:SUBMITS_TO]-> ApiEndpoint/ActionPath` 表示，不直接跳到 Java 方法，避免路由解析和页面解析耦合。
- MyBatis 使用 `Method -[:BINDS_TO]-> SqlStatement` 表示 Mapper 方法到 SQL statement。
- 数据库对象使用 `DbTable`、`DbColumn`，SQL statement 到表字段的读写关系单独建边。

典型关系：

```text
CONTAINS
DECLARES
CALLS
IMPLEMENTS
EXTENDS
INJECTS
ROUTES_TO
SUBMITS_TO
BINDS_TO
READS_PARAM
WRITES_PARAM
READS_TABLE
WRITES_TABLE
USES_CONFIG
FORWARDS_TO
CHANGED_IN
IMPACTS
COVERED_BY
```

MVP 最小关系契约：

```text
ApiEndpoint -[:ROUTES_TO]-> Method
ActionPath -[:ROUTES_TO]-> Method
JspForm -[:SUBMITS_TO]-> ApiEndpoint/ActionPath
Method -[:CALLS]-> Method
Method -[:BINDS_TO]-> SqlStatement
SqlStatement -[:READS_TABLE|WRITES_TABLE]-> DbTable
```

每个节点和关系必须保存：

- `symbolId`：统一符号 ID。
- `sourceType`：`SPOON`、`TAI_E`、`JASPER`、`XML`、`SQL`、`GIT`、`AI_ASSISTED` 等。
- `confidence`：`CERTAIN`、`LIKELY`、`POSSIBLE`、`UNKNOWN`。
- `evidence`：文件路径、行号、XML path、JSP tag、SQL id、commit id。
- `projectId`、`moduleId`、`snapshotId`：用于多项目和增量分析。

统一符号 ID 示例：

```text
method://com.foo.OrderService#cancelOrder(java.lang.Long)
class://com.foo.OrderService
jsp://WEB-INF/jsp/order/edit.jsp
jsp-input://WEB-INF/jsp/order/edit.jsp#input[name=orderId]
sql://mapper/OrderMapper.xml#selectById
table://orders
config://application.yml#spring.datasource.url
```

## 5. SymbolId 与跨引擎映射契约

`symbolId` 是 Spoon、Tai-e、Jasper、XML、SQL、Neo4j、RAG 和报告之间的主键。所有分析器必须先归一化到同一套 `symbolId`，再写入图谱。

基础格式：

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
- XML/JSP/SQL 节点使用逻辑路径 + 局部 id，例如 `jsp://{project}/{module}/src/main/webapp/WEB-INF/jsp/order/edit.jsp#input[name=orderId]`。
- MyBatis statement 使用 namespace + statement id；SQL 片段和动态 SQL 节点必须带 XML path 和 statement id。

映射要求：

- Spoon 负责 source signature、源码行号和 sourceRoot。
- Tai-e 负责 JVM descriptor、字节码调用图和数据流；Tai-e 结果必须映射回 Spoon `symbolId`。
- Jasper 生成的 servlet 方法必须通过 JSP path、SMAP、行号或 generated source metadata 映射回 JSP 节点。
- 无法稳定映射的符号不得覆盖确定节点，只能以 `POSSIBLE` confidence 写入候选关系。

## 6. 增量图谱语义

图谱必须防止旧关系残留。每次分析写入的不只是节点/边，而是带生命周期的事实。

核心字段：

```text
factKey
snapshotId
analysisRunId
scopeKey
active
validFromSnapshot
validToSnapshot
tombstone
evidenceKey
confidence
sourceType
```

语义规则：

- `factKey` 是同一事实的稳定 key，由 source symbol、target symbol、relation type、qualifier 组成。
- `evidenceKey` 是同一事实的证据 key，由 analyzer、文件路径、行号/XML path/JSP tag/SQL id 组成。
- 同一 `factKey` 可以有多个 evidence，Neo4j 查询默认展示聚合事实，也可展开全部证据。
- 分析器每次只负责自己的 `scopeKey`，例如一个 Java 文件、一个 JSP、一个 XML、一个 module。
- 对被重新分析的 scope，本次未重新 emit 的旧事实必须标记 `tombstone=true` 或设置 `validToSnapshot`。
- 未触碰的 scope 不做删除，沿用上一 snapshot 的 active facts。
- 当前图查询默认只读取 `active=true` 且对当前 `snapshotId` 有效的事实。
- snapshot diff 通过比较两个 snapshot 的 active fact set 产生新增、删除和变化。
- confidence 聚合使用确定性来源优先：`CERTAIN > LIKELY > POSSIBLE > UNKNOWN`；`AI_ASSISTED` 不能提升确定性事实，只能附加候选解释。
- 如果多个分析器给出同一事实，保留所有 evidence，最终 confidence 取最高确定性，但报告中必须能看到来源列表。

增量写入流程：

```text
1. 计算 changed scopes
2. 对 changed scopes 运行对应 analyzer
3. analyzer emit facts + evidence
4. upsert factKey/evidenceKey
5. tombstone 当前 scope 中未重新出现的旧 facts
6. 更新 snapshot active view
7. 重建受影响的 JVM InMemoryGraphCache 分片
```

## 7. JSP 与旧框架设计

JSP 是老系统分析的一等能力，尤其是 Struts1 和 Seasar2 系统。

JSP 解析需要先建立 WebAppContext，否则 Jasper 成功率会不稳定。

WebAppContext 包含：

- web root：例如 `src/main/webapp`。
- `WEB-INF/web.xml`：servlet、filter、listener、welcome-file、JSP config。
- classpath：`WEB-INF/classes`、`WEB-INF/lib`、Maven/Gradle dependencies、编译输出目录。
- servlet/JSP API 版本和容器 profile：Tomcat 6/7/8/9/10 等。
- TLD/taglib registry：taglib URI 到 TLD 文件、jar 内 TLD、web.xml taglib 映射。
- include resolver：静态 include、`jsp:include`、相对路径和 context path 解析。
- encoding resolver：page directive、web.xml、文件 BOM、项目默认编码。
- framework configs：`struts-config.xml`、Seasar2 `dicon`、Spring XML/context。

Jasper 解析失败时：

- 记录失败原因和缺失上下文。
- 使用 JetHTMLParser/Jericho 做容错结构提取。
- 相关边标记为 `POSSIBLE` 或 `LIKELY`，不得标记为 `CERTAIN`。

JSP 分析采用双通道：

```text
JSP 原文件
  -> Apache Jasper 解析 JSP 语义
  -> Jasper 生成 Servlet Java
  -> Spoon/Tai-e 分析生成后的 Servlet
  -> SMAP/行号映射回原 JSP

JSP 原文件
  -> JetHTMLParser/Jericho 容错解析
  -> 提取 form/input/select/textarea/taglib/html 结构
```

需要识别：

- JSP directive：`page`、`include`、`taglib`。
- JSP action：`jsp:include`、`jsp:forward`、`jsp:param`、`jsp:useBean`。
- EL：`${user.id}`。
- JSTL：`c:if`、`c:forEach`、`c:set`。
- Struts tag：`html:form`、`html:text`、`bean:write`、`logic:iterate`。
- Spring form tag：`form:form`、`form:input`。
- scriptlet：`<% ... %>`、`<%= ... %>`。
- HTML form/input/select/textarea。
- JavaScript ajax/fetch/XMLHttpRequest，作为后续增强。

Struts1 链路：

```text
JSP
  -> html:form action
  -> struts-config.xml action path
  -> ActionForm
  -> Action.execute()
  -> Service
  -> DAO/Mapper
  -> SQL/table
  -> ActionForward/JSP
```

Seasar2 链路：

```text
dicon component
  -> Java class/interface
  -> naming convention binding
  -> service/dao
  -> interceptor
  -> SQL/table
```

Spring 链路：

```text
@RequestMapping
  -> Controller
  -> Service
  -> Repository/Mapper
  -> SQL/table
```

## 8. 调用关系与变量追踪

调用关系需要分层建模：

- 确定调用：源码或字节码中直接调用。
- 多态调用：接口、抽象类、继承实现、指针分析候选。
- 框架入口：URL、JSP、Action、Controller。
- DI 调用：Spring Bean、Seasar2 component、XML bean。
- 配置驱动调用：XML、YAML、properties、dicon、struts-config。
- 异步调用：MQ、事件、定时任务，后续增强。
- 数据访问：method -> SQL -> table/column。

变量追踪分三层：

1. 方法内追踪：
   - 局部变量 def-use。
   - 参数传递。
   - 赋值、return 来源。
   - `request.getParameter`。
   - getter/setter 简单传播。

2. 跨方法追踪：
   - Controller/Action 参数到 Service。
   - Service 参数到 DAO/Mapper。
   - DTO/Form 字段传播。
   - JSP input 到 request parameter。
   - SQL 参数绑定。

3. 影响切片：
   - 页面字段最终影响哪些 SQL。
   - request 参数最终流向哪些 Service/DAO。
   - DB 字段最终展示在哪些 JSP/API。
   - 修改一个方法、字段、参数后影响哪些入口和数据路径。

示例：

```text
user_edit.jsp input[name=userId]
  -> /user/update.do
  -> UserUpdateAction.execute()
  -> request.getParameter("userId")
  -> UserService.update(userId)
  -> UserDao.update()
  -> users.user_id
```

## 9. 智能与快速设计

主要目标是智能和快速，因此采用快慢双层分析。

快速层：

- 基于 Git diff 定位变更文件和符号。
- 基于 ClassGraph/ASM/JGit/缓存做秒级索引。
- 基于 Neo4j 和 JVM InMemoryGraphCache 查询调用方、入口、SQL、JSP。
- 10 到 30 秒内生成初版影响报告。

深度层：

- Spoon 完整源码模型。
- 基础跨方法变量追踪、SQL/table 影响、更多框架配置。
- Tai-e 指针分析、调用图、多态和 taint analysis 作为增强阶段能力接入。
- Jasper 生成 Servlet 后回映射 JSP。
- 1 到 3 分钟内后台补充深度报告。

性能策略：

- 增量扫描，基于 file hash 和 snapshot。
- 按 module/source root/package 分批处理。
- 分析结果批量写 Neo4j，及时释放 AST/IR。
- MVP 阶段使用 JVM primitive adjacency cache 缓存热点 caller/callee 边。
- Tai-e 深度分析独立 worker JVM，设置独立 `-Xmx`、超时和失败隔离，增强阶段接入。
- benchmark 证明瓶颈后再将高频调用边导出到 FFM OffHeapGraphIndex。
- Neo4j 配置 heap、page cache 和索引，避免大查询拖慢 PR 报告。

MVP 影响查询契约：

- 输入是 `projectId`、`snapshotId`、`changeSet`，其中 `changeSet` 包含变更文件、变更符号、变更类型。
- 对 Java method 变更，先沿 `CALLS` 反向找调用方，再向上追溯 `ROUTES_TO`、`SUBMITS_TO` 找入口。
- 对 JSP 变更，直接以 `JspPage/JspForm/JspInput` 为入口，向下追踪 `SUBMITS_TO`、`ROUTES_TO`、`CALLS`、`BINDS_TO`、`READS_TABLE/WRITES_TABLE`。
- 对 SQL/table 变更，沿 `BINDS_TO`、`CALLS`、`ROUTES_TO`、`SUBMITS_TO` 反向找入口。
- 默认限制路径深度和返回节点数，超限时返回 `truncated=true` 和继续展开提示。
- 报告必须返回 `entrypoint`、`changedSymbol`、`path`、`confidence`、`evidenceList`、`sourceTypeList`、`riskLevel`、`reason`。

MVP 内存缓存契约：

- JVM InMemoryGraphCache 只缓存当前 active snapshot 的 primitive 邻接表。
- 缓存内容来自 Neo4j active facts，不作为事实源。
- 缓存按 `projectId + snapshotId + relationGroup` 分片。
- 增量分析 tombstone 或新增关系后，只重建受影响分片。
- 缓存失效时必须回退到 Neo4j 查询，不能影响报告正确性。
- 缓存指标至少包含 node count、edge count、heap bytes、hit ratio、P95 query time。

## 10. FFM OffHeapGraphIndex

FFM 使用 JDK 22+ Foreign Function & Memory API，建议运行平台使用 JDK 25 LTS。被分析项目仍可为 Java 6/7/8。

FFM 是 benchmark 驱动的增强能力，不进入 MVP 关键路径。MVP 先使用 Neo4j + JVM primitive adjacency cache；只有满足以下任一条件时才启用 FFM：

- 单项目 active edge 数达到百万级以上。
- Neo4j 多跳路径查询 P95 无法满足影响报告 SLA。
- JVM adjacency cache 对 heap 造成稳定压力。
- 影响路径搜索中 visited set/frontier queue 成为主要内存热点。
- benchmark 证明 FFM 版本在目标数据规模上明显优于 JVM cache。

FFM 不保存复杂对象，只保存 primitive 和扁平结构：

```text
methodId -> calleeIds
methodId -> callerIds
nodeId -> edgeOffset
edgeType[]
confidence[]
visitedBitmap
impactFrontierQueue
```

推荐压缩结构：

```text
forward_offsets: long[nodeCount + 1]
forward_targets: long[edgeCount]
forward_edge_types: short[edgeCount]

reverse_offsets: long[nodeCount + 1]
reverse_sources: long[edgeCount]
reverse_edge_types: short[edgeCount]
```

使用方式：

- `Arena.ofConfined()`：单个分析任务临时内存，任务结束关闭。
- `Arena.ofShared()`：多线程共享只读索引。
- `FileChannel.map(..., Arena)`：大图索引落盘并 mmap，重启后快速加载。

边界：

- Neo4j 是事实源。
- FFM 是可重建的高速缓存。
- 不把 Spoon `CtElement`、Tai-e IR、AI 文本或业务 DTO 放入 FFM。

## 11. RAG、Agent 与 MCP

CodeAtlas 需要 RAG，但不是普通文档 RAG，而是 Code Graph RAG。

Vector backend decision:

- MVP default: Neo4j Vector Index, because vector recall must stay close to graph facts, `symbolId`, snapshot scope, and evidence keys.
- pgvector: optional lightweight backend for deployments that already operate PostgreSQL.
- OpenSearch: optional backend when full-text code search and vector search should share one search platform.
- Qdrant: optional dedicated vector backend when vector throughput, payload filtering, or independent scaling becomes important.
- Details are recorded in `docs/rag-vector-backends.md`.

```text
用户问题
  -> 意图识别
  -> 精确符号检索
  -> 向量语义召回
  -> Neo4j 图扩展
  -> 源码/JSP/XML/SQL 证据收集
  -> AI 生成解释
```

向量适合：

- 自然语言问答。
- 语义代码搜索。
- 相似代码发现。
- 老代码理解。
- 历史报告和设计文档召回。

向量不负责：

- 最终调用关系。
- 变量追踪事实。
- SQL 表字段影响事实。
- JSP 到 Action 的确定链路。

Agent 定位：

```text
Agent 是任务编排层，不是事实判断层。
```

MVP Agent：

- `ImpactAnalysisAgent`：PR 影响分析。
- `VariableTraceAgent`：变量来源和流向。
- `CodeQuestionAgent`：自然语言代码问答。

MCP 定位：

```text
MCP 是 CodeAtlas 对 AI Agent / IDE / 外部工具开放能力的标准接口层。
```

第一版 MCP 只读，暴露：

```text
symbol.search
graph.findCallers
graph.findCallees
graph.findImpactPaths
variable.traceSource
variable.traceSink
jsp.findBackendFlow
impact.analyzeDiff
rag.semanticSearch
report.getImpactReport
```

安全要求：

- 默认只读。
- 禁止任意 Cypher。
- 禁止任意文件读取。
- 工具白名单。
- project 级权限。
- 返回结果限量。
- 源码脱敏。
- 全量审计日志。
- 写操作必须人工确认。

## 12. AI 使用原则

AI 不直接生成最终图谱事实。

AI 负责：

- 影响报告摘要。
- 风险解释。
- 测试建议。
- Review 关注点。
- 自然语言问答。
- 旧框架关系的候选推断。
- 架构规则的辅助生成。

AI 输入必须是证据包：

```text
Git diff
变更符号
Neo4j 路径
源码/JSP/XML/SQL 片段
置信度
历史缺陷/提交
测试覆盖
```

AI 输出必须包含：

- 结论。
- 证据路径。
- 置信度。
- 不确定项说明。

## 13. 检索与结果展示契约

CodeAtlas 的主入口应该是问答式检索，但结果不是聊天文本，而是可点击、可追溯、可展开的证据化分析报告。

检索入口分三类：

- 问答式主入口：用户用自然语言提问，例如“userId 从哪里来，到哪里去”“改 UserService.update 会影响什么”。
- 精确检索入口：用户搜索类、方法、JSP、SQL、表、字段、配置，例如 `OrderService`、`/user/update.do`、`users.user_id`。
- 图谱入口：用户从某个节点出发，查看调用方、被调用方、变量来源、变量流向、影响范围或展开一层。

问答式检索流程：

```text
用户问题
  -> intent 识别
  -> symbol candidate 检索
  -> 用户选择候选符号，若只有一个高置信候选可自动选中
  -> 确定性查询：Neo4j / Variable Flow Index / JVM cache
  -> evidence pack
  -> AI 摘要解释
  -> 结构化结果展示
```

首批 intent：

```text
SYMBOL_SEARCH
FIND_CALLERS
FIND_CALLEES
TRACE_VARIABLE_SOURCE
TRACE_VARIABLE_SINK
TRACE_VARIABLE_BOTH
IMPACT_ANALYSIS
JSP_FLOW
SQL_TABLE_IMPACT
CONFIG_IMPACT
```

多候选处理：

- 搜索词匹配多个符号时，先显示候选列表，不直接追踪。
- 候选至少展示 symbol type、qualified name/path、文件、行号、所属模块、最近变更信息。
- 用户选择候选后再执行来源/流向/影响查询。

结果展示固定为四层：

1. 答案摘要：显示找到几条确定路径、几条可能路径、主要来源/流向/影响点、风险等级、后台分析状态。
2. 证据路径：纵向链路为主，例如 `JSP input -> Action -> Service -> Mapper -> SQL -> Table`，每条边展示 edge type、confidence、sourceType。
3. 证据列表：表格展示文件、行号、证据类型、证据片段、分析器来源。
4. 图谱与明细：可展开图谱、候选项、不确定项、原始 JSON、继续展开入口。

变量追踪结果示例：

```text
问题：userId 从哪里来，到哪里去？

摘要：
- 找到 3 条确定来源，2 条确定流向，1 条可能流向。
- 主要来源：/WEB-INF/jsp/user/edit.jsp input[name=userId]
- 主要流向：UserMapper.update -> users.user_id

路径：
edit.jsp:42 input[name=userId]
  -> SUBMITS_AS /user/update.do
  -> ROUTES_TO UserUpdateAction.execute()
  -> READS_PARAM request.getParameter("userId")
  -> PASSES_TO UserService.update(userId)
  -> BINDS_TO_SQL UserMapper.update
  -> WRITES_TABLE users.user_id
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

展示限制：

- 默认不展示全量大图，只展示当前查询相关路径。
- 默认最多 50 个节点、6 跳路径；超限返回 `truncated=true`。
- 每个结果都必须能展开 evidence。
- AI 摘要旁必须显示“基于 N 条静态分析证据生成”。
- AI 关闭时仍显示结构化结果、路径和证据。

## 14. 运行平台、构建与可视化前端

CodeAtlas 自身运行平台和工程基线：

- 后端和分析 worker 使用 Java 25 LTS。
- 构建系统使用 Gradle，采用 multi-project 结构。
- 使用 Gradle Java Toolchains 固定 Java 25 编译/运行基线。
- 被分析项目不受 CodeAtlas 运行 JDK 限制，首批需要支持 Java 6/7/8 风格老项目以及现代 Java 项目。
- 后端服务、分析 worker、MCP server、前端应用在 Gradle 中分模块管理。

建议工程模块：

```text
codeatlas-server
codeatlas-worker
codeatlas-analyzers
codeatlas-graph
codeatlas-ai
codeatlas-mcp
codeatlas-ui
```

可视化前端是一等产品能力，不只是报告展示。首版前端建议使用 React + TypeScript + Vite，图谱展示使用 Cytoscape.js，流程路径展示可使用 React Flow，组件库可使用 Ant Design 或同类企业级组件库。

首版前端视图：

- Project Dashboard：项目概览、扫描状态、模块数量、风险热区。
- Impact Report：PR/commit 影响报告、风险等级、建议测试、AI 摘要。
- Graph Explorer：Neo4j 路径查询结果可视化，支持 caller/callee、入口到 SQL 链路。
- Variable Trace View：变量来源、流向、JSP input、request parameter、SQL 参数路径。
- JSP Flow View：JSP -> Action/Controller -> Service -> DAO/Mapper -> SQL/table。
- Symbol Search：类、方法、JSP、SQL、表字段统一搜索。
- AI Q&A：自然语言查询，回答必须展示证据路径。
- Candidate Picker：搜索词存在多个候选时，先选择符号再执行追踪。
- Evidence Panel：展示路径对应源码、JSP、XML、SQL 证据。

前端交互原则：

- 图谱视图默认展示关键路径，不一次性铺满全项目大图。
- 每条边展示 edge type、confidence、sourceType 和 evidence。
- AI 摘要旁边必须展示可点击证据。
- 大图查询默认限制深度和节点数，必要时提示用户继续展开。

## 15. 验收目标

MVP 需要达到：

- 能扫描一个 Spring + Struts1 + JSP + MyBatis 混合 Java Web 项目。
- 能打通 Spring RequestMapping、Struts action、JSP form/action、Java direct call、MyBatis statement 五类最小边。
- 能稳定生成 `symbolId`、`factKey`、`evidenceKey`，并支持 snapshot/tombstone 防止旧关系残留。
- 能从 JSP 追踪到 Action/Controller。
- 能从 Action/Controller 追踪到 Service/DAO/SQL/table。
- 能基于 Git diff 在 10 到 30 秒内生成初版影响报告。
- 能在后台补充基础变量追踪和 SQL/table 影响。
- 能说明每个影响项的证据路径和置信度。
- 能用 AI 生成简洁风险摘要和测试建议。
- 能通过 MCP/REST 对外提供只读分析能力。
- 能通过可视化前端查看影响报告、调用路径、变量流和 JSP 链路。
- Tai-e 和 FFM 不作为 MVP 验收前置条件。
