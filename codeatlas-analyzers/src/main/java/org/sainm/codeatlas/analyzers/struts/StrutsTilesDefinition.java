package org.sainm.codeatlas.analyzers.struts;

import java.util.List;

public record StrutsTilesDefinition(
    String name,
    String path,
    String extendsName,
    List<StrutsTilesPut> puts
) {
    public StrutsTilesDefinition {
        name = require(name, "name");
        path = path == null || path.isBlank() ? null : path.trim();
        extendsName = extendsName == null || extendsName.isBlank() ? null : extendsName.trim();
        puts = List.copyOf(puts);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
