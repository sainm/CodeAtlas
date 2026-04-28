package org.sainm.codeatlas.analyzers.git;

import org.sainm.codeatlas.analyzers.index.ChangedFileCandidates;
import org.sainm.codeatlas.analyzers.index.FileSymbolIndex;
import java.nio.file.Path;
import java.util.List;

public final class DiffSymbolCandidateResolver {
    private final UnifiedDiffParser diffParser = new UnifiedDiffParser();

    public List<ChangedFileCandidates> resolve(String diffText, FileSymbolIndex index) {
        List<Path> changedPaths = diffParser.parseChangedFiles(diffText).stream()
            .map(ChangedFile::effectivePath)
            .map(Path::of)
            .toList();
        return index.candidatesForChangedFiles(changedPaths);
    }
}
