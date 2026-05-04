package org.sainm.codeatlas.symbols;

import java.util.Optional;

public enum DefaultSymbolKind {
    PROJECT("project", IdentityType.SYMBOL_ID),
    MODULE("module", IdentityType.SYMBOL_ID),
    SOURCE_FILE("source-file", IdentityType.SYMBOL_ID),
    CLASS("class", IdentityType.SYMBOL_ID),
    METHOD("method", IdentityType.SYMBOL_ID),
    FIELD("field", IdentityType.SYMBOL_ID),
    JSP_PAGE("jsp-page", IdentityType.SYMBOL_ID),
    JSP_INCLUDE("jsp-include", IdentityType.SYMBOL_ID),
    JSP_TAG("jsp-tag", IdentityType.SYMBOL_ID),
    JSP_EXPRESSION("jsp-expression", IdentityType.SYMBOL_ID),
    JSP_SCRIPTLET("jsp-scriptlet", IdentityType.SYMBOL_ID),
    HTML_PAGE("html-page", IdentityType.SYMBOL_ID),
    HTML_FORM("html-form", IdentityType.SYMBOL_ID),
    HTML_LINK("html-link", IdentityType.SYMBOL_ID),
    SCRIPT_RESOURCE("script-resource", IdentityType.SYMBOL_ID),
    CLIENT_REQUEST("client-request", IdentityType.SYMBOL_ID),
    JSP_FORM("jsp-form", IdentityType.SYMBOL_ID),
    API_ENDPOINT("api-endpoint", IdentityType.SYMBOL_ID),
    ACTION_PATH("action-path", IdentityType.SYMBOL_ID),
    DICON_COMPONENT("dicon-component", IdentityType.SYMBOL_ID),
    ENTRYPOINT("entrypoint", IdentityType.SYMBOL_ID),
    SCHEDULE("schedule", IdentityType.SYMBOL_ID),
    CRON_TRIGGER("cron-trigger", IdentityType.SYMBOL_ID),
    MESSAGE_QUEUE("message-queue", IdentityType.SYMBOL_ID),
    MESSAGE_TOPIC("message-topic", IdentityType.SYMBOL_ID),
    DOMAIN_EVENT("domain-event", IdentityType.SYMBOL_ID),
    MESSAGE_LISTENER("message-listener", IdentityType.SYMBOL_ID),
    BATCH_JOB("batch-job", IdentityType.SYMBOL_ID),
    CLI_COMMAND("cli-command", IdentityType.SYMBOL_ID),
    SHELL_SCRIPT("shell-script", IdentityType.SYMBOL_ID),
    EXTERNAL_COMMAND("external-command", IdentityType.SYMBOL_ID),
    SQL_STATEMENT("sql-statement", IdentityType.SYMBOL_ID),
    SQL_VARIANT("sql-variant", IdentityType.SYMBOL_ID),
    DATASOURCE("datasource", IdentityType.SYMBOL_ID),
    DB_SCHEMA("db-schema", IdentityType.SYMBOL_ID),
    DB_TABLE("db-table", IdentityType.SYMBOL_ID),
    DB_COLUMN("db-column", IdentityType.SYMBOL_ID),
    DB_INDEX("db-index", IdentityType.SYMBOL_ID),
    DB_CONSTRAINT("db-constraint", IdentityType.SYMBOL_ID),
    DB_VIEW("db-view", IdentityType.SYMBOL_ID),
    CONFIG_KEY("config-key", IdentityType.SYMBOL_ID),
    REPORT_DEFINITION("report-definition", IdentityType.SYMBOL_ID),
    REPORT_FIELD("report-field", IdentityType.SYMBOL_ID),
    NATIVE_LIBRARY("native-library", IdentityType.SYMBOL_ID),
    BOUNDARY_SYMBOL("boundary-symbol", IdentityType.SYMBOL_ID),
    SYNTHETIC_SYMBOL("synthetic-symbol", IdentityType.SYMBOL_ID),
    PARAM_SLOT("param-slot", IdentityType.FLOW_ID),
    RETURN_SLOT("return-slot", IdentityType.FLOW_ID),
    REQUEST_PARAM("request-param", IdentityType.FLOW_ID),
    REQUEST_ATTR("request-attr", IdentityType.FLOW_ID),
    SESSION_ATTR("session-attr", IdentityType.FLOW_ID),
    MODEL_ATTR("model-attr", IdentityType.FLOW_ID),
    SQL_PARAM("sql-param", IdentityType.FLOW_ID),
    SQL_BRANCH_CONDITION("sql-branch-condition", IdentityType.FLOW_ID),
    METHOD_SUMMARY("method-summary", IdentityType.FLOW_ID),
    HTML_INPUT("html-input", IdentityType.FLOW_ID),
    JSP_INPUT("jsp-input", IdentityType.FLOW_ID),
    DOM_EVENT_HANDLER("dom-event-handler", IdentityType.FLOW_ID),
    HTML_RENDER_SLOT("html-render-slot", IdentityType.FLOW_ID),
    CLIENT_INIT_DATA("client-init-data", IdentityType.FLOW_ID),
    REFLECTION_CANDIDATE("reflection-candidate", IdentityType.FLOW_ID),
    FEATURE_SEED("feature-seed", IdentityType.ARTIFACT_ID),
    FEATURE_SCOPE("feature-scope", IdentityType.ARTIFACT_ID),
    CHANGE_PLAN("change-plan", IdentityType.ARTIFACT_ID),
    ARCHITECTURE_HEALTH("architecture-health", IdentityType.ARTIFACT_ID),
    ARCHITECTURE_METRIC("architecture-metric", IdentityType.ARTIFACT_ID),
    HOTSPOT_CANDIDATE("hotspot-candidate", IdentityType.ARTIFACT_ID),
    IMPORT_REVIEW("import-review", IdentityType.ARTIFACT_ID),
    ANALYSIS_SCOPE_DECISION("analysis-scope-decision", IdentityType.ARTIFACT_ID),
    CHANGE_ITEM("change-item", IdentityType.ARTIFACT_ID),
    SAVED_QUERY("saved-query", IdentityType.ARTIFACT_ID),
    WATCH_SUBSCRIPTION("watch-subscription", IdentityType.ARTIFACT_ID),
    REVIEW_COMMENT("review-comment", IdentityType.ARTIFACT_ID),
    POLICY_VIOLATION("policy-violation", IdentityType.ARTIFACT_ID),
    EXPORT_ARTIFACT("export-artifact", IdentityType.ARTIFACT_ID);

