package org.sainm.codeatlas.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaiEWorkerCommandBuilderTest {
    @Test
    void buildsIndependentJvmCommandForTaiEWorker() {
        TaiEWorkerRequest request = new TaiEWorkerRequest(
            Path.of("C:/jdk-25/bin/java.exe"),
            Path.of("D:/tools/tai-e/tai-e-all.jar"),
            Path.of("D:/work"),
            Path.of("D:/work/tai-e-output"),
            List.of(Path.of("D:/app/WEB-INF/classes"), Path.of("D:/app/WEB-INF/lib/vendor.jar")),
            List.of(Path.of("D:/app/WEB-INF/classes")),
            "com.acme.Main",
            List.of("com.acme.UserAction", "com.acme.UserService"),
            List.of("cg", "pta=cs:2-type;time-limit:60;"),
            17,
            true,
            true,
            "4g",
            Duration.ofMinutes(10)
        );

        TaiEWorkerLaunchPlan plan = new TaiEWorkerCommandBuilder().build(request);

        assertEquals(Path.of("D:/work"), plan.workingDirectory());
        assertEquals(Duration.ofMinutes(10), plan.timeout());
        assertEquals("C:/jdk-25/bin/java.exe", plan.command().getFirst());
        assertTrue(plan.command().contains("-Xmx4g"));
        assertTrue(plan.command().contains("-jar"));
        assertTrue(plan.command().contains("D:/tools/tai-e/tai-e-all.jar"));
        assertTrue(plan.command().contains("-cp"));
        assertTrue(plan.command().contains("D:/app/WEB-INF/lib/vendor.jar"));
        assertTrue(plan.command().contains("-acp"));
        assertTrue(plan.command().contains("-m"));
        assertTrue(plan.command().contains("com.acme.Main"));
        assertTrue(plan.command().contains("--input-classes=com.acme.UserAction,com.acme.UserService"));
        assertTrue(plan.command().contains("-pp"));
        assertTrue(plan.command().contains("-ap"));
        assertTrue(plan.command().contains("--output-dir"));
        assertTrue(plan.command().contains("D:/work/tai-e-output"));
        assertTrue(plan.command().contains("cg"));
        assertTrue(plan.command().contains("pta=cs:2-type;time-limit:60;"));
    }

    @Test
    void rejectsMissingTaiEJar() {
        TaiEWorkerRequest request = new TaiEWorkerRequest(
            Path.of("java"),
            null,
            Path.of("."),
            Path.of("output"),
            List.of(Path.of("classes")),
            List.of(),
            "",
            List.of(),
            List.of("cg"),
            17,
            false,
            false,
            "2g",
            Duration.ofMinutes(1)
        );

        assertThrows(IllegalArgumentException.class, () -> new TaiEWorkerCommandBuilder().build(request));
    }
}
