package io.github.mandala.sbdp.spring;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class JavaAnnotationValues {
    private JavaAnnotationValues() {}

    static Map<String, String> stringConstants(CompilationUnit unit) {
        return stringConstants(unit, Map.of());
    }

    static Map<String, String> stringConstants(CompilationUnit unit, Map<String, String> inherited) {
        Map<String, String> values = new LinkedHashMap<>(inherited);
        // Resolve repeated concatenations in a few passes so declaration order is irrelevant.
        for (int pass = 0; pass < 4; pass++) {
            for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
                if (!field.isStatic() || !field.isFinal()) {
                    continue;
                }
                field.getVariables().forEach(variable -> variable.getInitializer().ifPresent(initializer -> {
                    Optional<String> value = stringValue(initializer, values);
                    value.ifPresent(text -> values.put(variable.getNameAsString(), text));
                }));
            }
        }
        return Map.copyOf(values);
    }

    static String simpleName(AnnotationExpr annotation) {
        return annotation.getName().getIdentifier();
    }

    static Optional<AnnotationExpr> annotation(NodeWithAnnotations<?> node, String simpleName) {
        return node.getAnnotations().stream()
                .filter(annotation -> simpleName(annotation).equals(simpleName))
                .findFirst();
    }

    static List<Expression> expressions(AnnotationExpr annotation, String attribute) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            return attribute.equals("value") ? flatten(single.getMemberValue()) : List.of();
        }
        if (annotation instanceof NormalAnnotationExpr normal) {
            return normal.getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals(attribute))
                    .findFirst()
                    .map(pair -> flatten(pair.getValue()))
                    .orElseGet(List::of);
        }
        return List.of();
    }

    static List<String> strings(AnnotationExpr annotation, String attribute, Map<String, String> constants) {
        List<String> result = new ArrayList<>();
        for (Expression expression : expressions(annotation, attribute)) {
            stringValue(expression, constants).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    static Optional<Boolean> booleanValue(AnnotationExpr annotation, String attribute) {
        return expressions(annotation, attribute).stream().findFirst().flatMap(expression -> {
            if (expression instanceof BooleanLiteralExpr literal) {
                return Optional.of(literal.getValue());
            }
            if (expression.isNameExpr()) {
                return Optional.of(Boolean.parseBoolean(expression.asNameExpr().getNameAsString()));
            }
            return Optional.empty();
        });
    }

    static String enumValue(AnnotationExpr annotation, String attribute) {
        return expressions(annotation, attribute).stream()
                .findFirst()
                .map(Expression::toString)
                .map(JavaAnnotationValues::lastIdentifier)
                .orElse("");
    }

    private static List<Expression> flatten(Expression expression) {
        if (expression instanceof ArrayInitializerExpr array) {
            return List.copyOf(array.getValues());
        }
        return List.of(expression);
    }

    private static Optional<String> stringValue(Expression expression, Map<String, String> constants) {
        if (expression instanceof StringLiteralExpr literal) {
            return Optional.of(literal.asString());
        }
        if (expression instanceof NameExpr name) {
            return Optional.ofNullable(constants.get(name.getNameAsString()))
                    .or(() -> Optional.of(name.getNameAsString()));
        }
        if (expression instanceof FieldAccessExpr field) {
            String identifier = field.getNameAsString();
            return Optional.ofNullable(constants.get(identifier))
                    .or(() -> Optional.of(identifier));
        }
        if (expression instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.PLUS) {
            Optional<String> left = stringValue(binary.getLeft(), constants);
            Optional<String> right = stringValue(binary.getRight(), constants);
            if (left.isPresent() && right.isPresent()) {
                return Optional.of(left.orElseThrow() + right.orElseThrow());
            }
        }
        return Optional.empty();
    }

    private static String lastIdentifier(String value) {
        int dot = value.lastIndexOf('.');
        return dot < 0 ? value : value.substring(dot + 1);
    }
}