    private final String kind;
    private final IdentityType identityType;

    DefaultSymbolKind(String kind, IdentityType identityType) {
        this.kind = kind;
        this.identityType = identityType;
    }

    public String kind() {
        return kind;
    }

    public IdentityType identityType() {
        return identityType;
    }

    public SymbolKind toSymbolKind() {
        return new SymbolKind(kind, identityType);
    }

    public String fragmentDelimiter() {
        return flowSuffixToken()
                .map(token -> token.substring(0, 1))
                .orElse("#");
    }

    public Optional<String> flowSuffixToken() {
        return switch (this) {
            case METHOD_SUMMARY -> Optional.of("@");
            case PARAM_SLOT, SQL_PARAM -> Optional.of(":param[");
            case RETURN_SLOT -> Optional.of(":return[");
            case SQL_BRANCH_CONDITION -> Optional.of(":branch[");
            case HTML_INPUT, JSP_INPUT -> Optional.of(":input[");
            case REFLECTION_CANDIDATE -> Optional.of(":reflect[");
            default -> Optional.empty();
        };
    }

    public boolean allowsFragment() {
        if (identityType == IdentityType.FLOW_ID) {
            return true;
        }
        return switch (this) {
            case METHOD, FIELD, JSP_FORM, JSP_TAG, JSP_EXPRESSION, JSP_SCRIPTLET, DICON_COMPONENT,
                    HTML_FORM, HTML_LINK, CLIENT_REQUEST, CONFIG_KEY, SQL_STATEMENT, SQL_VARIANT,
                    DB_COLUMN, DB_INDEX, DB_CONSTRAINT, REPORT_DEFINITION, REPORT_FIELD,
                    SYNTHETIC_SYMBOL -> true;
            default -> false;
        };
    }

    public static Optional<DefaultSymbolKind> from(String kind) {
        for (DefaultSymbolKind defaultKind : values()) {
            if (defaultKind.kind.equals(kind)) {
                return Optional.of(defaultKind);
            }
        }
        return Optional.empty();
    }
}
