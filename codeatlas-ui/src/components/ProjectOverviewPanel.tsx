import { Sparkles } from 'lucide-react';
import type { Mode, QueryResultPayload, QuickTemplate } from '../types';

type OverviewCapability = {
  label: string;
  title: string;
  text: string;
};

export function ProjectOverviewPanel({
  result,
  quickTemplates,
  overviewCapabilities,
  applyTemplate
}: {
  result: QueryResultPayload | null;
  quickTemplates: QuickTemplate[];
  overviewCapabilities: OverviewCapability[];
  applyTemplate: (template: QuickTemplate) => void;
}) {
  const capabilities = overviewCapabilityItems(result, overviewCapabilities);
  const entrypoints = overviewEntrypointItems(result, quickTemplates);
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

function overviewCapabilityItems(result: QueryResultPayload | null, fallbackCapabilities: OverviewCapability[]) {
  const items = result?.capabilities ?? [];
  if (items.length === 0) {
    return fallbackCapabilities;
  }
  return items.map((item) => ({
    label: item.label ?? item.status ?? '能力',
    title: item.title ?? '-',
    text: item.text ?? item.detail ?? item.status ?? '由后端项目总览接口返回'
  }));
}

function overviewEntrypointItems(result: QueryResultPayload | null, quickTemplates: QuickTemplate[]) {
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
