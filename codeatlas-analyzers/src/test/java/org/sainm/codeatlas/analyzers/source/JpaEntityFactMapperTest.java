package org.sainm.codeatlas.analyzers.source;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.facts.Confidence;

class JpaEntityFactMapperTest {
    @Test
    void mapsJpaEntitiesAndFieldsToDatabaseFacts() {
        SourceLocation classLocation = new SourceLocation("src/main/java/com/acme/UserEntity.java", 8, 1);
        SourceLocation fieldLocation = new SourceLocation("src/main/java/com/acme/UserEntity.java", 12, 5);
        JpaEntityAnalysisResult jpa = new JpaEntityAnalysisResult(
                List.of(new JpaEntityInfo(
                        "com.acme.UserEntity",
                        "users",
                        "crm",
                        List.of(new JpaColumnMappingInfo("id", "Ljava/lang/Long;", "user_id", fieldLocation)),
                        classLocation)),
                List.of());

        JavaSourceFactBatch batch = JpaEntityFactMapper.defaults().map(
                jpa,
                new JpaEntityFactContext(
                        "shop",
                        "_root",
                        "src/main/java",
                        "mainDs",
                        "public",
                        "snapshot-1",
                        "analysis-1",
                        "scope-1",
                        "src/main/java/com/acme/UserEntity.java"));

        assertFact(batch,
                "BINDS_TO",
                "class://shop/_root/src/main/java/com.acme.UserEntity",
                "db-table://shop/mainDs/crm/users");
        assertFact(batch,
                "MAPS_TO_COLUMN",
                "field://shop/_root/src/main/java/com.acme.UserEntity#id:Ljava/lang/Long;",
                "db-column://shop/mainDs/crm/users#user_id");
        assertTrue(batch.facts().stream().allMatch(fact -> fact.confidence() == Confidence.LIKELY));
    }

    private static void assertFact(
            JavaSourceFactBatch batch,
            String relation,
            String source,
            String target) {
        assertTrue(batch.facts().stream().anyMatch(fact -> fact.relationType().name().equals(relation)
                && fact.sourceIdentityId().equals(source)
                && fact.targetIdentityId().equals(target)), relation + " " + source + " -> " + target);
    }
}
