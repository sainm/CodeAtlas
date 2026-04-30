package org.sainm.codeatlas.analyzers.jsp;

import org.apache.jasper.JspC;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.compiler.BeanRepository;
import org.apache.jasper.compiler.Compiler;
import org.apache.jasper.compiler.ErrorDispatcher;
import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.Node;
import org.apache.jasper.compiler.ParserController;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.TldCache;
import org.apache.jasper.servlet.JspCServletContext;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.PageInfo;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.xml.sax.Attributes;

final class ApacheJasperJspSemanticExtractor {
    JspSemanticAnalysis extract(Path jspFile, WebAppContext context) {
        if (jspFile == null || context == null || context.webRoot() == null || !Files.exists(jspFile)) {
            return null;
        }
        try {
            Path webRoot = context.webRoot().toAbsolutePath().normalize();
            Path jspPath = jspFile.toAbsolutePath().normalize();
            if (!jspPath.startsWith(webRoot)) {
                return null;
            }
            String jspUri = "/" + webRoot.relativize(jspPath).toString().replace('\\', '/');
            JspC options = new JspC();
            options.setUriroot(webRoot.toString());
            options.setOutputDir(Files.createTempDirectory("codeatlas-jasper").toString());
            options.setCompile(false);
            options.setFailOnError(true);
            options.setValidateTld(false);
            options.setValidateXml(false);
            options.setClassPath(classPath(context));

            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            JspCServletContext servletContext = new JspCServletContext(new PrintWriter(Writer.nullWriter()), webRoot.toUri().toURL(), loader, false, false);
            initializeOptions(options, servletContext);
            JspRuntimeContext runtimeContext = new JspRuntimeContext(servletContext, options);
            JspCompilationContext compilationContext = new JspCompilationContext(jspUri, options, servletContext, null, runtimeContext);
            compilationContext.setClassPath(options.getClassPath());
            compilationContext.setClassLoader(loader);
            Compiler compiler = compilationContext.createCompiler();
            compiler.init(compilationContext, null);
            initializeCompiler(compiler, compilationContext, loader);
            Node.Nodes nodes = parserController(compilationContext, compiler).parse(jspUri);
            JasperVisitor visitor = new JasperVisitor(context);
            nodes.visit(visitor);
            return visitor.analysis();
        } catch (Exception exception) {
            return null;
        }
    }

    private void initializeOptions(JspC options, JspCServletContext servletContext) throws ReflectiveOperationException {
        setField(options, "jspConfig", new JspConfig(servletContext));
        setField(options, "tagPluginManager", new TagPluginManager(servletContext));
        setField(options, "tldCache", new TldCache(servletContext, Map.of(), Map.of()));
    }

    private void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = JspC.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void initializeCompiler(Compiler compiler, JspCompilationContext context, ClassLoader loader) throws ReflectiveOperationException {
        ErrorDispatcher errorDispatcher = new ErrorDispatcher(true);
        setCompilerField(compiler, "errDispatcher", errorDispatcher);
        setCompilerField(compiler, "pageInfo", pageInfo(context, loader, errorDispatcher));
    }

    private PageInfo pageInfo(JspCompilationContext context, ClassLoader loader, ErrorDispatcher errorDispatcher) throws ReflectiveOperationException {
        Constructor<PageInfo> constructor = PageInfo.class.getDeclaredConstructor(BeanRepository.class, String.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new BeanRepository(loader, errorDispatcher), context.getJspFile(), context.isTagFile());
    }

    private void setCompilerField(Compiler compiler, String name, Object value) throws ReflectiveOperationException {
        Field field = Compiler.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(compiler, value);
    }

    private ParserController parserController(JspCompilationContext context, Compiler compiler) throws ReflectiveOperationException {
        Constructor<ParserController> constructor = ParserController.class.getDeclaredConstructor(JspCompilationContext.class, Compiler.class);
        constructor.setAccessible(true);
        return constructor.newInstance(context, compiler);
    }

    private String classPath(WebAppContext context) {
        List<String> entries = new ArrayList<>();
        for (Path path : context.classpathEntries()) {
            entries.add(path.toAbsolutePath().normalize().toString());
        }
        String runtime = System.getProperty("java.class.path");
        if (runtime != null && !runtime.isBlank()) {
            entries.add(runtime);
        }
        return String.join(java.io.File.pathSeparator, entries);
    }

