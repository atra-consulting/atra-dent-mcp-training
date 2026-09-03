package consulting.atra.agenten.mcp;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public interface ToolSource {

    List<ToolCallback> forPermission(ToolPermission allowed);
}
