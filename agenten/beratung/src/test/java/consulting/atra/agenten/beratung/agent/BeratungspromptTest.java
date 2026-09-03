package consulting.atra.agenten.beratung.agent;

import consulting.atra.agenten.beratung.tools.SignalTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BeratungspromptTest {

    @Test
    @DisplayName("the Beratung speaks as atra.denta, in both Beratung roles")
    void namesThePersona() {
        assertThat(Beratungsprompt.forMode(Beratungsmodus.BESTANDSBERATUNG, null, false))
                .contains("atra.denta");
        assertThat(Beratungsprompt.forMode(Beratungsmodus.NEUBERATUNG, null, false))
                .contains("atra.denta");
    }

    @Test
    @DisplayName("bestandsberatung asks for nothing that is already in the Akte")
    void bestandsberatungDoesNotAskForMasterData() {
        String portfolio = Beratungsprompt.forMode(Beratungsmodus.BESTANDSBERATUNG, null, false);

        assertThat(portfolio)
                .contains("DIESE KUNDIN IST VERSICHERT")
                .contains("EINE TARIFFRAGE IST BEI IHR EINE WECHSELFRAGE")
                .contains("FRAGEN SIE NICHT NACH IHREN STAMMDATEN")
                .doesNotContain("OHNE EINTRITTSALTER GIBT ES KEINE TARIFEMPFEHLUNG");
    }

    @Test
    @DisplayName("neuberatung asks for what cannot be looked up without an Akte")
    void neuberatungAsksForTheEintrittsalter() {
        String fresh = Beratungsprompt.forMode(Beratungsmodus.NEUBERATUNG, null, false);

        assertThat(fresh)
                .contains("ES IST NIEMAND ANGEMELDET")
                .contains("OHNE EINTRITTSALTER GIBT ES KEINE TARIFEMPFEHLUNG")
                .doesNotContain("DIESE KUNDIN IST VERSICHERT");
    }

    @Test
    @DisplayName("the Kontaktdaten are read back for confirmation before anything is written")
    void kontaktdatenAreConfirmedBeforeTheyAreWritten() {
        String portfolio = Beratungsprompt.forMode(Beratungsmodus.BESTANDSBERATUNG, null, false);

        assertThat(portfolio)
                .contains("KONTAKTDATEN ÄNDERN SIE ERST NACH BESTÄTIGUNG")
                .contains("meine_kontaktdaten_aendern")
                .contains(SignalTools.FOLLOW_UP)
                .contains("Ihr Zug endet damit, und Sie ändern in ihm NICHTS")
                .contains("alle vier Adressfelder")
                .contains("NAME UND GEBURTSDATUM ÄNDERN SIE NICHT");
    }

    @Test
    @DisplayName("only the Bestandsberatung may change Kontaktdaten -- it is the only mode holding the tool")
    void noKontaktdatenWithoutAnAkte() {
        assertThat(Beratungsprompt.forMode(Beratungsmodus.NEUBERATUNG, null, false))
                .doesNotContain("meine_kontaktdaten_aendern");
        assertThat(Beratungsprompt.forMode(Beratungsmodus.RECHNUNG_EINREICHEN, "{}", true))
                .doesNotContain("meine_kontaktdaten_aendern");
    }

    @Test
    @DisplayName("the reference from the Tarif comparison is no Fundstelle")
    void aComparisonReferenceIsNoFundstelle() {
        for (Beratungsmodus modus : new Beratungsmodus[] {
                Beratungsmodus.BESTANDSBERATUNG, Beratungsmodus.NEUBERATUNG}) {
            assertThat(Beratungsprompt.forMode(modus, null, false))
                    .as("modus=" + modus)
                    .contains("tarife_vergleichen")
                    .contains("keine Fundstelle");
        }
    }

    @Test
    @DisplayName("the Vertrag that was read out appears as a raw result in the prompt")
    void theVertragAppearsRawInThePrompt() {
        String raw = "{\"id\":10003,\"tarifId\":\"ATRA_DENT_X\",\"geburtsdatum\":\"1978-07-22\"}";

        String withVertrag = Beratungsprompt.forMode(Beratungsmodus.BESTANDSBERATUNG, raw, false);

        assertThat(withVertrag).contains(raw).contains("nicht geraten");
        assertThat(Beratungsprompt.forMode(Beratungsmodus.BESTANDSBERATUNG, null, false))
                .doesNotContain("DER VERTRAG DIESER KUNDIN");
        assertThat(Beratungsprompt.forMode(Beratungsmodus.BESTANDSBERATUNG, "  ", false))
                .doesNotContain("DER VERTRAG DIESER KUNDIN");
    }

    @Test
    @DisplayName("the prompt asks for the first call only when there is no Vertrag block")
    void theFirstCallOnlyWithoutTheVertragBlock() {
        String raw = "{\"id\":10003,\"tarifId\":\"ATRA_DENT_X\"}";

        assertThat(Beratungsprompt.forMode(Beratungsmodus.BESTANDSBERATUNG, raw, false))
                .doesNotContain("Beginnen Sie JEDES Anliegen mit mein_vertrag_lesen");
        assertThat(Beratungsprompt.forMode(Beratungsmodus.BESTANDSBERATUNG, null, false))
                .contains("Beginnen Sie JEDES Anliegen mit mein_vertrag_lesen");
    }

    @Test
    @DisplayName("prose stays the rule, headings stay excluded")
    void sticksToTheProseStyle() {
        assertThat(Beratungsprompt.forMode(Beratungsmodus.BESTANDSBERATUNG, null, false))
                .contains("ohne Überschriften")
                .contains("Prosa bleibt der Regelfall");
    }

    @Test
    @DisplayName("both roles name the signal tools explicitly")
    void bothRolesNameTheSignalTools() {
        for (Beratungsmodus modus : new Beratungsmodus[] {
                Beratungsmodus.BESTANDSBERATUNG, Beratungsmodus.NEUBERATUNG}) {
            assertThat(Beratungsprompt.forMode(modus, null, false))
                    .as("modus=" + modus)
                    .contains(SignalTools.FOLLOW_UP)
                    .contains(SignalTools.REJECT);
        }
    }

    @Test
    @DisplayName("the prompt forbids naming another service in the refusal")
    void aRefusalNamesNoForeignResponsibility() {
        assertThat(Beratungsprompt.forMode(Beratungsmodus.BESTANDSBERATUNG, null, false))
                .contains("über sich")
                .doesNotContain("Arztservice");
    }

    @Test
    @DisplayName("the text points at a view instead of repeating its numbers")
    void pointsAtTheView() {
        for (String prompt : new String[] {
                Beratungsprompt.forMode(Beratungsmodus.BESTANDSBERATUNG, null, false),
                Beratungsprompt.forMode(Beratungsmodus.NEUBERATUNG, null, false)}) {
            assertThat(prompt)
                    .contains("WAS SIE NACHSCHLAGEN, WIRD GEZEIGT")
                    .contains("ZÄHLEN SIE DANN NICHT AUF");
            assertThat(prompt).contains("Die Tabelle ist kein Beleg");
        }
    }

    @Test
    @DisplayName("whoever compares Beitraege looks up the Tarif names first")
    void theBeitragComparisonNeedsTheNames() {
        assertThat(Beratungsprompt.forMode(Beratungsmodus.BESTANDSBERATUNG, null, false))
                .contains("tarife_auflisten");
    }

    @Test
    @DisplayName("intake does not advise: no Beleg, Tarifwechsel, GOZ, Leitfaden or view block")
    void intakePrompt() {
        String prompt = Beratungsprompt.forMode(Beratungsmodus.RECHNUNG_EINREICHEN,
                "{\"tarif\":\"ATRA_DENT_B\"}", true);
        assertThat(prompt).contains("RECHNUNG EINREICHEN").contains("schadensfall_einreichen")
                .contains("keine Erstattung").doesNotContain("goz_pruefen")
                .doesNotContain("Beratungsleitfaden").doesNotContain("tarife_vergleichen")
                .doesNotContain("reichen keinen Schadensfall ein");
    }

    @Test
    @DisplayName("intake leaves out no Position - not even those without a Gebuehrennummer")
    void intakeSubmitsEveryPosition() {
        String prompt = Beratungsprompt.forMode(Beratungsmodus.RECHNUNG_EINREICHEN,
                "{\"tarif\":\"ATRA_DENT_B\"}", true);

        assertThat(prompt)
                .doesNotContain("lassen Sie weg")
                .doesNotContain("ausgelassene Positionen")
                .contains("ALLE Positionen")
                .contains("zahn, datum und anzahl");
    }

    @Test
    @DisplayName("the limits are shared: the Schadensfall sentence only in the Beratung, the rest in all "
            + "three roles")
    void limitsAreSharedBetweenBeratungAndIntake() {
        String portfolio = Beratungsprompt.forMode(Beratungsmodus.BESTANDSBERATUNG, null, false);
        String fresh = Beratungsprompt.forMode(Beratungsmodus.NEUBERATUNG, null, false);
        String assumption = Beratungsprompt.forMode(Beratungsmodus.RECHNUNG_EINREICHEN, null, false);

        assertThat(portfolio).contains("reichen keinen Schadensfall ein");
        assertThat(fresh).contains("reichen keinen Schadensfall ein");
        assertThat(assumption).doesNotContain("reichen keinen Schadensfall ein");

        for (String prompt : new String[] {portfolio, fresh, assumption}) {
            assertThat(prompt)
                    .contains("Sie sagen keine Erstattungshöhe zu")
                    .contains("Zahnmedizinische Sachfragen beantworten Sie nicht");
        }
    }

    @Test
    @DisplayName("a Beleg without a login: neuberatung asks for a login and submits nothing")
    void aBelegWithoutLogin() {
        String prompt = Beratungsprompt.forMode(Beratungsmodus.NEUBERATUNG, null, true);
        assertThat(prompt).contains("anmelden").doesNotContain("schadensfall_einreichen");
        assertThat(Beratungsprompt.forMode(Beratungsmodus.NEUBERATUNG, null, false))
                .doesNotContain("anmelden");
    }

    @Test
    @DisplayName("the Bestandsberatung answers status questions from the case card and names no internal "
            + "Eskalationsgruende")
    void statusRule() {
        assertThat(Beratungsprompt.forMode(Beratungsmodus.BESTANDSBERATUNG, "{}", false))
                .contains("meine_schadensfaelle_auflisten").contains("Eskalationsgr");
    }
}
