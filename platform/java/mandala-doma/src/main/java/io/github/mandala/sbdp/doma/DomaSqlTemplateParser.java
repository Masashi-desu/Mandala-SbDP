package io.github.mandala.sbdp.doma;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tokenizes Doma's comment based SQL template language and renders bounded representative variants.
 * Each conditional branch remains available for AST analysis, without concatenating mutually
 * exclusive fragments into invalid SQL.
 */
public final class DomaSqlTemplateParser {
    private static final int MAX_VARIANTS = 32;

    public DomaSqlTemplate parse(String sql) {
        Lexed lexed = lex(sql == null ? "" : sql);
        Sequence root = parseSequence(lexed.tokens(), new Cursor(), Set.of()).sequence();
        List<String> variants = root.render(MAX_VARIANTS).stream().distinct().toList();
        if (variants.isEmpty()) {
            variants = List.of("");
        }
        boolean dynamic = lexed.directives().stream().anyMatch(directive -> switch (directive.type()) {
            case IF, ELSEIF, ELSE, FOR, POPULATE, EMBEDDED_VARIABLE -> true;
            default -> false;
        });
        return new DomaSqlTemplate(
                dynamic,
                lexed.directives(),
                lexed.parameters(),
                variants.getFirst(),
                variants);
    }

    private Lexed lex(String source) {
        List<Token> tokens = new ArrayList<>();
        List<TemplateDirective> directives = new ArrayList<>();
        Set<String> parameters = new LinkedHashSet<>();
        int cursor = 0;
        while (cursor < source.length()) {
            int start = source.indexOf("/*", cursor);
            if (start < 0) {
                addText(tokens, source.substring(cursor));
                break;
            }
            addText(tokens, source.substring(cursor, start));
            int end = source.indexOf("*/", start + 2);
            if (end < 0) {
                addText(tokens, source.substring(start));
                break;
            }
            String body = source.substring(start + 2, end).trim();
            Classified classified = classify(body, start);
            if (classified == null) {
                addText(tokens, source.substring(start, end + 2));
                cursor = end + 2;
                continue;
            }
            TemplateDirective.Type type = classified.directive().type();
            if ((type == TemplateDirective.Type.BIND_VARIABLE || type == TemplateDirective.Type.LITERAL_VARIABLE)
                    && !isTestLiteralStart(source, skipWhitespace(source, end + 2))) {
                // Preserve ordinary one-word block comments; Doma value comments must have a test literal.
                addText(tokens, source.substring(start, end + 2));
                cursor = end + 2;
                continue;
            }
            directives.add(classified.directive());
            if (!classified.expression().isBlank()) {
                parameters.add(classified.expression());
            }
            cursor = end + 2;
            switch (type) {
                case IF, ELSEIF, ELSE, END, FOR -> tokens.add(new Token(type, ""));
                case EXPAND -> {
                    // The '*' test literal after an expand directive is already ordinary SQL text.
                }
                case POPULATE -> addText(tokens, "mandala_dynamic_column = ?");
                case EMBEDDED_VARIABLE -> addText(tokens, "mandala_dynamic_identifier");
                case BIND_VARIABLE, LITERAL_VARIABLE -> {
                    int valueStart = skipWhitespace(source, cursor);
                    String whitespace = source.substring(cursor, valueStart);
                    LiteralRange range = testLiteral(source, valueStart);
                    addText(tokens, whitespace + (range.parenthesized() ? "(?)" : "?"));
                    cursor = range.end();
                }
                case OTHER -> addText(tokens, source.substring(start, end + 2));
            }
        }
        return new Lexed(tokens, directives, parameters);
    }

