package consulting.atra.agenten.beratung.beleg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public class BelegFidelity {

    public static final String EINREICHEN = "schadensfall_einreichen";

    public static final String TOTAL_AMOUNT = "gesamtbetrag";

    private static final String RECHNUNG = "rechnung";

    private static final String MUSTER = "[0-9]{1,10}(\\.[0-9]{1,2})?";

    private static final Logger log = LoggerFactory.getLogger(BelegFidelity.class);

    private final ObjectMapper mapper;

    public BelegFidelity(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "abbilder");
    }

    public static String totalAmount(Object beleg) {
        if (!(beleg instanceof Map<?, ?> fields)) {
            log.warn("Der Beleg ist kein Objekt; der Gesamtbetrag wird nicht uebernommen");
            return null;
        }
        Object value = fields.get(TOTAL_AMOUNT);
        String amount = switch (value) {
            case String text -> text.trim();
            case Number zahl -> new BigDecimal(zahl.toString()).toPlainString();
            case null, default -> null;
        };
        if (amount != null && amount.matches(MUSTER)) {
            return amount;
        }
        log.warn("Der Beleg nennt keinen verwertbaren Gesamtbetrag ({}); es wird nicht "
                + "uebernommen", value);
        return null;
    }

    public List<ToolCallback> wrap(List<ToolCallback> tools, String gesamtbetrag,
                                        BiConsumer<String, String> gemeldet) {
        if (gesamtbetrag == null) {
            return tools;
        }
        return tools.stream()
                .map(delegate -> EINREICHEN.equals(delegate.getToolDefinition().name())
                        ? (ToolCallback) new Wrapper(delegate, this, gesamtbetrag, gemeldet)
                        : delegate)
                .toList();
    }

    private String record(String input, String gesamtbetrag,
                             BiConsumer<String, String> gemeldet) {
        Korrektur korrektur;
        try {
            korrektur = correct(input, gesamtbetrag);
        } catch (RuntimeException unreadable) {
            log.warn("Argumente von {} nicht lesbar; der Gesamtbetrag bleibt, wie das Modell "
                    + "ihn geschrieben hat", EINREICHEN, unreadable);
            return input;
        }
        if (korrektur == null) {
            return input;
        }
        gemeldet.accept(korrektur.vomModell(), gesamtbetrag);
        return korrektur.arguments();
    }

    private Korrektur correct(String input, String gesamtbetrag) {
        if (!(mapper.readTree(input) instanceof ObjectNode arguments)) {
            return null;
        }
        ObjectNode rechnung = arguments.get(RECHNUNG) instanceof ObjectNode vorhanden
                ? vorhanden
                : arguments.putObject(RECHNUNG);
        JsonNode alt = rechnung.get(TOTAL_AMOUNT);
        String fromModell = alt == null || alt.isNull() ? null : alt.asString();
        if (equal(fromModell, gesamtbetrag)) {
            return null;
        }
        rechnung.put(TOTAL_AMOUNT, gesamtbetrag);
        return new Korrektur(arguments.toString(), fromModell);
    }

    private record Korrektur(String arguments, String vomModell) {
    }

    private static boolean equal(String vomModell, String ausDemBeleg) {
        if (vomModell == null) {
            return false;
        }
        try {
            return new BigDecimal(vomModell.trim()).compareTo(new BigDecimal(ausDemBeleg)) == 0;
        } catch (NumberFormatException keineZahl) {
            return false;
        }
    }

    private record Wrapper(ToolCallback delegate, BelegFidelity belegtreue, String gesamtbetrag,
                          BiConsumer<String, String> gemeldet) implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public String call(String input) {
            return call(input, null);
        }

        @Override
        public String call(String input, ToolContext context) {
            String corrected = belegtreue.record(input, gesamtbetrag, gemeldet);
            return context == null ? delegate.call(corrected) : delegate.call(corrected, context);
        }
    }
}
