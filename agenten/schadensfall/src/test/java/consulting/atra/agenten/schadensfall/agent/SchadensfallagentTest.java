package consulting.atra.agenten.schadensfall.agent;

import consulting.atra.agenten.a2a.agent.Outcome;
import consulting.atra.agenten.a2a.agent.AgentResult;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.schadensfall.kernsystem.KernsystemClient;
import consulting.atra.agenten.schadensfall.check.BewertungResult;
import consulting.atra.agenten.schadensfall.check.Bewertungsvorschlag;
import consulting.atra.agenten.schadensfall.check.SchadensfallCheck;
import consulting.atra.agenten.schadensfall.check.ConflictException;
import consulting.atra.agenten.schadensfall.check.CheckResult;
import consulting.atra.agenten.schadensfall.check.Eskalationsgrund;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchadensfallagentTest {

    private static final ObjectMapper IMAGES = JsonMapper.builder().build();

    private KernsystemClient kernsystem;
    private SchadensfallCheck caseCheck;
    private Schadensfallagent agent;

    @BeforeEach
    void prepare() {
        kernsystem = mock(KernsystemClient.class);
        caseCheck = mock(SchadensfallCheck.class);
        agent = new Schadensfallagent(kernsystem, caseCheck, IMAGES);
        when(caseCheck.check(any(), any())).thenReturn(new CheckResult(
                new BewertungResult(Bewertungsvorschlag.ESKALATION, null,
                        List.of(new Eskalationsgrund(Eskalationsgrund.MODELL_WITHOUT_RESULT,
                                "Das Modell hat keine Bewertung abgegeben.")),
                        List.of(), null, "Kein Ergebnis."),
                List.of(), true, null));
    }

    @Test
    @DisplayName("without a Schadensfall number the agent asks back instead of guessing")
    void withoutANummerInputRequired() {
        AgentResult result = run("Hallo, koennen Sie mir helfen?", Map.of());

        assertThat(result.outcome()).isEqualTo(Outcome.INPUT_REQUIRED);
        assertThat(result.answerText()).contains("Fallnummer");
        verify(kernsystem, never()).fall(anyLong());
    }

    @Test
    @DisplayName("the Schadensfall number may appear in the text")
    void theNummerFromTheText() {
        submitted(50071);

        run("Bitte pruefe Fall 50071.", Map.of());

        verify(kernsystem).fall(50071);
    }

    @Test
    @DisplayName("a Datum in the sentence does not become a Schadensfall number")
    void aDatumIsNoSchadensfallNumber() {
        submitted(50071);

        run("Pruefe den Fall vom 8.1.2026, Nummer 50071.", Map.of());

        verify(kernsystem).fall(50071);
        verify(kernsystem, never()).fall(8);
    }

    @Test
    @DisplayName("without a five-digit number the input-required stands")
    void withoutAFiveDigitNumberInputRequired() {
        AgentResult result = run("Es geht um die Rechnung ueber 1200 Euro.", Map.of());

        assertThat(result.outcome()).isEqualTo(Outcome.INPUT_REQUIRED);
        verify(kernsystem, never()).fall(anyLong());
    }

    @Test
    @DisplayName("the data part takes precedence over the text")
    void theExplicitValueBeatsTheText() {
        submitted(50071);

        run("Der Fall vom 3. Maerz, 1200 Euro.",
                Map.of(Schadensfallagent.CASE_NUMBER_HINT, 50071));

        verify(kernsystem).fall(50071);
    }

    @Test
    @DisplayName("the agent refuses a Schadensfall that does not exist")
    void anUnknownSchadensfallIsRejected() {
        when(kernsystem.fall(99999)).thenReturn(Optional.empty());

        AgentResult result = run("Bitte pruefe Fall 99999.", Map.of());

        assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(result.answerText()).contains("99999");
        verify(kernsystem, never()).claim(anyLong());
    }

    @Test
    @DisplayName("an already paid-out Schadensfall is not checked -- and the status says so")
    void aDecidedSchadensfallIsRejected() {
        when(kernsystem.fall(50071)).thenReturn(Optional.of(fall(50071, "ausgezahlt")));

        AgentResult result = run("Bitte pruefe Fall 50071.", Map.of());

        assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(result.answerText()).contains("ausgezahlt").contains("eingereicht");
        verify(kernsystem, never()).claim(anyLong());
        verify(caseCheck, never()).check(any(), any());
    }

    @Test
    @DisplayName("a Schadensfall in someone else's hands is not checked a second time")
    void aLostClaimIsRejected() {
        when(kernsystem.fall(50071)).thenReturn(Optional.of(fall(50071, "eingereicht")));
        when(kernsystem.claim(50071)).thenReturn(false);

        AgentResult result = run("Bitte pruefe Fall 50071.", Map.of());

        assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(result.answerText()).contains("50071");
        verify(caseCheck, never()).check(any(), any());
    }

    @Test
    @DisplayName("after a lost claim the agent re-reads the Schadensfall and names the state")
    void aLostClaimNamesTheNewState() {
        when(kernsystem.fall(50071))
                .thenReturn(Optional.of(fall(50071, "eingereicht")))
                .thenReturn(Optional.of(fall(50071, "in_pruefung")));
        when(kernsystem.claim(50071)).thenReturn(false);

        AgentResult result = run("Bitte pruefe Fall 50071.", Map.of());

        assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(result.answerText()).contains("in_pruefung");
        verify(kernsystem, times(2)).fall(50071);
    }

    @Test
    @DisplayName("after a lost claim the agent names a final state too")
    void aLostClaimNamesAFinalState() {
        when(kernsystem.fall(50071))
                .thenReturn(Optional.of(fall(50071, "eingereicht")))
                .thenReturn(Optional.of(fall(50071, "genehmigt")));
        when(kernsystem.claim(50071)).thenReturn(false);

        AgentResult result = run("Bitte pruefe Fall 50071.", Map.of());

        assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(result.answerText()).contains("genehmigt").doesNotContain("in_pruefung");
        verify(caseCheck, never()).check(any(), any());
    }

    @Test
    @DisplayName("a Freigabe completes, with the artifact bewertung and the Betrag in the prose")
    void freigabeBecomesCompleted() {
        submitted(50071);
        when(caseCheck.check(any(), any())).thenReturn(new CheckResult(
                new BewertungResult(Bewertungsvorschlag.FREIGABE, new BigDecimal("336.00"),
                        List.of(), List.of(), null, "Beide Positionen sind gedeckt."),
                List.of(Map.of("schritt", "Vertrag gelesen")), true, null));

        AgentResult result = run("Bitte pruefe Fall 50071.", Map.of());

        assertThat(result.outcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(result.artifactName()).isEqualTo(Schadensfallagent.ARTIFACT);
        assertThat(result.answerText()).contains("50071").contains("336.00");
        assertThat(result.content()).containsKeys("bewertung", "protokoll", "schadensfallId");
        assertThat(result.content().get("schadensfallId")).isEqualTo(50071L);
        assertThat(result.content().get("protokoll")).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("the Bewertung goes out as a plain map, not as a Java object")
    void theBewertungIsAMap() {
        submitted(50071);
        when(caseCheck.check(any(), any())).thenReturn(new CheckResult(
                new BewertungResult(Bewertungsvorschlag.ESKALATION, null,
                        List.of(new Eskalationsgrund(Eskalationsgrund.GOZ_UNCLEAR,
                                "Position 2150 ist dem Grunde nach nicht geklärt.")),
                        List.of(), null, "Eine Position bleibt offen."),
                List.of(), true, null));

        AgentResult result = run("Bitte pruefe Fall 50071.", Map.of());

        assertThat(result.content().get("bewertung")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> bewertung = (Map<String, Object>) result.content().get("bewertung");
        assertThat(bewertung).containsEntry("empfehlung", "eskalation");
        assertThat(bewertung.get("gruende")).isInstanceOf(List.class);
        assertThat(result.answerText()).contains("Eskalation").contains("GOZ_UNCLEAR");
    }

    @Test
    @DisplayName("an unwritten Bewertung says so in the prose")
    void anUnwrittenBewertungWithAHinweis() {
        submitted(50071);
        when(caseCheck.check(any(), any())).thenReturn(new CheckResult(
                new BewertungResult(Bewertungsvorschlag.FREIGABE, new BigDecimal("336.00"),
                        List.of(), List.of(), null, "Alles gedeckt."),
                List.of(), false, "Das Kernsystem hat die Bewertung nicht angenommen."));

        AgentResult result = run("Bitte pruefe Fall 50071.", Map.of());

        assertThat(result.outcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(result.answerText()).contains("nicht angenommen");
    }

    @Test
    @DisplayName("a conflict during the check is a refusal, not a failure")
    void aConflictIsRejected() {
        submitted(50071);
        when(caseCheck.check(any(), any()))
                .thenThrow(new ConflictException("Eine Bewertung ist nur aus in_pruefung moeglich",
                        null));

        AgentResult result = run("Bitte pruefe Fall 50071.", Map.of());

        assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(result.answerText()).contains("50071");
    }

    @Test
    @DisplayName("a foreign Schadensfall number is refused -- and not silently checked")
    void aForeignSchadensfallNumberIsRejected() {
        submitted(50071);

        AgentResult result = run("Bitte pruefe Fall 50071.", Map.of(), 4712L);

        assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
        verify(kernsystem, never()).claim(anyLong());
        verify(caseCheck, never()).check(any(), any());
    }

    @Test
    @DisplayName("the refusal for a foreign Schadensfall reveals nothing about it")
    void aRefusalForAForeignSchadensfallRevealsNothing() {
        when(kernsystem.fall(50099)).thenReturn(Optional.of(IMAGES.readTree("""
                {"id":50099,"kundenId":4711,"status":"eingereicht",
                 "rechnungsbetrag":"1840.00",
                 "versicherter":{"name":"Miriam Kern","geburtsdatum":"1979-04-02"}}
                """)));
        when(kernsystem.claim(50099)).thenReturn(true);

        AgentResult result = run("Bitte pruefe Fall 50099.", Map.of(), 4712L);

        assertThat(result.answerText())
                .doesNotContain("4711")
                .doesNotContain("Kern")
                .doesNotContain("eingereicht")
                .doesNotContain("1840");
        assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(result.content()).isEmpty();
        assertThat(result.artifactName()).isNull();
    }

    @Test
    @DisplayName("to the caller a foreign Schadensfall looks like one that does not exist")
    void aForeignSchadensfallLooksLikeAnUnknownOne() {
        when(kernsystem.fall(50071)).thenReturn(Optional.of(fall(50071, "eingereicht")));
        when(kernsystem.fall(59999)).thenReturn(Optional.empty());

        String fremd = run("Bitte pruefe Fall 50071.", Map.of(), 4712L).answerText();
        String unknown = run("Bitte pruefe Fall 59999.", Map.of(), 4712L).answerText();

        assertThat(fremd.replace("50071", "N")).isEqualTo(unknown.replace("59999", "N"));
    }

    @Test
    @DisplayName("the caller's own Schadensfall gets through with the header")
    void theOwnSchadensfallNumberGetsThrough() {
        submitted(50071);

        AgentResult result = run("Bitte pruefe Fall 50071.", Map.of(), 4711L);

        assertThat(result.outcome()).isEqualTo(Outcome.COMPLETED);
        verify(kernsystem).claim(50071);
    }

    @Test
    @DisplayName("without the header the call ends as a refusal -- nothing is read, nothing claimed")
    void withoutTheHeaderItIsRejected() {
        submitted(50071);

        AgentResult result = run("Bitte pruefe Fall 50071.", Map.of(), null);

        assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
        verify(kernsystem, never()).fall(anyLong());
        verify(kernsystem, never()).claim(anyLong());
        verify(caseCheck, never()).check(any(), any());
        assertThat(result.content()).isEmpty();
    }

    @Test
    @DisplayName("the refusal without the header is verbatim the one for the foreign Schadensfall")
    void aRefusalWithoutTheHeaderIsTheOneForAForeignSchadensfall() {
        submitted(50071);

        String withoutHeader = run("Bitte pruefe Fall 50071.", Map.of(), null).answerText();
        String fremd = run("Bitte pruefe Fall 50071.", Map.of(), 4712L).answerText();

        assertThat(withoutHeader).isEqualTo(fremd);
    }


    private void submitted(long id) {
        when(kernsystem.fall(id)).thenReturn(Optional.of(fall(id, "eingereicht")));
        when(kernsystem.claim(id)).thenReturn(true);
    }

    private static JsonNode fall(long id, String status) {
        return IMAGES.readTree("{\"id\":" + id + ",\"kundenId\":4711,\"status\":\""
                + status + "\"}");
    }

    private AgentResult run(String text, Map<String, Object> data) {
        return run(text, data, 4711L);
    }

    private AgentResult run(String text, Map<String, Object> data, Long kundenId) {
        return agent.run("gespraech-1", text, data, kundenId, StatusChannel.discarded());
    }
}
