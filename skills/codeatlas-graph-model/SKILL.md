---
name: codeatlas-graph-model
description: Maintain CodeAtlas Neo4j graph model and fact lifecycle. Use when designing or changing labels, relationships, symbolId, factKey, evidenceKey, confidence, sourceType, snapshots, tombstones, or graph query semantics.
---

# CodeAtlas Graph Model

## Workflow

1. Read `docs/design.md` sections for Neo4j graph model, SymbolId, incremental graph semantics, and impact query contracts.
2. Model Java code as fact nodes:
   - Use `Class`, `Interface`, `Method`, `Field` for code facts.
   - Treat `Controller`, `Service`, and `Dao` as role labels/properties unless a framework mapping node is required.
   - Use `ActionPath` and `ApiEndpoint` for routing concepts.
3. Enforce stable keys:
   - `symbolId` for entities.
   - `factKey` for relation facts.
   - `evidenceKey` for each analyzer-specific proof.
4. Preserve lifecycle semantics:
   - Analyze by `scopeKey`.
   - Upsert emitted facts.
   - Tombstone old facts in reanalyzed scopes.
   - Query only active facts for the current snapshot.
5. Make uncertainty explicit with `confidence` and `sourceType`.

## MVP Minimum Relations

```text
ApiEndpoint -[:ROUTES_TO]-> Method
ActionPath -[:ROUTES_TO]-> Method
JspForm -[:SUBMITS_TO]-> ApiEndpoint/ActionPath
Method -[:CALLS]-> Method
Method -[:BINDS_TO]-> SqlStatement
SqlStatement -[:READS_TABLE|WRITES_TABLE]-> DbTable
```

## Guardrails

- Do not create duplicate Controller/Service/Dao nodes for the same Java class or method.
- Do not let `AI_ASSISTED` raise confidence for a fact.
- Do not return tombstoned facts in current snapshot queries.
- Do not make JVM cache or FFM the fact source; Neo4j active facts remain authoritative.

## Reference

Read `references/graph-contract.md` before implementing schema, upsert, snapshot, or query logic.

