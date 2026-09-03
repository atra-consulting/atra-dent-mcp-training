package de.atra.kernsystem.domain.rechnungsextraktion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class ParserHelper {

    static final String AMOUNT = "\\d{1,3}(?:\\.\\d{3})*,\\d{2}";

    static final String DATUM = "\\d{2}\\.\\d{2}\\.\\d{4}";

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private ParserHelper() {
    }

    static BigDecimal amount(String text) {
        return new BigDecimal(text.replace(".", "").replace(',', '.'));
    }

    static LocalDate datum(String text) {
        return LocalDate.parse(text, DATE_FORMAT);
    }

    static String faktor(String text) {
        return text.replace(',', '.');
    }

    private static final java.util.regex.Pattern SPECIAL_SPACES =
            java.util.regex.Pattern.compile("[\u00A0\u2007\u2009\u202F]");

    static List<TextLine> lines(String text) {
        return List.of(SPECIAL_SPACES.matcher(text).replaceAll(" ").split("\\R"))
                .stream().map(ParserHelper::textLine).toList();
    }

    private static TextLine textLine(String raw) {
        int indent = 0;
        while (indent < raw.length() && raw.charAt(indent) == ' ') {
            indent++;
        }
        return new TextLine(indent, raw.strip());
    }
}
