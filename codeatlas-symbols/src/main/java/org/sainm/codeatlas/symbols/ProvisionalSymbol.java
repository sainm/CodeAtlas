package org.sainm.codeatlas.symbols;

import java.util.Optional;

public record ProvisionalSymbol(
        SymbolId symbolId,
        DescriptorStatus descriptorStatus,
        String reason,
        Optional<SymbolId> canonicalReplacement) {
    public ProvisionalSymbol {
        if (symbolId == null) {
            throw new IllegalArgumentException("symbolId is required");
        }
        if (descriptorStatus == null) {
            throw new IllegalArgumentException("descriptorStatus is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        canonicalReplacement = canonicalReplacement == null ? Optional.empty() : canonicalReplacement;
        if (canonicalReplacement.isPresent()) {
            ResolvedSymbolRedirect.requireSameMergeBoundary(symbolId, canonicalReplacement.orElseThrow());
        }
    }

    public static ProvisionalSymbol unresolvedDescriptor(SymbolId symbolId, String reason) {
        return new ProvisionalSymbol(symbolId, DescriptorStatus.UNRESOLVED, reason, Optional.empty());
    }

    public boolean allowsCertainFacts() {
        return descriptorStatus == DescriptorStatus.RESOLVED;
    }

    public ProvisionalSymbol withCanonicalReplacement(SymbolId replacement) {
        return new ProvisionalSymbol(symbolId, descriptorStatus, reason, Optional.of(replacement));
    }
}
