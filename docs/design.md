# CodeAtlas Design

## 1. 项目定位

CodeAtlas 是一个面向 Java 企业项目的静态分析与变更影响分析平台。核心目标不是做一个普通代码扫描器，而是用尽量智能、尽量快速的方式回答开发者每天最关心的问题：

- 这里是谁调用的？
- 这个变量从哪里来、到哪里去？
- 我改这里会影响什么？
- 这次变更风险在哪里，应该测什么？

平台重点面向老旧 Java Web 系统和新旧混合系统。完整蓝图覆盖 Spring、Struts1、Seasar2、JSP、MyBatis/SQL、Git diff、调用关系、变量追踪和 AI 辅助解释。MVP 必须先闭环 Spring/Struts1/JSP/MyBatis 的最小影响链路；Seasar2 在 MVP 中只做 dicon/component discovery、配置证据采集和 `POSSIBLE` 候选关系，不要求输出确定性影响路径，也不作为 MVP 影响报告阻塞项。Seasar2 确定性链路进入增强阶段。

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
- Framework Adapters：适配 Spring、Struts1、JSP、MyBatis、JPA、配置文件和旧框架约定；Seasar2 在 MVP 中仅作为 discovery/candidate adapter，确定性影响链路放入增强阶段。
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
| Java 源码 AST | Spoon | JavaParser、Eclipse JDT Core | Spoon 做主源码模型；MVP 快速路径只对 changed scope 和缓存失效 scope 运行 Spoon，不做全项目同步重扫；JavaParser 做轻量快速扫描，JDT 做绑定/编译兜底 |
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

选型约束：

- JSP 不直接使用 jsoup 解析。jsoup 适合 HTML，不适合原始 JSP。
- Spoon 应作为 Java 源码分析主引擎，而不是只用 JavaParser。
- Tai-e 可以优先于 WALA/SootUp 作为高级静态分析引擎。
- MVP 不把 Tai-e 和 FFM 放入关键路径，先用 Spoon/Jasper/JSqlParser/Neo4j 打通业务影响闭环。
- Neo4j 是代码图谱主存储，JVM 缓存和 FFM 都是高速旁路索引，不是事实源。

Spoon 使用边界：
- 全量导入或缓存冷启动时，Spoon 可以按 module/source root 分批构建源码模型。
- PR/commit 快速报告路径不得默认全项目运行 Spoon；只能对 changed Java file、受影响的同源文件、缓存 miss 文件运行 Spoon。
- 10 到 30 秒初版报告主要依赖上一 snapshot 的 active facts、符号索引、Neo4j 查询和 JVM primitive adjacency cache。
- Spoon 产物缓存保存归一化 symbol、source range、direct call、annotation、field、method summary 和 file hash，不长期持有 `CtElement` 或完整 AST。

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
method://shop/_root/src/main/java/com.foo.OrderService#cancelOrder(Ljava/lang/Long;)V
class://shop/_root/src/main/java/com.foo.OrderService
jsp-page://shop/_root/src/main/webapp/WEB-INF/jsp/order/edit.jsp
jsp-input://shop/_root/src/main/webapp/WEB-INF/jsp/order/edit.jsp#form[/order/save.do:post:42:0]:input[userId:text:43:0]
sql-statement://shop/_root/src/main/resources/com/foo/OrderMapper.xml#com.foo.OrderMapper.selectById
db-table://shop/_root/_database/_default/orders
config-key://shop/_root/src/main/resources/application.yml#spring.datasource.url
```

## 5. SymbolId 与跨引擎映射契约

`symbolId` 是 Spoon、Tai-e、Jasper、XML、SQL、Neo4j、RAG 和报告之间的主键。所有分析器必须先归一化到同一套 `symbolId`，再写入图谱。

### 5.1 基础格式

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
- XML/JSP/SQL 节点使用逻辑路径 + 局部 id，例如 `jsp-input://{project}/{module}/src/main/webapp/WEB-INF/jsp/order/edit.jsp#form[{stableFormKey}]:input[{stableInputKey}]`。
- MyBatis statement 使用 namespace + statement id；SQL 片段和动态 SQL 节点必须带 XML path 和 statement id。

映射要求：

