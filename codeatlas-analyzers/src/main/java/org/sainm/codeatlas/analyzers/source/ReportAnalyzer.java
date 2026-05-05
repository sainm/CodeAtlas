package org.sainm.codeatlas.analyzers.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Discovers report definition files and dispatches them to registered
 * {@link ReportPluginAdapter} implementations.
 *
 * <p>A single {@code ReportAnalyzer} instance holds all known adapters.
 * Use {@link #defaults()} for the built-in adapter set, or
 * {@link #builder()} for custom configurations.
 */
public final class ReportAnalyzer {

    private final List<ReportPluginAdapter> adapters;

    private ReportAnalyzer(List<ReportPluginAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    public static ReportAnalyzer defaults() {
        return new ReportAnalyzer(List.of(
                new InterstageListCreatorAdapter(),
                new WingArcSvfXmlAdapter(),
                new PsfReportAdapter(),
                new PmdReportAdapter(),
                new BipReportAdapter(),
                new LayoutXmlReportAdapter(),
                new FieldDefinitionXmlReportAdapter()));
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ReportPluginAdapter> adapters() {
        return adapters;
    }

    /**
     * Scans a directory tree for report definition files, dispatches each
     * to a matching adapter, and returns all parse results.
     */
    private static final Set<String> IGNORED_DIR_NAMES = Set.of(
            ".git", "node_modules", "build", "target", ".gradle", "__pycache__",
            ".idea", ".settings", "bin", "dist");

    private static final int DEFAULT_MAX_FILES = 10_000;

    private static final int DEFAULT_MAX_DEPTH = 8;

    public ReportAnalysisResult analyze(Path rootDir) {
        return analyze(rootDir, DEFAULT_MAX_FILES);
    }

    public ReportAnalysisResult analyze(Path rootDir, int maxFiles) {
        if (rootDir == null || !Files.isDirectory(rootDir)) {
            return new ReportAnalysisResult(List.of());
        }
        List<ReportDefinitionParseResult> results = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(rootDir, DEFAULT_MAX_DEPTH)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        for (int i = 0; i < p.getNameCount(); i++) {
                            if (IGNORED_DIR_NAMES.contains(p.getName(i).toString())) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .limit(maxFiles)
                    .forEach(file -> {
                        for (ReportPluginAdapter adapter : adapters) {
                            if (adapter.supports(file)) {
                                results.add(adapter.parse(file));
                                break;
                            }
                        }
                    });
        } catch (IOException e) {
            results.add(new ReportDefinitionParseResult(
                    "", List.of(), List.of(),
                    List.of(new JavaAnalysisDiagnostic(
                            "REPORT_SCAN_ERROR",
                            "Failed to scan directory " + rootDir + ": " + e.getMessage()))));
        }
        return new ReportAnalysisResult(results);
    }

    /**
     * Attempts to parse a single file using the first matching adapter.
     */
    public ReportDefinitionParseResult parseFile(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return new ReportDefinitionParseResult(
                    "", List.of(), List.of(),
                    List.of(new JavaAnalysisDiagnostic(
                            "REPORT_INVALID_FILE",
                            "File is null or not a regular file")));
        }
        for (ReportPluginAdapter adapter : adapters) {
            if (adapter.supports(file)) {
                return adapter.parse(file);
            }
        }
        return new ReportDefinitionParseResult(
                "", List.of(), List.of(),
                List.of(new JavaAnalysisDiagnostic(
                        "REPORT_UNSUPPORTED_FORMAT",
                        "No adapter supports file: " + file.getFileName())));
    }

    public static final class Builder {
        private final List<ReportPluginAdapter> adapters = new ArrayList<>();

        public Builder add(ReportPluginAdapter adapter) {
            adapters.add(adapter);
            return this;
        }

        public ReportAnalyzer build() {
            return new ReportAnalyzer(adapters);
        }
    }
}
