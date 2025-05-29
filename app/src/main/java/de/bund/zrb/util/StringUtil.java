package de.bund.zrb.util;

public class StringUtil {

    /**
     * Inserts a value before a trailing quote (if present) in the input string.
     * If the string ends with a single or double quote, the value is inserted before it.
     * Otherwise, the value is appended normally.
     *
     * @param input the original string
     * @param value the value to insert
     * @param separator the separator to use between input and value
     * @return the resulting string with the inserted value
     */
    public static String insertBeforeTrailingQuote(String input, String value, String separator) {
        if (input == null || input.isEmpty()) {
            return value;
        }

        int lastQuotePos = input.length() - 1;
        char lastChar = input.charAt(lastQuotePos);

        if (lastChar == '\'' || lastChar == '"') {
            String prefix = input.substring(0, lastQuotePos);
            return prefix + separator + value + lastChar;
        }

        return input + separator + value;
    }

    /**
     * Removes matching leading and trailing quotes from the given string.
     * Supports single (') and double (") quotes.
     *
     * @param input the quoted string
     * @return the unquoted string, or original if not properly quoted
     */
    public static String unquote(String input) {
        if (input == null || input.length() < 2) {
            return input;
        }

        char first = input.charAt(0);
        char last = input.charAt(input.length() - 1);

        if ((first == '\'' || first == '"') && first == last) {
            return input.substring(1, input.length() - 1);
        }

        return input;
    }

}
