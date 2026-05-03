package org.sainm.codeatlas.analyzers.bytecode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Stream;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class BytecodeAnalyzer {
    private BytecodeAnalyzer() {
    }

    public static BytecodeAnalyzer defaults() {
        return new BytecodeAnalyzer();
    }

    public BytecodeAnalysisResult analyze(List<Path> roots) throws IOException {
        if (roots == null || roots.isEmpty()) {
            return new BytecodeAnalysisResult(List.of(), List.of(), List.of(), List.of());
        }
        MutableResult result = new MutableResult();
        for (Path root : roots) {
            if (Files.isDirectory(root)) {
                scanDirectory(root, result);
            } else if (root.toString().endsWith(".jar")) {
                scanJar(root, result);
            } else if (root.toString().endsWith(".class")) {
                try (InputStream input = Files.newInputStream(root)) {
                    scanClass(input, root.toString(), result);
                }
            }
        }
        return result.toResult();
    }

    private static void scanDirectory(Path root, MutableResult result) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path classFile : paths.filter(path -> path.toString().endsWith(".class")).toList()) {
                try (InputStream input = Files.newInputStream(classFile)) {
                    scanClass(input, root.relativize(classFile).toString(), result);
                }
            }
        }
    }

    private static void scanJar(Path jar, MutableResult result) throws IOException {
        try (JarInputStream input = new JarInputStream(Files.newInputStream(jar))) {
            JarEntry entry;
            while ((entry = input.getNextJarEntry()) != null) {
                if (entry.getName().endsWith(".class")) {
                    scanClass(input, jar.getFileName() + "!" + entry.getName(), result);
                }
            }
        }
    }

    private static void scanClass(InputStream input, String originPath, MutableResult result) throws IOException {
        ClassReader reader = new ClassReader(input);
        reader.accept(new ScanningClassVisitor(originPath, result), ClassReader.SKIP_FRAMES);
    }

    private static String className(String internalName) {
        return internalName == null ? "" : internalName.replace('/', '.');
    }

    private static String descriptorClassName(String descriptor) {
        return Type.getType(descriptor).getClassName();
    }

    private static final class ScanningClassVisitor extends ClassVisitor {
        private final String originPath;
        private final MutableResult result;
        private final List<String> classAnnotations = new ArrayList<>();
        private final List<String> interfaceNames = new ArrayList<>();
        private String className;
        private String superClassName;

        ScanningClassVisitor(String originPath, MutableResult result) {
            super(Opcodes.ASM9);
            this.originPath = originPath;
            this.result = result;
        }

        @Override
        public void visit(
                int version,
                int access,
                String name,
                String signature,
                String superName,
                String[] interfaces) {
            className = className(name);
            superClassName = className(superName);
            if (interfaces != null) {
                for (String interfaceName : interfaces) {
                    interfaceNames.add(className(interfaceName));
                }
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            classAnnotations.add(descriptorClassName(descriptor));
            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        public FieldVisitor visitField(
                int access,
                String name,
                String descriptor,
                String signature,
                Object value) {
            List<String> annotations = new ArrayList<>();
            return new FieldVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    annotations.add(descriptorClassName(descriptor));
                    return super.visitAnnotation(descriptor, visible);
                }

                @Override
                public void visitEnd() {
                    result.fields.add(new BytecodeFieldInfo(
                            className,
                            name,
                            descriptorClassName(descriptor),
                            descriptor,
                            annotations,
                            originPath));
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(
                int access,
                String name,
                String descriptor,
                String signature,
                String[] exceptions) {
            List<String> annotations = new ArrayList<>();
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    annotations.add(descriptorClassName(descriptor));
                    return super.visitAnnotation(descriptor, visible);
                }

                @Override
                public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String name,
                        String descriptor,
                        boolean isInterface) {
                    result.methodCalls.add(new BytecodeMethodCallInfo(
                            className,
                            thisMethodName(),
                            thisDescriptor(),
                            className(owner),
                            name,
                            descriptor,
                            originPath));
                }

                @Override
                public void visitEnd() {
                    result.methods.add(new BytecodeMethodInfo(className, name, descriptor, annotations, originPath));
                }

                private String thisMethodName() {
                    return name;
                }

                private String thisDescriptor() {
                    return descriptor;
                }
            };
        }

        @Override
        public void visitEnd() {
            result.classes.add(new BytecodeClassInfo(className, superClassName, interfaceNames, classAnnotations, originPath));
        }
    }

    private static final class MutableResult {
        private final List<BytecodeClassInfo> classes = new ArrayList<>();
        private final List<BytecodeMethodInfo> methods = new ArrayList<>();
        private final List<BytecodeFieldInfo> fields = new ArrayList<>();
        private final List<BytecodeMethodCallInfo> methodCalls = new ArrayList<>();

        BytecodeAnalysisResult toResult() {
            return new BytecodeAnalysisResult(
                    classes.stream().sorted(Comparator.comparing(BytecodeClassInfo::qualifiedName)).toList(),
                    methods.stream().sorted(Comparator.comparing(BytecodeMethodInfo::ownerQualifiedName)
                            .thenComparing(BytecodeMethodInfo::simpleName)
                            .thenComparing(BytecodeMethodInfo::descriptor)).toList(),
                    fields.stream().sorted(Comparator.comparing(BytecodeFieldInfo::ownerQualifiedName)
                            .thenComparing(BytecodeFieldInfo::simpleName)).toList(),
                    methodCalls.stream().sorted(Comparator.comparing(BytecodeMethodCallInfo::ownerQualifiedName)
                            .thenComparing(BytecodeMethodCallInfo::ownerMethodName)
                            .thenComparing(BytecodeMethodCallInfo::targetQualifiedName)
                            .thenComparing(BytecodeMethodCallInfo::targetMethodName)).toList());
        }
    }
}
