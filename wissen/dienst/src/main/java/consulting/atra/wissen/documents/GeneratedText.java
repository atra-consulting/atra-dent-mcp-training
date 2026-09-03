package consulting.atra.wissen.documents;

public final class GeneratedText {

    private static final char SOFT_HYPHEN = '\u00AD';

    private static final char NON_BREAKING_SPACE = '\u00A0';

    private static final char NARROW_SPACE = '\u202F';

    private GeneratedText() {
    }

    public static String normalize(String text) {
        StringBuilder cleaned = new StringBuilder(text.length());
        for (int position = 0; position < text.length(); position++) {
            char chars = text.charAt(position);
            if (chars == SOFT_HYPHEN) {
                continue;
            }
            if (Character.isWhitespace(chars)
                    || chars == NON_BREAKING_SPACE
                    || chars == NARROW_SPACE) {
                if (!cleaned.isEmpty() && cleaned.charAt(cleaned.length() - 1) != ' ') {
                    cleaned.append(' ');
                }
                continue;
            }
            cleaned.append(chars);
        }
        return cleaned.toString().strip();
    }
}
