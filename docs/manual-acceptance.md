# CodeAtlas Current Manual Acceptance Checklist

This checklist is for the current MVP baseline. It verifies that the project can build, analyze representative legacy Java web code, and expose enough graph facts for later UI, MCP, and AI layers.

## 0. Preconditions

- [ ] Current directory is `D:\source\CodeAtlas`.
- [ ] Current branch is `next`.
- [ ] JDK 25 is available from `java -version`.
- [ ] Gradle wrapper is available from `.\gradlew.bat --version`.
- [ ] `git status --short` is either clean or contains only the expected current task changes.

## 1. Build

Run:

```powershell
.\gradlew.bat build --no-daemon
```

Acceptance:

- [ ] The command ends with `BUILD SUCCESSFUL`.
- [ ] `codeatlas-graph` tests pass.
- [ ] `codeatlas-analyzers` tests pass.
- [ ] `codeatlas-ai` tests pass.
- [ ] `codeatlas-mcp` tests pass.
- [ ] `codeatlas-server` tests pass.
- [ ] `codeatlas-worker` tests pass.
- [ ] `codeatlas-ui:npmBuild` passes.

## 2. Project Structure

- [ ] Gradle multi-project structure exists.
- [ ] `settings.gradle` includes `codeatlas-graph`, `codeatlas-analyzers`, `codeatlas-ai`, `codeatlas-mcp`, `codeatlas-server`, `codeatlas-worker`, and `codeatlas-ui`.
- [ ] Java group/package uses `org.sainm.codeatlas`.
- [ ] `docs/design.md`, `docs/plan.md`, and `docs/task.md` exist.
- [ ] `docs/struts1-analysis.md` exists.
- [ ] `.gitignore` excludes build output, `node_modules`, dist output, IDE files, local env files, and logs.

## 3. Graph Contract

- [ ] `SymbolId` represents project, module, source root, file, class, method, field, JSP, SQL, table, column, action path, config key, and request parameter identities.
- [ ] `FactKey` and `EvidenceKey` are stable value objects.
- [ ] `GraphFact` includes `confidence`, `sourceType`, `snapshotId`, `analysisRunId`, and `scopeKey`.
- [ ] AI-assisted facts are not marked `CERTAIN`.
- [ ] Neo4j schema/query plan code can produce labels, relationship types, constraints, indexes, caller/callee query plans, and impact path query plans.

## 4. Java Analysis

- [ ] Spoon noClasspath mode can parse Java source.
- [ ] Classes, interfaces, enums, annotations, methods, constructors, and fields are extracted.
- [ ] Direct method invocation edges are extracted.
- [ ] Extends/implements edges are extracted.
- [ ] File path, line number, and method signature evidence are preserved.
- [ ] Basic request parameter reads are extracted.
- [ ] Basic getter/setter variable propagation is extracted.
- [ ] Business jars under `WEB-INF/lib` can be indexed as class and method nodes.
- [ ] Business jar bytecode calls create `CALLS` facts for `invokevirtual`, `invokespecial`, `invokestatic`, and `invokeinterface`.
- [ ] Bytecode `CALLS` facts include sourceType `ASM`, class-file evidence, qualifier such as `bytecode-static` or `bytecode-virtual`, and confidence.

Focused command:

```powershell
.\gradlew.bat :codeatlas-analyzers:test --tests org.sainm.codeatlas.analyzers.bytecode.ClassFileAnalyzerTest --no-daemon
```

## 5. Spring Analysis

- [ ] `@Controller` and `@RestController` are detected.
- [ ] `@RequestMapping`, `@GetMapping`, `@PostMapping`, and related mapping annotations create API endpoint nodes.
- [ ] API endpoint to handler method `ROUTES_TO` facts are generated.
- [ ] `@Service`, `@Repository`, and `@Component` are detected.
- [ ] Field and constructor injection candidates are detected.
- [ ] Qualifiers such as `@Qualifier`, `@Resource(name=...)`, and `@Named` are recorded when present.

## 6. Struts1 Focus

Use `samples/struts1-jsp` as the focused fixture.

