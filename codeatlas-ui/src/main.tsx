import React, { useMemo, useState } from 'react';
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
  Workflow
} from 'lucide-react';
import './styles.css';

type Mode = 'impact' | 'variable' | 'jsp' | 'graph' | 'sql';

type Evidence = {
  type: string;
  file: string;
  line: number;
  confidence: 'CERTAIN' | 'LIKELY' | 'POSSIBLE';
  text: string;
};

const evidence: Evidence[] = [
  { type: 'JSP', file: 'WEB-INF/jsp/user/edit.jsp', line: 42, confidence: 'LIKELY', text: 'input name="userId"' },
  { type: 'Struts', file: 'WEB-INF/struts-config.xml', line: 88, confidence: 'CERTAIN', text: '/user/update -> UserUpdateAction' },
  { type: 'Java', file: 'UserUpdateAction.java', line: 31, confidence: 'LIKELY', text: 'request.getParameter("userId")' },
  { type: 'Spring', file: 'UserService.java', line: 18, confidence: 'CERTAIN', text: '@Autowired UserRepository' },
  { type: 'MyBatis', file: 'UserMapper.xml', line: 19, confidence: 'LIKELY', text: 'users.user_id' }
];

const modes: Array<{ id: Mode; label: string; icon: React.ComponentType<{ size?: number }> }> = [
  { id: 'impact', label: '影响报告', icon: AlertTriangle },
  { id: 'variable', label: '变量追踪', icon: Workflow },
  { id: 'jsp', label: 'JSP 链路', icon: FileCode2 },
  { id: 'graph', label: '图谱探索', icon: Network },
  { id: 'sql', label: 'SQL / Table', icon: Database }
];

const pathNodes = [
  ['JSP_INPUT', 'edit.jsp input[name=userId]'],
  ['WRITES_PARAM', 'request parameter userId'],
  ['SUBMITS_TO', '/user/update'],
  ['ROUTES_TO', 'UserUpdateAction'],
  ['CALLS', 'UserService.update'],
  ['INJECTS', 'UserRepository'],
  ['BRIDGES_TO', 'UserMapper.update'],
  ['WRITES_TABLE', 'users.user_id']
];

function App() {
  const [mode, setMode] = useState<Mode>('impact');
  const [query, setQuery] = useState('userId 从哪里来，到哪里去？');
  const activeMode = useMemo(() => modes.find((item) => item.id === mode)!, [mode]);
  const ActiveIcon = activeMode.icon;

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
                onClick={() => setMode(item.id)}
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
            <button type="button">查询</button>
          </div>
          <div className="status-pill">
            <ShieldCheck size={17} />
            <span>静态事实优先</span>
          </div>
        </header>

        <section className="summary-band">
          <div>
            <p className="eyebrow">{activeMode.label}</p>
            <h1>{headline(mode)}</h1>
            <p>{description(mode)}</p>
          </div>
          <div className="metrics" aria-label="analysis metrics">
            <Metric label="确定路径" value="3" />
            <Metric label="可能路径" value="2" />
            <Metric label="P95 查询" value="0.8s" />
            <Metric label="截断" value="false" />
          </div>
        </section>

        <section className="tool-grid">
          <article className="path-panel">
            <div className="panel-title">
              <ActiveIcon size={18} />
              <h2>证据路径</h2>
            </div>
            <ol className="path">
              {pathNodes.map(([kind, label]) => (
                <li key={`${kind}-${label}`}>
                  <span>{kind}</span>
                  <strong>{label}</strong>
                </li>
              ))}
            </ol>
          </article>

          <article className="evidence-panel">
            <div className="panel-title">
              <GitBranch size={18} />
              <h2>证据列表</h2>
            </div>
            <table>
              <thead>
                <tr>
                  <th>类型</th>
                  <th>文件</th>
                  <th>行</th>
                  <th>置信度</th>
                  <th>证据</th>
                </tr>
              </thead>
              <tbody>
                {evidence.map((item) => (
                  <tr key={`${item.file}-${item.line}-${item.text}`}>
                    <td>{item.type}</td>
                    <td>{item.file}</td>
                    <td>{item.line}</td>
                    <td><span className={`confidence ${item.confidence.toLowerCase()}`}>{item.confidence}</span></td>
                    <td>{item.text}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </article>
        </section>

        <section className="detail-band">
          <div>
            <h2>查询计划</h2>
            <p>{queryPlan(mode)}</p>
          </div>
          <button type="button">查看 JSON</button>
        </section>
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

function headline(mode: Mode) {
  switch (mode) {
    case 'variable':
      return 'request parameter 的来源和流向已合并展示';
    case 'jsp':
      return 'JSP 表单到 Action / Service / Mapper / Table 的路径';
    case 'graph':
      return 'caller / callee 与多跳影响路径查询';
    case 'sql':
      return 'SQL statement 到 table / column 的影响面';
    default:
      return 'Git diff 到入口、调用链和数据表的快速影响报告';
  }
}

function description(mode: Mode) {
  switch (mode) {
    case 'variable':
      return '优先使用 JSP input、request.getParameter、ActionForm 字段和 getter/setter 简单传播作为证据。';
    case 'jsp':
      return '支持 Struts1 JSP form、Action path、Spring Controller、Service、DAO、MyBatis statement 和 SQL/table 节点。';
    case 'graph':
      return '图查询只走封装后的只读 query plan，展示 relation、confidence、sourceType 和 evidenceKey。';
    case 'sql':
      return 'MyBatis XML 和注解 SQL 均可输出 DbTable 与 DbColumn 关系，动态 SQL 降为 POSSIBLE。';
    default:
      return '静态分析和图谱是事实来源，AI 只负责摘要、风险解释和测试建议。';
  }
}

function queryPlan(mode: Mode) {
  switch (mode) {
    case 'variable':
      return 'variable.traceSource + variable.traceSink -> request parameter -> evidence path';
    case 'jsp':
      return 'jsp.findBackendFlow -> SUBMITS_TO / ROUTES_TO / CALLS / INJECTS / BINDS_TO';
    case 'graph':
      return 'graph.findCallers / graph.findCallees / graph.findImpactPaths';
    case 'sql':
      return 'symbol.search -> Mapper statement -> READS_TABLE / WRITES_TABLE';
    default:
      return 'impact.analyzeDiff -> changed symbols -> Neo4j + adjacency cache -> report';
  }
}

createRoot(document.getElementById('root')!).render(<App />);
