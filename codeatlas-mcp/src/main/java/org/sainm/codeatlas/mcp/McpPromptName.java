package org.sainm.codeatlas.mcp;

public enum McpPromptName {
    IMPACT_REVIEW("impact-review"),
    VARIABLE_TRACE("variable-trace"),
    JSP_FLOW_ANALYSIS("jsp-flow-analysis"),
    TEST_RECOMMENDATION("test-recommendation");

    private final String value;

    McpPromptName(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
