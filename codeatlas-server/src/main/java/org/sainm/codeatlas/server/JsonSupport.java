package org.sainm.codeatlas.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

final class JsonSupport {
    private JsonSupport() {
    }

    static String map(Map<String, ?> map) {
        return map.entrySet().stream()
            .map(entry -> string(entry.getKey()) + ":" + value(entry.getValue()))
            .collect(Collectors.joining(",", "{", "}"));
    }

    static String value(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> converted = new LinkedHashMap<>();
            rawMap.forEach((key, mapValue) -> converted.put(String.valueOf(key), mapValue));
            return map(converted);
        }
        if (value instanceof Iterable<?> iterable) {
            String joined = StreamSupport.stream(iterable.spliterator(), false)
                .map(JsonSupport::value)
                .collect(Collectors.joining(","));
            return "[" + joined + "]";
        }
        return string(value == null ? "" : value.toString());
    }

    static String string(String value) {
        return "\"" + escape(value) + "\"";
    }

    static String escape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
