package consulting.atra.agenten.mcp;

@FunctionalInterface
public interface ToolPermission {

    boolean allowed(String toolName, String verbindung);
}
