import React, { useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  Braces,
  Database,
  FileCode2,
  GitBranch,
  ListChecks,
  Play,
  Search,
  ShieldCheck,
  Sparkles,
  Workflow,
  type LucideIcon
} from 'lucide-react';
import './styles.css';

type TaskKey = 'diff' | 'db' | 'variable' | 'web' | 'change' | 'similar';

type WorkbenchTask = {
  key: TaskKey;
  title: string;
  hint: string;
  icon: LucideIcon;
};

const tasks: WorkbenchTask[] = [
  { key: 'diff', title: '分析 Git Diff', hint: '从 changed method 找入口、SQL、页面和测试建议', icon: GitBranch },
  { key: 'db', title: '查 DB 影响', hint: '表/字段到 SQL、Mapper、Service、入口和报表', icon: Database },
  { key: 'variable', title: '查变量流向', hint: 'request 参数、Form 字段、DTO 字段和 SQL 参数', icon: Workflow },
  { key: 'web', title: '查 JSP/Web Client 链路', hint: '页面表单、静态请求、Action/Controller 和 SQL', icon: FileCode2 },
  { key: 'change', title: '规划功能修改', hint: '必须关注、建议检查、候选范围和回归建议', icon: ListChecks },
  { key: 'similar', title: '找相似实现', hint: '按功能描述找参照、复用点和新增落点', icon: Sparkles }
];

const capabilityCards = [
  { label: 'Java / JSP / SQL', value: '待分析', tone: 'neutral' },
  { label: '导入审查', value: '未运行', tone: 'warning' },
  { label: 'Active Snapshot', value: '未生成', tone: 'neutral' },
  { label: 'AI 候选', value: '默认关闭', tone: 'safe' }
];

const recentReports = [
  'ImportReviewReport 示例待生成',
  'FastImpactReport 示例待生成',
  'DbImpactReport 示例待生成'
];

function App() {
  const [activeTask, setActiveTask] = useState<TaskKey>('diff');
  const [query, setQuery] = useState('user 表 email 字段改名会影响哪里？');

  const selectedTask = useMemo(() => tasks.find((task) => task.key === activeTask) ?? tasks[0], [activeTask]);
  const SelectedIcon = selectedTask.icon;

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <h1>CodeAtlas</h1>
          <p>Java 静态分析与变更影响工作台</p>
        </div>
        <div className="topbar-actions">
          <button type="button" className="icon-button" aria-label="架构健康">
            <Activity size={18} />
          </button>
          <button type="button" className="primary-button">
            <Play size={16} />
            运行分析
          </button>
        </div>
      </header>

      <section className="dashboard-band" aria-label="项目概览">
        <div className="status-summary">
          <span className="status-dot" />
          <div>
            <strong>Project Dashboard</strong>
            <span>当前为重置版基线，等待导入 workspace 并生成 committed snapshot。</span>
          </div>
        </div>
        <div className="capability-grid">
          {capabilityCards.map((card) => (
            <div className={`capability ${card.tone}`} key={card.label}>
              <span>{card.label}</span>
              <strong>{card.value}</strong>
            </div>
          ))}
        </div>
      </section>

      <section className="workbench" aria-label="任务工作台">
        <aside className="task-rail">
          <div className="rail-section">
            <h2>常用任务</h2>
            <div className="task-list">
              {tasks.map((task) => {
                const Icon = task.icon;
                return (
                  <button
                    type="button"
                    className={`task-button ${task.key === activeTask ? 'active' : ''}`}
                    key={task.key}
                    onClick={() => setActiveTask(task.key)}
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
          </div>

          <div className="rail-section compact">
            <h2>最近报告</h2>
            {recentReports.map((report) => (
              <button type="button" className="report-link" key={report}>
                {report}
              </button>
            ))}
          </div>
        </aside>

        <section className="query-panel">
          <div className="query-header">
            <div className="task-icon">
              <SelectedIcon size={22} />
            </div>
            <div>
              <h2>{selectedTask.title}</h2>
              <p>{selectedTask.hint}</p>
            </div>
          </div>

          <label className="query-box">
            <Search size={20} />
            <textarea value={query} onChange={(event) => setQuery(event.target.value)} rows={4} />
          </label>

          <div className="query-actions">
            <button type="button" className="primary-button">
              <ArrowRight size={16} />
              生成初版报告
            </button>
            <button type="button" className="secondary-button">
              <Braces size={16} />
              查看候选
            </button>
          </div>

          <div className="result-summary">
            <div>
              <span className="summary-label">风险</span>
              <strong>等待分析</strong>
              <p>运行后会展示 Fast/Deep 状态、受影响入口、SQL/table、建议测试和截断说明。</p>
            </div>
            <div className="path-preview" aria-label="影响路径预览">
              <span>EntryPoint</span>
              <i />
              <span>Service</span>
              <i />
              <span>Mapper</span>
              <i />
              <span>DbColumn</span>
            </div>
          </div>
        </section>

        <aside className="evidence-panel">
          <section>
            <h2>证据与覆盖</h2>
            <div className="evidence-row">
              <ShieldCheck size={18} />
              <span>查询默认 pin 到 committed snapshot。</span>
            </div>
            <div className="evidence-row">
              <AlertTriangle size={18} />
              <span>未运行导入审查，盲区和 capability level 待确认。</span>
            </div>
          </section>

          <section>
            <h2>默认展示规则</h2>
            <ul>
              <li>优先展示业务友好名称。</li>
              <li>raw SymbolId 仅在证据详情中展开。</li>
              <li>AI candidate 默认不进入确定路径。</li>
            </ul>
          </section>
        </aside>
      </section>
    </main>
  );
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
