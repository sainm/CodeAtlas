---
name: codeatlas-java-jsp-analyzer
description: Build CodeAtlas Java, JSP, Spring, Struts1, Seasar2, MyBatis, and SQL analyzers. Use when implementing Spoon, Jasper, WebAppContext, framework adapters, SQL parsing, or variable-flow extraction.
---

# CodeAtlas Java/JSP Analyzer

## Workflow

1. Start with the MVP chain:
   - Spring RequestMapping.
   - Struts action.
   - JSP form/action.
   - Java direct call.
   - MyBatis statement.
2. Emit graph facts, not final prose. Every emitted relation needs:
   - `symbolId`
   - `factKey`
   - `evidenceKey`
   - `confidence`
   - `sourceType`
   - file/path/line or XML/JSP/SQL evidence.
3. Use the intended engines:
   - Spoon for Java source model.
   - Apache Jasper for JSP semantics.
   - JetHTMLParser/Jericho only as JSP-tolerant fallback.
   - JSqlParser for SQL tables/columns.
   - Tai-e only in enhancement work.
4. Prefer conservative certainty:
   - Use `CERTAIN` for parsed source/config facts.
   - Use `LIKELY` or `POSSIBLE` for fallback parsing, conventions, missing classpath, or partial TLD context.

## JSP Rules

- Build `WebAppContext` before Jasper parsing.
- Include web root, `web.xml`, classpath, servlet/JSP API profile, TaglibRegistry, include resolver, and encoding resolver.
- If Jasper fails, record missing context and fall back to tolerant extraction.
- Do not use jsoup directly for raw JSP.

## Seasar2 MVP Boundary

- MVP only requires dicon/component discovery and candidate relations.
- Deterministic Seasar2 impact paths belong to enhancement work.

## Reference

Read `references/analyzer-contract.md` for source-specific emit rules.

