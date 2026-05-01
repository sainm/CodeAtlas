package org.sainm.codeatlas.analyzers.java;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.declaration.CtAnnotation;

public final class SpoonAnnotationValues {
    private SpoonAnnotationValues() {
    }

    public static Optional<String> firstString(CtAnnotation<?> annotation, String... keys) {
        return strings(annotation, keys).stream().findFirst();
    }

    public static List<String> strings(CtAnnotation<?> annotation, String... keys) {
        for (String key : keys) {
            CtExpression<?> expression = annotation.getValues().get(key);
            if (expression == null && "value".equals(key)) {
                expression = annotation.getValues().get("");
            }
            List<String> values = strings(expression);
            if (!values.isEmpty()) {
                return values;
            }
        }
        return List.of();
    }

    private static List<String> strings(CtExpression<?> expression) {
        if (expression == null) {
            return List.of();
        }
        if (expression instanceof CtLiteral<?> literal && literal.getValue() instanceof String text) {
            return text.isBlank() ? List.of() : List.of(text);
        }
        if (expression instanceof CtNewArray<?> array) {
            List<String> values = new ArrayList<>();
            for (CtExpression<?> item : array.getElements()) {
                values.addAll(strings(item));
            }
            return List.copyOf(values);
        }
        return List.of();
    }
}