- Spoon 负责 source signature、源码行号和 sourceRoot。
- Tai-e 负责 JVM descriptor、字节码调用图和数据流；Tai-e 结果必须映射回 Spoon `symbolId`。
- Jasper 生成的 servlet 方法必须通过 JSP path、SMAP、行号或 generated source metadata 映射回 JSP 节点。
- 无法稳定映射的符号不得覆盖确定节点，只能以 `POSSIBLE` confidence 写入候选关系。

### 5.2 SymbolId 归一化算法

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
5. normalizeKind(input.kind)
6. normalizeOwner(kind, analyzer-specific owner)
7. normalizeMember(kind, memberName/localId)
8. normalizeDescriptor(kind, sourceSignature, bytecode descriptor, classpath)
9. normalizeFragment(kind, local JSP/XML/SQL/report id)
10. assemble canonical symbolId
11. attach display metadata and aliases
12. validate parse(symbolId) round-trip
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
- bridge/synthetic method 保留独立 `symbolId`，并增加 `BRIDGES_TO` 或 `SYNTHETIC_OF` 指向源码方法。

Java field：

```text
field://{projectKey}/{moduleKey}/{sourceRootKey}/{ownerBinaryName}#{fieldName}:{erasedJvmTypeDescriptor}
```

- 字段类型 descriptor 参与 canonical id，解决同名字段在字节码增强或语言特性下的冲突。
- 若类型无法解析，使用 `:U` 作为 unresolved type marker，并记录 display type。

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
db-table://{projectKey}/{moduleKey}/_database/{schema?}/{table}
db-column://{projectKey}/{moduleKey}/_database/{schema?}/{table}#{column}
```

- MyBatis 使用 `namespace#statementId`，动态 SQL fragment 追加 XML path。
- JDBC literal SQL 没有 statement id 时，使用 normalized SQL hash，并在 metadata 保存调用方法和源码位置。
- table/column 名按数据库实际大小写策略保存；比较时另存 normalized database name。
- schema 缺失时使用 `_default` 或项目配置的 default schema。

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

alias 与冲突处理：

- 每个 canonical `symbolId` 可以有多个 alias：source signature、Tai-e/Soot signature、JVM internal name、display path、legacy id。
- alias 只用于解析输入和合并，不作为图谱主键。
- 两个 analyzer 生成不同 canonical id 但 alias 指向同一 JVM descriptor 时，合并到 resolved canonical id，并记录 `MERGED_ALIAS` evidence。
- 两个符号 canonical id 相同但 source range/owner/type 冲突时，不直接覆盖；创建 conflict record，等待人工或后续 analyzer 消歧。
- unresolved descriptor 后续 resolved 时，旧 provisional id 设置 `canonicalReplacement`，查询层应跳转到 resolved id，但历史 evidence 保留原 id。

合法性校验：

- `symbolId` 必须能被 parser 无损解析出 kind、projectKey、moduleKey、sourceRootKey、owner/path、member/local fragment。
- canonical id 不允许包含未转义空白、反斜杠、控制字符。
- Java method 必须有 descriptor；没有 descriptor 的 method id 只能作为 search candidate，不能作为确定 CALLS/BINDS_TO 事实主键。
- JSP/XML/SQL/report symbol 必须带文件 sourceRoot 和相对路径。
- 所有 upsert 前必须执行 `parse -> normalize -> assemble` round-trip 测试。

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
- confidence 聚合不等于简单投票。聚合顺序为：确定性等级优先，其次 analyzer priority，其次 evidence count 只用于排序展示，不提升等级。
- analyzer priority 只用于同等级排序，默认顺序为：`SPOON/JASPER/JSP_SMAP/XML/SQL/JPA/ASM/CLASSGRAPH/JAVAPARSER_FAST/TAI_E/AI_ASSISTED`。其中 `TAI_E` 可补充深度事实，但不能覆盖已有更确定的源码/XML/JSP 事实；`AI_ASSISTED` 永远不能产生确定性 graph fact。
- 同一 `factKey` 同时存在 `CERTAIN` 和 `POSSIBLE` evidence 时，聚合 fact 可显示 `CERTAIN`，但 UI/报告必须保留所有 evidence 的 sourceType/confidence，不能隐藏低置信来源。
- 冲突事实不能靠 confidence 自动合并。例如同一 JSP form action 映射到两个不同 ActionPath 时，应保留两条 fact，并在报告中显示歧义。

增量写入流程：

