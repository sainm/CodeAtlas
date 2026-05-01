package org.sainm.codeatlas.worker;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TaiEWorkerCommandBuilder {
    public TaiEWorkerLaunchPlan build(TaiEWorkerRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        Path javaExecutable = requirePath(request.javaExecutable(), "javaExecutable");
        Path taiEAllJar = requirePath(request.taiEAllJar(), "taiEAllJar");
        Path workingDirectory = requirePath(request.workingDirectory(), "workingDirectory");
        Path outputDirectory = requirePath(request.outputDirectory(), "outputDirectory");
        if (request.analyses().isEmpty()) {
            throw new IllegalArgumentException("at least one Tai-e analysis is required");
        }

        List<String> command = new ArrayList<>();
        command.add(path(javaExecutable));
        command.add("-Xmx" + request.maxHeap());
        command.add("-jar");
        command.add(path(taiEAllJar));
        addRepeatedPathOption(command, "-cp", request.classPaths());
        addRepeatedPathOption(command, "-acp", request.applicationClassPaths());
        if (!request.mainClass().isBlank()) {
            command.add("-m");
            command.add(request.mainClass());
        }
        if (!request.inputClasses().isEmpty()) {
            command.add("--input-classes=" + String.join(",", request.inputClasses()));
        }
        if (request.prependJvmClassPath()) {
            command.add("-pp");
        } else if (request.javaVersion() > 0) {
            command.add("-java");
            command.add(Integer.toString(request.javaVersion()));
        }
        if (request.allowPhantomReferences()) {
            command.add("-ap");
        }
        command.add("--output-dir");
        command.add(path(outputDirectory));
        for (String analysis : request.analyses()) {
            if (analysis == null || analysis.isBlank()) {
                continue;
            }
            command.add("-a");
            command.add(analysis.trim());
        }
        return new TaiEWorkerLaunchPlan(command, workingDirectory, request.timeout());
    }

    private void addRepeatedPathOption(List<String> command, String option, List<Path> paths) {
        for (Path classPath : paths) {
            if (classPath != null) {
                command.add(option);
                command.add(path(classPath));
            }
        }
    }

    private Path requirePath(Path value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private String path(Path value) {
        return value.toString().replace('\\', '/');
    }
}
