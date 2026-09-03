package de.atra.kernsystem.api;

import de.atra.kernsystem.WithWorkingCopy;
import de.atra.kernsystem.domain.ConflictException;
import de.atra.kernsystem.generated.model.Fehler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorHandlingTest extends WithWorkingCopy {

    private static final String KUNDE_BODY = """
            "vorname":"Test","nachname":"Person","geburtsdatum":"1990-01-01",
            "email":"test.person@example.com","telefon":"+49 711 1234567",
            "adresse":{"strasse":"Koenigstrasse 1","plz":"70173","ort":"Stuttgart","land":"DE"},
            "versicherungsbeginn":"2026-09-01","vorversicherung":true
            """;

    @Test
    void syntaktisch_kaputtes_json_ist_ein_400() {
        assertThat(mvc.post().uri("/api/v1/kunden")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vorname\": "))
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void ein_falscher_typ_im_anfragekoerper_ist_ein_400() {
        assertThat(mvc.post().uri("/api/v1/kunden")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + KUNDE_BODY + """
                        ,"tarifId":"ATRA_DENT_B","fehlendeZaehne":"viele"}
                        """))
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void ein_unbekannter_enum_wert_im_anfragekoerper_ist_ein_400() {
        assertThat(mvc.post().uri("/api/v1/kunden")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + KUNDE_BODY + """
                        ,"tarifId":"GIBTESNICHT","fehlendeZaehne":0}
                        """))
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void eine_min_verletzung_am_pfadparameter_ist_ein_400() {
        assertThat(mvc.get().uri("/api/v1/kunden/0").header("x-api-key", API_KEY))
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void eine_min_verletzung_am_abfrageparameter_ist_ein_400() {
        assertThat(mvc.get().uri("/api/v1/schadensfaelle?kundenId=0").header("x-api-key", API_KEY))
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void ein_nicht_unterstuetzter_medientyp_ist_ein_415() {
        assertThat(mvc.post().uri("/api/v1/kunden")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.TEXT_PLAIN)
                .content("hallo"))
                .hasStatus(415)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void eine_nicht_unterstuetzte_methode_ist_ein_405() {
        assertThat(mvc.delete().uri("/api/v1/kunden/10001").header("x-api-key", API_KEY))
                .hasStatus(405)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void ein_unbekannter_pfad_ist_ein_404() {
        assertThat(mvc.get().uri("/api/v1/gibtesnicht").header("x-api-key", API_KEY))
                .hasStatus(404)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void the_inherited_handlers_also_return_the_error_schema() {
        assertThat(mvc.delete().uri("/api/v1/kunden/10001").header("x-api-key", API_KEY))
                .bodyJson().extractingPath("$.status").isEqualTo(405);
        assertThat(mvc.delete().uri("/api/v1/kunden/10001").header("x-api-key", API_KEY))
                .bodyJson().extractingPath("$.title").isEqualTo("Methode nicht erlaubt");
        assertThat(mvc.delete().uri("/api/v1/kunden/10001").header("x-api-key", API_KEY))
                .bodyJson().extractingPath("$.type").isEqualTo("https://atra.example/fehler/anfrage");
    }

    @Test
    void validation_messages_come_out_without_umlauts() {
        var result = mvc.post().uri("/api/v1/kunden")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + KUNDE_BODY + """
                        ,"tarifId":"ATRA_DENT_B","fehlendeZaehne":-1}
                        """)
                .exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyText().doesNotContainPattern("[\u00e4\u00f6\u00fc\u00c4\u00d6\u00dc\u00df]");
    }

    @Test
    void ein_echter_serverfehler_bleibt_500_ohne_interna() {
        RuntimeException cause = new IllegalStateException("Verbindung zu geheim-intern-4711 verloren");

        ResponseEntity<Fehler> response = new ErrorHandling().unexpectedError(cause);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        Fehler body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getDetail()).isEqualTo("Ein unerwarteter Fehler ist aufgetreten");
        assertThat(body.getDetail()).doesNotContain("geheim-intern-4711", "IllegalStateException");
    }

    @Test
    void ein_konflikt_ist_eine_409_im_problem_json() {
        var response = new ErrorHandling().conflict(new ConflictException("Fall ist schon in Pruefung"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getType()).hasToString("https://atra.example/fehler/konflikt");
        assertThat(response.getBody().getDetail()).isEqualTo("Fall ist schon in Pruefung");
    }
}
