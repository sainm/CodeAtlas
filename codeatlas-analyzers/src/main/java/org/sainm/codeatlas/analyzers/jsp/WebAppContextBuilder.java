package org.sainm.codeatlas.analyzers.jsp;

import org.sainm.codeatlas.analyzers.xml.SafeXmlDocumentLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class WebAppContextBuilder {
    private final SafeXmlDocumentLoader xmlLoader = new SafeXmlDocumentLoader();

    public WebAppContext build(Path webRoot) {
        Path webXml = webRoot.resolve("WEB-INF/web.xml");
        String servletVersion = "unknown";
        String defaultEncoding = "UTF-8";
        Map<String, String> taglibs = new LinkedHashMap<>();
        if (Files.exists(webXml)) {
            Document document = xmlLoader.load(webXml);
            Element root = document.getDocumentElement();
            servletVersion = root.getAttribute("version");
            defaultEncoding = defaultEncoding(document);
            taglibs.putAll(taglibs(document));
        }
        taglibs.putAll(tldTaglibs(webRoot));
        taglibs.putAll(jarTaglibs(webRoot));
        return new WebAppContext(webRoot, webXml, servletVersion, jspVersion(servletVersion), "generic", classpath(webRoot), taglibs, tagFiles(webRoot), defaultEncoding);
    }

    private List<Path> classpath(Path webRoot) {
        Path classes = webRoot.resolve("WEB-INF/classes");
        Path lib = webRoot.resolve("WEB-INF/lib");
        java.util.ArrayList<Path> entries = new java.util.ArrayList<>();
        if (Files.exists(classes)) {
            entries.add(classes.toAbsolutePath().normalize());
        }
        if (Files.exists(lib)) {
            try (var stream = Files.list(lib)) {
                entries.addAll(stream.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted()
                    .toList());
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to scan WEB-INF/lib: " + lib, exception);
            }
        }
        entries.addAll(buildOutputClasspath(webRoot));
        return entries;
    }

    private List<Path> buildOutputClasspath(Path webRoot) {
        java.util.ArrayList<Path> entries = new java.util.ArrayList<>();
        for (Path base : candidateProjectRoots(webRoot)) {
            addIfExists(entries, base.resolve("build/classes/java/main"));
            addIfExists(entries, base.resolve("build/resources/main"));
            addIfExists(entries, base.resolve("target/classes"));
        }
        return entries;
    }

    private List<Path> candidateProjectRoots(Path webRoot) {
        java.util.ArrayList<Path> roots = new java.util.ArrayList<>();
        Path current = webRoot.toAbsolutePath().normalize();
        for (int i = 0; i < 5 && current != null; i++) {
            roots.add(current);
            current = current.getParent();
        }
        return roots;
    }

    private void addIfExists(List<Path> entries, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.exists(normalized) && !entries.contains(normalized)) {
            entries.add(normalized);
        }
    }

    private Map<String, String> taglibs(Document document) {
        Map<String, String> taglibs = new LinkedHashMap<>();
        NodeList nodes = document.getElementsByTagName("taglib");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element taglib = (Element) nodes.item(i);
            String uri = text(taglib, "taglib-uri");
            String location = text(taglib, "taglib-location");
            if (uri != null && location != null) {
                taglibs.put(uri, location);
            }
        }
        return taglibs;
    }

    private Map<String, String> tagFiles(Path webRoot) {
        Map<String, String> tagFiles = new LinkedHashMap<>();
        Path tagsRoot = webRoot.resolve("WEB-INF/tags");
        if (!Files.exists(tagsRoot)) {
            return tagFiles;
        }
        try (var stream = Files.walk(tagsRoot)) {
            stream.filter(path -> {
                    String name = path.getFileName().toString();
                    return name.endsWith(".tag") || name.endsWith(".tagx");
                })
                .sorted()
                .forEach(path -> {
                    String location = webRoot.relativize(path).toString().replace('\\', '/');
                    String key = tagFileKey(location);
                    tagFiles.putIfAbsent(key, location);
                    tagFiles.putIfAbsent(location, location);
                });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan JSP tag files: " + tagsRoot, exception);
        }
        return tagFiles;
    }

    private String tagFileKey(String location) {
        String key = location;
        if (key.startsWith("WEB-INF/tags/")) {
            key = key.substring("WEB-INF/tags/".length());
        }
        if (key.endsWith(".tagx")) {
            return key.substring(0, key.length() - ".tagx".length());
        }
        if (key.endsWith(".tag")) {
            return key.substring(0, key.length() - ".tag".length());
        }
        return key;
    }

    private Map<String, String> tldTaglibs(Path webRoot) {
        Map<String, String> taglibs = new LinkedHashMap<>();
        if (!Files.exists(webRoot)) {
            return taglibs;
        }
        try (var stream = Files.walk(webRoot)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".tld"))
                .forEach(path -> {
                    String location = webRoot.relativize(path).toString().replace('\\', '/');
                    taglibs.putIfAbsent(location, path.toString());
                    tldUri(path).ifPresent(uri -> taglibs.putIfAbsent(uri, location));
                });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan TLD files: " + webRoot, exception);
        }
        return taglibs;
    }

    private Map<String, String> jarTaglibs(Path webRoot) {
        Map<String, String> taglibs = new LinkedHashMap<>();
        Path lib = webRoot.resolve("WEB-INF/lib");
        if (!Files.exists(lib)) {
            return taglibs;
        }
        try (var stream = Files.list(lib)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".jar"))
                .sorted()
                .forEach(path -> taglibs.putAll(jarTaglibs(webRoot, path)));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan WEB-INF/lib TLD files: " + lib, exception);
        }
        return taglibs;
    }

    private Map<String, String> jarTaglibs(Path webRoot, Path jarPath) {
        Map<String, String> taglibs = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String entryName = entry.getName();
                if (entry.isDirectory() || !entryName.startsWith("META-INF/") || !entryName.endsWith(".tld")) {
                    continue;
                }
                String jarLocation = webRoot.relativize(jarPath).toString().replace('\\', '/') + "!/" + entryName;
                taglibs.putIfAbsent(jarLocation, jarLocation);
                try (var input = jar.getInputStream(entry)) {
                    Document document = xmlLoader.load(input, jarLocation);
                    String uri = text(document.getDocumentElement(), "uri");
                    if (uri != null) {
                        taglibs.putIfAbsent(uri, jarLocation);
                    }
                } catch (RuntimeException exception) {
                    // Old tag libraries can contain non-standard descriptors; keep the jar location and continue.
                }
            }
        } catch (IOException exception) {
            return Map.of();
        }
        return taglibs;
    }

    private java.util.Optional<String> tldUri(Path tldFile) {
        try {
            Document document = xmlLoader.load(tldFile);
            String uri = text(document.getDocumentElement(), "uri");
            return uri == null ? java.util.Optional.empty() : java.util.Optional.of(uri);
        } catch (RuntimeException exception) {
            return java.util.Optional.empty();
        }
    }

    private String defaultEncoding(Document document) {
        NodeList nodes = document.getElementsByTagName("page-encoding");
        if (nodes.getLength() == 0) {
            return "UTF-8";
        }
        String value = nodes.item(0).getTextContent();
        return value == null || value.isBlank() ? "UTF-8" : value.trim();
    }

    private String jspVersion(String servletVersion) {
        return switch (servletVersion == null ? "" : servletVersion.trim()) {
            case "2.4" -> "2.0";
            case "2.5" -> "2.1";
            case "3.0" -> "2.2";
            case "3.1", "4.0" -> "2.3";
            case "5.0" -> "3.0";
            case "6.0" -> "3.1";
            default -> "unknown";
        };
    }

    private String text(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = nodes.item(0).getTextContent();
        return value == null || value.isBlank() ? null : value.trim();
    }
}
