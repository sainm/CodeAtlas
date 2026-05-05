package org.sainm.codeatlas.analyzers.source;

import java.nio.file.Path;

/**
 * Parses PSF (Print Service Format / Page Specification Format) report definition files.
 *
 * <p>PSF files define print form layouts with field mappings to data sources.
 * This adapter supports {@code .psf} files and XML-based PSF variants.
 */
public final class PsfReportAdapter implements ReportPluginAdapter {

    private final XmlReportAdapter delegate;

    public PsfReportAdapter() {
        this.delegate = new XmlReportAdapter("PSF", "PSF_PARSE_ERROR", "PSF_NO_FIELDS");
    }

    @Override
    public String formatName() {
        return "PSF";
    }

    @Override
    public boolean supports(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".psf") || name.endsWith(".psf.xml");
    }

    @Override
    public ReportDefinitionParseResult parse(Path file) {
        return delegate.parse(file);
    }
}
