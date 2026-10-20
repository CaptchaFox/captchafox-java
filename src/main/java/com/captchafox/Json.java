package com.captchafox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a JSON text (RFC 8259) into maps, lists, strings, booleans, {@link BigDecimal} and {@code null}. The library
 * needs a few fields of one answer and carries no JSON library at runtime.
 */
final class Json {

    // Bounds the recursion, so that a deeply nested body is a malformed answer instead of a StackOverflowError.
    private static final int MAX_DEPTH = 64;

    private final String text;
    private int position;

    private Json(String text) {
        this.text = text;
    }

    /**
     * @throws IllegalArgumentException if the text is no JSON, or an object names a member twice
     */
    static Object parse(String text) {
        Json json = new Json(text);
        json.skipWhitespace();
        Object value = json.readValue(0);
        json.skipWhitespace();
        if (json.position != text.length()) {
            throw json.malformed("content after the value");
        }
        return value;
    }

    private Object readValue(int depth) {
        if (depth > MAX_DEPTH) {
            throw malformed("nested deeper than " + MAX_DEPTH);
        }
        if (position >= text.length()) {
            throw malformed("a value is missing");
        }

        char next = text.charAt(position);
        switch (next) {
            case '{':
                return readObject(depth);
            case '[':
                return readArray(depth);
            case '"':
                return readString();
            case 't':
                readLiteral("true");
                return Boolean.TRUE;
            case 'f':
                readLiteral("false");
                return Boolean.FALSE;
            case 'n':
                readLiteral("null");
                return null;
            default:
                return readNumber();
        }
    }

    private Map<String, Object> readObject(int depth) {
        Map<String, Object> members = new LinkedHashMap<>();
        position++;
        skipWhitespace();
        if (consume('}')) {
            return members;
        }

        while (true) {
            skipWhitespace();
            if (position >= text.length() || text.charAt(position) != '"') {
                throw malformed("a member name is missing");
            }
            String name = readString();
            // A repeated name, such as "success" twice, has no defined meaning, so the answer is malformed.
            if (members.containsKey(name)) {
                throw malformed("a member name is repeated");
            }

            skipWhitespace();
            expect(':');
            skipWhitespace();
            members.put(name, readValue(depth + 1));
            skipWhitespace();

            if (consume('}')) {
                return members;
            }
            expect(',');
        }
    }

    private List<Object> readArray(int depth) {
        List<Object> elements = new ArrayList<>();
        position++;
        skipWhitespace();
        if (consume(']')) {
            return elements;
        }

        while (true) {
            skipWhitespace();
            elements.add(readValue(depth + 1));
            skipWhitespace();

            if (consume(']')) {
                return elements;
            }
            expect(',');
        }
    }

    private String readString() {
        StringBuilder value = new StringBuilder();
        position++;
        while (true) {
            if (position >= text.length()) {
                throw malformed("a string is not closed");
            }

            char next = text.charAt(position++);
            if (next == '"') {
                return value.toString();
            }
            if (next < 0x20) {
                throw malformed("a control character in a string");
            }
            if (next != '\\') {
                value.append(next);
                continue;
            }

            if (position >= text.length()) {
                throw malformed("an escape is not complete");
            }
            char escape = text.charAt(position++);
            switch (escape) {
                case '"':
                case '\\':
                case '/':
                    value.append(escape);
                    break;
                case 'b':
                    value.append('\b');
                    break;
                case 'f':
                    value.append('\f');
                    break;
                case 'n':
                    value.append('\n');
                    break;
                case 'r':
                    value.append('\r');
                    break;
                case 't':
                    value.append('\t');
                    break;
                case 'u':
                    value.append(readHexCharacter());
                    break;
                default:
                    throw malformed("an unknown escape");
            }
        }
    }

    private char readHexCharacter() {
        if (position + 4 > text.length()) {
            throw malformed("an escape is not complete");
        }

        int code = 0;
        for (int i = 0; i < 4; i++) {
            char next = text.charAt(position++);
            // Character.digit would also take digits of other scripts, such as fullwidth ones.
            int digit = next < 0x80 ? Character.digit(next, 16) : -1;
            if (digit < 0) {
                throw malformed("an escape is no hexadecimal number");
            }
            code = code * 16 + digit;
        }
        return (char) code;
    }

    private BigDecimal readNumber() {
        int start = position;
        consume('-');

        // A leading zero stands alone; a digit after it is content after the number.
        if (!consume('0') && !skipDigits()) {
            throw malformed("an unexpected character");
        }

        if (consume('.') && !skipDigits()) {
            throw malformed("a fraction without digits");
        }

        if (consume('e') || consume('E')) {
            if (!consume('+')) {
                consume('-');
            }
            if (!skipDigits()) {
                throw malformed("an exponent without digits");
            }
        }

        return new BigDecimal(text.substring(start, position));
    }

    private boolean skipDigits() {
        int start = position;
        while (position < text.length() && text.charAt(position) >= '0' && text.charAt(position) <= '9') {
            position++;
        }
        return position > start;
    }

    private void readLiteral(String literal) {
        if (!text.startsWith(literal, position)) {
            throw malformed("an unexpected character");
        }
        position += literal.length();
    }

    private void skipWhitespace() {
        while (position < text.length()) {
            char next = text.charAt(position);
            if (next != ' ' && next != '\t' && next != '\n' && next != '\r') {
                return;
            }
            position++;
        }
    }

    private boolean consume(char expected) {
        if (position < text.length() && text.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void expect(char expected) {
        if (!consume(expected)) {
            throw malformed("'" + expected + "' expected");
        }
    }

    // The message names the position only, never the text.
    private IllegalArgumentException malformed(String problem) {
        return new IllegalArgumentException("no JSON: " + problem + " at position " + position);
    }
}
