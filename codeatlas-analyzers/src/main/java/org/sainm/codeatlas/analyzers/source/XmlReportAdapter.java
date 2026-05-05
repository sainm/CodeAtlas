package org.sainm.codeatlas.analyzers.source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Generic XML-based report adapter that extracts report name, fields, and
 * parameters from common XML form/report definition structures.
 *
 * <p>Used as a delegate by format-specific adapters (PSF, PMD, etc.) and directly
 * for layout XML and field definition XML files. Searches for common element
 * patterns: {@code <field>}, {@code <item>}, {@code <column>}, {@code <parameter>}
 * with typical attribute names.
 */
public final class XmlReportAdapter implements ReportPluginAdapter {

    private final String formatLabel;
    private final String parseErrorCode;
    private final String noFieldsCode;
    private final DocumentBuilder documentBuilder;

    public XmlReportAdapter(String formatLabel, String parseErrorCode, String noFieldsCode) {
        this.formatLabel = formatLabel;
        this.parseErrorCode = parseErrorCode;
        this.noFieldsCode = noFieldsCode;
        this.documentBuilder = createSecureDocumentBuilder();
    }

    public static DocumentBuilder createSecureDocumentBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return factory.newDocumentBuilder();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create XML document builder", e);
        }
    }

    @Override
    public String formatName() {
        return formatLabel;
    }

    @Override
    public boolean supports(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".xml");
    }

    @Override
    public ReportDefinitionParseResult parse(Path file) {
        List<JavaAnalysisDiagnostic> diagnostics = new ArrayList<>();
        String reportName = "";
        List<ReportFieldDefinition> fields = new ArrayList<>();
        List<ReportParameterDefinition> parameters = new ArrayList<>();
        try {
            Document doc = parseXml(file);
            Element root = doc.getDocumentElement();
            String relativePath = relativePathOf(file);

            reportName = extractReportName(root, file);
            fields = extractAllFields(root, relativePath);
            parameters = extractAllParameters(root, relativePath);

            if (fields.isEmpty() && parameters.isEmpty()) {
                diagnostics.add(new JavaAnalysisDiagnostic(
                        noFieldsCode,
                        "No fields or parameters extracted from " + file.getFileName()));
            }
        } catch (Exception e) {
            diagnostics.add(new JavaAnalysisDiagnostic(
                    parseErrorCode,
                    "Failed to parse " + file.getFileName() + ": " + e.getMessage()));
        }
        return new ReportDefinitionParseResult(reportName, fields, parameters, diagnostics);
    }

    private Document parseXml(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file)) {
            return documentBuilder.parse(in);
        }
    }

    private String extractReportName(Element root, Path file) {
        String name = attr(root, "name", "id", "report-name", "form-name", "title");
        if (name.isBlank()) {
            for (String tag : new String[]{"title", "report-name", "form-name", "name", "label"}) {
                Element child = firstChild(root, tag);
                if (child != null) {
                    name = child.getTextContent().trim();
                    if (!name.isBlank()) break;
                }
            }
        }
        return name.isBlank() ? fileNameWithoutExtension(file) : name;
    }

    List<ReportFieldDefinition> extractAllFields(Element root, String relativePath) {
        List<ReportFieldDefinition> result = new ArrayList<>();
        String[] fieldTags = {"field", "item", "column", "element", "data-field",
                "report-field", "form-field"};
        for (String tag : fieldTags) {
            NodeList nodes = root.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                if (isNestedInExcludedParent(el)) continue;
                String fieldName = attr(el, "name", "id", "key", "value", "label", "field-name");
                String dataType = attr(el, "type", "data-type", "datatype", "field-type", "dataType");
                String sourceTable = attr(el, "table", "source-table", "table-name", "tableName");
                String sourceColumn = attr(el, "column", "source-column", "column-name", "field-name", "bind");
                SourceLocation location = locationOf(relativePath, el);
                result.add(new ReportFieldDefinition(fieldName, dataType, sourceTable, sourceColumn, location));
            }
        }
        return result;
    }

    List<ReportParameterDefinition> extractAllParameters(Element root, String relativePath) {
        List<ReportParameterDefinition> result = new ArrayList<>();
        String[] paramTags = {"parameter", "param", "query-param", "report-parameter",
                "input-parameter", "bind-parameter"};
        for (String tag : paramTags) {
            NodeList nodes = root.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String paramName = attr(el, "name", "id", "key", "param-name");
                String paramType = attr(el, "type", "data-type", "datatype", "param-type", "dataType");
                String defaultValue = attr(el, "default", "default-value", "defaultValue",
                        "initial-value", "value");
                SourceLocation location = locationOf(relativePath, el);
                result.add(new ReportParameterDefinition(paramName, paramType, defaultValue, location));
            }
        }
        return result;
    }

    /**
     * Avoid double-counting: skip elements inside an already-processed parent of the same tag.
     */
    private static boolean isNestedInExcludedParent(Element el) {
        Node parent = el.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            String parentTag = ((Element) parent).getTagName();
            if (parentTag.equals(el.getTagName())) return true;
            parent = parent.getParentNode();
        }
        return false;
    }

    // -- XML helpers

    static String attr(Element el, String... candidates) {
        for (String c : candidates) {
            String v = getAttr(el, c);
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    static String getAttr(Element el, String attr) {
        if (el.hasAttribute(attr)) return el.getAttribute(attr).trim();
        return null;
    }

    static Element firstChild(Element parent, String... tagNames) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String tag = ((Element) child).getTagName();
                for (String t : tagNames) {
                    if (t.equals(tag)) return (Element) child;
                }
            }
        }
        return null;
    }

    static SourceLocation locationOf(String relativePath, Element el) {
        return new SourceLocation(relativePath, 1, 0);
    }

    static String relativePathOf(Path file) {
        return file.toString().replace('\\', '/');
    }

    static String fileNameWithoutExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
