package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;

class VariableTraceFactMapperTest {
    @Test
    void mapsRequestDerivedArgumentsToPassesParamFacts() {
        SourceLocation location = new SourceLocation("src/main/java/com/acme/UserController.java", 8, 9);
        VariableTraceAnalysisResult result = new VariableTraceAnalysisResult(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new RequestDerivedArgumentInfo(
                        "com.acme.UserController",
                        "handle",
                        "()V",
                        "com.acme.UserService",
                        "find",
                        "(Ljava/lang/String;)V",
                        0,
                        "Ljava/lang/String;",
                        "id",
                        "alias",
                        location)),
                List.of(new ParameterDerivedArgumentInfo(
                        "com.acme.UserService",
                        "load",
                        "(Ljava/lang/String;)V",
                        0,
                        "Ljava/lang/String;",
                        "com.acme.UserDao",
                        "find",
                        "(Ljava/lang/String;)V",
                        0,
                        "Ljava/lang/String;",
                        "alias",
                        location)),
                List.of());

        JavaSourceFactBatch batch = VariableTraceFactMapper.defaults().map(
                result,
                new VariableTraceFactContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "_api",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/java/com/acme/UserController.java"));

        assertTrue(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("PASSES_PARAM")
                && fact.sourceIdentityId().equals("request-param://shop/_root/_api#id")
                && fact.targetIdentityId().equals("param-slot://shop/_root/src/main/java/com.acme.UserService#find(Ljava/lang/String;)V:param[0:Ljava/lang/String;]")
                && fact.confidence() == Confidence.LIKELY));
        assertTrue(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals("PASSES_PARAM")
                && fact.sourceIdentityId().equals("param-slot://shop/_root/src/main/java/com.acme.UserService#load(Ljava/lang/String;)V:param[0:Ljava/lang/String;]")
                && fact.targetIdentityId().equals("param-slot://shop/_root/src/main/java/com.acme.UserDao#find(Ljava/lang/String;)V:param[0:Ljava/lang/String;]")));
    }
}
