package consulting.atra.agenten.a2a.client;

import consulting.atra.agenten.a2a.agent.Outcome;
import io.a2a.spec.Artifact;
import io.a2a.spec.DataPart;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TextPart;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record SubagentResponse(Outcome outcome, String text, String contextId, String taskId,
                               Map<String, Object> data, String artifactName) {

    public SubagentResponse {
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public SubagentResponse(Outcome outcome, String text, String contextId, String taskId) {
        this(outcome, text, contextId, taskId, Map.of(), null);
    }

    public SubagentResponse(Outcome outcome, String text, String contextId, String taskId,
                            Map<String, Object> data) {
        this(outcome, text, contextId, taskId, data, null);
    }

    public static SubagentResponse of(Task task, String agentName) {
        TaskState state = task.getStatus().state();
        return switch (state) {
            case COMPLETED -> new SubagentResponse(Outcome.COMPLETED, artifactText(task),
                    task.getContextId(), task.getId(), artifactData(task), artifactName(task));
            case INPUT_REQUIRED -> new SubagentResponse(Outcome.INPUT_REQUIRED,
                    text(task.getStatus().message()), task.getContextId(), task.getId());
            case REJECTED -> new SubagentResponse(Outcome.REJECTED,
                    text(task.getStatus().message()), task.getContextId(), task.getId());
            case FAILED -> {
                String reason = text(task.getStatus().message());
                throw new AgentUnreachableException(agentName, reason.isBlank()
                        ? "der Task endete im Zustand failed" : reason);
            }
            default -> throw new AgentUnreachableException(agentName,
                    "der Task endete im Zustand " + state.asString()
                            + " statt mit einer verwertbaren Antwort");
        };
    }

    static String text(Message message) {
        if (message == null || message.getParts() == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Part<?> part : message.getParts()) {
            if (part instanceof TextPart textteil) {
                text.append(textteil.getText());
            }
        }
        return text.toString();
    }

    private static String artifactText(Task task) {
        if (task.getArtifacts() == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Artifact artifact : task.getArtifacts()) {
            if (artifact.parts() == null) {
                continue;
            }
            for (Part<?> part : artifact.parts()) {
                if (part instanceof TextPart textteil) {
                    text.append(textteil.getText());
                }
            }
        }
        return text.toString();
    }

    private static String artifactName(Task task) {
        if (task.getArtifacts() == null) {
            return null;
        }
        for (Artifact artifact : task.getArtifacts()) {
            if (artifact.name() != null && !artifact.name().isBlank()) {
                return artifact.name();
            }
        }
        return null;
    }

    private static Map<String, Object> artifactData(Task task) {
        if (task.getArtifacts() == null) {
            return Map.of();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        for (Artifact artifact : task.getArtifacts()) {
            if (artifact.parts() == null) {
                continue;
            }
            for (Part<?> part : artifact.parts()) {
                if (part instanceof DataPart dataPart && dataPart.getData() != null) {
                    data.putAll(dataPart.getData());
                }
            }
        }
        return data;
    }
}
