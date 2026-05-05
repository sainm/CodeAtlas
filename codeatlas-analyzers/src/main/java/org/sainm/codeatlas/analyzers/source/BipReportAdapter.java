package org.sainm.codeatlas.analyzers.source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Parses Oracle BI Publisher (BIP) report definition files
 * ({@code .xdo}, {@code .xdm}, {@code .xpt}).
 */
public final class BipReportAdapter implements ReportPluginAdapter {

    private final DocumentBuilder documentBuilder;

    public BipReportAdapter() {
        this.documentBuilder = XmlReportAdapter.createSecureDocumentBuilder();
    }

    @Override
    public String formatName() {
        return "Oracle BI Publisher";
    }

    @Override
    public boolean supports(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".xdo") || name.endsWith(".xdm") || name.endsWith(".xpt");
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
            fields = extractFields(root, relativePath);
            parameters = extractParameters(root, relativePath);

            if (fields.isEmpty() && parameters.isEmpty()) {
                diagnostics.add(new JavaAnalysisDiagnostic(
                        "BIP_NO_FIELDS",
                        "No fields or parameters extracted from " + file.getFileName()));
            }
        } catch (Exception e) {
            diagnostics.add(new JavaAnalysisDiagnostic(
                    "BIP_PARSE_ERROR",
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
        String name = XmlReportAdapter.attr(root, "name", "id", "reportName");
        if (name.isBlank()) {
            Element dataQuery = XmlReportAdapter.firstChild(
                    root, "dataQuery", "dataTemplate", "dataModel", "report");
            if (dataQuery != null) {
                name = XmlReportAdapter.attr(dataQuery, "name", "id");
            }
        }
        return name.isBlank() ? XmlReportAdapter.fileNameWithoutExtension(file) : name;
    }

    private List<ReportFieldDefinition> extractFields(Element root, String relativePath) {
        Set<String> seen = new LinkedHashSet<>();
        List<ReportFieldDefinition> result = new ArrayList<>();
        // Elements directly under <dataTemplate> or inside <group> containers.
        // getElementsByTagName finds all recursively; deduplicate by field name.
        NodeList elementNodes = root.getElementsByTagName("element");
        for (int i = 0; i < elementNodes.getLength(); i++) {
            Element el = (Element) elementNodes.item(i);
            String name = XmlReportAdapter.attr(el, "name", "id", "value");
            if (!name.isBlank() && seen.add(name)) {
                result.add(new ReportFieldDefinition(
                        name,
                        XmlReportAdapter.attr(el, "dataType", "type"),
                        "", "",
                        XmlReportAdapter.locationOf(relativePath, el)));
            }
        }
        return result;
    }

    private List<ReportParameterDefinition> extractParameters(Element root, String relativePath) {
        List<ReportParameterDefinition> result = new ArrayList<>();
        NodeList paramNodes = root.getElementsByTagName("parameter");
        for (int i = 0; i < paramNodes.getLength(); i++) {
            Element el = (Element) paramNodes.item(i);
            result.add(new ReportParameterDefinition(
                    XmlReportAdapter.attr(el, "name", "id"),
                    XmlReportAdapter.attr(el, "dataType", "type"),
                    XmlReportAdapter.attr(el, "defaultValue", "default"),
                    XmlReportAdapter.locationOf(relativePath, el)));
        }
        NodeList lexNodes = root.getElementsByTagName("lexical");
        for (int i = 0; i < lexNodes.getLength(); i++) {
            Element el = (Element) lexNodes.item(i);
            result.add(new ReportParameterDefinition(
                    XmlReportAdapter.attr(el, "name", "key", "ref"),
                    XmlReportAdapter.attr(el, "type"),
                    "",
                    XmlReportAdapter.locationOf(relativePath, el)));
        }
        return result;
    }
}
