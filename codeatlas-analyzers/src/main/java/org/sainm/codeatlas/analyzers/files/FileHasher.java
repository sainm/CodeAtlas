package org.sainm.codeatlas.analyzers.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class FileHasher {
    private static final int BUFFER_SIZE = 64 * 1024;

    public SourceFileFingerprint fingerprint(Path root, Path file) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedFile = file.toAbsolutePath().normalize();
        if (!normalizedFile.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("file must be inside root");
        }
        String relativePath = SourceFileFingerprint.normalize(normalizedRoot.relativize(normalizedFile).toString());
        long size = Files.size(normalizedFile);
        String sha256 = sha256(normalizedFile);
        return new SourceFileFingerprint(
            normalizedRoot,
            normalizedFile,
            relativePath,
            relativePath.toLowerCase(),
            size,
            sha256
        );
    }

    private String sha256(Path file) throws IOException {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = Files.newInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}

