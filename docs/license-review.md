# CodeAtlas License Review

This file tracks third-party dependencies before they enter the default CodeAtlas distribution.

## Current Build Dependencies

| Dependency | Purpose | License status | Notes |
| --- | --- | --- | --- |
| Gradle 9.4.1 | Build system | Pending formal review | Wrapper generated for project builds. |
| Foojay Resolver Convention | Gradle Java toolchain provisioning | Pending formal review | Used to resolve Java 25 toolchains. |
| JUnit Jupiter | Unit testing | Pending formal review | Used by `codeatlas-graph` tests. |
| React | UI runtime | Pending formal review | Used by `codeatlas-ui`. |
| React DOM | UI runtime | Pending formal review | Used by `codeatlas-ui`. |
| Vite | UI build | Pending formal review | Used by `codeatlas-ui`. |
| TypeScript | UI type checking | Pending formal review | Used by `codeatlas-ui`. |
| Lucide React | UI icons | Pending formal review | Used by `codeatlas-ui`. |
| Spoon Core 11.3.1-beta-8 | Java source analysis | Pending formal review | Used by `codeatlas-analyzers`; must pass review before default release packaging. |
| Neo4j Java Driver 6.0.3 | Graph persistence | Apache 2.0, pending formal review | Used by `codeatlas-graph` Neo4j writer. |
| JGit 7.6.0.202603022253-r | Git repository metadata and diff reading | EDL/BSD-style, pending formal review | Used by `codeatlas-analyzers` Git reader. |

## Planned Dependencies Requiring Review

| Dependency | Planned purpose | Notes |
| --- | --- | --- |
| Apache Jasper | JSP semantic parser | MVP JSP parser candidate. |
| JetHTMLParser / Jericho | JSP-tolerant fallback parser | Pick one after compatibility check. |
| JSqlParser | SQL table/column parsing | MVP SQL parser candidate. |
| ASM / ClassGraph | Fast classpath scanning | Needed for fast indexing. |
| Tai-e | Deep static analysis | Reviewed 2026-05-01: GPL-3.0/LGPL-3.0 licenses found; enhancement-only optional worker. Do not add to default distribution without legal approval and dependency notice packaging. Source: https://github.com/pascal-lab/Tai-e |

## Review Decisions

| Dependency | Decision | Conditions |
| --- | --- | --- |
| Tai-e | Conditionally allowed only as an optional enhancement worker. | Keep out of the MVP/default runtime path; run in an isolated worker JVM; do not shade into CodeAtlas default artifacts; require explicit operator opt-in, license notice packaging, and legal approval before production distribution. |

## Gate

Dependencies that have not passed review must not be added to the default distribution as production requirements.
