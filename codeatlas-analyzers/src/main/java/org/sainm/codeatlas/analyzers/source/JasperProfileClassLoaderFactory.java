package org.sainm.codeatlas.analyzers.source;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JasperProfileClassLoaderFactory {
    private final Map<String, Set<String>> availableClassesByProfile;
    private final Map<String, List<Path>> classpathByProfile;

    private JasperProfileClassLoaderFactory(
            Map<String, Set<String>> availableClassesByProfile,
            Map<String, List<Path>> classpathByProfile) {
        this.availableClassesByProfile = availableClassesByProfile == null
                ? Map.of()
                : Map.copyOf(availableClassesByProfile);
        this.classpathByProfile = classpathByProfile == null
                ? Map.of()
                : Map.copyOf(classpathByProfile);
    }

    static JasperProfileClassLoaderFactory defaults() {
        return new JasperProfileClassLoaderFactory(Map.of(), Map.of());
    }

    static JasperProfileClassLoaderFactory using(Map<String, Set<String>> availableClassesByProfile) {
        return new JasperProfileClassLoaderFactory(availableClassesByProfile, Map.of());
    }

    static JasperProfileClassLoaderFactory usingProfileClasspaths(Map<String, List<Path>> classpathByProfile) {
        return new JasperProfileClassLoaderFactory(Map.of(), classpathByProfile);
    }

    JasperRuntimeProbe probeFor(JasperProjectContext context) {
        String profileName = context == null ? "TOKEN_ONLY" : context.preferredProfile();
        List<Path> profileClasspath = classpathByProfile.get(profileName);
        if (profileClasspath != null && !profileClasspath.isEmpty()) {
            return JasperRuntimeProbe.usingClassLoader(
                    classLoader(profileClasspath),
                    isolatedDiagnostic(profileName));
        }
        Set<String> profileClasses = availableClassesByProfile.get(profileName);
        if (profileName.equals("TOKEN_ONLY")) {
            return JasperRuntimeProbe.using(
                    className -> false,
                    List.of(new JavaAnalysisDiagnostic(
                            "JASPER_TOKEN_ONLY_PROFILE",
                            "Jasper profile=TOKEN_ONLY; using tolerant token fallback")));
        }
        if (profileClasses == null || profileClasses.isEmpty()) {
            if (!profileName.equals("TOKEN_ONLY")) {
                return JasperRuntimeProbe.using(
                        className -> false,
                        List.of(new JavaAnalysisDiagnostic(
                                "JASPER_ISOLATED_PROFILE_MISSING",
                                "Jasper isolated profile=" + profileName + " is not configured; using TOKEN_ONLY")));
            }
            return JasperRuntimeProbe.defaults();
        }
        return JasperRuntimeProbe.using(
                profileClasses::contains,
                isolatedDiagnostic(profileName));
    }

    private static List<JavaAnalysisDiagnostic> isolatedDiagnostic(String profileName) {
        return List.of(new JavaAnalysisDiagnostic(
                "JASPER_ISOLATED_PROFILE_SELECTED",
                "Selected Jasper profile=" + profileName + ", classloader=isolated"));
    }

    private static ClassLoader classLoader(List<Path> classpath) {
        List<URL> urls = new ArrayList<>();
        for (Path entry : classpath) {
            try {
                urls.add(entry.toAbsolutePath().normalize().toUri().toURL());
            } catch (MalformedURLException exception) {
                throw new IllegalArgumentException("Invalid Jasper profile classpath entry: " + entry, exception);
            }
        }
        return URLClassLoader.newInstance(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
    }
}
