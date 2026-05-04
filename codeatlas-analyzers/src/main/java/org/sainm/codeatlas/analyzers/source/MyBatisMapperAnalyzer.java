package org.sainm.codeatlas.analyzers.source;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MyBatisMapperAnalyzer {
    private static final String MYBATIS_MAPPER_ANNOTATION = "org.apache.ibatis.annotations.Mapper";

    private MyBatisMapperAnalyzer() {
    }

    public static MyBatisMapperAnalyzer defaults() {
        return new MyBatisMapperAnalyzer();
    }

    public MyBatisMapperAnalysisResult analyze(JavaSourceAnalysisResult source) {
        return analyze(source, null);
    }

    public MyBatisMapperAnalysisResult analyze(JavaSourceAnalysisResult source, MyBatisXmlAnalysisResult xml) {
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        Set<String> xmlNamespaces = xmlNamespaces(xml);
        List<MyBatisMapperInterfaceInfo> interfaces = new ArrayList<>();
        Set<String> mapperNames = new HashSet<>();
        for (JavaClassInfo type : source.classes()) {
            if (type.kind() == JavaTypeKind.INTERFACE
                    && (isMyBatisMapper(type.annotations()) || xmlNamespaces.contains(type.qualifiedName()))) {
                interfaces.add(new MyBatisMapperInterfaceInfo(type.qualifiedName(), type.location()));
                mapperNames.add(type.qualifiedName());
            }
        }

        List<MyBatisMapperMethodInfo> methods = new ArrayList<>();
        for (JavaMethodInfo method : source.methods()) {
            if (mapperNames.contains(method.ownerQualifiedName()) && !method.hasBody()) {
                methods.add(new MyBatisMapperMethodInfo(
                        method.ownerQualifiedName(),
                        method.simpleName(),
                        method.signature(),
                        method.location()));
            }
        }
        return new MyBatisMapperAnalysisResult(interfaces, methods);
    }

    private static Set<String> xmlNamespaces(MyBatisXmlAnalysisResult xml) {
        if (xml == null) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (MyBatisXmlMapperInfo mapper : xml.mappers()) {
            result.add(mapper.namespace());
        }
        return result;
    }

    private static boolean isMyBatisMapper(List<String> annotations) {
        return annotations.stream()
                .anyMatch(annotation -> annotation.equals(MYBATIS_MAPPER_ANNOTATION));
    }
}