- [ ] `WEB-INF/web.xml` detects `org.apache.struts.action.ActionServlet`.
- [ ] ActionServlet `config` and `config/<module>` init params are parsed.
- [ ] ActionServlet without a `config` init param falls back to `/WEB-INF/struts-config.xml`.
- [ ] `config/admin` applies module prefix `/admin` to action path identity.
- [ ] Root action `/user/save` remains `user/save`.
- [ ] Admin module action `/user/list` becomes `admin/user/list`.
- [ ] The same physical Struts config can be analyzed under multiple module prefixes when referenced by multiple `config/<module>` params.
- [ ] Forward-only actions such as `<action path="/edit" forward="/edit.jsp"/>` do not require an Action class.
- [ ] Module-scoped `.do` forward, global-forward, and exception paths keep the current module prefix.
- [ ] `*.do` servlet mapping is parsed.
- [ ] `form-beans/form-bean` is parsed.
- [ ] `DynaActionForm` `form-property` entries create request parameter bindings.
- [ ] `action-mappings/action` creates `ACTION_PATH -ROUTES_TO-> CLASS`.
- [ ] `parameter="method"` creates `LIKELY` candidate `ACTION_PATH -ROUTES_TO-> METHOD` facts when matching Action methods are visible in Java source.
- [ ] `LookupDispatchAction#getKeyMethodMap()` simple string-literal `Map.put` entries create keyed `LIKELY` method route facts.
- [ ] Indirect Action inheritance is supported: `UserAction extends BaseAction extends Action` can route to inherited `execute`.
- [ ] Indirect LookupDispatchAction inheritance is supported: `UserLookupAction extends BaseLookupAction extends LookupDispatchAction` can use inherited `getKeyMethodMap`.
- [ ] Action form binding creates `ACTION_PATH -BINDS_TO-> CLASS`.
- [ ] Local and global forwards create `FORWARDS_TO` to JSP pages or action paths.
- [ ] Local and global exceptions create exception config facts.
- [ ] `message-resources` creates message bundle config facts.
- [ ] `controller` captures attributes and class references such as `processorClass` and `multipartClass`.
- [ ] `plug-in` and `set-property` capture Tiles and Validator plugin configuration.
- [ ] Tiles definitions parse `definition`, `extends`, and `put`.
- [ ] Validator XML parses `form`, `field property`, and `field depends`.
- [ ] Struts JSP tags create form/input/tag semantic facts.
- [ ] Struts JSP submit/cancel/button tags with `property` create request parameter write facts.
- [ ] Static `html:link action` and JSP-valued `html:link page` targets create `JSP_PAGE -FORWARDS_TO-> ...` facts.
- [ ] Static `html:link paramId` creates link parameter `WRITES_PARAM` facts to `_request` and the static link target.
- [ ] Static `logic:redirect action/page/href` targets create `JSP_PAGE -FORWARDS_TO-> ...` facts.
- [ ] Static `logic:forward name` targets create `JSP_PAGE -USES_CONFIG-> CONFIG_KEY` facts for the named Struts forward.
- [ ] Static JSP include directives and `jsp:include` actions create `JSP_PAGE -INCLUDES-> JSP_PAGE` facts.
- [ ] Static `jsp:forward page` targets create `JSP_PAGE -FORWARDS_TO-> ...` facts.

Focused commands:

```powershell
.\gradlew.bat :codeatlas-analyzers:test --tests "org.sainm.codeatlas.analyzers.struts.*" --tests "org.sainm.codeatlas.analyzers.CodeAtlasProjectAnalyzerTest" --no-daemon
Select-String -Path samples/struts1-jsp/src/main/webapp/WEB-INF/web.xml -Pattern "ActionServlet","config","config/admin","*.do"
Select-String -Path samples/struts1-jsp/src/main/webapp/WEB-INF/struts-config*.xml -Pattern "controller","plug-in","TilesPlugin","ValidatorPlugIn","DynaActionForm","form-property","global-forwards","message-resources","exception"
Select-String -Path samples/struts1-jsp/src/main/webapp/WEB-INF/tiles-defs.xml -Pattern "definition","extends","put"
Select-String -Path samples/struts1-jsp/src/main/webapp/WEB-INF/validation.xml -Pattern "form","field","depends"
Select-String -Path samples/struts1-jsp/src/main/webapp/**/*.jsp -Pattern "html:form","html:text","html:hidden","html:password","html:checkbox","html:select","html:textarea","html:radio","html:multibox","html:button","html:rewrite","html:img","bean:write","logic:iterate","tiles:getAsString","include","jsp:forward"
```

Expected relation keywords:

- [ ] `SUBMITS_TO`
- [ ] `INCLUDES`
- [ ] `WRITES_PARAM`
- [ ] `ROUTES_TO`
- [ ] `BINDS_TO`
- [ ] `FORWARDS_TO`
- [ ] `USES_CONFIG`
- [ ] `EXTENDS`
- [ ] `COVERED_BY`

## 7. JSP Analysis

