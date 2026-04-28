# Struts1 JSP Sample

This sample is a static-analysis fixture for CodeAtlas. It is intentionally small and is not wired into the main Gradle build.

It covers:

- `web.xml` ActionServlet discovery.
- Multiple Struts config files through `init-param config`.
- Struts plug-ins such as Tiles and Validator.
- Struts controller settings such as `processorClass`, `inputForward`, and upload limits.
- `html:form`, `html:text`, `html:hidden`, `html:textarea`.
- `bean:write` and `logic:iterate`.
- `ActionForm` field binding.
- Action -> Service -> Mapper style Java calls.
- MyBatis XML statement -> table/column extraction.
