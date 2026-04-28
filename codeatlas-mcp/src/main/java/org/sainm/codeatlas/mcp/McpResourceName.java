package org.sainm.codeatlas.mcp;

public enum McpResourceName {
    SYMBOL("symbol"),
    JSP("jsp"),
    TABLE("table"),
    REPORT("report");

    private final String value;

    McpResourceName(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
