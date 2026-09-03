package consulting.atra.agenten.schadensfall.agent;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.a2a.tracelog.Tracelog;
import consulting.atra.agenten.schadensfall.kernsystem.KernsystemClient;
import consulting.atra.agenten.schadensfall.check.BewertungResult;
import consulting.atra.agenten.schadensfall.check.SchadensfallCheck;
import consulting.atra.agenten.schadensfall.check.ConflictException;
import consulting.atra.agenten.schadensfall.check.CheckResult;
import consulting.atra.agenten.schadensfall.check.SchadensfallProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchadensfallPollerTest {

    private static final ObjectMapper IMAGES = JsonMapper.builder().build();

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");

    private static final Duration FRIST = Duration.ofMinutes(5);

    private KernsystemClient kernsystem;
    private SchadensfallCheck caseCheck;
    private SchadensfallPoller poller;
    private ListAppender<ILoggingEvent> recording;

    @AfterEach
    void hangUp() {
        pollerLogger().detachAppender(recording);
    }

    @BeforeEach
    void prepare() {
        recording = new ListAppender<>();
        recording.start();
        pollerLogger().addAppender(recording);
        kernsystem = mock(KernsystemClient.class);
        caseCheck = mock(SchadensfallCheck.class);
        poller = new SchadensfallPoller(kernsystem, caseCheck,
                new SchadensfallProperties(Duration.ofSeconds(10), true, FRIST,
                        new BigDecimal("1500.00")),
                Tracelog.OFF, Clock.fixed(NOW, ZoneOffset.UTC));
        when(kernsystem.casesWithStatus("in_pruefung")).thenReturn(List.of());
        when(kernsystem.casesWithStatus("eingereicht")).thenReturn(List.of());
    }

    @Test
    @DisplayName("whoever does not get the Schadensfall does not check it -- the second claim is lost")
    void aLostClaimSkipsTheSchadensfall() {
        when(kernsystem.casesWithStatus("eingereicht"))
                .thenReturn(List.of(submitted(50071), submitted(50014)));
        when(kernsystem.claim(50071)).thenReturn(true);
        when(kernsystem.claim(50014)).thenReturn(false);

        poller.run();

        verify(caseCheck).check(caseWithId(50071), any());
        verify(caseCheck, never()).check(caseWithId(50014), any());
    }

    @Test
    @DisplayName("reconcile reclaims an old claim, not a fresh one")
    void reconcileOnlyReclaimsOldClaims() {
        when(kernsystem.casesWithStatus("in_pruefung")).thenReturn(List.of(
                inPruefungSeit(50001, NOW.minus(Duration.ofHours(1))),
                inPruefungSeit(50002, NOW.minus(Duration.ofSeconds(30)))));

        poller.run();

        verify(kernsystem).reset(50001);
        verify(kernsystem, never()).reset(50002);
    }

    @Test
    @DisplayName("without a claim entry nothing is reset -- nothing is guessed")
    void withoutAClaimEntryTheSchadensfallStays() {
        when(kernsystem.casesWithStatus("in_pruefung"))
                .thenReturn(List.of(IMAGES.readTree(
                        "{\"id\":50003,\"status\":\"in_pruefung\",\"bearbeitungsprotokoll\":[]}")));

        poller.run();

        verify(kernsystem, never()).reset(anyLong());
    }

    @Test
    @DisplayName("clean up first, then fetch -- otherwise the run misses its own leftovers")
    void reconcileRunsBeforeFetching() {
        poller.run();

        InOrder order = inOrder(kernsystem);
        order.verify(kernsystem).casesWithStatus("in_pruefung");
        order.verify(kernsystem).casesWithStatus("eingereicht");
    }

    @Test
    @DisplayName("a failure on one Schadensfall does not stop the run")
    void aFailureDoesNotStopTheRun() {
        when(kernsystem.casesWithStatus("eingereicht"))
                .thenReturn(List.of(submitted(50071), submitted(50014)));
        when(kernsystem.claim(anyLong())).thenReturn(true);
        when(caseCheck.check(caseWithId(50071), any()))
                .thenThrow(new IllegalStateException("Kernsystem antwortet nicht"));

        poller.run();

        verify(caseCheck).check(caseWithId(50014), any());
    }

    @Test
    @DisplayName("a conflict leaves the Schadensfall alone and does not end the run")
    void aConflictLeavesTheSchadensfallAlone() {
        when(kernsystem.casesWithStatus("eingereicht"))
                .thenReturn(List.of(submitted(50071), submitted(50014)));
        when(kernsystem.claim(anyLong())).thenReturn(true);
        when(caseCheck.check(caseWithId(50071), any()))
                .thenThrow(new ConflictException("so nicht mehr", null));

        poller.run();

        verify(caseCheck).check(caseWithId(50014), any());
        verify(kernsystem, never()).reset(anyLong());
    }

    @Test
    @DisplayName("a Kernsystem that does not answer at all does not stop the scheduler")
    void aKernsystemOutageDoesNotStopThePoller() {
        when(kernsystem.casesWithStatus("in_pruefung"))
                .thenThrow(new IllegalStateException("Verbindung abgelehnt"));

        poller.run();

        verify(kernsystem).casesWithStatus("eingereicht");
    }

    @Test
    @DisplayName("the check run gets its own channel per Schadensfall")
    void everySchadensfallHasItsOwnChannel() {
        when(kernsystem.casesWithStatus("eingereicht")).thenReturn(List.of(submitted(50071)));
        when(kernsystem.claim(50071)).thenReturn(true);
        when(caseCheck.check(any(), any())).thenReturn(
                new CheckResult(new BewertungResult("eskalation", null, List.of(), List.of(),
                        null, "keine Bewertung"), List.of(), true, null));

        poller.run();

        verify(caseCheck).check(caseWithId(50071), any(StatusChannel.class));
    }

    @Test
    @DisplayName("an unreadable timestamp on the latest entry leaves the Schadensfall standing")
    void anUnreadableLatestEntryLeavesTheSchadensfallStanding() {
        when(kernsystem.casesWithStatus("in_pruefung")).thenReturn(List.of(IMAGES.readTree("""
                {"id":50004,"kundenId":4711,"status":"in_pruefung","bearbeitungsprotokoll":[
                  {"zeitpunkt":"2026-08-18T09:00:00Z","akteur":"agent",
                   "schritt":"Status eingereicht -> in_pruefung"},
                  {"zeitpunkt":"2026-08-18T10:00:00Z","akteur":"sachbearbeitung",
                   "schritt":"Status in_pruefung -> eingereicht"},
                  {"zeitpunkt":"vorhin","akteur":"agent",
                   "schritt":"Status eingereicht -> in_pruefung"}]}
                """)));

        poller.run();

        verify(kernsystem, never()).reset(anyLong());
    }

    @Test
    @DisplayName("a failed claim sends nobody into the reconcile run")
    void aFailedClaimNamesTheCorrectState() {
        when(kernsystem.casesWithStatus("eingereicht")).thenReturn(List.of(submitted(50071)));
        when(kernsystem.claim(50071))
                .thenThrow(new IllegalStateException("Kernsystem antwortet nicht"));

        poller.run();

        String message = recording.list.stream()
                .filter(entry -> entry.getLevel().toInt() >= ch.qos.logback.classic.Level.WARN
                        .toInt())
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (eins, zwei) -> eins + " | " + zwei);
        assertThat(message).contains("50071").contains("eingereicht")
                .doesNotContain("in_pruefung");
    }


    private static Logger pollerLogger() {
        return (Logger) LoggerFactory.getLogger(SchadensfallPoller.class);
    }

    private static JsonNode submitted(long id) {
        return IMAGES.readTree("{\"id\":" + id + ",\"kundenId\":4711,\"status\":\"eingereicht\"}");
    }

    private static JsonNode inPruefungSeit(long id, Instant zeitpunkt) {
        return IMAGES.readTree("""
                {"id":%d,"kundenId":4711,"status":"in_pruefung","bearbeitungsprotokoll":[
                  {"zeitpunkt":"2026-08-01T08:00:00Z","akteur":"kunde",
                   "schritt":"Schadensfall eingereicht"},
                  {"zeitpunkt":"%s","akteur":"agent",
                   "schritt":"Status eingereicht -> in_pruefung"}]}
                """.formatted(id, zeitpunkt));
    }

    private static JsonNode caseWithId(long id) {
        return argThat(fall -> fall != null && fall.get("id").asLong() == id);
    }
}
