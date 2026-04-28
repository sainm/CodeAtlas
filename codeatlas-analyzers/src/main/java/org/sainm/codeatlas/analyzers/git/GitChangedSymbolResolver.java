package org.sainm.codeatlas.analyzers.git;

import org.sainm.codeatlas.analyzers.index.ChangedFileCandidates;
import org.sainm.codeatlas.analyzers.index.FileSymbolIndex;
import java.nio.file.Path;
import java.util.List;

public final class GitChangedSymbolResolver {
    private final JGitRepositoryReader repositoryReader;

    public GitChangedSymbolResolver(JGitRepositoryReader repositoryReader) {
        this.repositoryReader = repositoryReader;
    }

    public List<ChangedFileCandidates> resolve(Path workTree, String oldCommit, String newCommit, FileSymbolIndex index) {
        GitDiffResult diff = repositoryReader.diff(workTree, oldCommit, newCommit);
        return index.candidatesForChangedFiles(diff.changedFiles().stream()
            .map(ChangedFile::effectivePath)
            .map(Path::of)
            .toList());
    }
}