```text
1. 计算 changed scopes
2. 对 changed scopes 运行对应 analyzer
3. analyzer emit facts + evidence
4. 写入 staging area: analysisRunId + scopeKey + emitted facts
5. 校验 staging facts 的 symbolId/factKey/evidenceKey/schema
6. 在单个 commit window 中 upsert factKey/evidenceKey
7. tombstone 当前 analyzer + 当前 scope 中未重新出现的旧 facts
8. 原子切换 snapshot active view 或 run status
9. 重建受影响的 JVM InMemoryGraphCache 分片
```

原子性要求：
- 每个 `analysisRunId` 有状态：`PLANNED -> RUNNING -> STAGED -> COMMITTED` 或 `FAILED/ROLLED_BACK`。
- `STAGED` facts 不参与当前查询；只有 `COMMITTED` run 才能进入 active view。
- 同一 scope 的 upsert 和 tombstone 必须在同一个提交窗口完成。失败时保留旧 active facts，不暴露半更新结果。
- tombstone 必须带 `analyzerId + scopeKey + previousRunId/currentRunId`，不能跨 analyzer、跨 scope 清理。
- 任何缓存重建都发生在 commit 成功之后；缓存失败必须回退 Neo4j active facts。

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

MVP JSP 定位边界：

- MVP 阶段可以基于 Jasper 节点、JSP 原文件 token/tag 位置和容错解析结果提供 JSP path、tag、粗粒度 line evidence。
- 在 SMAP parser 完成前，generated servlet 行号不得作为确定 JSP 原始行号；只能作为辅助 evidence，并标记 `mappingStatus=GENERATED_ONLY` 或 `SMAP_DEFERRED`。
- 对 include/tagfile/custom tag 展开的精确原始行号定位进入 hardening 阶段；MVP 报告必须显示定位置信度和降级原因。

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
- 基于上一 snapshot 的 active facts 和 symbol index 直接定位变更符号，不等待全量源码模型重建。
- 仅对 changed Java/JSP/XML/SQL 文件和缓存失效 scope 做增量 Spoon/Jasper/JSqlParser 分析。
- 基于 Neo4j 和 JVM InMemoryGraphCache 查询调用方、入口、SQL、JSP。
- 10 到 30 秒内生成初版影响报告。

深度层：

- Spoon 完整源码模型，按后台任务补全缓存 miss、绑定解析和更大范围调用关系。
- 基础跨方法变量追踪、SQL/table 影响、更多框架配置。
- Tai-e 指针分析、调用图、多态和 taint analysis 作为增强阶段能力接入。
- Jasper 生成 Servlet 后回映射 JSP。
- 1 到 3 分钟内后台补充深度报告。

性能策略：

- 增量扫描，基于 file hash 和 snapshot。
- Spoon 增量缓存基于 `projectId + moduleKey + sourceRootKey + relativePath + fileHash`；缓存内容为 symbol summary、source range、annotation/direct call/field/method facts，不缓存完整 AST。
- 变更文件优先走轻量符号定位；只有需要刷新 Java facts 时才启动 Spoon changed-scope 分析。
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

FFM 是 benchmark 驱动的增强能力，不进入 MVP 关键路径。MVP 先使用 Neo4j + JVM primitive adjacency cache；只有同时满足“规模/性能触发条件”和“收益证明条件”时才启用 FFM。

规模/性能触发条件满足任一项即可：

- 单项目 active edge 数达到百万级以上。
- Neo4j 多跳路径查询 P95 连续 benchmark 超过影响报告 SLA。
- JVM adjacency cache 对 heap 造成稳定压力，例如缓存常驻占用超过为 graph cache 分配的 heap budget。
- 影响路径搜索中 visited set/frontier queue 成为主要内存热点。

收益证明条件必须全部满足：

- 同一数据集、同一查询模板下，FFM P95 明显优于 JVM primitive adjacency cache。
- FFM heap 占用明显低于 JVM cache。
- FFM 结果和 Neo4j active facts 校验一致。
- FFM 构建/加载失败时可以无损回退到 Neo4j/JVM cache。

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

