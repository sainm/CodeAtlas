package org.sainm.codeatlas.analyzers.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EntrypointClueDetector {
    private static final Pattern STATIC_FETCH = Pattern.compile("\\b(?:fetch|axios\\.(?:get|post|put|delete|patch))\\s*\\(\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern FORM_ACTION = Pattern.compile("<form\\b[^>]*\\baction\\s*=\\s*['\"]?([^'\" >]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STRUTS_ACTION = Pattern.compile("<action\\b[^>]*\\bpath\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVA_COMMAND = Pattern.compile("\\bjava\\s+(?:-jar\\s+\\S+|[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+)");

    private EntrypointClueDetector() {
    }

    public static EntrypointClueDetector defaults() {
        return new EntrypointClueDetector();
    }

    public List<EntrypointClue> detect(WorkspaceInventory inventory, WorkspaceLayoutProfile layoutProfile) throws IOException {
        if (inventory == null) {
            throw new IllegalArgumentException("inventory is required");
        }
        if (layoutProfile == null) {
            throw new IllegalArgumentException("layoutProfile is required");
        }
        List<EntrypointClue> clues = new ArrayList<>();
        for (FileInventoryEntry entry : inventory.entries()) {
            String path = entry.relativePath();
            String name = fileName(path);
            String lower = path.toLowerCase(Locale.ROOT);
            if (isBuildScript(name)) {
                clues.add(new EntrypointClue(EntrypointKind.BUILD_SCRIPT, path, name, "static-build-script"));
            }
            if (entry.level() == FileCapabilityLevel.L5_SKIPPED || entry.decodeDiagnostic().binary()) {
                continue;
            }
            String content = Files.readString(inventory.sourceRoot().resolve(path), StandardCharsets.UTF_8);
            if (lower.endsWith(".java")) {
                detectJava(path, content, clues);
            } else if (lower.endsWith("struts-config.xml")) {
                addPatternClues(path, content, STRUTS_ACTION, EntrypointKind.STRUTS_ACTION, clues, "struts-config");
            } else if (lower.endsWith(".jsp") || lower.endsWith(".jspx")) {
                clues.add(new EntrypointClue(EntrypointKind.JSP_PAGE, path, "", "jsp"));
                addPatternClues(path, content, FORM_ACTION, EntrypointKind.HTML_FORM, clues, "jsp-form");
            } else if (lower.endsWith(".html") || lower.endsWith(".htm")) {
                addPatternClues(path, content, FORM_ACTION, EntrypointKind.HTML_FORM, clues, "html-form");
            } else if (lower.endsWith(".js")) {
                addPatternClues(path, content, STATIC_FETCH, EntrypointKind.STATIC_JS_HTTP, clues, "static-js");
            } else if (isShellScript(lower)) {
                if (JAVA_COMMAND.matcher(content).find()) {
                    clues.add(new EntrypointClue(EntrypointKind.SHELL_JAVA_COMMAND, path, "", "shell-java"));
                }
                if (content.contains("ant -f") || content.contains("ant -buildfile")
                        || content.contains("mvn ") || content.contains("gradle ")) {
                    clues.add(new EntrypointClue(EntrypointKind.BUILD_SCRIPT, path, "", "shell-build-command"));
                }
            }
        }
        return List.copyOf(clues);
    }

    private static void detectJava(String path, String content, List<EntrypointClue> clues) {
        if (content.contains("static void main(") || content.contains("static void main (")) {
            clues.add(new EntrypointClue(EntrypointKind.MAIN_METHOD, path, "", "java-main"));
        }
        if (content.contains("@RequestMapping") || content.contains("@GetMapping") || content.contains("@PostMapping")
                || content.contains("@PutMapping") || content.contains("@DeleteMapping") || content.contains("@PatchMapping")) {
            clues.add(new EntrypointClue(EntrypointKind.SPRING_REQUEST_MAPPING, path, "", "spring-mapping"));
        }
        if (content.contains("@Scheduled")) {
            clues.add(new EntrypointClue(EntrypointKind.SCHEDULER, path, "", "spring-scheduled"));
        }
        if (content.contains("@JmsListener") || content.contains("@KafkaListener")
                || content.contains("@RabbitListener") || content.contains("@MessageMapping")) {
            clues.add(new EntrypointClue(EntrypointKind.MESSAGE_LISTENER, path, "", "message-listener"));
        }
    }

    private static void addPatternClues(
            String path,
            String content,
            Pattern pattern,
            EntrypointKind kind,
            List<EntrypointClue> clues,
            String source) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            clues.add(new EntrypointClue(kind, path, matcher.group(1), source));
        }
    }

    private static boolean isBuildScript(String name) {
        return name.equals("build.gradle") || name.equals("settings.gradle") || name.equals("pom.xml") || name.equals("build.xml");
    }

    private static boolean isShellScript(String lowerPath) {
        return lowerPath.endsWith(".sh") || lowerPath.endsWith(".bat") || lowerPath.endsWith(".cmd")
                || lowerPath.endsWith(".ps1");
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
