import type React from 'react';
import {
  AlertTriangle,
  Braces,
  Database,
  FileCode2,
  Network,
  Sparkles,
  Workflow
} from 'lucide-react';
import type { Evidence, GraphQueryMode, Mode, QueryPlan, QueryResultView, QuickTemplate } from './types';

export const modes: Array<{ id: Mode; label: string; icon: React.ComponentType<{ size?: number }> }> = [
  { id: 'overview', label: '项目总览', icon: Braces },
  { id: 'impact', label: '变更影响', icon: AlertTriangle },
  { id: 'variable', label: '变量去向', icon: Workflow },
  { id: 'jsp', label: '页面链路', icon: FileCode2 },
  { id: 'graph', label: '调用关系', icon: Network },
  { id: 'sql', label: '数据表影响', icon: Database },
  { id: 'qa', label: 'AI 问答', icon: Sparkles }
];

export const quickTemplates: QuickTemplate[] = [
  {
    title: '输入值去哪了',
    example: 'userId 从页面到后台去了哪里',
    mode: 'variable',
    query: '看看 userId 从页面到后台去了哪里',
    parameterOverrides: { symbolId: 'request-parameter://shop/_root/_request/userId' }
  },
  {
    title: '页面会调哪里',
    example: 'user/edit.jsp 提交后经过哪些后台处理',
    mode: 'jsp',
    query: '看看 user/edit.jsp 提交后经过哪些后台处理',
    parameterOverrides: { symbolId: 'jsp-page://shop/_root/src/main/webapp/user/edit.jsp' }
  },
  {
    title: '改这里影响什么',
    example: 'UserService.save 改动会影响哪些页面和表',
    mode: 'impact',
    query: 'UserService.save 改动会影响哪些页面和数据表',
    parameterOverrides: { changedSymbol: 'method://shop/_root/src/main/java/com.acme.UserService#save()V' }
  },
  {
    title: '数据表被谁用',
    example: 'users 表被哪些功能读取或修改',
    mode: 'sql',
    query: 'users 表被哪些功能读取或修改',
    parameterOverrides: { symbolId: 'db-table://shop/_root/_database/users' }
  },
  {
    title: '谁调用了它',
    example: 'UserAction.execute 被谁调用，后面又调用谁',
    mode: 'graph',
    query: '查看 UserAction.execute 的上下游调用关系',
    parameterOverrides: { symbolId: 'method://shop/_root/src/main/java/com.acme.UserAction#execute()V' }
  },
  {
    title: '代码问答',
    example: 'UserService.save 是什么，旁边给我证据',
    mode: 'qa',
    query: 'UserService.save 是什么，旁边给我证据',
    parameterOverrides: { q: 'UserService.save 是什么，旁边给我证据' }
  }
];

export const graphModes: Array<{ id: GraphQueryMode; label: string; description: string }> = [
  { id: 'both', label: '上下游', description: '同时查询调用方和被调用方' },
  { id: 'callers', label: '谁调用它', description: '只看上游 caller' },
  { id: 'callees', label: '它调用谁', description: '只看下游 callee' },
  { id: 'entrySql', label: '入口到 SQL', description: '沿影响路径查看入口、业务代码和 SQL/table' }
];

export const overviewCapabilities = [
  {
    label: '覆盖范围',
    title: 'Java / JSP / Struts1 / Spring / SQL / Jar',
    text: '把页面、Action、业务代码、配置和数据访问放到一张证据图里。'
  },
  {
    label: '查询方式',
    title: '问答式入口 + 精确参数',
    text: '先用业务问题找对象，再保留 symbolId 和 JSON 供开发人员核对。'
  },
  {
    label: '结果表达',
    title: '路径、证据、置信度一起展示',
    text: '每条结论都能看到从哪个文件、哪类分析器、哪条关系推出来。'
  },
  {
    label: '性能策略',
    title: '先快后深，后台补强',
    text: '快速静态事实先返回，Tai-e、AI/RAG、FFM 按需增强，不阻塞首屏。'
  }
];

