package org.sainm.codeatlas.analyzers.source;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public final class SeasarDiconAnalyzer {
    private SeasarDiconAnalyzer() {
    }

    public static SeasarDiconAnalyzer defaults() {
        return new SeasarDiconAnalyzer();
    }

    public SeasarDiconAnalysisResult analyze(Path sourceRoot, List<Path> diconFiles) {
        if (sourceRoot == null) {
            throw new IllegalArgumentException("sourceRoot is required");
        }
        if (diconFiles == null || diconFiles.isEmpty()) {
            return new SeasarDiconAnalysisResult(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        List<SeasarDiconComponentInfo> components = new ArrayList<>();
        List<SeasarDiconIncludeInfo> includes = new ArrayList<>();
        List<SeasarDiconPropertyInfo> properties = new ArrayList<>();
        List<SeasarDiconAspectInfo> aspects = new ArrayList<>();
        List<SeasarDiconInterceptorInfo> interceptors = new ArrayList<>();
        List<SeasarDiconNamespaceInfo> namespaces = new ArrayList<>();
        List<JavaAnalysisDiagnostic> diagnostics = new ArrayList<>();

        for (Path diconFile : diconFiles) {
            try {
                Document document = documentBuilder().parse(diconFile.toFile());
                parseDicon(
                        sourceRoot,
                        diconFile,
                        document.getDocumentElement(),
                        components,
                        includes,
                        properties,
                        aspects,
                        interceptors,
                        namespaces);
            } catch (IOException | ParserConfigurationException | SAXException exception) {
                diagnostics.add(new JavaAnalysisDiagnostic(
                        "SEASAR_DICON_PARSE_FAILED",
                        diconFile + ": " + exception.getMessage()));
            }
        }

        return new SeasarDiconAnalysisResult(
                components,
                includes,
                properties,
                aspects,
                interceptors,
                namespaces,
                diagnostics);
    }

    private static void parseDicon(
            Path sourceRoot,
            Path diconFile,
            Element root,
            List<SeasarDiconComponentInfo> components,
            List<SeasarDiconIncludeInfo> includes,
            List<SeasarDiconPropertyInfo> properties,
            List<SeasarDiconAspectInfo> aspects,
            List<SeasarDiconInterceptorInfo> interceptors,
            List<SeasarDiconNamespaceInfo> namespaces) {
        if (root == null) {
            return;
        }
        String diconPath = relativePath(sourceRoot, diconFile);
        SourceLocation fileLocation = new SourceLocation(diconPath, 1, 1);
        String namespace = attr(root, "namespace");
        if (!namespace.isBlank()) {
            namespaces.add(new SeasarDiconNamespaceInfo(diconPath, namespace, fileLocation));
        }

        for (Element include : descendants(root, "include")) {
            String path = attr(include, "path");
            if (!path.isBlank()) {
                includes.add(new SeasarDiconIncludeInfo(diconPath, path, fileLocation));
            }
        }

        for (Element component : descendants(root, "component")) {
            String name = attr(component, "name");
            String className = attr(component, "class");
            String interfaceName = attr(component, "interface");
            String autoBinding = attr(component, "autoBinding");
            boolean namingCandidate = className.isBlank() && interfaceName.isBlank() && !name.isBlank();
            String componentKey = componentKey(name, className, interfaceName);
            SeasarDiconComponentInfo componentInfo = new SeasarDiconComponentInfo(
                    diconPath,
                    namespace,
                    name,
                    className,
                    interfaceName,
                    autoBinding,
                    namingCandidate,
                    fileLocation);
            components.add(componentInfo);
            if (isInterceptorComponent(name, className)) {
                interceptors.add(new SeasarDiconInterceptorInfo(diconPath, name, className, fileLocation));
            }
            for (Element property : directChildren(component, "property")) {
                String propertyName = attr(property, "name");
                if (!propertyName.isBlank()) {
                    properties.add(new SeasarDiconPropertyInfo(
                            diconPath,
                            componentKey,
                            propertyName,
                            text(property),
                            fileLocation));
                }
            }
            for (Element aspect : directChildren(component, "aspect")) {
                String interceptor = attr(aspect, "interceptor");
                if (interceptor.isBlank()) {
                    interceptor = text(aspect);
                }
                    aspects.add(new SeasarDiconAspectInfo(
                            diconPath,
                            componentKey,
                            attr(aspect, "pointcut"),
                            interceptor,
                            fileLocation));
            }
        }
    }

    private static boolean isInterceptorComponent(String name, String className) {
        String normalizedName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String normalizedClass = className == null ? "" : className.toLowerCase(Locale.ROOT);
        return normalizedName.contains("interceptor") || normalizedClass.contains("interceptor");
    }

    private static String componentKey(String name, String className, String interfaceName) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (className != null && !className.isBlank()) {
            return className;
        }
        if (interfaceName != null && !interfaceName.isBlank()) {
            return interfaceName;
        }
        return "";
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
            // XML parser implementations vary; secure-processing plus entity resolver keeps parsing local.
        }
    }

    private static List<Element> descendants(Element parent, String tagName) {
        List<Element> result = new ArrayList<>();
        collectDescendants(parent, tagName, result);
        return result;
    }

    private static void collectDescendants(Element parent, String tagName, List<Element> result) {
        if (parent == null) {
            return;
        }
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                if (element.getTagName().equals(tagName)) {
                    result.add(element);
                }
                collectDescendants(element, tagName, result);
            }
        }
    }

    private static List<Element> directChildren(Element parent, String tagName) {
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

    private static String relativePath(Path sourceRoot, Path file) {
        return sourceRoot.toAbsolutePath().normalize()
                .relativize(file.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }
}
