package de.atra.kernsystem;

import de.atra.kernsystem.generated.model.Adresse;
import de.atra.kernsystem.generated.model.Kunde;
import de.atra.kernsystem.generated.model.Leistungsbereich;
import de.atra.kernsystem.generated.model.Schadenposition;
import de.atra.kernsystem.generated.model.TarifId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OpenApiContractTest {

    @Autowired
    ObjectMapper mapper;

    @Test
    void geldbetrag_is_a_bigdecimal_and_goes_out_as_a_json_string() {
        Schadenposition position = new Schadenposition()
                .goz("2080")
                .leistungsbereich(Leistungsbereich.ZERH)
                .betrag(new BigDecimal("15.90"))
                .beschreibung("Fuellung, mehrflaechig");

        BigDecimal amount = position.getBetrag();
        assertThat(amount).isEqualByComparingTo("15.90");

        String json = mapper.writeValueAsString(position);
        assertThat(json).contains("\"betrag\":\"15.90\"");
    }

    @Test
    void geldbetrag_keeps_two_decimals_in_the_roundtrip() {
        Schadenposition position = new Schadenposition()
                .goz("0010")
                .leistungsbereich(Leistungsbereich.ZERH)
                .betrag(new BigDecimal("8.90"))
                .beschreibung("Eingehende Untersuchung");

        Schadenposition back =
                mapper.readValue(mapper.writeValueAsString(position), Schadenposition.class);

        assertThat(back.getBetrag().scale()).isEqualTo(2);
        assertThat(back.getBetrag().toPlainString()).isEqualTo("8.90");
    }

    @Test
    void date_fields_are_written_as_iso_strings() {
        Kunde kunde = new Kunde()
                .id(10001L)
                .vorname("Anna")
                .nachname("Mueller")
                .geburtsdatum(LocalDate.of(1990, 4, 12))
                .email("anna.mueller@example.com")
                .telefon("+49 30 1234567")
                .adresse(new Adresse().strasse("Musterstrasse 12").plz("10115").ort("Berlin").land("DE"))
                .tarifId(TarifId.ATRA_DENT_B)
                .versicherungsbeginn(LocalDate.of(2024, 3, 1))
                .vorversicherung(false)
                .fehlendeZaehne(0);

        String json = mapper.writeValueAsString(kunde);

        assertThat(json).contains("\"geburtsdatum\":\"1990-04-12\"");
        assertThat(json).contains("\"versicherungsbeginn\":\"2024-03-01\"");
    }
}
