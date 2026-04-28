package org.sainm.codeatlas.analyzers.git;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;

public final class JGitRepositoryReader {
    public GitRepositoryInfo info(Path workTree) {
        try (Repository repository = open(workTree)) {
            ObjectId head = repository.resolve("HEAD");
            return new GitRepositoryInfo(repository.getBranch(), head == null ? "UNKNOWN" : head.name());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read Git repository info: " + workTree, exception);
        }
    }

    public GitDiffResult diff(Path workTree, String oldCommit, String newCommit) {
        try (Repository repository = open(workTree)) {
            ObjectId oldTree = repository.resolve(oldCommit + "^{tree}");
            ObjectId newTree = repository.resolve(newCommit + "^{tree}");
            if (oldTree == null || newTree == null) {
                throw new IllegalArgumentException("Unable to resolve commit tree for diff");
            }

            CanonicalTreeParser oldParser = treeParser(repository, oldTree);
            CanonicalTreeParser newParser = treeParser(repository, newTree);
            List<org.eclipse.jgit.diff.DiffEntry> entries;
            try (Git git = new Git(repository)) {
                entries = git.diff()
                    .setOldTree(oldParser)
                    .setNewTree(newParser)
                    .call();
            }

            String diffText = unifiedDiff(repository, entries);
            return new GitDiffResult(oldCommit, newCommit, new UnifiedDiffParser().parseChangedFiles(diffText), diffText);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read Git diff: " + workTree, exception);
        }
    }

    private Repository open(Path workTree) throws Exception {
        return new FileRepositoryBuilder()
            .findGitDir(workTree.toFile())
            .build();
    }

    private CanonicalTreeParser treeParser(Repository repository, ObjectId treeId) throws Exception {
        try (var reader = repository.newObjectReader()) {
            CanonicalTreeParser parser = new CanonicalTreeParser();
            parser.reset(reader, treeId);
            return parser;
        }
    }

    private String unifiedDiff(Repository repository, List<org.eclipse.jgit.diff.DiffEntry> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DiffFormatter formatter = new DiffFormatter(output)) {
            formatter.setRepository(repository);
            for (org.eclipse.jgit.diff.DiffEntry entry : entries) {
                formatter.format(entry);
            }
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}
