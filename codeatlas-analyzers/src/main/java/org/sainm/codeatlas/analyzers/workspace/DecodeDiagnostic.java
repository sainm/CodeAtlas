package org.sainm.codeatlas.analyzers.workspace;

public record DecodeDiagnostic(String code, String message, boolean binary, String charset) {
    public DecodeDiagnostic {
        code = requireNonBlank(code, "code");
        message = message == null ? "" : message;
        charset = charset == null ? "" : charset;
    }

    public static DecodeDiagnostic text(String charset) {
        return new DecodeDiagnostic("TEXT", "", false, charset);
    }

    public static DecodeDiagnostic binaryFile() {
        return new DecodeDiagnostic("BINARY", "", true, "");
    }

    public static DecodeDiagnostic skipped(String code, String message) {
        return new DecodeDiagnostic(code, message, false, "");
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
