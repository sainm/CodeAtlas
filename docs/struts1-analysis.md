# Struts1 Analysis Notes

This document records the current Struts1 analysis direction for CodeAtlas. It is written as an executable memory: every item should map to parser input, graph facts, tests, or manual acceptance checks.

## Current Scope

### web.xml

- Detect `org.apache.struts.action.ActionServlet`.
- Read ActionServlet `init-param` named `config`.
- Read module params such as `config/admin`; `/admin` becomes the Struts module prefix.
- If ActionServlet has no `config` init-param, use the Struts1 default `/WEB-INF/struts-config.xml`.
- Split comma-separated config locations.
- Read ActionServlet `servlet-mapping`, such as `*.do`.

### struts-config.xml

- Parse `form-beans/form-bean`, including `DynaActionForm`.
- Parse `form-property` under dynamic forms.
- Parse `action-mappings/action`: `path`, `type`, `name`, `scope`, `input`, `parameter`, and `forward`.
- Support forward-only actions, such as `<action path="/edit" forward="/edit.jsp"/>`.
- Parse action-local `forward` and action-local `exception`.
- Parse `global-forwards`, `global-exceptions`, and `message-resources`.
- Parse `controller` attributes such as `processorClass`, `multipartClass`, `inputForward`, and `maxFileSize`.
- Parse `plug-in` and nested `set-property`, including Tiles and Validator plugin configuration.

### Dispatch Actions

- When an action has `parameter` and the Action class source is visible, create `LIKELY` route facts from the action path to matching Struts action methods.
- Matching method signature:
  - return type contains `ActionForward`
  - parameters contain `ActionMapping`, `ActionForm`, `HttpServletRequest`, and `HttpServletResponse`
- For `LookupDispatchAction#getKeyMethodMap()`, parse simple static `Map.put("resource.key", "methodName")` mappings.
- Lookup dispatch mappings create keyed method route facts with qualifier `lookup-dispatch-method:<resourceKey>:<methodName>`.

### Tiles

- Parse Tiles `definition`: `name`, `path`, and `extends`.
- Parse Tiles `put`: `name`, `value`, and `type`.
- Treat JSP-valued `definition path` and `put value` as `FORWARDS_TO` JSP targets.
- Treat Struts forward paths such as `legacy.user.detail` or `admin.user.list` as Tiles definition `CONFIG_KEY` targets, not JSP pages.

### Validator

- Parse `form-validation/formset/form`.
- Parse `field property`.
- Parse `field depends`, splitting rule lists such as `required,maxlength`.

### JSP Struts Tags

- `html:form` creates JSP form facts and `SUBMITS_TO` action path facts.
- `html:text`, `html:hidden`, `html:password`, `html:checkbox`, `html:select`, `html:textarea`, `html:radio`, `html:multibox`, `html:submit`, `html:cancel`, and `html:button` create JSP input and `WRITES_PARAM` facts when they declare `property`.
- `html:link` is extracted as a semantic tag occurrence, and static `action` / `page` targets create `FORWARDS_TO` graph facts.
- Static JSP include directives and `jsp:include` actions create `INCLUDES` graph facts to the resolved JSP page when the path is not dynamic.
- Static `jsp:forward page` targets create `FORWARDS_TO` graph facts to JSP pages or action paths.
- `html:submit`, `html:cancel`, and `html:errors` are extracted as semantic tag occurrences.
- `html:button`, `html:rewrite`, and `html:img` are extracted as semantic tag occurrences.
- `bean:write`, `logic:iterate`, `logic:messagesPresent`, and `logic:messagesNotPresent` are extracted as semantic tag occurrences.
- `tiles:insert`, `tiles:put`, and `tiles:getAsString` are extracted as semantic tag occurrences.

## Module Prefix Semantics

- `config` maps to the root Struts module; prefix is empty.
- Missing `config` is equivalent to root module config `/WEB-INF/struts-config.xml`.
- `config/admin` maps to module prefix `/admin`.
- Root action `/user/save` remains `/user/save`.
- Action `/user/list` inside `/admin` module becomes `/admin/user/list`.
- If the action path already starts with the module prefix, the prefix is not added again.
- The same physical config file may be analyzed under multiple module prefixes.
- `.do` forward, exception, and global-forward targets inside a module inherit the current module prefix.
- CodeAtlas project/module/source-root identity stays unchanged; the Struts module prefix only affects Struts action path identity.

## Graph Mapping

