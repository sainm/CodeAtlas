import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Button } from 'antd';
import { Background, Controls, ReactFlow } from '@xyflow/react';
import {
  Braces,
  FileCode2,
  GitBranch,
  Network,
  Search,
  ShieldCheck,
  Sparkles
} from 'lucide-react';
import 'antd/dist/reset.css';
import '@xyflow/react/dist/style.css';
import './styles.css';
import {
  defaultParamsByMode,
  evidence,
  fallbackPlans,
  fallbackViews,
  graphModes,
  modes,
  overviewCapabilities,
  pathsByMode,
  quickTemplates
} from './appData';
import { CytoscapeGraph, type CytoscapeElement } from './components/CytoscapeGraph';
import { Metric } from './components/Metric';
import { ProjectOverviewPanel } from './components/ProjectOverviewPanel';
import {
  endpointList,
  endpointUrl,
  graphEndpointList,
  mergeParams,
  mergePayloads,
  parameterKeys
} from './queryApi';
import {
  businessPlanSummary,
  businessStep,
  businessViewSummary,
  categoryLabel,
  confidenceLabel,
  confidenceValue,
  friendlyDisplayName,
  friendlyEvidenceText,
  headline,
  inferMode,
  intentLabel,
  modeFromIntent,
  numberValue,
  parameterLabel,
  relationLabel,
  riskLabel,
  shortEvidenceKey,
  shortPath,
  shortView,
  sourceLabel,
  statusMessage,
  stringValue,
  symbolKindLabel
} from './formatters';
import type {
  AnalysisStageStatus,
  AssistantResult,
  Evidence,
  GraphQueryMode,
  Mode,
  QueryPlan,
  QueryResultPayload,
  QueryResultView,
  QuickTemplate,
  ResultPath,
  RunStatus,
  SymbolCandidate,
  VariableTraceDirection,
  VariableTraceSection
} from './types';

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
          <ProjectOverviewPanel
            result={result}
            quickTemplates={quickTemplates}
            overviewCapabilities={overviewCapabilities}
            applyTemplate={applyTemplate}
          />
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
      <CytoscapeGraph elements={cytoscapeElements(result)} />
    </section>
  );
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

function cytoscapeElements(result: QueryResultPayload | null): CytoscapeElement[] {
  const elements: CytoscapeElement[] = [];
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

createRoot(document.getElementById('root')!).render(<App />);
