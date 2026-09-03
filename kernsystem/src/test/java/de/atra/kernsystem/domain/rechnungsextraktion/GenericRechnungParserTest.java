package de.atra.kernsystem.domain.rechnungsextraktion;

import de.atra.kernsystem.generated.model.ExtrahierteRechnungsposition;
import de.atra.kernsystem.generated.model.RechnungsextraktionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenericRechnungParserTest {

    final GenericRechnungParser parser = new GenericRechnungParser();

    @Test
    void a_leistungstext_wrapped_over_two_lines_is_reassembled() {
        String textLayer = """
                Rechnung über zahnärztliche Leistungen
                  Datum Zahn GOZ-Nr. Leistung Anz Faktor Betrag
                  02.03.2026 16 4050 Entfernung harter und weicher Zahnbeläge 1 2,3fach 7,45 EUR
                                     einschließlich Politur aller zugänglichen Flächen
                                     und Fluoridierung
                                                       Rechnungsbetrag 7,45 EUR
                """;

        ExtrahierteRechnungsposition position = firstOf(textLayer);

        assertThat(position.getLeistung()).isEqualTo(
                "Entfernung harter und weicher Zahnbeläge einschließlich Politur "
                        + "aller zugänglichen Flächen und Fluoridierung");
    }

    @Test
    void the_steigerungssatz_on_the_continuation_line_is_lifted_the_section_heading_is_not() {
        String textLayer = """
                Rechnung über zahnärztliche Leistungen
                  Datum Ziffer Zahn Bezeichnung der Leistung Anz Betrag EUR
                  02.03.2026 4050 16 Entfernung harter und weicher Zahnbeläge einschließlich Politur 1 7,45 EUR
                                     aller zugänglichen Flächen und Fluoridierung (2,3fach)
                Material- und Laborkosten nach § 9 GOZ
                  Datum Bezeichnung Anz Betrag EUR
                  16.06.2026 Antibakterielles Gel 1 19,30 EUR
                                                       Rechnungsbetrag 26,75 EUR
                """;

        RechnungsextraktionResult result = parser.parse(textLayer);

        assertThat(result.getPositionen()).hasSize(2);
        assertThat(result.getPositionen().getFirst().getLeistung()).isEqualTo(
                "Entfernung harter und weicher Zahnbeläge einschließlich Politur "
                        + "aller zugänglichen Flächen und Fluoridierung");
        assertThat(result.getPositionen().getFirst().getFaktor()).isEqualTo("2.3");
    }

    @Test
    void a_continuation_line_with_a_dot_leader_yields_text_and_zahn() {
        String textLayer = """
                Für die zahnärztliche Behandlung erlaube ich mir zu berechnen:
                4050 Entfernung harter und weicher Zahnbeläge einschließlich Politur aller 7,45 EUR
                     zugänglichen Flächen und Fluoridierung (Zahn 16) . . . . . . . . . . . .
                Rechnungsbetrag 7,45 EUR
                """;

        ExtrahierteRechnungsposition position = firstOf(textLayer);

        assertThat(position.getLeistung()).isEqualTo(
                "Entfernung harter und weicher Zahnbeläge einschließlich Politur "
                        + "aller zugänglichen Flächen und Fluoridierung");
        assertThat(position.getZahn()).isEqualTo("16");
    }

    @Test
    void a_line_with_its_own_betrag_is_not_part_of_the_leistungstext() {
        String textLayer = """
                Rechnung
                  Ziffer Zahn Leistung Menge Satz Betrag
                  4050 16 Entfernung harter und weicher Zahnbeläge einschließlich 1 2,3fach 7,45 EUR
                          Politur aller zugänglichen Flächen und Fluoridierung
                          Zahntechnische Leistungen nach beigefügtem Laborbeleg der 1 214,60 EUR
                                                       Gesamtbetrag 222,05 EUR
                """;

        ExtrahierteRechnungsposition position = firstOf(textLayer);

        assertThat(position.getLeistung()).isEqualTo(
                "Entfernung harter und weicher Zahnbeläge einschließlich Politur "
                        + "aller zugänglichen Flächen und Fluoridierung");
    }

    @Test
    void a_page_marker_is_not_part_of_the_leistungstext() {
        String textLayer = """
                Einzelaufstellung
                  Datum GOZ/GOÄ Zahn Leistung Anz Faktor Betrag
                  02.03.2026 4050 16 Entfernung harter und weicher Zahnbeläge 1 2,3fach 7,45 EUR
                                     einschließlich Politur aller zugänglichen Flächen und
                                     Fluoridierung
                                Seite 2 von 4
                  Datum GOZ/GOÄ Zahn Leistung Anz Faktor Betrag
                  02.03.2026 0010 Eingehende Untersuchung 1 2,3fach 10,72 EUR
                                                       Rechnungsbetrag 18,17 EUR
                """;

        RechnungsextraktionResult result = parser.parse(textLayer);

        assertThat(result.getPositionen()).hasSize(2);
        assertThat(result.getPositionen().getFirst().getLeistung()).isEqualTo(
                "Entfernung harter und weicher Zahnbeläge einschließlich Politur "
                        + "aller zugänglichen Flächen und Fluoridierung");
    }

    private ExtrahierteRechnungsposition firstOf(String textlayer) {
        return parser.parse(textlayer).getPositionen().getFirst();
    }
}
