package org.sainm.codeatlas.analyzers;

import org.sainm.codeatlas.analyzers.java.SpoonJavaAnalyzer;
import org.sainm.codeatlas.analyzers.java.SpoonVariableTraceAnalyzer;
import org.sainm.codeatlas.analyzers.bytecode.ClassFileAnalyzer;
import org.sainm.codeatlas.analyzers.java.RequestParameterGraphBuilder;
import org.sainm.codeatlas.analyzers.jsp.JspFormAnalyzer;
import org.sainm.codeatlas.analyzers.seasar.SeasarDiconAnalyzer;
import org.sainm.codeatlas.analyzers.spring.SpringBeanAnalyzer;
import org.sainm.codeatlas.analyzers.spring.SpringMvcAnalyzer;
import org.sainm.codeatlas.analyzers.sql.JdbcSqlAnalyzer;
import org.sainm.codeatlas.analyzers.sql.MyBatisMapperInterfaceAnalyzer;
import org.sainm.codeatlas.analyzers.sql.MyBatisXmlAnalyzer;
import org.sainm.codeatlas.analyzers.struts.StrutsActionFormAnalyzer;
import org.sainm.codeatlas.analyzers.struts.StrutsConfigAnalysisResult;
import org.sainm.codeatlas.analyzers.struts.StrutsConfigAnalyzer;
import org.sainm.codeatlas.analyzers.struts.StrutsPluginInitXmlAnalyzer;
import org.sainm.codeatlas.analyzers.struts.StrutsWebXmlAnalyzer;
import org.sainm.codeatlas.analyzers.struts.StrutsTilesAnalyzer;
import org.sainm.codeatlas.analyzers.struts.StrutsValidatorAnalyzer;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CodeAtlasProjectAnalyzer {
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern CLASS_DECLARATION = Pattern.compile("\\bclass\\s+(\\w+)\\b");
    private static final Pattern LOOKUP_PUT = Pattern.compile("\\.put\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*\\)");

    private final SpoonJavaAnalyzer javaAnalyzer = new SpoonJavaAnalyzer();
    private final ClassFileAnalyzer classFileAnalyzer = new ClassFileAnalyzer();
    private final SpoonVariableTraceAnalyzer variableTraceAnalyzer = new SpoonVariableTraceAnalyzer();
    private final RequestParameterGraphBuilder requestParameterGraphBuilder = new RequestParameterGraphBuilder();
    private final SpringMvcAnalyzer springMvcAnalyzer = new SpringMvcAnalyzer();
    private final SpringBeanAnalyzer springBeanAnalyzer = new SpringBeanAnalyzer();
    private final MyBatisMapperInterfaceAnalyzer myBatisMapperInterfaceAnalyzer = new MyBatisMapperInterfaceAnalyzer();
    private final JdbcSqlAnalyzer jdbcSqlAnalyzer = new JdbcSqlAnalyzer();
    private final StrutsActionFormAnalyzer strutsActionFormAnalyzer = new StrutsActionFormAnalyzer();
    private final StrutsConfigAnalyzer strutsConfigAnalyzer = new StrutsConfigAnalyzer();
    private final StrutsWebXmlAnalyzer strutsWebXmlAnalyzer = new StrutsWebXmlAnalyzer();
    private final StrutsTilesAnalyzer strutsTilesAnalyzer = new StrutsTilesAnalyzer();
    private final StrutsValidatorAnalyzer strutsValidatorAnalyzer = new StrutsValidatorAnalyzer();
    private final StrutsPluginInitXmlAnalyzer strutsPluginInitXmlAnalyzer = new StrutsPluginInitXmlAnalyzer();
    private final JspFormAnalyzer jspFormAnalyzer = new JspFormAnalyzer();
    private final MyBatisXmlAnalyzer myBatisXmlAnalyzer = new MyBatisXmlAnalyzer();
    private final SeasarDiconAnalyzer seasarDiconAnalyzer = new SeasarDiconAnalyzer();

    public ProjectAnalysisResult analyze(AnalyzerScope scope, String projectKey) {
        List<Path> files = scanFiles(scope.root());
        List<Path> javaFiles = files.stream().filter(path -> path.toString().endsWith(".java")).toList();
        List<Path> binaryFiles = files.stream()
            .filter(path -> {
                String normalized = path.toString().replace('\\', '/');
                return normalized.endsWith(".jar") || normalized.endsWith(".class");
            })
            .toList();
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphFact> facts = new ArrayList<>();
        List<GraphNode> javaMethodNodes = new ArrayList<>();
        Map<String, String> superClassByType = new LinkedHashMap<>();
        Map<String, List<LookupDispatchMethodMapping>> lookupDispatchMappings = lookupDispatchMappings(javaFiles);

        if (!javaFiles.isEmpty()) {
            var javaResult = javaAnalyzer.analyze(scope, projectKey, "src/main/java", javaFiles);
            nodes.addAll(javaResult.nodes());
            facts.addAll(javaResult.facts());
            superClassByType.putAll(superClassByType(javaResult.facts()));
            javaMethodNodes.addAll(javaResult.nodes().stream()
                .filter(node -> node.symbolId().kind() == SymbolKind.METHOD)
                .toList());

            var springResult = springMvcAnalyzer.analyze(scope, projectKey, "src/main/java", javaFiles);
            nodes.addAll(springResult.nodes());
            facts.addAll(springResult.facts());

            var springBeanResult = springBeanAnalyzer.analyze(scope, projectKey, "src/main/java", javaFiles);
            nodes.addAll(springBeanResult.nodes());
            facts.addAll(springBeanResult.facts());

            var myBatisMapperInterfaceResult = myBatisMapperInterfaceAnalyzer.analyze(scope, projectKey, "src/main/java", javaFiles);
            nodes.addAll(myBatisMapperInterfaceResult.nodes());
            facts.addAll(myBatisMapperInterfaceResult.facts());

            var jdbcSqlResult = jdbcSqlAnalyzer.analyze(scope, projectKey, "src/main/java", javaFiles);
            nodes.addAll(jdbcSqlResult.nodes());
            facts.addAll(jdbcSqlResult.facts());

            var strutsActionFormResult = strutsActionFormAnalyzer.analyze(scope, projectKey, "src/main/java", javaFiles);
            nodes.addAll(strutsActionFormResult.nodes());
            facts.addAll(strutsActionFormResult.facts());

            var requestParameterResult = requestParameterGraphBuilder.build(
                scope,
                projectKey,
                variableTraceAnalyzer.analyze(scope, projectKey, "src/main/java", javaFiles)
            );
            nodes.addAll(requestParameterResult.nodes());
            facts.addAll(requestParameterResult.facts());
        }

        if (!binaryFiles.isEmpty()) {
            var classFileResult = classFileAnalyzer.analyze(scope, projectKey, "src/main/java", binaryFiles);
            nodes.addAll(classFileResult.nodes());
            facts.addAll(classFileResult.facts());
        }

        Map<Path, List<String>> strutsConfigs = strutsConfigFiles(files);
        for (Path file : files) {
            String normalized = file.toString().replace('\\', '/');
            if (strutsConfigs.containsKey(file)) {
                for (String modulePrefix : strutsConfigs.get(file)) {
                    var result = strutsConfigAnalyzer.analyze(scope, projectKey, "src/main/webapp", file, modulePrefix);
                    nodes.addAll(result.nodes());
                    facts.addAll(result.facts());
                    var pluginInitXmlResult = strutsPluginInitXmlAnalyzer.analyze(scope, projectKey, "src/main/webapp", file, result);
                    nodes.addAll(pluginInitXmlResult.nodes());
                    facts.addAll(pluginInitXmlResult.facts());
                    addDispatchMethodCandidates(
                        scope,
                        projectKey,
                        "src/main/webapp",
                        file,
                        modulePrefix,
                        result,
                        javaMethodNodes,
                        superClassByType,
                        lookupDispatchMappings,
                        facts
                    );
                }
            } else if (isTilesDefinitionFile(file)) {
                var result = strutsTilesAnalyzer.analyze(scope, projectKey, "src/main/webapp", file);
                nodes.addAll(result.nodes());
                facts.addAll(result.facts());
            } else if (isValidatorFile(file)) {
                var result = strutsValidatorAnalyzer.analyze(scope, projectKey, "src/main/webapp", file);
                nodes.addAll(result.nodes());
                facts.addAll(result.facts());
            } else if (normalized.endsWith(".jsp")) {
                var result = jspFormAnalyzer.analyze(scope, projectKey, "src/main/webapp", file);
                nodes.addAll(result.nodes());
                facts.addAll(result.facts());
            } else if (normalized.endsWith("Mapper.xml")) {
                var result = myBatisXmlAnalyzer.analyze(scope, projectKey, "src/main/resources", "src/main/java", file);
                nodes.addAll(result.nodes());
                facts.addAll(result.facts());
            } else if (normalized.endsWith(".dicon")) {
                var result = seasarDiconAnalyzer.analyze(scope, projectKey, "src/main/resources", file);
                nodes.addAll(result.nodes());
                facts.addAll(result.facts());
            }
        }

        return new ProjectAnalysisResult(nodes, facts);
    }

    private boolean isTilesDefinitionFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return fileName.endsWith(".xml") && fileName.contains("tiles");
    }

    private boolean isValidatorFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return fileName.endsWith(".xml") && (fileName.equals("validation.xml") || fileName.contains("validator"));
    }

    private Map<Path, List<String>> strutsConfigFiles(List<Path> files) {
        Map<Path, List<String>> configs = new LinkedHashMap<>();
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String normalized = file.toString().replace('\\', '/');
            if (fileName.startsWith("struts-config") && fileName.endsWith(".xml")) {
                addStrutsConfig(configs, file, "");
            }
            if (normalized.endsWith("/WEB-INF/web.xml")) {
                Path webRoot = file.getParent().getParent();
                for (var moduleConfig : strutsWebXmlAnalyzer.analyze(file).moduleConfigs()) {
                    Path config = resolveWebLocation(webRoot, moduleConfig.configLocation());
                    if (java.nio.file.Files.exists(config)) {
                        addStrutsConfig(configs, config, moduleConfig.modulePrefix());
                    }
                }
            }
        }
        return configs;
    }

    private void addStrutsConfig(Map<Path, List<String>> configs, Path config, String modulePrefix) {
        List<String> prefixes = configs.computeIfAbsent(config, ignored -> new ArrayList<>());
        if (!prefixes.contains(modulePrefix)) {
            prefixes.add(modulePrefix);
        }
    }

    private Path resolveWebLocation(Path webRoot, String location) {
        String normalized = location == null ? "" : location.trim().replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return webRoot.resolve(normalized).toAbsolutePath().normalize();
    }

    private void addDispatchMethodCandidates(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path configXml,
        String modulePrefix,
        StrutsConfigAnalysisResult strutsResult,
        List<GraphNode> javaMethodNodes,
        Map<String, String> superClassByType,
        Map<String, List<LookupDispatchMethodMapping>> lookupDispatchMappings,
        List<GraphFact> facts
    ) {
        for (var mapping : strutsResult.actionMappings()) {
            SymbolId actionPath = StrutsConfigAnalyzer.actionPath(
                projectKey,
                scope.moduleKey(),
                sourceRootKey,
                StrutsConfigAnalyzer.moduleActionPath(modulePrefix, mapping.path())
            );
            Set<String> actionTypeHierarchy = actionTypeHierarchy(mapping.type(), superClassByType);
            if (mapping.type() == null || mapping.parameter() == null) {
                if (mapping.type() != null) {
                    addStandardActionExecuteCandidate(scope, configXml, mapping, actionPath, actionTypeHierarchy, javaMethodNodes, facts);
                }
                continue;
            }
            javaMethodNodes.stream()
                .map(GraphNode::symbolId)
                .filter(method -> actionTypeHierarchy.contains(method.ownerQualifiedName()))
                .filter(this::isStrutsDispatchMethod)
                .forEach(method -> facts.add(GraphFact.active(
                    new FactKey(actionPath, RelationType.ROUTES_TO, method, "dispatch-method-candidate:" + mapping.parameter() + ":" + method.memberName()),
                    new EvidenceKey(SourceType.STRUTS_CONFIG, "struts-dispatch-method", configXml.toString(), 0, 0, mapping.parameter()),
                    scope.projectId(),
                    scope.snapshotId(),
                    scope.analysisRunId(),
                    scope.scopeKey(),
                    Confidence.LIKELY,
                    SourceType.STRUTS_CONFIG
                )));
            for (LookupDispatchMethodMapping lookupMapping : lookupMappingsFor(actionTypeHierarchy, lookupDispatchMappings)) {
                javaMethodNodes.stream()
                    .map(GraphNode::symbolId)
                    .filter(method -> actionTypeHierarchy.contains(method.ownerQualifiedName()))
                    .filter(method -> method.memberName().equals(lookupMapping.methodName()))
                    .filter(this::isStrutsDispatchMethod)
                    .forEach(method -> facts.add(GraphFact.active(
                        new FactKey(
                            actionPath,
                            RelationType.ROUTES_TO,
                            method,
                            "lookup-dispatch-method:" + lookupMapping.resourceKey() + ":" + lookupMapping.methodName()
                        ),
                        new EvidenceKey(SourceType.STRUTS_CONFIG, "struts-lookup-dispatch-method", configXml.toString(), 0, 0, lookupMapping.resourceKey()),
                        scope.projectId(),
                        scope.snapshotId(),
                        scope.analysisRunId(),
                        scope.scopeKey(),
                        Confidence.LIKELY,
                        SourceType.STRUTS_CONFIG
                    )));
            }
        }
    }

    private void addStandardActionExecuteCandidate(
        AnalyzerScope scope,
        Path configXml,
        org.sainm.codeatlas.analyzers.struts.StrutsActionMapping mapping,
        SymbolId actionPath,
        Set<String> actionTypeHierarchy,
        List<GraphNode> javaMethodNodes,
        List<GraphFact> facts
    ) {
        javaMethodNodes.stream()
            .map(GraphNode::symbolId)
            .filter(method -> actionTypeHierarchy.contains(method.ownerQualifiedName()))
            .filter(method -> method.memberName().equals("execute"))
            .filter(this::isStrutsDispatchMethod)
            .forEach(method -> facts.add(GraphFact.active(
                new FactKey(actionPath, RelationType.ROUTES_TO, method, "struts-action-execute"),
                new EvidenceKey(SourceType.STRUTS_CONFIG, "struts-action-execute", configXml.toString(), 0, 0, mapping.path()),
                scope.projectId(),
                scope.snapshotId(),
                scope.analysisRunId(),
                scope.scopeKey(),
                Confidence.LIKELY,
                SourceType.STRUTS_CONFIG
            )));
    }

    private Map<String, String> superClassByType(List<GraphFact> facts) {
        Map<String, String> result = new LinkedHashMap<>();
        for (GraphFact fact : facts) {
            FactKey factKey = fact.factKey();
            if (factKey.relationType() != RelationType.EXTENDS) {
                continue;
            }
            if (factKey.source().kind() != SymbolKind.CLASS || factKey.target().ownerQualifiedName() == null) {
                continue;
            }
            result.put(factKey.source().ownerQualifiedName(), factKey.target().ownerQualifiedName());
        }
        return result;
    }

    private Set<String> actionTypeHierarchy(String actionType, Map<String, String> superClassByType) {
        LinkedHashSet<String> hierarchy = new LinkedHashSet<>();
        String current = actionType;
        while (current != null && !current.isBlank() && hierarchy.add(current)) {
            current = superClassByType.get(current);
        }
        return hierarchy;
    }

    private List<LookupDispatchMethodMapping> lookupMappingsFor(
        Set<String> actionTypeHierarchy,
        Map<String, List<LookupDispatchMethodMapping>> lookupDispatchMappings
    ) {
        List<LookupDispatchMethodMapping> result = new ArrayList<>();
        for (String type : actionTypeHierarchy) {
            result.addAll(lookupDispatchMappings.getOrDefault(type, List.of()));
        }
        return result;
    }

    private Map<String, List<LookupDispatchMethodMapping>> lookupDispatchMappings(List<Path> javaFiles) {
        Map<String, List<LookupDispatchMethodMapping>> mappings = new LinkedHashMap<>();
        for (Path javaFile : javaFiles) {
            String source = readSource(javaFile);
            String packageName = firstGroup(PACKAGE_DECLARATION.matcher(source));
            Matcher classMatcher = CLASS_DECLARATION.matcher(source);
            while (classMatcher.find()) {
                String className = packageName == null ? classMatcher.group(1) : packageName + "." + classMatcher.group(1);
                String methodBody = methodBody(source, "getKeyMethodMap", classMatcher.end());
                if (methodBody == null) {
                    continue;
                }
                Matcher putMatcher = LOOKUP_PUT.matcher(methodBody);
                while (putMatcher.find()) {
                    mappings.computeIfAbsent(className, ignored -> new ArrayList<>())
                        .add(new LookupDispatchMethodMapping(putMatcher.group(1), putMatcher.group(2)));
                }
            }
        }
        return mappings;
    }

    private String readSource(Path javaFile) {
        try {
            return Files.readString(javaFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read Java source: " + javaFile, exception);
        }
    }

    private String firstGroup(Matcher matcher) {
        return matcher.find() ? matcher.group(1) : null;
    }

    private String methodBody(String source, String methodName, int fromIndex) {
        int nameIndex = source.indexOf(methodName, fromIndex);
        if (nameIndex < 0) {
            return null;
        }
        int openBrace = source.indexOf('{', nameIndex);
        if (openBrace < 0) {
            return null;
        }
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBrace + 1, i);
                }
            }
        }
        return null;
    }

    private boolean isStrutsDispatchMethod(SymbolId method) {
        String descriptor = method.descriptor();
        if (descriptor == null || method.memberName() == null || method.memberName().startsWith("<")) {
            return false;
        }
        return descriptor.contains("ActionMapping")
            && descriptor.contains("ActionForm")
            && descriptor.contains("HttpServletRequest")
            && descriptor.contains("HttpServletResponse")
            && descriptor.contains("ActionForward");
    }

    private List<Path> scanFiles(Path root) {
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan project root: " + root, exception);
        }
    }

    private record LookupDispatchMethodMapping(String resourceKey, String methodName) {
    }
}
