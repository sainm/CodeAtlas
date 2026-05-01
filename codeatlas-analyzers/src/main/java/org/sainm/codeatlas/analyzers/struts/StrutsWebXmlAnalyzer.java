package org.sainm.codeatlas.analyzers.struts;

import org.sainm.codeatlas.analyzers.xml.SafeXmlDocumentLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class StrutsWebXmlAnalyzer {
    private final SafeXmlDocumentLoader xmlLoader = new SafeXmlDocumentLoader();

    public StrutsWebXmlAnalysisResult analyze(Path webXml) {
        Document document = xmlLoader.load(webXml);
        Set<String> actionServletNames = new LinkedHashSet<>();
        Set<String> configLocations = new LinkedHashSet<>();
        Set<String> urlPatterns = new LinkedHashSet<>();
        List<StrutsModuleConfig> moduleConfigs = new ArrayList<>();

        NodeList servlets = document.getElementsByTagName("servlet");
        for (int i = 0; i < servlets.getLength(); i++) {
            Element servlet = (Element) servlets.item(i);
            String servletClass = text(servlet, "servlet-class");
            if (!"org.apache.struts.action.ActionServlet".equals(servletClass)) {
                continue;
            }
            String servletName = text(servlet, "servlet-name");
            if (servletName != null) {
                actionServletNames.add(servletName);
            }
            boolean hasConfigParam = false;
            NodeList initParams = servlet.getElementsByTagName("init-param");
            for (int j = 0; j < initParams.getLength(); j++) {
                Element initParam = (Element) initParams.item(j);
                String paramName = text(initParam, "param-name");
                if (paramName != null && (paramName.equals("config") || paramName.startsWith("config/"))) {
                    hasConfigParam = true;
                    String modulePrefix = paramName.equals("config") ? "" : paramName.substring("config".length());
                    for (String location : splitConfigLocations(text(initParam, "param-value"))) {
                        configLocations.add(location);
                        moduleConfigs.add(new StrutsModuleConfig(modulePrefix, location));
                    }
                }
            }
            if (!hasConfigParam) {
                String defaultLocation = "/WEB-INF/struts-config.xml";
                configLocations.add(defaultLocation);
                moduleConfigs.add(new StrutsModuleConfig("", defaultLocation));
            }
        }

        NodeList mappings = document.getElementsByTagName("servlet-mapping");
        for (int i = 0; i < mappings.getLength(); i++) {
            Element mapping = (Element) mappings.item(i);
            String servletName = text(mapping, "servlet-name");
            if (servletName != null && actionServletNames.contains(servletName)) {
                String urlPattern = text(mapping, "url-pattern");
                if (urlPattern != null) {
                    urlPatterns.add(urlPattern);
                }
            }
        }

        return new StrutsWebXmlAnalysisResult(
            new ArrayList<>(actionServletNames),
            new ArrayList<>(configLocations),
            new ArrayList<>(urlPatterns),
            moduleConfigs
        );
    }

    private List<String> splitConfigLocations(String value) {
        if (value == null || value.isBlank()) {
            return List.of("/WEB-INF/struts-config.xml");
        }
        List<String> locations = new ArrayList<>();
        for (String part : value.split(",")) {
            String location = part.trim();
            if (!location.isBlank()) {
                locations.add(location);
            }
        }
        return locations.isEmpty() ? List.of("/WEB-INF/struts-config.xml") : locations;
    }

    private String text(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = nodes.item(0).getTextContent();
        return value == null || value.isBlank() ? null : value.trim();
    }
}
