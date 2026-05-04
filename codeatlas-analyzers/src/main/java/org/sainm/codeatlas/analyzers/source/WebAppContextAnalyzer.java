package org.sainm.codeatlas.analyzers.source;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public final class WebAppContextAnalyzer {
    private WebAppContextAnalyzer() {
    }

    public static WebAppContextAnalyzer defaults() {
        return new WebAppContextAnalyzer();
    }

    public WebAppContext analyze(Path webRoot) {
        if (webRoot == null) {
            throw new IllegalArgumentException("webRoot is required");
        }
        Path normalizedWebRoot = webRoot.toAbsolutePath().normalize();
        List<JavaAnalysisDiagnostic> diagnostics = new ArrayList<>();
        List<WebPageEncodingInfo> pageEncodings = new ArrayList<>();
        List<StrutsConfigPathInfo> strutsConfigs = new ArrayList<>();
        List<WebServletMappingInfo> servletMappings = new ArrayList<>();
        String servletVersion = "";
        Path webXml = normalizedWebRoot.resolve("WEB-INF/web.xml");
        if (Files.isRegularFile(webXml)) {
            try {
                Document document = documentBuilder().parse(webXml.toFile());
                Element root = document.getDocumentElement();
                servletVersion = attr(root, "version");
                pageEncodings.addAll(pageEncodings(root));
                strutsConfigs.addAll(strutsConfigs(root));
                servletMappings.addAll(servletMappings(root));
            } catch (IOException | ParserConfigurationException | SAXException exception) {
                diagnostics.add(new JavaAnalysisDiagnostic(
                        "WEB_XML_PARSE_FAILED",
                        logicalPath(webXml) + ": " + exception.getMessage()));
            }
        }

        List<String> tldFiles = filesMatching(normalizedWebRoot, path -> path.getFileName().toString().endsWith(".tld"));
        List<String> tagFiles = filesMatching(normalizedWebRoot.resolve("WEB-INF/tags"),
                path -> path.getFileName().toString().endsWith(".tag")
                        || path.getFileName().toString().endsWith(".tagx"));
        List<String> jars = filesMatching(normalizedWebRoot.resolve("WEB-INF/lib"),
                path -> path.getFileName().toString().toLowerCase().endsWith(".jar"));
        List<String> classpathCandidates = new ArrayList<>();
        Path classes = normalizedWebRoot.resolve("WEB-INF/classes");
        if (Files.isDirectory(classes)) {
            classpathCandidates.add(logicalPath(classes));
        }
        classpathCandidates.addAll(jars);

        return new WebAppContext(
                logicalPath(normalizedWebRoot),
                Files.isRegularFile(webXml) ? logicalPath(webXml) : "",
                servletVersion,
                pageEncodings,
                tldFiles,
                tagFiles,
                jars,
                classpathCandidates,
                strutsConfigs,
                servletMappings,
                diagnostics);
    }

    private static List<WebPageEncodingInfo> pageEncodings(Element root) {
        List<WebPageEncodingInfo> result = new ArrayList<>();
        for (Element jspConfig : children(root, "jsp-config")) {
            for (Element group : children(jspConfig, "jsp-property-group")) {
                String encoding = text(firstChild(group, "page-encoding"));
                if (encoding.isBlank()) {
                    continue;
                }
                for (Element pattern : children(group, "url-pattern")) {
                    result.add(new WebPageEncodingInfo(text(pattern), encoding));
                }
            }
        }
        return result;
    }

    private static List<StrutsConfigPathInfo> strutsConfigs(Element root) {
        List<StrutsConfigPathInfo> result = new ArrayList<>();
        for (Element servlet : children(root, "servlet")) {
            String servletClass = text(firstChild(servlet, "servlet-class"));
            if (!servletClass.endsWith("ActionServlet")) {
                continue;
            }
            boolean hasConfigParam = false;
            for (Element initParam : children(servlet, "init-param")) {
                String paramName = text(firstChild(initParam, "param-name"));
                if (paramName.equals("config") || paramName.startsWith("config/")) {
                    hasConfigParam = true;
                    String moduleKey = strutsModuleKey(paramName);
                    for (String path : text(firstChild(initParam, "param-value")).split(",")) {
                        String trimmed = path.trim();
                        if (!trimmed.isBlank()) {
                            result.add(new StrutsConfigPathInfo(moduleKey, trimmed));
                        }
                    }
                }
            }
            if (!hasConfigParam) {
                result.add(new StrutsConfigPathInfo("", "/WEB-INF/struts-config.xml"));
            }
        }
        return result;
    }

    private static String strutsModuleKey(String paramName) {
        if (!paramName.startsWith("config/")) {
            return "";
        }
        String moduleKey = paramName.substring("config/".length()).trim();
        while (moduleKey.startsWith("/")) {
            moduleKey = moduleKey.substring(1);
        }
        while (moduleKey.endsWith("/")) {
            moduleKey = moduleKey.substring(0, moduleKey.length() - 1);
        }
        return moduleKey;
    }

    private static List<WebServletMappingInfo> servletMappings(Element root) {
        List<WebServletMappingInfo> result = new ArrayList<>();
        for (Element mapping : children(root, "servlet-mapping")) {
            result.add(new WebServletMappingInfo(
                    text(firstChild(mapping, "servlet-name")),
                    text(firstChild(mapping, "url-pattern"))));
        }
        return result;
    }

    private static List<String> filesMatching(Path root, FilePredicate predicate) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(predicate::matches)
                    .map(WebAppContextAnalyzer::logicalPath)
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private static DocumentBuilder documentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return builder;
    }

    private static void setFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException ignored) {
            // Parser implementations vary; the resolver still prevents external access.
        }
    }

    private static Element firstChild(Element parent, String tagName) {
        for (Element child : children(parent, tagName)) {
            return child;
        }
        return null;
    }

    private static List<Element> children(Element parent, String tagName) {
        if (parent == null) {
            return List.of();
        }
        List<Element> result = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && element.getTagName().equals(tagName)) {
                result.add(element);
            }
        }
        return result;
    }

    private static String attr(Element element, String name) {
        return element == null ? "" : element.getAttribute(name).trim();
    }

    private static String text(Element element) {
        return element == null ? "" : element.getTextContent().trim();
    }

    private static String logicalPath(Path path) {
        String value = path.toAbsolutePath().normalize().toString().replace('\\', '/');
        List<String> anchors = List.of("src/main/webapp", "src/test/webapp", "webapp", "WebContent");
        return anchors.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(anchor -> {
                    int index = value.indexOf(anchor);
                    return index >= 0 ? value.substring(index) : "";
                })
                .filter(candidate -> !candidate.isBlank())
                .findFirst()
                .orElse(path.getFileName().toString());
    }

    private interface FilePredicate {
        boolean matches(Path path);
    }
}
