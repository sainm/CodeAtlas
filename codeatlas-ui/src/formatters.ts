import type { Evidence, Mode } from './types';

export function shortEvidenceKey(value: string) {
  const parts = value.split('|');
  if (parts.length >= 6) {
    const path = shortPath(parts[2]);
    const line = parts[3] && parts[3] !== '0' ? `:${parts[3]}` : '';
    return `${sourceLabel(parts[0])} ${path}${line}`;
  }
  return shortPath(value);
}

export function stringValue(value: unknown, fallback: string) {
  return typeof value === 'string' && value ? value : fallback;
}

export function numberValue(value: unknown) {
  return typeof value === 'number' ? value : 0;
}

export function confidenceValue(value: unknown): Evidence['confidence'] {
  return value === 'CERTAIN' || value === 'LIKELY' || value === 'POSSIBLE' ? value : 'POSSIBLE';
}

export function headline(mode: Mode) {
  switch (mode) {
    case 'overview':
      return '项目总览';
    case 'variable':
      return '变量从哪里来、流向哪里';
    case 'jsp':
      return '页面到后台处理链路';
    case 'graph':
      return '调用关系查询';
    case 'sql':
      return '数据库表影响';
    case 'qa':
      return 'AI 问答与证据';
    default:
      return '变更影响分析';
  }
}

export function businessStep(relationType: string, symbol: string) {
  return {
    relation: relationLabel(relationType),
    label: friendlyDisplayName(symbol),
    kind: symbolKindLabel(symbol),
    raw: symbol
  };
}

export function relationLabel(relationType: string) {
  const normalized = relationType.toUpperCase();
  switch (normalized) {
    case 'ENTRY':
      return '起点';
    case 'DECLARES':
      return '定义';
    case 'CALLS':
      return '调用业务处理';
    case 'IMPLEMENTS':
      return '实现接口';
    case 'EXTENDS':
      return '继承';
    case 'INJECTS':
      return '依赖组件';
    case 'ROUTES_TO':
      return '由后台处理';
    case 'SUBMITS_TO':
      return '提交表单';
    case 'INCLUDES':
      return '包含页面';
    case 'BINDS_TO':
      return '绑定数据访问';
    case 'PASSES_PARAM':
      return '传递输入值';
    case 'READS_PARAM':
      return '读取输入值';
    case 'WRITES_PARAM':
      return '写入输入值';
    case 'READS_TABLE':
      return '读取数据表';
    case 'WRITES_TABLE':
      return '修改数据表';
    case 'USES_CONFIG':
      return '使用配置';
    case 'FORWARDS_TO':
      return '跳转到';
    case 'BRIDGES_TO':
      return '对应实现';
    case 'SYNTHETIC_OF':
      return '编译生成';
    default:
      return normalized.replaceAll('_', ' ');
  }
}

