package org.sainm.codeatlas.analyzers.files;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class IncrementalScanPlanner {
    public IncrementalScanDiff diff(List<SourceFileFingerprint> before, List<SourceFileFingerprint> after) {
        Map<String, SourceFileFingerprint> beforeByPath = byPath(before);
        Map<String, SourceFileFingerprint> afterByPath = byPath(after);
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        paths.addAll(beforeByPath.keySet());
        paths.addAll(afterByPath.keySet());

        List<FileChange> changes = paths.stream()
            .sorted()
            .map(path -> change(path, beforeByPath.get(path), afterByPath.get(path)))
            .sorted(Comparator.comparing(FileChange::relativePath))
            .toList();
        return new IncrementalScanDiff(changes);
    }

    private FileChange change(String path, SourceFileFingerprint before, SourceFileFingerprint after) {
        if (before == null) {
            return new FileChange(path, FileChangeType.ADDED, null, after);
        }
        if (after == null) {
            return new FileChange(path, FileChangeType.REMOVED, before, null);
        }
        if (!before.sha256().equals(after.sha256()) || before.size() != after.size()) {
            return new FileChange(path, FileChangeType.MODIFIED, before, after);
        }
        return new FileChange(path, FileChangeType.UNCHANGED, before, after);
    }

    private Map<String, SourceFileFingerprint> byPath(List<SourceFileFingerprint> fingerprints) {
        return fingerprints.stream()
            .collect(Collectors.toMap(
                SourceFileFingerprint::relativePath,
                Function.identity(),
                (left, right) -> right
            ));
    }
}
