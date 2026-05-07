# CodeAtlas UI Workbench Design

## 目标

CodeAtlas 前端采用“改动工作台优先”的信息架构。首页不要求用户先理解图谱、SymbolId、relation 或 snapshot，而是先围绕变更意图回答“应该从哪里开始、改哪里、影响哪里、怎么测、改完如何验证”，再进入对应修改计划、测试计划、报告、证据和图谱细节。

本说明是 `docs/design.md` 中可视化前端和结果展示契约的派生设计，不能引入与 `docs/design.md` 不一致的新事实。

## 设计原则

- 首页优先展示需求探索、修改计划和测试计划入口，而不是技术对象。
- 用户输入可以是需求、bug 描述、设计书片段、页面/字段/菜单名、DB 表/字段、JSP/HTML 页面、Java symbol、变量名或自然语言功能描述。
- Git diff 或本地变更默认作为关系刷新和修改后验证输入；只有用户显式选择“分析这次变更影响”时才生成 diff 影响报告。
- 查询结果先给摘要和下一步建议，再允许展开路径、证据、图谱和原始 JSON。
- 所有结论必须展示 confidence、sourceType、evidence 和 truncation/pending/stale 状态。
- 图谱探索是下钻能力，不是默认第一屏。
- 导入审查是进入确定性报告前的正确性边界；项目范围、覆盖盲区或用户确认项未完成时，UI 必须先展示 Import Review，而不是直接给完整影响结论。

## 主屏布局

主屏分成三列：

- 左侧任务导航：常用任务、最近报告、保存查询。
- 中央工作区：需求/bug/设计片段输入框、快捷任务按钮、探索摘要、修改计划、测试计划、下一步动作和主要路径预览。
- 右侧上下文面板：证据、分析覆盖、盲区提醒、当前 snapshot、AI 状态。

中央工作区的快捷任务：

- 粘贴需求探索
- 规划功能修改
- 新增功能落点
- 生成测试计划
- 修改后验证
- 刷新关系库
- 查 DB 影响
- 查变量流向
- 查 JSP/Web Client 链路
- 找相似实现

## 主要页面

### Project Dashboard

展示项目概览、分析状态、覆盖能力、入口点、最近报告、盲区和推荐下一步。它是默认项目首页的概览区域，不是和 Change Workbench 竞争的独立首页。

### Import Review View

展示 workspace profile、候选项目、项目状态、能力覆盖、不可分析盲区、secret/config 风险、建议分析计划和需要用户确认的 scope。存在 `PARTIAL`、`BOUNDARY_ONLY`、`UNSUPPORTED`、`BROKEN` 或 `UNKNOWN` 时，必须先让用户确认 `AnalysisScopeDecision`；未确认或确认已 stale 时，后续报告必须显示 partial/candidate 状态。

### Change Workbench

承载需求/bug/设计片段输入和改动快捷入口。默认项目首页是 “Project Dashboard + Change Workbench” 组合页，其中 Change Workbench 是主操作区；根据输入内容触发 requirement exploration 和 query planning，返回候选、修改计划、测试计划、报告或进一步确认请求。

### Requirement Exploration View

展示 `RequirementInput`、`ChangeIntent`、`ExplorationPlan`、候选入口、候选代码、SQL/DB/页面/测试线索、OpenQuestions 和证据。

### Modification Plan View

展示必须修改、建议检查、可能相关、无需修改但回归、无法判断需确认、风险残留和建议改动顺序。

### Test Plan View

展示测试入口、测试场景、测试数据、断言、前置数据、回归范围和人工准备项。

### Post-change Validation View

用户修改后基于新 snapshot 对照原计划展示覆盖项、遗漏项、新增影响和测试计划更新。

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

1. 用户导入或进入项目；如果存在未确认的导入审查、盲区或 stale scope，先进入 Import Review View。
2. 用户确认项目边界、include/exclude、source root、web root、datasource 和不可分析范围后，进入 “Project Dashboard + Change Workbench” 组合首页。
3. 用户输入需求、bug、设计片段、字段名、页面名或点击快捷任务。
4. Requirement Exploration 返回候选入口、候选代码、OpenQuestions 或需要确认的问题。
5. 多候选进入 Candidate Picker；唯一高置信候选可直接生成 Modification Plan。
6. 用户查看修改计划、最高风险路径、测试计划、测试数据和盲区。
7. 用户修改代码后触发关系刷新，再进入 Post-change Validation View 对照原计划检查覆盖、遗漏和新增影响。
8. 用户按需展开证据、图谱、原始 JSON 或保存报告。
9. 后台深度补充完成后，原报告显示 stale/upgrade available。

## 验收

- 新用户能从首页在一次输入或一次点击内进入需求探索、功能规划、测试计划、修改后验证、DB 影响、变量追踪或 JSP/Web Client 链路。
- Git diff/local changes 默认刷新关系库和支持修改后验证，不默认生成影响报告。
- 存在未确认 Import Review 或 stale scope 时，UI 不得展示为完整确定性报告，必须先显示确认项、覆盖盲区和降级状态。
- 默认视图不暴露 raw SymbolId，除非用户进入下钻详情。
- 每个报告都显示 confidence、sourceType、evidence、truncated/pending/stale 状态。
- 多候选时必须先显示 Candidate Picker，不能自动跨 project/module/datasource 选择。
- 图谱查询默认限制 depth、node、path 数量，并展示 truncation reason。
- AI 关闭时 UI 仍显示静态摘要、路径、证据和建议测试。
