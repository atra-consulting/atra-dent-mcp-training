package consulting.atra.agenten.schadensfall.check;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalGuardTest {

    private static final ObjectMapper IMAGES = JsonMapper.builder().build();
    private static final BigDecimal THRESHOLD = new BigDecimal("1500.00");
    private static final BigDecimal RECHENKERN = new BigDecimal("336.00");

    private final ApprovalGuard guard = new ApprovalGuard(THRESHOLD);


    @Test
    @DisplayName("all rules green -> Freigabe with the Betrag from the Rechenkern")
    void allGreenYieldsFreigabe() {
        BewertungResult result = guard.check(fall(), proposal("336.00"), findings().build());

        assertThat(result.gruende()).isEmpty();
        assertThat(result.empfehlung()).isEqualTo("freigabe");
        assertThat(result.erstattungsvorschlag()).isEqualByComparingTo(RECHENKERN);
        assertThat(result.begruendung()).isEqualTo("Beide Positionen sind versichert.");
        assertThat(result.arztauskunft()).isNotNull();
    }

    @Test
    @DisplayName("Vorschlag 999.00 against Rechenkern 336.00 -> BETRAG_DEVIATES, the result says 336.00")
    void theVorschlagDiffersTheResultTakesTheRechenkern() {
        BewertungResult result = guard.check(fall(), proposal("999.00"), findings().build());

        assertThat(codes(result)).containsExactly(Eskalationsgrund.BETRAG_DEVIATES);
        assertThat(result.empfehlung()).isEqualTo("eskalation");
        assertThat(result.erstattungsvorschlag()).isEqualByComparingTo(RECHENKERN);
        assertThat(result.gruende().getFirst().text()).contains("999.00").contains("336.00");
    }

    @Test
    @DisplayName("no Vorschlag -> MODELL_WITHOUT_RESULT and Positionen from the GOZ Befunde")
    void withoutAVorschlagPositionenComeFromTheBefunde() {
        BewertungResult result = guard.check(fall(), null, findings().build());

        assertThat(codes(result)).containsExactly(Eskalationsgrund.MODELL_WITHOUT_RESULT);
        assertThat(result.empfehlung()).isEqualTo("eskalation");
        assertThat(result.begruendung()).isEqualTo("Das Modell hat keine Bewertung abgegeben.");
        assertThat(result.positionen()).hasSize(2);
        assertThat(result.positionen().getFirst().goz()).isEqualTo("2197");
        assertThat(result.positionen().getFirst().zustand()).isEqualTo("ENTHALTEN");
        assertThat(result.positionen().getFirst().leistungsbereich()).isEqualTo("ZAHNERHALT");
        assertThat(result.erstattungsvorschlag()).isEqualByComparingTo(RECHENKERN);
    }

    @Test
    @DisplayName("without a Vorschlag the remaining rules still run - the reasons stay complete")
    void withoutAVorschlagTheRemainingRulesStillRun() {
        BewertungResult result = guard.check(fall(), null,
                findings().vertrag(VERTRAG.replace("\"aktiv\"", "\"inaktiv\"")).build());

        assertThat(codes(result)).containsExactly(
                Eskalationsgrund.VERTRAG_INACTIVE, Eskalationsgrund.MODELL_WITHOUT_RESULT);
    }

    @Test
    @DisplayName("several violations appear in the order of the contract")
    void theOrderFollowsTheContract() {
        BewertungResult result = new ApprovalGuard(new BigDecimal("300.00")).check(
                fall(),
                null,
                findings().arzt(null, true, true)
                        .vertrag(VERTRAG.replace("\"aktiv\"", "\"inaktiv\""))
                        .goz(finding("2197", "NICHT_BESTIMMBAR", null, null),
                                finding("2200", "ENTHALTEN", "ZAHNERHALT", null))
                        .build());

        assertThat(codes(result)).containsExactly(
                Eskalationsgrund.GOZ_UNCLEAR,
                Eskalationsgrund.ARZT_UNAVAILABLE,
                Eskalationsgrund.BETRAG_ABOVE_THRESHOLD,
                Eskalationsgrund.VERTRAG_INACTIVE,
                Eskalationsgrund.MODELL_WITHOUT_RESULT);
    }

    @Test
    @DisplayName("the reasons are always a subsequence of Eskalationsgrund.ALLE")
    void theReasonsAreASubsequenceOfTheContract() {
        BewertungResult result = guard.check(
                fall("[]", "1862.10", "\"Max Beispiel\"", "2026-05-03"),
                new Bewertungsvorschlag("eskalation", null, List.of(), "Bitte pruefen."),
                findings().vertrag(null).erstattung(null).arzt(null, false, false)
                        .goz(finding("2197", "UNBEKANNT", null, null)).build());

        assertThat(codes(result)).hasSizeGreaterThan(3);
        assertThat(Eskalationsgrund.ALLE)
                .containsSubsequence(codes(result).toArray(String[]::new));
    }


    @Nested
    @DisplayName("MODELL_ESCALATION")
    class ModellEskalation {

        @Test
        @DisplayName("green: the model recommends Freigabe, all rules hold -> Freigabe without codes")
        void green() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().build());

            assertThat(result.gruende()).isEmpty();
            assertThat(result.empfehlung()).isEqualTo("freigabe");
        }

        @Test
        @DisplayName("red: the model recommends Eskalation, all rules hold -> ESKALATION with a code")
        void theModelReturns() {
            var proposal = new Bewertungsvorschlag("eskalation", RECHENKERN, List.of(),
                    "Position 2200 ist laut Bedingungswerk ausgeschlossen.");

            BewertungResult result = guard.check(fall(), proposal, findings().build());

            assertThat(codes(result)).containsExactly(Eskalationsgrund.MODELL_ESCALATION);
            assertThat(result.empfehlung()).isEqualTo("eskalation");
            assertThat(text(result, Eskalationsgrund.MODELL_ESCALATION))
                    .contains("ausgeschlossen");
            assertThat(result.begruendung())
                    .isEqualTo("Position 2200 ist laut Bedingungswerk ausgeschlossen.");
        }

        @Test
        @DisplayName("red: a long Begruendung appears truncated in the reason")
        void aLongBegruendungIsTruncated() {
            String longText = "x".repeat(500);
            var proposal = new Bewertungsvorschlag("eskalation", RECHENKERN, List.of(), longText);

            BewertungResult result = guard.check(fall(), proposal, findings().build());

            assertThat(text(result, Eskalationsgrund.MODELL_ESCALATION)).hasSizeLessThan(300);
            assertThat(result.begruendung()).isEqualTo(longText);
        }
    }

    @Nested
    @DisplayName("GOZ_UNCLEAR")
    class GozUnklar {

        @Test
        @DisplayName("green: every Position has a Befund, none is undetermined")
        void green() {
            assertThat(codes(guard.check(fall(), proposal("336.00"), findings().build())))
                    .doesNotContain(Eskalationsgrund.GOZ_UNCLEAR);
        }

        @Test
        @DisplayName("green: NICHT_BESTIMMBAR, but the model assigns the Position to a Hauptbehandlung "
                + "that is ENTHALTEN")
        void nichtBestimmbarWithAMainBehandlung() {
            BewertungResult result = guard.check(fall(),
                    proposal("336.00", "ZAHNERHALT", "NICHT_BESTIMMBAR"),
                    findings().goz(finding("2197", "ENTHALTEN", "ZAHNERHALT", null),
                            finding("2200", "NICHT_BESTIMMBAR", null, null)).build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.GOZ_UNCLEAR);
        }

        @Test
        @DisplayName("red: NICHT_BESTIMMBAR and the named Leistungsbereich has no ENTHALTEN Position on "
                + "this Rechnung")
        void nichtBestimmbarWithAForeignLeistungsbereich() {
            BewertungResult result = guard.check(fall(),
                    proposal("336.00", "ZAHNERSATZ", "NICHT_BESTIMMBAR"),
                    findings().goz(finding("2197", "ENTHALTEN", "ZAHNERHALT", null),
                            finding("2200", "NICHT_BESTIMMBAR", null, null)).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR))
                    .contains("2200").contains("ZAHNERSATZ");
        }

        @Test
        @DisplayName("red: NICHT_BESTIMMBAR and the model names no Leistungsbereich at all")
        void nichtBestimmbarWithoutALeistungsbereich() {
            BewertungResult result = guard.check(fall(),
                    proposal("336.00", null, "NICHT_BESTIMMBAR"),
                    findings().goz(finding("2197", "ENTHALTEN", "ZAHNERHALT", null),
                            finding("2200", "NICHT_BESTIMMBAR", null, null)).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR))
                    .contains("2200").contains("NICHT_BESTIMMBAR");
        }

        @Test
        @DisplayName("red: NICHT_BESTIMMBAR is the only Position - there is no Hauptbehandlung it could "
                + "belong to")
        void nichtBestimmbarWithoutAMainBehandlung() {
            String one = """
                    [{"goz":"0010","leistungsbereich":null,"betrag":"20.11","beschreibung":"Eingehende Untersuchung"}]
                    """;
            var proposal = new Bewertungsvorschlag("freigabe", new BigDecimal("336.00"), List.of(
                    new Bewertungsvorschlag.Position("0010", "ZAHNERHALT", "NICHT_BESTIMMBAR",
                            "gehoert zur Fuellung")),
                    "Nur eine Untersuchung.");

            BewertungResult result = guard.check(
                    fall(one, "20.11", "\"Anna Mueller\"", "2026-05-03"), proposal,
                    findings().goz(finding("0010", "NICHT_BESTIMMBAR", null, null)).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR)).contains("0010");
        }

        @Test
        @DisplayName("red: UNBEKANNT stays a defect, even with a named Leistungsbereich")
        void unbekanntDespiteALeistungsbereich() {
            BewertungResult result = guard.check(fall(),
                    proposal("336.00", "ZAHNERHALT", "UNBEKANNT"),
                    findings().goz(finding("2197", "ENTHALTEN", "ZAHNERHALT", null),
                            finding("2200", "UNBEKANNT", null, null)).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR))
                    .contains("2200").contains("UNBEKANNT");
        }

        @Test
        @DisplayName("red: a Position of the case has no Befund at all - even with a named "
                + "Leistungsbereich")
        void aPositionWithoutABefund() {
            BewertungResult result = guard.check(fall(),
                    proposal("336.00", "ZAHNERHALT", "NICHT_BESTIMMBAR"),
                    findings().goz(finding("2197", "ENTHALTEN", "ZAHNERHALT", null)).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR))
                    .contains("zu GOZ-Nummer 2200 liegt kein Befund vor");
        }

        @Test
        @DisplayName("red: the ENTHALTEN Befund belongs to a Nummer that is not on the Rechnung at all")
        void anchorNotOnTheRechnung() {
            var proposal = new Bewertungsvorschlag("freigabe", new BigDecimal("336.00"), List.of(
                    new Bewertungsvorschlag.Position("2197", "ZAHNERHALT", "NICHT_BESTIMMBAR",
                            "folgt der Fuellung"),
                    new Bewertungsvorschlag.Position("2200", "ZAHNERHALT", "NICHT_BESTIMMBAR",
                            "folgt der Fuellung")),
                    "Beide folgen der Hauptbehandlung.");

            BewertungResult result = guard.check(fall(), proposal,
                    findings().goz(finding("2197", "NICHT_BESTIMMBAR", null, null),
                            finding("2200", "NICHT_BESTIMMBAR", null, null),
                            finding("2080", "ENTHALTEN", "ZAHNERHALT", null)).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR))
                    .contains("2197").contains("2200");
        }

        @Test
        @DisplayName("red: a Position cannot be its own anchor")
        void anchorIsNotItsOwnNummer() {
            BewertungResult result = guard.check(fall(),
                    proposal("336.00", "ZAHNERHALT", "NICHT_BESTIMMBAR"),
                    findings().goz(finding("2197", "ENTHALTEN", "ZAHNERSATZ", null),
                            finding("2200", "NICHT_BESTIMMBAR", null, null),
                            finding("2200", "ENTHALTEN", "ZAHNERHALT", null)).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR)).contains("2200");
        }

        @Test
        @DisplayName("red: the Rechnung puts a covered and an uncovered Hauptbehandlung side by side - the "
                + "assignment cannot be decided")
        void anAmbiguousRechnung() {
            String mixed = """
                    [{"goz":"2197","betrag":"200.00","beschreibung":"Adhaesive Befestigung der Krone"},
                     {"goz":"2200","betrag":"195.00","beschreibung":"Zuschlag"},
                     {"goz":"9010","betrag":"892.40","beschreibung":"Implantatinsertion"}]
                    """;
            var proposal = new Bewertungsvorschlag("freigabe", new BigDecimal("336.00"), List.of(
                    new Bewertungsvorschlag.Position("2197", "ZAHNERSATZ", "ENTHALTEN", "Krone"),
                    new Bewertungsvorschlag.Position("2200", "ZAHNERSATZ", "NICHT_BESTIMMBAR",
                            "folgt der Krone"),
                    new Bewertungsvorschlag.Position("9010", "IMPLANTATE", "NICHT_ENTHALTEN",
                            "Implantat")),
                    "Der Zuschlag folgt der Krone.");

            BewertungResult result = guard.check(
                    fall(mixed, "1287.40", "\"Anna Mueller\"", "2026-05-03"), proposal,
                    findings().goz(finding("2197", "ENTHALTEN", "ZAHNERSATZ", null),
                            finding("2200", "NICHT_BESTIMMBAR", null, null),
                            finding("9010", "NICHT_ENTHALTEN", "IMPLANTATE", null)).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR))
                    .contains("2200").contains("9010");
        }

        @Test
        @DisplayName("green: a NICHT_ENTHALTEN Nummer that is not on the Rechnung at all does not make it "
                + "ambiguous")
        void nichtEnthaltenOutsideTheRechnung() {
            BewertungResult result = guard.check(fall(),
                    proposal("336.00", "ZAHNERHALT", "NICHT_BESTIMMBAR"),
                    findings().goz(finding("2197", "ENTHALTEN", "ZAHNERHALT", null),
                            finding("2200", "NICHT_BESTIMMBAR", null, null),
                            finding("9010", "NICHT_ENTHALTEN", "IMPLANTATE", null)).build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.GOZ_UNCLEAR);
        }

        @Test
        @DisplayName("green: the case of the Leistungsbereich decides nothing")
        void leistungsbereichCaseDoesNotMatter() {
            BewertungResult result = guard.check(fall(),
                    proposal("336.00", " zahnerhalt ", "NICHT_BESTIMMBAR"),
                    findings().goz(finding("2197", "ENTHALTEN", "ZAHNERHALT", null),
                            finding("2200", "NICHT_BESTIMMBAR", null, null)).build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.GOZ_UNCLEAR);
        }
    }

    @Nested
    @DisplayName("GOZ_UNCLEAR: the anchor on the Zahn for Positionen without a Gebuehrennummer")
    class ZahnAnker {

        @Test
        @DisplayName("green (counter-check): the Labor follows the Krone on the same Zahn")
        void anchorOnTheSameZahn() {
            BewertungResult result = guard.check(caseWithLabor("46"),
                    proposalWithLabor("ZE"), findingsWithLabor().build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.GOZ_UNCLEAR);
        }

        @Test
        @DisplayName("red: the named Leistungsbereich is on the Rechnung, but on a different Zahn")
        void aLeistungsbereichOnAForeignZahn() {
            BewertungResult result = guard.check(caseWithLabor("46"),
                    proposalWithLabor("ZAHNERHALT"),
                    findingsWithLabor().erstattungArguments(
                            argumentsWithLabor("ZAHNERHALT")).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR)
                    .doesNotContain(Eskalationsgrund.BETRAG_DEVIATES);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR))
                    .contains("Laborkosten").contains("ZAHNERHALT").contains("46");
        }

        @Test
        @DisplayName("red: the model maps the Position to no Behandlung at all")
        void withoutAMapping() {
            BewertungResult result = guard.check(caseWithLabor("46"),
                    proposalWithLabor(null), findingsWithLabor().build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR)).contains("Laborkosten");
        }

        @Test
        @DisplayName("red: no Vorschlag at all is no mapping either")
        void withoutAVorschlag() {
            BewertungResult result = guard.check(caseWithLabor("46"), null,
                    findingsWithLabor().build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR)).contains("Laborkosten");
        }

        @Test
        @DisplayName("red: on the same Zahn there is a covered and an uncovered Behandlung - a human "
                + "decides that")
        void disputedOnTheSameZahn() {
            JsonNode fall = fall("""
                    [{"goz":"2197","zahn":"46","betrag":"400.00","beschreibung":"Vollkeramische Krone"},
                     {"goz":"9010","zahn":"46","betrag":"100.00","beschreibung":"Implantatinsertion"},
                     {"goz":null,"zahn":"46","betrag":"486.30","beschreibung":"Laborkosten"}]
                    """, "986.30", "\"Anna Mueller\"", "2026-05-03");

            BewertungResult result = guard.check(fall, proposalWithLabor("ZE"),
                    findings().goz(finding("2197", "ENTHALTEN", "ZE", null),
                                    finding("9010", "NICHT_ENTHALTEN", "IMP", null))
                            .erstattungArguments(null).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR))
                    .contains("Laborkosten").contains("9010");
        }

        @Test
        @DisplayName("green: an uncovered Behandlung on a DIFFERENT Zahn does not make the assignment "
                + "disputed")
        void uncoveredOnesOnAnotherZahn() {
            JsonNode fall = fall("""
                    [{"goz":"2197","zahn":"46","betrag":"400.00","beschreibung":"Vollkeramische Krone"},
                     {"goz":"9010","zahn":"16","betrag":"100.00","beschreibung":"Implantatinsertion"},
                     {"goz":null,"zahn":"46","betrag":"486.30","beschreibung":"Laborkosten"}]
                    """, "986.30", "\"Anna Mueller\"", "2026-05-03");

            BewertungResult result = guard.check(fall, proposalWithLabor("ZE"),
                    findings().goz(finding("2197", "ENTHALTEN", "ZE", null),
                                    finding("9010", "NICHT_ENTHALTEN", "IMP", null))
                            .erstattungArguments(null).build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.GOZ_UNCLEAR);
        }

        @Test
        @DisplayName("red: on this Zahn there is no covered Behandlung at all")
        void withoutAnAnchorOnTheZahn() {
            BewertungResult result = guard.check(caseWithLabor("38"),
                    proposalWithLabor("ZE"), findingsWithLabor().build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR))
                    .contains("Laborkosten").contains("38");
        }

        @Test
        @DisplayName("green: without a Zahn the whole Rechnung is the anchor space")
        void withoutAZahnTheWholeRechnungApplies() {
            BewertungResult result = guard.check(caseWithLabor(null),
                    proposalWithLabor("ZAHNERHALT"),
                    findingsWithLabor().erstattungArguments(
                            argumentsWithLabor("ZAHNERHALT")).build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.GOZ_UNCLEAR)
                    .doesNotContain(Eskalationsgrund.BETRAG_DEVIATES);
        }

        @Test
        @DisplayName("green: case decides nothing here either")
        void caseDoesNotMatter() {
            BewertungResult result = guard.check(caseWithLabor("46"),
                    proposalWithLabor(" ze "), findingsWithLabor().build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.GOZ_UNCLEAR);
        }

        @Test
        @DisplayName("red: a deviating Zahn spelling must not make the dispute disappear")
        void theZahnSpellingDoesNotLoosen() {
            JsonNode fall = fall("""
                    [{"goz":"2197","zahn":"46","betrag":"400.00","beschreibung":"Vollkeramische Krone"},
                     {"goz":"9010","zahn":"046","betrag":"100.00","beschreibung":"Implantatinsertion"},
                     {"goz":null,"zahn":"46","betrag":"486.30","beschreibung":"Laborkosten"}]
                    """, "986.30", "\"Anna Mueller\"", "2026-05-03");

            BewertungResult result = guard.check(fall, proposalWithLabor("ZE"),
                    findings().erstattungArguments(null).goz(
                            finding("2197", "ENTHALTEN", "ZE", null),
                            finding("9010", "NICHT_ENTHALTEN", "IMP", null)).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR))
                    .contains("Laborkosten").contains("9010");
        }

        @Test
        @DisplayName("green: a space and a leading zero are the same Zahn")
        void theZahnSpellingDoesNotTighten() {
            JsonNode fall = fall("""
                    [{"goz":"2197","zahn":" 046 ","betrag":"400.00","beschreibung":"Vollkeramische Krone"},
                     {"goz":"2200","zahn":"16","betrag":"100.00","beschreibung":"Fuellung"},
                     {"goz":null,"zahn":"46","betrag":"486.30","beschreibung":"Laborkosten"}]
                    """, "986.30", "\"Anna Mueller\"", "2026-05-03");

            assertThat(codes(guard.check(fall, proposalWithLabor("ZE"),
                    findingsWithLabor().erstattungArguments(null).build())))
                    .doesNotContain(Eskalationsgrund.GOZ_UNCLEAR);
        }

        @Test
        @DisplayName("red: an unreadable Zahn does not widen the anchor space to the whole Rechnung")
        void anUnreadableZahnDoesNotWiden() {
            JsonNode fall = fall("""
                    [{"goz":"2197","zahn":"46","betrag":"400.00","beschreibung":"Vollkeramische Krone"},
                     {"goz":"2200","zahn":"16","betrag":"100.00","beschreibung":"Fuellung"},
                     {"goz":null,"zahn":"unleserlich","betrag":"486.30","beschreibung":"Laborkosten"}]
                    """, "986.30", "\"Anna Mueller\"", "2026-05-03");

            BewertungResult result = guard.check(fall, proposalWithLabor("ZE"),
                    findingsWithLabor().erstattungArguments(null).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR)).contains("Laborkosten");
        }

        @Test
        @DisplayName("red: an ENTHALTEN Befund for a Nummer that is not on the Rechnung at all anchors "
                + "nothing")
        void aCoCheckedPositionDoesNotAnchor() {
            BewertungResult result = guard.check(caseWithLabor("46"),
                    proposalWithLabor("IMP"),
                    findingsWithLabor().erstattungArguments(null).goz(
                            finding("2197", "ENTHALTEN", "ZE", null),
                            finding("2200", "ENTHALTEN", "ZAHNERHALT", null),
                            finding("9010", "ENTHALTEN", "IMP", null)).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR))
                    .contains("Laborkosten").contains("IMP");
        }

        @Test
        @DisplayName("red: an invented zustand does not anchor - the writer would throw the Position away")
        void anInventedZustandDoesNotAnchor() {
            var proposal = new Bewertungsvorschlag("freigabe", RECHENKERN, List.of(
                    new Bewertungsvorschlag.Position(0, "2197", "ZE", "ENTHALTEN", "Krone"),
                    new Bewertungsvorschlag.Position(1, "2200", "ZAHNERHALT", "ENTHALTEN",
                            "Fuellung"),
                    new Bewertungsvorschlag.Position(2, null, "ZE", "material", "Laborkosten")),
                    "Das Labor folgt der Krone.");

            BewertungResult result = guard.check(caseWithLabor("46"), proposal,
                    findingsWithLabor().erstattungArguments(null).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR)).contains("Laborkosten");
        }

        @Test
        @DisplayName("green: a lowercase zustand anchors - as far as the writer does")
        void aLowercaseZustandAnchors() {
            var proposal = new Bewertungsvorschlag("freigabe", RECHENKERN, List.of(
                    new Bewertungsvorschlag.Position(0, "2197", "ZE", "ENTHALTEN", "Krone"),
                    new Bewertungsvorschlag.Position(1, "2200", "ZAHNERHALT", "ENTHALTEN",
                            "Fuellung"),
                    new Bewertungsvorschlag.Position(2, null, "ZE", " nicht_bestimmbar ",
                            "Laborkosten")),
                    "Das Labor folgt der Krone.");

            assertThat(codes(guard.check(caseWithLabor("46"), proposal,
                    findingsWithLabor().erstattungArguments(null).build())))
                    .doesNotContain(Eskalationsgrund.GOZ_UNCLEAR);
        }

        @Test
        @DisplayName("red: two Positionen without a Nummer do not anchor each other")
        void positionenWithoutANummerDoNotAnchorEachOther() {
            JsonNode fall = fall("""
                    [{"goz":"2197","zahn":"46","betrag":"400.00","beschreibung":"Vollkeramische Krone"},
                     {"goz":null,"zahn":"46","betrag":"300.00","beschreibung":"Materialkosten"},
                     {"goz":null,"zahn":"46","betrag":"486.30","beschreibung":"Laborkosten"}]
                    """, "1186.30", "\"Anna Mueller\"", "2026-05-03");
            var proposal = new Bewertungsvorschlag("freigabe", RECHENKERN, List.of(
                    new Bewertungsvorschlag.Position(0, "2197", "ZE", "ENTHALTEN", "Krone"),
                    new Bewertungsvorschlag.Position(1, null, "IMP", "NICHT_BESTIMMBAR", "Material"),
                    new Bewertungsvorschlag.Position(2, null, "IMP", "NICHT_BESTIMMBAR", "Labor")),
                    "Beides folgt dem Implantat.");

            BewertungResult result = guard.check(fall, proposal,
                    findings().erstattungArguments(null).goz(
                            finding("2197", "ENTHALTEN", "ZE", null),
                            """
                            {"nummer":null,"bezeichnung":"Leistung","abschnitt":"K",
                             "leistungsbereich":"IMP","status":"ENTHALTEN","quote":85,
                             "grenzen":{},"begruendung":"Begruendung"}
                            """).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR))
                    .contains("Materialkosten").contains("Laborkosten").contains("IMP");
        }

        @Test
        @DisplayName("red: ZE to the guard, IMP to the Rechenkern - for a Position without a Nummer")
        void theCalculatedLeistungsbereichDiffers() {
            BewertungResult result = guard.check(caseWithLabor("46"),
                    proposalWithLabor("ZE"),
                    findingsWithLabor().erstattungArguments(argumentsWithLabor("IMP")).build());

            assertThat(codes(result)).contains(Eskalationsgrund.BETRAG_DEVIATES);
            assertThat(text(result, Eskalationsgrund.BETRAG_DEVIATES))
                    .contains("Laborkosten").contains("IMP").contains("ZE");
        }

        @Test
        @DisplayName("green (counter-check): the calculation used the Leistungsbereich the anchor carries")
        void theCalculatedLeistungsbereichMatches() {
            assertThat(codes(guard.check(caseWithLabor("46"), proposalWithLabor("ZE"),
                    findingsWithLabor().build())))
                    .doesNotContain(Eskalationsgrund.BETRAG_DEVIATES);
        }

        @Test
        @DisplayName("red: a NICHT_BESTIMMBAR Position anchors on the Zahn too")
        void nichtBestimmbarAnchorsOnTheZahn() {
            BewertungResult result = guard.check(caseWithAbformung("46"),
                    proposalWithAbformung("ZE"), findingsWithAbformung().build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR))
                    .contains("0060").contains("ZE").contains("46");
        }

        @Test
        @DisplayName("green: the same Abformung on the same Zahn as the Krone")
        void nichtBestimmbarOnTheSameZahn() {
            assertThat(codes(guard.check(caseWithAbformung("16"),
                    proposalWithAbformung("ZE"), findingsWithAbformung().build())))
                    .doesNotContain(Eskalationsgrund.GOZ_UNCLEAR);
        }

        @Test
        @DisplayName("red: a NICHT_BESTIMMBAR Nummer that is not on the Rechnung at all")
        void nichtBestimmbarNotOnTheRechnung() {
            BewertungResult result = guard.check(caseWithAbformung("16"),
                    proposalWithAbformung("ZE"),
                    findingsWithAbformung().goz(finding("2197", "ENTHALTEN", "ZE", null),
                            finding("0060", "NICHT_BESTIMMBAR", null, null),
                            finding("0090", "NICHT_BESTIMMBAR", null, null)).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR))
                    .contains("0090").contains("steht nicht auf dieser Rechnung");
        }

        private JsonNode caseWithAbformung(String zahnDerAbformung) {
            return fall("""
                    [{"goz":"2197","zahn":"16","betrag":"400.00","beschreibung":"Vollkeramische Krone"},
                     {"goz":"0060","zahn":"%s","betrag":"100.00","beschreibung":"Abformung"}]
                    """.formatted(zahnDerAbformung),
                    "500.00", "\"Anna Mueller\"", "2026-05-03");
        }

        private Bewertungsvorschlag proposalWithAbformung(String bereichDerAbformung) {
            return new Bewertungsvorschlag("freigabe", RECHENKERN, List.of(
                    new Bewertungsvorschlag.Position(0, "2197", "ZE", "ENTHALTEN", "Krone"),
                    new Bewertungsvorschlag.Position(1, "0060", bereichDerAbformung,
                            "NICHT_BESTIMMBAR", "Abformung zur Krone")),
                    "Die Abformung folgt der Krone.");
        }

        private Befundbau findingsWithAbformung() {
            return findings()
                    .goz(finding("2197", "ENTHALTEN", "ZE", null),
                            finding("0060", "NICHT_BESTIMMBAR", null, null))
                    .erstattungArguments(null);
        }

        private JsonNode caseWithLabor(String zahnDesLabors) {
            return fall("""
                    [{"goz":"2197","zahn":"46","betrag":"400.00","beschreibung":"Vollkeramische Krone"},
                     {"goz":"2200","zahn":"16","betrag":"100.00","beschreibung":"Fuellung"},
                     {"goz":null,"zahn":%s,"betrag":"486.30","beschreibung":"Laborkosten"}]
                    """.formatted(zahnDesLabors == null ? "null" : "\"" + zahnDesLabors + "\""),
                    "986.30", "\"Anna Mueller\"", "2026-05-03");
        }

        private Bewertungsvorschlag proposalWithLabor(String bereichDesLabors) {
            return new Bewertungsvorschlag("freigabe", RECHENKERN, List.of(
                    new Bewertungsvorschlag.Position(0, "2197", "ZE", "ENTHALTEN", "Krone"),
                    new Bewertungsvorschlag.Position(1, "2200", "ZAHNERHALT", "ENTHALTEN",
                            "Fuellung"),
                    new Bewertungsvorschlag.Position(2, null, bereichDesLabors,
                            "NICHT_BESTIMMBAR", "Laborkosten zur Krone an Zahn 46")),
                    "Das Labor folgt der Krone.");
        }

        private Befundbau findingsWithLabor() {
            return findings()
                    .goz(finding("2197", "ENTHALTEN", "ZE", null),
                            finding("2200", "ENTHALTEN", "ZAHNERHALT", null))
                    .erstattungArguments(argumentsWithLabor("ZE"));
        }

        private String argumentsWithLabor(String bereichDesLabors) {
            return """
                    {"tarifId":"ATRA_DENT_B","versicherungsbeginn":"2025-01-01",
                     "behandlungsdatum":"2026-05-03",
                     "positionen":[
                       {"goz":"2197","leistungsbereich":"ZE","betrag":"400.00","beschreibung":"Vollkeramische Krone"},
                       {"goz":"2200","leistungsbereich":"ZAHNERHALT","betrag":"100.00","beschreibung":"Fuellung"},
                       {"leistungsbereich":"%s","betrag":"486.30","beschreibung":"Laborkosten"}],
                     "verbrauch":{"gesamt":"102.00"}}
                    """.formatted(bereichDesLabors);
        }
    }

    @Nested
    @DisplayName("BETRAG_DEVIATES")
    class BetragAbweichend {

        @Test
        @DisplayName("green: Vorschlag and Rechenkern are the same Betrag, even at a different scale")
        void green() {
            assertThat(codes(guard.check(fall(), proposal("336.0"), findings().build())))
                    .doesNotContain(Eskalationsgrund.BETRAG_DEVIATES);
        }

        @Test
        @DisplayName("green: on a Eskalation proposal the rule does not check")
        void onlyOnFreigabe() {
            BewertungResult result = guard.check(fall(),
                    new Bewertungsvorschlag("eskalation", new BigDecimal("999.00"),
                            List.of(), "Bitte pruefen."),
                    findings().build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.BETRAG_DEVIATES);
        }

        @Test
        @DisplayName("red: erstattung_berechnen was not called at all")
        void withoutTheRechenkern() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().erstattung(null).build());

            assertThat(codes(result)).contains(Eskalationsgrund.BETRAG_DEVIATES);
            assertThat(result.erstattungsvorschlag()).isNull();
        }

        @Test
        @DisplayName("red: the model names no Betrag at all")
        void withoutABetragInTheVorschlag() {
            BewertungResult result = guard.check(fall(),
                    new Bewertungsvorschlag("freigabe", null, List.of(), "Passt."),
                    findings().build());

            assertThat(codes(result)).contains(Eskalationsgrund.BETRAG_DEVIATES);
        }
    }

    @Nested
    @DisplayName("BETRAG_DEVIATES from the arguments of the Rechenkern")
    class ErstattungsargumenteAbweichend {

        @Test
        @DisplayName("green: the calculation used Tarif, Beginn, Datum and Positionen of the Schadensfall")
        void green() {
            assertThat(codes(guard.check(fall(), proposal("336.00"), findings().build())))
                    .doesNotContain(Eskalationsgrund.BETRAG_DEVIATES);
        }

        @Test
        @DisplayName("green: without a call there are no arguments to check")
        void withoutACallNoArguments() {
            BewertungResult result = guard.check(fall(),
                    new Bewertungsvorschlag("eskalation", null, List.of(), "Bitte pruefen."),
                    findings().erstattung(null).erstattungArguments(null).build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.BETRAG_DEVIATES);
        }

        @Test
        @DisplayName("red: the calculation used a foreign Tarif")
        void aForeignTarif() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().erstattungArguments(
                            ERSTATTUNG_ARGUMENTS.replace("\"tarifId\":\"ATRA_DENT_B\"",
                                    "\"tarifId\":\"ATRA_DENT_PREMIUM\"")).build());

            assertThat(codes(result)).contains(Eskalationsgrund.BETRAG_DEVIATES);
            assertThat(text(result, Eskalationsgrund.BETRAG_DEVIATES))
                    .contains("ATRA_DENT_PREMIUM").contains("ATRA_DENT_B");
        }

        @Test
        @DisplayName("red: the calculation used a Versicherungsbeginn moved forward")
        void aForeignBeginn() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().erstattungArguments(
                            ERSTATTUNG_ARGUMENTS.replace("\"versicherungsbeginn\":\"2025-01-01\"",
                                    "\"versicherungsbeginn\":\"2021-01-01\"")).build());

            assertThat(codes(result)).contains(Eskalationsgrund.BETRAG_DEVIATES);
            assertThat(text(result, Eskalationsgrund.BETRAG_DEVIATES))
                    .contains("2021-01-01").contains("2025-01-01");
        }

        @Test
        @DisplayName("red: the calculation used a different treatment day")
        void aForeignDatum() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().erstattungArguments(
                            ERSTATTUNG_ARGUMENTS.replace("\"behandlungsdatum\":\"2026-05-03\"",
                                    "\"behandlungsdatum\":\"2027-05-03\"")).build());

            assertThat(codes(result)).contains(Eskalationsgrund.BETRAG_DEVIATES);
            assertThat(text(result, Eskalationsgrund.BETRAG_DEVIATES))
                    .contains("2027-05-03").contains("2026-05-03");
        }

        @Test
        @DisplayName("red: a Position was calculated higher than it appears on the Rechnung")
        void anInflatedBetrag() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().erstattungArguments(
                            ERSTATTUNG_ARGUMENTS.replace("\"betrag\":\"200.00\"",
                                    "\"betrag\":\"900.00\"")).build());

            assertThat(codes(result)).contains(Eskalationsgrund.BETRAG_DEVIATES);
            assertThat(text(result, Eskalationsgrund.BETRAG_DEVIATES))
                    .contains("2197").contains("900.00");
        }

        @Test
        @DisplayName("red: the same Position counted twice - a Position counts only once")
        void aPositionCountedTwice() {
            String duplicate = """
                    {"tarifId":"ATRA_DENT_B","versicherungsbeginn":"2025-01-01",
                     "behandlungsdatum":"2026-05-03",
                     "positionen":[
                       {"goz":"2197","betrag":"200.00"},
                       {"goz":"2197","betrag":"200.00"},
                       {"goz":"2200","betrag":"195.00"}]}
                    """;

            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().erstattungArguments(duplicate).build());

            assertThat(codes(result)).contains(Eskalationsgrund.BETRAG_DEVIATES);
        }

        @Test
        @DisplayName("green: fewer Positionen than the Schadensfall has are allowed")
        void aSubsetIsAllowed() {
            String onlyOne = """
                    {"tarifId":"ATRA_DENT_B","versicherungsbeginn":"2025-01-01",
                     "behandlungsdatum":"2026-05-03",
                     "positionen":[{"goz":"2200","betrag":"195.0"}]}
                    """;

            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().erstattungArguments(onlyOne).build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.BETRAG_DEVIATES);
        }

        @Test
        @DisplayName("red: the Erstattungsbetrag is higher than the Rechnungsbetrag")
        void aboveTheRechnungsbetrag() {
            BewertungResult result = guard.check(fall(), proposal("500.00"),
                    findings().erstattung("500.00").build());

            assertThat(codes(result)).contains(Eskalationsgrund.BETRAG_DEVIATES);
            assertThat(text(result, Eskalationsgrund.BETRAG_DEVIATES))
                    .contains("500.00").contains("395.00");
        }

        @Test
        @DisplayName("green: exactly the Rechnungsbetrag is not yet an overrun")
        void exactlyTheRechnungsbetrag() {
            BewertungResult result = guard.check(fall(), proposal("395.00"),
                    findings().erstattung("395.00").build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.BETRAG_DEVIATES);
        }

        @Test
        @DisplayName("green: without the field unfallbedingt there is nothing to report")
        void withoutTheUnfallFlagGreen() {
            assertThat(codes(guard.check(fall(), proposal("336.00"), findings().build())))
                    .doesNotContain(Eskalationsgrund.BETRAG_DEVIATES);
        }

        @Test
        @DisplayName("green: unfallbedingt=false is the same calculation as without the field")
        void theUnfallFlagFalseIsGreen() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().erstattungArguments(
                            ERSTATTUNG_ARGUMENTS.replace("\"behandlungsdatum\"",
                                    "\"unfallbedingt\":false,\"behandlungsdatum\"")).build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.BETRAG_DEVIATES);
        }

        @Test
        @DisplayName("red: unfallbedingt=true lifts the Zahnstaffel - and is in no Akte")
        void theUnfallFlagIsInvented() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().erstattungArguments(
                            ERSTATTUNG_ARGUMENTS.replace("\"behandlungsdatum\"",
                                    "\"unfallbedingt\":true,\"behandlungsdatum\"")).build());

            assertThat(codes(result)).contains(Eskalationsgrund.BETRAG_DEVIATES);
            assertThat(text(result, Eskalationsgrund.BETRAG_DEVIATES))
                    .contains("unfallbedingt")
                    .contains("Zahnstaffel");
        }

        @Test
        @DisplayName("red: an unfallbedingt as text or number is set too")
        void theUnfallFlagIsNotABoolean() {
            for (String geschrieben : new String[] {"\"true\"", "1", "\"ja\""}) {
                BewertungResult result = guard.check(fall(), proposal("336.00"),
                        findings().erstattungArguments(
                                ERSTATTUNG_ARGUMENTS.replace("\"behandlungsdatum\"",
                                        "\"unfallbedingt\":" + geschrieben
                                                + ",\"behandlungsdatum\"")).build());

                assertThat(codes(result))
                        .as("unfallbedingt=%s", geschrieben)
                        .contains(Eskalationsgrund.BETRAG_DEVIATES);
            }
        }

        @Test
        @DisplayName("green: unfallbedingt=\"false\" as text stays silent")
        void theUnfallFlagAsTheTextFalseIsGreen() {
            assertThat(codes(guard.check(fall(), proposal("336.00"),
                    findings().erstattungArguments(
                            ERSTATTUNG_ARGUMENTS.replace("\"behandlungsdatum\"",
                                    "\"unfallbedingt\":\"false\",\"behandlungsdatum\"")).build())))
                    .doesNotContain(Eskalationsgrund.BETRAG_DEVIATES);
        }

        @Test
        @DisplayName("two argument findings sit in ONE reason with the code")
        void oneCodeOneEntry() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().erstattungArguments(
                            ERSTATTUNG_ARGUMENTS.replace("ATRA_DENT_B", "ATRA_DENT_PREMIUM")
                                    .replace("2026-05-03", "2027-05-03")).build());

            assertThat(codes(result)).containsOnlyOnce(Eskalationsgrund.BETRAG_DEVIATES);
            assertThat(text(result, Eskalationsgrund.BETRAG_DEVIATES))
                    .contains("ATRA_DENT_PREMIUM").contains("2027-05-03");
        }
    }

    @Nested
    @DisplayName("BETRAG_DEVIATES from the Leistungsbereich of the Rechenkern")
    class ErstattungsbereichAbweichend {

        private Bewertungsvorschlag assigned() {
            return proposal("336.00", "ZAHNERHALT", "NICHT_BESTIMMBAR");
        }

        private Befundbau withUndeterminable() {
            return findings().goz(finding("2197", "ENTHALTEN", "ZAHNERHALT", null),
                    finding("2200", "NICHT_BESTIMMBAR", null, null));
        }

        private String argumentsWithArea(String fuer2200) {
            return ERSTATTUNG_ARGUMENTS.replace(
                    "{\"goz\":\"2200\",\"leistungsbereich\":\"ZAHNERHALT\"",
                    "{\"goz\":\"2200\",\"leistungsbereich\":\"" + fuer2200 + "\"");
        }

        @Test
        @DisplayName("green: the calculation used the Leistungsbereich goz_pruefen named")
        void green() {
            assertThat(codes(guard.check(fall(), proposal("336.00"), findings().build())))
                    .doesNotContain(Eskalationsgrund.BETRAG_DEVIATES);
        }

        @Test
        @DisplayName("green: on NICHT_BESTIMMBAR the Leistungsbereich the model named applies")
        void nichtBestimmbarOnesWithTheCheckedLeistungsbereich() {
            BewertungResult result = guard.check(fall(), assigned(),
                    withUndeterminable().build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.BETRAG_DEVIATES);
        }

        @Test
        @DisplayName("red: the calculation used a different Leistungsbereich than the Befund's")
        void aForeignLeistungsbereichForTheBefund() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().erstattungArguments(argumentsWithArea("ZE")).build());

            assertThat(codes(result)).contains(Eskalationsgrund.BETRAG_DEVIATES);
            assertThat(text(result, Eskalationsgrund.BETRAG_DEVIATES))
                    .contains("2200").contains("ZE").contains("ZAHNERHALT");
        }

        @Test
        @DisplayName("red: with NICHT_BESTIMMBAR the model tells the guard ZAHNERHALT and the Rechenkern "
                + "ZE")
        void nichtBestimmbarOnesWithTwoLeistungsbereiche() {
            BewertungResult result = guard.check(fall(), assigned(),
                    withUndeterminable().erstattungArguments(argumentsWithArea("ZE")).build());

            assertThat(codes(result)).contains(Eskalationsgrund.BETRAG_DEVIATES);
            assertThat(text(result, Eskalationsgrund.BETRAG_DEVIATES))
                    .contains("2200").contains("ZE");
        }

        @Test
        @DisplayName("green: a Position without a Leistungsbereich is no claim that could be wrong")
        void withoutALeistungsbereichNoBefund() {
            String withoutArea = """
                    {"tarifId":"ATRA_DENT_B","versicherungsbeginn":"2025-01-01",
                     "behandlungsdatum":"2026-05-03",
                     "positionen":[{"goz":"2200","betrag":"195.00"}]}
                    """;

            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().erstattungArguments(withoutArea).build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.BETRAG_DEVIATES);
        }

        @Test
        @DisplayName("green: case decides nothing here either")
        void caseDoesNotMatter() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().erstattungArguments(argumentsWithArea("zahnerhalt")).build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.BETRAG_DEVIATES);
        }
    }

    @Nested
    @DisplayName("GOZ_UNCLEAR from the arguments of the Wissensdienst")
    class GozargumenteAbweichend {

        @Test
        @DisplayName("green: the question used the Tarif of the Vertrag")
        void green() {
            assertThat(codes(guard.check(fall(), proposal("336.00"), findings().build())))
                    .doesNotContain(Eskalationsgrund.GOZ_UNCLEAR);
        }

        @Test
        @DisplayName("red: the question was asked for a foreign Tarif - the Befunde clarify nothing")
        void aForeignTarif() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().gozArguments(
                            GOZ_ARGUMENTS.replace("ATRA_DENT_B", "ATRA_DENT_PREMIUM")).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.GOZ_UNCLEAR))
                    .contains("ATRA_DENT_PREMIUM").contains("ATRA_DENT_B");
        }

        @Test
        @DisplayName("red: a single foreign call among several spoils the batch")
        void oneForeignCallIsEnough() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().gozArguments(GOZ_ARGUMENTS,
                            GOZ_ARGUMENTS.replace("ATRA_DENT_B", "ATRA_DENT_PREMIUM")).build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
        }

        @Test
        @DisplayName("red: without a tarif in the arguments the Befund is worth just as little")
        void withoutATarif() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().gozArguments("{\"nummern\":[\"2197\",\"2200\"]}").build());

            assertThat(codes(result)).contains(Eskalationsgrund.GOZ_UNCLEAR);
        }
    }

    @Nested
    @DisplayName("ARZT_UNAVAILABLE")
    class ArztNichtVerfuegbar {

        @Test
        @DisplayName("green: the Arztservice answered")
        void green() {
            assertThat(codes(guard.check(fall(), proposal("336.00"), findings().build())))
                    .doesNotContain(Eskalationsgrund.ARZT_UNAVAILABLE);
        }

        @Test
        @DisplayName("red: the Arztservice was not asked")
        void notAsked() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().arzt(null, false, false).build());

            assertThat(codes(result)).contains(Eskalationsgrund.ARZT_UNAVAILABLE);
        }

        @Test
        @DisplayName("red: the call failed - never a silent Freigabe")
        void failed() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().arzt(null, true, true).build());

            assertThat(codes(result)).contains(Eskalationsgrund.ARZT_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("ARZT_FLAGGED")
    class ArztAuffaellig {

        @Test
        @DisplayName("green: plausibel and ueblich")
        void green() {
            assertThat(codes(guard.check(fall(), proposal("336.00"), findings().build())))
                    .doesNotContain(Eskalationsgrund.ARZT_FLAGGED);
        }

        @Test
        @DisplayName("red: the plausibilitaet is auffaellig")
        void conspicuous() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().arzt(arzt("auffaellig", "ueblich"), true, false).build());

            assertThat(codes(result)).contains(Eskalationsgrund.ARZT_FLAGGED);
            assertThat(text(result, Eskalationsgrund.ARZT_FLAGGED)).contains("auffaellig");
        }

        @Test
        @DisplayName("red: the notwendigkeit is fraglich")
        void questionable() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().arzt(arzt("plausibel", "fraglich"), true, false).build());

            assertThat(codes(result)).contains(Eskalationsgrund.ARZT_FLAGGED);
            assertThat(text(result, Eskalationsgrund.ARZT_FLAGGED)).contains("fraglich");
        }
    }

    @Nested
    @DisplayName("BETRAG_ABOVE_THRESHOLD")
    class BetragUeberSchwelle {


        @Test
        @DisplayName("green: exactly on the threshold is still below it")
        void onTheThreshold() {
            var guard = new ApprovalGuard(new BigDecimal("395.00"));

            assertThat(codes(guard.check(fall(), proposal("336.00"), findings().build())))
                    .doesNotContain(Eskalationsgrund.BETRAG_ABOVE_THRESHOLD);
        }

        @Test
        @DisplayName("red: the Rechnungsbetrag is above it, the text names both numbers")
        void above() {
            var guard = new ApprovalGuard(new BigDecimal("300.00"));
            BewertungResult result =
                    guard.check(fall(), proposal("336.00"), findings().build());

            assertThat(codes(result)).contains(Eskalationsgrund.BETRAG_ABOVE_THRESHOLD);
            assertThat(text(result, Eskalationsgrund.BETRAG_ABOVE_THRESHOLD))
                    .contains("395.00").contains("300.00");
        }
    }

    @Nested
    @DisplayName("VERTRAG_INACTIVE")
    class VertragInaktiv {

        @Test
        @DisplayName("green: the Vertrag is active")
        void green() {
            assertThat(codes(guard.check(fall(), proposal("336.00"), findings().build())))
                    .doesNotContain(Eskalationsgrund.VERTRAG_INACTIVE);
        }

        @Test
        @DisplayName("red: the Vertrag is inactive")
        void inactive() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().vertrag(VERTRAG.replace("\"aktiv\"", "\"inaktiv\"")).build());

            assertThat(codes(result)).contains(Eskalationsgrund.VERTRAG_INACTIVE);
            assertThat(text(result, Eskalationsgrund.VERTRAG_INACTIVE)).contains("inaktiv");
        }

        @Test
        @DisplayName("red: the Vertrag was not read at all")
        void withoutAVertrag() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().vertrag(null).build());

            assertThat(codes(result)).contains(Eskalationsgrund.VERTRAG_INACTIVE);
        }
    }

    @Nested
    @DisplayName("WARTEZEIT")
    class Wartezeit {

        @Test
        @DisplayName("green: the Wartezeit had expired on the treatment day")
        void expired() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().goz(finding("2197", "ENTHALTEN", "ZAHNERHALT", 8),
                            finding("2200", "ENTHALTEN", "ZAHNERHALT", null)).build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.WARTEZEIT);
        }

        @Test
        @DisplayName("red: the treatment happened before the Wartezeit ended")
        void notYetExpired() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().vertrag(VERTRAG.replace("2025-01-01", "2026-01-01"))
                            .goz(finding("2197", "ENTHALTEN", "ZAHNERHALT", 8),
                                    finding("2200", "ENTHALTEN", "ZAHNERHALT", null)).build());

            assertThat(codes(result)).contains(Eskalationsgrund.WARTEZEIT);
            assertThat(text(result, Eskalationsgrund.WARTEZEIT))
                    .contains("2197").contains("2026-09-01").contains("2026-05-03");
        }

        @Test
        @DisplayName("green: without grenzen.wartezeitMonate the Leistungsbereich Wartezeit does not apply")
        void withoutTheFieldNoRule() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().vertrag(VERTRAG.replace("2025-01-01", "2026-01-01"))
                            .tarifWartezeit(3, false).build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.WARTEZEIT);
        }

        @Test
        @DisplayName("red: the general Tarif Wartezeit from tarife_vergleichen had not elapsed yet")
        void generalWartezeit() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().vertrag(VERTRAG.replace("2025-01-01", "2026-01-01"))
                            .tarifWartezeit(8, false).build());

            assertThat(codes(result)).contains(Eskalationsgrund.WARTEZEIT);
            assertThat(text(result, Eskalationsgrund.WARTEZEIT))
                    .contains("allgemeine").contains("2026-09-01");
        }

        @Test
        @DisplayName("green: the general Wartezeit is waived on Vorversicherung")
        void waivedOnVorversicherung() {
            String preInsured = VERTRAG.replace("2025-01-01", "2026-01-01")
                    .replace("\"vorversicherung\":false", "\"vorversicherung\":true");

            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().vertrag(preInsured).tarifWartezeit(8, true).build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.WARTEZEIT);
        }

        @Test
        @DisplayName("red: without a Vorversicherung the Tarif's waiver flag does not help")
        void waivedWithoutVorversicherung() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().vertrag(VERTRAG.replace("2025-01-01", "2026-01-01"))
                            .tarifWartezeit(8, true).build());

            assertThat(codes(result)).contains(Eskalationsgrund.WARTEZEIT);
        }

        @Test
        @DisplayName("red: without a result from tarife_vergleichen the general Wartezeit is unchecked")
        void withoutAComparisonTheGeneralWartezeitIsUnchecked() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().tarifWartezeit(null, false).build());

            assertThat(codes(result)).contains(Eskalationsgrund.WARTEZEIT);
            assertThat(text(result, Eskalationsgrund.WARTEZEIT))
                    .contains("nicht geprueft").contains(ToolSelection.COMPARISON);
        }

        @Test
        @DisplayName("red: if no row matches the Tarif of the Vertrag, the Wartezeit is unchecked too")
        void aForeignTarifIsNoComparison() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().tarifWartezeit(null, false)
                            .vertrag(VERTRAG.replace("ATRA_DENT_B", "ATRA_DENT_Z")).build());

            assertThat(codes(result)).contains(Eskalationsgrund.WARTEZEIT);
        }

        @Test
        @DisplayName("green: if the general Wartezeit is waived on Vorversicherung, it is not missing either")
        void aWaiverNeedsNoDeadline() {
            String preInsured = VERTRAG.replace("\"vorversicherung\":false",
                    "\"vorversicherung\":true");

            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().vertrag(preInsured).tarifWartezeit(null, true).build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.WARTEZEIT);
        }

        @Test
        @DisplayName("red: the general and the Leistungsbereich Wartezeit sit together in one reason")
        void bothTimeoutsTogether() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().vertrag(VERTRAG.replace("2025-01-01", "2026-01-01"))
                            .tarifWartezeit(6, false)
                            .goz(finding("2197", "ENTHALTEN", "ZAHNERHALT", 8),
                                    finding("2200", "ENTHALTEN", "ZAHNERHALT", null)).build());

            assertThat(text(result, Eskalationsgrund.WARTEZEIT))
                    .contains("allgemeine").contains("2026-07-01")
                    .contains("2197").contains("2026-09-01");
        }

        @Test
        @DisplayName("green: a NICHT_ENTHALTEN Befund contributes no Wartezeit")
        void onlyEnthaltenOnes() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().vertrag(VERTRAG.replace("2025-01-01", "2026-01-01"))
                            .tarifWartezeit(3, false)
                            .goz(finding("2197", "NICHT_ENTHALTEN", "ZAHNERHALT", 8),
                                    finding("2200", "ENTHALTEN", "ZAHNERHALT", null)).build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.WARTEZEIT);
        }
    }

    @Nested
    @DisplayName("PATIENT_UNCLEAR")
    class PatientUnklar {

        private static final String MIT_UMLAUT = VERTRAG.replace("Mueller", "Müller");

        private static String vertragOn(String vorname, String nachname) {
            return VERTRAG.replace("\"vorname\":\"Anna\",\"nachname\":\"Mueller\"",
                    "\"vorname\":\"" + vorname + "\",\"nachname\":\"" + nachname + "\"");
        }

        @Test
        @DisplayName("green: the Kundin's name is on the Rechnung, case does not matter")
        void green() {
            assertThat(codesFuer("\"Frau ANNA MUELLER\"", VERTRAG))
                    .doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("green: the Rechnung writes Mueller, the Vertrag Müller")
        void anUmlautInTheRechnung() {
            assertThat(codesFuer("\"Anna Müller\"", VERTRAG))
                    .doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("green: and the other way round too - both sides are normalized")
        void anUmlautInTheVertrag() {
            assertThat(codesFuer("\"Anna Mueller\"", MIT_UMLAUT))
                    .doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("green: salutation and title do not count as a Vorname")
        void salutationAndTitle() {
            assertThat(codesFuer("\"Frau Dr. Müller\"", MIT_UMLAUT))
                    .doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("green: \"Herrn\" is a form of address, not a Vorname")
        void salutationHerr() {
            assertThat(codesFuer("\"Herrn Schuster\"", vertragOn("Bernd", "Schuster")))
                    .doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("green: Familie, Eheleute and Fraeulein likewise")
        void furtherSalutations() {
            for (String anrede : List.of("Fam.", "Familie", "Eheleute", "Frl.")) {
                assertThat(codesFuer("\"" + anrede + " Müller\"", MIT_UMLAUT))
                        .as(anrede).doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
            }
        }

        @Test
        @DisplayName("green: a period separates even without a following space")
        void aFusedAbbreviation() {
            assertThat(codesFuer("\"A.Müller\"", MIT_UMLAUT))
                    .doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("green: a double Vorname matches if one part of it is the Vertrag's")
        void aDoubleVorname() {
            assertThat(codesFuer("\"Anna-Maria Müller\"", MIT_UMLAUT))
                    .doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("green: an abbreviated Vorname is no discrepancy")
        void abbreviatedVorname() {
            assertThat(codesFuer("\"A. Müller\"", MIT_UMLAUT))
                    .doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("green: if the Rechnung names no Vorname at all, there is nothing to compare")
        void withoutAVornameInTheRechnung() {
            assertThat(codesFuer("\"Müller\"", MIT_UMLAUT))
                    .doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("green: what stands after the Nachname is no Vorname")
        void aDoubleSurnameWithoutAVorname() {
            assertThat(codesFuer("\"Müller-Schmidt\"", MIT_UMLAUT))
                    .doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("green: foreign diacritics are stripped - Papadákis is Papadakis")
        void diacritics() {
            assertThat(codesFuer("\"Dimitrios Papadákis\"", vertragOn("Dimitrios", "Papadakis")))
                    .doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("green: several marks on one letter too - Nguyễn is Nguyen")
        void multipleDiacritics() {
            assertThat(codesFuer("\"Quang Nguyễn\"", vertragOn("Quang", "Nguyen")))
                    .doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("green: \"Nachname, Vorname\" with the Vorname from the Vertrag")
        void nachnameFirstWithAComma() {
            assertThat(codesFuer("\"Müller, Anna\"", MIT_UMLAUT))
                    .doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("green: without a comma the second name part stays a name part")
        void nachnameFirstWithoutAComma() {
            assertThat(codesFuer("\"Müller Lena\"", MIT_UMLAUT))
                    .doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("green: Ozdemir, Oezdemir, Özdemir - the Vertrag carries the umlaut")
        void oezdemirInEverySpelling() {
            for (String schreibweise : List.of("Özdemir", "Oezdemir", "Ozdemir", "ÖZDEMIR")) {
                assertThat(codesFuer("\"Yusuf " + schreibweise + "\"",
                        vertragOn("Yusuf", "Özdemir")))
                        .as(schreibweise).doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
            }
        }

        @Test
        @DisplayName("green: the bare base letter too - \"Muller\" against Müller")
        void muellerWithoutTheUmlautCharacter() {
            assertThat(codesFuer("\"Anna Muller\"", MIT_UMLAUT))
                    .doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("green: without a Vorname in the Vertrag the Nachname stands alone")
        void withoutAVornameInTheVertrag() {
            assertThat(codesFuer("\"Lena Müller\"", MIT_UMLAUT.replace("\"vorname\":\"Anna\",", "")))
                    .doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("green: no Patient on the Rechnung is no claim, no discrepancy")
        void withoutAPatient() {
            assertThat(codesFuer("null", VERTRAG)).doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("green: no Rechnung at all is no discrepancy either")
        void withoutARechnung() {
            JsonNode fall = node("""
                    {"id":50071,"kundenId":4711,"behandlungsdatum":"2026-05-03",
                     "positionen":%s,"rechnungsbetrag":"395.00","rechnung":null,
                     "status":"in_pruefung","bearbeitungsprotokoll":[]}
                    """.formatted(POSITIONEN));

            assertThat(codes(guard.check(fall, proposal("336.00"), findings().build())))
                    .doesNotContain(Eskalationsgrund.PATIENT_UNCLEAR);
        }

        @Test
        @DisplayName("red: a different name is on the Rechnung")
        void aDifferentName() {
            JsonNode fall = fall(POSITIONEN, "395.00", "\"Max Beispiel\"", "2026-05-03");
            BewertungResult result = guard.check(fall, proposal("336.00"), findings().build());

            assertThat(codes(result)).contains(Eskalationsgrund.PATIENT_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.PATIENT_UNCLEAR))
                    .contains("Max Beispiel").contains("Mueller");
        }

        @Test
        @DisplayName("red: the same Nachname, a different Vorname - the text names both")
        void aDifferentVorname() {
            JsonNode fall = fall(POSITIONEN, "395.00", "\"Lena Müller\"", "2026-05-03");
            BewertungResult result = guard.check(fall, proposal("336.00"),
                    findings().vertrag(MIT_UMLAUT).build());

            assertThat(codes(result)).contains(Eskalationsgrund.PATIENT_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.PATIENT_UNCLEAR))
                    .contains("Lena").contains("Anna");
        }

        @Test
        @DisplayName("red: form of address and comma form together - \"Frau Müller, Lena\"")
        void salutationBeforeTheCommaForm() {
            JsonNode fall = fall(POSITIONEN, "395.00", "\"Frau Müller, Lena\"", "2026-05-03");
            BewertungResult result = guard.check(fall, proposal("336.00"),
                    findings().vertrag(MIT_UMLAUT).build());

            assertThat(codes(result)).contains(Eskalationsgrund.PATIENT_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.PATIENT_UNCLEAR))
                    .contains("Lena").contains("Anna");
        }

        @Test
        @DisplayName("red: the child shows up in the \"Nachname, Vorname\" form too")
        void aDifferentVornameAfterTheComma() {
            JsonNode fall = fall(POSITIONEN, "395.00", "\"Müller, Lena\"", "2026-05-03");
            BewertungResult result = guard.check(fall, proposal("336.00"),
                    findings().vertrag(MIT_UMLAUT).build());

            assertThat(codes(result)).contains(Eskalationsgrund.PATIENT_UNCLEAR);
            assertThat(text(result, Eskalationsgrund.PATIENT_UNCLEAR))
                    .contains("Lena").contains("Anna");
        }

        private List<String> codesFuer(String patient, String vertrag) {
            return codes(guard.check(fall(POSITIONEN, "395.00", patient, "2026-05-03"),
                    proposal("336.00"), findings().vertrag(vertrag).build()));
        }
    }

    @Nested
    @DisplayName("DUPLICATE")
    class Duplikat {

        @Test
        @DisplayName("green: the Schadensfall is no duplicate of itself")
        void theSchadensfallItself() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().otherCases(
                            "{\"id\":50071,\"behandlungsdatum\":\"2026-05-03\","
                                    + "\"rechnungsbetrag\":\"395.00\"}").build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.DUPLICATE);
        }

        @Test
        @DisplayName("green: an empty history is an Auskunft")
        void anEmptyHistory() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().otherCases().build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.DUPLICATE);
        }

        @Test
        @DisplayName("red: without meine_schadensfaelle_auflisten a duplicate cannot be ruled out")
        void withoutASchadensfallList() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().withoutASchadensfallList().build());

            assertThat(codes(result)).contains(Eskalationsgrund.DUPLICATE);
            assertThat(text(result, Eskalationsgrund.DUPLICATE)).contains("nicht gelesen");
        }

        @Test
        @DisplayName("green: same Datum, different Betrag")
        void aDifferentBetrag() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().otherCases(
                            "{\"id\":50014,\"behandlungsdatum\":\"2026-05-03\","
                                    + "\"rechnungsbetrag\":\"120.00\"}").build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.DUPLICATE);
        }

        @Test
        @DisplayName("red: another Schadensfall with the same Datum and the same Betrag")
        void theSameSchadensfall() {
            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().otherCases(
                            "{\"id\":50014,\"behandlungsdatum\":\"2026-05-03\","
                                    + "\"rechnungsbetrag\":\"395.0\"}").build());

            assertThat(codes(result)).contains(Eskalationsgrund.DUPLICATE);
            assertThat(text(result, Eskalationsgrund.DUPLICATE))
                    .contains("50014").contains("2026-05-03");
        }
    }

    @Nested
    @DisplayName("EXTRACTION_INCOMPLETE")
    class ExtraktionUnvollstaendig {

        @Test
        @DisplayName("green: every Position has a GOZ Nummer and a Betrag")
        void green() {
            assertThat(codes(guard.check(fall(), proposal("336.00"), findings().build())))
                    .doesNotContain(Eskalationsgrund.EXTRACTION_INCOMPLETE);
        }

        @Test
        @DisplayName("green: a Position without a Gebuehrennummer is no capture defect")
        void withoutAGozThereIsNoDefect() {
            JsonNode fall = fall("""
                    [{"goz":"2197","leistungsbereich":"ZAHNERHALT","betrag":"200.00","beschreibung":"Fuellung"},
                     {"goz":null,"leistungsbereich":null,"betrag":"195.00","beschreibung":"Material"}]
                    """, "395.00", "\"Anna Mueller\"", "2026-05-03");

            assertThat(codes(guard.check(fall, proposal("336.00"), findings().build())))
                    .doesNotContain(Eskalationsgrund.EXTRACTION_INCOMPLETE);
        }

        @Test
        @DisplayName("red: the Positionen add up to less than the Gesamtbetrag of the Rechnung")
        void theSumBelowTheGesamtbetrag() {
            JsonNode fall = node("""
                    {"id":50071,"kundenId":4711,"behandlungsdatum":"2026-05-03","positionen":%s,
                     "rechnungsbetrag":"395.00","erstattungsbetrag":null,"status":"in_pruefung",
                     "rechnung":{"rechnungsnummer":"R-2026-0815","rechnungsdatum":"2026-05-04",
                                 "absender":"Zahnarztpraxis Dr. Beispiel","patient":"Anna Mueller",
                                 "gesamtbetrag":"1186.30"},
                     "bewertung":null,"bearbeitungsprotokoll":[]}
                    """.formatted(POSITIONEN));
            BewertungResult result = guard.check(fall, proposal("336.00"), findings().build());

            assertThat(codes(result)).contains(Eskalationsgrund.EXTRACTION_INCOMPLETE);
            assertThat(text(result, Eskalationsgrund.EXTRACTION_INCOMPLETE))
                    .contains("395.00").contains("1186.30");
        }

        @Test
        @DisplayName("red: the Rechnung is there, but without a Gesamtbetrag - the sum check would "
                + "otherwise be optional for the model")
        void aRechnungWithoutAGesamtbetrag() {
            JsonNode fall = node("""
                    {"id":50071,"kundenId":4711,"behandlungsdatum":"2026-05-03","positionen":%s,
                     "rechnungsbetrag":"395.00","erstattungsbetrag":null,"status":"in_pruefung",
                     "rechnung":{"rechnungsnummer":"R-2026-0815","rechnungsdatum":"2026-05-04",
                                 "absender":"Zahnarztpraxis Dr. Beispiel","patient":"Anna Mueller"},
                     "bewertung":null,"bearbeitungsprotokoll":[]}
                    """.formatted(POSITIONEN));
            BewertungResult result = guard.check(fall, proposal("336.00"), findings().build());

            assertThat(codes(result)).contains(Eskalationsgrund.EXTRACTION_INCOMPLETE);
            assertThat(text(result, Eskalationsgrund.EXTRACTION_INCOMPLETE))
                    .contains("Gesamtbetrag");
        }

        @Test
        @DisplayName("green: a Schadensfall with no Rechnung at all misses no Gesamtbetrag either")
        void withoutARechnungNoGesamtbetrag() {
            JsonNode fall = node("""
                    {"id":50071,"kundenId":4711,"behandlungsdatum":"2026-05-03","positionen":%s,
                     "rechnungsbetrag":"395.00","erstattungsbetrag":null,"status":"in_pruefung",
                     "rechnung":null,"bewertung":null,"bearbeitungsprotokoll":[]}
                    """.formatted(POSITIONEN));

            assertThat(codes(guard.check(fall, proposal("336.00"), findings().build())))
                    .doesNotContain(Eskalationsgrund.EXTRACTION_INCOMPLETE);
        }

        @Test
        @DisplayName("green: sum and Gesamtbetrag agree, even at a different scale")
        void theSumIsRight() {
            JsonNode fall = node("""
                    {"id":50071,"kundenId":4711,"behandlungsdatum":"2026-05-03","positionen":%s,
                     "rechnungsbetrag":"395.00","erstattungsbetrag":null,"status":"in_pruefung",
                     "rechnung":{"patient":"Anna Mueller","gesamtbetrag":"395.0"},
                     "bewertung":null,"bearbeitungsprotokoll":[]}
                    """.formatted(POSITIONEN));

            assertThat(codes(guard.check(fall, proposal("336.00"), findings().build())))
                    .doesNotContain(Eskalationsgrund.EXTRACTION_INCOMPLETE);
        }

        @Test
        @DisplayName("red: no Positionen at all - otherwise that would switch off GOZ_UNCLEAR")
        void withoutPositionen() {
            JsonNode fall = fall("[]", "395.00", "\"Anna Mueller\"", "2026-05-03");
            BewertungResult result = guard.check(fall, proposal("336.00"), findings().build());

            assertThat(codes(result)).contains(Eskalationsgrund.EXTRACTION_INCOMPLETE);
            assertThat(text(result, Eskalationsgrund.EXTRACTION_INCOMPLETE))
                    .contains("keine Positionen");
        }

        @Test
        @DisplayName("red: no Rechnungsbetrag - otherwise that would switch off the threshold")
        void withoutARechnungsbetrag() {
            JsonNode fall = node("""
                    {"id":50071,"kundenId":4711,"behandlungsdatum":"2026-05-03",
                     "positionen":%s,"rechnungsbetrag":null,"rechnung":null,
                     "status":"in_pruefung","bearbeitungsprotokoll":[]}
                    """.formatted(POSITIONEN));
            BewertungResult result = guard.check(fall, proposal("336.00"), findings().build());

            assertThat(codes(result)).contains(Eskalationsgrund.EXTRACTION_INCOMPLETE);
            assertThat(text(result, Eskalationsgrund.EXTRACTION_INCOMPLETE))
                    .contains("Rechnungsbetrag");
        }

        @Test
        @DisplayName("red: a Position without a Betrag")
        void withoutABetrag() {
            JsonNode fall = fall("""
                    [{"goz":"2197","leistungsbereich":"ZAHNERHALT","betrag":null,"beschreibung":"Fuellung"},
                     {"goz":"2200","leistungsbereich":"ZAHNERHALT","betrag":"195.00","beschreibung":"Fuellung"}]
                    """, "395.00", "\"Anna Mueller\"", "2026-05-03");

            assertThat(codes(guard.check(fall, proposal("336.00"), findings().build())))
                    .contains(Eskalationsgrund.EXTRACTION_INCOMPLETE);
        }
    }

    @Nested
    @DisplayName("off-shape JSON")
    class FormfremdesJson {

        @Test
        @DisplayName("an Arztauskunft with plausibilitaet as an object does not throw")
        void arztauskunftWithAnObjectField() {
            String askew = """
                    {"plausibilitaet":{"wert":"auffaellig"},"notwendigkeit":["fraglich"],
                     "text":"Einschaetzung.","hinweis":"Sprachmodell."}
                    """;

            BewertungResult result = guard.check(fall(), proposal("336.00"),
                    findings().arzt(askew, true, false).build());

            assertThat(codes(result)).doesNotContain(Eskalationsgrund.ARZT_FLAGGED);
        }

        @Test
        @DisplayName("a Schadensfall with object fields instead of values does not throw")
        void aSchadensfallWithObjectFields() {
            JsonNode fall = node("""
                    {"id":50071,"kundenId":4711,"behandlungsdatum":{"tag":"2026-05-03"},
                     "positionen":[{"goz":{"nummer":"2197"},"betrag":{"wert":"200.00"}}],
                     "rechnungsbetrag":{"wert":"395.00"},
                     "rechnung":{"patient":{"name":"Anna Mueller"}},
                     "status":"in_pruefung","bearbeitungsprotokoll":[]}
                    """);

            BewertungResult result = guard.check(fall, proposal("336.00"), findings().build());

            assertThat(codes(result)).contains(Eskalationsgrund.EXTRACTION_INCOMPLETE);
            assertThat(result.empfehlung()).isEqualTo("eskalation");
        }
    }


    @Nested
    @DisplayName("Positionen")
    class Positionen {

        @Test
        @DisplayName("a missing Leistungsbereich comes from the GOZ Befund of the same Nummer")
        void leistungsbereichFromTheBefund() {
            var proposal = new Bewertungsvorschlag("freigabe", RECHENKERN, List.of(
                    new Bewertungsvorschlag.Position("2197", null, "ENTHALTEN", "versichert")),
                    "Passt.");

            BewertungResult result = guard.check(fall(), proposal, findings().build());

            assertThat(result.positionen().getFirst().leistungsbereich()).isEqualTo("ZAHNERHALT");
        }

        @Test
        @DisplayName("a Leistungsbereich that was named stays")
        void theNamedOneStays() {
            var proposal = new Bewertungsvorschlag("freigabe", RECHENKERN, List.of(
                    new Bewertungsvorschlag.Position("2197", "ZAHNERSATZ", "ENTHALTEN", "versichert")),
                    "Passt.");

            BewertungResult result = guard.check(fall(), proposal, findings().build());

            assertThat(result.positionen().getFirst().leistungsbereich()).isEqualTo("ZAHNERSATZ");
        }
    }


    static final String POSITIONEN = """
            [{"goz":"2197","leistungsbereich":"ZAHNERHALT","betrag":"200.00","beschreibung":"Adhaesive Befestigung"},
             {"goz":"2200","leistungsbereich":"ZAHNERHALT","betrag":"195.00","beschreibung":"Fuellung"}]
            """;

    static final String GOZ_ARGUMENTS = """
            {"tarif":"ATRA_DENT_B","nummern":["2197","2200"]}
            """;

    static final String ERSTATTUNG_ARGUMENTS = """
            {"tarifId":"ATRA_DENT_B","versicherungsbeginn":"2025-01-01",
             "behandlungsdatum":"2026-05-03",
             "positionen":[
               {"goz":"2197","leistungsbereich":"ZAHNERHALT","betrag":"200.00","beschreibung":"Adhaesive Befestigung"},
               {"goz":"2200","leistungsbereich":"ZAHNERHALT","betrag":"195.00","beschreibung":"Fuellung"}],
             "verbrauch":{"gesamt":"102.00"}}
            """;

    static final String VERTRAG = """
            {"id":4711,"vorname":"Anna","nachname":"Mueller","geburtsdatum":"1990-04-12",
             "tarifId":"ATRA_DENT_B","versicherungsbeginn":"2025-01-01","status":"aktiv",
             "vorversicherung":false}
            """;

    static String arzt(String plausibilitaet, String notwendigkeit) {
        return """
                {"plausibilitaet":"%s","notwendigkeit":"%s","positionen":[],
                 "text":"Einschaetzung.","quelle":"Arztservice","modell":"gemini-3.6-flash",
                 "hinweis":"Sprachmodell, keine Entscheidung."}
                """.formatted(plausibilitaet, notwendigkeit);
    }

    static String finding(String number, String status, String leistungsbereich, Integer wartezeitMonate) {
        return """
                {"nummer":"%s","bezeichnung":"Leistung","abschnitt":"K","leistungsbereich":%s,
                 "status":"%s","quote":85,"grenzen":%s,"begruendung":"Begruendung"}
                """.formatted(number,
                leistungsbereich == null ? "null" : "\"" + leistungsbereich + "\"",
                status,
                wartezeitMonate == null ? "{}" : "{\"wartezeitMonate\":" + wartezeitMonate + "}");
    }

    static JsonNode fall() {
        return fall(POSITIONEN, "395.00", "\"Anna Mueller\"", "2026-05-03");
    }

    static JsonNode fall(String positionen, String rechnungsbetrag, String patient,
                         String behandlungsdatum) {
        return node("""
                {"id":50071,"kundenId":4711,"behandlungsdatum":"%s","positionen":%s,
                 "rechnungsbetrag":"%s","erstattungsbetrag":null,"status":"in_pruefung",
                 "rechnung":{"rechnungsnummer":"R-2026-0815","rechnungsdatum":"2026-05-04",
                             "absender":"Zahnarztpraxis Dr. Beispiel","patient":%s,
                             "gesamtbetrag":"%s"},
                 "bewertung":null,"bearbeitungsprotokoll":[]}
                """.formatted(behandlungsdatum, positionen, rechnungsbetrag, patient, rechnungsbetrag));
    }

    static Bewertungsvorschlag proposal(String betrag) {
        return new Bewertungsvorschlag("freigabe", new BigDecimal(betrag), List.of(
                new Bewertungsvorschlag.Position("2197", "ZAHNERHALT", "ENTHALTEN", "versichert"),
                new Bewertungsvorschlag.Position("2200", "ZAHNERHALT", "ENTHALTEN", "versichert")),
                "Beide Positionen sind versichert.");
    }

    static Bewertungsvorschlag proposal(String betrag, String leistungsbereich, String zustand) {
        return new Bewertungsvorschlag("freigabe", new BigDecimal(betrag), List.of(
                new Bewertungsvorschlag.Position("2197", "ZAHNERHALT", "ENTHALTEN", "versichert"),
                new Bewertungsvorschlag.Position("2200", leistungsbereich, zustand,
                        "gehoert zur Hauptbehandlung")),
                "Die zweite Position folgt der ersten.");
    }

    static Befundbau findings() {
        return new Befundbau();
    }

    static final class Befundbau {

        private String vertrag = VERTRAG;
        private List<String> goz = List.of(
                finding("2197", "ENTHALTEN", "ZAHNERHALT", null),
                finding("2200", "ENTHALTEN", "ZAHNERHALT", null));
        private List<String> gozArgumente = List.of(GOZ_ARGUMENTS);
        private String erstattung = "336.00";
        private String erstattungsargumente = ERSTATTUNG_ARGUMENTS;
        private String arzt = ApprovalGuardTest.arzt("plausibel", "ueblich");
        private boolean aufgerufen = true;
        private boolean failed;
        private List<String> other = List.of();
        private boolean faellelisteGelesen = true;
        private Integer generalWartezeit = 8;
        private boolean waivedOnVorversicherung;

        Befundbau vertrag(String json) {
            this.vertrag = json;
            return this;
        }

        Befundbau goz(String... befunde) {
            this.goz = List.of(befunde);
            return this;
        }

        Befundbau erstattung(String betrag) {
            this.erstattung = betrag;
            return this;
        }

        Befundbau gozArguments(String... arguments) {
            this.gozArgumente = List.of(arguments);
            return this;
        }

        Befundbau erstattungArguments(String arguments) {
            this.erstattungsargumente = arguments;
            return this;
        }

        Befundbau arzt(String json, boolean aufgerufen, boolean failed) {
            this.arzt = json;
            this.aufgerufen = aufgerufen;
            this.failed = failed;
            return this;
        }

        Befundbau otherCases(String... faelle) {
            this.other = List.of(faelle);
            this.faellelisteGelesen = true;
            return this;
        }

        Befundbau withoutASchadensfallList() {
            this.other = List.of();
            this.faellelisteGelesen = false;
            return this;
        }

        Befundbau tarifWartezeit(Integer monate, boolean waivedOnVorversicherung) {
            this.generalWartezeit = monate;
            this.waivedOnVorversicherung = waivedOnVorversicherung;
            return this;
        }

        RawFindings build() {
            List<JsonNode> findings = new ArrayList<>();
            goz.forEach(json -> findings.add(node(json)));
            List<JsonNode> gozInputs = new ArrayList<>();
            gozArgumente.forEach(json -> gozInputs.add(node(json)));
            List<JsonNode> cases = new ArrayList<>();
            other.forEach(json -> cases.add(node(json)));
            return new RawFindings(
                    Optional.ofNullable(vertrag).map(ApprovalGuardTest::node),
                    findings, gozInputs,
                    Optional.ofNullable(erstattung).map(BigDecimal::new),
                    Optional.ofNullable(erstattungsargumente).map(ApprovalGuardTest::node),
                    Optional.ofNullable(arzt).map(ApprovalGuardTest::node),
                    aufgerufen, failed, cases, faellelisteGelesen,
                    Optional.ofNullable(generalWartezeit), waivedOnVorversicherung);
        }
    }

    static JsonNode node(String json) {
        return IMAGES.readTree(json);
    }

    static List<String> codes(BewertungResult result) {
        return result.gruende().stream().map(Eskalationsgrund::code).toList();
    }

    static String text(BewertungResult result, String code) {
        return result.gruende().stream()
                .filter(grund -> grund.code().equals(code))
                .map(Eskalationsgrund::text)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Kein Grund mit dem Code " + code));
    }
}
