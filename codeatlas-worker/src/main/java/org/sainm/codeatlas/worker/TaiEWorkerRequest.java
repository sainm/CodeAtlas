package org.sainm.codeatlas.worker;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public record TaiEWorkerRequest(
    Path javaExecutable,
    Path taiEAllJar,
    Path workingDirectory,
    Path outputDirectory,
    List<Path> classPaths,
    List<Path> applicationClassPaths,
    String mainClass,
    List<String> inputClasses,
    List<String> analyses,
    int javaVersion,
    boolean prependJvmClassPath,
    boolean allowPhantomReferences,
    String maxHeap,
    Duration timeout
) {
    public TaiEWorkerRequest {
        classPaths = classPaths == null ? List.of() : List.copyOf(classPaths);
        applicationClassPaths = applicationClassPaths == null ? List.of() : List.copyOf(applicationClassPaths);
        inputClasses = inputClasses == null ? List.of() : List.copyOf(inputClasses);
        analyses = analyses == null ? List.of() : List.copyOf(analyses);
        mainClass = mainClass == null ? "" : mainClass.trim();
        maxHeap = maxHeap == null || maxHeap.isBlank() ? "4g" : maxHeap.trim();
        timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? Duration.ofMinutes(10) : timeout;
    }
}
