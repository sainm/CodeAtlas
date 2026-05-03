package org.sainm.codeatlas.analyzers.bytecode;

import java.util.List;

public record BytecodeFieldInfo(
        String ownerQualifiedName,
        String simpleName,
        String typeName,
        List<String> annotations,
        String originPath) {
    public BytecodeFieldInfo {
        BytecodeClassInfo.requireNonBlank(ownerQualifiedName, "ownerQualifiedName");
        BytecodeClassInfo.requireNonBlank(simpleName, "simpleName");
        typeName = typeName == null ? "" : typeName;
        annotations = BytecodeClassInfo.copySorted(annotations);
        originPath = originPath == null ? "" : originPath.replace('\\', '/');
    }
}
