package org.sainm.codeatlas.analyzers.bytecode;

import java.util.List;

public record BytecodeMethodInfo(
        String ownerQualifiedName,
        String simpleName,
        String descriptor,
        List<String> annotations,
        String originPath) {
    public BytecodeMethodInfo {
        BytecodeClassInfo.requireNonBlank(ownerQualifiedName, "ownerQualifiedName");
        BytecodeClassInfo.requireNonBlank(simpleName, "simpleName");
        BytecodeClassInfo.requireNonBlank(descriptor, "descriptor");
        annotations = BytecodeClassInfo.copySorted(annotations);
        originPath = originPath == null ? "" : originPath.replace('\\', '/');
    }
}
