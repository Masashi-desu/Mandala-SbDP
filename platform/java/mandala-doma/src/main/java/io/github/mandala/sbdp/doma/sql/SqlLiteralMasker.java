package io.github.mandala.sbdp.doma.sql;

/** Removes literal values from already parsed SQL while preserving its executable structure. */
public final class SqlLiteralMasker {
    public String mask(String sql) {
        if (sql == null || sql.isBlank()) {
            return "";
        }
        StringBuilder output = new StringBuilder(sql.length());
        int cursor = 0;
        while (cursor < sql.length()) {
            char character = sql.charAt(cursor);
            if (character == '"') {
                int end = quotedIdentifierEnd(sql, cursor);
                output.append(sql, cursor, end);
                cursor = end;
                continue;
            }
            if (character == '\'') {
                if (output.length() > 0
                        && (output.charAt(output.length() - 1) == 'E' || output.charAt(output.length() - 1) == 'e')
                        && (output.length() == 1 || !isIdentifier(output.charAt(output.length() - 2)))) {
                    output.setLength(output.length() - 1);
                }
                output.append('?');
                cursor = quotedStringEnd(sql, cursor);
                continue;
            }
            String dollarDelimiter = dollarDelimiter(sql, cursor);
            if (dollarDelimiter != null) {
                int end = sql.indexOf(dollarDelimiter, cursor + dollarDelimiter.length());
                output.append('?');
                cursor = end < 0 ? sql.length() : end + dollarDelimiter.length();
                continue;
            }
            if (Character.isDigit(character) && numericStarts(sql, cursor)) {
                output.append('?');
                cursor = numericEnd(sql, cursor);
                continue;
            }
            output.append(character);
            cursor++;
        }
        return output.toString().trim();
    }

    private boolean numericStarts(String sql, int cursor) {
        if (cursor > 0) {
            char previous = sql.charAt(cursor - 1);
            if (isIdentifier(previous) || previous == '$') {
                return false;
            }
        }
        return true;
    }

    private int numericEnd(String sql, int cursor) {
        int index = cursor;
        boolean exponent = false;
        while (index < sql.length()) {
            char character = sql.charAt(index);
            if (Character.isDigit(character) || character == '.' || character == '_') {
                index++;
            } else if ((character == 'e' || character == 'E') && !exponent) {
                exponent = true;
                index++;
                if (index < sql.length() && (sql.charAt(index) == '+' || sql.charAt(index) == '-')) {
                    index++;
                }
            } else {
                break;
            }
        }
        return index;
    }

    private int quotedStringEnd(String sql, int start) {
        int cursor = start + 1;
        while (cursor < sql.length()) {
            if (sql.charAt(cursor) == '\'') {
                if (cursor + 1 < sql.length() && sql.charAt(cursor + 1) == '\'') {
                    cursor += 2;
                    continue;
                }
                return cursor + 1;
            }
            if (sql.charAt(cursor) == '\\' && cursor + 1 < sql.length()) {
                cursor += 2;
            } else {
                cursor++;
            }
        }
        return cursor;
    }

    private int quotedIdentifierEnd(String sql, int start) {
        int cursor = start + 1;
        while (cursor < sql.length()) {
            if (sql.charAt(cursor) == '"') {
                if (cursor + 1 < sql.length() && sql.charAt(cursor + 1) == '"') {
                    cursor += 2;
                    continue;
                }
                return cursor + 1;
            }
            cursor++;
        }
        return cursor;
    }

    private String dollarDelimiter(String sql, int cursor) {
        if (sql.charAt(cursor) != '$') {
            return null;
        }
        int end = sql.indexOf('$', cursor + 1);
        if (end < 0) {
            return null;
        }
        for (int index = cursor + 1; index < end; index++) {
            char character = sql.charAt(index);
            if (!(Character.isLetterOrDigit(character) || character == '_')) {
                return null;
            }
        }
        return sql.substring(cursor, end + 1);
    }

    private boolean isIdentifier(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '$';
    }
}
