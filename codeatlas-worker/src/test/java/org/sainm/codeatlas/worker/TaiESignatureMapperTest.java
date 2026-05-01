package org.sainm.codeatlas.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;

class TaiESignatureMapperTest {
    @Test
    void mapsTaiEMethodSignatureToCodeAtlasSymbolId() {
        SymbolId symbolId = new TaiESignatureMapper("shop", "_root", "bytecode")
            .mapMethod("<com.acme.UserService: void save(java.lang.String,int)>");

        assertEquals(SymbolKind.METHOD, symbolId.kind());
        assertEquals("com.acme.UserService", symbolId.ownerQualifiedName());
        assertEquals("save", symbolId.memberName());
        assertEquals("(Ljava/lang/String;I)V", symbolId.descriptor());
    }

    @Test
    void mapsConstructorAndArrayTypes() {
        SymbolId symbolId = new TaiESignatureMapper("shop", "_root", "bytecode")
            .mapMethod("<com.acme.UserService: void <init>(java.lang.String[],int[])>");

        assertEquals("<init>", symbolId.memberName());
        assertEquals("([Ljava/lang/String;[I)V", symbolId.descriptor());
    }

    @Test
    void mapsTaiEFieldSignatureToCodeAtlasSymbolId() {
        SymbolId symbolId = new TaiESignatureMapper("shop", "_root", "bytecode")
            .mapField("<com.acme.UserService: java.lang.String name>");

        assertEquals(SymbolKind.FIELD, symbolId.kind());
        assertEquals("com.acme.UserService", symbolId.ownerQualifiedName());
        assertEquals("name", symbolId.memberName());
        assertEquals("Ljava/lang/String;", symbolId.descriptor());
    }
}
