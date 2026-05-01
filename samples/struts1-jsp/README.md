# Struts1 JSP Sample

This sample is a static-analysis fixture for CodeAtlas. It is intentionally small and is not wired into the main Gradle build.

It covers:

- `web.xml` ActionServlet discovery.
- Default and named module Struts config files through `init-param config` and `config/admin`.
- Struts plug-ins such as Tiles and Validator.
- Struts controller settings such as `processorClass`, `inputForward`, and upload limits.
- Global forwards to JSPs and module actions, message resource bundles, and exception mappings with handlers.
- Tiles definitions with definition inheritance and JSP put values.
- Validator XML forms, fields, and depends rules.
- `DynaActionForm` properties declared in module config and bound from JSP inputs.
- `DispatchAction` and `LookupDispatchAction` style `parameter="method"` routing to candidate Action methods.
- `html:form`, `html:text`, `html:hidden`, `html:textarea`, `html:checkbox`, `html:submit`, and static `html:link` navigation.
- `bean:write` and `logic:iterate`.
- `ActionForm` field binding.
- Tiles layout JSPs that render inserted bodies for default and admin modules.
- Action -> Service -> Mapper style Java calls.
- MyBatis XML statement -> table/column extraction.
