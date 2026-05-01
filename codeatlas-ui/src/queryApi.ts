import type { GraphQueryMode, QueryPlan, QueryResultPayload } from './types';

export function endpointList(endpoint: string) {
  const normalized = endpoint.replaceAll(' or ', ' and ');
  return normalized.split(' and ')
    .map((value) => value.trim())
    .filter((value) => value.startsWith('/api/'));
}

export function graphEndpointList(mode: GraphQueryMode) {
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

export function endpointUrl(endpoint: string, plan: QueryPlan, parameters: Record<string, string>) {
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

export function parameterKeys(plan: QueryPlan) {
  return Array.from(new Set([...plan.requiredParameters, ...Object.keys(plan.defaultParameters)]));
}

export function mergeParams(defaults: Record<string, string>, current: Record<string, string>, plan: QueryPlan) {
  const next = { ...defaults, ...plan.defaultParameters };
  for (const key of Object.keys(current)) {
    if (key in next && current[key]) {
      next[key] = current[key];
    }
  }
  return next;
}

export function mergePayloads(payloads: QueryResultPayload[]) {
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
