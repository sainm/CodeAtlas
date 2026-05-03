package org.sainm.codeatlas.facts;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class SourceTypeTest {
    @Test
    void includesDesignLevelAnalyzerSourceTypes() {
        assertSame(SourceType.JSP_SMAP, SourceType.valueOf("JSP_SMAP"));
        assertSame(SourceType.JPA, SourceType.valueOf("JPA"));
        assertSame(SourceType.JAVAPARSER_FAST, SourceType.valueOf("JAVAPARSER_FAST"));
    }
}