export function friendlyDisplayName(value: string) {
  if (!value) {
    return '-';
  }
  const withoutScheme = value.includes('://') ? value.substring(value.indexOf('://') + 3) : value;
  const afterRoot = withoutScheme.split('/').slice(3).join('/');
  const candidate = afterRoot || withoutScheme;
  if (value.startsWith('method://')) {
    const hash = candidate.indexOf('#');
    const owner = hash >= 0 ? candidate.substring(0, hash) : candidate;
    const member = hash >= 0 ? candidate.substring(hash + 1) : '';
    return `${simpleClassName(owner)}${member ? '.' + member.replace(/\(.*$/, '') : ''}`;
  }
  if (value.startsWith('class://') || value.startsWith('interface://')) {
    return simpleClassName(candidate);
  }
  if (value.startsWith('jsp-page://') || value.startsWith('source-file://')) {
    return shortPath(candidate);
  }
  if (value.startsWith('jsp-form://')) {
    const hash = candidate.indexOf('#');
    const page = hash >= 0 ? candidate.substring(0, hash) : candidate;
    const form = hash >= 0 ? candidate.substring(hash + 1) : '';
    return form ? `${shortPath(page)} ${form}` : `页面表单 ${shortPath(page)}`;
  }
  if (value.startsWith('jsp-input://')) {
    const hash = candidate.indexOf('#');
    const page = hash >= 0 ? candidate.substring(0, hash) : candidate;
    const fragment = hash >= 0 ? candidate.substring(hash + 1) : '';
    const marker = ':input:';
    const markerIndex = fragment.lastIndexOf(marker);
    const inputName = markerIndex >= 0 ? fragment.substring(markerIndex + marker.length) : fragment;
    return inputName ? `页面输入 ${inputName}` : `页面输入 ${shortPath(page)}`;
  }
  if (value.startsWith('request-parameter://')) {
    const parts = candidate.split('/');
    const parameter = parts[parts.length - 1] || candidate;
    return `输入参数 ${parameter}`;
  }
  if (value.startsWith('action-path://')) {
    return candidate.startsWith('/') ? candidate : `/${candidate}`;
  }
  if (value.startsWith('api-endpoint://')) {
    return candidate.startsWith('/') ? candidate : `/${candidate}`;
  }
  if (value.startsWith('db-table://') || value.startsWith('db-column://')) {
    const parts = candidate.split('/');
    return parts[parts.length - 1] || candidate;
  }
  if (value.startsWith('sql-statement://')) {
    const hash = candidate.lastIndexOf('#');
    return hash >= 0 ? `SQL ${candidate.substring(hash + 1)}` : 'SQL 语句';
  }
  if (value.includes('#')) {
    return friendlyDisplayName(`method://x/y/z/${value}`);
  }
  return shortPath(value);
}

export function symbolKindLabel(symbol: string) {
  if (symbol.startsWith('jsp-page://') || symbol === 'JSP_PAGE') {
    return '页面';
  }
  if (symbol.startsWith('jsp-form://') || symbol === 'JSP_FORM') {
    return '页面表单';
  }
  if (symbol.startsWith('jsp-input://') || symbol === 'JSP_INPUT') {
    return '页面输入';
  }
  if (symbol.startsWith('action-path://') || symbol === 'ACTION_PATH') {
    return 'Struts Action';
  }
  if (symbol.startsWith('api-endpoint://')) {
    return '接口';
  }
  if (symbol.startsWith('method://')) {
    return 'Java 方法';
  }
  if (symbol.startsWith('class://') || symbol.startsWith('interface://')) {
    return 'Java 类';
  }
  if (symbol.startsWith('sql-statement://')) {
    return 'SQL';
  }
  if (symbol.startsWith('db-table://') || symbol === 'READS_TABLE' || symbol === 'WRITES_TABLE') {
    return '数据表';
  }
  if (symbol.startsWith('request-parameter://')) {
    return '输入参数';
  }
  return categoryLabel(symbol);
}

export function categoryLabel(category: string) {
  switch (category.toUpperCase()) {
    case 'JSP':
    case 'JSP_PAGE':
    case 'JSP_FORM':
    case 'JSP_INPUT':
      return '页面';
    case 'API':
    case 'API_ENDPOINT':
      return '接口';
    case 'ACTION':
    case 'ACTION_PATH':
    case 'STRUTS_ACTION':
      return 'Action';
    case 'CONTROLLER':
      return '控制器';
    case 'BLOGIC':
    case 'BUSINESS_LOGIC':
      return '业务逻辑';
    case 'UTILITY':
      return '公共工具';
    case 'SERVICE':
      return '服务';
    case 'DAO':
      return 'DAO';
    case 'MAPPER':
      return 'Mapper';
    case 'SQL':
    case 'SQL_STATEMENT':
      return 'SQL';
    case 'TABLE':
    case 'DB_TABLE':
      return '数据表';
    case 'DB_COLUMN':
      return '字段';
    case 'METHOD':
      return '方法';
    case 'CLASS':
    case 'INTERFACE':
      return '类';
    default:
      return category.replaceAll('_', ' ');
  }
}

