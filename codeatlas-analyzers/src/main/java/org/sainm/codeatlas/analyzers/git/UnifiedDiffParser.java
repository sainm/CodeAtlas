package org.sainm.codeatlas.analyzers.git;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UnifiedDiffParser {
    public List<ChangedFile> parseChangedFiles(String diffText) {
        Map<String, ChangedFile> files = new LinkedHashMap<>();
        String currentOld = null;
        String currentNew = null;
        for (String line : diffText.split("\\R")) {
            if (line.startsWith("diff --git ")) {
                if (currentOld != null || currentNew != null) {
                    add(files, currentOld, currentNew);
                }
                String[] parts = line.split("\\s+");
                currentOld = parts.length > 2 ? parts[2] : null;
                currentNew = parts.length > 3 ? parts[3] : null;
            } else if (line.startsWith("--- ")) {
                currentOld = line.substring(4).trim();
            } else if (line.startsWith("+++ ")) {
                currentNew = line.substring(4).trim();
            }
        }
        if (currentOld != null || currentNew != null) {
            add(files, currentOld, currentNew);
        }
        return List.copyOf(files.values());
    }

    private void add(Map<String, ChangedFile> files, String oldPath, String newPath) {
        ChangedFile file = new ChangedFile(oldPath, newPath);
        files.putIfAbsent(file.effectivePath(), file);
    }
}
