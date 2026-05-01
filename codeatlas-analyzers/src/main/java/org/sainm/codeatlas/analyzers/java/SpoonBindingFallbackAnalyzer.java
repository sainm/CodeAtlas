package org.sainm.codeatlas.analyzers.java;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import java.nio.file.Path;
import java.util.List;

public final class SpoonBindingFallbackAnalyzer {
    public SpoonBindingFallbackResult analyze(AnalyzerScope scope, String projectKey, String sourceRootKey, List<Path> sourceFiles) {
        try {
            JavaAnalysisResult result = new SpoonJavaAnalyzer(25, false).analyze(scope, projectKey, sourceRootKey, sourceFiles);
            return new SpoonBindingFallbackResult(result, SpoonBindingFallbackMode.BINDING, null);
        } catch (Throwable exception) {
            if (exception instanceof VirtualMachineError) {
                throw exception;
            }
            JavaAnalysisResult fallback = new SpoonJavaAnalyzer(25, true).analyze(scope, projectKey, sourceRootKey, sourceFiles);
            return new SpoonBindingFallbackResult(
                fallback,
                SpoonBindingFallbackMode.NO_CLASSPATH_FALLBACK,
                "binding analysis failed; fell back to Spoon no-classpath mode: " + exception.getClass().getSimpleName()
            );
        }
    }
}