- [ ] JSP semantic analysis exposes parser source metadata: Apache Jasper, Jasper plus tokenizer merge, or tokenizer fallback.
- [ ] Tokenizer fallback records a fallback reason when Apache Jasper cannot run with the available JSP path/webapp context.
- [ ] `WebAppContext` model exists.
- [ ] `WebAppContextBuilder` reads web.xml servlet version, derives JSP version, reads jsp-property-group page encoding, scans WEB-INF classpath entries, includes Gradle/Maven build output candidates, scans WEB-INF/lib jar TLDs, and maps TLD URI values.
- [ ] JSP directives, include directives, taglib directives, JSP actions, EL, scriptlets, and common JSTL tags are extracted.
- [ ] JSP taglib directives preserve prefix, uri/tagdir, resolved location, confidence, and source line.
- [ ] JSP tagdir custom tags under WEB-INF/tags are discovered and declared tagdir prefixes produce custom tag actions.
- [ ] JSP pages emit `USES_CONFIG` facts for taglib directives and resolved tagdir custom tag files.
- [ ] JSP scriptlet and JSP expression Java code emit request parameter read/write facts for `request.getParameter`, `request.getAttribute`, and `request.setAttribute`.
- [ ] JSP analysis uses Apache Jasper for standard JSP AST parsing when webapp context is available.
- [ ] JSP tokenizer fallback skips script/style blocks so JavaScript and CSS string literals do not create false Struts/JSP tag facts.
- [ ] JSP analysis does not emit JavaScript navigation facts from regex inference; client-side navigation waits for a JS parser/AST integration.
- [ ] Struts1 JSP tag recognition is centralized through `StrutsJspTagAdapter` rather than scattered parser constants.
- [ ] Struts1 Tiles JSP tags emit graph facts: `tiles:insert definition` to `USES_CONFIG`, and static JSP `tiles:put value/page` to `INCLUDES`.
- [ ] JSP include directives and `jsp:include` actions resolve through `WebAppContext`.
- [ ] HTML form/input/select/textarea are extracted.
- [ ] Struts taglib MVP covers `html:form`, `html:text`, `html:hidden`, `html:password`, `html:checkbox`, and `html:select`.
- [ ] Struts taglib extension covers `html:textarea`, `html:radio`, `html:multibox`, `html:button`, `html:rewrite`, `html:img`, `bean:write`, `logic:iterate`, and common Tiles tags.
- [ ] JSP input to request parameter relation is generated.
- [ ] Struts1 form controls include `html:file`, `html:image`, and `html:reset` in parameter extraction.
- [ ] Struts1 option tags include `html:option`, `html:options`, and `html:optionsCollection`; dynamic option sources emit `USES_CONFIG`.
- [ ] Struts1 display/control tags such as `bean:write` and common `logic:*` conditions emit `READS_PARAM` facts for bean/property references.
- [ ] Spring form taglib covers `form:form`, common `form:* path` controls, and `form:options items` option-source `USES_CONFIG` facts.
- [ ] JSP EL expressions emit conservative `READS_PARAM` facts for simple variables and property chains.
- [ ] JSP EL scope prefixes are normalized so `requestScope.user.name` is reported as `user.name`.
- [ ] The current tolerant parser does not use jsoup as the direct JSP parser.

## 8. SQL And MyBatis

- [ ] MyBatis XML mapper namespace is parsed.
- [ ] XML statement ids for `select`, `insert`, `update`, and `delete` are parsed.
- [ ] Mapper interface methods are parsed.
- [ ] Mapper method to SQL statement `BINDS_TO` facts are generated.
- [ ] SQL statement to table read/write facts are generated.
- [ ] Dynamic SQL uncertainty is represented with lower confidence.

## 9. Impact And Query

- [ ] Caller report endpoint `/api/graph/callers/report` returns active-facts paths with confidence and evidence keys.
- [ ] Callee report endpoint `/api/graph/callees/report` returns active-facts paths with confidence and evidence keys.
- [ ] JGit can read branch, commit, changed files, and diff data.
- [ ] Unified diff parser identifies changed files and hunks.
- [ ] Changed files map to candidate symbols.
- [ ] Caller/callee and impact path Neo4j query plans can be generated.
- [ ] Fast impact report JSON and Markdown outputs are available.
- [ ] Each impact path includes confidence and evidence.
- [ ] Each impact path step includes confidence, sourceType, and evidenceKeys in JSON/Markdown output.
- [ ] Impact analyze can parse non-method `symbolId` values such as JSP pages, action paths, request parameters, SQL statements, tables, and config keys.
- [ ] Impact analyze diff endpoint `/api/impact/analyze-diff` accepts unified diff text, maps changed files to active evidence symbols, and returns a Fast Impact Report.
- [ ] Changing an included JSP can report the parent JSP through `INCLUDES`.

## 10. API, MCP, AI, UI

