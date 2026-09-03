package de.atra.kernsystem.api;

import de.atra.kernsystem.WithWorkingCopy;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class KundenControllerTest extends WithWorkingCopy {

    @Test
    void listing_without_a_filter_returns_all_fifteen() {
        assertThat(mvc.get().uri("/api/v1/kunden").header("x-api-key", API_KEY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.length()").isEqualTo(15);
    }

    @Test
    void filter_by_nachname() {
        assertThat(mvc.get().uri("/api/v1/kunden?nachname=Müller").header("x-api-key", API_KEY))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0].vorname").isEqualTo("Anna");
    }

    @Test
    void filtering_by_status_finds_the_deactivated_kunde() {
        assertThat(mvc.get().uri("/api/v1/kunden?status=inaktiv").header("x-api-key", API_KEY))
                .hasStatusOk()
                .bodyJson().extractingPath("$[0].nachname").isEqualTo("Yilmaz");
    }

    @Test
    void several_filters_are_combined_with_and() {
        assertThat(mvc.get().uri("/api/v1/kunden?vorname=Anna&nachname=Schuster").header("x-api-key", API_KEY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.length()").isEqualTo(0);
    }

    @Test
    void filter_by_geburtsdatum() {
        assertThat(mvc.get().uri("/api/v1/kunden?geburtsdatum=1990-04-12").header("x-api-key", API_KEY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.length()").isEqualTo(1);
    }

    @Test
    void read_a_kunde() {
        assertThat(mvc.get().uri("/api/v1/kunden/10004").header("x-api-key", API_KEY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.nachname").isEqualTo("Papadakis");
    }

    @Test
    void ein_unbekannter_kunde_ist_ein_404_als_problem_json() {
        assertThat(mvc.get().uri("/api/v1/kunden/99999").header("x-api-key", API_KEY))
                .hasStatus(404)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void creating_assigns_the_next_nummer_and_sets_the_location_header() {
        var result = mvc.post().uri("/api/v1/kunden")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vorname":"Greta","nachname":"Hoffmann","geburtsdatum":"1988-02-29",
                         "email":"greta.hoffmann@example.com","telefon":"+49 711 1122334",
                         "adresse":{"strasse":"Koenigstrasse 5","plz":"70173","ort":"Stuttgart","land":"DE"},
                         "tarifId":"ATRA_DENT_X","versicherungsbeginn":"2026-09-01",
                         "vorversicherung":true,"fehlendeZaehne":0}
                        """)
                .exchange();

        assertThat(result).hasStatus(201);
        assertThat(result).containsHeader("Location");
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(10049);
    }

    @Test
    void anlegen_ohne_pflichtfeld_ist_ein_400() {
        assertThat(mvc.post().uri("/api/v1/kunden")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vorname":"Ohne","nachname":"Geburtsdatum"}
                        """))
                .hasStatus(400);
    }

    @Test
    void a_partial_update_leaves_the_remaining_fields_untouched() {
        assertThat(mvc.patch().uri("/api/v1/kunden/10002")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"telefon":"+49 40 0000000"}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.nachname").isEqualTo("Schuster");

        assertThat(mvc.get().uri("/api/v1/kunden/10002").header("x-api-key", API_KEY))
                .bodyJson().extractingPath("$.telefon").isEqualTo("+49 40 0000000");
    }

    @Test
    void deactivating_happens_via_the_status_not_via_delete() {
        assertThat(mvc.patch().uri("/api/v1/kunden/10001")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"inaktiv"}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("inaktiv");
    }

    @Test
    void replacing_overwrites_completely_and_keeps_the_id() {
        assertThat(mvc.put().uri("/api/v1/kunden/10005")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vorname":"Fatima","nachname":"Yilmaz-Berger","geburtsdatum":"2001-05-19",
                         "email":"fatima.berger@example.com","telefon":"+49 341 7788990",
                         "adresse":{"strasse":"Karl-Liebknecht-Strasse 21","plz":"04107","ort":"Leipzig","land":"DE"},
                         "tarifId":"ATRA_DENT_B","versicherungsbeginn":"2022-09-01",
                         "vorversicherung":false,"fehlendeZaehne":0,"status":"aktiv"}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.nachname").isEqualTo("Yilmaz-Berger");
    }

    @Test
    void a_put_without_a_status_leaves_a_deactivated_kunde_deactivated() {
        assertThat(mvc.put().uri("/api/v1/kunden/10005")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vorname":"Fatima","nachname":"Yilmaz","geburtsdatum":"2001-05-19",
                         "email":"fatima.yilmaz@example.com","telefon":"+49 341 7788990",
                         "adresse":{"strasse":"Karl-Liebknecht-Strasse 99","plz":"04107","ort":"Leipzig","land":"DE"},
                         "tarifId":"ATRA_DENT_B","versicherungsbeginn":"2022-09-01",
                         "vorversicherung":false,"fehlendeZaehne":0}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("inaktiv");

        assertThat(mvc.get().uri("/api/v1/kunden/10005").header("x-api-key", API_KEY))
                .bodyJson().extractingPath("$.status").isEqualTo("inaktiv");
    }

    @Test
    void a_put_with_a_status_still_sets_it() {
        assertThat(mvc.put().uri("/api/v1/kunden/10005")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vorname":"Fatima","nachname":"Yilmaz","geburtsdatum":"2001-05-19",
                         "email":"fatima.yilmaz@example.com","telefon":"+49 341 7788990",
                         "adresse":{"strasse":"Karl-Liebknecht-Strasse 21","plz":"04107","ort":"Leipzig","land":"DE"},
                         "tarifId":"ATRA_DENT_B","versicherungsbeginn":"2022-09-01",
                         "vorversicherung":false,"fehlendeZaehne":0,"status":"aktiv"}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("aktiv");
    }

    @Test
    void creating_without_a_status_stays_aktiv() {
        assertThat(mvc.post().uri("/api/v1/kunden")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vorname":"Greta","nachname":"Hoffmann","geburtsdatum":"1988-02-29",
                         "email":"greta.hoffmann@example.com","telefon":"+49 711 1122334",
                         "adresse":{"strasse":"Koenigstrasse 5","plz":"70173","ort":"Stuttgart","land":"DE"},
                         "tarifId":"ATRA_DENT_X","versicherungsbeginn":"2026-09-01",
                         "vorversicherung":true,"fehlendeZaehne":0}
                        """))
                .hasStatus(201)
                .bodyJson().extractingPath("$.status").isEqualTo("aktiv");
    }

    @Test
    void ohne_schluessel_kommt_401() {
        assertThat(mvc.get().uri("/api/v1/kunden")).hasStatus(401);
    }
}
