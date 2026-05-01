import React, { useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Button } from 'antd';
import cytoscape from 'cytoscape';
import { Background, Controls, ReactFlow } from '@xyflow/react';
import {
  AlertTriangle,
  Braces,
  Database,
  FileCode2,
  GitBranch,
  Network,
  Search,
  ShieldCheck,
  Sparkles,
  Workflow
} from 'lucide-react';
import 'antd/dist/reset.css';
import '@xyflow/react/dist/style.css';
import './styles.css';

type Mode = 'overview' | 'impact' | 'variable' | 'jsp' | 'graph' | 'sql' | 'qa';
type RunStatus = 'idle' | 'planning' | 'executing' | 'fallback' | 'error';
type VariableTraceDirection = 'both' | 'source' | 'sink';
type GraphQueryMode = 'both' | 'callers' | 'callees' | 'entrySql';

type QueryPlan = {
  intent: string;
  endpoint: string;
  method: string;
  summary: string;
  requiredParameters: string[];
  defaultParameters: Record<string, string>;
  relationTypes: string[];
  resultView: string;
};

type QueryResultView = {
  name: string;
  title: string;
  summary: string;
  primaryFields: string[];
  evidenceFields: string[];
};

type Evidence = {
  type: string;
  file: string;
  line: number;
  confidence: 'CERTAIN' | 'LIKELY' | 'POSSIBLE';
  text: string;
};

type ResultStep = {
  symbolId?: string;
  incomingRelation?: string;
  qualifier?: string;
  sourceType?: string;
  analysisLayer?: string;
  confidence?: 'CERTAIN' | 'LIKELY' | 'POSSIBLE';
  evidenceKeys?: EvidenceKey[];
};

type EvidenceKey = {
  sourceType?: string;
  analyzer?: string;
  path?: string;
  lineStart?: number;
  lineEnd?: number;
  localPath?: string;
};

type ResultPath = {
  direction?: string;
  endpoint?: string;
  entrypoint?: string;
  changedSymbol?: string;
  confidence?: string;
  riskLevel?: string;
  truncated?: boolean;
  path?: ResultStep[];
};

type VariableTraceSection = {
  direction: 'SOURCE' | 'SINK';
  title: string;
  paths: ResultPath[];
};

type QueryResultPayload = {
  pathCount?: number;
  reportId?: string;
  answer?: string;
  query?: string;
  depth?: string;
  analysisLayer?: string;
  backgroundStatus?: AnalysisStageStatus[];
  analysisStatus?: AnalysisStageStatus[];
  capabilities?: Array<Record<string, string>>;
  entrypoints?: Array<Record<string, string>>;
  paths?: ResultPath[];
  evidenceList?: Array<Record<string, unknown>>;
  affectedSymbols?: AffectedSymbol[];
  results?: SymbolCandidate[];
  truncated?: boolean;
};

type AnalysisStageStatus = {
  id: string;
  label: string;
  state: 'ready' | 'running' | 'waiting' | 'disabled' | 'error';
  detail: string;
};

type AffectedSymbol = {
  category: string;
  symbolId: string;
  displayName: string;
};

type AssistantResult = {
  reportId: string;
  summary: string;
  riskExplanation: string;
  testSuggestions: string[];
  evidenceCount: number;
  aiAssisted: boolean;
};

type SymbolCandidate = {
  symbolId: string;
  kind: string;
  displayName: string;
  score: number;
  summary?: string;
  matchKinds?: string[];
  evidenceKeys?: string[];
  suggestedParameter?: string;
  reason?: string;
};

const modes: Array<{ id: Mode; label: string; icon: React.ComponentType<{ size?: number }> }> = [
  { id: 'overview', label: '项目总览', icon: Braces },
  { id: 'impact', label: '变更影响', icon: AlertTriangle },
  { id: 'variable', label: '变量去向', icon: Workflow },
  { id: 'jsp', label: '页面链路', icon: FileCode2 },
  { id: 'graph', label: '调用关系', icon: Network },
  { id: 'sql', label: '数据表影响', icon: Database }
  ,
  { id: 'qa', label: 'AI 问答', icon: Sparkles }
];

type QuickTemplate = {
  title: string;
  example: string;
  mode: Mode;
  query: string;
  parameterOverrides?: Record<string, string>;
};

const quickTemplates: QuickTemplate[] = [
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
  }
  ,
  {
    title: '代码问答',
    example: 'UserService.save 是什么，旁边给我证据',
    mode: 'qa',
    query: 'UserService.save 是什么，旁边给我证据',
    parameterOverrides: { q: 'UserService.save 是什么，旁边给我证据' }
  }
];

const graphModes: Array<{ id: GraphQueryMode; label: string; description: string }> = [
  { id: 'both', label: '上下游', description: '同时查询调用方和被调用方' },
  { id: 'callers', label: '谁调用它', description: '只看上游 caller' },
  { id: 'callees', label: '它调用谁', description: '只看下游 callee' },
  { id: 'entrySql', label: '入口到 SQL', description: '沿影响路径查看入口、业务代码和 SQL/table' }
];