- [ ] Natural-language call graph plans prefer evidence-carrying caller/callee report endpoints over raw Cypher-only endpoints.
- [ ] REST health endpoint returns status.
- [ ] REST API returns structured `400 bad_request` JSON for missing or invalid query parameters.
- [ ] REST API responds to browser CORS preflight `OPTIONS` requests with allow-origin and allow-method headers.
- [ ] Natural-language query planning endpoint `/api/query/plan?q=...` maps user questions to safe read-only API plans.
- [ ] Query result view endpoint `/api/query/views` exposes stable display contracts for impact, variable trace, JSP flow, graph, SQL/table, and symbol picker results.
- [ ] Query result view endpoint supports `?name=VIEW_NAME` filtering for focused UI, MCP, and Agent usage.
- [ ] Symbol search, graph caller/callee, impact path, variable trace, JSP backend flow, and impact analyze endpoints have current MVP coverage.
- [ ] Variable sink query includes Java `READS_PARAM`, ActionForm `BINDS_TO`, and Validator `COVERED_BY` destinations.
- [ ] Variable sink query can continue from a Java parameter read through downstream `PASSES_PARAM` and `READS_TABLE`/`WRITES_TABLE` effects.
- [ ] Java source analysis emits `PASSES_PARAM` when a request-derived local variable or method parameter is passed as a method argument.
- [ ] Variable trace report endpoints `/api/variables/trace-source/report` and `/api/variables/trace-sink/report` return active-facts paths with confidence, qualifiers, sourceTypes, and evidenceKeys.
- [ ] Combined variable trace endpoint `/api/variables/trace/report` returns both `SOURCE` and `SINK` paths in one result.
- [ ] Variable trace JSON includes business display fields such as `directionLabel`, `parameterDisplayName`, `endpointDisplayName`, `displayName`, and `symbolKindLabel`.
- [ ] JSP backend flow report endpoint `/api/jsp/backend-flow/report` returns active-facts paths from JSP pages through Struts routes, Java calls, SQL/table links, config links, confidence, and evidenceKeys.
- [ ] MCP exposes read-only tool definitions for query planning, symbol search, graph traversal, variable tracing, JSP flow, impact analysis, RAG semantic search, and report retrieval.
- [ ] MCP exposes a read-only query view resource for result display contracts.
- [ ] Agent profiles expose only read-only whitelisted tools with bounded max tool calls, timeout, and evidence-required policy.
- [ ] Agent tool-call guard rejects out-of-profile tools, non-read-only tools, and calls over the configured step limit.
- [ ] Agent answer contract carries summary, findings, evidence refs, confidence, sourceType, and truncation state.
- [ ] AI provider abstraction exists and can be disabled without breaking static analysis output.
- [ ] AI prompts cite evidence paths and never turn guesses into certain graph facts.
- [ ] UI can build and displays impact report, variable trace, JSP flow, graph exploration, and SQL/table views.
- [ ] UI displays query intent, endpoint, required parameters, relation types, evidence path, and evidence table.
- [ ] UI loads `/api/query/views` and displays primary/evidence field contracts for the active result view.
- [ ] UI JSON button toggles raw query plan, result view contract, path preview, and evidence preview.
- [ ] UI Execute action calls planned read-only API endpoints and renders returned path/evidence JSON before falling back to local preview data.
- [ ] UI displays symbol candidates from `/api/symbols/search` after planning and fills `symbolId` or `changedSymbol` when a candidate is selected.
- [ ] UI result summary displays path count, confidence, risk, evidence row count, truncated state, and an expand action for large results.
- [ ] UI variable trace view groups results into "值从哪里来" and "值去了哪里".
- [ ] UI variable trace view displays JSP input/form/page and request parameter symbols as business-friendly names while preserving raw JSON for detail inspection.
- [ ] Report assistant endpoint `/api/reports-assistant?reportId=...` returns summary, risk explanation, test suggestions, evidenceCount, and aiAssisted.
- [ ] UI displays report assistant summary/test suggestions and labels whether the text is AI-assisted or static fallback.

## 11. Not In Current Acceptance Scope

- [ ] Apache Jasper full JSP compilation and SMAP mapping.
- [ ] JetHTMLParser or Jericho production integration.
- [ ] Tai-e advanced call graph, pointer analysis, or taint analysis.
- [ ] FFM off-heap graph index.
- [ ] Full JSqlParser AST support.
- [ ] Neo4j vector index and complete RAG.
- [ ] Autonomous multi-step production agent orchestration.
- [ ] Full interactive graph visualization with Cytoscape.js or React Flow.
- [ ] Enterprise-grade auth, rate limiting, audit, and encrypted API key storage.

## 12. Result

- [ ] Pass: current baseline is acceptable for continued MVP development.
- [ ] Conditional pass: build passes but listed follow-up issues remain.
- [ ] Fail: build fails or a core Struts1/JSP/graph relation is unavailable.

Acceptance owner:

Acceptance date:

Issue notes:

-
