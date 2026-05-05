package org.sainm.codeatlas.analyzers.source;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public final class MyBatisXmlAnalyzer {
    private static final Set<String> DYNAMIC_SQL_TAGS = Set.of(
            "if",
            "choose",
            "when",
            "otherwise",
            "trim",
            "where",
            "set",
            "foreach",
            "bind",
            "script");

    private MyBatisXmlAnalyzer() {
    }

    public static MyBatisXmlAnalyzer defaults() {
        return new MyBatisXmlAnalyzer();
    }

    public MyBatisXmlAnalysisResult analyze(Path sourceRoot, List<Path> mapperFiles) {
        if (sourceRoot == null) {
            throw new IllegalArgumentException("sourceRoot is required");
        }
        if (mapperFiles == null || mapperFiles.isEmpty()) {
            return new MyBatisXmlAnalysisResult(List.of(), List.of(), List.of());
        }
        List<MyBatisXmlMapperInfo> mappers = new ArrayList<>();
        List<MyBatisXmlStatementInfo> statements = new ArrayList<>();
        List<JavaAnalysisDiagnostic> diagnostics = new ArrayList<>();
        List<ParsedMapperFile> parsedFiles = new ArrayList<>();
        for (Path mapperFile : mapperFiles) {
            try {
                Document document = documentBuilder().parse(mapperFile.toFile());
                parsedFiles.add(new ParsedMapperFile(mapperFile, document.getDocumentElement()));
            } catch (IOException | ParserConfigurationException | SAXException exception) {
                diagnostics.add(new JavaAnalysisDiagnostic(
                        "MYBATIS_XML_PARSE_FAILED",
                        mapperFile + ": " + exception.getMessage()));
            }
        }
        Map<String, Element> sqlFragments = sqlFragments(parsedFiles);
        for (ParsedMapperFile parsedFile : parsedFiles) {
            parseMapper(sourceRoot, parsedFile.mapperFile(), parsedFile.root(), sqlFragments, mappers, statements);
        }
        return new MyBatisXmlAnalysisResult(mappers, statements, diagnostics);
    }

    private static void parseMapper(
            Path sourceRoot,
            Path mapperFile,
            Element root,
            Map<String, Element> sqlFragments,
            List<MyBatisXmlMapperInfo> mappers,
            List<MyBatisXmlStatementInfo> statements) {
        if (root == null || !root.getTagName().equals("mapper")) {
            return;
        }
        String path = relativePath(sourceRoot, mapperFile);
        SourceLocation location = new SourceLocation(path, 1, 1);
        String namespace = attr(root, "namespace");
        if (namespace.isBlank()) {
            return;
        }
        mappers.add(new MyBatisXmlMapperInfo(path, namespace, location));
        for (Element child : directChildren(root)) {
            String tagName = child.getTagName();
            MyBatisStatementKind kind = statementKind(tagName);
            String id = attr(child, "id");
            if (kind != null && !id.isBlank()) {
                SourceLocation statementLocation = statementLocation(sourceRoot, mapperFile, tagName, id, location);
                statements.add(new MyBatisXmlStatementInfo(
                        path,
                        namespace,
                        id,
                        kind,
                        sqlText(child, namespace, sqlFragments),
                        containsDynamicSql(child, namespace, sqlFragments, new HashSet<>()),
                        statementLocation));
            }
        }
    }

    private static MyBatisStatementKind statementKind(String tagName) {
        return switch (tagName.toLowerCase(Locale.ROOT)) {
            case "select" -> MyBatisStatementKind.SELECT;
            case "insert" -> MyBatisStatementKind.INSERT;
            case "update" -> MyBatisStatementKind.UPDATE;
            case "delete" -> MyBatisStatementKind.DELETE;
            default -> null;
        };
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

    private static List<Element> directChildren(Element parent) {
        if (parent == null) {
            return List.of();
        }
        List<Element> result = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                result.add(element);
            }
        }
        return result;
    }

    private static String attr(Element element, String name) {
        return element == null ? "" : element.getAttribute(name).trim();
    }

    private static Map<String, Element> sqlFragments(List<ParsedMapperFile> parsedFiles) {
        Map<String, Element> fragments = new HashMap<>();
        for (ParsedMapperFile parsedFile : parsedFiles) {
            Element root = parsedFile.root();
            if (root == null || !root.getTagName().equals("mapper")) {
                continue;
            }
            String namespace = attr(root, "namespace");
            if (namespace.isBlank()) {
                continue;
            }
            for (Element child : directChildren(root)) {
                if (!child.getTagName().equals("sql")) {
                    continue;
                }
                String id = attr(child, "id");
                if (id.isBlank()) {
                    continue;
                }
                fragments.put(namespace + "." + id, child);
            }
        }
        return fragments;
    }

    private static String sqlText(Element element, String namespace, Map<String, Element> sqlFragments) {
        return normalizedText(element, namespace, sqlFragments, new HashSet<>());
    }

    private static String normalizedText(
            Node node,
            String namespace,
            Map<String, Element> sqlFragments,
            Set<String> expandingRefIds) {
        return rawText(node, namespace, sqlFragments, expandingRefIds).replaceAll("\\s+", " ").trim();
    }

    private static String rawText(
            Node node,
            String namespace,
            Map<String, Element> sqlFragments,
            Set<String> expandingRefIds) {
        if (node == null) {
            return "";
        }
        if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
            return node.getTextContent();
        }
        if (node instanceof Element element && element.getTagName().equals("include")) {
            String refid = attr(element, "refid");
            String key = fragmentKey(namespace, refid);
            Element fragment = sqlFragments.get(key);
            if (fragment == null || !expandingRefIds.add(key)) {
                return "";
            }
            try {
                return rawChildrenText(fragment, namespace, sqlFragments, expandingRefIds);
            } finally {
                expandingRefIds.remove(key);
            }
        }
        return rawChildrenText(node, namespace, sqlFragments, expandingRefIds);
    }

    private static boolean containsDynamicSql(
            Node node,
            String namespace,
            Map<String, Element> sqlFragments,
            Set<String> expandingRefIds) {
        if (node == null) {
            return false;
        }
        if (node instanceof Element element) {
            String tagName = element.getTagName().toLowerCase(Locale.ROOT);
            if (DYNAMIC_SQL_TAGS.contains(tagName)) {
                return true;
            }
            if (tagName.equals("include")) {
                String key = fragmentKey(namespace, attr(element, "refid"));
                Element fragment = sqlFragments.get(key);
                if (fragment == null || !expandingRefIds.add(key)) {
                    return false;
                }
                try {
                    return containsDynamicSql(fragment, namespace, sqlFragments, expandingRefIds);
                } finally {
                    expandingRefIds.remove(key);
                }
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (containsDynamicSql(child, namespace, sqlFragments, expandingRefIds)) {
                return true;
            }
        }
        return false;
    }

    private static String rawChildrenText(
            Node node,
            String namespace,
            Map<String, Element> sqlFragments,
            Set<String> expandingRefIds) {
        StringBuilder result = new StringBuilder();
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            result.append(' ').append(rawText(child, namespace, sqlFragments, expandingRefIds));
        }
        return result.toString();
    }

    private static String fragmentKey(String namespace, String refid) {
        if (refid.contains(".")) {
            return refid;
        }
        return namespace + "." + refid;
    }

    private static String relativePath(Path sourceRoot, Path file) {
        return sourceRoot.toAbsolutePath().normalize()
                .relativize(file.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    private static SourceLocation statementLocation(
            Path sourceRoot,
            Path mapperFile,
            String tagName,
            String id,
            SourceLocation fallback) {
        try {
            List<String> lines = Files.readAllLines(mapperFile);
            String path = relativePath(sourceRoot, mapperFile);
            String tagPrefix = "<" + tagName;
            String doubleQuotedId = "id=\"" + id + "\"";
            String singleQuotedId = "id='" + id + "'";
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                String line = lines.get(lineIndex);
                int tagColumn = line.indexOf(tagPrefix);
                if (tagColumn >= 0) {
                    StringBuilder startTag = new StringBuilder(line.substring(tagColumn));
                    for (int tagLineIndex = lineIndex + 1;
                            startTag.indexOf(">") < 0 && tagLineIndex < lines.size();
                            tagLineIndex++) {
                        startTag.append('\n').append(lines.get(tagLineIndex));
                    }
                    if (startTag.indexOf(doubleQuotedId) >= 0 || startTag.indexOf(singleQuotedId) >= 0) {
                        return new SourceLocation(path, lineIndex + 1, tagColumn + 1);
                    }
                }
            }
        } catch (IOException ignored) {
            // Fall back to the mapper location when source text is unavailable.
        }
        return fallback;
    }

    private record ParsedMapperFile(Path mapperFile, Element root) {
    }
}
