package org.sainm.codeatlas.analyzers.source;

import java.nio.file.Path;

/**
 * Plugin adapter interface for parsing report definition formats (PSF, PMD, BIP,
 * SVF XML, layout XML, field definition XML).
 *
 * <p>Each adapter handles one format and produces a unified
 * {@link ReportDefinitionParseResult}.
 */
public interface ReportPluginAdapter {
    String formatName();

    boolean supports(Path file);

    ReportDefinitionParseResult parse(Path file);
}
