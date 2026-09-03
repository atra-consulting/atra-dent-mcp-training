package consulting.atra.agenten.orchestrator.agent;

import consulting.atra.agenten.a2a.agent.TracePoint;
import org.springframework.ai.tool.annotation.Tool;

import java.util.Map;

public class HymneTool {

    public static final String TOOL = "hymne_abspielen";

    public static final String ART = "hymne";

    @Tool(name = TOOL, description = """
        Spielt der Kundin die atra.dent-Hymne vor. Nur aufrufen, wenn sie
        ausdruecklich nach der atra.dent-Hymne oder dem atra.dent-Lied
        fragt.""")
    public String playHymne() {
        TaskContext.Auftrag task = TaskContext.current();
        task.auskuenfte().collectView(Map.of("kind", ART));
        task.status().report(TracePoint.internal(Orchestrator.SENDER, TOOL,
                "lege die Hymne auf", "atra.dent-Hymne abspielen", Map.of()));
        return "Die Hymne laeuft bereits bei der Kundin. Sag EINEN kurzen,"
                + " freundlichen Satz dazu und gib den Liedtext nicht wieder.";
    }
}
