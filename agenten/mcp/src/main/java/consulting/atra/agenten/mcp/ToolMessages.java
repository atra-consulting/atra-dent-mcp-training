package consulting.atra.agenten.mcp;

@FunctionalInterface
public interface ToolMessages {

    ObservedTools.Meldung forTool(String toolName);
}
