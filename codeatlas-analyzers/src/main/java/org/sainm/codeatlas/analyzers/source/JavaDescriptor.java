package org.sainm.codeatlas.analyzers.source;

import java.util.List;

import spoon.reflect.reference.CtTypeReference;

final class JavaDescriptor {
    private JavaDescriptor() {
    }

    static String methodDescriptor(List<? extends CtTypeReference<?>> parameterTypes, CtTypeReference<?> returnType) {
        StringBuilder descriptor = new StringBuilder("(");
        for (CtTypeReference<?> parameterType : parameterTypes) {
            descriptor.append(typeDescriptor(parameterType));
        }
        descriptor.append(")");
        descriptor.append(typeDescriptor(returnType));
        return descriptor.toString();
    }

    static String typeDescriptor(CtTypeReference<?> type) {
        if (type == null) {
            return "V";
        }
        String qualifiedName = type.getQualifiedName();
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return "Ljava/lang/Object;";
        }
        return typeDescriptor(qualifiedName);
    }

    private static String typeDescriptor(String qualifiedName) {
        int arrayDepth = 0;
        String baseName = qualifiedName;
        while (baseName.endsWith("[]")) {
            arrayDepth++;
            baseName = baseName.substring(0, baseName.length() - 2);
        }
        StringBuilder descriptor = new StringBuilder();
        descriptor.append("[".repeat(arrayDepth));
        descriptor.append(scalarDescriptor(baseName));
        return descriptor.toString();
    }

    private static String scalarDescriptor(String qualifiedName) {
        return switch (qualifiedName) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> "L" + eraseGenerics(qualifiedName).replace('.', '/') + ";";
        };
    }

    private static String eraseGenerics(String qualifiedName) {
        int genericStart = qualifiedName.indexOf('<');
        return genericStart >= 0 ? qualifiedName.substring(0, genericStart) : qualifiedName;
    }
}
