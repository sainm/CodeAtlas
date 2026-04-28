package org.sainm.codeatlas.analyzers.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class SourceDirectoryScanner {
    private final FileHasher fileHasher;

    public SourceDirectoryScanner(FileHasher fileHasher) {
        this.fileHasher = fileHasher;
    }

    public List<SourceFileFingerprint> scan(Path root, Set<String> extensions) throws IOException {
        Set<String> normalizedExtensions = extensions.stream()
            .map(extension -> extension.startsWith(".") ? extension.toLowerCase() : "." + extension.toLowerCase())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        try (var stream = Files.walk(root)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> matches(path, normalizedExtensions))
                .map(path -> fingerprint(root, path))
                .sorted(Comparator.comparing(SourceFileFingerprint::relativePath))
                .toList();
        }
    }

    private boolean matches(Path path, Set<String> extensions) {
        String fileName = path.getFileName().toString().toLowerCase();
        return extensions.isEmpty() || extensions.stream().anyMatch(fileName::endsWith);
    }

    private SourceFileFingerprint fingerprint(Path root, Path path) {
        try {
            return fileHasher.fingerprint(root, path);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to fingerprint " + path, exception);
        }
    }
}

