package org.sainm.codeatlas.analyzers.source;

import java.util.List;

public record MyBatisMapperAnalysisResult(
        List<MyBatisMapperInterfaceInfo> interfaces,
        List<MyBatisMapperMethodInfo> methods) {
    public MyBatisMapperAnalysisResult {
        interfaces = List.copyOf(interfaces == null ? List.of() : interfaces);
        methods = List.copyOf(methods == null ? List.of() : methods);
    }
}
