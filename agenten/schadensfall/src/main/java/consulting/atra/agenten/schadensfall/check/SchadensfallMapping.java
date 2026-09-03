package consulting.atra.agenten.schadensfall.check;

import consulting.atra.agenten.mcp.ToolMapping;

public final class SchadensfallMapping {

    public static final ToolMapping ALLE = ToolSelection::serverOf;

    private SchadensfallMapping() {
    }
}
