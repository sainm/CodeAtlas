package org.sainm.codeatlas.analyzers.source;

import java.nio.file.Path;

/**
 * Parses report layout XML files that define form/report visual layout
 * with field placements and data bindings.
 */
public final class LayoutXmlReportAdapter implements ReportPluginAdapter {

    private final XmlReportAdapter delegate;

    public LayoutXmlReportAdapter() {
        this.delegate = new XmlReportAdapter(
                "Layout XML", "LAYOUT_XML_PARSE_ERROR", "LAYOUT_XML_NO_FIELDS");
    }

    @Override
    public String formatName() {
        return "Layout XML";
    }

    @Override
    public boolean supports(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.contains("layout") && name.endsWith(".xml");
    }

    @Override
    public ReportDefinitionParseResult parse(Path file) {
        return delegate.parse(file);
    }
}
