package consulting.atra.agenten.beratung.view;

import consulting.atra.agenten.a2a.agent.Protocol;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.a2a.agent.TracePoint;
import consulting.atra.agenten.beratung.tools.BeratungMessages;
import consulting.atra.agenten.beratung.tools.ToolSelection;
import consulting.atra.agenten.mcp.ObservedTools;
import consulting.atra.agenten.mcp.McpWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ViewCollectorTest {

    private final ViewCollector collector = new ViewCollector(JsonMapper.builder().build());

    @Nested
    @DisplayName("the Tarif comparison")
    class Tarifvergleich {

        @Test
        @DisplayName("passes the result of the Wissensdienst through")
        void passedThrough() {
            var turn = new Zug().calls("tarife_vergleichen", "{}", COMPARISON);

            Map<String, Object> view = only(turn);

            assertThat(view).containsEntry("kind", ViewCollector.TARIFVERGLEICH);
            assertThat(view).containsKeys("stand", "tarife", "leistungsbereiche");
            assertThat(values(view, "tarife")).hasSize(2);
            assertThat(first(view, "leistungsbereiche")).containsEntry("schluessel", "IMP");
        }

        @Test
        @DisplayName("takes the last call -- it answers the most recent question")
        void theLastOneWins() {
            var turn = new Zug()
                    .calls("tarife_vergleichen", "{}", COMPARISON)
                    .calls("tarife_vergleichen", "{}", COMPARISON.replace("\"IMP\"", "\"PZR\""));

            assertThat(first(only(turn), "leistungsbereiche")).containsEntry("schluessel", "PZR");
        }

        @Test
        @DisplayName("marks the column that belongs to the Kundin")
        void itsOwnColumn() {
            var turn = new Zug()
                    .calls("mein_vertrag_lesen", "{}", VERTRAG)
                    .calls("tarife_vergleichen", "{}", COMPARISON);

            assertThat(vertragTarif(only(turn)))
                    .containsEntry("schluessel", "ATRA_DENT_B")
                    .containsEntry("anzeigename", "atra.dent.balance");
        }

        @Test
        @DisplayName("without a Vertrag having been read nothing is marked")
        void withoutAVertragNoMarking() {
            Map<String, Object> view = only(new Zug().calls("tarife_vergleichen", "{}", COMPARISON));

            assertThat(view).doesNotContainKey(ViewCollector.VERTRAG_TARIF);
            assertThat(view).containsKeys("stand", "tarife", "leistungsbereiche");
        }

        @Test
        @DisplayName("marks a Tarif that is not in the table at all too")
        void theOwnTarifOutside() {
            var turn = new Zug()
                    .calls("mein_vertrag_lesen", "{}", VERTRAG.replace("ATRA_DENT_B", "ATRA_DENT_S"))
                    .calls("tarife_vergleichen", "{}", COMPARISON);

            assertThat(vertragTarif(only(turn)))
                    .containsEntry("schluessel", "ATRA_DENT_S")
                    .containsEntry("anzeigename", null);
        }
    }

    @Nested
    @DisplayName("the Beitrag comparison")
    class Beitragsvergleich {

        @Test
        @DisplayName("takes the Tarif from the arguments and the Betrag from the result")
        void theTarifFromTheArguments() {
            var turn = new Zug()
                    .calls("tarife_auflisten", "{}", LIST)
                    .calls("beitrag_berechnen", arguments("ATRA_DENT_B"), "{\"monatsbeitrag\":\"15.90\"}")
                    .calls("beitrag_berechnen", arguments("ATRA_DENT_X"), "{\"monatsbeitrag\":\"24.90\"}");

            Map<String, Object> view = only(turn);

            assertThat(view).containsEntry("kind", ViewCollector.BEITRAG_COMPARISON);
            assertThat(values(view, "beitraege")).hasSize(2);
            assertThat(first(view, "beitraege"))
                    .containsEntry("tarif", "ATRA_DENT_B")
                    .containsEntry("monatsbeitrag", "15.90")
                    .containsEntry("anzeigename", "atra.dent.balance");
        }

        @Test
        @DisplayName("a single Beitrag is no comparison")
        void oneIsNotEnough() {
            var turn = new Zug().calls("beitrag_berechnen", arguments("ATRA_DENT_B"),
                    "{\"monatsbeitrag\":\"15.90\"}");

            assertThat(collector.collect(turn.observed())).isEmpty();
        }

        @Test
        @DisplayName("the Anzeigename also comes from tarif_lesen")
        void nameFromReadTarif() {
            var turn = new Zug()
                    .calls("tarif_lesen", "{\"tarifId\":\"ATRA_DENT_B\"}",
                            "{\"id\":\"ATRA_DENT_B\",\"name\":\"atra.dent.balance\","
                                    + "\"basisbeitragMonatlich\":\"15.90\"}")
                    .calls("beitrag_berechnen", arguments("ATRA_DENT_B"), "{\"monatsbeitrag\":\"15.90\"}")
                    .calls("beitrag_berechnen", arguments("ATRA_DENT_X"), "{\"monatsbeitrag\":\"24.90\"}");

            assertThat(first(only(turn), "beitraege")).containsEntry("anzeigename", "atra.dent.balance");
        }

        @Test
        @DisplayName("says which of the Beitraege belongs to the caller's own Tarif")
        void theOwnBeitrag() {
            var turn = new Zug()
                    .calls("mein_vertrag_lesen", "{}", VERTRAG)
                    .calls("mein_beitrag_berechnen", "{\"tarifId\":\"ATRA_DENT_B\"}",
                            "{\"monatsbeitrag\":\"15.90\"}")
                    .calls("mein_beitrag_berechnen", "{\"tarifId\":\"ATRA_DENT_X\"}",
                            "{\"monatsbeitrag\":\"24.90\"}");

            assertThat(vertragTarif(only(turn))).containsEntry("schluessel", "ATRA_DENT_B");
        }

        @Test
        @DisplayName("without a Tarif list the Anzeigename stays empty instead of guessed")
        void noNameIsGuessed() {
            var turn = new Zug()
                    .calls("beitrag_berechnen", arguments("ATRA_DENT_B"), "{\"monatsbeitrag\":\"15.90\"}")
                    .calls("beitrag_berechnen", arguments("ATRA_DENT_X"), "{\"monatsbeitrag\":\"24.90\"}");

            assertThat(first(only(turn), "beitraege")).containsEntry("anzeigename", null);
        }

        @Test
        @DisplayName("a second call for the same Tarif replaces the first")
        void theSameTarifCountsOnce() {
            var turn = new Zug()
                    .calls("beitrag_berechnen", arguments("ATRA_DENT_B"), "{\"monatsbeitrag\":\"15.90\"}")
                    .calls("beitrag_berechnen", arguments("ATRA_DENT_B"), "{\"monatsbeitrag\":\"17.90\"}");

            assertThat(collector.collect(turn.observed())).isEmpty();
        }
    }

    @Nested
    @DisplayName("the Tarifempfehlung")
    class Tarifempfehlung {

        @Test
        @DisplayName("carries rank, Tarif and Anzeigename")
        void rankAndTarif() {
            Map<String, Object> view = only(new Zug().calls("tarifempfehlung", "{}", RECOMMENDATION));

            assertThat(view).containsEntry("kind", ViewCollector.TARIFEMPFEHLUNG);
            assertThat(first(view, "empfehlungen"))
                    .containsEntry("rang", 1)
                    .containsEntry("tarif", "ATRA_DENT_X")
                    .containsEntry("anzeigename", "atra.dent.brillant");
        }

        @Test
        @DisplayName("leaves every field of the Beratungsleitfaden out")
        void nothingFromTheLeitfaden() {
            Map<String, Object> view = only(new Zug().calls("tarifempfehlung", "{}", RECOMMENDATION));

            assertThat(view).doesNotContainKeys("zusammenfassung", "grundsatz", "complianceGrenzen",
                    "hinweise", "ausgeschlossene", "weg", "anliegen", "vertraulichkeit");
            assertThat(first(view, "empfehlungen"))
                    .containsOnlyKeys("rang", "tarif", "anzeigename");
        }

        @Test
        @DisplayName("says which of the recommended Tarife is the caller's own")
        void theOwnTarifInTheRanking() {
            var turn = new Zug()
                    .calls("mein_vertrag_lesen", "{}", VERTRAG)
                    .calls("tarifempfehlung", "{}", RECOMMENDATION);

            assertThat(vertragTarif(only(turn))).containsEntry("schluessel", "ATRA_DENT_B");
        }

        @Test
        @DisplayName("no Empfehlung means no ranking")
        void anEmptyEmpfehlung() {
            var turn = new Zug().calls("tarifempfehlung", "{}",
                    "{\"weg\":\"KEIN_TARIF\",\"empfehlungen\":[],\"vertraulichkeit\":\"intern\"}");

            assertThat(collector.collect(turn.observed())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Vertrag and Schadensfaelle")
    class Akte {

        @Test
        @DisplayName("the Vertrag comes only when nothing else does")
        void theVertragIsTheFallback() {
            var alone = new Zug().calls("mein_vertrag_lesen", "{}", VERTRAG);
            assertThat(only(alone)).containsEntry("kind", ViewCollector.VERTRAG);

            var beside = new Zug()
                    .calls("mein_vertrag_lesen", "{}", VERTRAG)
                    .calls("tarife_vergleichen", "{}", COMPARISON);
            assertThat(collector.collect(beside.observed()))
                    .extracting(view -> view.get("kind"))
                    .containsExactly(ViewCollector.TARIFVERGLEICH);
        }

        @Test
        @DisplayName("the Vertrag carries no Anschrift and no contact data")
        void noPersonalData() {
            Map<String, Object> view = only(new Zug().calls("mein_vertrag_lesen", "{}", VERTRAG));

            assertThat(view).doesNotContainKeys("adresse", "email", "telefon", "nachname", "id",
                    "geburtsdatum");
            assertThat(view).containsEntry("tarif", "ATRA_DENT_B")
                    .containsEntry("versicherungsbeginn", "2024-03-01");
        }

        @Test
        @DisplayName("schadensfaelle arrive as an array at the root")
        void schadensfaelleAsAList() {
            Map<String, Object> view =
                    only(new Zug().calls("meine_schadensfaelle_auflisten", "{}", CASES));

            assertThat(view).containsEntry("kind", ViewCollector.SCHADENSFAELLE);
            assertThat(values(view, "faelle")).hasSize(2);
            assertThat(first(view, "faelle"))
                    .containsEntry("status", "ausgezahlt")
                    .containsEntry("rechnungsbetrag", "1862.10");
            assertThat(values(view, "faelle").get(1)).containsEntry("erstattungsbetrag", null);
        }

        @Test
        @DisplayName("Positionen and Kundennummer stay out, the Schadensfall number is a number")
        void noRechnungspositionen() {
            Map<String, Object> view =
                    only(new Zug().calls("meine_schadensfaelle_auflisten", "{}", CASES));

            assertThat(first(view, "faelle")).doesNotContainKeys("positionen", "kundenId")
                    .containsEntry("id", 50001L);
        }

        @Test
        @DisplayName("no Schadensfaelle means no empty table")
        void anEmptyHistory() {
            var turn = new Zug().calls("meine_schadensfaelle_auflisten", "{}", "[]");

            assertThat(collector.collect(turn.observed())).isEmpty();
        }
    }

    @Nested
    @DisplayName("the Kontaktdaten after a change")
    class Kontaktdaten {

        @Test
        @DisplayName("what now stands in the Akte comes from the result of the change")
        void theCardShowsWhatWasWritten() {
            Map<String, Object> view =
                    only(new Zug().calls("meine_kontaktdaten_aendern", CHANGE, CHANGED));

            assertThat(view).containsEntry("kind", ViewCollector.KONTAKTDATEN);
            assertThat(view)
                    .containsEntry("vorname", "Anna")
                    .containsEntry("email", "anna.mueller@example.com")
                    .containsEntry("telefon", "+49 30 1234567");
            assertThat(anschrift(view))
                    .containsEntry("strasse", "Lindenallee 7")
                    .containsEntry("plz", "20095")
                    .containsEntry("ort", "Hamburg")
                    .containsEntry("land", "DE");
        }

        @Test
        @DisplayName("the card carries nothing that is none of its business")
        void nothingBeyondTheKontaktdaten() {
            Map<String, Object> view =
                    only(new Zug().calls("meine_kontaktdaten_aendern", CHANGE, CHANGED));

            assertThat(view).doesNotContainKeys("id", "nachname", "geburtsdatum", "tarifId",
                    "versicherungsbeginn", "fehlendeZaehne", "vorversicherung");
        }

        @Test
        @DisplayName("the read-back beats the Vertrag that was read before it")
        void theChangeWinsOverTheVertrag() {
            var turn = new Zug()
                    .calls("mein_vertrag_lesen", "{}", VERTRAG)
                    .calls("meine_kontaktdaten_aendern", CHANGE, CHANGED);

            assertThat(collector.collect(turn.observed()))
                    .extracting(view -> view.get("kind"))
                    .containsExactly(ViewCollector.KONTAKTDATEN);
        }

        @Test
        @DisplayName("the read-back survives the cap of two")
        void theChangeSurvivesTheCap() {
            var turn = new Zug()
                    .calls("meine_kontaktdaten_aendern", CHANGE, CHANGED)
                    .calls("tarife_vergleichen", "{}", COMPARISON)
                    .calls("tarifempfehlung", "{}", RECOMMENDATION);

            assertThat(collector.collect(turn.observed()))
                    .hasSize(ViewCollector.MAX_COUNT)
                    .extracting(view -> view.get("kind"))
                    .containsExactly(ViewCollector.KONTAKTDATEN, ViewCollector.TARIFVERGLEICH);
        }

        @Test
        @DisplayName("an Akte without an Anschrift shows the card without one")
        void withoutAnAnschrift() {
            Map<String, Object> view = only(new Zug().calls("meine_kontaktdaten_aendern", CHANGE,
                    "{\"vorname\":\"Anna\",\"email\":\"anna@example.com\",\"adresse\":null}"));

            assertThat(view).doesNotContainKey("adresse")
                    .containsEntry("email", "anna@example.com");
        }
    }

    @Nested
    @DisplayName("the Schadensfall card after submitting")
    class Fallkarte {

        @Test
        @DisplayName("after schadensfall_einreichen the Schadensfall card with the new Schadensfall sits below the answer")
        void theSchadensfallCardAfterSubmitting() {
            ObservedTools observed = withResult("schadensfall_einreichen", """
                    {"id":50071,"kundenId":10001,"behandlungsdatum":"2026-08-10","rechnungsbetrag":"482.10",
                     "erstattungsbetrag":null,"status":"eingereicht","ablehnungsgrund":null,"ablehnungshinweis":null,
                     "positionen":[],"eingereichtAm":"2026-08-18T10:00:00Z","bewertung":null}
                    """);

            List<Map<String, Object>> views = collector.collect(observed);

            assertThat(views).hasSize(1);
            assertThat(views.getFirst()).containsEntry("kind", "schadensfaelle");
            List<Map<String, Object>> faelle = cases(views.getFirst());
            assertThat(faelle).hasSize(1);
            assertThat(faelle.getFirst()).containsEntry("id", 50071L).containsEntry("status", "eingereicht")
                    .containsEntry("erstattungsvorschlag", null).containsEntry("empfehlung", null)
                    .doesNotContainKey("positionen");
        }

        @Test
        @DisplayName("the Bewertung yields a Vorschlag and an Empfehlung, but no Eskalationsgruende")
        void theBewertungWithoutInternals() {
            ObservedTools observed = withResult("meine_schadensfaelle_auflisten", """
                    {"schadensfaelle":[{"id":50014,"behandlungsdatum":"2026-05-02","rechnungsbetrag":"900.00",
                       "erstattungsbetrag":null,"status":"geprueft_eskalation","ablehnungsgrund":null,"ablehnungshinweis":null,
                       "bewertung":{"empfehlung":"eskalation","erstattungsvorschlag":"630.00",
                                    "eskalationsgruende":[{"code":"ARZT_FLAGGED","text":"..."}]}}]}
                    """);

            Map<String, Object> fall = cases(collector.collect(observed).getFirst()).getFirst();

            assertThat(fall).containsEntry("empfehlung", "eskalation").containsEntry("erstattungsvorschlag", "630.00");
            assertThat(fall.toString()).doesNotContain("ARZT_FLAGGED").doesNotContain("eskalationsgruende");
        }
    }

    @Nested
    @DisplayName("the trace")
    class Trace {

        @Test
        @DisplayName("carries the kinds and the data part as it goes out")
        void carriesTheDataPart() {
            var turn = new Zug()
                    .calls("mein_vertrag_lesen", "{}", VERTRAG)
                    .calls("tarife_vergleichen", "{}", COMPARISON);
            List<Map<String, Object>> views = collector.collect(turn.observed());

            TracePoint point = collector.trace("beratung", views).orElseThrow();

            assertThat(point.protocol()).isEqualTo(Protocol.INTERNAL);
            assertThat(point.sender()).isEqualTo("beratung");
            assertThat(point.operation()).isEqualTo("views");
            assertThat(point.data()).containsEntry("kinds", List.of("tarifvergleich"));
            assertThat(point.data().get("data").toString())
                    .contains("\"vertragstarif\"", "ATRA_DENT_B", "atra.dent.balance",
                            "\"leistungsbereiche\"");
        }

        @Test
        @DisplayName("without a view no trace point")
        void withoutAViewNoTracePoint() {
            assertThat(collector.trace("beratung", List.of())).isEmpty();
        }

        @Test
        @DisplayName("truncates visibly what is too long")
        void truncatesVisibly() {
            List<Map<String, Object>> riesig = List.of(Map.of(
                    "kind", ViewCollector.TARIFVERGLEICH,
                    "tarife", "x".repeat(TracePoint.MAX_LENGTH + 1)));

            TracePoint point = collector.trace("beratung", riesig).orElseThrow();

            assertThat(point.data().get("data").toString()).contains("gekürzt nach");
        }
    }

    @Nested
    @DisplayName("the limits")
    class Grenzen {

        @Test
        @DisplayName("without a tool call there is nothing to show -- that is the normal case")
        void emptyIsValid() {
            assertThat(collector.collect(new Zug().observed())).isEmpty();
        }

        @Test
        @DisplayName("an unreadable result does not cost a finished Auskunft")
        void anUnreadableResultDoesNotThrow() {
            var turn = new Zug().calls("tarife_vergleichen", "{}", "das ist kein json");

            assertThat(collector.collect(turn.observed())).isEmpty();
        }

        @Test
        @DisplayName("at most two views per turn")
        void atMostTwo() {
            var turn = new Zug()
                    .calls("tarife_vergleichen", "{}", COMPARISON)
                    .calls("beitrag_berechnen", arguments("ATRA_DENT_B"), "{\"monatsbeitrag\":\"15.90\"}")
                    .calls("beitrag_berechnen", arguments("ATRA_DENT_X"), "{\"monatsbeitrag\":\"24.90\"}")
                    .calls("tarifempfehlung", "{}", RECOMMENDATION)
                    .calls("meine_schadensfaelle_auflisten", "{}", CASES);

            assertThat(collector.collect(turn.observed()))
                    .hasSize(ViewCollector.MAX_COUNT)
                    .extracting(view -> view.get("kind"))
                    .containsExactly(ViewCollector.TARIFVERGLEICH, ViewCollector.BEITRAG_COMPARISON);
        }
    }


    private static final String COMPARISON = """
            {"stand":"2026-01-01",
             "tarife":[{"schluessel":"ATRA_DENT_B","anzeigename":"atra.dent.balance",
                        "positionierung":"Breiter Schutz","selbstbehalt":100,
                        "jahreshoechstgrenze":2000,"wartezeitMonate":8,
                        "wartezeitEntfaelltBeiVorversicherung":true,
                        "zahnstaffel":[{"bisJahr":1,"betrag":1000}],
                        "bedingungswerk":"bedingungen-balance"},
                       {"schluessel":"ATRA_DENT_X","anzeigename":"atra.dent.brillant",
                        "positionierung":"Voller Schutz","selbstbehalt":0,
                        "jahreshoechstgrenze":null,"wartezeitMonate":8,
                        "wartezeitEntfaelltBeiVorversicherung":true,
                        "zahnstaffel":[{"bisJahr":1,"betrag":1500}],
                        "bedingungswerk":"bedingungen-brillant"}],
             "leistungsbereiche":[{"schluessel":"IMP","name":"Implantate",
                        "beschreibung":"Implantate samt Aufbau",
                        "leistungen":{"ATRA_DENT_B":{"versichert":true,"quote":80,
                                        "limitProJahr":null,"maxFaelle":2},
                                      "ATRA_DENT_X":{"versichert":true,"quote":100,
                                        "limitProJahr":null,"maxFaelle":null}}}]}
            """;

    private static final String LIST = """
            [{"id":"ATRA_DENT_B","name":"atra.dent.balance","basisbeitragMonatlich":"15.90"},
             {"id":"ATRA_DENT_X","name":"atra.dent.brillant","basisbeitragMonatlich":"24.90"}]
            """;

    private static final String RECOMMENDATION = """
            {"anliegen":{"alter":42,"behandlungsschwerpunkte":[],"angerateneBehandlung":"KEINE"},
             "weg":"BEDARF","zusammenfassung":"Der Bedarf entscheidet.",
             "empfehlungen":[{"rang":1,"tarifschluessel":"ATRA_DENT_X",
                              "anzeigename":"atra.dent.brillant","kurzformel":"voller Schutz",
                              "reihenfolgeplatz":2,"needReasons":[],
                              "begruendung":"Weil der Leitfaden das so sagt.",
                              "hinweispflicht":"Woertlich aus dem Leitfaden."}],
             "ausgeschlossene":[],"hinweise":[],"grundsatz":{"vorrang":"intern"},
             "complianceGrenzen":[{"schluessel":"KEINE_ERSTATTUNGSZUSAGE","text":"intern"}],
             "vertraulichkeit":"intern"}
            """;

    private static final String VERTRAG = """
            {"id":10001,"vorname":"Anna","nachname":"Müller","geburtsdatum":"1990-04-12",
             "email":"anna.mueller@example.com","telefon":"+49 30 1234567",
             "adresse":{"strasse":"Musterstrasse 12","plz":"10115","ort":"Berlin","land":"DE"},
             "tarifId":"ATRA_DENT_B","versicherungsbeginn":"2024-03-01","vorversicherung":false,
             "fehlendeZaehne":0,"status":"aktiv","erstelltAm":"2024-02-19T10:24:00Z"}
            """;

    private static final String CASES = """
            [{"id":50001,"kundenId":10001,"behandlungsdatum":"2024-09-14",
              "positionen":[{"goz":"5010","leistungsbereich":"ZE","betrag":"1180.00",
                             "beschreibung":"Bruecke Regio 24-26"}],
              "rechnungsbetrag":"1862.10","erstattungsbetrag":"1000.00","status":"ausgezahlt",
              "ablehnungsgrund":null,"ablehnungshinweis":null,
              "eingereichtAm":"2024-09-20T09:12:00Z"},
             {"id":50002,"kundenId":10001,"behandlungsdatum":"2025-11-20",
              "positionen":[],"rechnungsbetrag":"340.00","erstattungsbetrag":null,
              "status":"abgelehnt","ablehnungsgrund":"WARTEZEIT",
              "ablehnungshinweis":"Die Wartezeit lief noch.",
              "eingereichtAm":"2025-11-25T09:12:00Z"}]
            """;

    private static final String CHANGE = """
            {"strasse":"Lindenallee 7","plz":"20095","ort":"Hamburg","land":"DE"}
            """;

    private static final String CHANGED = """
            {"id":10001,"vorname":"Anna","nachname":"Müller","geburtsdatum":"1990-04-12",
             "email":"anna.mueller@example.com","telefon":"+49 30 1234567",
             "adresse":{"strasse":"Lindenallee 7","plz":"20095","ort":"Hamburg","land":"DE"},
             "tarifId":"ATRA_DENT_B","versicherungsbeginn":"2024-03-01","vorversicherung":false,
             "fehlendeZaehne":0,"status":"aktiv","geaendertAm":"2026-08-25T11:04:00Z"}
            """;

    private static String arguments(String tarif) {
        return """
                {"geburtsdatum":"1990-04-12","tarifId":"%s","gewuenschterBeginn":"2026-01-01"}
                """.formatted(tarif);
    }


    private Map<String, Object> only(Zug zug) {
        List<Map<String, Object>> views = collector.collect(zug.observed());
        assertThat(views).hasSize(1);
        return views.getFirst();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> values(Map<String, Object> view, String field) {
        return (List<Map<String, Object>>) view.get(field);
    }

    private static Map<String, Object> first(Map<String, Object> view, String field) {
        return values(view, field).getFirst();
    }

    private static List<Map<String, Object>> cases(Map<String, Object> view) {
        return values(view, "faelle");
    }

    private static ObservedTools withResult(String tool, String result) {
        return new Zug().calls(tool, "{}", result).observed();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> vertragTarif(Map<String, Object> view) {
        return (Map<String, Object>) view.get(ViewCollector.VERTRAG_TARIF);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> anschrift(Map<String, Object> view) {
        return (Map<String, Object>) view.get("adresse");
    }

    private static final class Zug {

        private final ObservedTools observed =
                new ObservedTools(StatusChannel.discarded(), "beratung",
                        ToolSelection.MAPPING, BeratungMessages.ALLE);

        Zug calls(String name, String arguments, String result) {
            observed.wrap(List.of(tool(name, result))).getFirst().call(arguments);
            return this;
        }

        ObservedTools observed() {
            return observed;
        }

        private static ToolCallback tool(String name, String result) {
            return new ToolCallback() {
                @Override
                public ToolDefinition getToolDefinition() {
                    return ToolDefinition.builder().name(name).description("Test")
                            .inputSchema("{\"type\":\"object\",\"properties\":{}}").build();
                }

                @Override
                public String call(String input) {
                    return McpWrapper.um(result);
                }

                @Override
                public String call(String input, ToolContext context) {
                    return McpWrapper.um(result);
                }
            };
        }
    }
}
