package org.sainm.codeatlas.analyzers.struts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StrutsLookupDispatchAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsLookupDispatchMappingsFromSpoonAst() throws Exception {
        Path source = tempDir.resolve("src/main/java/com/acme/UserLookupAction.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            class UserLookupAction extends org.apache.struts.actions.LookupDispatchAction {
                protected Map getKeyMethodMap() {
                    Map methods = new HashMap();
                    methods.put("button.save", "save");
                    methods.put("button.delete", "delete");
                    methods.put(dynamicKey(), "ignored");
                    return methods;
                }
            }
            """);

        List<StrutsLookupDispatchMethodMapping> mappings = new StrutsLookupDispatchAnalyzer().analyze(List.of(source));

        assertEquals(2, mappings.size());
        assertTrue(mappings.stream().anyMatch(mapping -> mapping.actionType().equals("com.acme.UserLookupAction")
            && mapping.resourceKey().equals("button.save")
            && mapping.methodName().equals("save")));
        assertTrue(mappings.stream().anyMatch(mapping -> mapping.actionType().equals("com.acme.UserLookupAction")
            && mapping.resourceKey().equals("button.delete")
            && mapping.methodName().equals("delete")));
    }
}
