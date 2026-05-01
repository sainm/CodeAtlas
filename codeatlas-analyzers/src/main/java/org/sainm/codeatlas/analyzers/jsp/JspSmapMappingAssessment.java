package org.sainm.codeatlas.analyzers.jsp;

import java.util.List;
import java.util.Map;

public record JspSmapMappingAssessment(
    JspSmapMappingStatus status,
    List<String> reasons,
    Map<String, String> evidence
) {
    public JspSmapMappingAssessment {
        if (status == null) {
            status = JspSmapMappingStatus.DEFERRED;
        }
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }
}
