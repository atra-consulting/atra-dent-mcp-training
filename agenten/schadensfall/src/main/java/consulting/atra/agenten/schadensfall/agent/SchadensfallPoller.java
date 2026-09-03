package consulting.atra.agenten.schadensfall.agent;

import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.a2a.tracelog.Tracelog;
import consulting.atra.agenten.a2a.tracelog.TracelogChannel;
import consulting.atra.agenten.schadensfall.kernsystem.KernsystemClient;
import consulting.atra.agenten.schadensfall.check.SchadensfallCheck;
import consulting.atra.agenten.schadensfall.check.ConflictException;
import consulting.atra.agenten.schadensfall.check.CheckResult;
import consulting.atra.agenten.schadensfall.check.SchadensfallProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "schadensfall.poll-enabled", havingValue = "true",
        matchIfMissing = true)
public class SchadensfallPoller {

    public static final String EINGEREICHT = "eingereicht";

    public static final String IN_PRUEFUNG = "in_pruefung";

    static final String TAKEOVER = "-> " + IN_PRUEFUNG;

    private static final String PROTOKOLL = "bearbeitungsprotokoll";

    private static final Logger log = LoggerFactory.getLogger(SchadensfallPoller.class);

    private final KernsystemClient kernsystem;
    private final SchadensfallCheck caseCheck;
    private final SchadensfallProperties properties;
    private final Tracelog tracelog;
    private final Clock clock;

    public SchadensfallPoller(KernsystemClient kernsystem, SchadensfallCheck fallpruefung,
                      SchadensfallProperties properties, Tracelog tracelog, Clock clock) {
        this.kernsystem = Objects.requireNonNull(kernsystem, "kernsystem");
        this.caseCheck = Objects.requireNonNull(fallpruefung, "fallpruefung");
        this.properties = Objects.requireNonNull(properties, "einstellungen");
        this.tracelog = Objects.requireNonNull(tracelog, "tracelog");
        this.clock = Objects.requireNonNull(clock, "uhr");
    }

    @Scheduled(fixedDelayString = "${schadensfall.poll-interval}")
    public void run() {
        reconcile();
        submittedCases();
    }


    private void reconcile() {
        List<JsonNode> faelle;
        try {
            faelle = kernsystem.casesWithStatus(IN_PRUEFUNG);
        } catch (RuntimeException failure) {
            log.warn("Reconcile uebersprungen: Das Kernsystem liefert die Faelle in {} nicht ({})",
                    IN_PRUEFUNG, failure.getMessage());
            return;
        }
        Instant limit = Instant.now(clock).minus(properties.reconcileTimeout());
        for (JsonNode fall : faelle) {
            try {
                rollBackWhenExpired(fall, limit);
            } catch (RuntimeException failure) {
                log.warn("Fall {} liess sich nicht zuruecksetzen", label(fall), failure);
            }
        }
    }

    private void rollBackWhenExpired(JsonNode fall, Instant grenze) {
        Optional<Instant> seit = adoptedSince(fall);
        if (seit.isEmpty()) {
            log.debug("Fall {} steht auf {}, hat aber keinen Uebernahmeeintrag - bleibt stehen",
                    label(fall), IN_PRUEFUNG);
            return;
        }
        if (seit.get().isAfter(grenze)) {
            return;
        }
        long id = fallId(fall);
        log.info("Fall {} ist seit {} in {} - die Pruefung gilt als steckengeblieben",
                id, seit.get(), IN_PRUEFUNG);
        kernsystem.reset(id);
    }

    private static Optional<Instant> adoptedSince(JsonNode fall) {
        JsonNode protokoll = fall.get(PROTOKOLL);
        if (protokoll == null || !protokoll.isArray()) {
            return Optional.empty();
        }
        JsonNode last = null;
        for (JsonNode entry : protokoll) {
            JsonNode step = entry.get("schritt");
            if (step == null || !step.isString() || !step.asString().endsWith(TAKEOVER)) {
                continue;
            }
            last = entry;
        }
        return last == null
                ? Optional.empty()
                : Optional.ofNullable(timestamp(last.get("zeitpunkt")));
    }

    private static Instant timestamp(JsonNode value) {
        if (value == null || !value.isString()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.asString()).toInstant();
        } catch (DateTimeParseException _) {
            log.warn("Unlesbarer Zeitpunkt im {}: {}", PROTOKOLL, value.asString());
            return null;
        }
    }


    private void submittedCases() {
        List<JsonNode> faelle;
        try {
            faelle = kernsystem.casesWithStatus(EINGEREICHT);
        } catch (RuntimeException failure) {
            log.warn("Kein Takt: Das Kernsystem liefert die Faelle in {} nicht ({})",
                    EINGEREICHT, failure.getMessage());
            return;
        }
        if (faelle.isEmpty()) {
            log.debug("Nichts zu tun - kein Fall steht auf {}", EINGEREICHT);
            return;
        }
        log.info("{} Fall/Faelle in {} gefunden", faelle.size(), EINGEREICHT);
        for (JsonNode fall : faelle) {
            claimAndCheck(fall);
        }
    }

    private void claimAndCheck(JsonNode fall) {
        String label = label(fall);
        long id;
        try {
            id = fallId(fall);
            if (!kernsystem.claim(id)) {
                log.debug("Fall {} uebersprungen - ein anderer hat ihn uebernommen", id);
                return;
            }
        } catch (RuntimeException failure) {
            log.error("Fall {} liess sich nicht uebernehmen - er steht weiter auf {} und kommt "
                    + "beim naechsten Takt wieder", label, EINGEREICHT, failure);
            return;
        }

        try {
            CheckResult result = caseCheck.check(fall, channel(id));
            log.info("Fall {} geprueft: {}{}", id, result.bewertung().empfehlung(),
                    result.geschrieben() ? "" : " (nicht geschrieben: " + result.hinweis() + ")");

        } catch (ConflictException conflict) {
            log.info("Fall {} liegen gelassen: {}", id, conflict.getMessage());

        } catch (RuntimeException failure) {
            log.error("Fall {} konnte nicht geprueft werden - er bleibt in {}",
                    id, IN_PRUEFUNG, failure);
        }
    }

    private StatusChannel channel(long id) {
        return TracelogChannel.wrap(StatusChannel.discarded(), tracelog, "poll-" + id);
    }

    private static long fallId(JsonNode fall) {
        JsonNode id = fall.get("id");
        if (id == null || !id.isNumber()) {
            throw new IllegalArgumentException("Ein Fall ohne auswertbares Feld 'id' - so laesst "
                    + "sich weder uebernehmen noch pruefen");
        }
        return id.asLong();
    }

    private static String label(JsonNode fall) {
        JsonNode id = fall == null ? null : fall.get("id");
        return id == null || !id.isNumber() ? "?" : String.valueOf(id.asLong());
    }
}
