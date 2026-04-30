package org.sainm.codeatlas.mcp;

public enum McpResourceName {
    SYMBOL("symbol"),
    JSP("jsp"),
    TABLE("table"),
    QUERY_VIEW("query-view"),
    REPORT("report");

    private final String value;

    McpResourceName(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
