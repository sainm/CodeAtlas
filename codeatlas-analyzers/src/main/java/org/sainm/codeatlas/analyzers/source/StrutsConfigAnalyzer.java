package org.sainm.codeatlas.analyzers.source;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public final class StrutsConfigAnalyzer {
    private StrutsConfigAnalyzer() {
    }

    public static StrutsConfigAnalyzer defaults() {
        return new StrutsConfigAnalyzer();
    }

    public StrutsConfigAnalysisResult analyze(Path sourceRoot, List<Path> configFiles) {
        if (sourceRoot == null) {
            throw new IllegalArgumentException("sourceRoot is required");
        }
        if (configFiles == null || configFiles.isEmpty()) {
            return new StrutsConfigAnalysisResult(
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        return analyzeConfigured(sourceRoot, configFiles.stream()
                .map(configFile -> new ConfiguredStrutsConfig(moduleKey(configFile), configFile))
                .toList());
    }

    public StrutsConfigAnalysisResult analyze(Path webRoot, WebAppContext context) {
        if (webRoot == null) {
            throw new IllegalArgumentException("webRoot is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        if (context.strutsConfigs().isEmpty()) {
            return new StrutsConfigAnalysisResult(
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        return analyzeConfigured(webRoot, context.strutsConfigs().stream()
                .map(config -> new ConfiguredStrutsConfig(
                        config.moduleKey(),
                        resolveConfigPath(webRoot, context.webRoot(), config.path())))
                .toList());
    }

    private static StrutsConfigAnalysisResult analyzeConfigured(
            Path sourceRoot,
            List<ConfiguredStrutsConfig> configFiles) {
        List<StrutsModuleInfo> modules = new ArrayList<>();
        List<StrutsFormBeanInfo> formBeans = new ArrayList<>();
        List<StrutsActionInfo> actions = new ArrayList<>();
        List<StrutsForwardInfo> globalForwards = new ArrayList<>();
        List<StrutsExceptionInfo> globalExceptions = new ArrayList<>();
        List<StrutsMessageResourceInfo> messageResources = new ArrayList<>();
        List<StrutsPluginInfo> plugins = new ArrayList<>();
        List<StrutsControllerInfo> controllers = new ArrayList<>();
        List<JavaAnalysisDiagnostic> diagnostics = new ArrayList<>();

        for (ConfiguredStrutsConfig configured : configFiles) {
            Path configFile = configured.configFile();
            try {
                Document document = documentBuilder().parse(configFile.toFile());
                Element root = document.getDocumentElement();
                String moduleKey = configured.moduleKey();
                String modulePrefix = moduleKey.isBlank() ? "" : "/" + moduleKey;
                SourceLocation fileLocation = location(sourceRoot, configFile);
                modules.add(new StrutsModuleInfo(moduleKey, modulePrefix, fileLocation.relativePath(), fileLocation));
                formBeans.addAll(formBeans(moduleKey, sourceRoot, configFile, root));
                globalForwards.addAll(forwards(moduleKey, "", sourceRoot, configFile, firstChild(root, "global-forwards")));
                globalExceptions.addAll(exceptions(moduleKey, "", sourceRoot, configFile, firstChild(root, "global-exceptions")));
                actions.addAll(actions(moduleKey, modulePrefix, sourceRoot, configFile, firstChild(root, "action-mappings")));
                messageResources.addAll(messageResources(moduleKey, sourceRoot, configFile, root));
                plugins.addAll(plugins(moduleKey, sourceRoot, configFile, root));
                controllers.addAll(controllers(moduleKey, sourceRoot, configFile, root));
            } catch (IOException | ParserConfigurationException | SAXException exception) {
                diagnostics.add(new JavaAnalysisDiagnostic(
                        "STRUTS_CONFIG_PARSE_FAILED",
                        configFile + ": " + exception.getMessage()));
            }
        }

        return new StrutsConfigAnalysisResult(
                modules,
                formBeans,
                actions,
                globalForwards,
                globalExceptions,
                messageResources,
                plugins,
                controllers,
                diagnostics);
    }

    private static Path resolveConfigPath(Path webRoot, String logicalWebRoot, String configPath) {
        String normalized = configPath == null ? "" : configPath.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String logicalRoot = logicalWebRoot == null ? "" : logicalWebRoot.replace('\\', '/');
        if (!logicalRoot.isBlank() && normalized.startsWith(logicalRoot + "/")) {
            normalized = normalized.substring(logicalRoot.length() + 1);
        }
        return webRoot.resolve(normalized).normalize();
    }

    private static List<StrutsFormBeanInfo> formBeans(
            String moduleKey,
            Path sourceRoot,
            Path configFile,
            Element root) {
        Element container = firstChild(root, "form-beans");
        if (container == null) {
            return List.of();
        }
        List<StrutsFormBeanInfo> result = new ArrayList<>();
        for (Element formBean : children(container, "form-bean")) {
            String type = attr(formBean, "type");
            List<StrutsFormPropertyInfo> properties = new ArrayList<>();
            for (Element property : children(formBean, "form-property")) {
                properties.add(new StrutsFormPropertyInfo(
                        attr(property, "name"),
                        attr(property, "type"),
                        attr(property, "initial"),
                        location(sourceRoot, configFile)));
            }
            result.add(new StrutsFormBeanInfo(
                    moduleKey,
                    attr(formBean, "name"),
                    type,
                    type.endsWith("DynaActionForm"),
                    properties,
                    location(sourceRoot, configFile)));
        }
        return result;
    }

    private static List<StrutsActionInfo> actions(
            String moduleKey,
            String modulePrefix,
            Path sourceRoot,
            Path configFile,
            Element mappings) {
        if (mappings == null) {
            return List.of();
        }
        List<StrutsActionInfo> result = new ArrayList<>();
        for (Element action : children(mappings, "action")) {
            String localPath = attr(action, "path");
            String path = modulePrefix + localPath;
            List<StrutsForwardInfo> forwards = new ArrayList<>();
            String actionForward = attr(action, "forward");
            if (!actionForward.isBlank()) {
                forwards.add(new StrutsForwardInfo(
                        moduleKey,
                        path,
                        "forward",
                        actionForward,
                        false,
                        false,
                        location(sourceRoot, configFile)));
            }
            forwards.addAll(forwards(moduleKey, path, sourceRoot, configFile, action));
            result.add(new StrutsActionInfo(
                    moduleKey,
                    path,
                    attr(action, "type"),
                    attr(action, "name"),
                    attr(action, "scope"),
                    attr(action, "parameter"),
                    dispatchKind(attr(action, "type")),
                    forwards,
                    exceptions(moduleKey, path, sourceRoot, configFile, action),
                    location(sourceRoot, configFile)));
        }
        return result;
    }

    private static List<StrutsForwardInfo> forwards(
            String moduleKey,
            String ownerPath,
            Path sourceRoot,
            Path configFile,
            Element parent) {
        if (parent == null) {
            return List.of();
        }
        List<StrutsForwardInfo> result = new ArrayList<>();
        for (Element forward : children(parent, "forward")) {
            result.add(new StrutsForwardInfo(
                    moduleKey,
                    ownerPath,
                    attr(forward, "name"),
                    attr(forward, "path"),
                    Boolean.parseBoolean(attr(forward, "redirect")),
                    Boolean.parseBoolean(attr(forward, "contextRelative")),
                    location(sourceRoot, configFile)));
        }
        return result;
    }

    private static List<StrutsExceptionInfo> exceptions(
            String moduleKey,
            String ownerPath,
            Path sourceRoot,
            Path configFile,
            Element parent) {
        if (parent == null) {
            return List.of();
        }
        List<StrutsExceptionInfo> result = new ArrayList<>();
        for (Element exception : children(parent, "exception")) {
            result.add(new StrutsExceptionInfo(
                    moduleKey,
                    ownerPath,
                    attr(exception, "key"),
                    attr(exception, "type"),
                    attr(exception, "path"),
                    location(sourceRoot, configFile)));
        }
        return result;
    }

    private static List<StrutsMessageResourceInfo> messageResources(
            String moduleKey,
            Path sourceRoot,
            Path configFile,
            Element root) {
        List<StrutsMessageResourceInfo> result = new ArrayList<>();
        for (Element resource : children(root, "message-resources")) {
            result.add(new StrutsMessageResourceInfo(
                    moduleKey,
                    attr(resource, "parameter"),
                    attr(resource, "key"),
                    location(sourceRoot, configFile)));
        }
        return result;
    }

    private static List<StrutsPluginInfo> plugins(
            String moduleKey,
            Path sourceRoot,
            Path configFile,
            Element root) {
        List<StrutsPluginInfo> result = new ArrayList<>();
        for (Element plugin : children(root, "plug-in")) {
            Map<String, String> properties = new LinkedHashMap<>();
            for (Element property : children(plugin, "set-property")) {
                properties.put(attr(property, "property"), attr(property, "value"));
            }
            String className = attr(plugin, "className");
            result.add(new StrutsPluginInfo(
                    moduleKey,
                    className,
                    pluginKind(className),
                    properties,
                    location(sourceRoot, configFile)));
        }
        return result;
    }

    private static List<StrutsControllerInfo> controllers(
            String moduleKey,
            Path sourceRoot,
            Path configFile,
            Element root) {
        Element controller = firstChild(root, "controller");
        if (controller == null) {
            return List.of();
        }
        return List.of(new StrutsControllerInfo(
                moduleKey,
                attr(controller, "processorClass"),
                attr(controller, "contentType"),
                Boolean.parseBoolean(attr(controller, "locale")),
                location(sourceRoot, configFile)));
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
            // XML parser implementations vary; secure-processing plus entity resolver still keeps parsing local.
        }
    }

    private static StrutsActionDispatchKind dispatchKind(String type) {
        if (type.endsWith("LookupDispatchAction")) {
            return StrutsActionDispatchKind.LOOKUP_DISPATCH;
        }
        if (type.endsWith("MappingDispatchAction")) {
            return StrutsActionDispatchKind.MAPPING_DISPATCH;
        }
        if (type.endsWith("DispatchAction")) {
            return StrutsActionDispatchKind.DISPATCH;
        }
        return StrutsActionDispatchKind.STANDARD;
    }

    private static StrutsPluginKind pluginKind(String className) {
        String normalized = className.toLowerCase(Locale.ROOT);
        if (normalized.contains("tiles")) {
            return StrutsPluginKind.TILES;
        }
        if (normalized.contains("validator")) {
            return StrutsPluginKind.VALIDATOR;
        }
        return StrutsPluginKind.OTHER;
    }

    private static String moduleKey(Path configFile) {
        String fileName = configFile.getFileName().toString();
        if (fileName.equals("struts-config.xml")) {
            return "";
        }
        if (fileName.startsWith("struts-config-") && fileName.endsWith(".xml")) {
            return fileName.substring("struts-config-".length(), fileName.length() - ".xml".length());
        }
        return "";
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

    private static SourceLocation location(Path sourceRoot, Path file) {
        String relativePath = sourceRoot.toAbsolutePath().normalize()
                .relativize(file.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
        return new SourceLocation(relativePath, 1, 1);
    }

    private record ConfiguredStrutsConfig(String moduleKey, Path configFile) {
        private ConfiguredStrutsConfig {
            moduleKey = moduleKey == null ? "" : moduleKey;
            if (configFile == null) {
                throw new IllegalArgumentException("configFile is required");
            }
        }
    }
}