export function confidenceLabel(confidence: string) {
  switch (confidence) {
    case 'CERTAIN':
      return '确定';
    case 'LIKELY':
      return '大概率';
    case 'POSSIBLE':
      return '可能';
    default:
      return confidence || '-';
  }
}

export function riskLabel(risk: string) {
  switch (risk) {
    case 'HIGH':
      return '高';
    case 'MEDIUM':
      return '中';
    case 'LOW':
      return '低';
    default:
      return risk || '-';
  }
}

export function sourceLabel(source: string) {
  switch (source.toUpperCase()) {
    case 'JSP':
    case 'JASPER':
    case 'JSP_FALLBACK':
      return '页面';
    case 'STRUTS_CONFIG':
      return 'Struts 配置';
    case 'SPOON':
      return 'Java 源码';
    case 'XML':
      return 'XML 配置';
    case 'ASM':
    case 'CLASSGRAPH':
      return '业务 Jar';
    case 'SQL':
      return 'SQL';
    case 'GIT':
      return 'Git 变更';
    default:
      return source || '证据';
  }
}

export function friendlyEvidenceText(text: string) {
  if (!text) {
    return '-';
  }
  return text
    .replaceAll('SUBMITS_TO', '提交表单')
    .replaceAll('ROUTES_TO', '后台处理')
    .replaceAll('CALLS', '调用')
    .replaceAll('BINDS_TO', '绑定')
    .replaceAll('PASSES_PARAM', '传递输入值')
    .replaceAll('READS_TABLE', '读取表')
    .replaceAll('WRITES_TABLE', '修改表')
    .replaceAll('WRITES_PARAM', '写入参数')
    .replaceAll('READS_PARAM', '读取参数');
}

export function businessPlanSummary(mode: Mode, fallback: string) {
  switch (mode) {
    case 'overview':
      return '用非开发人员也能理解的方式查看系统覆盖范围、常用查询入口、证据状态和后续分析方向。';
    case 'impact':
      return '从变更点出发，找出可能受影响的页面、Action、业务处理和数据表。';
    case 'variable':
      return '追踪一个输入值从页面进入后台后，被哪些代码读取、绑定或继续传递。';
    case 'jsp':
      return '展示 JSP 页面提交后经过哪个 Struts Action、业务逻辑、数据访问和跳转页面。';
    case 'graph':
      return '查看某段代码被谁调用，以及它继续调用了哪些业务处理。';
    case 'sql':
      return '从 SQL 或数据表出发，查看哪些页面、Action 或业务功能可能受影响。';
    case 'qa':
      return '用自然语言提问，但答案必须贴着静态分析证据、召回节点和调用路径展示。';
    default:
      return fallback;
  }
}

export function businessViewSummary(viewName: string, fallback: string) {
  switch (viewName) {
    case 'PROJECT_OVERVIEW_VIEW':
      return '展示当前项目分析能力、常用入口和状态概览。';
    case 'IMPACT_REPORT_VIEW':
      return '按业务对象展示变更影响，并保留证据路径用于确认。';
    case 'VARIABLE_TRACE_VIEW':
      return '展示输入值的来源、去向和中间绑定关系。';
    case 'JSP_FLOW_VIEW':
      return '展示页面到后台处理、业务逻辑和数据访问的完整链路。';
    case 'GRAPH_NEIGHBOR_VIEW':
      return '展示代码之间的上下游调用关系。';
    case 'SQL_TABLE_VIEW':
      return '展示数据表或 SQL 变化会影响到哪些业务入口。';
    case 'RAG_SEARCH_VIEW':
      return '展示问答草稿、命中的代码节点、相关证据 key 和可追溯路径。';
    default:
      return fallback;
  }
}