export const evidence: Evidence[] = [
  { type: 'JSP', file: 'user/edit.jsp', line: 2, confidence: 'CERTAIN', text: 'include file="/common/footer.jsp"' },
  { type: 'JSP', file: 'user/edit.jsp', line: 5, confidence: 'LIKELY', text: 'html:hidden property="userId"' },
  { type: 'Struts', file: 'WEB-INF/struts-config.xml', line: 0, confidence: 'CERTAIN', text: '/user/save -> UserSaveAction' },
  { type: 'Java', file: 'AdminUserAction.java', line: 18, confidence: 'LIKELY', text: 'LookupDispatchAction button.search -> search' },
  { type: 'MyBatis', file: 'UserMapper.xml', line: 7, confidence: 'LIKELY', text: 'users table read/write candidate' }
];

export const pathsByMode: Record<Mode, Array<[string, string]>> = {
  overview: [
    ['PROJECT', 'Java Web legacy project'],
    ['SCANS', 'Java / JSP / XML / SQL / Jar'],
    ['BUILDS_GRAPH', 'Neo4j active facts'],
    ['ANSWERS', 'Impact / variable / JSP / graph / SQL questions']
  ],
  impact: [
    ['JSP_PAGE', 'common/footer.jsp'],
    ['INCLUDES', 'user/edit.jsp'],
    ['SUBMITS_TO', '/user/save'],
    ['ROUTES_TO', 'UserSaveAction'],
    ['CALLS', 'UserService.save'],
    ['BINDS_TO', 'UserMapper.save'],
    ['WRITES_TABLE', 'users']
  ],
  variable: [
    ['JSP_INPUT', 'html:hidden property=userId'],
    ['WRITES_PARAM', 'request parameter userId'],
    ['READS_PARAM', 'request.getParameter("userId")'],
    ['BINDS_TO', 'ActionForm.userId'],
    ['CALLS', 'UserService.save(userId)']
  ],
  jsp: [
    ['JSP_PAGE', 'user/edit.jsp'],
    ['INCLUDES', 'common/footer.jsp'],
    ['SUBMITS_TO', '/user/save'],
    ['ROUTES_TO', 'UserSaveAction.execute'],
    ['FORWARDS_TO', 'user/detail.jsp'],
    ['READS_TABLE', 'users']
  ],
  graph: [
    ['METHOD', 'UserController.save'],
    ['CALLS', 'UserService.save'],
    ['CALLS', 'UserRepository.save'],
    ['BINDS_TO', 'UserMapper.insert']
  ],
  sql: [
    ['SQL_STATEMENT', 'UserMapper.findAll'],
    ['READS_TABLE', 'users'],
    ['IMPACTS', 'UserService.list'],
    ['ROUTES_TO', '/users']
  ],
  qa: [
    ['QUESTION', 'UserService.save 是什么'],
    ['EXACT_SYMBOL', 'UserService.save'],
    ['GRAPH_NEIGHBOR', 'UserAction.execute -> UserService.save'],
    ['EVIDENCE', '静态分析证据']
  ]
};

