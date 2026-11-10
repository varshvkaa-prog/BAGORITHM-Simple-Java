package balgorithm.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {}

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) throw new IllegalArgumentException("Unexpected characters after JSON value.");
        return value;
    }

    public static String stringify(Object value) {
        StringBuilder output = new StringBuilder();
        writeValue(output, value);
        return output.toString();
    }

    private static void writeValue(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String text) {
            writeString(output, text);
        } else if (value instanceof Boolean || value instanceof Number) {
            output.append(value);
        } else if (value instanceof Map<?, ?> map) {
            output.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) output.append(',');
                first = false;
                writeString(output, String.valueOf(entry.getKey()));
                output.append(':');
                writeValue(output, entry.getValue());
            }
            output.append('}');
        } else if (value instanceof Iterable<?> values) {
            output.append('[');
            boolean first = true;
            for (Object element : values) {
                if (!first) output.append(',');
                first = false;
                writeValue(output, element);
            }
            output.append(']');
        } else {
            throw new IllegalArgumentException("Cannot convert this value to JSON.");
        }
    }

    private static void writeString(StringBuilder output, String text) {
        output.append('"');
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append(String.format("\\u%04x", (int) character));
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text;
        }

        private boolean atEnd() {
            return index >= text.length();
        }

        private void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(text.charAt(index))) index++;
        }

        private Object readValue() {
            skipWhitespace();
            if (atEnd()) throw error("Expected a JSON value.");
            return switch (text.charAt(index)) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            index++;
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (take('}')) return object;
            while (true) {
                skipWhitespace();
                if (atEnd() || text.charAt(index) != '"') throw error("Expected an object key.");
                String key = readString();
                skipWhitespace();
                if (!take(':')) throw error("Expected ':' after an object key.");
                object.put(key, readValue());
                skipWhitespace();
                if (take('}')) return object;
                if (!take(',')) throw error("Expected ',' between object values.");
            }
        }

        private List<Object> readArray() {
            index++;
            List<Object> array = new ArrayList<>();
            skipWhitespace();
            if (take(']')) return array;
            while (true) {
                array.add(readValue());
                skipWhitespace();
                if (take(']')) return array;
                if (!take(',')) throw error("Expected ',' between array values.");
            }
        }

        private String readString() {
            index++;
            StringBuilder result = new StringBuilder();
            while (!atEnd()) {
                char character = text.charAt(index++);
                if (character == '"') return result.toString();
                if (character == '\\') {
                    if (atEnd()) throw error("Incomplete escape sequence.");
                    char escaped = text.charAt(index++);
                    switch (escaped) {
                        case '"', '\\', '/' -> result.append(escaped);
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> result.append(readUnicode());
                        default -> throw error("Invalid escape sequence.");
                    }
                } else {
                    if (character < 0x20) throw error("A string cannot contain a control character.");
                    result.append(character);
                }
            }
            throw error("Unterminated string.");
        }

        private char readUnicode() {
            if (index + 4 > text.length()) throw error("Incomplete Unicode escape.");
            try {
                char result = (char) Integer.parseInt(text.substring(index, index + 4), 16);
                index += 4;
                return result;
            } catch (NumberFormatException exception) {
                throw error("Invalid Unicode escape.");
            }
        }

        private Object readNumber() {
            int start = index;
            take('-');
            if (take('0')) {
                if (!atEnd() && Character.isDigit(text.charAt(index))) throw error("Invalid number.");
            } else {
                readDigits();
            }
            if (take('.')) readDigits();
            if (take('e') || take('E')) {
                if (!take('+')) take('-');
                readDigits();
            }
            try {
                if (start == index) throw error("Expected a JSON value.");
                return Double.parseDouble(text.substring(start, index));
            } catch (NumberFormatException exception) {
                throw error("Invalid number.");
            }
        }

        private void readDigits() {
            int start = index;
            while (!atEnd() && Character.isDigit(text.charAt(index))) index++;
            if (start == index) throw error("Expected a digit.");
        }

        private Object readLiteral(String literal, Object result) {
            if (!text.startsWith(literal, index)) throw error("Invalid JSON value.");
            index += literal.length();
            return result;
        }

        private boolean take(char expected) {
            if (!atEnd() && text.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " (at character " + index + ")");
        }
    }
}
