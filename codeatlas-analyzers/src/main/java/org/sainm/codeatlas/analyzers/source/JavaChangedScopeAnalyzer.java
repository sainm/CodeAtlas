package org.sainm.codeatlas.analyzers.source;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.sainm.codeatlas.analyzers.workspace.FileInventoryEntry;
import org.sainm.codeatlas.analyzers.workspace.GitDiffSummary;
import org.sainm.codeatlas.analyzers.workspace.WorkspaceInventory;

public final class JavaChangedScopeAnalyzer {
    private final JavaSourceAnalyzer analyzer;

    public JavaChangedScopeAnalyzer(JavaSourceAnalyzer analyzer) {
        this.analyzer = analyzer == null ? JavaSourceAnalyzer.defaults() : analyzer;
    }

    public static JavaChangedScopeAnalyzer defaults() {
        return new JavaChangedScopeAnalyzer(JavaSourceAnalyzer.defaults());
    }

    public JavaChangedScopeAnalysis analyze(
            Path sourceRoot,
            WorkspaceInventory inventory,
            GitDiffSummary diff,
            JavaSourceIncrementalCache cache) {
        if (sourceRoot == null) {
            throw new IllegalArgumentException("sourceRoot is required");
        }
        if (inventory == null) {
            throw new IllegalArgumentException("inventory is required");
        }
        GitDiffSummary normalizedDiff = diff == null ? new GitDiffSummary(List.of()) : diff;
        JavaSourceIncrementalCache effectiveCache = cache == null ? new JavaSourceIncrementalCache() : cache;
        List<JavaSourceAnalysisResult> partialResults = new ArrayList<>();
        List<String> analyzedPaths = new ArrayList<>();
        int cacheHits = 0;
        for (FileInventoryEntry entry : inventory.entries()) {
            if (!isChangedJavaFile(entry.relativePath(), normalizedDiff)) {
                continue;
            }
            Optional<JavaSourceAnalysisResult> cached = effectiveCache.getIfHashMatches(entry.relativePath(), entry.sha256());
            if (cached.isPresent()) {
                partialResults.add(cached.orElseThrow());
                cacheHits++;
                analyzedPaths.add(entry.relativePath());
                continue;
            }
            JavaSourceAnalysisResult result = analyzer.analyze(sourceRoot, List.of(sourceRoot.resolve(entry.relativePath())));
            effectiveCache.put(entry.relativePath(), entry.sha256(), result);
            partialResults.add(result);
            analyzedPaths.add(entry.relativePath());
        }
        return new JavaChangedScopeAnalysis(merge(partialResults), analyzedPaths.stream().sorted().toList(), cacheHits);
    }

    private static boolean isChangedJavaFile(String relativePath, GitDiffSummary diff) {
        return relativePath.endsWith(".java") && diff.changedPaths().contains(relativePath);
    }

    private static JavaSourceAnalysisResult merge(List<JavaSourceAnalysisResult> results) {
        List<JavaClassInfo> classes = new ArrayList<>();
        List<JavaMethodInfo> methods = new ArrayList<>();
        List<JavaFieldInfo> fields = new ArrayList<>();
        List<JavaInvocationInfo> invocations = new ArrayList<>();
        List<JavaAnalysisDiagnostic> diagnostics = new ArrayList<>();
        boolean fallback = false;
        for (JavaSourceAnalysisResult result : results) {
            classes.addAll(result.classes());
            methods.addAll(result.methods());
            fields.addAll(result.fields());
            invocations.addAll(result.directInvocations());
            diagnostics.addAll(result.diagnostics());
            fallback |= result.noClasspathFallbackUsed();
        }
        return new JavaSourceAnalysisResult(fallback, classes, methods, fields, invocations, diagnostics);
    }
}
