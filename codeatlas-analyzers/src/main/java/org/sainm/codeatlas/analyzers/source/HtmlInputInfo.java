package org.sainm.codeatlas.analyzers.source;

public record HtmlInputInfo(String pagePath, String logicalName, String inputType, String formKey, SourceLocation location) {
    public HtmlInputInfo(String pagePath, String logicalName, String inputType, SourceLocation location) {
        this(pagePath, logicalName, inputType, "", location);
    }

    public HtmlInputInfo {
        JavaClassInfo.requireNonBlank(pagePath, "pagePath");
        JavaClassInfo.requireNonBlank(logicalName, "logicalName");
        inputType = inputType == null || inputType.isBlank() ? "text" : inputType;
        formKey = formKey == null ? "" : formKey;
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }
}
