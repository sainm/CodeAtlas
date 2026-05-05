import React, { useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  Braces,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Code2,
  Database,
  Eye,
  FileCode2,
  FileSearch,
  GitBranch,
  Layers,
  ListChecks,
  Network,
  Play,
  Route,
  Search,
  ShieldCheck,
  Sparkles,
  Table2,
  Workflow,
  type LucideIcon
} from 'lucide-react';
import './styles.css';

type TaskKey = 'diff' | 'db' | 'variable' | 'web' | 'change' | 'similar';
type ViewKey = 'impact' | 'graph' | 'variable' | 'webflow' | 'sqlpath' | 'dbimpact' | 'feature' | 'health';

type WorkbenchTask = {
  key: TaskKey;
  title: string;
  hint: string;
  example: string;
  icon: LucideIcon;
};

type ViewSpec = {
  key: ViewKey;
  title: string;
  icon: LucideIcon;
  summary: string;
};

const tasks: WorkbenchTask[] = [
  {
    key: 'diff',
    title: '分析 Git Diff',
    hint: '从变更文件和行号定位方法、入口、SQL、页面和建议测试。',
    example: '粘贴 git diff，分析 users.email 改名会影响哪些入口和测试。',
    icon: GitBranch
  },
  {
    key: 'db',
    title: '查 DB 影响',
    hint: '按表或字段查 SQL、Mapper、Service、页面展示和测试影响。',
    example: 'public.users.email 字段类型从 varchar(64) 改到 varchar(128)。',
    icon: Database
  },
  {
    key: 'variable',
    title: '查变量流向',
    hint: '追踪 request 参数、Form 字段、DTO 字段、Java 参数和 SQL 参数。',
    example: '变量 email 从 JSP 输入框流到哪些 SQL 参数？',
    icon: Workflow
  },
  {
    key: 'web',
    title: '查 JSP/Web Client 链路',
    hint: '串起 JSP/HTML/ClientRequest 到 Action、Service、DAO、SQL/table。',
    example: 'user-edit.jsp 的保存按钮会调用哪些后端和表？',
    icon: FileCode2
  },
  {
    key: 'change',
    title: '规划功能修改',
    hint: '输出必须关注、建议检查、可能相关、建议测试和不确定项。',
    example: '新增用户邮箱唯一性校验，帮我找修改点和回归测试。',
    icon: ListChecks
  },
  {
    key: 'similar',
    title: '找相似实现',
    hint: '按自然语言功能描述找可参考实现、复用点和新增落点。',
    example: '找一个已有的 CSV 导入校验流程作为参考。',
    icon: Sparkles
  }
];

const views: ViewSpec[] = [
  {
    key: 'impact',
    title: 'Impact Report',
    icon: Activity,
    summary: '汇总风险、受影响入口、SQL/table、建议测试、Fast/Deep 状态和下一步动作。'
  },
  {
    key: 'graph',
    title: 'Graph Explorer',
    icon: Network,
    summary: '支持 caller、callee、combined、entry-to-SQL 四种模式，并保留路径证据。'
  },
  {
    key: 'variable',
    title: 'Variable Trace',
    icon: Workflow,
    summary: '支持 source、sink、all 模式，展示 request/Form/DTO/Java 参数到 SQL 参数的流向。'
  },
  {
    key: 'webflow',
    title: 'JSP/Web Flow',
    icon: FileCode2,
    summary: '展示 JSP/HTML/ClientRequest -> Action/Controller -> Service -> DAO/Mapper -> SQL/table。'
  },
  {
    key: 'sqlpath',
    title: 'SQL/Table Path',
    icon: Route,
    summary: '用于从入口、方法、Mapper 或 SQL 下钻到 SQL/table/column 路径。'
  },
  {
    key: 'dbimpact',
    title: 'DB Impact',
    icon: Database,
    summary: '表/字段变更报告页，区分 read、write、display、test impact。'
  },
  {
    key: 'feature',
    title: 'Feature Plan',
    icon: ListChecks,
    summary: '展示必须关注、建议检查、可能相关、建议测试和不确定项。'
  },
  {
    key: 'health',
    title: 'Architecture Health',
    icon: Layers,
    summary: '展示热点、循环依赖候选、动态风险和边界风险。'
  }
];

const queryKinds = ['Git diff', 'DB 表/字段', 'JSP/HTML 页面', 'Java symbol', '变量名', '自然语言'];