export const fallbackPlans: Record<Mode, QueryPlan> = {
  overview: {
    intent: 'PROJECT_OVERVIEW',
    endpoint: '/api/project/overview',
    method: 'GET',
    summary: 'Show project-level capabilities, analysis coverage, and safe entry points for non-developer users.',
    requiredParameters: [],
    defaultParameters: {},
    relationTypes: ['CALLS', 'ROUTES_TO', 'SUBMITS_TO', 'READS_PARAM', 'WRITES_TABLE'],
    resultView: 'PROJECT_OVERVIEW_VIEW'
  },
  impact: {
    intent: 'IMPACT_ANALYSIS',
    endpoint: '/api/impact/analyze',
    method: 'GET',
    summary: 'Run fast impact analysis from a changed symbol using active graph facts.',
    requiredParameters: ['projectId', 'snapshotId', 'changedSymbol'],
    defaultParameters: { maxDepth: '6', limit: '50' },
    relationTypes: ['CALLS', 'ROUTES_TO', 'SUBMITS_TO', 'INCLUDES', 'BINDS_TO', 'FORWARDS_TO'],
    resultView: 'IMPACT_REPORT_VIEW'
  },
  variable: {
    intent: 'VARIABLE_TRACE',
    endpoint: '/api/variables/trace/report',
    method: 'GET',
    summary: 'Trace request parameter sources and sinks through JSP pages, inputs, Struts, argument propagation, and table effects.',
    requiredParameters: ['projectId', 'snapshotId', 'symbolId'],
    defaultParameters: { maxDepth: '4', limit: '50' },
    relationTypes: ['DECLARES', 'WRITES_PARAM', 'READS_PARAM', 'PASSES_PARAM', 'BINDS_TO', 'COVERED_BY', 'READS_TABLE', 'WRITES_TABLE'],
    resultView: 'VARIABLE_TRACE_VIEW'
  },
  jsp: {
    intent: 'JSP_BACKEND_FLOW',
    endpoint: '/api/jsp/backend-flow/report',
    method: 'GET',
    summary: 'Find paths from JSP pages through includes, forms, Struts routes, Java calls, and SQL.',
    requiredParameters: ['projectId', 'snapshotId', 'symbolId'],
    defaultParameters: { maxDepth: '8', limit: '50' },
    relationTypes: ['INCLUDES', 'FORWARDS_TO', 'SUBMITS_TO', 'ROUTES_TO', 'CALLS', 'BINDS_TO'],
    resultView: 'JSP_FLOW_VIEW'
  },
  graph: {
    intent: 'CALL_GRAPH',
    endpoint: '/api/graph/callers/report or /api/graph/callees/report',
    method: 'GET',
    summary: 'Resolve callers or callees for an exact method symbol with active facts and evidence.',
    requiredParameters: ['projectId', 'snapshotId', 'symbolId'],
    defaultParameters: { maxDepth: '2', limit: '50' },
    relationTypes: ['CALLS', 'IMPLEMENTS', 'BRIDGES_TO'],
    resultView: 'GRAPH_NEIGHBOR_VIEW'
  },
  sql: {
    intent: 'SQL_TABLE_IMPACT',
    endpoint: '/api/graph/impact-paths/query',
    method: 'GET',
    summary: 'Expand SQL statement, table, or column impact through active graph paths.',
    requiredParameters: ['projectId', 'snapshotId', 'symbolId'],
    defaultParameters: { maxDepth: '6', limit: '50' },
    relationTypes: ['BINDS_TO', 'READS_TABLE', 'WRITES_TABLE', 'CALLS', 'ROUTES_TO'],
    resultView: 'SQL_TABLE_VIEW'
  },
  qa: {
    intent: 'RAG_SEMANTIC_SEARCH',
    endpoint: '/api/rag/answer-draft',
    method: 'GET',
    summary: 'Build an evidence-backed answer from exact symbols, summaries, historical reports, and graph neighbors.',
    requiredParameters: ['projectId', 'snapshotId', 'q'],
    defaultParameters: { limit: '20' },
    relationTypes: ['EXACT_SYMBOL', 'VECTOR', 'GRAPH_NEIGHBOR'],
    resultView: 'RAG_SEARCH_VIEW'
  }
};

