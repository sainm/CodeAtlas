# CodeAtlas UI Workbench Design

## 目标

CodeAtlas 前端采用“任务工作台优先”的信息架构。首页不要求用户先理解图谱、SymbolId、relation 或 snapshot，而是先回答“今天要分析什么”，再进入对应报告、证据和图谱细节。

本说明是 `docs/design.md` 中可视化前端和结果展示契约的派生设计，不能引入与 `docs/design.md` 不一致的新事实。

## 设计原则

- 首页优先展示任务入口，而不是技术对象。
- 用户输入可以是 Git diff、DB 表/字段、JSP/HTML 页面、Java symbol、变量名或自然语言功能描述。
- 查询结果先给摘要和下一步建议，再允许展开路径、证据、图谱和原始 JSON。
- 所有结论必须展示 confidence、sourceType、evidence 和 truncation/pending/stale 状态。
- 图谱探索是下钻能力，不是默认第一屏。

## 主屏布局

主屏分成三列：

- 左侧任务导航：常用任务、最近报告、保存查询。
- 中央工作区：统一输入框、快捷任务按钮、结果摘要、下一步动作和主要路径预览。
- 右侧上下文面板：证据、分析覆盖、盲区提醒、当前 snapshot、AI 状态。

中央工作区的快捷任务：

- 分析 Git Diff
- 查 DB 影响
- 查变量流向
- 查 JSP/Web Client 链路
- 规划功能修改
- 找相似实现

## 主要页面

### Project Dashboard

展示项目概览、分析状态、覆盖能力、入口点、最近报告、盲区和推荐下一步。它是默认项目首页的概览区域，不是和 Task Workbench 竞争的独立首页。

### Task Workbench

承载统一输入和任务快捷入口。默认项目首页是 “Project Dashboard + Task Workbench” 组合页，其中 Task Workbench 是主操作区；根据输入内容触发 query planning，返回候选、报告或进一步确认请求。

### Impact Report View

展示风险摘要、受影响入口、路径列表、建议测试、pending/deep supplement 状态和证据下钻。

### DB Impact View

按 read/write/display/test 分组展示表或字段变更影响，区分 table-level 降级和 column-level 确定影响。

### SQL/Table Path View

用于从入口、方法、Mapper 或 SQL 出发下钻到 SQL/table/column 路径。它是路径探索视图，不替代 DB Impact View 的表字段变更报告。

### Variable Trace View

支持 source、sink、all 三种模式，展示 request parameter、JSP/HTML input、Java 参数、SQL 参数和 DB 影响。

### JSP/Web Client Flow View

展示 JSP/HTML/ClientRequest 到 Action/Controller，再到 Service、DAO/Mapper、SQL/table 的链路，并明确 CLIENT_JS_DYNAMIC 边界。

### Feature Change/Add Plan View

展示必须关注、建议检查、可能相关、建议测试和不确定项；AI/弱证据候选不得显示为确定修改项。

### Graph Explorer

作为下钻页面，支持 caller、callee、combined、entry-to-SQL 模式，默认限制深度和节点数。

### Evidence Panel

作为右侧抽屉或固定面板复用在所有报告中，展示文件、行号、evidence type、snippet metadata、analyzer、sourceType、confidence、boundary 和 raw details。

## 交互流

1. 用户进入项目，看到 “Project Dashboard + Task Workbench” 组合首页。
2. 用户输入问题或点击快捷任务。
3. Query Planning 返回唯一结果、候选列表或需要确认的问题。
4. 唯一结果直接展示摘要；多候选进入 Candidate Picker。
5. 用户查看摘要、最高风险路径、建议测试和盲区。
6. 用户按需展开证据、图谱、原始 JSON 或保存报告。
7. 后台深度补充完成后，原报告显示 stale/upgrade available。

## 验收

- 新用户能从首页在一次输入或一次点击内进入 Git diff、DB 影响、变量追踪、JSP/Web Client 链路或功能规划。
- 默认视图不暴露 raw SymbolId，除非用户进入下钻详情。
- 每个报告都显示 confidence、sourceType、evidence、truncated/pending/stale 状态。
- 多候选时必须先显示 Candidate Picker，不能自动跨 project/module/datasource 选择。
- 图谱查询默认限制 depth、node、path 数量，并展示 truncation reason。
- AI 关闭时 UI 仍显示静态摘要、路径、证据和建议测试。
