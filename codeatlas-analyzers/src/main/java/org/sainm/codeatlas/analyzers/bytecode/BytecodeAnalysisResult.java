package org.sainm.codeatlas.analyzers.bytecode;

import java.util.List;

public record BytecodeAnalysisResult(
        List<BytecodeClassInfo> classes,
        List<BytecodeMethodInfo> methods,
        List<BytecodeFieldInfo> fields,
        List<BytecodeMethodCallInfo> methodCalls) {
    public BytecodeAnalysisResult {
        classes = List.copyOf(classes == null ? List.of() : classes);
        methods = List.copyOf(methods == null ? List.of() : methods);
        fields = List.copyOf(fields == null ? List.of() : fields);
        methodCalls = List.copyOf(methodCalls == null ? List.of() : methodCalls);
    }
}