const overviewCapabilities = [
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

const evidence: Evidence[] = [
  { type: 'JSP', file: 'user/edit.jsp', line: 2, confidence: 'CERTAIN', text: 'include file="/common/footer.jsp"' },
  { type: 'JSP', file: 'user/edit.jsp', line: 5, confidence: 'LIKELY', text: 'html:hidden property="userId"' },
  { type: 'Struts', file: 'WEB-INF/struts-config.xml', line: 0, confidence: 'CERTAIN', text: '/user/save -> UserSaveAction' },
  { type: 'Java', file: 'AdminUserAction.java', line: 18, confidence: 'LIKELY', text: 'LookupDispatchAction button.search -> search' },
  { type: 'MyBatis', file: 'UserMapper.xml', line: 7, confidence: 'LIKELY', text: 'users table read/write candidate' }
];

const pathsByMode: Record<Mode, Array<[string, string]>> = {
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

const fallbackPlans: Record<Mode, QueryPlan> = {
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

const fallbackViews: QueryResultView[] = [
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

const defaultParamsByMode: Record<Mode, Record<string, string>> = {
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

function App() {
  const [mode, setMode] = useState<Mode>('impact');
  const [query, setQuery] = useState('看看 userId 从页面到后台去了哪里');
  const [plan, setPlan] = useState<QueryPlan>(fallbackPlans.impact);
  const [views, setViews] = useState<QueryResultView[]>(fallbackViews);
  const [status, setStatus] = useState<RunStatus>('idle');
  const [parameters, setParameters] = useState<Record<string, string>>(defaultParamsByMode.impact);
  const [result, setResult] = useState<QueryResultPayload | null>(null);
  const [assistant, setAssistant] = useState<AssistantResult | null>(null);
  const [candidates, setCandidates] = useState<SymbolCandidate[]>([]);
  const [message, setMessage] = useState('Static facts first');
  const [showRawJson, setShowRawJson] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [traceDirection, setTraceDirection] = useState<VariableTraceDirection>('both');
  const [graphQueryMode, setGraphQueryMode] = useState<GraphQueryMode>('both');

  const activeMode = useMemo(() => modes.find((item) => item.id === mode)!, [mode]);
  const activeView = useMemo(
    () => views.find((view) => view.name === plan.resultView) ?? fallbackViews.find((view) => view.name === plan.resultView) ?? fallbackViews[0],
    [plan.resultView, views]
  );
  const ActiveIcon = activeMode.icon;
  const pathNodes = resultPathNodes(result) ?? pathsByMode[mode];
  const variableTraceSections = mode === 'variable' ? variableTraceSectionsFromResult(result) : [];
  const visibleVariableTraceSections = filteredVariableTraceSections(variableTraceSections, traceDirection);
  const visualPathNodes = visibleVariableTraceSections[0]?.paths[0]
    ? pathNodesFromResultPath(visibleVariableTraceSections[0].paths[0])
    : pathNodes;
  const resultEvidence = resultEvidenceRows(result);
  const evidenceRows = resultEvidence.length > 0 ? resultEvidence : evidence;
  const summary = resultSummary(result, evidenceRows);
  const analysisStages = analysisStageStatuses(status, mode, plan, result, assistant);
  const rawJson = useMemo(() => JSON.stringify({
    query,
    queryPlan: plan,
    resultView: activeView,
    parameters,
    result,
    pathPreview: pathNodes.map(([relationType, symbol]) => ({ relationType, symbol })),
    evidencePreview: evidenceRows,
    analysisStages,
    graphQueryMode,
    variableTraceDirection: traceDirection
  }, null, 2), [activeView, analysisStages, evidenceRows, graphQueryMode, parameters, pathNodes, plan, query, result, traceDirection]);

  useEffect(() => {
    let active = true;
    fetch('/api/query/views')
      .then((response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }
        return response.json() as Promise<{ views: QueryResultView[] }>;
      })
      .then((payload) => {
        if (active && payload.views.length > 0) {
          setViews(payload.views);
        }
      })
      .catch(() => {
        if (active) {
          setViews(fallbackViews);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  async function runQueryPlan() {
    setStatus('planning');
    setMessage('Planning from natural language');
    try {
      const resolveUrl = `/api/query/resolve?q=${encodeURIComponent(query)}&projectId=${encodeURIComponent(parameters.projectId ?? 'shop')}&snapshotId=${encodeURIComponent(parameters.snapshotId ?? 's1')}&limit=8`;
      const response = await fetch(resolveUrl);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const payload = (await response.json()) as { plan: QueryPlan; candidates?: SymbolCandidate[] };
      const nextPlan = payload.plan;
      const nextMode = modeFromIntent(nextPlan.intent);
      setPlan(nextPlan);
      setMode(nextMode);
      setParameters((current) => mergeParams(defaultParamsByMode[nextMode], current, nextPlan));
      setResult(null);
      setAssistant(null);
      setCandidates(payload.candidates ?? []);
      setStatus('idle');
      setMessage('Plan ready');
    } catch {
      const nextMode = inferMode(query);
      setMode(nextMode);
      setPlan(fallbackPlans[nextMode]);
      setParameters((current) => mergeParams(defaultParamsByMode[nextMode], current, fallbackPlans[nextMode]));
      setResult(null);
      setAssistant(null);
      void loadCandidates(query);
      setStatus('fallback');
      setMessage('Using local fallback plan');
    }
  }

  async function loadCandidates(searchText: string) {
    const trimmed = searchText.trim();
    if (!trimmed) {
      setCandidates([]);
      return;
    }
    try {
      const response = await fetch(`/api/query/resolve?q=${encodeURIComponent(trimmed)}&projectId=${encodeURIComponent(parameters.projectId ?? 'shop')}&snapshotId=${encodeURIComponent(parameters.snapshotId ?? 's1')}&limit=8`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const payload = (await response.json()) as { candidates?: SymbolCandidate[] };
      setCandidates(payload.candidates ?? []);
    } catch {
      setCandidates([]);
    }
  }

  async function executeQuery() {
    setStatus('executing');
    setMessage('Executing read-only query');
    try {
      const runtimeParameters = plan.requiredParameters.includes('q') && !parameters.q
        ? { ...parameters, q: query }
        : parameters;
      const endpoints = mode === 'graph' ? graphEndpointList(graphQueryMode) : endpointList(plan.endpoint);
      const payloads = await Promise.all(endpoints.map((endpoint) => {
        const url = endpointUrl(endpoint, plan, runtimeParameters);
        return fetch(url).then((response) => {
          if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
          }
          return response.json() as Promise<QueryResultPayload>;
        });
      }));
      const merged = mergePayloads(payloads);
      setResult(merged);
      if (merged.reportId) {
        void loadAssistant(merged.reportId);
      } else {
        setAssistant(null);
      }
      setStatus('idle');
      setMessage('Result loaded');
    } catch (error) {
      setResult(null);
      setAssistant(null);
      setStatus('error');
      setMessage(error instanceof Error ? error.message : 'Query failed');
    }
  }

  async function loadAssistant(reportId: string) {
    try {
      const response = await fetch(`/api/reports-assistant?reportId=${encodeURIComponent(reportId)}`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      setAssistant((await response.json()) as AssistantResult);
    } catch {
      setAssistant(null);
    }
  }

  function applyTemplate(template: QuickTemplate) {
    const nextPlan = fallbackPlans[template.mode];
    setMode(template.mode);
    setQuery(template.query);
    setPlan(nextPlan);
    setParameters({
      ...defaultParamsByMode[template.mode],
      ...nextPlan.defaultParameters,
      ...template.parameterOverrides
    });
    setResult(null);
    setAssistant(null);
    setCandidates([]);
    setMessage('Plan ready');
    setStatus('idle');
    setShowAdvanced(false);
    setGraphQueryMode(template.mode === 'graph' ? 'both' : graphQueryMode);
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <Braces size={22} />
          <span>CodeAtlas</span>
        </div>
        <nav aria-label="CodeAtlas views">
          {modes.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.id}
                className={item.id === mode ? 'active' : ''}
                onClick={() => {
                  setMode(item.id);
                  setPlan(fallbackPlans[item.id]);
                  setParameters(defaultParamsByMode[item.id]);
                  setResult(null);
                  setAssistant(null);
                  setCandidates([]);
                  setStatus('idle');
                  setMessage('Static facts first');
                  setGraphQueryMode(item.id === 'graph' ? 'both' : graphQueryMode);
                }}
                type="button"
                title={item.label}
              >
                <Icon size={17} />
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div className="query-box">
            <Search size={18} />
            <input value={query} onChange={(event) => setQuery(event.target.value)} aria-label="CodeAtlas query" />
            <button type="button" onClick={runQueryPlan}>生成查询</button>
            <button type="button" onClick={executeQuery}>查看结果</button>
          </div>
          <div className={`status-pill ${status}`}>
            <ShieldCheck size={17} />
            <span>{statusMessage(message)}</span>
          </div>
        </header>

        <section className="quick-start-panel">
          <div>
            <p className="eyebrow">常见问题</p>
            <h2>不需要知道类名或方法名，先从业务问题开始</h2>
          </div>
          <div className="quick-actions">
            {quickTemplates.map((template) => (
              <button key={template.title} type="button" onClick={() => applyTemplate(template)}>
                <span>{template.title}</span>
                <strong>{template.example}</strong>
              </button>
            ))}
          </div>
        </section>

        <section className="summary-band">
          <div>
            <p className="eyebrow">{intentLabel(plan.intent)}</p>
            <h1>{headline(mode)}</h1>
            <p>{businessPlanSummary(mode, plan.summary)}</p>
          </div>
          <div className="metrics" aria-label="analysis metrics">
            <Metric label="分析深度" value={plan.defaultParameters.maxDepth ?? '-'} />
            <Metric label="最多结果" value={plan.defaultParameters.limit ?? '-'} />
            <Metric label="链路数" value={result?.pathCount?.toString() ?? result?.paths?.length?.toString() ?? '-'} />
            <Metric label="已截断" value={result?.truncated === true ? '是' : '否'} />
          </div>
        </section>

        {mode === 'overview' ? (
          <ProjectOverviewPanel result={result} applyTemplate={applyTemplate} />
        ) : null}

        <section className="analysis-status-panel">
          <div className="panel-title">
            <ShieldCheck size={18} />
            <h2>后台分析状态</h2>
          </div>
          <div className="analysis-status-grid">
            {analysisStages.map((stage) => (
              <div key={stage.id} className={`analysis-stage ${stage.state}`}>
                <span>{stage.label}</span>
                <strong>{stageStateLabel(stage.state)}</strong>
                <small>{stage.detail}</small>
              </div>
            ))}
          </div>
        </section>

        <section className="answer-summary">
          <div>
            <p className="eyebrow">结论摘要</p>
            <h2>{summary.title}</h2>
            <p>{summary.text}</p>
          </div>
          {result?.truncated ? (
            <button type="button" onClick={() => expandAndExecute(plan, setParameters, executeQuery)}>
              <Network size={16} />
              <span>展开更多</span>
            </button>
          ) : null}
        </section>

        {result?.answer ? (
          <section className="rag-answer-panel">
            <div className="rag-answer-copy">
              <p className="eyebrow">AI 问答答案</p>
              <h2>{result.answer}</h2>
              <p className="evidence-note">答案基于 {result.results?.length ?? 0} 个召回节点和静态分析证据生成。</p>
            </div>
            <div className="rag-evidence-side">
              <div>
                <p className="eyebrow">证据节点</p>
                <div className="rag-node-list">
                  {(result.results ?? []).slice(0, 6).map((item) => (
                    <div key={item.symbolId} className="rag-node" title={item.symbolId}>
                      <span>{categoryLabel(item.kind)}</span>
                      <strong>{friendlyDisplayName(item.displayName || item.symbolId)}</strong>
                      {item.summary ? <small>{item.summary}</small> : null}
                    </div>
                  ))}
                </div>
              </div>
              <div>
                <p className="eyebrow">证据路径</p>
                <div className="rag-evidence-keys">
                  {ragEvidenceKeys(result).slice(0, 10).map((key) => (
                    <span key={key} title={key}>{shortEvidenceKey(key)}</span>
                  ))}
                </div>
              </div>
            </div>
          </section>
        ) : null}

        {assistant ? (
          <section className="assistant-panel">
            <div>
              <p className="eyebrow">{assistant.aiAssisted ? 'AI 辅助说明' : '静态分析说明'}</p>
              <h2>{assistant.summary}</h2>
              <p>{assistant.riskExplanation}</p>
              <p className="evidence-note">基于 {assistant.evidenceCount} 条静态分析证据生成。</p>
            </div>
            <ul>
              {assistant.testSuggestions.map((suggestion) => (
                <li key={suggestion}>{suggestion}</li>
              ))}
            </ul>
          </section>
        ) : null}

        {result?.affectedSymbols && result.affectedSymbols.length > 0 ? (
          <section className="affected-panel">
            <div className="panel-title">
              <Network size={18} />
              <h2>受影响对象</h2>
            </div>
            <div className="affected-list">
              {result.affectedSymbols.slice(0, 24).map((symbol) => (
                <div key={symbol.symbolId} className="affected-item" title={symbol.symbolId}>
                  <span>{categoryLabel(symbol.category)}</span>
                  <strong>{friendlyDisplayName(symbol.displayName || symbol.symbolId)}</strong>
                </div>
              ))}
            </div>
          </section>
        ) : null}

        {mode === 'graph' ? (
          <GraphExplorerControl
            value={graphQueryMode}
            onChange={setGraphQueryMode}
            parameters={parameters}
            setParameters={setParameters}
            result={result}
            executeQuery={executeQuery}
          />
        ) : null}

        <section className="tool-grid">
          <article className="path-panel">
            <div className="panel-title">
              <ActiveIcon size={18} />
              <h2>业务链路</h2>
            </div>
            {mode === 'variable' && variableTraceSections.length > 0 ? (
              <TraceDirectionControl value={traceDirection} onChange={setTraceDirection} sections={variableTraceSections} />
            ) : null}
            {visibleVariableTraceSections.length > 0 ? (
              <div className="trace-groups">
                {visibleVariableTraceSections.map((section) => (
                  <div className="trace-group" key={section.direction}>
                    <div className="trace-group-header">
                      <h3>{section.title}</h3>
                      <span>{section.paths.length} 条路径</span>
                    </div>
                    {section.paths.slice(0, 4).map((tracePath, index) => (
                      <PathList
                        key={`${section.direction}-${index}`}
                        nodes={pathNodesFromResultPath(tracePath)}
                      />
                    ))}
                    {section.paths.length > 4 ? (
                      <p className="trace-group-note">仅显示前 4 条，展开更多可在高级设置中提高 limit。</p>
                    ) : null}
                  </div>
                ))}
              </div>
            ) : (
              <PathList nodes={pathNodes} />
            )}
            <PathFlowPanel nodes={visualPathNodes} />
          </article>

          <article className="plan-panel">
            <div className="panel-title">
              <Sparkles size={18} />
              <h2>本次查询</h2>
            </div>
            <div className="business-query-card">
              <span>{headline(mode)}</span>
              <p>{businessPlanSummary(mode, plan.summary)}</p>
              <button type="button" onClick={executeQuery}>直接查看</button>
            </div>
            <div className="relation-cloud">
              {plan.relationTypes.length === 0 ? <span>符号</span> : plan.relationTypes.map((relation) => <span key={relation}>{relationLabel(relation)}</span>)}
            </div>
            <button className="advanced-toggle" type="button" onClick={() => setShowAdvanced((value) => !value)}>
              {showAdvanced ? '收起高级设置' : '高级设置'}
            </button>
            {showAdvanced ? (
              <>
                <dl className="plan-list">
                  <div>
                    <dt>接口</dt>
                    <dd>{plan.endpoint}</dd>
                  </div>
                  <div>
                    <dt>必填参数</dt>
                    <dd>{plan.requiredParameters.join(', ')}</dd>
                  </div>
                  <div>
                    <dt>默认值</dt>
                    <dd>{Object.entries(plan.defaultParameters).map(([key, value]) => `${key}=${value}`).join(', ') || '-'}</dd>
                  </div>
                  <div>
                    <dt>结果视图</dt>
                    <dd>{shortView(plan.resultView)}</dd>
                  </div>
                </dl>
                <div className="parameter-grid" aria-label="query parameters">
                  {parameterKeys(plan).map((key) => (
                    <label key={key}>
                      <span>{parameterLabel(key)}</span>
                      <input
                        value={parameters[key] ?? ''}
                        onChange={(event) => setParameters((current) => ({ ...current, [key]: event.target.value }))}
                      />
                    </label>
                  ))}
                </div>
              </>
            ) : null}
          </article>
        </section>

        {candidates.length > 0 ? (
          <section className="candidate-panel">
            <div className="panel-title">
              <Search size={18} />
              <h2>候选对象</h2>
            </div>
            <div className="candidate-list">
              {candidates.map((candidate) => (
                <button
                  key={candidate.symbolId}
                  type="button"
                  onClick={() => selectCandidate(candidate, plan, setParameters)}
                  title={candidate.symbolId}
                >
                  <span className="candidate-kind">{categoryLabel(candidate.kind)}</span>
                  <strong>{friendlyDisplayName(candidate.displayName)}</strong>
                  <span className="candidate-score">{candidate.score.toFixed(0)}</span>
                  <small>{candidate.symbolId}</small>
                  {candidate.reason ? <em>{candidate.reason}</em> : null}
                </button>
              ))}
            </div>
          </section>
        ) : null}

        <section className="evidence-panel">
          <div className="panel-title">
            <GitBranch size={18} />
              <h2>证据列表</h2>
          </div>
          <table>
            <thead>
              <tr>
                <th>来源</th>
                <th>文件</th>
                <th>行号</th>
                <th>可信度</th>
                <th>证据</th>
              </tr>
            </thead>
            <tbody>
              {evidenceRows.map((item) => (
                <tr key={`${item.file}-${item.line}-${item.text}`}>
                  <td>{sourceLabel(item.type)}</td>
                  <td title={item.file}>{shortPath(item.file)}</td>
                  <td>{item.line || '-'}</td>
                  <td><span className={`confidence ${item.confidence.toLowerCase()}`}>{confidenceLabel(item.confidence)}</span></td>
                  <td>{friendlyEvidenceText(item.text)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section className="detail-band">
          <div>
            <h2>{activeView.title}</h2>
            <p>{businessViewSummary(activeView.name, activeView.summary)}</p>
            <div className="contract-fields" aria-label="primary result fields">
              {activeView.primaryFields.map((field) => <span key={field}>{field}</span>)}
            </div>
            <div className="contract-fields evidence-fields" aria-label="evidence result fields">
              {activeView.evidenceFields.map((field) => <span key={field}>{field}</span>)}
            </div>
          </div>
          <Button type="primary" title="Toggle raw JSON" onClick={() => setShowRawJson((value) => !value)}>
            <FileCode2 size={16} />
            <span>原始 JSON</span>
          </Button>
        </section>
        {showRawJson ? (
          <section className="raw-json-panel" aria-label="raw query and result contract json">
            <pre>{rawJson}</pre>
          </section>
        ) : null}
      </section>
    </main>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function overviewCapabilityItems(result: QueryResultPayload | null) {
  const items = result?.capabilities ?? [];
  if (items.length === 0) {
    return overviewCapabilities;
  }
  return items.map((item) => ({
    label: item.label ?? item.status ?? '能力',
    title: item.title ?? '-',
    text: item.text ?? item.detail ?? item.status ?? '由后端项目总览接口返回'
  }));
}

function overviewEntrypointItems(result: QueryResultPayload | null) {
  const items = result?.entrypoints ?? [];
  if (items.length === 0) {
    return quickTemplates.slice(0, 5).map((template) => ({
      mode: template.mode,
      label: template.title,
      endpoint: template.example,
      template
    }));
  }
  return items
    .map((item) => {
      const mode = modeFromEntrypoint(item.mode);
      const template = quickTemplates.find((candidate) => candidate.mode === mode) ?? quickTemplates[0];
      return {
        mode,
        label: item.label ?? template.title,
        endpoint: item.endpoint ?? template.example,
        template
      };
    })
    .slice(0, 6);
}

function modeFromEntrypoint(value: string | undefined): Mode {
  if (value === 'impact' || value === 'variable' || value === 'jsp' || value === 'graph' || value === 'sql' || value === 'qa') {
    return value;
  }
  return 'impact';
}

function ProjectOverviewPanel({
  result,
  applyTemplate
}: {
  result: QueryResultPayload | null;
  applyTemplate: (template: QuickTemplate) => void;
}) {
  const capabilities = overviewCapabilityItems(result);
  const entrypoints = overviewEntrypointItems(result);
  return (
    <section className="project-overview-panel">
      <div className="overview-capabilities">
        {capabilities.map((item) => (
          <article key={item.title}>
            <span>{item.label}</span>
            <strong>{item.title}</strong>
            <p>{item.text}</p>
          </article>
        ))}
      </div>
      <div className="overview-entrypoints">
        <div className="panel-title">
          <Sparkles size={18} />
          <h2>常用入口</h2>
        </div>
        <div className="overview-entry-grid">
          {entrypoints.map((entrypoint) => (
            <button
              key={`${entrypoint.mode}-${entrypoint.endpoint}`}
              type="button"
              onClick={() => applyTemplate(entrypoint.template)}
            >
              <span>{entrypoint.label}</span>
              <strong>{entrypoint.endpoint}</strong>
            </button>
          ))}
        </div>
      </div>
    </section>
  );
}

function TraceDirectionControl({
  value,
  onChange,
  sections
}: {
  value: VariableTraceDirection;
  onChange: (value: VariableTraceDirection) => void;
  sections: VariableTraceSection[];
}) {
  const sourceCount = sections.find((section) => section.direction === 'SOURCE')?.paths.length ?? 0;
  const sinkCount = sections.find((section) => section.direction === 'SINK')?.paths.length ?? 0;
  const options: Array<{ value: VariableTraceDirection; label: string; count: number }> = [
    { value: 'both', label: '全部', count: sourceCount + sinkCount },
    { value: 'source', label: '来源', count: sourceCount },
    { value: 'sink', label: '流向', count: sinkCount }
  ];

  return (
    <div className="trace-direction-control" aria-label="variable trace direction">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          className={value === option.value ? 'active' : ''}
          onClick={() => onChange(option.value)}
          disabled={option.value !== 'both' && option.count === 0}
        >
          <span>{option.label}</span>
          <strong>{option.count}</strong>
        </button>
      ))}
    </div>
  );
}

function GraphExplorerControl({
  value,
  onChange,
  parameters,
  setParameters,
  result,
  executeQuery
}: {
  value: GraphQueryMode;
  onChange: React.Dispatch<React.SetStateAction<GraphQueryMode>>;
  parameters: Record<string, string>;
  setParameters: React.Dispatch<React.SetStateAction<Record<string, string>>>;
  result: QueryResultPayload | null;
  executeQuery: () => Promise<void>;
}) {
  const nodes = graphNodeCandidates(result);
  return (
    <section className="graph-explorer-panel">
      <div className="panel-title">
        <Network size={18} />
        <h2>图谱探索</h2>
      </div>
      <div className="graph-mode-toolbar" aria-label="graph query mode">
        {graphModes.map((item) => (
          <button
            key={item.id}
            type="button"
            className={item.id === value ? 'active' : ''}
            onClick={() => onChange(item.id)}
            title={item.description}
          >
            {item.label}
          </button>
        ))}
      </div>
      <div className="graph-query-controls">
        <label>
          <span>中心节点</span>
          <input
            value={parameters.symbolId ?? ''}
            onChange={(event) => setParameters((current) => ({ ...current, symbolId: event.target.value }))}
          />
        </label>
        <label>
          <span>深度</span>
          <input
            inputMode="numeric"
            value={parameters.maxDepth ?? '2'}
            onChange={(event) => setParameters((current) => ({ ...current, maxDepth: event.target.value }))}
          />
        </label>
        <label>
          <span>数量</span>
          <input
            inputMode="numeric"
            value={parameters.limit ?? '50'}
            onChange={(event) => setParameters((current) => ({ ...current, limit: event.target.value }))}
          />
        </label>
      </div>
      {nodes.length > 0 ? (
        <div className="graph-expand-list">
          {nodes.slice(0, 12).map((node) => (
            <button
              key={node}
              type="button"
              title={node}
              onClick={() => expandGraphNode(node, setParameters, executeQuery)}
            >
              <span>{symbolKindLabel(node)}</span>
              <strong>{friendlyDisplayName(node)}</strong>
            </button>
          ))}
        </div>
      ) : null}
      <CytoscapeGraph result={result} />
    </section>
  );
}

function CytoscapeGraph({ result }: { result: QueryResultPayload | null }) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const elements = useMemo(() => cytoscapeElements(result), [result]);

  useEffect(() => {
    if (!containerRef.current || elements.length === 0) {
      return;
    }
    const cy = cytoscape({
      container: containerRef.current,
      elements,
      layout: { name: 'breadthfirst', directed: true, padding: 18, spacingFactor: 1.15 },
      style: [
        {
          selector: 'node',
          style: {
            'background-color': '#2f6f73',
            color: '#182230',
            label: 'data(label)',
            'font-size': '10px',
            'text-wrap': 'wrap',
            'text-max-width': '110px',
            'text-valign': 'bottom',
            'text-margin-y': 6
          }
        },
        {
          selector: 'edge',
          style: {
            'curve-style': 'bezier',
            'line-color': '#8fb9b7',
            'target-arrow-color': '#8fb9b7',
            'target-arrow-shape': 'triangle',
            label: 'data(label)',
            'font-size': '9px',
            'text-background-color': '#ffffff',
            'text-background-opacity': 0.86
          }
        }
      ]
    });
    cy.fit(undefined, 18);
    return () => {
      cy.destroy();
    };
  }, [elements]);

  if (elements.length === 0) {
    return null;
  }
  return <div ref={containerRef} className="cytoscape-graph" aria-label="Cytoscape graph explorer" />;
}

function PathFlowPanel({ nodes }: { nodes: Array<[string, string]> }) {
  const flowNodes = nodes.map(([relationType, symbol], index) => ({
    id: flowId(index),
    position: { x: index * 210, y: index % 2 === 0 ? 20 : 96 },
    data: { label: `${relationLabel(relationType)}\n${friendlyDisplayName(symbol)}` },
    type: 'default'
  }));
  const flowEdges = nodes.slice(1).map(([relationType], index) => ({
    id: `e-${index}`,
    source: flowId(index),
    target: flowId(index + 1),
    label: relationLabel(relationType),
    animated: false
  }));
  return (
    <div className="path-flow-panel" aria-label="React Flow path view">
      <ReactFlow nodes={flowNodes} edges={flowEdges} fitView nodesDraggable={false} nodesConnectable={false}>
        <Background />
        <Controls showInteractive={false} />
      </ReactFlow>
    </div>
  );
}

function PathList({ nodes }: { nodes: Array<[string, string]> }) {
  const steps = nodes.map(([relationType, symbol]) => businessStep(relationType, symbol));
  return (
    <ol className="path">
      {steps.map((step, index) => (
        <li key={`${index}-${step.relation}-${step.raw}`} title={step.raw}>
          <span>{step.relation}</span>
          <strong>{step.label}</strong>
          <small>{step.kind}</small>
        </li>
      ))}
    </ol>
  );
}

function endpointList(endpoint: string) {
  const normalized = endpoint.replaceAll(' or ', ' and ');
  return normalized.split(' and ')
    .map((value) => value.trim())
    .filter((value) => value.startsWith('/api/'));
}

function graphEndpointList(mode: GraphQueryMode) {
  switch (mode) {
    case 'callers':
      return ['/api/graph/callers/report'];
    case 'callees':
      return ['/api/graph/callees/report'];
    case 'entrySql':
      return ['/api/graph/impact-paths/query'];
    case 'both':
    default:
      return ['/api/graph/callers/report', '/api/graph/callees/report'];
  }
}

function endpointUrl(endpoint: string, plan: QueryPlan, parameters: Record<string, string>) {
  const query = new URLSearchParams();
  const merged = { ...plan.defaultParameters, ...parameters };
  for (const key of parameterKeys(plan)) {
    const value = merged[key];
    if (value != null && value !== '') {
      query.set(key, value);
    }
  }
  return `${endpoint}?${query.toString()}`;
}

function parameterKeys(plan: QueryPlan) {
  return Array.from(new Set([...plan.requiredParameters, ...Object.keys(plan.defaultParameters)]));
}

function mergeParams(defaults: Record<string, string>, current: Record<string, string>, plan: QueryPlan) {
  const next = { ...defaults, ...plan.defaultParameters };
  for (const key of Object.keys(current)) {
    if (key in next && current[key]) {
      next[key] = current[key];
    }
  }
  return next;
}

function mergePayloads(payloads: QueryResultPayload[]) {
  if (payloads.length === 1) {
    return payloads[0];
  }
  const paths = payloads.flatMap((payload) => payload.paths ?? []);
  const evidenceList = payloads.flatMap((payload) => payload.evidenceList ?? []);
  const results = payloads.flatMap((payload) => payload.results ?? []);
  return {
    ...payloads[0],
    pathCount: paths.length,
    paths,
    evidenceList,
    results,
    truncated: payloads.some((payload) => payload.truncated)
  };
}

function resultSummary(result: QueryResultPayload | null, evidenceRows: Evidence[]) {
  if (!result) {
    return {
      title: '可以开始查询',
      text: '先输入问题并生成计划，确认对象后执行查询。系统会优先展示业务链路，再保留原始证据用于核对。'
    };
  }
  const pathCount = result.pathCount ?? result.paths?.length ?? 0;
  const confidence = confidenceLabel(strongestConfidence(result.paths ?? []));
  const risk = riskLabel(strongestRisk(result.paths ?? []));
  const truncated = result.truncated ? ' 结果已截断，可以展开查看更多链路。' : '';
  return {
    title: `找到 ${pathCount} 条相关链路`,
    text: `可信度：${confidence}；风险：${risk}；证据：${evidenceRows.length} 条。${truncated}`
  };
}

function strongestConfidence(paths: ResultPath[]) {
  const order = ['CERTAIN', 'LIKELY', 'POSSIBLE'];
  const values = paths.map((path) => path.confidence).filter(Boolean) as string[];
  return order.find((value) => values.includes(value)) ?? '-';
}

function strongestRisk(paths: ResultPath[]) {
  const order = ['HIGH', 'MEDIUM', 'LOW'];
  const values = paths.map((path) => path.riskLevel).filter(Boolean) as string[];
  return order.find((value) => values.includes(value)) ?? '-';
}

function expandAndExecute(
  plan: QueryPlan,
  setParameters: React.Dispatch<React.SetStateAction<Record<string, string>>>,
  executeQuery: () => Promise<void>
) {
  setParameters((current) => ({
    ...current,
    maxDepth: String(Math.min(Number(current.maxDepth ?? plan.defaultParameters.maxDepth ?? 4) + 2, 12)),
    limit: String(Math.min(Number(current.limit ?? plan.defaultParameters.limit ?? 50) + 50, 500))
  }));
  window.setTimeout(() => {
    void executeQuery();
  }, 0);
}

function expandGraphNode(
  symbolId: string,
  setParameters: React.Dispatch<React.SetStateAction<Record<string, string>>>,
  executeQuery: () => Promise<void>
) {
  setParameters((current) => ({
    ...current,
    symbolId
  }));
  window.setTimeout(() => {
    void executeQuery();
  }, 0);
}

function selectCandidate(
  candidate: SymbolCandidate,
  plan: QueryPlan,
  setParameters: React.Dispatch<React.SetStateAction<Record<string, string>>>
) {
  const targetParameter = candidate.suggestedParameter
    ?? (plan.requiredParameters.includes('changedSymbol') ? 'changedSymbol' : 'symbolId');
  setParameters((current) => ({ ...current, [targetParameter]: candidate.symbolId }));
}

function resultPathNodes(result: QueryResultPayload | null): Array<[string, string]> | null {
  const path = result?.paths?.find((candidate) => candidate.path && candidate.path.length > 0)?.path;
  if (!path || path.length === 0) {
    return null;
  }
  return path.map((step) => [
    step.incomingRelation ?? 'ENTRY',
    step.symbolId ?? step.qualifier ?? '-'
  ]);
}

function graphNodeCandidates(result: QueryResultPayload | null) {
  const nodes = (result?.paths ?? [])
    .flatMap((path) => path.path ?? [])
    .map((step) => step.symbolId ?? step.qualifier ?? '')
    .filter((value) => value.length > 0);
  return Array.from(new Set(nodes));
}

function cytoscapeElements(result: QueryResultPayload | null) {
  const elements: Array<{ data: Record<string, string> }> = [];
  const seenNodes = new Set<string>();
  const seenEdges = new Set<string>();
  for (const resultPath of result?.paths ?? []) {
    const steps = resultPath.path ?? [];
    for (let index = 0; index < steps.length; index += 1) {
      const current = steps[index].symbolId ?? steps[index].qualifier;
      if (!current) {
        continue;
      }
      if (!seenNodes.has(current)) {
        seenNodes.add(current);
        elements.push({ data: { id: current, label: friendlyDisplayName(current) } });
      }
      const next = steps[index + 1]?.symbolId ?? steps[index + 1]?.qualifier;
      if (!next) {
        continue;
      }
      const edgeId = `${current}->${next}-${steps[index + 1]?.incomingRelation ?? 'CALLS'}`;
      if (!seenEdges.has(edgeId)) {
        seenEdges.add(edgeId);
        elements.push({
          data: {
            id: edgeId,
            source: current,
            target: next,
            label: relationLabel(steps[index + 1]?.incomingRelation ?? 'CALLS')
          }
        });
      }
    }
  }
  return elements;
}

function flowId(index: number) {
  return `path-step-${index}`;
}

function variableTraceSectionsFromResult(result: QueryResultPayload | null): VariableTraceSection[] {
  const paths = result?.paths ?? [];
  const sections: VariableTraceSection[] = [
    {
      direction: 'SOURCE',
      title: '值从哪里来',
      paths: paths.filter((path) => path.direction?.toUpperCase() === 'SOURCE' && (path.path?.length ?? 0) > 0)
    },
    {
      direction: 'SINK',
      title: '值去了哪里',
      paths: paths.filter((path) => path.direction?.toUpperCase() === 'SINK' && (path.path?.length ?? 0) > 0)
    }
  ];
  return sections.filter((section) => section.paths.length > 0);
}

function filteredVariableTraceSections(
  sections: VariableTraceSection[],
  direction: VariableTraceDirection
): VariableTraceSection[] {
  if (direction === 'source') {
    return sections.filter((section) => section.direction === 'SOURCE');
  }
  if (direction === 'sink') {
    return sections.filter((section) => section.direction === 'SINK');
  }
  return sections;
}

function pathNodesFromResultPath(resultPath: ResultPath): Array<[string, string]> {
  return (resultPath.path ?? []).map((step) => [
    step.incomingRelation ?? 'ENTRY',
    step.symbolId ?? step.qualifier ?? '-'
  ]);
}

function resultEvidenceRows(result: QueryResultPayload | null): Evidence[] {
  const rows: Evidence[] = [];
  for (const path of result?.paths ?? []) {
    for (const step of path.path ?? []) {
      for (const evidenceKey of step.evidenceKeys ?? []) {
        rows.push({
          type: evidenceKey.sourceType ?? step.sourceType ?? 'STATIC',
          file: evidenceKey.path ?? '-',
          line: evidenceKey.lineStart ?? 0,
          confidence: step.confidence ?? 'POSSIBLE',
          text: [step.incomingRelation, step.qualifier || evidenceKey.localPath].filter(Boolean).join(' ')
        });
      }
    }
  }
  for (const evidenceItem of result?.evidenceList ?? []) {
    rows.push({
      type: stringValue(evidenceItem.sourceType, stringValue(evidenceItem.evidenceType, 'Evidence')),
      file: stringValue(evidenceItem.filePath, '-'),
      line: numberValue(evidenceItem.lineNumber),
      confidence: confidenceValue(evidenceItem.confidence),
      text: stringValue(evidenceItem.snippet, '')
    });
  }
  return rows.slice(0, 30);
}

function ragEvidenceKeys(result: QueryResultPayload | null) {
  return Array.from(new Set((result?.results ?? []).flatMap((item) => item.evidenceKeys ?? [])));
}

function analysisStageStatuses(
  status: RunStatus,
  mode: Mode,
  plan: QueryPlan,
  result: QueryResultPayload | null,
  assistant: AssistantResult | null
): AnalysisStageStatus[] {
  if (result?.backgroundStatus?.length || result?.analysisStatus?.length) {
    return result.backgroundStatus?.length ? result.backgroundStatus : result.analysisStatus!;
  }
  const executing = status === 'planning' || status === 'executing';
  const pathCount = result?.pathCount ?? result?.paths?.length ?? 0;
  const sourceTypes = pathStepValues(result, 'sourceType');
  const analysisLayers = pathStepValues(result, 'analysisLayer');
  const planText = `${plan.summary} ${plan.intent} ${plan.relationTypes.join(' ')}`.toUpperCase();
  const hasDeep = result?.depth === 'DEEP'
    || result?.analysisLayer === 'DEEP_SUPPLEMENT'
    || analysisLayers.has('DEEP_SUPPLEMENT')
    || sourceTypes.has('TAI_E');
  const hasFfm = sourceTypes.has('FFM') || planText.includes('FFM');
  const needsAi = mode === 'qa' || plan.intent === 'RAG_SEMANTIC_SEARCH';
  const stages: AnalysisStageStatus[] = [
    {
      id: 'fast-static',
      label: '快速静态分析',
      state: status === 'error' ? 'error' : result ? 'ready' : executing ? 'running' : 'waiting',
      detail: result ? `已返回 ${pathCount} 条相关路径` : '等待执行查询'
    },
    {
      id: 'deep-supplement',
      label: '深度补强',
      state: hasDeep ? 'ready' : executing ? 'running' : 'waiting',
      detail: hasDeep ? 'Tai-e 深度证据已并入结果' : '后台按需补强，不阻塞快速结果'
    },
    {
      id: 'ai-rag',
      label: 'AI/RAG 解释',
      state: assistant || result?.answer ? 'ready' : needsAi ? (executing ? 'running' : 'waiting') : 'disabled',
      detail: assistant
        ? `基于 ${assistant.evidenceCount} 条证据生成`
        : result?.answer
          ? `基于 ${result.results?.length ?? 0} 个召回节点生成`
          : 'AI 只解释证据，不生成事实'
    },
    {
      id: 'ffm',
      label: 'FFM 加速',
      state: hasFfm ? 'ready' : 'disabled',
      detail: hasFfm ? '已由性能决策启用' : '默认关闭，benchmark 证明收益后启用'
    }
  ];
  if (status !== 'error') {
    return stages;
  }
  return stages.map((stage) => (
    stage.id === 'fast-static'
      ? { ...stage, detail: '查询失败，保留原始错误状态' }
      : stage
  ));
}

function pathStepValues(result: QueryResultPayload | null, key: 'sourceType' | 'analysisLayer') {
  return new Set(
    (result?.paths ?? [])
      .flatMap((path) => path.path ?? [])
      .map((step) => step[key])
      .filter((value): value is string => Boolean(value))
      .map((value) => value.toUpperCase())
  );
}

function stageStateLabel(state: AnalysisStageStatus['state']) {
  switch (state) {
    case 'ready':
      return '已完成';
    case 'running':
      return '运行中';
    case 'waiting':
      return '等待';
    case 'disabled':
      return '关闭';
    case 'error':
      return '失败';
    default:
      return state;
  }
}

function shortEvidenceKey(value: string) {
  const parts = value.split('|');
  if (parts.length >= 6) {
    const path = shortPath(parts[2]);
    const line = parts[3] && parts[3] !== '0' ? `:${parts[3]}` : '';
    return `${sourceLabel(parts[0])} ${path}${line}`;
  }
  return shortPath(value);
}

function stringValue(value: unknown, fallback: string) {
  return typeof value === 'string' && value ? value : fallback;
}

function numberValue(value: unknown) {
  return typeof value === 'number' ? value : 0;
}

function confidenceValue(value: unknown): Evidence['confidence'] {
  return value === 'CERTAIN' || value === 'LIKELY' || value === 'POSSIBLE' ? value : 'POSSIBLE';
}

function headline(mode: Mode) {
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

function businessStep(relationType: string, symbol: string) {
  return {
    relation: relationLabel(relationType),
    label: friendlyDisplayName(symbol),
    kind: symbolKindLabel(symbol),
    raw: symbol
  };
}

function relationLabel(relationType: string) {
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

function friendlyDisplayName(value: string) {
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

function symbolKindLabel(symbol: string) {
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

function categoryLabel(category: string) {
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

function confidenceLabel(confidence: string) {
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

function riskLabel(risk: string) {
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

function sourceLabel(source: string) {
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

function friendlyEvidenceText(text: string) {
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

function businessPlanSummary(mode: Mode, fallback: string) {
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

function businessViewSummary(viewName: string, fallback: string) {
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

function statusMessage(message: string) {
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

function parameterLabel(key: string) {
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

function intentLabel(intent: string) {
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

function shortPath(path: string) {
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

function inferMode(query: string): Mode {
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

function modeFromIntent(intent: string): Mode {
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

function shortView(view: string) {
  return view.replace('_VIEW', '').replaceAll('_', ' ');
}

createRoot(document.getElementById('root')!).render(<App />);
