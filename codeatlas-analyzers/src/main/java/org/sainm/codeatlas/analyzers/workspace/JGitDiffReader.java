package org.sainm.codeatlas.analyzers.workspace;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;

public final class JGitDiffReader {
    private JGitDiffReader() {
    }

    public static JGitDiffReader defaults() {
        return new JGitDiffReader();
    }

    public GitDiffDetails read(Path workspaceRoot, String baseRevision, String headRevision) {
        if (workspaceRoot == null) {
            throw new IllegalArgumentException("workspaceRoot is required");
        }
        try (Repository repository = openRepository(workspaceRoot)) {
            ObjectId headCommit = resolve(repository, blankToDefault(headRevision, Constants.HEAD), "headRevision");
            ObjectId baseCommit = resolve(repository, blankToDefault(baseRevision, headCommit.getName() + "^"), "baseRevision");
            return new GitDiffDetails(
                    repository.getBranch(),
                    baseCommit.getName(),
                    headCommit.getName(),
                    changedFiles(repository, baseCommit, headCommit));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read git diff: " + exception.getMessage(), exception);
        }
    }

    private static Repository openRepository(Path workspaceRoot) throws IOException {
        return new FileRepositoryBuilder()
                .findGitDir(workspaceRoot.toFile())
                .build();
    }

    private static ObjectId resolve(Repository repository, String revision, String name) throws IOException {
        ObjectId id = repository.resolve(revision + "^{commit}");
        if (id == null) {
            throw new IllegalArgumentException(name + " cannot be resolved: " + revision);
        }
        return id;
    }

    private static List<GitChangedFile> changedFiles(
            Repository repository,
            ObjectId baseCommit,
            ObjectId headCommit) throws IOException {
        try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
                ObjectReader reader = repository.newObjectReader()) {
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);
            List<GitChangedFile> result = new ArrayList<>();
            for (DiffEntry diff : formatter.scan(
                    treeParser(repository, reader, baseCommit),
                    treeParser(repository, reader, headCommit))) {
                FileHeader fileHeader = formatter.toFileHeader(diff);
                result.add(new GitChangedFile(
                        diff.getOldPath().equals(DiffEntry.DEV_NULL) ? "" : diff.getOldPath(),
                        diff.getNewPath().equals(DiffEntry.DEV_NULL) ? "" : diff.getNewPath(),
                        diff.getChangeType().name(),
                        hunks(fileHeader)));
            }
            return result;
        }
    }

    private static CanonicalTreeParser treeParser(
            Repository repository,
            ObjectReader reader,
            ObjectId commitId) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(commitId);
            CanonicalTreeParser parser = new CanonicalTreeParser();
            parser.reset(reader, commit.getTree());
            return parser;
        }
    }

    private static List<GitChangedHunk> hunks(FileHeader fileHeader) {
        List<GitChangedHunk> result = new ArrayList<>();
        for (Edit edit : fileHeader.toEditList()) {
            result.add(new GitChangedHunk(
                    edit.getBeginA() + 1,
                    edit.getEndA() - edit.getBeginA(),
                    edit.getBeginB() + 1,
                    edit.getEndB() - edit.getBeginB()));
        }
        return result;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
