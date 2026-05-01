# CodeAtlas RAG Vector Backend Evaluation

Date: 2026-05-01

## Decision

MVP default remains Neo4j Vector Index because CodeAtlas already uses Neo4j as the code knowledge graph and RAG results must stay close to `symbolId`, graph neighbors, evidence keys, and snapshot scope.

Alternative vector stores are enhancement options, not MVP blockers:

| Backend | Best fit | Tradeoff | CodeAtlas position |
| --- | --- | --- | --- |
| pgvector | Small to medium deployments that already run PostgreSQL and want simple operations. | Easy SQL integration, but adds another fact/index store if Neo4j remains graph source. | Good optional backend for self-hosted lightweight installs. |
| OpenSearch | Teams that already use OpenSearch for full-text search, logs, or enterprise search. | Strong hybrid search story, heavier cluster operations. | Good optional backend when full-text search becomes first-class. |
| Qdrant | Dedicated vector search with metadata payload filtering and separate scaling. | Extra service to operate; graph/evidence joins still resolve back to CodeAtlas/Neo4j. | Good optional backend for large RAG workloads or isolated vector infra. |

## Integration Contract

All vector backends must return the same CodeAtlas result shape:

- `symbolId`
- `summaryKind`
- `displayName`
- `score`
- `matchKinds`
- `evidenceKeys`
- `projectId`
- `snapshotId`

The vector backend is only a recall index. It must not become the source of truth for call graph, variable trace, JSP flow, SQL/table impact, or confidence.

## Recommended Order

1. Keep Neo4j Vector Index as v1 default.
2. Define a `SemanticSummaryVectorStore` abstraction only when a second backend is actually needed.
3. Add pgvector first if deployment simplicity and SQL operations matter most.
4. Add OpenSearch if full-text search, code search, and vector search need one search platform.
5. Add Qdrant if vector throughput, payload filtering, or independent vector scaling becomes the bottleneck.

## Sources

- pgvector official repository: https://github.com/pgvector/pgvector
- OpenSearch vector search documentation: https://docs.opensearch.org/docs/latest/vector-search/
- Qdrant payload and filtering documentation: https://qdrant.tech/documentation/concepts/payload/ and https://qdrant.tech/documentation/concepts/filtering/