export const fallbackViews: QueryResultView[] = [
  {
    name: 'PROJECT_OVERVIEW_VIEW',
    title: 'Project Overview',
    summary: 'Project-level status, supported artifact types, and guided entry points for analysis.',
    primaryFields: ['projectId', 'snapshotId', 'capabilities', 'analysisStatus'],
    evidenceFields: ['sourceType', 'confidence', 'evidenceKeys', 'path', 'lineStart', 'lineEnd']
  },
  {
    name: 'IMPACT_REPORT_VIEW',
    title: 'Impact Report',
    summary: 'Changed symbol, affected entrypoint, ordered path, confidence, risk, evidence, and truncation state.',
    primaryFields: ['reportId', 'changedSymbol', 'entrypoint', 'riskLevel', 'confidence', 'truncated'],
    evidenceFields: ['relationType', 'sourceType', 'confidence', 'evidenceKey', 'file', 'line']
  },
  {
    name: 'VARIABLE_TRACE_VIEW',
    title: 'Variable Trace',
    summary: 'Request parameter sources, sinks, argument propagation, table effects, ActionForm bindings, Validator coverage, and evidence.',
    primaryFields: ['parameterSymbolId', 'direction', 'endpoint', 'pathCount', 'path', 'confidence'],
    evidenceFields: ['incomingRelation', 'qualifier', 'sourceType', 'confidence', 'evidenceKeys', 'path', 'lineStart', 'lineEnd']
  },
  {
    name: 'JSP_FLOW_VIEW',
    title: 'JSP Flow',
    summary: 'JSP includes, form submission, Struts routing, Java calls, SQL/table links, and evidence path.',
    primaryFields: ['startSymbolId', 'endpoint', 'pathCount', 'path', 'confidence', 'truncated'],
    evidenceFields: ['incomingRelation', 'qualifier', 'sourceType', 'confidence', 'evidenceKeys', 'path', 'lineStart', 'lineEnd']
  },
  {
    name: 'GRAPH_NEIGHBOR_VIEW',
    title: 'Graph Neighbors',
    summary: 'Caller/callee paths for exact symbols with active relation facts, confidence, source type, and evidence keys.',
    primaryFields: ['startSymbolId', 'endpoint', 'pathCount', 'path', 'confidence'],
    evidenceFields: ['incomingRelation', 'qualifier', 'sourceType', 'confidence', 'evidenceKeys', 'path', 'lineStart', 'lineEnd']
  },
  {
    name: 'SQL_TABLE_VIEW',
    title: 'SQL Table Impact',
    summary: 'SQL statement, table, column, mapper, and upstream web entrypoint impact paths.',
    primaryFields: ['sqlSymbolId', 'tableSymbolId', 'columnSymbolId', 'entrypoint', 'relationType'],
    evidenceFields: ['sourceType', 'confidence', 'evidenceKey', 'file', 'line']
  },
  {
    name: 'RAG_SEARCH_VIEW',
    title: 'AI Q&A Evidence',
    summary: 'Evidence-backed answer draft with matched symbols, summaries, graph-neighbor evidence, and source keys.',
    primaryFields: ['answer', 'query', 'symbolId', 'displayName', 'score', 'matchKinds'],
    evidenceFields: ['evidenceKeys', 'sourceType', 'analyzer', 'path', 'lineStart', 'lineEnd']
  },
  {
    name: 'SYMBOL_PICKER_VIEW',
    title: 'Symbol Picker',
    summary: 'Candidate symbols before running exact graph, variable, JSP, SQL, or impact queries.',
    primaryFields: ['symbolId', 'kind', 'displayName', 'score'],
    evidenceFields: ['file', 'line', 'moduleKey', 'sourceRootKey']
  }
];

export const defaultParamsByMode: Record<Mode, Record<string, string>> = {
  overview: {
    projectId: 'shop',
    snapshotId: 's1'
  },
  impact: {
    projectId: 'shop',
    snapshotId: 's1',
    changedSymbol: 'method://shop/_root/src/main/java/com.acme.UserService#save()V',
    diffText: 'diff --git a/src/main/java/com/acme/UserService.java b/src/main/java/com/acme/UserService.java\n--- a/src/main/java/com/acme/UserService.java\n+++ b/src/main/java/com/acme/UserService.java\n@@ -1 +1 @@\n-old\n+new',
    maxDepth: '6',
    limit: '50'
  },
  variable: {
    projectId: 'shop',
    snapshotId: 's1',
    symbolId: 'request-parameter://shop/_root/_request/userId',
    maxDepth: '4',
    limit: '50'
  },
  jsp: {
    projectId: 'shop',
    snapshotId: 's1',
    symbolId: 'jsp-page://shop/_root/src/main/webapp/user/edit.jsp',
    maxDepth: '8',
    limit: '50'
  },
  graph: {
    projectId: 'shop',
    snapshotId: 's1',
    symbolId: 'method://shop/_root/src/main/java/com.acme.UserService#save()V',
    maxDepth: '2',
    limit: '50'
  },
  sql: {
    projectId: 'shop',
    snapshotId: 's1',
    symbolId: 'db-table://shop/_root/db/users',
    maxDepth: '6',
    limit: '50'
  },
  qa: {
    projectId: 'shop',
    snapshotId: 's1',
    q: 'UserService.save 是什么，影响哪些代码',
    limit: '20'
  }
};