const candidates = [
  {
    project: 'legacy-shop',
    module: 'user-web',
    datasource: 'mainDs',
    type: 'Java Method',
    path: 'src/main/java/com/acme/user/UserAction.java',
    line: 84,
    confidence: 'LIKELY',
    evidence: 'spoon:UserAction#save'
  },
  {
    project: 'legacy-shop',
    module: 'user-dao',
    datasource: 'mainDs',
    type: 'DB Column',
    path: 'public.users#email',
    line: '-',
    confidence: 'CERTAIN',
    evidence: 'sql-table:users.email'
  },
  {
    project: 'legacy-shop',
    module: 'user-web',
    datasource: 'mainDs',
    type: 'JSP Input',
    path: 'src/main/webapp/user/edit.jsp',
    line: 37,
    confidence: 'LIKELY',
    evidence: 'jsp-input:email'
  }
];

const evidenceRows = [
  {
    file: 'src/main/webapp/user/edit.jsp',
    line: 37,
    type: 'RENDERS_INPUT',
    analyzer: 'JspAnalyzer',
    sourceType: 'STATIC',
    confidence: 'LIKELY',
    snippet: '<input name="email" value="${user.email}">'
  },
  {
    file: 'src/main/resources/mapper/UserMapper.xml',
    line: 18,
    type: 'WRITES_COLUMN',
    analyzer: 'MyBatisXmlAnalyzer',
    sourceType: 'STATIC_SQL',
    confidence: 'CERTAIN',
    snippet: 'update users set email = #{email}'
  }
];

function App() {
  const [activeTask, setActiveTask] = useState<TaskKey>('diff');
  const [activeView, setActiveView] = useState<ViewKey>('impact');
  const [graphMode, setGraphMode] = useState('combined');
  const [traceMode, setTraceMode] = useState('all');
  const [query, setQuery] = useState(tasks[0].example);

  const selectedTask = useMemo(() => tasks.find((task) => task.key === activeTask) ?? tasks[0], [activeTask]);
  const activeViewSpec = useMemo(() => views.find((view) => view.key === activeView) ?? views[0], [activeView]);
  const SelectedIcon = selectedTask.icon;
  const ActiveViewIcon = activeViewSpec.icon;

  function chooseTask(task: WorkbenchTask) {
    setActiveTask(task.key);
    setQuery(task.example);
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <h1>CodeAtlas</h1>
          <p>任务工作台优先的代码事实、影响分析和证据浏览界面</p>
        </div>
        <div className="topbar-actions">
          <button type="button" className="icon-button" aria-label="查看健康状态">
            <Activity size={18} />
          </button>
          <button type="button" className="primary-button">
            <Play size={16} />
            运行分析
          </button>
        </div>
      </header>

      <section className="project-home" aria-label="项目首页">
        <div className="project-overview">
          <div className="status-heading">
            <span className="status-dot" />
            <div>
              <strong>Project Dashboard</strong>
              <span>默认项目首页把 Dashboard 作为概览带，主操作区始终是 Task Workbench。</span>
            </div>
          </div>
          <div className="capability-strip">
            <Metric label="Capability" value="Java / JSP / SQL" tone="green" />
            <Metric label="Analysis status" value="Ready" tone="green" />
            <Metric label="Entrypoints" value="24 indexed" tone="blue" />
            <Metric label="Blind spots" value="3 pending" tone="amber" />
          </div>
        </div>
      </section>

      <section className="workbench" aria-label="Task Workbench">
        <aside className="left-rail">
          <section className="rail-block">
            <h2>常用任务</h2>
            <div className="task-list">
              {tasks.map((task) => {
                const Icon = task.icon;
                return (
                  <button
                    type="button"
                    key={task.key}
                    className={`task-button ${activeTask === task.key ? 'active' : ''}`}
                    onClick={() => chooseTask(task)}
                  >
                    <Icon size={18} />
                    <span>
                      <strong>{task.title}</strong>
                      <small>{task.hint}</small>
                    </span>
                  </button>
                );
              })}
            </div>
          </section>

          <section className="rail-block">
            <h2>最近报告</h2>
            {['FastImpactReport #18', 'DbImpactReport users.email', 'VariableTrace email', 'ImportReviewReport local'].map((report) => (
              <button type="button" className="report-link" key={report}>
                <FileSearch size={15} />
                {report}
              </button>
            ))}
          </section>
        </aside>

        <section className="center-stage">
          <div className="query-header">
            <div className="task-icon">
              <SelectedIcon size={22} />
            </div>
            <div>
              <h2>{selectedTask.title}</h2>
              <p>{selectedTask.hint}</p>
            </div>
          </div>

          <div className="query-kinds" aria-label="统一输入支持类型">
            {queryKinds.map((kind) => (
              <span key={kind}>{kind}</span>
            ))}
          </div>

          <label className="query-box">
            <Search size={20} />
            <textarea
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              rows={4}
              aria-label="全局查询输入"
              placeholder="输入 Git diff、DB 表/字段、JSP/HTML 页面、Java symbol、变量名或自然语言功能描述"
            />
          </label>

          <div className="query-actions">
            <button type="button" className="primary-button">
              <ArrowRight size={16} />
              生成初版报告
            </button>
            <button type="button" className="secondary-button">
              <Braces size={16} />
              打开候选
            </button>
          </div>

          <ResultSummary />
          <CandidatePicker />

          <section className="view-panel" aria-label="结果视图">
            <div className="tabs" role="tablist" aria-label="分析视图">
              {views.map((view) => (
                <button
                  type="button"
                  role="tab"
                  aria-selected={activeView === view.key}
                  className={activeView === view.key ? 'active' : ''}
                  key={view.key}
                  onClick={() => setActiveView(view.key)}
                >
                  {view.title}
                </button>
              ))}
            </div>

            <div className="active-view">
              <ActiveViewIcon size={24} />
              <div>
                <h2>{activeViewSpec.title}</h2>
                <p>{activeViewSpec.summary}</p>
                {activeView === 'graph' && (
                  <ModeSwitch modes={['caller', 'callee', 'combined', 'entry-to-SQL']} value={graphMode} onChange={setGraphMode} />
                )}
                {activeView === 'variable' && (
                  <ModeSwitch modes={['source', 'sink', 'all']} value={traceMode} onChange={setTraceMode} />
                )}
              </div>
            </div>

            <ViewDetail activeView={activeView} />
          </section>
        </section>

        <EvidencePanel />
      </section>
    </main>
  );
}

