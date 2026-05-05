package org.sainm.codeatlas.analyzers.source;

import java.nio.file.Path;

/**
 * Parses field definition XML files that map report fields to data source columns
 * and define field data types.
 */
public final class FieldDefinitionXmlReportAdapter implements ReportPluginAdapter {

    private final XmlReportAdapter delegate;

    public FieldDefinitionXmlReportAdapter() {
        this.delegate = new XmlReportAdapter(
                "Field Definition XML", "FIELD_XML_PARSE_ERROR", "FIELD_XML_NO_FIELDS");
    }

    @Override
    public String formatName() {
        return "Field Definition XML";
    }

    @Override
    public boolean supports(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return (name.contains("field") || name.contains("fields")) && name.endsWith(".xml");
    }

    @Override
    public ReportDefinitionParseResult parse(Path file) {
        return delegate.parse(file);
    }
}
