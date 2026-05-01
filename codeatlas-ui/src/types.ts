export type Mode = 'overview' | 'impact' | 'variable' | 'jsp' | 'graph' | 'sql' | 'qa';
export type RunStatus = 'idle' | 'planning' | 'executing' | 'fallback' | 'error';
export type VariableTraceDirection = 'both' | 'source' | 'sink';
export type GraphQueryMode = 'both' | 'callers' | 'callees' | 'entrySql';

export type QuickTemplate = {
  title: string;
  example: string;
  mode: Mode;
  query: string;
  parameterOverrides?: Record<string, string>;
};

export type QueryPlan = {
  intent: string;
  endpoint: string;
  method: string;
  summary: string;
  requiredParameters: string[];
  defaultParameters: Record<string, string>;
  relationTypes: string[];
  resultView: string;
};

export type QueryResultView = {
  name: string;
  title: string;
  summary: string;
  primaryFields: string[];
  evidenceFields: string[];
};

export type Evidence = {
  type: string;
  file: string;
  line: number;
  confidence: 'CERTAIN' | 'LIKELY' | 'POSSIBLE';
  text: string;
};

export type ResultStep = {
  symbolId?: string;
  incomingRelation?: string;
  qualifier?: string;
  sourceType?: string;
  analysisLayer?: string;
  confidence?: 'CERTAIN' | 'LIKELY' | 'POSSIBLE';
  evidenceKeys?: EvidenceKey[];
};

export type EvidenceKey = {
  sourceType?: string;
  analyzer?: string;
  path?: string;
  lineStart?: number;
  lineEnd?: number;
  localPath?: string;
};

export type ResultPath = {
  direction?: string;
  endpoint?: string;
  entrypoint?: string;
  changedSymbol?: string;
  confidence?: string;
  riskLevel?: string;
  truncated?: boolean;
  path?: ResultStep[];
};

export type VariableTraceSection = {
  direction: 'SOURCE' | 'SINK';
  title: string;
  paths: ResultPath[];
};

export type QueryResultPayload = {
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

export type AnalysisStageStatus = {
  id: string;
  label: string;
  state: 'ready' | 'running' | 'waiting' | 'disabled' | 'error';
  detail: string;
};

export type AffectedSymbol = {
  category: string;
  symbolId: string;
  displayName: string;
};

export type AssistantResult = {
  reportId: string;
  summary: string;
  riskExplanation: string;
  testSuggestions: string[];
  evidenceCount: number;
  aiAssisted: boolean;
};

export type SymbolCandidate = {
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
