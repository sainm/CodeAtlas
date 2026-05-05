package org.sainm.codeatlas.analyzers.source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Parses WingArc1st SVF (Super Visual Formade) report definition files.
 */
public final class WingArcSvfXmlAdapter implements ReportPluginAdapter {

    private final DocumentBuilder documentBuilder;

    public WingArcSvfXmlAdapter() {
        this.documentBuilder = XmlReportAdapter.createSecureDocumentBuilder();
    }

    @Override
    public String formatName() {
        return "WingArc1st SVF XML";
    }

    @Override
    public boolean supports(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".svf")) {
            return true;
        }
        if (name.endsWith(".xml")) {
            return probeSvfXml(file);
        }
        return false;
    }

    private boolean probeSvfXml(Path file) {
        try {
            Document doc = parseXml(file);
            Element root = doc.getDocumentElement();
            String rootName = root.getTagName().toLowerCase();
            return rootName.contains("svf") || rootName.contains("form")
                    || rootName.contains("report");
        } catch (Exception e) {
            return false;
        }
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
            String relativePath = XmlReportAdapter.relativePathOf(file);

            reportName = extractReportName(root, file);
            fields = extractSvfFields(root, relativePath);
            parameters = extractSvfParameters(root, relativePath);

            if (fields.isEmpty() && parameters.isEmpty()) {
                diagnostics.add(new JavaAnalysisDiagnostic(
                        "SVF_NO_FIELDS",
                        "No fields or parameters extracted from " + file.getFileName()));
            }
        } catch (Exception e) {
            diagnostics.add(new JavaAnalysisDiagnostic(
                    "SVF_PARSE_ERROR",
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
        String name = XmlReportAdapter.attr(root, "name", "form-name", "id");
        if (name.isBlank()) {
            Element titleEl = XmlReportAdapter.firstChild(root, "title", "form-name", "name");
            if (titleEl != null) {
                name = titleEl.getTextContent().trim();
            }
        }
        if (name.isBlank()) {
            name = XmlReportAdapter.fileNameWithoutExtension(file);
        }
        return name;
    }

    private List<ReportFieldDefinition> extractSvfFields(Element root, String relativePath) {
        List<ReportFieldDefinition> result = new ArrayList<>();
        NodeList fieldNodes = root.getElementsByTagName("field");
        addFieldsFromNodeList(fieldNodes, relativePath, result);
        NodeList itemNodes = root.getElementsByTagName("item");
        addFieldsFromNodeList(itemNodes, relativePath, result);
        return result;
    }

    private void addFieldsFromNodeList(NodeList nodes, String relativePath,
                                        List<ReportFieldDefinition> result) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            result.add(new ReportFieldDefinition(
                    XmlReportAdapter.attr(el, "name", "id", "key"),
                    XmlReportAdapter.attr(el, "type", "data-type", "datatype", "field-type"),
                    XmlReportAdapter.attr(el, "table", "source-table", "table-name"),
                    XmlReportAdapter.attr(el, "column", "source-column", "field-name", "bind"),
                    locationOf(relativePath, el)));
        }
    }

    private List<ReportParameterDefinition> extractSvfParameters(Element root, String relativePath) {
        List<ReportParameterDefinition> result = new ArrayList<>();
        NodeList paramNodes = root.getElementsByTagName("parameter");
        for (int i = 0; i < paramNodes.getLength(); i++) {
            Element el = (Element) paramNodes.item(i);
            result.add(new ReportParameterDefinition(
                    XmlReportAdapter.attr(el, "name", "id"),
                    XmlReportAdapter.attr(el, "type", "data-type"),
                    XmlReportAdapter.attr(el, "default", "default-value", "initial-value"),
                    locationOf(relativePath, el)));
        }
        NodeList queryParamsNodes = root.getElementsByTagName("query-params");
        for (int i = 0; i < queryParamsNodes.getLength(); i++) {
            Element qp = (Element) queryParamsNodes.item(i);
            NodeList params = qp.getElementsByTagName("param");
            for (int j = 0; j < params.getLength(); j++) {
                Element el = (Element) params.item(j);
                result.add(new ReportParameterDefinition(
                        XmlReportAdapter.attr(el, "name", "id"),
                        XmlReportAdapter.attr(el, "type", "data-type"),
                        XmlReportAdapter.attr(el, "default", "default-value"),
                        locationOf(relativePath, el)));
            }
        }
        return result;
    }

    private static SourceLocation locationOf(String relativePath, Element el) {
        Object userData = el.getUserData("line-number");
        int line = userData instanceof Integer ? (Integer) userData : 1;
        return new SourceLocation(relativePath, line, 0);
    }
}
