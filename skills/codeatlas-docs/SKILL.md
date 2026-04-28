---
name: codeatlas-docs
description: Maintain CodeAtlas project memory docs. Use when updating docs/design.md, docs/plan.md, docs/task.md, or when reconciling architecture decisions, MVP scope, estimates, skills, or product behavior for CodeAtlas.
---

# CodeAtlas Docs

## Workflow

1. Read the relevant project memory docs before editing:
   - `docs/design.md` for architecture and contracts.
   - `docs/plan.md` for phases and sequencing.
   - `docs/task.md` for executable task lists and acceptance checks.
2. Preserve the current strategy:
   - MVP first closes Spring/Struts1/JSP/MyBatis minimum impact paths.
   - Tai-e and FFM are benchmark-driven enhancements, not MVP gates.
   - Static analysis and Neo4j produce facts; AI explains evidence.
   - Every conclusion needs evidence, confidence, and source type.
3. Keep docs aligned:
   - If architecture changes, update `design.md`.
   - If order/scope changes, update `plan.md`.
   - If implementation work changes, update `task.md`.
4. Validate with targeted searches for the changed terms.

## Guardrails

- Do not expand MVP scope without explicitly moving work into an enhancement phase.
- Do not describe AI as a fact source.
- Do not put FFM or Tai-e into the critical path unless the plan says benchmark criteria are met.
- Keep Seasar2 deterministic impact analysis out of MVP; MVP only requires dicon/component discovery.
- Keep results productized as structured evidence reports, not chat-only answers.

## Reference

Read `references/doc-contract.md` when making broad documentation changes.

