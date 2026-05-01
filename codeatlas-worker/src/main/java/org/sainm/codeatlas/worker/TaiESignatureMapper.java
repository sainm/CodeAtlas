package org.sainm.codeatlas.worker;

import java.util.ArrayList;
import java.util.List;
import org.sainm.codeatlas.graph.model.SymbolId;

public final class TaiESignatureMapper {
    private final String projectKey;
    private final String moduleKey;
    private final String sourceRootKey;

    public TaiESignatureMapper(String projectKey, String moduleKey, String sourceRootKey) {
        this.projectKey = require(projectKey, "projectKey");
        this.moduleKey = moduleKey == null || moduleKey.isBlank() ? "_root" : moduleKey.trim();
        this.sourceRootKey = sourceRootKey == null || sourceRootKey.isBlank() ? "bytecode" : sourceRootKey.trim();
    }

    public SymbolId mapMethod(String taiESignature) {
        ParsedMember member = parse(taiESignature);
        int openParen = member.remainder().indexOf('(');
        int closeParen = member.remainder().lastIndexOf(')');
        if (openParen < 0 || closeParen < openParen) {
            throw new IllegalArgumentException("Tai-e method signature must contain parameters: " + taiESignature);
        }
        String returnAndName = member.remainder().substring(0, openParen).trim();
        int nameStart = returnAndName.lastIndexOf(' ');
        if (nameStart < 0) {
            throw new IllegalArgumentException("Tai-e method signature must contain return type and method name: " + taiESignature);
        }
        String returnType = returnAndName.substring(0, nameStart).trim();
        String methodName = returnAndName.substring(nameStart + 1).trim();
        String parameters = member.remainder().substring(openParen + 1, closeParen).trim();
        return SymbolId.method(
            projectKey,
            moduleKey,
            sourceRootKey,
            member.owner(),
            methodName,
            methodDescriptor(parameters, returnType)
        );
    }

    public SymbolId mapField(String taiESignature) {
        ParsedMember member = parse(taiESignature);
        int nameStart = member.remainder().lastIndexOf(' ');
        if (nameStart < 0) {
            throw new IllegalArgumentException("Tai-e field signature must contain field type and name: " + taiESignature);
        }
        String fieldType = member.remainder().substring(0, nameStart).trim();
        String fieldName = member.remainder().substring(nameStart + 1).trim();
        return SymbolId.field(projectKey, moduleKey, sourceRootKey, member.owner(), fieldName, typeDescriptor(fieldType));
    }

    private ParsedMember parse(String signature) {
        String value = require(signature, "signature");
        if (!value.startsWith("<") || !value.endsWith(">")) {
            throw new IllegalArgumentException("Tai-e signature must be enclosed in angle brackets: " + signature);
        }
        String body = value.substring(1, value.length() - 1).trim();
        int separator = body.indexOf(':');
        if (separator < 0) {
            throw new IllegalArgumentException("Tai-e signature must contain owner separator ':'");
        }
        String owner = body.substring(0, separator).trim();
        String remainder = body.substring(separator + 1).trim();
        if (owner.isBlank() || remainder.isBlank()) {
            throw new IllegalArgumentException("Tai-e signature owner and member are required");
        }
        return new ParsedMember(owner, remainder);
    }

    private String methodDescriptor(String parameters, String returnType) {
        StringBuilder builder = new StringBuilder("(");
        for (String parameter : splitParameters(parameters)) {
            builder.append(typeDescriptor(parameter));
        }
        builder.append(')');
        builder.append(typeDescriptor(returnType));
        return builder.toString();
    }

    private List<String> splitParameters(String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < parameters.length(); i++) {
            if (parameters.charAt(i) == ',') {
                addParameter(result, parameters.substring(start, i));
                start = i + 1;
            }
        }
        addParameter(result, parameters.substring(start));
        return result;
    }

    private void addParameter(List<String> result, String parameter) {
        String normalized = parameter == null ? "" : parameter.trim();
        if (!normalized.isBlank()) {
            result.add(normalized);
        }
    }

    private String typeDescriptor(String type) {
        String normalized = require(type, "type");
        int arrayDimensions = 0;
        while (normalized.endsWith("[]")) {
            arrayDimensions++;
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < arrayDimensions; i++) {
            builder.append('[');
        }
        builder.append(scalarDescriptor(normalized));
        return builder.toString();
    }

    private String scalarDescriptor(String type) {
        return switch (type) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> "L" + type.replace('.', '/') + ";";
        };
    }

    private String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private record ParsedMember(String owner, String remainder) {
    }
}
