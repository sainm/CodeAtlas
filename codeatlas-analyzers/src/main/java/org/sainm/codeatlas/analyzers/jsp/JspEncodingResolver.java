package org.sainm.codeatlas.analyzers.jsp;

import org.sainm.codeatlas.graph.model.Confidence;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JspEncodingResolver {
    private static final Pattern CHARSET = Pattern.compile("(?i)charset\\s*=\\s*([^;\\s]+)");

    public JspEncodingResolution resolve(byte[] bytes, JspSemanticAnalysis analysis, WebAppContext context) {
        Optional<String> bomEncoding = bomEncoding(bytes);
        if (bomEncoding.isPresent()) {
            return new JspEncodingResolution(bomEncoding.get(), "bom", Confidence.CERTAIN);
        }
        for (JspDirective directive : analysis.directives()) {
            if (!directive.name().equals("page")) {
                continue;
            }
            String pageEncoding = directive.attributes().get("pageEncoding");
            if (pageEncoding != null && !pageEncoding.isBlank()) {
                return new JspEncodingResolution(pageEncoding, "pageEncoding", Confidence.CERTAIN);
            }
            String contentType = directive.attributes().get("contentType");
            Optional<String> charset = charset(contentType);
            if (charset.isPresent()) {
                return new JspEncodingResolution(charset.get(), "contentType", Confidence.CERTAIN);
            }
        }
        return new JspEncodingResolution(context.defaultEncoding(), "webAppDefault", Confidence.LIKELY);
    }

    private Optional<String> bomEncoding(byte[] bytes) {
        if (bytes == null || bytes.length < 2) {
            return Optional.empty();
        }
        if (bytes.length >= 3
            && (bytes[0] & 0xFF) == 0xEF
            && (bytes[1] & 0xFF) == 0xBB
            && (bytes[2] & 0xFF) == 0xBF) {
            return Optional.of("UTF-8");
        }
        if ((bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return Optional.of("UTF-16BE");
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return Optional.of("UTF-16LE");
        }
        return Optional.empty();
    }

    private Optional<String> charset(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = CHARSET.matcher(contentType);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1).replace("\"", "").replace("'", "").toUpperCase(Locale.ROOT));
    }
}
