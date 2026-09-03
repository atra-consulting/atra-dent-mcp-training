package consulting.atra.agenten.schadensfall.agent;

import consulting.atra.agenten.a2a.agent.AgentResult;
import consulting.atra.agenten.a2a.agent.Agent;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.schadensfall.kernsystem.KernsystemClient;
import consulting.atra.agenten.schadensfall.check.BewertungResult;
import consulting.atra.agenten.schadensfall.check.SchadensfallCheck;
import consulting.atra.agenten.schadensfall.check.ConflictException;
import consulting.atra.agenten.schadensfall.check.CheckResult;
import consulting.atra.agenten.schadensfall.check.Eskalationsgrund;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class Schadensfallagent implements Agent {

    public static final String SENDER = "schadensfall";

    public static final String ARTIFACT = "bewertung";

    public static final String CASE_NUMBER_HINT = "schadensfallId";

    private static final Logger log = LoggerFactory.getLogger(Schadensfallagent.class);

    private static final String FOLLOW_UP =
            "Welchen Fall soll ich prüfen? Bitte die Fallnummer nennen.";

    private static final Pattern NUMBER = Pattern.compile("(?<!\\d)\\d{5}(?!\\d)");

    private final KernsystemClient kernsystem;
    private final SchadensfallCheck caseCheck;
    private final ObjectMapper mapper;

    public Schadensfallagent(KernsystemClient kernsystem, SchadensfallCheck fallpruefung,
                             ObjectMapper mapper) {
        this.kernsystem = Objects.requireNonNull(kernsystem, "kernsystem");
        this.caseCheck = Objects.requireNonNull(fallpruefung, "fallpruefung");
        this.mapper = Objects.requireNonNull(mapper, "abbilder");
    }

    @Override
    public AgentResult run(String contextId, String text, Map<String, Object> data,
                                    Long kundenId, StatusChannel status) {
        Optional<Long> caseNumber = caseNumber(text, data);
        if (caseNumber.isEmpty()) {
            return AgentResult.inputRequired(FOLLOW_UP);
        }
        long id = caseNumber.get();

        if (kundenId == null) {
            return AgentResult.rejected(noInfo(id));
        }

        Optional<JsonNode> record = kernsystem.fall(id);
        if (record.isEmpty() || fremd(record.get(), kundenId)) {
            return AgentResult.rejected(noInfo(id));
        }
        JsonNode fall = record.get();

        String caseStatus = status(fall);
        if (!SchadensfallPoller.EINGEREICHT.equals(caseStatus)) {
            return AgentResult.rejected("Fall " + id + " steht auf " + caseStatus
                    + "; geprüft werden nur eingereichte Fälle. Zum erneuten Prüfen setzt die "
                    + "Sachbearbeitung ihn auf eingereicht.");
        }
        if (!kernsystem.claim(id)) {
            return AgentResult.rejected(leftLying(id));
        }

        try {
            CheckResult result = caseCheck.check(fall, status);
            return AgentResult.completed(ARTIFACT, summary(id, result),
                    content(id, result));
        } catch (ConflictException conflict) {
            log.info("Fall {} wurde waehrend der Pruefung weitergezogen", id, conflict);
            return AgentResult.rejected("Fall " + id + " wurde während der Prüfung "
                    + "weiterbearbeitet; die Bewertung gilt nicht mehr. Zum erneuten Prüfen "
                    + "setzt die Sachbearbeitung ihn auf eingereicht.");
        }
    }


    private static String noInfo(long id) {
        return "Zu Fall " + id + " kann ich Ihnen nichts sagen.";
    }

    private static boolean fremd(JsonNode fall, long kundenId) {
        JsonNode owner = fall.get("kundenId");
        return owner == null || !owner.isNumber()
                || owner.asLong() != kundenId;
    }

    private String leftLying(long id) {
        String now = kernsystem.fall(id).map(Schadensfallagent::status).orElse(null);
        if (now == null) {
            return "Fall " + id + " ließ sich nicht übernehmen; ein anderer war schneller.";
        }
        return "Fall " + id + " ließ sich nicht übernehmen; er steht jetzt auf " + now
                + ". Ein anderer war schneller.";
    }

    private Map<String, Object> content(long id, CheckResult result) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("bewertung", mapper.convertValue(result.bewertung(),
                new TypeReference<Map<String, Object>>() { }));
        content.put("protokoll", result.protokoll());
        content.put("schadensfallId", id);
        return content;
    }

    private static String summary(long id, CheckResult result) {
        BewertungResult bewertung = result.bewertung();
        StringBuilder sentence = new StringBuilder("Fall ").append(id).append(": ");
        if (bewertung.freigabe()) {
            sentence.append("Freigabe empfohlen");
            BigDecimal proposal = bewertung.erstattungsvorschlag();
            sentence.append(proposal == null ? "." : ", Vorschlag " + proposal.toPlainString()
                    + " EUR.");
        } else {
            sentence.append("Eskalation an die Sachbearbeitung — Gründe: ")
                    .append(String.join(", ", bewertung.codes())).append(".");
            for (Eskalationsgrund grund : bewertung.gruende()) {
                sentence.append(' ').append(grund.text());
            }
        }
        if (!result.geschrieben()) {
            sentence.append(" Achtung: Die Bewertung steht nicht in der Akte — ")
                    .append(result.hinweis());
        }
        return sentence.toString();
    }

    private static String status(JsonNode fall) {
        JsonNode status = fall.get("status");
        return status == null || !status.isString() ? "unbekannt" : status.asString();
    }

    private static Optional<Long> caseNumber(String text, Map<String, Object> data) {
        Object entry = data == null ? null : data.get(CASE_NUMBER_HINT);
        if (entry instanceof Number zahl) {
            return Optional.of(zahl.longValue());
        }
        if (entry instanceof String wort) {
            Optional<Long> fromEntry = firstNumber(wort);
            if (fromEntry.isPresent()) {
                return fromEntry;
            }
        }
        return firstNumber(text);
    }

    private static Optional<Long> firstNumber(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher hits = NUMBER.matcher(text);
        return hits.find() ? Optional.of(Long.parseLong(hits.group())) : Optional.empty();
    }
}
