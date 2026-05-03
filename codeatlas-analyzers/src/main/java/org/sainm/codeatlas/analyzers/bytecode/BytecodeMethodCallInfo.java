package org.sainm.codeatlas.analyzers.bytecode;

public record BytecodeMethodCallInfo(
        String ownerQualifiedName,
        String ownerMethodName,
        String ownerDescriptor,
        String targetQualifiedName,
        String targetMethodName,
        String targetDescriptor,
        String originPath) {
    public BytecodeMethodCallInfo {
        BytecodeClassInfo.requireNonBlank(ownerQualifiedName, "ownerQualifiedName");
        BytecodeClassInfo.requireNonBlank(ownerMethodName, "ownerMethodName");
        BytecodeClassInfo.requireNonBlank(ownerDescriptor, "ownerDescriptor");
        BytecodeClassInfo.requireNonBlank(targetQualifiedName, "targetQualifiedName");
        BytecodeClassInfo.requireNonBlank(targetMethodName, "targetMethodName");
        BytecodeClassInfo.requireNonBlank(targetDescriptor, "targetDescriptor");
        originPath = originPath == null ? "" : originPath.replace('\\', '/');
    }
}
