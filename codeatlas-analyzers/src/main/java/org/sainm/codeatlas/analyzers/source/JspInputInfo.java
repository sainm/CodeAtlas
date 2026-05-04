package org.sainm.codeatlas.analyzers.source;

public record JspInputInfo(
        String logicalName,
        String inputType,
        String sourceTag,
        String formKey,
        SourceLocation location) {
    public JspInputInfo(String logicalName, String inputType, String sourceTag, SourceLocation location) {
        this(logicalName, inputType, sourceTag, "", location);
    }

    public JspInputInfo {
        JavaClassInfo.requireNonBlank(logicalName, "logicalName");
        inputType = inputType == null || inputType.isBlank() ? "text" : inputType;
        JavaClassInfo.requireNonBlank(sourceTag, "sourceTag");
        formKey = formKey == null ? "" : formKey;
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