实现约束：
- MCP tool 参数只能是结构化参数，例如 `projectId`、`snapshotId`、`symbolId`、`maxDepth`、`limit`、`queryText`。不得接受原始 Cypher、SQL、文件路径通配符或 shell 命令。
- project 级权限在 tool dispatch 前检查，拒绝时返回结构化 forbidden error，并写审计日志。
- 源码脱敏在 evidence pack 构建阶段执行，默认移除密钥、token、password、连接串、个人信息样式字段，并限制单段 snippet 字数和总字符预算。
- 审计日志记录 `requestId`、`principal`、`projectId`、`toolName`、参数摘要、结果数量、是否脱敏、耗时、拒绝原因；不记录完整源码片段、API key 或大段 prompt。
- 所有工具必须有 per-tool `maxDepth`、`limit` 和 timeout 上限。超过上限返回 `truncated=true` 或 structured error。
- 写操作即使未来加入，也必须同时满足工具白名单、显式 `confirmWrite=true`、人工确认意图和审计记录；MVP 不开放写工具。

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

## 16. Review Hardening Notes

本节记录架构 review 后补充的硬化设计，作为后续实现优先级和验收依据。

### 16.1 JSP SMAP 与证据定位

Jasper 生成 Servlet 后，生成 Java 行号与原 JSP 行号不能假设为 1:1。尤其是嵌套 include、自定义 tag、tag file、JSTL 展开、scriptlet 混合场景，必须把行号映射作为 evidence 的一等能力。

设计要求：

- evidence 同时保留 `jspPath`、`jspLineStart`、`jspLineEnd`、`generatedServletPath`、`generatedLineStart`、`generatedLineEnd`。
- SMAP 解析成功时，JSP 原始位置作为主定位；生成 Servlet 位置作为辅助定位。
- SMAP 缺失或不可解析时，定位结果降级为 `LIKELY` 或 `POSSIBLE`，并记录 `mappingStatus=SMAP_MISSING|SMAP_AMBIGUOUS|GENERATED_ONLY`。
- include/tagfile 展开导致多个 JSP 来源候选时，不合并为单一确定行号，必须在 evidence 中保留候选列表。
- UI 展示默认给非开发人员看 JSP 原文件位置，展开后再显示 generated servlet 细节。

### 16.2 图谱模型补充

老旧框架和数据库字段影响需要补充以下模型：

- Struts1 `ActionForm` 到 `ActionPath/Method` 的绑定必须成为显式事实：`ActionPath -[:BINDS_TO]-> FormBean/Class`、`FormBean/Field -[:BINDS_TO]-> RequestParameter`。
- Seasar2 `dicon` 的 `autoBinding`、`namespace`、`include`、`component`、`property`、`aspect`、`interType` 必须建模为配置事实。MVP 只要求 discovery/candidate 事实，默认 `POSSIBLE`，不要求确定性路由或影响路径闭环；增强阶段再提升到确定性链路。
- SQL/table 字段级影响需要增加 `MAPS_TO_COLUMN` 关系，用于 Java field、DTO/Form field、JPA entity field、MyBatis resultMap/property、JDBC setter/getter 与 `DbColumn` 的映射。
- `BINDS_TO` 仍表示方法/Mapper/SQL 之间的绑定，`MAPS_TO_COLUMN` 专门表示字段级映射，避免语义混用。

建议关系：

```text
ActionPath -[:BINDS_TO]-> FormBean
FormBeanField -[:BINDS_TO]-> RequestParameter
Field -[:MAPS_TO_COLUMN]-> DbColumn
SqlStatement -[:READS_TABLE|WRITES_TABLE]-> DbTable
SqlStatement -[:READS_COLUMN|WRITES_COLUMN]-> DbColumn
DiconComponent -[:AUTO_BINDS_TO]-> Class/Interface
```

### 16.3 Report Adapter

企业 Java 迁移里，报表定义经常直接依赖 SQL、表、字段和固定格式输出。CodeAtlas 需要把报表资源作为 Framework Adapter 的一类，而不是只把它们当普通 XML。

Report Adapter 首批目标：

- 支持 Interstage List Creator、WingArc1st SVF 等报表资源的可插拔解析。
- 识别常见报表定义文件，例如 PSF、PMD、BIP、SVF XML、报表布局 XML、字段定义 XML。
- 提取报表名、报表字段、绑定 SQL、表、列、参数、数据源配置。
- 建立 `ReportDefinition -[:READS_TABLE|READS_COLUMN|USES_CONFIG]-> ...`。
- 支持查询“改了数据库字段 -> 哪些报表可能受影响”。

报表解析失败时：

- 不能假装确定。
- 需要生成 `ReportDefinition` 候选节点和 `POSSIBLE` 证据。
- evidence 记录文件路径、XML path/字段名、解析器名称和失败原因。

