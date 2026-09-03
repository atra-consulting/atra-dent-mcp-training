package consulting.atra.agenten.orchestrator.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorpromptTest {

    @Test
    @DisplayName("the Orchestrator speaks as atra.denta, as a single agent")
    void namesThePersona() {
        assertThat(Orchestratorprompt.TEMPLATE)
                .contains("atra.denta")
                .contains("EIN einzelner Agent");
    }

    @Test
    @DisplayName("nothing is collected before an agent has been asked")
    void forwardsBeforeItAsksTheKundin() {
        assertThat(Orchestratorprompt.TEMPLATE)
                .contains("REICHE SOFORT WEITER, AUCH WENN ANGABEN FEHLEN")
                .contains("sammelst nie")
                .contains("keine Checkliste fuer dich");
    }

    @Test
    @DisplayName("no address of the advisory firm in the prompt")
    void noAddressOfTheAdvisoryFirm() {
        assertThat(Orchestratorprompt.TEMPLATE)
                .doesNotContain("atra.consulting")
                .contains("service@atra.dent");
    }

    @Test
    @DisplayName("the refusal rule for Leistungsfragen is gone")
    void noRefusalRuleForLeistungsfragen() {
        assertThat(Orchestratorprompt.TEMPLATE)
                .doesNotContain("Erstattung im Einzelfall")
                .doesNotContain("NICHT an die Beratung");
    }

    @Test
    @DisplayName("the text points at a view instead of retelling it")
    void pointsAtTheView() {
        assertThat(Orchestratorprompt.TEMPLATE)
                .contains("Anzeige")
                .contains("EINEM Satz")
                .contains("zaehle die Zahlen nicht auf");
        assertThat(Orchestratorprompt.TEMPLATE).contains("Den Hinweis selbst gibst du nie wieder");
    }

    @Test
    @DisplayName("the Hymne plays only on an explicit request and is never offered")
    void theHymneStaysAnEasterEgg() {
        assertThat(Orchestratorprompt.TEMPLATE)
                .contains(HymneTool.TOOL)
                .contains("atra.dent-Hymne")
                .contains("Erwaehne sie nie von dir aus");
    }

    @Test
    @DisplayName("a Rueckfrage that stands in the Akte is answered, not relayed")
    void aRueckfrageFromTheAkteIsAnsweredByTheOrchestrator() {
        assertThat(Orchestratorprompt.TEMPLATE)
                .contains("=== RUECKFRAGEN EINES AGENTEN ===")
                .contains(KundendatenTool.TOOL)
                .contains("DENSELBEN Agenten")
                .contains("bekommt diese Frage gar nicht zu sehen");
    }

    @Test
    @DisplayName("the block names no agent and no subject of its own")
    void theBlockStaysGeneric() {
        String block = Orchestratorprompt.TEMPLATE
                .substring(Orchestratorprompt.TEMPLATE.indexOf("=== RUECKFRAGEN EINES AGENTEN ==="));

        assertThat(block)
                .doesNotContain("Arzt")
                .doesNotContain("Termin")
                .doesNotContain("Beratungsagent")
                .doesNotContain("Schadensfallagent");
    }

    @Test
    @DisplayName("the Akte is asked before the Kundin is, not the other way round")
    void theAkteComesBeforeTheKundin() {
        assertThat(Orchestratorprompt.TEMPLATE)
                .contains("beantworte sie ZUERST selbst")
                .contains("Nur sonst stellst du sie der Kundin woertlich");
    }

    @Test
    @DisplayName("an address, a telephone number and a Kundennummer are never passed on")
    void theContactDataNeverLeaves() {
        assertThat(Orchestratorprompt.TEMPLATE)
                .contains("Eine Anschrift, eine Telefonnummer")
                .contains("Kundennummer gibst du nie weiter");
    }

    @Test
    @DisplayName("without a login the Orchestrator asks the customer after all")
    void withoutALoginTheCustomerIsAsked() {
        assertThat(Orchestratorprompt.TEMPLATE)
                .contains("Ist niemand angemeldet, kannst du nichts nachschlagen");
    }

    @Test
    @DisplayName("what only the customer knows stays a question for her")
    void whatOnlyTheCustomerKnowsStaysHers() {
        assertThat(Orchestratorprompt.TEMPLATE)
                .contains("was sie will, wann sie")
                .contains("Das beantwortest du nie selbst");
    }

    @Test
    @DisplayName("a refusal may trigger a second attempt, but need not")
    void aRefusalAllowsASecondAttempt() {
        assertThat(Orchestratorprompt.TEMPLATE)
                .contains("[Absage")
                .contains("DARFST");
    }
}
