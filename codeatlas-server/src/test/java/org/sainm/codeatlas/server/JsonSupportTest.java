package org.sainm.codeatlas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonSupportTest {
    @Test
    void serializesNestedValuesAndEscapesStrings() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", "JSP");
        item.put("ready", true);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("message", "line1\n\"line2\"");
        value.put("items", List.of(item));

        String json = JsonSupport.value(value);

        assertEquals(
            "{\"message\":\"line1\\n\\\"line2\\\"\",\"items\":[{\"name\":\"JSP\",\"ready\":true}]}",
            json
        );
    }
}
