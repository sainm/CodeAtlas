package org.sainm.codeatlas.analyzers.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class WorkspaceInventoryScanner {
    private static final long DEFAULT_MAX_FILE_BYTES = 20L * 1024L * 1024L;
    private static final Set<String> L1_EXTENSIONS = Set.of(
            ".java", ".class", ".jar", ".xml", ".properties", ".yml", ".yaml", ".sql", ".jsp", ".jspx", ".tag", ".tagx");
    private static final Set<String> L2_EXTENSIONS = Set.of(
            ".sh", ".bat", ".cmd", ".ps1", ".gradle", ".classpath", ".project", ".css");
    private static final Set<String> L3_EXTENSIONS = Set.of(
            ".exe", ".dll", ".so", ".a", ".lib", ".c", ".h", ".cpp", ".hpp", ".cbl", ".cob", ".cpy", ".jcl");
    private static final Set<String> L4_EXTENSIONS = Set.of(
            ".txt", ".md", ".csv", ".json", ".html", ".htm", ".js", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".pdf",
            ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx");
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            ".class", ".jar", ".exe", ".dll", ".so", ".a", ".lib", ".png", ".jpg", ".jpeg", ".gif", ".pdf", ".doc",
            ".docx", ".xls", ".xlsx", ".ppt", ".pptx");

    private final long maxFileBytes;

    public WorkspaceInventoryScanner(long maxFileBytes) {
        if (maxFileBytes < 1) {
            throw new IllegalArgumentException("maxFileBytes must be positive");
        }
        this.maxFileBytes = maxFileBytes;
    }

    public static WorkspaceInventoryScanner defaults() {
        return new WorkspaceInventoryScanner(DEFAULT_MAX_FILE_BYTES);
    }

    public WorkspaceInventory scan(ImportRequest request) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        List<FileInventoryEntry> entries = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(request.sourceRoot())) {
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> relativePath(request.sourceRoot(), path)))
                    .toList();
            for (Path file : files) {
                entries.add(toEntry(request.sourceRoot(), file));
            }
        }
        return new WorkspaceInventory(
                request.workspaceId(),
                request.sourceType(),
                request.mode(),
                request.sourceRoot(),
                request.archivePath(),
                entries);
    }

    private FileInventoryEntry toEntry(Path sourceRoot, Path file) throws IOException {
        String relativePath = relativePath(sourceRoot, file);
        long sizeBytes = Files.size(file);
        FileCapabilityLevel level = classify(relativePath, sizeBytes);
        DecodeDiagnostic diagnostic = decodeDiagnostic(relativePath, file, sizeBytes, level);
        String sha256 = level == FileCapabilityLevel.L5_SKIPPED ? "" : sha256(file);
        if (diagnostic.code().equals("DECODE_FAILED")) {
            level = FileCapabilityLevel.L5_SKIPPED;
            sha256 = "";
        }
        return new FileInventoryEntry(relativePath, sizeBytes, sha256, level, diagnostic);
    }

    private FileCapabilityLevel classify(String relativePath, long sizeBytes) {
        if (sizeBytes > maxFileBytes) {
            return FileCapabilityLevel.L5_SKIPPED;
        }
        String fileName = fileName(relativePath);
        String extension = extension(fileName);
        if (fileName.equals("pom.xml") || fileName.equals("build.xml") || fileName.equals("build.gradle")
                || fileName.equals("settings.gradle") || fileName.equals("Makefile") || fileName.equals("CMakeLists.txt")) {
            return FileCapabilityLevel.L2_SEMI_STRUCTURED;
        }
        if (L3_EXTENSIONS.contains(extension)) {
            return FileCapabilityLevel.L3_BOUNDARY;
        }
        if (L1_EXTENSIONS.contains(extension)) {
            return FileCapabilityLevel.L1_STRUCTURED;
        }
        if (L2_EXTENSIONS.contains(extension)) {
            return FileCapabilityLevel.L2_SEMI_STRUCTURED;
        }
        if (L4_EXTENSIONS.contains(extension)) {
            return FileCapabilityLevel.L4_INVENTORY;
        }
        return FileCapabilityLevel.L5_SKIPPED;
    }

    private DecodeDiagnostic decodeDiagnostic(
            String relativePath,
            Path file,
            long sizeBytes,
            FileCapabilityLevel level) throws IOException {
        if (level == FileCapabilityLevel.L5_SKIPPED) {
            if (sizeBytes > maxFileBytes) {
                return DecodeDiagnostic.skipped("FILE_TOO_LARGE", "file exceeds inventory size limit");
            }
            return DecodeDiagnostic.skipped("UNSUPPORTED_FILE_TYPE", "file type is not supported");
        }
        if (BINARY_EXTENSIONS.contains(extension(fileName(relativePath)))) {
            return DecodeDiagnostic.binaryFile();
        }
        try {
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Files.readAllBytes(file)));
            return DecodeDiagnostic.text(StandardCharsets.UTF_8.name());
        } catch (CharacterCodingException exception) {
            return DecodeDiagnostic.skipped("DECODE_FAILED", exception.getMessage());
        }
    }

    private static String relativePath(Path sourceRoot, Path file) {
        return sourceRoot.relativize(file).toString().replace('\\', '/');
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (InputStream input = Files.newInputStream(file)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String fileName(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash >= 0 ? relativePath.substring(slash + 1) : relativePath;
    }

    private static String extension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        return dot >= 0 ? lower.substring(dot) : "";
    }
}