function Metric({ label, value, tone }: { label: string; value: string; tone: 'green' | 'blue' | 'amber' }) {
  return (
    <div className={`metric ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function ResultSummary() {
  return (
    <section className="result-summary" aria-label="结果摘要">
      <div className="summary-main">
        <span className="summary-label">风险</span>
        <strong>Medium</strong>
        <p>Fast report 已覆盖 7 个入口、4 条 SQL/table 路径和 3 条建议测试。Deep supplement queued。</p>
        <div className="pill-row">
          <span>Fast ready</span>
          <span>Deep queued</span>
          <span>Truncated: no</span>
        </div>
      </div>
      <div className="summary-grid">
        <SummaryItem icon={Route} label="影响入口" value="UserAction.save, AdminUserController.update" />
        <SummaryItem icon={Table2} label="SQL/table" value="users.email read/write, audit_log display" />
        <SummaryItem icon={ListChecks} label="建议测试" value="保存用户、重复邮箱、列表展示" />
        <SummaryItem icon={ChevronRight} label="下一步" value="打开 DB Impact 或 entry-to-SQL 路径" />
      </div>
    </section>
  );
}

function SummaryItem({ icon: Icon, label, value }: { icon: LucideIcon; label: string; value: string }) {
  return (
    <div className="summary-item">
      <Icon size={16} />
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function CandidatePicker() {
  return (
    <section className="candidate-picker" aria-label="Symbol Candidate Picker">
      <div className="section-title">
        <h2>Symbol Candidate Picker</h2>
        <p>多候选时先展示范围信息，禁止自动跨 project/module/datasource 选择。</p>
      </div>
      <div className="candidate-table" role="table">
        <div className="candidate-row header" role="row">
          <span>Project</span>
          <span>Module</span>
          <span>Datasource</span>
          <span>Type</span>
          <span>Path</span>
          <span>Line</span>
          <span>Confidence</span>
          <span>Evidence</span>
        </div>
        {candidates.map((candidate) => (
          <button type="button" className="candidate-row" role="row" key={`${candidate.type}-${candidate.path}`}>
            <span>{candidate.project}</span>
            <span>{candidate.module}</span>
            <span>{candidate.datasource}</span>
            <span>{candidate.type}</span>
            <span>{candidate.path}</span>
            <span>{candidate.line}</span>
            <span>{candidate.confidence}</span>
            <span>{candidate.evidence}</span>
          </button>
        ))}
      </div>
    </section>
  );
}

function ModeSwitch({ modes, value, onChange }: { modes: string[]; value: string; onChange: (mode: string) => void }) {
  return (
    <div className="mode-switch" aria-label="模式切换">
      {modes.map((mode) => (
        <button type="button" key={mode} className={value === mode ? 'active' : ''} onClick={() => onChange(mode)}>
          {mode}
        </button>
      ))}
    </div>
  );
}

function ViewDetail({ activeView }: { activeView: ViewKey }) {
  if (activeView === 'dbimpact') {
    return (
      <div className="impact-columns">
        <ImpactColumn title="Read impact" items={['UserMapper.selectByEmail', 'UserSearchService.filterByEmail']} />
        <ImpactColumn title="Write impact" items={['UserMapper.updateEmail', 'UserImportJob.upsertUser']} />
        <ImpactColumn title="Display impact" items={['user/edit.jsp input[name=email]', 'admin/user-list.jsp column email']} />
        <ImpactColumn title="Test impact" items={['UserActionSaveTest', 'UserImportJobTest']} />
      </div>
    );
  }

  if (activeView === 'webflow') {
    return <PathLine nodes={['JSP/HTML', 'ClientRequest', 'Action/Controller', 'Service', 'DAO/Mapper', 'SQL/table']} />;
  }

  if (activeView === 'sqlpath') {
    return <PathLine nodes={['Entrypoint', 'Method', 'Mapper XML', 'SQL statement', 'table', 'column']} />;
  }

  if (activeView === 'feature') {
    return (
      <div className="planning-grid">
        <ImpactColumn title="必须关注" items={['入口参数校验', '唯一键冲突处理']} />
        <ImpactColumn title="建议检查" items={['导入流程', '后台编辑页']} />
        <ImpactColumn title="可能相关" items={['邮件通知模板', '审计日志展示']} />
        <ImpactColumn title="不确定项" items={['历史数据清洗策略']} />
      </div>
    );
  }

  if (activeView === 'health') {
    return (
      <div className="planning-grid">
        <ImpactColumn title="热点" items={['UserService: 34 callers', 'UserMapper.xml: 18 statements']} />
        <ImpactColumn title="循环依赖候选" items={['user-service <-> audit-service']} />
        <ImpactColumn title="动态风险" items={['reflection dispatch: possible', 'native boundary: none']} />
        <ImpactColumn title="边界风险" items={['external datasource mainDs read only path']} />
      </div>
    );
  }

  return <PathLine nodes={['EntryPoint', 'Controller/Action', 'Service', 'Mapper/DAO', 'SQL', 'DbColumn']} />;
}

function PathLine({ nodes }: { nodes: string[] }) {
  return (
    <div className="path-line" aria-label="路径预览">
      {nodes.map((node, index) => (
        <React.Fragment key={node}>
          <span>{node}</span>
          {index < nodes.length - 1 && <i />}
        </React.Fragment>
      ))}
    </div>
  );
}

function ImpactColumn({ title, items }: { title: string; items: string[] }) {
  return (
    <div className="impact-column">
      <h3>{title}</h3>
      {items.map((item) => (
        <span key={item}>{item}</span>
      ))}
    </div>
  );
}

function EvidencePanel() {
  return (
    <aside className="evidence-panel">
      <section className="panel-section">
        <h2>证据 / 覆盖 / 盲区</h2>
        <div className="coverage-grid">
          <StatusLine icon={ShieldCheck} label="analysisBoundary" value="STATIC_ONLY" tone="green" />
          <StatusLine icon={Clock3} label="pending" value="deep supplement queued" tone="amber" />
          <StatusLine icon={CheckCircle2} label="stale" value="none" tone="green" />
          <StatusLine icon={AlertTriangle} label="coverage" value="JSP include: partial" tone="amber" />
        </div>
      </section>

      <section className="panel-section">
        <h2>Evidence Panel</h2>
        <div className="evidence-list">
          {evidenceRows.map((row) => (
            <article className="evidence-row" key={`${row.file}-${row.line}`}>
              <div className="evidence-meta">
                <strong>{row.type}</strong>
                <span>{row.file}:{row.line}</span>
              </div>
              <p>{row.snippet}</p>
              <div className="evidence-tags">
                <span>{row.analyzer}</span>
                <span>{row.sourceType}</span>
                <span>{row.confidence}</span>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="panel-section">
        <h2>下钻详情</h2>
        <p className="muted">默认隐藏 raw SymbolId 和 raw JSON。大结果展示 truncation 状态和 continuation 操作。</p>
        <button type="button" className="secondary-button full-width">
          <Eye size={16} />
          继续加载后续结果
        </button>
        <details className="raw-details">
          <summary>展开 raw details</summary>
          <pre>{'{\n  "snapshotId": "latest-committed",\n  "truncated": false,\n  "symbolId": "hidden-until-drilldown"\n}'}</pre>
        </details>
      </section>
    </aside>
  );
}

function StatusLine({ icon: Icon, label, value, tone }: { icon: LucideIcon; label: string; value: string; tone: 'green' | 'amber' }) {
  return (
    <div className={`status-line ${tone}`}>
      <Icon size={16} />
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