    private Parsed parseSequence(List<Token> tokens, Cursor cursor, Set<TemplateDirective.Type> stops) {
        List<TemplateNode> nodes = new ArrayList<>();
        while (cursor.index < tokens.size()) {
            Token token = tokens.get(cursor.index);
            if (token.type() != null && stops.contains(token.type())) {
                return new Parsed(new Sequence(nodes), token.type());
            }
            if (token.type() == TemplateDirective.Type.IF) {
                cursor.index++;
                List<Sequence> branches = new ArrayList<>();
                Parsed branch = parseSequence(
                        tokens,
                        cursor,
                        Set.of(TemplateDirective.Type.ELSEIF, TemplateDirective.Type.ELSE, TemplateDirective.Type.END));
                branches.add(branch.sequence());
                while (branch.stop() == TemplateDirective.Type.ELSEIF) {
                    cursor.index++;
                    branch = parseSequence(
                            tokens,
                            cursor,
                            Set.of(TemplateDirective.Type.ELSEIF, TemplateDirective.Type.ELSE, TemplateDirective.Type.END));
                    branches.add(branch.sequence());
                }
                if (branch.stop() == TemplateDirective.Type.ELSE) {
                    cursor.index++;
                    branch = parseSequence(tokens, cursor, Set.of(TemplateDirective.Type.END));
                    branches.add(branch.sequence());
                }
                if (branch.stop() == TemplateDirective.Type.END) {
                    cursor.index++;
                }
                nodes.add(new Choice(branches));
                continue;
            }
            if (token.type() == TemplateDirective.Type.FOR) {
                cursor.index++;
                Parsed body = parseSequence(tokens, cursor, Set.of(TemplateDirective.Type.END));
                if (body.stop() == TemplateDirective.Type.END) {
                    cursor.index++;
                }
                nodes.add(body.sequence());
                continue;
            }
            if (token.type() == null) {
                nodes.add(new Text(token.text()));
            }
            cursor.index++;
        }
        return new Parsed(new Sequence(nodes), null);
    }

    private Classified classify(String body, int offset) {
        if (body.isBlank() || body.startsWith("*")) {
            return null;
        }
        if (body.startsWith("%")) {
            String source = body.substring(1).trim();
            String keyword = firstWord(source).toLowerCase(Locale.ROOT);
            String expression = source.substring(Math.min(keyword.length(), source.length())).trim();
            TemplateDirective.Type type = switch (keyword) {
                case "if" -> TemplateDirective.Type.IF;
                case "elseif" -> TemplateDirective.Type.ELSEIF;
                case "else" -> TemplateDirective.Type.ELSE;
                case "end" -> TemplateDirective.Type.END;
                case "for" -> TemplateDirective.Type.FOR;
                case "expand" -> TemplateDirective.Type.EXPAND;
                case "populate" -> TemplateDirective.Type.POPULATE;
                default -> TemplateDirective.Type.OTHER;
            };
            return new Classified(new TemplateDirective(type, expression, offset), expression);
        }
        if (body.startsWith("#")) {
            String expression = body.substring(1).trim();
            return new Classified(
                    new TemplateDirective(TemplateDirective.Type.EMBEDDED_VARIABLE, expression, offset), expression);
        }
        if (body.startsWith("^")) {
            String expression = body.substring(1).trim();
            return new Classified(
                    new TemplateDirective(TemplateDirective.Type.LITERAL_VARIABLE, expression, offset), expression);
        }
        if (looksLikeValueExpression(body)) {
            return new Classified(
                    new TemplateDirective(TemplateDirective.Type.BIND_VARIABLE, body, offset), body);
        }
        return null;
    }

