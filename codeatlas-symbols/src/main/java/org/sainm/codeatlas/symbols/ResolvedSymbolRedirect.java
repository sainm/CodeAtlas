package org.sainm.codeatlas.symbols;

public record ResolvedSymbolRedirect(
        SymbolId from,
        SymbolId to,
        AliasMergeStatus status,
        String evidenceKey) {
    public ResolvedSymbolRedirect {
        if (from == null) {
            throw new IllegalArgumentException("from is required");
        }
        if (to == null) {
            throw new IllegalArgumentException("to is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (evidenceKey == null || evidenceKey.isBlank()) {
            throw new IllegalArgumentException("evidenceKey is required");
        }
        requireSameMergeBoundary(from, to);
        if (status == AliasMergeStatus.REDIRECT && !isForwardResolution(from, to)) {
            throw new IllegalArgumentException("redirect must point from unresolved to resolved symbol");
        }
    }

    public static ResolvedSymbolRedirect from(
            ProvisionalSymbol provisional,
            SymbolId resolved,
            String evidenceKey) {
        if (provisional == null) {
            throw new IllegalArgumentException("provisional is required");
        }
        return new ResolvedSymbolRedirect(provisional.symbolId(), resolved, AliasMergeStatus.REDIRECT, evidenceKey);
    }

    public boolean redirects(SymbolId symbolId) {
        return status == AliasMergeStatus.REDIRECT && from.equals(symbolId);
    }

    public SymbolId resolve(SymbolId symbolId) {
        return redirects(symbolId) ? to : symbolId;
    }

    static void requireSameMergeBoundary(SymbolId from, SymbolId to) {
        if (!from.kind().kind().equals(to.kind().kind())) {
            throw new IllegalArgumentException("alias merge cannot cross symbol kind");
        }
        if (!from.projectKey().equals(to.projectKey())) {
            throw new IllegalArgumentException("alias merge cannot cross project");
        }
        if (!from.moduleKey().equals(to.moduleKey())) {
            throw new IllegalArgumentException("alias merge cannot cross module or datasource");
        }
        if (!from.sourceRootKey().equals(to.sourceRootKey())) {
            throw new IllegalArgumentException("alias merge cannot cross source root");
        }
        if (!from.ownerPath().equals(to.ownerPath())) {
            throw new IllegalArgumentException("alias merge cannot cross local owner");
        }
        if (!hasCompatibleFragment(from, to)) {
            throw new IllegalArgumentException("alias merge cannot cross local fragment identity");
        }
    }

    private static boolean hasCompatibleFragment(SymbolId from, SymbolId to) {
        if (from.fragment().equals(to.fragment())) {
            return true;
        }
        String kind = from.kind().kind();
        if (kind.equals(DefaultSymbolKind.METHOD.kind())) {
            return methodName(from).equals(methodName(to))
                    && isExactlyOneUnresolvedMethod(from, to);
        }
        if (kind.equals(DefaultSymbolKind.FIELD.kind())) {
            return fieldName(from).equals(fieldName(to))
                    && isExactlyOneUnresolvedField(from, to);
        }
        return false;
    }

    private static String methodName(SymbolId symbolId) {
        String fragment = symbolId.fragment().orElseThrow(
                () -> new IllegalArgumentException("method symbol must contain member fragment"));
        int descriptorStart = fragment.indexOf('(');
        if (descriptorStart <= 0) {
            throw new IllegalArgumentException("method fragment must contain descriptor");
        }
        return fragment.substring(0, descriptorStart);
    }

    private static String fieldName(SymbolId symbolId) {
        String fragment = symbolId.fragment().orElseThrow(
                () -> new IllegalArgumentException("field symbol must contain member fragment"));
        int descriptorStart = fragment.indexOf(':');
        if (descriptorStart <= 0) {
            throw new IllegalArgumentException("field fragment must contain descriptor");
        }
        return fragment.substring(0, descriptorStart);
    }

    private static boolean isExactlyOneUnresolvedMethod(SymbolId from, SymbolId to) {
        return isUnresolvedMethodFragment(from.fragment().orElseThrow())
                != isUnresolvedMethodFragment(to.fragment().orElseThrow());
    }

    private static boolean isForwardResolution(SymbolId from, SymbolId to) {
        String kind = from.kind().kind();
        if (kind.equals(DefaultSymbolKind.METHOD.kind())) {
            return isUnresolvedMethodFragment(from.fragment().orElseThrow())
                    && !isUnresolvedMethodFragment(to.fragment().orElseThrow());
        }
        if (kind.equals(DefaultSymbolKind.FIELD.kind())) {
            return isUnresolvedFieldFragment(from.fragment().orElseThrow())
                    && !isUnresolvedFieldFragment(to.fragment().orElseThrow());
        }
        return from.equals(to);
    }

    private static boolean isUnresolvedMethodFragment(String fragment) {
        int descriptorStart = fragment.indexOf('(');
        if (descriptorStart <= 0 || !fragment.endsWith(")U")) {
            return false;
        }
        String descriptorMarker = fragment.substring(descriptorStart + 1, fragment.length() - 2);
        return descriptorMarker.length() == 13
                && descriptorMarker.charAt(0) == 'U'
                && isLowerHex(descriptorMarker.substring(1));
    }

    private static boolean isExactlyOneUnresolvedField(SymbolId from, SymbolId to) {
        return isUnresolvedFieldFragment(from.fragment().orElseThrow())
                != isUnresolvedFieldFragment(to.fragment().orElseThrow());
    }

    private static boolean isUnresolvedFieldFragment(String fragment) {
        int descriptorStart = fragment.indexOf(':');
        return descriptorStart > 0 && fragment.substring(descriptorStart + 1).equals("U");
    }

    private static boolean isLowerHex(String value) {
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f'))) {
                return false;
            }
        }
        return true;
    }
}
