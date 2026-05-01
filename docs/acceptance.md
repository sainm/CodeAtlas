# CodeAtlas Current Acceptance Checklist

## 验收时间

- 日期：2026-05-01
- 范围：当前主线代码、文档记忆、后端 API、MCP/Agent 契约、前端可视化入口。

## 自动验收结果

- [x] `.\gradlew.bat build`
  - 结果：通过。
  - 覆盖：Java 25 Gradle 多模块编译、测试、jar/dist 任务、前端 Gradle 集成构建。
- [x] `.\gradlew.bat test`
  - 结果：通过。
  - 覆盖：graph、analyzers、server、ai、mcp、worker 模块测试。
- [x] `npm run build`
  - 路径：`codeatlas-ui`
  - 结果：通过。
  - 覆盖：TypeScript 编译和 Vite 生产构建。
- [x] `npm audit --audit-level=high`
  - 路径：`codeatlas-ui`
  - 结果：未发现 high 及以上漏洞。
- [x] `git diff --check`
  - 结果：未发现空白错误或冲突标记。
  - 整理：已补充 `.gitattributes`，默认文本文件按 LF 归一化，Windows 脚本按 CRLF 保留。
- [x] `Select-String -Path docs/task.md -Pattern "^- \[ \]"`
  - 结果：Review Hardening backlog 存在未完成增强任务；这些任务不属于当前 MVP 完成门槛。

## 功能验收清单

- [x] Java 25 + Gradle 多模块工程可以完整构建。
- [x] 项目 group/package 使用 `org.sainm.codeatlas`。
- [x] Java/Spoon/Jar 字节码分析可以进入统一图谱事实模型。
- [x] Struts1 解析覆盖 action、forward、controller、自定义 ActionMapping、plugin、validator、tiles、DynaActionForm。
- [x] JSP 解析接入 Apache Jasper，并保留 tolerant fallback；Struts1 标签、scriptlet、EL、include、forward、form/input 能生成证据事实。
- [x] 变量追踪覆盖 request parameter、JSP input、ActionForm/DynaActionForm、局部变量别名、方法参数传递、下游 SQL/table。
- [x] JDBC、MyBatis、JPA 基础 SQL/table 影响链路可建模。
- [x] Git diff 到变更符号、影响路径、报告 JSON/Markdown 可输出。
- [x] Neo4j 图谱、active facts、snapshot/tombstone、evidence/confidence/sourceType 契约已建立。
- [x] AI/RAG 作为解释、摘要、问答和证据召回层，不直接生成最终事实。
- [x] MCP/Agent 只读工具覆盖 query plan、symbol search、impact、variable trace、JSP flow、RAG、project overview。
- [x] 前端支持项目总览、变更影响、调用关系、变量追踪、JSP 链路、SQL/table、AI 问答。
- [x] 前端接入 Ant Design、Cytoscape.js、React Flow，并通过 Vite manual chunks 分包。

## 人工验收入口

1. 启动后端服务，确认 `/health` 返回 `{"status":"ok"}`。
2. 打开前端总览页，点击“查看结果”，确认项目能力、后台状态、常用入口来自 `/api/project/overview`。
3. 在自然语言框输入“项目总览和分析状态”，确认 planner 返回 `PROJECT_OVERVIEW`。
4. 在 Graph Explorer 切换“上下游 / 谁调用它 / 它调用谁 / 入口到 SQL”，确认请求 endpoint 和深度参数变化正常。
5. 对 Struts1 + JSP 样例执行 JSP backend flow，确认路径包含 JSP -> Action -> Java -> SQL/table。
6. 对 request parameter 执行变量追踪，确认结果分为来源和流向，并展示 JSP input 与 Java/SQL 证据。
7. 查看原始 JSON，确认每条路径仍保留 `sourceType`、`confidence`、`evidenceKeys`。

## 当前残余风险

- Cytoscape.js 和 React Flow 已接入生产构建，但还没有浏览器截图级视觉回归测试。
- `/api/project/overview` 当前是轻量状态接口，后续可接入真实扫描任务、图谱统计、最近报告和索引状态。
- FFM 与 Tai-e 均已按增强路径接入契约，默认仍应由 benchmark/可选 worker 控制，不作为 MVP 强依赖。
- Windows 工作区已加入 `.gitattributes` 换行策略；后续提交前仍建议保留 `git diff --check`。
- Jasper generated servlet 到 JSP 原文件的精确 SMAP 回映射仍是增强任务；当前不能把所有 generated line 都视作 1:1 JSP line。
- 报表资源、字段级 `MAPS_TO_COLUMN`、JNI/native 边界识别、Neo4j 并发 batch upsert 是企业落地硬化任务，已记录在 `docs/design.md` 和 `docs/task.md`。
- Seasar2 当前 MVP 范围只接受 discovery/candidate 事实，不接受“确定性影响链路已闭环”的验收表述。
- 10 到 30 秒初版报告依赖 committed snapshot、symbol index、Neo4j/JVM cache 和 changed-scope 增量刷新，不以全项目 Spoon 重建作为前置条件。
- 增量写入必须满足 staging/commit/rollback 原子性；失败时继续暴露上一 committed active facts。