### 16.4 JNI 与 Native 边界

对企业老系统，JNI/native jar 是影响分析的断点。MVP 不需要理解 native 实现，但必须识别和显式标记。

设计要求：

- Java `native` method 建立 `Method` 节点并标记 `native=true`。
- `System.load`、`System.loadLibrary`、JNI wrapper jar、平台相关 native library 文件建立 `NativeLibrary` 或 `ConfigKey` 节点。
- `Method -[:CALLS_NATIVE]-> NativeLibrary/ConfigKey` 或 `Method -[:HAS_NATIVE_BOUNDARY]-> ConfigKey`。
- 影响路径遇到 native 边界时返回 `analysisBoundary=NATIVE`、`confidence=POSSIBLE`、`requiresManualReview=true`。
- UI 和报告必须显示“分析在 JNI/native 边界中断，需要人工确认”，不能静默截断。
- native 边界是单条分支的终止节点，不等同于全局查询失败。搜索应继续探索其他非 native 分支。
- `truncated=true` 仅表示深度、节点数或时间限制导致结果截断；native 边界使用 `analysisBoundary=NATIVE` 表示，不复用 `truncated`。
- 如果 native 方法的上游入口可确定，报告仍返回入口到 native 边界的路径，并把风险标为需人工确认。

### 16.5 增量写入与 Neo4j 并发策略

增量分析的最小 scope 需要明确：

- 源码/JSP/XML/SQL：以文件为最小分析 Unit。
- jar/classpath：以 JAR 或 module 为最小缓存 Unit。
- 报表资源：以报表定义文件为最小 Unit。

Neo4j 写入策略：

- 分析 worker 不直接高频逐条写共享热点节点，优先产生 fact batch。
- 写入层使用 Batch Upsert，按 `projectId + snapshotId + analyzerId + scopeKey` 分组。
- 同一 `projectId + snapshotId` 默认单写入协调器提交，多个 analyzer 可以并行分析，但提交阶段串行或按不相交 scope 分区。
- 每个 batch 内部按稳定顺序 upsert：Project/Module/SourceFile -> Symbol nodes -> Facts -> Evidence -> Tombstone，降低锁顺序冲突。
- 对高频节点，例如 BaseService、CommonAction、基础 DAO，写入时需要控制 batch 大小并支持 Neo4j transient deadlock retry，重试必须幂等。
- 如果后续出现多 worker 并发写入，增加轻量消息队列或单写入协调器，把分析和图谱提交解耦。
- batch size、retry count、retry backoff、deadlock count 必须进入 benchmark 指标。
- 所有 tombstone 操作必须限定在当前 analyzer + 当前 scope，避免误删其他分析器或未触碰 scope 的事实。

### 16.6 FFM 定位收紧

FFM 只作为极限性能插件：

- 默认事实源仍是 Neo4j。
- 默认快速查询仍优先 Neo4j index + JVM primitive adjacency cache。
- 只有 benchmark 证明 Neo4j/JVM cache 无法满足 P95/SLA 或 heap 压力明显时，才允许启用 FFM。
- FFM 不能进入早期关键路径，也不能影响报告正确性。

### 16.7 MCP 实现候选

MCP 是对 IDE 和外部 Agent 的标准只读接口层。实现方式保持可替换：

- Java 直接实现 MCP 协议。
- 或评估 Spring AI MCP 相关 SDK。

实现时必须以当时官方文档确认 API 和兼容性。无论采用哪种实现，MCP 工具都必须走 CodeAtlas 内部只读工具白名单，不能暴露任意 Cypher、任意 SQL 或任意文件读取。

### 16.8 Benchmark 靶机

需要建立可重复 benchmark：

- 小型样例：仓库内 Spring MVC、Struts1 + JSP、Seasar2、MyBatis/JDBC fixture。
- 中型开源项目：优先选择类似 RuoYi 的 Spring/MyBatis 项目验证现代 Java Web 链路。
- 遗留靶机：寻找 Struts1/JSP 公开样例或构造一个中型 Struts1 fixture，覆盖 ActionForm、DispatchAction、LookupDispatchAction、Tiles、Validator、plugin、JSP tag、JDBC。
- 指标必须包含扫描耗时、图谱写入耗时、Neo4j 查询 P95、JVM cache heap、路径报告耗时、误报/漏报样例。