    private boolean looksLikeValueExpression(String value) {
        if (value.isBlank() || !Character.isJavaIdentifierStart(value.charAt(0))) {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(Character.isJavaIdentifierPart(character)
                    || character == '.'
                    || character == '['
                    || character == ']'
                    || character == '('
                    || character == ')')) {
                return false;
            }
        }
        return true;
    }

    private LiteralRange testLiteral(String source, int start) {
        if (start >= source.length()) {
            return new LiteralRange(start, false);
        }
        char first = source.charAt(start);
        if (first == '(') {
            return new LiteralRange(skipBalanced(source, start), true);
        }
        if (first == '\'' || first == '"') {
            return new LiteralRange(skipQuoted(source, start, first), false);
        }
        int cursor = start;
        if (first == '-' || first == '+') {
            cursor++;
        }
        while (cursor < source.length()) {
            char character = source.charAt(cursor);
            if (Character.isLetterOrDigit(character)
                    || character == '.'
                    || character == '_'
                    || character == ':') {
                cursor++;
            } else {
                break;
            }
        }
        return new LiteralRange(cursor == start ? start : cursor, false);
    }

    private boolean isTestLiteralStart(String source, int start) {
        if (start >= source.length()) {
            return false;
        }
        char first = source.charAt(start);
        if (first == '\'' || first == '"' || first == '(' || first == '+' || first == '-' || Character.isDigit(first)) {
            return true;
        }
        return startsWord(source, start, "null")
                || startsWord(source, start, "true")
                || startsWord(source, start, "false");
    }

    private boolean startsWord(String source, int start, String word) {
        int end = start + word.length();
        return end <= source.length()
                && source.regionMatches(true, start, word, 0, word.length())
                && (end == source.length() || !Character.isJavaIdentifierPart(source.charAt(end)));
    }

    private int skipBalanced(String source, int start) {
        int depth = 0;
        int cursor = start;
        while (cursor < source.length()) {
            char character = source.charAt(cursor);
            if (character == '\'' || character == '"') {
                cursor = skipQuoted(source, cursor, character);
                continue;
            }
            if (character == '(') {
                depth++;
            } else if (character == ')' && --depth == 0) {
                return cursor + 1;
            }
            cursor++;
        }
        return cursor;
    }

    private int skipQuoted(String source, int start, char quote) {
        int cursor = start + 1;
        while (cursor < source.length()) {
            if (source.charAt(cursor) == quote) {
                if (cursor + 1 < source.length() && source.charAt(cursor + 1) == quote) {
                    cursor += 2;
                    continue;
                }
                return cursor + 1;
            }
            cursor++;
        }
        return cursor;
    }

    private int skipWhitespace(String source, int cursor) {
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private String firstWord(String source) {
        int cursor = 0;
        while (cursor < source.length() && !Character.isWhitespace(source.charAt(cursor))) {
            cursor++;
        }
        return source.substring(0, cursor);
    }

    private void addText(List<Token> tokens, String value) {
        if (!value.isEmpty()) {
            tokens.add(new Token(null, value));
        }
    }

    private sealed interface TemplateNode permits Text, Sequence, Choice {
        List<String> render(int limit);
    }

    private record Text(String value) implements TemplateNode {
        @Override
        public List<String> render(int limit) {
            return List.of(value);
        }
    }

    private record Sequence(List<TemplateNode> children) implements TemplateNode {
        private Sequence {
            children = List.copyOf(children);
        }

        @Override
        public List<String> render(int limit) {
            List<String> result = new ArrayList<>(List.of(""));
            for (TemplateNode child : children) {
                List<String> next = new ArrayList<>();
                for (String prefix : result) {
                    for (String suffix : child.render(limit)) {
                        next.add(prefix + suffix);
                        if (next.size() >= limit) {
                            break;
                        }
                    }
                    if (next.size() >= limit) {
                        break;
                    }
                }
                result = next;
            }
            return List.copyOf(result);
        }
    }

    private record Choice(List<Sequence> branches) implements TemplateNode {
        private Choice {
            branches = List.copyOf(branches);
        }

        @Override
        public List<String> render(int limit) {
            List<String> result = new ArrayList<>();
            for (Sequence branch : branches) {
                for (String rendered : branch.render(limit)) {
                    result.add(rendered);
                    if (result.size() >= limit) {
                        return List.copyOf(result);
                    }
                }
            }
            return List.copyOf(result);
        }
    }

    private record Token(TemplateDirective.Type type, String text) {}

    private record Lexed(List<Token> tokens, List<TemplateDirective> directives, Set<String> parameters) {}

    private record Classified(TemplateDirective directive, String expression) {}

    private record LiteralRange(int end, boolean parenthesized) {}

    private record Parsed(Sequence sequence, TemplateDirective.Type stop) {}

    private static final class Cursor {
        private int index;
    }
}
