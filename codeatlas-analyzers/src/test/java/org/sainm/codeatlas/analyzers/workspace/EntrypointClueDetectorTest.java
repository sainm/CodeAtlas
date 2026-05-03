package org.sainm.codeatlas.analyzers.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntrypointClueDetectorTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsJavaFrameworkWebAndScriptEntrypoints() throws IOException {
        write("src/main/java/com/acme/App.java", "class App { public static void main(String[] args) {} }\n");
        write("src/main/java/com/acme/HomeController.java", """
                import org.springframework.web.bind.annotation.GetMapping;
                class HomeController { @GetMapping("/home") void home() {} }
                """);
        write("src/main/java/com/acme/Jobs.java", """
                import org.springframework.scheduling.annotation.Scheduled;
                class Jobs { @Scheduled(cron = "0 * * * * *") void run() {} }
                """);
        write("src/main/java/com/acme/Listener.java", """
                import org.springframework.jms.annotation.JmsListener;
                class Listener { @JmsListener(destination = "orders") void receive(String event) {} }
                """);
        write("src/main/webapp/WEB-INF/struts-config.xml", "<struts-config><action path=\"/save\" type=\"com.acme.SaveAction\" /></struts-config>\n");
        write("src/main/webapp/index.jsp", "<form action=\"/save\"></form>\n");
        write("src/main/webapp/index.html", "<form action=\"/api/orders\"></form>\n<script src=\"app.js\"></script>\n");
        write("src/main/webapp/app.js", "fetch('/api/orders');\n");
        write("bin/run.sh", "java -jar app.jar\nant -f build.xml deploy\n");
        write("build.gradle", "plugins { id 'java' }\n");

        List<EntrypointClue> clues = detect();

        assertHas(clues, EntrypointKind.MAIN_METHOD, "src/main/java/com/acme/App.java");
        assertHas(clues, EntrypointKind.SPRING_REQUEST_MAPPING, "src/main/java/com/acme/HomeController.java");
        assertHas(clues, EntrypointKind.SCHEDULER, "src/main/java/com/acme/Jobs.java");
        assertHas(clues, EntrypointKind.MESSAGE_LISTENER, "src/main/java/com/acme/Listener.java");
        assertHas(clues, EntrypointKind.STRUTS_ACTION, "src/main/webapp/WEB-INF/struts-config.xml");
        assertHas(clues, EntrypointKind.JSP_PAGE, "src/main/webapp/index.jsp");
        assertHas(clues, EntrypointKind.HTML_FORM, "src/main/webapp/index.html");
        assertHas(clues, EntrypointKind.STATIC_JS_HTTP, "src/main/webapp/app.js");
        assertHas(clues, EntrypointKind.SHELL_JAVA_COMMAND, "bin/run.sh");
        assertHas(clues, EntrypointKind.BUILD_SCRIPT, "build.gradle");
    }

    @Test
    void keepsDynamicJavaScriptOutOfStaticHttpEntrypoints() throws IOException {
        write("src/main/webapp/app.js", "const path = '/api/' + name; fetch(path);\n");

        List<EntrypointClue> clues = detect();

        assertEquals(0, clues.stream()
                .filter(clue -> clue.kind() == EntrypointKind.STATIC_JS_HTTP)
                .count());
    }

    private List<EntrypointClue> detect() throws IOException {
        WorkspaceInventory inventory = WorkspaceInventoryScanner.defaults()
                .scan(ImportRequest.localFolder("ws-entry", tempDir, ImportMode.ASSISTED_IMPORT_REVIEW));
        WorkspaceLayoutProfile layoutProfile = WorkspaceLayoutDetector.defaults().detect(inventory);
        return EntrypointClueDetector.defaults().detect(inventory, layoutProfile);
    }

    private static void assertHas(List<EntrypointClue> clues, EntrypointKind kind, String path) {
        assertTrue(clues.stream().anyMatch(clue -> clue.kind() == kind && clue.evidencePath().equals(path)),
                () -> "missing " + kind + " at " + path + " in " + clues);
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
