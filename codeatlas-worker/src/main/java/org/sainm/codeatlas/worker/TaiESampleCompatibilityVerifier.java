package org.sainm.codeatlas.worker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TaiESampleCompatibilityVerifier {
    private final TaiESignatureMapper signatureMapper;

    public TaiESampleCompatibilityVerifier(TaiESignatureMapper signatureMapper) {
        if (signatureMapper == null) {
            throw new IllegalArgumentException("signatureMapper is required");
        }
        this.signatureMapper = signatureMapper;
    }

    public TaiESampleCompatibilityResult verify(TaiEWorkerRequest request, List<String> outputSignatures) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        List<String> messages = new ArrayList<>();
        int classPathCount = existingClassPathCount(request.classPaths(), messages);
        int mappedSignatureCount = mappedSignatureCount(outputSignatures, messages);
        return new TaiESampleCompatibilityResult(
            messages.isEmpty(),
            classPathCount,
            mappedSignatureCount,
            messages
        );
    }

    private int existingClassPathCount(List<Path> classPaths, List<String> messages) {
        int count = 0;
        for (Path classPath : classPaths) {
            if (classPath != null && Files.exists(classPath)) {
                count++;
            } else {
                messages.add("Missing Tai-e classpath: " + classPath);
            }
        }
        return count;
    }

    private int mappedSignatureCount(List<String> outputSignatures, List<String> messages) {
        if (outputSignatures == null || outputSignatures.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String signature : outputSignatures) {
            try {
                signatureMapper.mapMethod(signature);
                count++;
            } catch (IllegalArgumentException exception) {
                messages.add("Unmapped Tai-e signature: " + signature);
            }
        }
        return count;
    }
}