export function statusMessage(message: string) {
  switch (message) {
    case 'Static facts first':
      return '静态事实优先';
    case 'Planning from natural language':
      return '正在理解问题';
    case 'Plan ready':
      return '查询计划已生成';
    case 'Using local fallback plan':
      return '使用本地默认计划';
    case 'Executing read-only query':
      return '正在查询只读图谱';
    case 'Result loaded':
      return '结果已加载';
    default:
      return message;
  }
}

export function parameterLabel(key: string) {
  switch (key) {
    case 'projectId':
      return '项目';
    case 'snapshotId':
      return '版本';
    case 'symbolId':
      return '分析对象';
    case 'changedSymbol':
      return '变更对象';
    case 'diffText':
      return '变更内容';
    case 'maxDepth':
      return '分析深度';
    case 'limit':
      return '最多结果';
    case 'reportId':
      return '报告编号';
    case 'changeSetId':
      return '变更编号';
    default:
      return key;
  }
}

export function intentLabel(intent: string) {
  switch (intent) {
    case 'PROJECT_OVERVIEW':
      return '项目总览';
    case 'IMPACT_ANALYSIS':
    case 'DIFF_IMPACT_ANALYSIS':
      return '变更影响';
    case 'VARIABLE_TRACE':
      return '变量追踪';
    case 'JSP_BACKEND_FLOW':
      return '页面链路';
    case 'SQL_TABLE_IMPACT':
      return '数据表影响';
    case 'CALL_GRAPH':
      return '调用关系';
    case 'RAG_SEMANTIC_SEARCH':
      return 'AI 问答';
    default:
      return intent.replaceAll('_', ' ');
  }
}

function simpleClassName(owner: string) {
  const clean = owner.replace(/\(.*$/, '');
  const dot = clean.lastIndexOf('.');
  const slash = clean.lastIndexOf('/');
  const index = Math.max(dot, slash);
  return index >= 0 ? clean.substring(index + 1) : clean;
}

export function shortPath(path: string) {
  if (!path || path === '-') {
    return path || '-';
  }
  const normalized = path.replaceAll('\\', '/');
  const parts = normalized.split('/').filter(Boolean);
  if (parts.length <= 3) {
    return normalized;
  }
  return parts.slice(-3).join('/');
}

export function inferMode(query: string): Mode {
  const value = query.toLowerCase();
  if (['variable', 'parameter', 'param', 'source', 'sink', 'where from', 'where to', 'userId'.toLowerCase()].some((token) => value.includes(token))) {
    return 'variable';
  }
  if (['impact', 'diff', 'change', 'changed', 'risk'].some((token) => value.includes(token))) {
    return 'impact';
  }
  if (['caller', 'callee', 'call graph', 'who calls', 'calls whom'].some((token) => value.includes(token))) {
    return 'graph';
  }
  if (['sql', 'table', 'column', 'mapper', 'db'].some((token) => value.includes(token))) {
    return 'sql';
  }
  if (['explain', 'what is', 'what does', 'semantic', 'answer', '问答', '是什么', '做什么', '解释'].some((token) => value.includes(token))) {
    return 'qa';
  }
  if (['jsp', 'page', 'form', 'action', 'struts', 'dispatchaction', 'lookupdispatchaction'].some((token) => value.includes(token))) {
    return 'jsp';
  }
  return 'impact';
}

export function modeFromIntent(intent: string): Mode {
  switch (intent) {
    case 'DIFF_IMPACT_ANALYSIS':
      return 'impact';
    case 'VARIABLE_TRACE':
      return 'variable';
    case 'JSP_BACKEND_FLOW':
      return 'jsp';
    case 'SQL_TABLE_IMPACT':
      return 'sql';
    case 'RAG_SEMANTIC_SEARCH':
      return 'qa';
    case 'CALL_GRAPH':
      return 'graph';
    default:
      return 'impact';
  }
}

export function shortView(view: string) {
  return view.replace('_VIEW', '').replaceAll('_', ' ');
}