    private static final class JasperVisitor extends Node.Visitor {
        private final WebAppContext context;
        private final List<JspDirective> directives = new ArrayList<>();
        private final List<JspAction> actions = new ArrayList<>();
        private final List<JspExpressionFragment> expressions = new ArrayList<>();
        private final List<JspTaglibReference> taglibs = new ArrayList<>();

        private JasperVisitor(WebAppContext context) {
            this.context = context;
        }

        private JspSemanticAnalysis analysis() {
            List<String> includes = directives.stream()
                .filter(directive -> directive.name().equals("include"))
                .map(directive -> directive.attributes().get("file"))
                .filter(value -> value != null && !value.isBlank())
                .toList();
            String encoding = directives.stream()
                .filter(directive -> directive.name().equals("page"))
                .map(directive -> firstNonBlank(directive.attributes().get("pageEncoding"), directive.attributes().get("contentType")))
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(context.defaultEncoding());
            return new JspSemanticAnalysis(
                directives,
                actions,
                expressions,
                taglibs,
                List.of(),
                includes,
                encoding,
                JspSemanticParserSource.APACHE_JASPER,
                "apache-jasper",
                null
            );
        }

        @Override
        public void visit(Node.PageDirective node) throws org.apache.jasper.JasperException {
            directives.add(new JspDirective("page", attrs(node), line(node)));
            visitBody(node);
        }

        @Override
        public void visit(Node.IncludeDirective node) throws org.apache.jasper.JasperException {
            directives.add(new JspDirective("include", attrs(node), line(node)));
            visitBody(node);
        }

        @Override
        public void visit(Node.TaglibDirective node) throws org.apache.jasper.JasperException {
            Map<String, String> attributes = attrs(node);
            directives.add(new JspDirective("taglib", attributes, line(node)));
            taglibs.add(TolerantJspSemanticExtractor.taglib(new JspDirective("taglib", attributes, line(node)), context));
            visitBody(node);
        }

        @Override
        public void visit(Node.IncludeAction node) throws org.apache.jasper.JasperException {
            addAction("jsp:include", node);
            visitBody(node);
        }

        @Override
        public void visit(Node.ForwardAction node) throws org.apache.jasper.JasperException {
            addAction("jsp:forward", node);
            visitBody(node);
        }

        @Override
        public void visit(Node.ParamAction node) throws org.apache.jasper.JasperException {
            addAction("jsp:param", node);
            visitBody(node);
        }

        @Override
        public void visit(Node.UseBean node) throws org.apache.jasper.JasperException {
            addAction("jsp:useBean", node);
            visitBody(node);
        }

        @Override
        public void visit(Node.CustomTag node) throws org.apache.jasper.JasperException {
            addAction(node.getQName(), node);
            visitBody(node);
        }

        @Override
        public void visit(Node.UninterpretedTag node) throws org.apache.jasper.JasperException {
            addAction(node.getQName(), node);
            visitBody(node);
        }

        @Override
        public void visit(Node.ELExpression node) {
            expressions.add(new JspExpressionFragment("EL", text(node), line(node)));
        }

        @Override
        public void visit(Node.Scriptlet node) {
            expressions.add(new JspExpressionFragment("SCRIPTLET", text(node), line(node)));
        }

        @Override
        public void visit(Node.Expression node) {
            expressions.add(new JspExpressionFragment("EXPRESSION", text(node), line(node)));
        }

        private void addAction(String name, Node node) {
            if (name != null && !name.isBlank()) {
                actions.add(new JspAction(name, attrs(node), line(node)));
            }
        }

        private Map<String, String> attrs(Node node) {
            return attrs(node.getAttributes());
        }

        private Map<String, String> attrs(Attributes attributes) {
            Map<String, String> result = new LinkedHashMap<>();
            if (attributes == null) {
                return result;
            }
            for (int i = 0; i < attributes.getLength(); i++) {
                result.put(attributes.getQName(i), attributes.getValue(i));
            }
            return result;
        }

        private int line(Node node) {
            return node.getStart() == null ? 0 : node.getStart().getLineNumber();
        }

        private String text(Node node) {
            return node.getText() == null ? "" : node.getText();
        }

        private String firstNonBlank(String first, String second) {
            if (first != null && !first.isBlank()) {
                return first;
            }
            return second;
        }
    }
}
