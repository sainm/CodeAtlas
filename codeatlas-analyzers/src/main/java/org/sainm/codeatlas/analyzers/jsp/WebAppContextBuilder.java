package org.sainm.codeatlas.analyzers.jsp;

import org.sainm.codeatlas.analyzers.xml.SafeXmlDocumentLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class WebAppContextBuilder {
    private final SafeXmlDocumentLoader xmlLoader = new SafeXmlDocumentLoader();

    public WebAppContext build(Path webRoot) {
        Path webXml = webRoot.resolve("WEB-INF/web.xml");
        String servletVersion = "unknown";
        Map<String, String> taglibs = new LinkedHashMap<>();
        if (Files.exists(webXml)) {
            Document document = xmlLoader.load(webXml);
            Element root = document.getDocumentElement();
            servletVersion = root.getAttribute("version");
            taglibs.putAll(taglibs(document));
        }
        taglibs.putAll(tldTaglibs(webRoot));
        return new WebAppContext(webRoot, webXml, servletVersion, "unknown", "generic", classpath(webRoot), taglibs, "UTF-8");
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
        return entries;
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

    private Map<String, String> tldTaglibs(Path webRoot) {
        Map<String, String> taglibs = new LinkedHashMap<>();
        if (!Files.exists(webRoot)) {
            return taglibs;
        }
        try (var stream = Files.walk(webRoot)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".tld"))
                .forEach(path -> taglibs.putIfAbsent(webRoot.relativize(path).toString().replace('\\', '/'), path.toString()));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan TLD files: " + webRoot, exception);
        }
        return taglibs;
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
