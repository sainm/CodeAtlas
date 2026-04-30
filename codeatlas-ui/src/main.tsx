import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
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
import './styles.css';

type Mode = 'impact' | 'variable' | 'jsp' | 'graph' | 'sql';
type RunStatus = 'idle' | 'planning' | 'executing' | 'fallback' | 'error';

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

type QueryResultPayload = {
  pathCount?: number;
  reportId?: string;
  paths?: ResultPath[];
  evidenceList?: Array<Record<string, unknown>>;
  affectedSymbols?: AffectedSymbol[];
  results?: SymbolCandidate[];
  truncated?: boolean;
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
  suggestedParameter?: string;
  reason?: string;
};

const modes: Array<{ id: Mode; label: string; icon: React.ComponentType<{ size?: number }> }> = [
  { id: 'impact', label: '变更影响', icon: AlertTriangle },
  { id: 'variable', label: '变量去向', icon: Workflow },
  { id: 'jsp', label: '页面链路', icon: FileCode2 },
  { id: 'graph', label: '调用关系', icon: Network },
  { id: 'sql', label: '数据表影响', icon: Database }
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
];

const evidence: Evidence[] = [
  { type: 'JSP', file: 'user/edit.jsp', line: 2, confidence: 'CERTAIN', text: 'include file="/common/footer.jsp"' },
  { type: 'JSP', file: 'user/edit.jsp', line: 5, confidence: 'LIKELY', text: 'html:hidden property="userId"' },
  { type: 'Struts', file: 'WEB-INF/struts-config.xml', line: 0, confidence: 'CERTAIN', text: '/user/save -> UserSaveAction' },
  { type: 'Java', file: 'AdminUserAction.java', line: 18, confidence: 'LIKELY', text: 'LookupDispatchAction button.search -> search' },
  { type: 'MyBatis', file: 'UserMapper.xml', line: 7, confidence: 'LIKELY', text: 'users table read/write candidate' }
];

const pathsByMode: Record<Mode, Array<[string, string]>> = {
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
  ]
};

const fallbackPlans: Record<Mode, QueryPlan> = {
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
  }
};

const fallbackViews: QueryResultView[] = [
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
    name: 'SYMBOL_PICKER_VIEW',
    title: 'Symbol Picker',
    summary: 'Candidate symbols before running exact graph, variable, JSP, SQL, or impact queries.',
    primaryFields: ['symbolId', 'kind', 'displayName', 'score'],
    evidenceFields: ['file', 'line', 'moduleKey', 'sourceRootKey']
  }
];

const defaultParamsByMode: Record<Mode, Record<string, string>> = {
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
    limit: '50'
  },
  sql: {
    projectId: 'shop',
    snapshotId: 's1',
    symbolId: 'db-table://shop/_root/db/users',
    maxDepth: '6',
    limit: '50'
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

  const activeMode = useMemo(() => modes.find((item) => item.id === mode)!, [mode]);
  const activeView = useMemo(
    () => views.find((view) => view.name === plan.resultView) ?? fallbackViews.find((view) => view.name === plan.resultView) ?? fallbackViews[0],
    [plan.resultView, views]
  );
  const ActiveIcon = activeMode.icon;
  const pathNodes = resultPathNodes(result) ?? pathsByMode[mode];
  const variableTraceSections = mode === 'variable' ? variableTraceSectionsFromResult(result) : [];
  const resultEvidence = resultEvidenceRows(result);
  const evidenceRows = resultEvidence.length > 0 ? resultEvidence : evidence;
  const summary = resultSummary(result, evidenceRows);
  const rawJson = useMemo(() => JSON.stringify({
    query,
    queryPlan: plan,
    resultView: activeView,
    parameters,
    result,
    pathPreview: pathNodes.map(([relationType, symbol]) => ({ relationType, symbol })),
    evidencePreview: evidenceRows
  }, null, 2), [activeView, evidenceRows, parameters, pathNodes, plan, query, result]);

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
      const payloads = await Promise.all(endpointList(plan.endpoint).map((endpoint) => {
        const url = endpointUrl(endpoint, plan, parameters);
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

        <section className="tool-grid">
          <article className="path-panel">
            <div className="panel-title">
              <ActiveIcon size={18} />
              <h2>业务链路</h2>
            </div>
            {variableTraceSections.length > 0 ? (
              <div className="trace-groups">
                {variableTraceSections.map((section) => (
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
          <button type="button" title="Toggle raw JSON" onClick={() => setShowRawJson((value) => !value)}>
            <FileCode2 size={16} />
            <span>原始 JSON</span>
          </button>
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

function variableTraceSectionsFromResult(result: QueryResultPayload | null) {
  const paths = result?.paths ?? [];
  return [
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
  ].filter((section) => section.paths.length > 0);
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
    case 'variable':
      return '变量从哪里来、流向哪里';
    case 'jsp':
      return '页面到后台处理链路';
    case 'graph':
      return '调用关系查询';
    case 'sql':
      return '数据库表影响';
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
    default:
      return fallback;
  }
}

function businessViewSummary(viewName: string, fallback: string) {
  switch (viewName) {
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