- `JSP_FORM -[:SUBMITS_TO]-> ACTION_PATH`
- `JSP_INPUT -[:WRITES_PARAM]-> REQUEST_PARAMETER`
- `ACTION_PATH -[:ROUTES_TO]-> CLASS`
- `ACTION_PATH -[:ROUTES_TO]-> METHOD` for dispatch candidates, `confidence=LIKELY`
- `ACTION_PATH -[:BINDS_TO]-> CLASS`
- `ACTION_PATH -[:FORWARDS_TO]-> JSP_PAGE`
- `ACTION_PATH -[:FORWARDS_TO]-> ACTION_PATH`
- `ACTION_PATH -[:FORWARDS_TO]-> CONFIG_KEY` for Tiles definition names
- `JSP_PAGE -[:FORWARDS_TO]-> ACTION_PATH` for static `html:link action` or `.do` `page`
- `JSP_PAGE -[:FORWARDS_TO]-> JSP_PAGE` for static JSP-valued `html:link page`
- `JSP_PAGE -[:INCLUDES]-> JSP_PAGE` for static include directives and static `jsp:include`
- `JSP_PAGE -[:FORWARDS_TO]-> JSP_PAGE` for static `jsp:forward page`
- `ACTION_PATH -[:READS_PARAM]-> REQUEST_PARAMETER`
- `REQUEST_PARAMETER -[:BINDS_TO]-> FIELD`
- `REQUEST_PARAMETER -[:BINDS_TO]-> CONFIG_KEY` for `DynaActionForm` properties
- `CONFIG_KEY -[:USES_CONFIG]-> CLASS` for plugins, controllers, exception types, handlers, and factories
- `CONFIG_KEY -[:FORWARDS_TO]-> JSP_PAGE` or `ACTION_PATH` for exception paths
- `CONFIG_KEY -[:EXTENDS]-> CONFIG_KEY` for Tiles definition inheritance
- `REQUEST_PARAMETER -[:COVERED_BY]-> CONFIG_KEY` for Validator field coverage

## Manual Acceptance Fixture

Use `samples/struts1-jsp` as the focused fixture.

- `src/main/webapp/WEB-INF/web.xml`
  - Confirms ActionServlet, `config`, `config/admin`, and `*.do`.
- `src/main/webapp/WEB-INF/struts-config.xml`
  - Confirms root action, controller, Tiles plugin, Validator plugin, global forwards, message resources, and exceptions.
- `src/main/webapp/WEB-INF/struts-config-admin.xml`
  - Confirms module action, `DynaActionForm`, `form-property`, dispatch parameter, module-scoped forwards, and exceptions.
- `src/main/java/com/acme/legacy/web/AdminUserAction.java`
  - Confirms `LookupDispatchAction` key-method mapping.
- `src/main/webapp/WEB-INF/tiles-defs.xml`
  - Confirms Tiles definitions, extends, and JSP-valued puts.
- `src/main/webapp/WEB-INF/validation.xml`
  - Confirms Validator forms, fields, and depends rules.
- `src/main/webapp/user/edit.jsp` and `src/main/webapp/admin/user/list.jsp`
  - Confirm Struts JSP form/input/link/submit tag extraction, request parameter binding, dispatch button parameters, static include facts, and static navigation facts.

## Verification Commands

Run from `D:\source\CodeAtlas`:

```powershell
.\gradlew.bat :codeatlas-analyzers:test --tests "org.sainm.codeatlas.analyzers.struts.*" --tests "org.sainm.codeatlas.analyzers.CodeAtlasProjectAnalyzerTest" --no-daemon
Select-String -Path samples/struts1-jsp/src/main/webapp/WEB-INF/web.xml -Pattern "ActionServlet","config","config/admin","*.do"
Select-String -Path samples/struts1-jsp/src/main/webapp/WEB-INF/struts-config*.xml -Pattern "controller","plug-in","TilesPlugin","ValidatorPlugIn","DynaActionForm","form-property","global-forwards","message-resources","exception","parameter"
Select-String -Path samples/struts1-jsp/src/main/webapp/WEB-INF/tiles-defs.xml -Pattern "definition","extends","put"
Select-String -Path samples/struts1-jsp/src/main/webapp/WEB-INF/validation.xml -Pattern "form","field","depends"
Select-String -Path samples/struts1-jsp/src/main/java/com/acme/legacy/web/AdminUserAction.java -Pattern "LookupDispatchAction","getKeyMethodMap","button.search"
```

Expected relation keywords:

- `SUBMITS_TO`
- `INCLUDES`
- `WRITES_PARAM`
- `ROUTES_TO`
- `BINDS_TO`
- `FORWARDS_TO`
- `USES_CONFIG`
- `EXTENDS`
- `COVERED_BY`

## Confidence Rules

- XML-declared Struts config relations are `CERTAIN`.
- JSP tag declarations are `LIKELY` for submitted action and written request parameter facts.
- Dispatch and LookupDispatch method route facts are `LIKELY`.
- Static JSP link navigation facts are `LIKELY`.
- Static JSP include facts are `CERTAIN` when the target file exists and `POSSIBLE` when it cannot be resolved.
- Java variable trace propagation remains `LIKELY`.

## Known Gaps

- LookupDispatchAction currently supports simple string-literal `Map.put("resource.key", "methodName")` mappings only.
- Complex helper methods, inherited key maps, and dynamic keys are future enhancements.
- DispatchAction currently emits candidate method sets; it does not choose one method from a concrete runtime request parameter value.
- `logic:*` tags are extracted as JSP tag occurrences; graph control-flow is not modeled yet.
- Full Struts module lifecycle semantics are future work.
