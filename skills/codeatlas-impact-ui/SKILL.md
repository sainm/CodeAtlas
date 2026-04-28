---
name: codeatlas-impact-ui
description: Build CodeAtlas impact analysis, variable trace, query APIs, and evidence UI. Use when implementing Git diff impact reports, JVM graph cache, REST query endpoints, result display, AI summaries, or MCP-facing query behavior.
---

# CodeAtlas Impact UI

## Workflow

1. Keep the query path deterministic:
   - Git diff or user query identifies symbols.
   - Neo4j active facts and JVM cache find paths.
   - AI only summarizes evidence.
2. Use the MVP query contract:
   - Input: `projectId`, `snapshotId`, `changeSet` or selected symbol.
   - Output: `entrypoint`, `changedSymbol`, `path`, `confidence`, `evidenceList`, `sourceTypeList`, `riskLevel`, `reason`, `truncated`.
3. Render results in four layers:
   - Answer summary.
   - Evidence path.
   - Evidence table.
   - Graph/detail view.
4. For natural language search, always resolve intent and candidates before running expensive trace queries.

## UI Contract

- Use a global question/search box as the primary entry.
- If multiple symbols match, show Candidate Picker first.
- Show variable trace as vertical paths, not a full graph by default.
- Limit graph display by node count and depth; show `truncated=true` with expand actions.
- Keep AI optional. With AI off, structured results must still render.
- Display "Based on N static analysis evidence items" beside AI summaries.

## Performance Guardrails

- Do not recompute source analysis on every query.
- Use precomputed Variable Flow summaries where available.
- Use JVM primitive adjacency cache for hot caller/callee paths.
- Fall back to Neo4j when cache misses or invalidates.

## Reference

Read `references/result-contract.md` before changing report JSON, REST query APIs, or UI result layouts.

