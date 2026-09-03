package de.atra.kernsystem.domain.rechnungsextraktion;

import de.atra.kernsystem.generated.model.ExtrahierteRechnungsposition;
import de.atra.kernsystem.generated.model.ExtrahierterPatient;
import de.atra.kernsystem.generated.model.RechnungsextraktionResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class GenericRechnungParser {

    private static final String B = ParserHelper.AMOUNT;
    private static final String D = ParserHelper.DATUM;

    private static final Pattern POSITION = Pattern.compile(
            "^(?:(" + D + ")\\s+)?(?:(\\d{4})\\s+)?(?:(\\d{2})\\s+)?(?:(\\d{4})\\s+)?"
                    + "(\\p{L}.*?)(?:\\s+(\\d+))?(?:\\s+(\\d+,\\d)fach)?\\s+(" + B + ")\\s+EUR$");

    private static final Pattern DOT_LEADER = Pattern.compile("(?:\\s*\\.){3,}");

    private static final List<Pattern> NUMBER_PATTERNS = List.of(
            Pattern.compile("Rechnungsnummer\\s+(\\S+)"),
            Pattern.compile("Rechnungs-Nr\\.\\s+(\\S+)"),
            Pattern.compile("Rechnung Nr\\.\\s+(\\S+)"),
            Pattern.compile("^Rechnung\\s+(\\S+)$"));

    private static final List<Pattern> DATUM_PATTERNS = List.of(
            Pattern.compile("Rechnungsdatum\\s+(" + D + ")"),
            Pattern.compile("^vom\\s+(" + D + ")$"),
            Pattern.compile(", den (" + D + ")$"));

    private static final Pattern TOTAL = Pattern.compile(
            "^(?:Rechnungsbetrag|Gesamtbetrag)\\s+(" + B + ")\\s+EUR$");

    private static final Pattern AMOUNT_DUE = Pattern.compile(
            "^Offener Betrag\\s+(" + B + ")\\s+EUR$");

    private static final Pattern FAKTOR_IM_TEXT =
            Pattern.compile("\\s*\\((\\d+,\\d)fach\\)");

    private static final Pattern ZAHN_IM_TEXT =
            Pattern.compile("\\s*\\(Zahn (\\d{2})\\)");

    private static final Pattern COUNT_IN_TEXT =
            Pattern.compile("\\s*\\((\\d+)×\\)");

    private static final Pattern HEADER_LABEL = Pattern.compile(
            "(?:^|\\s)(?:Rechnungsnummer|Rechnungs-Nr\\.|Rechnungsdatum|Geburtsdatum"
                    + "|Kunden-Nr\\.|Vorgang|Patient geb\\.|Ärztliche Leitung)(?:\\s.*)?$");

    private static final Pattern POSTAL_CODE_CITY = Pattern.compile("^(\\d{5})\\s+(\\p{L}.*)$");

    private static final Pattern PAGE_MARKER = Pattern.compile("Seite \\d+ von \\d+");

    RechnungsextraktionResult parse(String text) {
        List<TextLine> lines = ParserHelper.lines(text);

        List<ExtrahierteRechnungsposition> positionen = new ArrayList<>();
        ExtrahierteRechnungsposition last = null;
        int indentOfLast = 0;
        String number = null;
        String rechnungDate = null;
        String total = null;
        String amountDue = null;

        for (TextLine line : lines) {
            String content = line.text();
            if (content.isEmpty()) {
                last = null;
                continue;
            }
            if (number == null) {
                number = firstMatch(content, NUMBER_PATTERNS);
            }
            if (rechnungDate == null) {
                rechnungDate = firstMatch(content, DATUM_PATTERNS);
            }
            if (total == null) {
                Matcher m = TOTAL.matcher(content);
                if (m.matches()) {
                    total = m.group(1);
                }
            }
            if (amountDue == null) {
                Matcher m = AMOUNT_DUE.matcher(content);
                if (m.matches()) {
                    amountDue = m.group(1);
                }
            }

            boolean dotLeader = DOT_LEADER.matcher(content).find();
            String candidate = DOT_LEADER.matcher(content).replaceAll(" ").strip();
            Matcher m = POSITION.matcher(candidate);
            if (m.matches() && (dotLeader || m.group(1) != null || m.group(2) != null
                    || m.group(3) != null || m.group(4) != null)) {
                last = position(m);
                indentOfLast = line.indent();
                positionen.add(last);
                continue;
            }

            if (last != null && isContinuationLine(line.indent(), indentOfLast, candidate)) {
                last.setLeistung(last.getLeistung() + " " + candidate);
                continue;
            }
            last = null;
        }

        positionen.forEach(GenericRechnungParser::liftDataFromLeistungText);

        if (positionen.isEmpty() || total == null) {
            throw new IllegalStateException("Keine Positionen oder kein Gesamtbetrag erkannt");
        }

        BigDecimal totalAmount = ParserHelper.amount(total);
        return new RechnungsextraktionResult()
                .rechnungsnummer(number == null ? null : withoutPunctuation(number))
                .rechnungsdatum(rechnungDate == null ? null : ParserHelper.datum(rechnungDate))
                .absender(sender(lines))
                .patient(patient(lines))
                .positionen(positionen)
                .gesamtbetrag(totalAmount)
                .zahlbetrag(amountDue == null ? totalAmount : ParserHelper.amount(amountDue));
    }

    private static boolean isContinuationLine(int indent, int indentOfLast, String candidate) {
        return indent > indentOfLast
                && !candidate.endsWith("EUR")
                && !PAGE_MARKER.matcher(candidate).find();
    }

    private static ExtrahierteRechnungsposition position(Matcher m) {
        String code = m.group(2) != null ? m.group(2) : m.group(4);
        return new ExtrahierteRechnungsposition()
                .datum(m.group(1) == null ? null : ParserHelper.datum(m.group(1)))
                .ziffer(code)
                .zahn(m.group(3))
                .leistung(m.group(5).strip())
                .anzahl(m.group(6) == null ? null : Integer.valueOf(m.group(6)))
                .faktor(m.group(7) == null ? null : ParserHelper.faktor(m.group(7)))
                .betrag(ParserHelper.amount(m.group(8)));
    }

    private static void liftDataFromLeistungText(ExtrahierteRechnungsposition position) {
        String leistung = position.getLeistung();

        Matcher faktor = FAKTOR_IM_TEXT.matcher(leistung);
        if (faktor.find()) {
            if (position.getFaktor() == null) {
                position.setFaktor(ParserHelper.faktor(faktor.group(1)));
            }
            leistung = faktor.replaceAll("");
        }
        Matcher zahn = ZAHN_IM_TEXT.matcher(leistung);
        if (zahn.find()) {
            if (position.getZahn() == null) {
                position.setZahn(zahn.group(1));
            }
            leistung = zahn.replaceAll("");
        }
        Matcher count = COUNT_IN_TEXT.matcher(leistung);
        if (count.find()) {
            if (position.getAnzahl() == null) {
                position.setAnzahl(Integer.valueOf(count.group(1)));
            }
            leistung = count.replaceAll("");
        }
        position.setLeistung(leistung.strip());
    }

    private static String sender(List<TextLine> lines) {
        return lines.stream().map(TextLine::text)
                .filter(z -> !z.isEmpty()).findFirst().orElse(null);
    }

    private static ExtrahierterPatient patient(List<TextLine> lines) {
        List<String> cleaned = lines.stream().map(TextLine::text)
                .map(z -> HEADER_LABEL.matcher(z).replaceAll("").strip())
                .toList();

        for (int i = 0; i < cleaned.size(); i++) {
            String line = cleaned.get(i);
            if (line.contains("·")) {
                continue;
            }
            Matcher m = POSTAL_CODE_CITY.matcher(line);
            if (!m.matches()) {
                continue;
            }
            List<String> before = new ArrayList<>();
            for (int j = i - 1; j >= 0 && before.size() < 2; j--) {
                String candidate = cleaned.get(j);
                if (candidate.isEmpty()) {
                    continue;
                }
                if (candidate.contains("·") || candidate.contains("EUR")) {
                    break;
                }
                before.add(candidate);
            }
            if (before.size() < 2 || before.get(1).matches(".*\\d.*")) {
                return null;
            }
            return new ExtrahierterPatient()
                    .name(before.get(1))
                    .strasse(before.get(0))
                    .plz(m.group(1))
                    .ort(m.group(2).strip());
        }
        return null;
    }

    private static String firstMatch(String line, List<Pattern> patterns) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static String withoutPunctuation(String value) {
        return value.replaceAll("[.,;:]$", "");
    }
}
