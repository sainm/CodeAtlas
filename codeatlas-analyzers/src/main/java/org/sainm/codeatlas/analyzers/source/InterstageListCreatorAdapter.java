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
import org.w3c.dom.NodeList;

/**
 * Parses Fujitsu Interstage List Creator report definition files.
 */
public final class InterstageListCreatorAdapter implements ReportPluginAdapter {

    private final DocumentBuilder documentBuilder;

    public InterstageListCreatorAdapter() {
        this.documentBuilder = XmlReportAdapter.createSecureDocumentBuilder();
    }

    @Override
    public String formatName() {
        return "Interstage List Creator";
    }

    @Override
    public boolean supports(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".iix")) {
            return true;
        }
        if (name.endsWith(".xml")) {
            return probeInterstageXml(file);
        }
        return false;
    }

    private boolean probeInterstageXml(Path file) {
        try {
            Document doc = parseXml(file);
            Element root = doc.getDocumentElement();
            String rootName = root.getTagName().toLowerCase();
            return rootName.contains("form") || rootName.contains("report")
                    || rootName.contains("list-creator") || rootName.contains("interstage");
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
            fields = extractFields(root, relativePath);
            parameters = extractParameters(root, relativePath);

            if (fields.isEmpty() && parameters.isEmpty()) {
                diagnostics.add(new JavaAnalysisDiagnostic(
                        "INTERSTAGE_NO_FIELDS",
                        "No fields or parameters extracted from " + file.getFileName()));
            }
        } catch (Exception e) {
            diagnostics.add(new JavaAnalysisDiagnostic(
                    "INTERSTAGE_PARSE_ERROR",
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
        String name = XmlReportAdapter.getAttr(root, "name");
        if (name == null || name.isBlank()) {
            name = XmlReportAdapter.getAttr(root, "id");
        }
        if (name == null || name.isBlank()) {
            Element titleEl = XmlReportAdapter.firstChild(root, "title");
            if (titleEl != null) {
                name = titleEl.getTextContent().trim();
            }
        }
        if (name == null || name.isBlank()) {
            name = XmlReportAdapter.fileNameWithoutExtension(file);
        }
        return name;
    }

    private List<ReportFieldDefinition> extractFields(Element root, String relativePath) {
        List<ReportFieldDefinition> result = new ArrayList<>();
        NodeList fieldNodes = root.getElementsByTagName("field");
        for (int i = 0; i < fieldNodes.getLength(); i++) {
            Element el = (Element) fieldNodes.item(i);
            result.add(new ReportFieldDefinition(
                    XmlReportAdapter.attr(el, "name", "id"),
                    XmlReportAdapter.attr(el, "type", "data-type", "datatype"),
                    XmlReportAdapter.attr(el, "table", "source-table"),
                    XmlReportAdapter.attr(el, "column", "source-column"),
                    locationOf(relativePath, el)));
        }
        NodeList itemNodes = root.getElementsByTagName("item");
        for (int i = 0; i < itemNodes.getLength(); i++) {
            Element el = (Element) itemNodes.item(i);
            result.add(new ReportFieldDefinition(
                    XmlReportAdapter.attr(el, "name", "id", "key"),
                    XmlReportAdapter.attr(el, "type", "data-type"),
                    XmlReportAdapter.attr(el, "table", "source-table"),
                    XmlReportAdapter.attr(el, "column", "source-column", "bind"),
                    locationOf(relativePath, el)));
        }
        NodeList colNodes = root.getElementsByTagName("column");
        for (int i = 0; i < colNodes.getLength(); i++) {
            Element el = (Element) colNodes.item(i);
            result.add(new ReportFieldDefinition(
                    XmlReportAdapter.attr(el, "name", "id", "label"),
                    XmlReportAdapter.attr(el, "type", "data-type"),
                    XmlReportAdapter.attr(el, "table", "source-table"),
                    XmlReportAdapter.attr(el, "name", "source-column", "column-name"),
                    locationOf(relativePath, el)));
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
                    XmlReportAdapter.attr(el, "type", "data-type"),
                    XmlReportAdapter.attr(el, "default", "default-value"),
                    locationOf(relativePath, el)));
        }
        return result;
    }

    private static SourceLocation locationOf(String relativePath, Element el) {
        Object userData = el.getUserData("line-number");
        int line = userData instanceof Integer ? (Integer) userData : 1;
        return new SourceLocation(relativePath, line, 0);
    }
}
