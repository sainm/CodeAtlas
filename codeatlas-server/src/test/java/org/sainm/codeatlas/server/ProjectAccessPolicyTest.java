package org.sainm.codeatlas.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProjectAccessPolicyTest {
    @Test
    void allowAllPolicyAllowsAnyProject() {
        ProjectAccessPolicy policy = ProjectAccessPolicy.allowAll();

        assertDoesNotThrow(() -> policy.requireAllowed("shop"));
        assertDoesNotThrow(() -> policy.requireAllowed("other"));
    }

    @Test
    void allowOnlyPolicyRejectsUnknownProjectsButIgnoresMissingProjectId() {
        ProjectAccessPolicy policy = ProjectAccessPolicy.allowOnly("shop");

        assertDoesNotThrow(() -> policy.requireAllowed("shop"));
        assertDoesNotThrow(() -> policy.requireAllowed(null));
        assertDoesNotThrow(() -> policy.requireAllowed(" "));
        assertThrows(ProjectAccessDeniedException.class, () -> policy.requireAllowed("other"));
    }
}
