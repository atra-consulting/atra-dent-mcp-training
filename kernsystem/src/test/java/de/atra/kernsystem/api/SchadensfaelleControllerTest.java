package de.atra.kernsystem.api;

import de.atra.kernsystem.WithWorkingCopy;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class SchadensfaelleControllerTest extends WithWorkingCopy {

    @Test
    void listing_returns_all_twenty_nine() {
        assertThat(mvc.get().uri("/api/v1/schadensfaelle").header("x-api-key", API_KEY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.length()").isEqualTo(29);
    }

    @Test
    void filter_by_kunde() {
        assertThat(mvc.get().uri("/api/v1/schadensfaelle?kundenId=10001").header("x-api-key", API_KEY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.length()").isEqualTo(4);
    }

    @Test
    void filter_by_status() {
        assertThat(mvc.get().uri("/api/v1/schadensfaelle?status=abgelehnt").header("x-api-key", API_KEY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.length()").isEqualTo(7);
    }

    @Test
    void kundenId_and_status_are_combined_with_and() {
        assertThat(mvc.get().uri("/api/v1/schadensfaelle?kundenId=10002&status=abgelehnt")
                .header("x-api-key", API_KEY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.length()").isEqualTo(3);
    }

    @Test
    void the_history_of_a_kunde_via_the_convenience_path() {
        assertThat(mvc.get().uri("/api/v1/kunden/10004/schadensfaelle").header("x-api-key", API_KEY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.length()").isEqualTo(3);
    }

    @Test
    void die_historie_eines_unbekannten_kunden_ist_ein_404() {
        assertThat(mvc.get().uri("/api/v1/kunden/99999/schadensfaelle").header("x-api-key", API_KEY))
                .hasStatus(404);
    }

    @Test
    void a_kunde_without_history_gets_an_empty_list_not_an_error() {
        assertThat(mvc.get().uri("/api/v1/kunden/10005/schadensfaelle").header("x-api-key", API_KEY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.length()").isEqualTo(0);
    }

    @Test
    void read_a_schadensfall() {
        assertThat(mvc.get().uri("/api/v1/schadensfaelle/50011").header("x-api-key", API_KEY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.erstattungsbetrag").isEqualTo("5000.00");
    }

    @Test
    void an_unprocessed_schadensfall_has_no_erstattungsbetrag_yet() {
        assertThat(mvc.get().uri("/api/v1/schadensfaelle/50014").header("x-api-key", API_KEY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.erstattungsbetrag").isNull();
    }

    @Test
    void submitting_sums_the_rechnungsbetrag_itself_and_starts_at_eingereicht() {
        var result = mvc.post().uri("/api/v1/schadensfaelle")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kundenId":10001,"behandlungsdatum":"2026-08-10","positionen":[
                          {"goz":"2150","leistungsbereich":"INL","betrag":"420.00","beschreibung":"Keramikinlay zweiflaechig Zahn 26"},
                          {"goz":"2197","leistungsbereich":"INL","betrag":"62.10","beschreibung":"Adhaesive Befestigung"}]}
                        """)
                .exchange();

        assertThat(result).hasStatus(201);
        assertThat(result).containsHeader("Location");
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(50073);
        assertThat(result).bodyJson().extractingPath("$.rechnungsbetrag").isEqualTo("482.10");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("eingereicht");
        assertThat(result).bodyJson().extractingPath("$.erstattungsbetrag").isNull();
    }

    @Test
    void submitting_takes_a_position_without_a_gebuehrennummer() {
        var result = mvc.post().uri("/api/v1/schadensfaelle")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kundenId":10001,"behandlungsdatum":"2026-04-28","positionen":[
                          {"goz":"9010","zahn":"46","datum":"2026-04-28","anzahl":1,
                           "betrag":"410.20","beschreibung":"Implantatinsertion"},
                          {"goz":null,"zahn":"46","datum":"2026-04-28","anzahl":1,
                           "betrag":"305.00","beschreibung":"Implantatkoerper und Verschlussschraube"}]}
                        """)
                .exchange();

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.rechnungsbetrag").isEqualTo("715.20");
        assertThat(result).bodyJson().extractingPath("$.positionen[1].goz").isNull();
        assertThat(result).bodyJson().extractingPath("$.positionen[1].beschreibung")
                .isEqualTo("Implantatkoerper und Verschlussschraube");
    }

    @Test
    void submitting_takes_over_zahn_datum_and_anzahl_per_position() {
        var result = mvc.post().uri("/api/v1/schadensfaelle")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kundenId":10001,"behandlungsdatum":"2026-06-08","positionen":[
                          {"goz":"9010","zahn":"36","datum":"2026-06-08","anzahl":1,
                           "betrag":"410.20","beschreibung":"Implantatinsertion"},
                          {"goz":"1040","zahn":null,"datum":null,"anzahl":null,
                           "betrag":"118.00","beschreibung":"Professionelle Zahnreinigung"}]}
                        """)
                .exchange();

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.positionen[0].zahn").isEqualTo("36");
        assertThat(result).bodyJson().extractingPath("$.positionen[0].datum").isEqualTo("2026-06-08");
        assertThat(result).bodyJson().extractingPath("$.positionen[0].anzahl").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.positionen[1].zahn").isNull();
    }

    @Test
    void einreichen_fuer_einen_unbekannten_kunden_ist_ein_400() {
        assertThat(mvc.post().uri("/api/v1/schadensfaelle")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kundenId":99999,"behandlungsdatum":"2026-08-10","positionen":[
                          {"goz":"1040","leistungsbereich":"PZR","betrag":"118.00","beschreibung":"PZR"}]}
                        """))
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void einreichen_ohne_position_ist_ein_400() {
        assertThat(mvc.post().uri("/api/v1/schadensfaelle")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kundenId":10001,"behandlungsdatum":"2026-08-10","positionen":[]}
                        """))
                .hasStatus(400);
    }

    @Test
    void the_status_change_during_processing_sets_the_erstattungsbetrag() {
        assertThat(mvc.patch().uri("/api/v1/schadensfaelle/50009")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"genehmigt","erstattungsbetrag":"2000.00"}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.erstattungsbetrag").isEqualTo("2000.00");
    }

    @Test
    void a_rejection_carries_grund_and_hinweis() {
        assertThat(mvc.patch().uri("/api/v1/schadensfaelle/50014")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"abgelehnt","erstattungsbetrag":"0.00",
                         "ablehnungsgrund":"SONSTIGES",
                         "ablehnungshinweis":"Jahreshoechstgrenze fuer dieses Versicherungsjahr ausgeschoepft"}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.ablehnungsgrund").isEqualTo("SONSTIGES");
    }

    @Test
    void unbekannter_status_ist_ein_400() {
        assertThat(mvc.get().uri("/api/v1/schadensfaelle?status=gibtesnicht").header("x-api-key", API_KEY))
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void ein_positionsbetrag_mit_mehr_als_zwei_nachkommastellen_ist_ein_400() {
        assertThat(mvc.post().uri("/api/v1/schadensfaelle")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kundenId":10001,"behandlungsdatum":"2026-08-10","positionen":[
                          {"goz":"1040","leistungsbereich":"PZR","betrag":"12.3456","beschreibung":"PZR"}]}
                        """))
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void position_betraege_with_at_most_two_decimals_are_accepted() {
        assertThat(mvc.post().uri("/api/v1/schadensfaelle")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kundenId":10001,"behandlungsdatum":"2026-08-10","positionen":[
                          {"goz":"1040","leistungsbereich":"PZR","betrag":"12.30","beschreibung":"PZR"},
                          {"goz":"1040","leistungsbereich":"PZR","betrag":"12.3","beschreibung":"PZR"}]}
                        """))
                .hasStatus(201)
                .bodyJson().extractingPath("$.rechnungsbetrag").isEqualTo("24.60");
    }

    @Test
    void an_erstattungsbetrag_with_more_than_two_decimals_is_a_400() {
        assertThat(mvc.patch().uri("/api/v1/schadensfaelle/50009")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"genehmigt","erstattungsbetrag":"12.3456"}
                        """))
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        assertThat(mvc.get().uri("/api/v1/schadensfaelle/50009").header("x-api-key", API_KEY))
                .bodyJson().extractingPath("$.status").isEqualTo("in_pruefung");
    }

    @Test
    void an_erstattungsbetrag_with_two_decimals_is_accepted() {
        assertThat(mvc.patch().uri("/api/v1/schadensfaelle/50009")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"erstattungsbetrag":"12.3"}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.erstattungsbetrag").isEqualTo("12.3");
    }

    @Test
    void ohne_schluessel_kommt_401() {
        assertThat(mvc.get().uri("/api/v1/schadensfaelle")).hasStatus(401);
    }

    @Test
    void the_status_filter_takes_several_values() {
        assertThat(mvc.get().uri("/api/v1/schadensfaelle?status=eingereicht,in_pruefung")
                .header("x-api-key", API_KEY))
                .hasStatusOk()
                .bodyJson().extractingPath("$.length()").isEqualTo(3);
    }

    @Test
    void submitting_takes_over_the_rechnungskopf_and_logs() {
        var result = mvc.post().uri("/api/v1/schadensfaelle")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kundenId":10001,"behandlungsdatum":"2026-08-10",
                         "rechnung":{"rechnungsnummer":"2026-4711","rechnungsdatum":"2026-08-12",
                                     "absender":"Dr. Beisser","patient":"Anna Mueller","gesamtbetrag":"482.10"},
                         "positionen":[
                          {"goz":"2150","betrag":"420.00","beschreibung":"Keramikinlay zweiflaechig Zahn 26"},
                          {"goz":"2197","betrag":"62.10","beschreibung":"Adhaesive Befestigung"}]}
                        """)
                .exchange();

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.rechnung.rechnungsnummer").isEqualTo("2026-4711");
        assertThat(result).bodyJson().extractingPath("$.positionen[0].leistungsbereich").isNull();
        assertThat(result).bodyJson().extractingPath("$.bearbeitungsprotokoll.length()").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.bearbeitungsprotokoll[0].akteur").isEqualTo("sachbearbeitung");
        assertThat(result).bodyJson().extractingPath("$.bearbeitungsprotokoll[0].schritt").isEqualTo("Schadensfall eingereicht");
    }

    @Test
    void claiming_sets_in_pruefung_and_logs_the_agent() {
        var result = mvc.patch().uri("/api/v1/schadensfaelle/50014")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"in_pruefung","erwarteterStatus":"eingereicht"}
                        """)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("in_pruefung");
        assertThat(result).bodyJson().extractingPath("$.bearbeitungsprotokoll[-1].akteur").isEqualTo("agent");
        assertThat(result).bodyJson().extractingPath("$.bearbeitungsprotokoll[-1].schritt")
                .isEqualTo("Status eingereicht -> in_pruefung");
    }

    @Test
    void das_zweite_claiming_verliert_mit_409() {
        mvc.patch().uri("/api/v1/schadensfaelle/50014")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"in_pruefung","erwarteterStatus":"eingereicht"}
                        """)
                .exchange();

        assertThat(mvc.patch().uri("/api/v1/schadensfaelle/50014")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"in_pruefung","erwarteterStatus":"eingereicht"}
                        """))
                .hasStatus(409)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.type").isEqualTo("https://atra.example/fehler/konflikt");
    }

    @Test
    void a_re_save_of_the_same_status_without_a_precondition_is_ok() {
        var result = mvc.patch().uri("/api/v1/schadensfaelle/50009")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"in_pruefung","erstattungsbetrag":"10.00"}
                        """)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("in_pruefung");
        assertThat(result).bodyJson().extractingPath("$.erstattungsbetrag").isEqualTo("10.00");
        assertThat(result).bodyJson().extractingPath("$.bearbeitungsprotokoll.length()").isEqualTo(0);
    }

    @Test
    void eine_falsche_vorbedingung_ist_ein_409() {
        assertThat(mvc.patch().uri("/api/v1/schadensfaelle/50009")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"genehmigt","erstattungsbetrag":"1.00","erwarteterStatus":"eingereicht"}
                        """))
                .hasStatus(409)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void geprueft_cannot_be_set_via_patch() {
        assertThat(mvc.patch().uri("/api/v1/schadensfaelle/50014")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"geprueft_freigabe"}
                        """))
                .hasStatus(409);
    }

    @Test
    void a_final_status_cannot_be_left() {
        assertThat(mvc.patch().uri("/api/v1/schadensfaelle/50001")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"eingereicht"}
                        """))
                .hasStatus(409);
    }

    @Test
    void the_same_status_again_is_no_conflict_and_no_protokolleintrag() {
        var result = mvc.patch().uri("/api/v1/schadensfaelle/50014")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"eingereicht"}
                        """)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.bearbeitungsprotokoll.length()").isEqualTo(0);
    }

    @Test
    void without_an_akteur_the_reset_stays_the_system_action_of_the_poller() {
        var result = mvc.patch().uri("/api/v1/schadensfaelle/50009")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"eingereicht","erwarteterStatus":"in_pruefung"}
                        """)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.bearbeitungsprotokoll[-1].akteur").isEqualTo("system");
        assertThat(result).bodyJson().extractingPath("$.bearbeitungsprotokoll[-1].schritt")
                .isEqualTo("Status in_pruefung -> eingereicht");
    }

    @Test
    void on_the_same_edge_a_named_akteur_logs_the_sachbearbeitung() {
        var result = mvc.patch().uri("/api/v1/schadensfaelle/50009")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"eingereicht","akteur":"sachbearbeitung"}
                        """)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("eingereicht");
        assertThat(result).bodyJson().extractingPath("$.bearbeitungsprotokoll[-1].akteur")
                .isEqualTo("sachbearbeitung");
        assertThat(result).bodyJson().extractingPath("$.bearbeitungsprotokoll[-1].schritt")
                .isEqualTo("Status in_pruefung -> eingereicht");
    }

    @Test
    void a_named_akteur_does_not_open_a_forbidden_transition() {
        assertThat(mvc.patch().uri("/api/v1/schadensfaelle/50001")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"eingereicht","akteur":"sachbearbeitung"}
                        """))
                .hasStatus(409);
    }

    private static final String BEWERTUNG_FREIGABE = """
            {"bewertung":{
               "empfehlung":"freigabe",
               "erstattungsvorschlag":"336.00",
               "eskalationsgruende":[],
               "positionen":[
                 {"goz":"2150","leistungsbereich":"INL","zustand":"ENTHALTEN","begruendung":"Inlay ist im Leistungsbereich INL versichert (Par. 4 Abs. 2)"},
                 {"goz":"2197","leistungsbereich":"INL","zustand":"ENTHALTEN","begruendung":"Befestigung gehoert zur Inlayversorgung"}],
               "arztauskunft":{"plausibilitaet":"plausibel","notwendigkeit":"ueblich",
                               "text":"Zweiflaechiges Inlay mit adhaesiver Befestigung ist eine uebliche Kombination.",
                               "hinweis":"Fachauskunft eines Sprachmodells, nicht belegt, keine Entscheidung."},
               "begruendung":"Beide Positionen versichert, Verbrauch im Jahr unter der Staffel.",
               "agent":"schadensfall","modell":"gemini-3.6-flash"},
             "protokoll":[
               {"zeitpunkt":"2026-08-18T10:00:00Z","akteur":"agent","schritt":"Vertrag gelesen","detail":"ATRA_DENT_B, aktiv",
                "calls":[{"zeitpunkt":"2026-08-18T10:00:00Z","art":"tool","name":"mein_vertrag_lesen","ergebnisKurz":"ATRA_DENT_B","dauerMs":120}]},
               {"zeitpunkt":"2026-08-18T10:00:02Z","akteur":"agent","schritt":"GOZ-Pruefung","detail":"2 von 2 ENTHALTEN"}]}
            """;

    private long submittedAndInPruefung() throws Exception {
        var updated = mvc.post().uri("/api/v1/schadensfaelle")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kundenId":10001,"behandlungsdatum":"2026-08-10","positionen":[
                          {"goz":"2150","betrag":"420.00","beschreibung":"Keramikinlay zweiflaechig Zahn 26"},
                          {"goz":"2197","betrag":"62.10","beschreibung":"Adhaesive Befestigung"}]}
                        """)
                .exchange();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(
                updated.getResponse().getContentAsString(), "$.id")).longValue();
        mvc.patch().uri("/api/v1/schadensfaelle/" + id)
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"in_pruefung\"}")
                .exchange();
        return id;
    }

    @Test
    void the_bewertung_sets_geprueft_freigabe_fills_in_leistungsbereiche_and_logs() throws Exception {
        long id = submittedAndInPruefung();

        var result = mvc.put().uri("/api/v1/schadensfaelle/" + id + "/bewertung")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BEWERTUNG_FREIGABE)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("geprueft_freigabe");
        assertThat(result).bodyJson().extractingPath("$.bewertung.erstattungsvorschlag").isEqualTo("336.00");
        assertThat(result).bodyJson().extractingPath("$.bewertung.zeitpunkt").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.erstattungsbetrag").isNull();
        assertThat(result).bodyJson().extractingPath("$.positionen[0].leistungsbereich").isEqualTo("INL");
        assertThat(result).bodyJson().extractingPath("$.positionen[1].leistungsbereich").isEqualTo("INL");
        assertThat(result).bodyJson().extractingPath("$.bearbeitungsprotokoll.length()").isEqualTo(5);
        assertThat(result).bodyJson().extractingPath("$.bearbeitungsprotokoll[2].calls[0].name").isEqualTo("mein_vertrag_lesen");
        assertThat(result).bodyJson().extractingPath("$.bearbeitungsprotokoll[4].schritt")
                .isEqualTo("Status in_pruefung -> geprueft_freigabe");
        assertThat(result).bodyJson().extractingPath("$.bearbeitungsprotokoll[4].akteur").isEqualTo("agent");
    }

    private long submittedWithPositionenWithoutZiffer() throws Exception {
        var updated = mvc.post().uri("/api/v1/schadensfaelle")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"kundenId":10001,"behandlungsdatum":"2026-04-28","positionen":[
                          {"goz":"9010","zahn":"46","betrag":"410.20","beschreibung":"Implantatinsertion"},
                          {"goz":null,"zahn":"46","betrag":"305.00","beschreibung":"Implantatkoerper"},
                          {"goz":"2200","zahn":"46","betrag":"218.40","beschreibung":"Vollkrone"},
                          {"goz":null,"zahn":"46","betrag":"486.30","beschreibung":"Vollkeramische Krone, Zirkondioxid"}]}
                        """)
                .exchange();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(
                updated.getResponse().getContentAsString(), "$.id")).longValue();
        mvc.patch().uri("/api/v1/schadensfaelle/" + id)
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"in_pruefung\"}")
                .exchange();
        return id;
    }

    @Test
    void the_bewertung_fills_in_the_leistungsbereich_via_the_index() throws Exception {
        long id = submittedWithPositionenWithoutZiffer();

        var result = mvc.put().uri("/api/v1/schadensfaelle/" + id + "/bewertung")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bewertung":{
                           "empfehlung":"freigabe",
                           "erstattungsvorschlag":"1419.90",
                           "eskalationsgruende":[],
                           "positionen":[
                             {"index":3,"goz":null,"leistungsbereich":"ZE","zustand":"NICHT_BESTIMMBAR","begruendung":"Laborkosten der Krone an Zahn 46"},
                             {"index":1,"goz":null,"leistungsbereich":"IMP","zustand":"NICHT_BESTIMMBAR","begruendung":"Implantatkoerper folgt der Insertion an Zahn 46"},
                             {"index":2,"goz":"2200","leistungsbereich":"ZE","zustand":"ENTHALTEN","begruendung":"Vollkrone, Leistungsbereich ZE"},
                             {"index":0,"goz":"9010","leistungsbereich":"IMP","zustand":"ENTHALTEN","begruendung":"Implantatinsertion, Leistungsbereich IMP"}],
                           "begruendung":"Alle vier Positionen zugeordnet.",
                           "agent":"schadensfall"}}
                        """)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.positionen[0].leistungsbereich").isEqualTo("IMP");
        assertThat(result).bodyJson().extractingPath("$.positionen[1].leistungsbereich").isEqualTo("IMP");
        assertThat(result).bodyJson().extractingPath("$.positionen[2].leistungsbereich").isEqualTo("ZE");
        assertThat(result).bodyJson().extractingPath("$.positionen[3].leistungsbereich").isEqualTo("ZE");
    }

    @Test
    void eine_bewertungsposition_mit_index_ausserhalb_der_positionen_ist_ein_400() throws Exception {
        long id = submittedWithPositionenWithoutZiffer();

        assertThat(mvc.put().uri("/api/v1/schadensfaelle/" + id + "/bewertung")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bewertung":{
                           "empfehlung":"freigabe",
                           "erstattungsvorschlag":"100.00",
                           "eskalationsgruende":[],
                           "positionen":[
                             {"index":9,"goz":null,"leistungsbereich":"ZE","zustand":"NICHT_BESTIMMBAR","begruendung":"Labor"}],
                           "begruendung":"x",
                           "agent":"schadensfall"}}
                        """))
                .hasStatus(400);
    }

    @Test
    void a_eskalation_needs_a_grund() throws Exception {
        long id = submittedAndInPruefung();

        assertThat(mvc.put().uri("/api/v1/schadensfaelle/" + id + "/bewertung")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bewertung":{"empfehlung":"eskalation","eskalationsgruende":[],"positionen":[],
                                      "begruendung":"?","agent":"schadensfall"}}
                        """))
                .hasStatus(400);
    }

    @Test
    void a_eskalation_with_a_grund_sets_geprueft_eskalation() throws Exception {
        long id = submittedAndInPruefung();

        var result = mvc.put().uri("/api/v1/schadensfaelle/" + id + "/bewertung")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bewertung":{"empfehlung":"eskalation",
                                      "eskalationsgruende":[{"code":"GOZ_UNCLEAR","text":"2197 NICHT_BESTIMMBAR ohne Kontext"}],
                                      "positionen":[{"goz":"2197","zustand":"NICHT_BESTIMMBAR","begruendung":"Zuordnung haengt an der Hauptleistung"}],
                                      "begruendung":"Bitte Zuordnung pruefen.","agent":"schadensfall"}}
                        """)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("geprueft_eskalation");
        assertThat(result).bodyJson().extractingPath("$.bewertung.eskalationsgruende[0].code").isEqualTo("GOZ_UNCLEAR");
    }

    @Test
    void a_freigabe_needs_an_erstattungsvorschlag() throws Exception {
        long id = submittedAndInPruefung();

        assertThat(mvc.put().uri("/api/v1/schadensfaelle/" + id + "/bewertung")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bewertung":{"empfehlung":"freigabe","eskalationsgruende":[],"positionen":[],
                                      "begruendung":"passt","agent":"schadensfall"}}
                        """))
                .hasStatus(400);
    }

    @Test
    void bewerten_ohne_claiming_ist_ein_409() {
        assertThat(mvc.put().uri("/api/v1/schadensfaelle/50014/bewertung")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BEWERTUNG_FREIGABE))
                .hasStatus(409);
    }

    @Test
    void die_zweite_bewertung_ist_ein_409() throws Exception {
        long id = submittedAndInPruefung();
        mvc.put().uri("/api/v1/schadensfaelle/" + id + "/bewertung")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BEWERTUNG_FREIGABE)
                .exchange();

        assertThat(mvc.put().uri("/api/v1/schadensfaelle/" + id + "/bewertung")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BEWERTUNG_FREIGABE))
                .hasStatus(409);
    }

    @Test
    void after_the_bewertung_the_sachbearbeitung_decides() throws Exception {
        long id = submittedAndInPruefung();
        mvc.put().uri("/api/v1/schadensfaelle/" + id + "/bewertung")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BEWERTUNG_FREIGABE)
                .exchange();

        var result = mvc.patch().uri("/api/v1/schadensfaelle/" + id)
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"genehmigt","erstattungsbetrag":"336.00"}
                        """)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("genehmigt");
        assertThat(result).bodyJson().extractingPath("$.bearbeitungsprotokoll[-1].akteur").isEqualTo("sachbearbeitung");
    }
}
