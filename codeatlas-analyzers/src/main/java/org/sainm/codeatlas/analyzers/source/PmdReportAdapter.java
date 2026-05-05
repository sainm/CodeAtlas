package org.sainm.codeatlas.analyzers.source;

import java.nio.file.Path;

/**
 * Parses PMD (Print Media Description / Page Media Definition) report definition files.
 *
 * <p>PMD files describe print media layouts with field definitions and data bindings.
 * This adapter supports {@code .pmd} files and XML-based variants.
 */
public final class PmdReportAdapter implements ReportPluginAdapter {

    private final XmlReportAdapter delegate;

    public PmdReportAdapter() {
        this.delegate = new XmlReportAdapter("PMD", "PMD_PARSE_ERROR", "PMD_NO_FIELDS");
    }

    @Override
    public String formatName() {
        return "PMD";
    }

    @Override
    public boolean supports(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".pmd") || name.endsWith(".pmd.xml");
    }

    @Override
    public ReportDefinitionParseResult parse(Path file) {
        return delegate.parse(file);
    }
}
