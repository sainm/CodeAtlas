# CodeAtlas Graph Contract

Facts must be stable, explainable, and incrementally safe.

Required fields:

- `symbolId`
- `factKey`
- `evidenceKey`
- `snapshotId`
- `analysisRunId`
- `scopeKey`
- `confidence`
- `sourceType`
- `active` or equivalent active-snapshot view

Tombstone facts that disappear from a reanalyzed scope. Never return tombstoned facts for current snapshot queries.

