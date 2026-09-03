package consulting.atra.agenten.beratung.tools;

import consulting.atra.agenten.a2a.agent.Outcome;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Optional;

public class SignalTools {

    public static final String FOLLOW_UP = "rueckfrage_stellen";

    public static final String REJECT = "anliegen_ablehnen";

    public record Signal(Outcome art, String text) {
    }

    private Signal signal;

    @Tool(name = FOLLOW_UP, description = """
        Rufe dies, wenn dir eine Angabe fehlt, ohne die deine Antwort anders
        ausfiele. Der Zug endet damit als Rueckfrage; die Kundin bekommt
        deine Frage und antwortet darauf. Rufe es NICHT fuer etwas, das du
        nachschlagen kannst.""")
    public String askFollowUp(
            @ToolParam(description = "die Frage an die Kundin, woertlich") String frage) {
        return set(new Signal(Outcome.INPUT_REQUIRED, frage));
    }

    @Tool(name = REJECT, description = """
        Rufe dies, wenn du dieses Anliegen nicht bearbeitest - weil es nicht
        dein Fach ist oder weil du es nicht darfst. Sage in der Begruendung,
        was von beidem zutrifft, und sage es ueber DICH: Welcher andere
        Dienst zustaendig waere, weisst du nicht und behauptest es nicht.
        Rufe es nicht fuer eine Frage, die du beantworten kannst, auch wenn
        die Antwort einschraenkend ausfaellt.""")
    public String rejectAnliegen(
            @ToolParam(description = "warum du es nicht bearbeitest") String begruendung) {
        return set(new Signal(Outcome.REJECTED, begruendung));
    }

    public synchronized Optional<Signal> called() {
        return Optional.ofNullable(signal);
    }

    public List<ToolCallback> callbacks() {
        return List.of(ToolCallbacks.from(this));
    }

    private synchronized String set(Signal neues) {
        if (signal == null) {
            signal = neues;
        }
        return "Vermerkt. Der Zug endet damit; schreibe keine weitere Antwort.";
    }
}
