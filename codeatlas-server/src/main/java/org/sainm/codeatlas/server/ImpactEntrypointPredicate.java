package org.sainm.codeatlas.server;

import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.util.function.Predicate;

public final class ImpactEntrypointPredicate implements Predicate<SymbolId> {
    @Override
    public boolean test(SymbolId symbol) {
        if (symbol == null) {
            return false;
        }
        return symbol.kind() == SymbolKind.API_ENDPOINT
            || symbol.kind() == SymbolKind.ACTION_PATH
            || symbol.kind() == SymbolKind.JSP_PAGE;
    }
}
