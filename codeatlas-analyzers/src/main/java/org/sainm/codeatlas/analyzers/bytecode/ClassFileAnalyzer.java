package org.sainm.codeatlas.analyzers.bytecode;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.analyzers.java.ApplicationLayerClassifier;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import org.sainm.codeatlas.graph.model.GraphNodeFactory;
import org.sainm.codeatlas.graph.model.NodeRole;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

public final class ClassFileAnalyzer {
    public ClassFileAnalysisResult analyze(AnalyzerScope scope, String projectKey, String symbolSourceRootKey, List<Path> binaryFiles) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphFact> facts = new ArrayList<>();
        for (Path binaryFile : binaryFiles) {
            String normalized = binaryFile.toString().replace('\\', '/');
            if (normalized.endsWith(".jar")) {
                analyzeJar(scope, projectKey, symbolSourceRootKey, binaryFile, nodes, facts);
            } else if (normalized.endsWith(".class")) {
                analyzeClassFile(scope, projectKey, symbolSourceRootKey, binaryFile, nodes, facts);
            }
        }
        return new ClassFileAnalysisResult(nodes, facts);
    }

    private void analyzeJar(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path jarPath,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (entry.getName().endsWith(".class") && !entry.getName().equals("module-info.class")) {
                    try (InputStream input = jar.getInputStream(entry)) {
                        analyzeClassBytes(scope, projectKey, sourceRootKey, jarPath, entry.getName(), input.readAllBytes(), nodes, facts);
                    }
                } else if (isClasspathResource(entry.getName())) {
                    nodes.add(GraphNodeFactory.configNode(SymbolId.logicalPath(
                        SymbolKind.CONFIG_KEY,
                        projectKey,
                        scope.moduleKey(),
                        sourceRootKey,
                        jarPath.toString(),
                        "resource:" + entry.getName()
                    )));
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to analyze jar: " + jarPath, exception);
        }
    }

    private boolean isClasspathResource(String entryName) {
        String normalized = entryName == null ? "" : entryName.toLowerCase(java.util.Locale.ROOT);
        return normalized.endsWith(".xml")
            || normalized.endsWith(".properties")
            || normalized.endsWith(".factories")
            || normalized.endsWith(".handlers")
            || normalized.endsWith(".schemas")
            || normalized.endsWith(".yml")
            || normalized.endsWith(".yaml")
            || normalized.endsWith(".dicon")
            || normalized.endsWith(".tld")
            || normalized.endsWith(".xsd");
    }

    private void analyzeClassFile(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path classFile,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        try {
            analyzeClassBytes(scope, projectKey, sourceRootKey, classFile, classFile.getFileName().toString(), Files.readAllBytes(classFile), nodes, facts);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to analyze class file: " + classFile, exception);
        }
    }

    private void analyzeClassBytes(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path evidencePath,
        String localPath,
        byte[] bytes,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        try {
            ClassFileInfo classFile = ClassFileInfo.read(bytes);
            SymbolId classSymbol = typeSymbol(projectKey, scope.moduleKey(), sourceRootKey, classFile.className(), classFile.symbolKind());
            nodes.add(GraphNodeFactory.classNode(
                classSymbol,
                roleForClass(classFile.className(), classFile.annotationNames()),
                classProperties(classFile.annotationNames())
            ));
            if (classFile.superClassName() != null && !classFile.superClassName().equals("java.lang.Object")) {
                SymbolId superClass = SymbolId.classSymbol(projectKey, scope.moduleKey(), sourceRootKey, classFile.superClassName());
                nodes.add(GraphNodeFactory.classNode(superClass, ApplicationLayerClassifier.roleForQualifiedName(classFile.superClassName())));
                facts.add(fact(scope, classSymbol, RelationType.EXTENDS, superClass, evidencePath, localPath, "classfile-extends", Confidence.CERTAIN));
            }
            for (String interfaceName : classFile.interfaceNames()) {
                SymbolId interfaceSymbol = new SymbolId(
                    SymbolKind.INTERFACE,
                    projectKey,
                    scope.moduleKey(),
                    sourceRootKey,
                    interfaceName,
                    null,
                    null,
                    null
                );
                nodes.add(GraphNodeFactory.classNode(interfaceSymbol, ApplicationLayerClassifier.roleForQualifiedName(interfaceName)));
                facts.add(fact(scope, classSymbol, RelationType.IMPLEMENTS, interfaceSymbol, evidencePath, localPath, "classfile-implements", Confidence.CERTAIN));
            }
            addClassFileFieldFacts(scope, projectKey, sourceRootKey, evidencePath, localPath, classFile, classSymbol, nodes, facts);
            for (ClassFileMethod method : classFile.methods()) {
                if (method.name().startsWith("<")) {
                    continue;
                }
                SymbolId methodSymbol = SymbolId.method(
                    projectKey,
                    scope.moduleKey(),
                    sourceRootKey,
                    classFile.className(),
                    method.name(),
                    method.sourceDescriptor()
                );
                nodes.add(GraphNodeFactory.methodNode(
                    methodSymbol,
                    ApplicationLayerClassifier.roleForQualifiedName(classFile.className()),
                    GraphNodeFactory.jvmMethodProperties(method.synthetic(), method.bridge())
                ));
                facts.add(fact(scope, classSymbol, RelationType.DECLARES, methodSymbol, evidencePath, localPath, "classfile-method:" + method.name(), Confidence.CERTAIN));
                addClassFileSpringEndpointFacts(scope, projectKey, sourceRootKey, evidencePath, localPath, classFile, method, methodSymbol, nodes, facts);
                for (MethodCall call : method.calls()) {
                    if (call.methodName().startsWith("<")) {
                        continue;
                    }
                    SymbolId target = SymbolId.method(
                        projectKey,
                        scope.moduleKey(),
                        sourceRootKey,
                        call.ownerClassName(),
                        call.methodName(),
                        call.sourceDescriptor()
                    );
                    nodes.add(GraphNodeFactory.methodNode(target, ApplicationLayerClassifier.roleForQualifiedName(call.ownerClassName())));
                    facts.add(fact(
                        scope,
                        methodSymbol,
                        RelationType.CALLS,
                        target,
                        evidencePath,
                        localPath,
                        call.qualifier(),
                        call.confidence()
                    ));
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse class bytes from: " + evidencePath + "!" + localPath, exception);
        }
    }

    private void addClassFileSpringEndpointFacts(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path evidencePath,
        String localPath,
        ClassFileInfo classFile,
        ClassFileMethod method,
        SymbolId methodSymbol,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        if (!springController(classFile.annotationNames())) {
            return;
        }
        EndpointMapping mapping = endpointMapping(method.annotations());
        if (mapping == null) {
            return;
        }
        String classPath = annotationValue(classFile.annotations(), "RequestMapping", "path")
            .or(() -> annotationValue(classFile.annotations(), "RequestMapping", "value"))
            .orElse("");
        String fullPath = joinPaths(classPath, mapping.path());
        SymbolId endpoint = SymbolId.logicalPath(SymbolKind.API_ENDPOINT, projectKey, scope.moduleKey(), sourceRootKey, fullPath, mapping.httpMethod());
        nodes.add(GraphNodeFactory.apiEndpointNode(endpoint));
        nodes.add(GraphNodeFactory.methodNode(methodSymbol, NodeRole.SPRING_HANDLER, GraphNodeFactory.jvmMethodProperties(method.synthetic(), method.bridge())));
        facts.add(fact(scope, endpoint, RelationType.ROUTES_TO, methodSymbol, evidencePath, localPath, "classfile-spring-endpoint:" + mapping.httpMethod(), Confidence.LIKELY));
    }

    private void addClassFileFieldFacts(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path evidencePath,
        String localPath,
        ClassFileInfo classFile,
        SymbolId classSymbol,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        boolean entity = hasSimpleAnnotation(classFile.annotationNames(), "Entity");
        SymbolId table = null;
        if (entity) {
            String tableName = annotationValue(classFile.annotations(), "Table", "name")
                .or(() -> annotationValue(classFile.annotations(), "Table", "value"))
                .orElseGet(() -> simpleClassName(classFile.className()));
            table = SymbolId.logicalPath(SymbolKind.DB_TABLE, projectKey, scope.moduleKey(), "_database", tableName, null);
            nodes.add(GraphNodeFactory.tableNode(table));
            facts.add(fact(scope, classSymbol, RelationType.BINDS_TO, table, evidencePath, localPath, "classfile-jpa-entity-table:" + tableName, Confidence.POSSIBLE));
        }
        for (ClassFileField field : classFile.fields()) {
            if (field.synthetic()) {
                continue;
            }
            SymbolId fieldSymbol = SymbolId.field(projectKey, scope.moduleKey(), sourceRootKey, classFile.className(), field.name(), field.sourceDescriptor());
            nodes.add(GraphNodeFactory.fieldNode(
                fieldSymbol,
                ApplicationLayerClassifier.roleForQualifiedName(classFile.className()),
                fieldProperties(field)
            ));
            facts.add(fact(scope, classSymbol, RelationType.DECLARES, fieldSymbol, evidencePath, localPath, "classfile-field:" + field.name(), Confidence.CERTAIN));
            if (entity && table != null && !field.staticField() && !hasSimpleAnnotation(field.annotationNames(), "Transient")) {
                String columnName = annotationValue(field.annotations(), "Column", "name")
                    .or(() -> annotationValue(field.annotations(), "Column", "value"))
                    .or(() -> annotationValue(field.annotations(), "JoinColumn", "name"))
                    .orElse(field.name());
                SymbolId column = SymbolId.logicalPath(SymbolKind.DB_COLUMN, projectKey, scope.moduleKey(), "_database", table.ownerQualifiedName(), columnName);
                nodes.add(GraphNodeFactory.tableNode(column));
                facts.add(fact(
                    scope,
                    fieldSymbol,
                    RelationType.BINDS_TO,
                    column,
                    evidencePath,
                    localPath,
                    "classfile-jpa-field-column:" + table.ownerQualifiedName() + "." + columnName,
                    hasSimpleAnnotation(field.annotationNames(), "Column") ? Confidence.LIKELY : Confidence.POSSIBLE
                ));
            }
        }
    }

    private Map<String, String> fieldProperties(ClassFileField field) {
        java.util.LinkedHashMap<String, String> properties = new java.util.LinkedHashMap<>();
        properties.put("codeOrigin", "jvm");
        properties.put("hasSource", "false");
        properties.put("hasJvm", "true");
        properties.put("sourceOnly", "false");
        properties.put("jvmOnly", "true");
        properties.put("static", Boolean.toString(field.staticField()));
        properties.put("synthetic", Boolean.toString(field.synthetic()));
        if (!field.annotationNames().isEmpty()) {
            properties.put("annotations", String.join(",", field.annotationNames()));
        }
        return Map.copyOf(properties);
    }

    private SymbolId typeSymbol(String projectKey, String moduleKey, String sourceRootKey, String className, org.sainm.codeatlas.graph.model.SymbolKind kind) {
        return new SymbolId(kind, projectKey, moduleKey, sourceRootKey, className, null, null, null);
    }

    private NodeRole roleForClass(String className, List<String> annotationNames) {
        if (springController(annotationNames)) {
            return NodeRole.CONTROLLER;
        }
        if (annotationNames.contains("org.springframework.stereotype.Service")) {
            return NodeRole.SERVICE;
        }
        if (annotationNames.contains("org.springframework.stereotype.Repository")) {
            return NodeRole.DAO;
        }
        return ApplicationLayerClassifier.roleForQualifiedName(className);
    }

    private boolean springController(List<String> annotationNames) {
        return annotationNames.stream().anyMatch(Set.of(
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController"
        )::contains);
    }

    private Map<String, String> classProperties(List<String> annotationNames) {
        if (annotationNames.isEmpty()) {
            return Map.of();
        }
        return Map.of("annotations", String.join(",", annotationNames));
    }

    private boolean hasSimpleAnnotation(List<String> annotationNames, String simpleName) {
        return annotationNames.stream().anyMatch(annotation -> simpleClassName(annotation).equals(simpleName));
    }

    private java.util.Optional<String> annotationValue(List<ClassFileAnnotation> annotations, String simpleName, String key) {
        return annotations.stream()
            .filter(annotation -> simpleClassName(annotation.name()).equals(simpleName))
            .map(annotation -> annotation.values().getOrDefault(key, ""))
            .filter(value -> !value.isBlank())
            .findFirst();
    }

    private EndpointMapping endpointMapping(List<ClassFileAnnotation> annotations) {
        for (ClassFileAnnotation annotation : annotations) {
            String simpleName = simpleClassName(annotation.name());
            String httpMethod = switch (simpleName) {
                case "GetMapping" -> "GET";
                case "PostMapping" -> "POST";
                case "PutMapping" -> "PUT";
                case "DeleteMapping" -> "DELETE";
                case "PatchMapping" -> "PATCH";
                case "RequestMapping" -> requestMappingMethod(annotation);
                default -> null;
            };
            if (httpMethod != null) {
                String path = firstAnnotationValue(annotation, "path", "value").orElse("/");
                return new EndpointMapping(httpMethod, path);
            }
        }
        return null;
    }

    private String requestMappingMethod(ClassFileAnnotation annotation) {
        String method = firstAnnotationValue(annotation, "method").orElse("");
        if (method.contains(",")) {
            return method.split(",")[0].trim();
        }
        return method.isBlank() ? "ANY" : method;
    }

    private java.util.Optional<String> firstAnnotationValue(ClassFileAnnotation annotation, String... keys) {
        for (String key : keys) {
            String value = annotation.values().getOrDefault(key, "");
            if (!value.isBlank()) {
                return java.util.Optional.of(value);
            }
        }
        return java.util.Optional.empty();
    }

    private String joinPaths(String left, String right) {
        String normalizedLeft = left == null || left.isBlank() || left.equals("/") ? "" : left;
        String normalizedRight = right == null || right.isBlank() || right.equals("/") ? "" : right;
        String joined = normalizedLeft + "/" + (normalizedRight.startsWith("/") ? normalizedRight.substring(1) : normalizedRight);
        return joined.equals("/") || joined.isBlank() ? "/" : joined.replaceAll("/{2,}", "/");
    }

    private String simpleClassName(String className) {
        int separator = className == null ? -1 : className.lastIndexOf('.');
        return separator < 0 ? className : className.substring(separator + 1);
    }

    private GraphFact fact(
        AnalyzerScope scope,
        SymbolId source,
        RelationType relationType,
        SymbolId target,
        Path evidencePath,
        String localPath,
        String qualifier,
        Confidence confidence
    ) {
        return GraphFact.active(
            new FactKey(source, relationType, target, qualifier),
            new EvidenceKey(SourceType.ASM, "class-file", evidencePath.toString(), 0, 0, localPath + ":" + qualifier),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            confidence,
            SourceType.ASM
        );
    }

    private record EndpointMapping(String httpMethod, String path) {
    }

    private record ClassFileMethod(String name, String sourceDescriptor, int accessFlags, List<ClassFileAnnotation> annotations, List<MethodCall> calls) {
        private static final int ACC_BRIDGE = 0x0040;
        private static final int ACC_SYNTHETIC = 0x1000;

        private ClassFileMethod {
            annotations = List.copyOf(annotations);
            calls = List.copyOf(calls);
        }

        boolean bridge() {
            return (accessFlags & ACC_BRIDGE) != 0;
        }

        boolean synthetic() {
            return (accessFlags & ACC_SYNTHETIC) != 0;
        }
    }

    private record ClassFileAnnotation(String name, Map<String, String> values) {
        private ClassFileAnnotation {
            values = Map.copyOf(values);
        }
    }

    private record ClassFileField(String name, String sourceDescriptor, int accessFlags, List<ClassFileAnnotation> annotations) {
        private static final int ACC_STATIC = 0x0008;
        private static final int ACC_SYNTHETIC = 0x1000;

        private ClassFileField {
            annotations = List.copyOf(annotations);
        }

        List<String> annotationNames() {
            return annotations.stream().map(ClassFileAnnotation::name).toList();
        }

        boolean staticField() {
            return (accessFlags & ACC_STATIC) != 0;
        }

        boolean synthetic() {
            return (accessFlags & ACC_SYNTHETIC) != 0;
        }
    }

    private record MethodCall(String ownerClassName, String methodName, String sourceDescriptor, String qualifier, Confidence confidence) {
    }

    private record ClassFileInfo(
        String className,
        String superClassName,
        int accessFlags,
        List<String> interfaceNames,
        List<ClassFileAnnotation> annotations,
        List<ClassFileField> fields,
        List<ClassFileMethod> methods
    ) {
        private static final int ACC_INTERFACE = 0x0200;
        private static final int ACC_ANNOTATION = 0x2000;
        private static final int ACC_ENUM = 0x4000;

        org.sainm.codeatlas.graph.model.SymbolKind symbolKind() {
            if ((accessFlags & ACC_ANNOTATION) != 0) {
                return org.sainm.codeatlas.graph.model.SymbolKind.ANNOTATION;
            }
            if ((accessFlags & ACC_ENUM) != 0) {
                return org.sainm.codeatlas.graph.model.SymbolKind.ENUM;
            }
            if ((accessFlags & ACC_INTERFACE) != 0) {
                return org.sainm.codeatlas.graph.model.SymbolKind.INTERFACE;
            }
            return org.sainm.codeatlas.graph.model.SymbolKind.CLASS;
        }

        List<String> annotationNames() {
            return annotations.stream().map(ClassFileAnnotation::name).toList();
        }

        static ClassFileInfo read(byte[] bytes) throws IOException {
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (input.readInt() != 0xCAFEBABE) {
                    throw new IOException("Not a Java class file");
                }
                input.readUnsignedShort();
                input.readUnsignedShort();
                ConstantPool constantPool = ConstantPool.read(input);
                int accessFlags = input.readUnsignedShort();
                String className = constantPool.className(input.readUnsignedShort());
                int superIndex = input.readUnsignedShort();
                String superClassName = superIndex == 0 ? null : constantPool.className(superIndex);
                int interfaceCount = input.readUnsignedShort();
                List<String> interfaces = new ArrayList<>();
                for (int i = 0; i < interfaceCount; i++) {
                    interfaces.add(constantPool.className(input.readUnsignedShort()));
                }
                List<ClassFileField> fields = readFields(input, constantPool);
                List<ClassFileMethod> methods = readMethods(input, constantPool);
                List<ClassFileAnnotation> annotations = readClassAttributes(input, constantPool);
                return new ClassFileInfo(className, superClassName, accessFlags, interfaces, annotations, fields, methods);
            }
        }

        private static List<ClassFileField> readFields(DataInputStream input, ConstantPool constantPool) throws IOException {
            int fields = input.readUnsignedShort();
            List<ClassFileField> result = new ArrayList<>();
            for (int i = 0; i < fields; i++) {
                int accessFlags = input.readUnsignedShort();
                String name = constantPool.utf8(input.readUnsignedShort());
                String descriptor = constantPool.utf8(input.readUnsignedShort());
                List<ClassFileAnnotation> annotations = readAnnotationMemberAttributes(input, constantPool);
                result.add(new ClassFileField(name, DescriptorParser.toSourceTypeName(descriptor), accessFlags, annotations));
            }
            return result;
        }

        private static List<ClassFileMethod> readMethods(DataInputStream input, ConstantPool constantPool) throws IOException {
            int methodCount = input.readUnsignedShort();
            List<ClassFileMethod> methods = new ArrayList<>();
            for (int i = 0; i < methodCount; i++) {
                int accessFlags = input.readUnsignedShort();
                String name = constantPool.utf8(input.readUnsignedShort());
                String descriptor = constantPool.utf8(input.readUnsignedShort());
                MethodAttributeInfo attributes = readMethodAttributes(input, constantPool);
                methods.add(new ClassFileMethod(name, DescriptorParser.toSourceDescriptor(descriptor), accessFlags, attributes.annotations(), attributes.calls()));
            }
            return methods;
        }

        private static List<ClassFileAnnotation> readClassAttributes(DataInputStream input, ConstantPool constantPool) throws IOException {
            int attributes = input.readUnsignedShort();
            List<ClassFileAnnotation> annotations = new ArrayList<>();
            for (int i = 0; i < attributes; i++) {
                String name = constantPool.utf8(input.readUnsignedShort());
                int length = input.readInt();
                if ("RuntimeVisibleAnnotations".equals(name) || "RuntimeInvisibleAnnotations".equals(name)) {
                    byte[] attribute = input.readNBytes(length);
                    annotations.addAll(readAnnotationAttribute(attribute, constantPool));
                } else {
                    input.skipNBytes(length);
                }
            }
            return annotations;
        }

        private static List<ClassFileAnnotation> readAnnotationMemberAttributes(DataInputStream input, ConstantPool constantPool) throws IOException {
            int attributes = input.readUnsignedShort();
            List<ClassFileAnnotation> annotations = new ArrayList<>();
            for (int i = 0; i < attributes; i++) {
                String name = constantPool.utf8(input.readUnsignedShort());
                int length = input.readInt();
                if ("RuntimeVisibleAnnotations".equals(name) || "RuntimeInvisibleAnnotations".equals(name)) {
                    byte[] attribute = input.readNBytes(length);
                    annotations.addAll(readAnnotationAttribute(attribute, constantPool));
                } else {
                    input.skipNBytes(length);
                }
            }
            return annotations;
        }

        private static List<ClassFileAnnotation> readAnnotationAttribute(byte[] attribute, ConstantPool constantPool) throws IOException {
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(attribute))) {
                int annotationCount = input.readUnsignedShort();
                List<ClassFileAnnotation> annotations = new ArrayList<>();
                for (int i = 0; i < annotationCount; i++) {
                    String annotationName = descriptorToClassName(constantPool.utf8(input.readUnsignedShort()));
                    int pairs = input.readUnsignedShort();
                    java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
                    for (int pair = 0; pair < pairs; pair++) {
                        String elementName = constantPool.utf8(input.readUnsignedShort());
                        String elementValue = readElementValue(input, constantPool);
                        if (!elementValue.isBlank()) {
                            values.put(elementName, elementValue);
                        }
                    }
                    annotations.add(new ClassFileAnnotation(annotationName, values));
                }
                return annotations;
            }
        }

        private static String descriptorToClassName(String descriptor) {
            if (descriptor != null && descriptor.startsWith("L") && descriptor.endsWith(";")) {
                return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
            }
            return descriptor == null ? "" : descriptor.replace('/', '.');
        }

        private static String readElementValue(DataInputStream input, ConstantPool constantPool) throws IOException {
            int tag = input.readUnsignedByte();
            return switch (tag) {
                case 's' -> constantPool.utf8(input.readUnsignedShort());
                case 'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z' -> {
                    input.readUnsignedShort();
                    yield "";
                }
                case 'e' -> {
                    input.readUnsignedShort();
                    yield constantPool.utf8(input.readUnsignedShort());
                }
                case 'c' -> descriptorToClassName(constantPool.utf8(input.readUnsignedShort()));
                case '@' -> {
                    skipAnnotation(input);
                    yield "";
                }
                case '[' -> {
                    int values = input.readUnsignedShort();
                    List<String> items = new ArrayList<>();
                    for (int i = 0; i < values; i++) {
                        String item = readElementValue(input, constantPool);
                        if (!item.isBlank()) {
                            items.add(item);
                        }
                    }
                    yield String.join(",", items);
                }
                default -> throw new IOException("Unsupported annotation element tag: " + tag);
            };
        }

        private static void skipAnnotation(DataInputStream input) throws IOException {
            input.readUnsignedShort();
            int pairs = input.readUnsignedShort();
            for (int i = 0; i < pairs; i++) {
                input.readUnsignedShort();
                skipElementValue(input);
            }
        }

        private static void skipElementValue(DataInputStream input) throws IOException {
            int tag = input.readUnsignedByte();
            switch (tag) {
                case 'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z', 's', 'c' -> input.readUnsignedShort();
                case 'e' -> {
                    input.readUnsignedShort();
                    input.readUnsignedShort();
                }
                case '@' -> skipAnnotation(input);
                case '[' -> {
                    int values = input.readUnsignedShort();
                    for (int i = 0; i < values; i++) {
                        skipElementValue(input);
                    }
                }
                default -> throw new IOException("Unsupported annotation element tag: " + tag);
            }
        }

        private static MethodAttributeInfo readMethodAttributes(DataInputStream input, ConstantPool constantPool) throws IOException {
            int attributes = input.readUnsignedShort();
            List<MethodCall> calls = new ArrayList<>();
            List<ClassFileAnnotation> annotations = new ArrayList<>();
            for (int i = 0; i < attributes; i++) {
                String name = constantPool.utf8(input.readUnsignedShort());
                int length = input.readInt();
                if ("Code".equals(name)) {
                    byte[] attribute = input.readNBytes(length);
                    calls.addAll(readCodeAttribute(attribute, constantPool));
                } else if ("RuntimeVisibleAnnotations".equals(name) || "RuntimeInvisibleAnnotations".equals(name)) {
                    byte[] attribute = input.readNBytes(length);
                    annotations.addAll(readAnnotationAttribute(attribute, constantPool));
                } else {
                    input.skipNBytes(length);
                }
            }
            return new MethodAttributeInfo(annotations, calls);
        }

        private static List<MethodCall> readCodeAttribute(byte[] attribute, ConstantPool constantPool) throws IOException {
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(attribute))) {
                input.readUnsignedShort();
                input.readUnsignedShort();
                int codeLength = input.readInt();
                byte[] code = input.readNBytes(codeLength);
                return BytecodeInvocationScanner.scan(code, constantPool);
            }
        }

        private static void skipAttributes(DataInputStream input) throws IOException {
            int attributes = input.readUnsignedShort();
            for (int i = 0; i < attributes; i++) {
                input.readUnsignedShort();
                int length = input.readInt();
                input.skipNBytes(length);
            }
        }

        private record MethodAttributeInfo(List<ClassFileAnnotation> annotations, List<MethodCall> calls) {
            private MethodAttributeInfo {
                annotations = List.copyOf(annotations);
                calls = List.copyOf(calls);
            }
        }
    }

    private static final class ConstantPool {
        private final Object[] values;

        private ConstantPool(Object[] values) {
            this.values = values;
        }

        static ConstantPool read(DataInputStream input) throws IOException {
            int count = input.readUnsignedShort();
            Object[] values = new Object[count];
            for (int i = 1; i < count; i++) {
                int tag = input.readUnsignedByte();
                switch (tag) {
                    case 1 -> values[i] = input.readUTF();
                    case 3, 4 -> input.readInt();
                    case 5, 6 -> {
                        input.readLong();
                        i++;
                    }
                    case 7, 8, 16, 19, 20 -> values[i] = input.readUnsignedShort();
                    case 9 -> values[i] = new MemberRef(input.readUnsignedShort(), input.readUnsignedShort(), false);
                    case 10 -> values[i] = new MemberRef(input.readUnsignedShort(), input.readUnsignedShort(), false);
                    case 11 -> values[i] = new MemberRef(input.readUnsignedShort(), input.readUnsignedShort(), true);
                    case 12 -> values[i] = new NameAndType(input.readUnsignedShort(), input.readUnsignedShort());
                    case 17, 18 -> values[i] = new DynamicRef(input.readUnsignedShort(), input.readUnsignedShort());
                    case 15 -> {
                        input.readUnsignedByte();
                        input.readUnsignedShort();
                    }
                    default -> throw new IOException("Unsupported constant pool tag: " + tag);
                }
            }
            return new ConstantPool(values);
        }

        String className(int index) {
            Object value = values[index];
            if (!(value instanceof Integer nameIndex)) {
                throw new IllegalArgumentException("Invalid class constant index: " + index);
            }
            return utf8(nameIndex).replace('/', '.');
        }

        MethodCall methodCall(int index, int opcode) {
            Object value = values[index];
            if (!(value instanceof MemberRef memberRef)) {
                return null;
            }
            Object nameAndTypeValue = values[memberRef.nameAndTypeIndex()];
            if (!(nameAndTypeValue instanceof NameAndType nameAndType)) {
                return null;
            }
            String name = utf8(nameAndType.nameIndex());
            String descriptor = utf8(nameAndType.descriptorIndex());
            return new MethodCall(
                className(memberRef.classIndex()),
                name,
                DescriptorParser.toSourceDescriptor(descriptor),
                bytecodeQualifier(opcode, memberRef.interfaceMethod()),
                bytecodeConfidence(opcode)
            );
        }

        String utf8(int index) {
            Object value = values[index];
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException("Invalid utf8 constant index: " + index);
            }
            return text;
        }

        private static String bytecodeQualifier(int opcode, boolean interfaceMethod) {
            return switch (opcode) {
                case 0xb6 -> "bytecode-virtual";
                case 0xb7 -> "bytecode-special";
                case 0xb8 -> "bytecode-static";
                case 0xb9 -> interfaceMethod ? "bytecode-interface" : "bytecode-virtual";
                default -> "bytecode-call";
            };
        }

        private static Confidence bytecodeConfidence(int opcode) {
            return switch (opcode) {
                case 0xb6, 0xb9 -> Confidence.LIKELY;
                default -> Confidence.CERTAIN;
            };
        }

        private record MemberRef(int classIndex, int nameAndTypeIndex, boolean interfaceMethod) {
        }

        private record NameAndType(int nameIndex, int descriptorIndex) {
        }

        private record DynamicRef(int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
        }

    }

    private static final class BytecodeInvocationScanner {
        private BytecodeInvocationScanner() {
        }

        static List<MethodCall> scan(byte[] code, ConstantPool constantPool) {
            List<MethodCall> calls = new ArrayList<>();
            int offset = 0;
            while (offset < code.length) {
                int opcode = unsignedByte(code, offset);
                if (opcode == 0xb6 || opcode == 0xb7 || opcode == 0xb8) {
                    if (offset + 2 >= code.length) {
                        break;
                    }
                    MethodCall call = constantPool.methodCall(unsignedShort(code, offset + 1), opcode);
                    if (call != null) {
                        calls.add(call);
                    }
                    offset += 3;
                } else if (opcode == 0xb9) {
                    if (offset + 4 >= code.length) {
                        break;
                    }
                    MethodCall call = constantPool.methodCall(unsignedShort(code, offset + 1), opcode);
                    if (call != null) {
                        calls.add(call);
                    }
                    offset += 5;
                } else if (opcode == 0xaa) {
                    offset = nextTableSwitchOffset(code, offset);
                } else if (opcode == 0xab) {
                    offset = nextLookupSwitchOffset(code, offset);
                } else if (opcode == 0xc4) {
                    offset += wideLength(code, offset);
                } else {
                    offset += opcodeLength(opcode);
                }
            }
            return calls;
        }

        private static int opcodeLength(int opcode) {
            return switch (opcode) {
                case 0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19,
                    0x36, 0x37, 0x38, 0x39, 0x3a, 0xa9, 0xbc -> 2;
                case 0x11, 0x13, 0x14, 0x84, 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e,
                    0x9f, 0xa0, 0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8,
                    0xbb, 0xbd, 0xc0, 0xc1, 0xc6, 0xc7 -> 3;
                case 0xc5 -> 4;
                case 0xba, 0xc8, 0xc9 -> 5;
                default -> 1;
            };
        }

        private static int wideLength(byte[] code, int offset) {
            if (offset + 1 >= code.length) {
                return 1;
            }
            return unsignedByte(code, offset + 1) == 0x84 ? 6 : 4;
        }

        private static int nextTableSwitchOffset(byte[] code, int offset) {
            int base = alignedSwitchBase(offset);
            if (base + 11 >= code.length) {
                return code.length;
            }
            int low = readInt(code, base + 4);
            int high = readInt(code, base + 8);
            long entries = (long) high - low + 1;
            if (entries < 0) {
                return code.length;
            }
            long next = (long) base + 12 + entries * 4;
            return next > code.length ? code.length : (int) next;
        }

        private static int nextLookupSwitchOffset(byte[] code, int offset) {
            int base = alignedSwitchBase(offset);
            if (base + 7 >= code.length) {
                return code.length;
            }
            int pairs = readInt(code, base + 4);
            if (pairs < 0) {
                return code.length;
            }
            long next = (long) base + 8 + (long) pairs * 8;
            return next > code.length ? code.length : (int) next;
        }

        private static int alignedSwitchBase(int offset) {
            int afterOpcode = offset + 1;
            int padding = (4 - (afterOpcode % 4)) % 4;
            return afterOpcode + padding;
        }

        private static int unsignedByte(byte[] code, int offset) {
            return code[offset] & 0xff;
        }

        private static int unsignedShort(byte[] code, int offset) {
            return ((code[offset] & 0xff) << 8) | (code[offset + 1] & 0xff);
        }

        private static int readInt(byte[] code, int offset) {
            return ((code[offset] & 0xff) << 24)
                | ((code[offset + 1] & 0xff) << 16)
                | ((code[offset + 2] & 0xff) << 8)
                | (code[offset + 3] & 0xff);
        }
    }

    private static final class DescriptorParser {
        private final String descriptor;
        private int offset;

        private DescriptorParser(String descriptor) {
            this.descriptor = descriptor;
        }

        static String toSourceDescriptor(String descriptor) {
            DescriptorParser parser = new DescriptorParser(descriptor);
            return parser.methodDescriptor();
        }

        static String toSourceTypeName(String descriptor) {
            DescriptorParser parser = new DescriptorParser(descriptor);
            return parser.typeName();
        }

        private String methodDescriptor() {
            if (descriptor.charAt(offset) != '(') {
                throw new IllegalArgumentException("Method descriptor expected: " + descriptor);
            }
            offset++;
            List<String> parameters = new ArrayList<>();
            while (descriptor.charAt(offset) != ')') {
                parameters.add(typeName());
            }
            offset++;
            String returnType = typeName();
            return "(" + String.join(",", parameters) + "):" + returnType;
        }

        private String typeName() {
            int arrays = 0;
            while (descriptor.charAt(offset) == '[') {
                arrays++;
                offset++;
            }
            char type = descriptor.charAt(offset++);
            String name = switch (type) {
                case 'B' -> "byte";
                case 'C' -> "char";
                case 'D' -> "double";
                case 'F' -> "float";
                case 'I' -> "int";
                case 'J' -> "long";
                case 'S' -> "short";
                case 'Z' -> "boolean";
                case 'V' -> "void";
                case 'L' -> objectTypeName();
                default -> throw new IllegalArgumentException("Unsupported descriptor type: " + type);
            };
            return name + "[]".repeat(arrays);
        }

        private String objectTypeName() {
            int end = descriptor.indexOf(';', offset);
            String name = descriptor.substring(offset, end).replace('/', '.');
            offset = end + 1;
            return name;
        }
    }
}

