package org.sainm.codeatlas.analyzers;

import org.sainm.codeatlas.analyzers.java.SpoonJavaAnalyzer;
import org.sainm.codeatlas.analyzers.java.SpoonVariableTraceAnalyzer;
import org.sainm.codeatlas.analyzers.java.RequestParameterGraphBuilder;
import org.sainm.codeatlas.analyzers.jsp.JspFormAnalyzer;
import org.sainm.codeatlas.analyzers.seasar.SeasarDiconAnalyzer;
import org.sainm.codeatlas.analyzers.spring.SpringBeanAnalyzer;
import org.sainm.codeatlas.analyzers.spring.SpringMvcAnalyzer;
import org.sainm.codeatlas.analyzers.sql.MyBatisMapperInterfaceAnalyzer;
import org.sainm.codeatlas.analyzers.sql.MyBatisXmlAnalyzer;
import org.sainm.codeatlas.analyzers.struts.StrutsActionFormAnalyzer;
import org.sainm.codeatlas.analyzers.struts.StrutsConfigAnalyzer;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CodeAtlasProjectAnalyzer {
    private final SpoonJavaAnalyzer javaAnalyzer = new SpoonJavaAnalyzer();
    private final SpoonVariableTraceAnalyzer variableTraceAnalyzer = new SpoonVariableTraceAnalyzer();
    private final RequestParameterGraphBuilder requestParameterGraphBuilder = new RequestParameterGraphBuilder();
    private final SpringMvcAnalyzer springMvcAnalyzer = new SpringMvcAnalyzer();
    private final SpringBeanAnalyzer springBeanAnalyzer = new SpringBeanAnalyzer();
    private final MyBatisMapperInterfaceAnalyzer myBatisMapperInterfaceAnalyzer = new MyBatisMapperInterfaceAnalyzer();
    private final StrutsActionFormAnalyzer strutsActionFormAnalyzer = new StrutsActionFormAnalyzer();
    private final StrutsConfigAnalyzer strutsConfigAnalyzer = new StrutsConfigAnalyzer();
    private final JspFormAnalyzer jspFormAnalyzer = new JspFormAnalyzer();
    private final MyBatisXmlAnalyzer myBatisXmlAnalyzer = new MyBatisXmlAnalyzer();
    private final SeasarDiconAnalyzer seasarDiconAnalyzer = new SeasarDiconAnalyzer();

    public ProjectAnalysisResult analyze(AnalyzerScope scope, String projectKey) {
        List<Path> files = scanFiles(scope.root());
        List<Path> javaFiles = files.stream().filter(path -> path.toString().endsWith(".java")).toList();
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphFact> facts = new ArrayList<>();

        if (!javaFiles.isEmpty()) {
            var javaResult = javaAnalyzer.analyze(scope, projectKey, "src/main/java", javaFiles);
            nodes.addAll(javaResult.nodes());
            facts.addAll(javaResult.facts());

            var springResult = springMvcAnalyzer.analyze(scope, projectKey, "src/main/java", javaFiles);
            nodes.addAll(springResult.nodes());
            facts.addAll(springResult.facts());

            var springBeanResult = springBeanAnalyzer.analyze(scope, projectKey, "src/main/java", javaFiles);
            nodes.addAll(springBeanResult.nodes());
            facts.addAll(springBeanResult.facts());

            var myBatisMapperInterfaceResult = myBatisMapperInterfaceAnalyzer.analyze(scope, projectKey, "src/main/java", javaFiles);
            nodes.addAll(myBatisMapperInterfaceResult.nodes());
            facts.addAll(myBatisMapperInterfaceResult.facts());

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

        for (Path file : files) {
            String normalized = file.toString().replace('\\', '/');
            if (normalized.endsWith("struts-config.xml")) {
                var result = strutsConfigAnalyzer.analyze(scope, projectKey, "src/main/webapp", file);
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

    private List<Path> scanFiles(Path root) {
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan project root: " + root, exception);
        }
    }
}
