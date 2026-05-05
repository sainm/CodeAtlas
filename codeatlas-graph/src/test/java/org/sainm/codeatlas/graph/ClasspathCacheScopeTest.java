package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClasspathCacheScopeTest {

    @Test
    void projectWideHasEmptyScopeKey() {
        ClasspathCacheScope scope = ClasspathCacheScope.projectWide();
        assertEquals(ClasspathCacheScope.ScopeType.PROJECT_WIDE, scope.type());
        assertEquals("", scope.scopeKey());
        assertTrue(scope.isProjectWide());
    }

    @Test
    void forModuleRequiresNonBlankPath() {
        assertThrows(IllegalArgumentException.class,
                () -> ClasspathCacheScope.forModule(""));
        assertThrows(IllegalArgumentException.class,
                () -> ClasspathCacheScope.forModule(null));
    }

    @Test
    void forJarRequiresNonBlankPath() {
        assertThrows(IllegalArgumentException.class,
                () -> ClasspathCacheScope.forJar(""));
        assertThrows(IllegalArgumentException.class,
                () -> ClasspathCacheScope.forJar(null));
    }

    @Test
    void forModuleCreatesModuleScope() {
        ClasspathCacheScope scope = ClasspathCacheScope.forModule("app-web");
        assertEquals(ClasspathCacheScope.ScopeType.MODULE, scope.type());
        assertEquals("app-web", scope.scopeKey());
        assertFalse(scope.isProjectWide());
    }

    @Test
    void forJarCreatesJarScope() {
        ClasspathCacheScope scope = ClasspathCacheScope.forJar("WEB-INF/lib/vendor.jar");
        assertEquals(ClasspathCacheScope.ScopeType.JAR, scope.type());
        assertEquals("WEB-INF/lib/vendor.jar", scope.scopeKey());
    }

    @Test
    void projectWideCoversEverything() {
        ClasspathCacheScope projectWide = ClasspathCacheScope.projectWide();
        assertTrue(projectWide.covers(ClasspathCacheScope.forModule("app-web")));
        assertTrue(projectWide.covers(ClasspathCacheScope.forJar("vendor.jar")));
        assertTrue(projectWide.covers(ClasspathCacheScope.projectWide()));
    }

    @Test
    void moduleScopeDoesNotCoverDifferentModule() {
        ClasspathCacheScope moduleA = ClasspathCacheScope.forModule("module-a");
        assertTrue(moduleA.covers(ClasspathCacheScope.forModule("module-a")));
        assertFalse(moduleA.covers(ClasspathCacheScope.forModule("module-b")));
        assertFalse(moduleA.covers(ClasspathCacheScope.forJar("vendor.jar")));
        assertFalse(moduleA.covers(ClasspathCacheScope.projectWide()));
    }

    @Test
    void jarScopeDoesNotCoverDifferentJar() {
        ClasspathCacheScope jarA = ClasspathCacheScope.forJar("a.jar");
        assertTrue(jarA.covers(ClasspathCacheScope.forJar("a.jar")));
        assertFalse(jarA.covers(ClasspathCacheScope.forJar("b.jar")));
        assertFalse(jarA.covers(ClasspathCacheScope.forModule("app-web")));
    }

    @Test
    void toStringIsHumanReadable() {
        assertEquals("PROJECT_WIDE", ClasspathCacheScope.projectWide().toString());
        assertTrue(ClasspathCacheScope.forModule("app-web").toString().contains("app-web"));
        assertTrue(ClasspathCacheScope.forJar("vendor.jar").toString().contains("vendor.jar"));
    }
}
