package consulting.atra.agenten.a2a.client;

import consulting.atra.agenten.a2a.agent.Outcome;
import io.a2a.spec.Artifact;
import io.a2a.spec.DataPart;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubagentResponseTest {

    @Test
    void completedYieldsCompletedWithConcatenatedArtifactText() {
        Task task = new Task.Builder()
                .id("aufgabe-1")
                .contextId("kontext-1")
                .status(new TaskStatus(TaskState.COMPLETED))
                .artifacts(List.of(
                        new Artifact.Builder()
                                .artifactId("artefakt-1")
                                .name("beratung")
                                .parts(List.<Part<?>>of(new TextPart("Teil eins. "), new TextPart("Teil zwei.")))
                                .build(),
                        new Artifact.Builder()
                                .artifactId("artefakt-2")
                                .name("beleg")
                                .parts(List.<Part<?>>of(new TextPart(" Und noch mehr.")))
                                .build()))
                .build();

        SubagentResponse response = SubagentResponse.of(task, "Beratungsagent");

        assertThat(response.outcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(response.text()).isEqualTo("Teil eins. Teil zwei. Und noch mehr.");
        assertThat(response.contextId()).isEqualTo("kontext-1");
        assertThat(response.taskId()).isEqualTo("aufgabe-1");
    }

    @Test
    void completedTakesTheDataPartOfTheArtifact() {
        Task task = new Task.Builder()
                .id("aufgabe-4")
                .contextId("kontext-4")
                .status(new TaskStatus(TaskState.COMPLETED))
                .artifacts(List.of(new Artifact.Builder()
                        .artifactId("artefakt-4")
                        .name("beratung")
                        .parts(List.<Part<?>>of(
                                new TextPart("Implantate sind mitversichert."),
                                new DataPart(Map.of(
                                        "belege", List.of(Map.of("abschnitt", "§ 7 Abs. 2")),
                                        "tools", List.of("bedingungen_suchen")))))
                        .build()))
                .build();

        SubagentResponse response = SubagentResponse.of(task, "Beratungsagent");

        assertThat(response.text()).isEqualTo("Implantate sind mitversichert.");
        assertThat(response.data())
                .containsEntry("belege", List.of(Map.of("abschnitt", "§ 7 Abs. 2")))
                .containsEntry("tools", List.of("bedingungen_suchen"));
    }

    @Test
    void completedWithoutADataPartCarriesAnEmptyMapAndNotNull() {
        Task task = new Task.Builder()
                .id("aufgabe-5")
                .contextId("kontext-5")
                .status(new TaskStatus(TaskState.COMPLETED))
                .artifacts(List.of(new Artifact.Builder()
                        .artifactId("artefakt-5")
                        .name("auskunft")
                        .parts(List.<Part<?>>of(new TextPart("Nur Prosa.")))
                        .build()))
                .build();

        assertThat(SubagentResponse.of(task, "Arztservice").data()).isEmpty();
    }

    @Test
    void completedCarriesTheNameOfTheArtifact() {
        Task task = new Task.Builder()
                .id("aufgabe-7")
                .contextId("kontext-7")
                .status(new TaskStatus(TaskState.COMPLETED))
                .artifacts(List.of(new Artifact.Builder()
                        .artifactId("artefakt-7")
                        .name("terminbestaetigung")
                        .parts(List.<Part<?>>of(new TextPart("Der Termin steht.")))
                        .build()))
                .build();

        assertThat(SubagentResponse.of(task, "Arztservice").artifactName())
                .isEqualTo("terminbestaetigung");
    }

    @Test
    void completedWithAnUnnamedArtifactCarriesNoName() {
        Task task = new Task.Builder()
                .id("aufgabe-8")
                .contextId("kontext-8")
                .status(new TaskStatus(TaskState.COMPLETED))
                .artifacts(List.of(new Artifact.Builder()
                        .artifactId("artefakt-8")
                        .parts(List.<Part<?>>of(new TextPart("Nur Prosa.")))
                        .build()))
                .build();

        assertThat(SubagentResponse.of(task, "Arztservice").artifactName()).isNull();
    }

    @Test
    void completedWithSeveralArtifactsTakesTheFirstName() {
        Task task = new Task.Builder()
                .id("aufgabe-9")
                .contextId("kontext-9")
                .status(new TaskStatus(TaskState.COMPLETED))
                .artifacts(List.of(
                        new Artifact.Builder()
                                .artifactId("artefakt-9a")
                                .parts(List.<Part<?>>of(new TextPart("Ohne Namen. ")))
                                .build(),
                        new Artifact.Builder()
                                .artifactId("artefakt-9b")
                                .name("terminauskunft")
                                .parts(List.<Part<?>>of(new TextPart("Nichts frei.")))
                                .build(),
                        new Artifact.Builder()
                                .artifactId("artefakt-9c")
                                .name("beleg")
                                .parts(List.<Part<?>>of(new TextPart(" Und noch mehr.")))
                                .build()))
                .build();

        assertThat(SubagentResponse.of(task, "Arztservice").artifactName())
                .isEqualTo("terminauskunft");
    }

    @Test
    void completedWithoutAnyArtifactCarriesNoName() {
        Task task = new Task.Builder()
                .id("aufgabe-10")
                .contextId("kontext-10")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build();

        assertThat(SubagentResponse.of(task, "Arztservice").artifactName()).isNull();
    }

    @Test
    void inputRequiredYieldsInputRequiredWithTheStatusMessageText() {
        Message question = new Message.Builder()
                .role(Message.Role.AGENT)
                .messageId("nachricht-1")
                .parts(List.<Part<?>>of(new TextPart("Wie alt sind Sie?")))
                .build();
        Task task = new Task.Builder()
                .id("aufgabe-2")
                .contextId("kontext-2")
                .status(new TaskStatus(TaskState.INPUT_REQUIRED, question, OffsetDateTime.now()))
                .build();

        SubagentResponse response = SubagentResponse.of(task, "Beratungsagent");

        assertThat(response.outcome()).isEqualTo(Outcome.INPUT_REQUIRED);
        assertThat(response.artifactName()).isNull();
        assertThat(response.text()).isEqualTo("Wie alt sind Sie?");
        assertThat(response.contextId()).isEqualTo("kontext-2");
        assertThat(response.taskId()).isEqualTo("aufgabe-2");
    }

    @Test
    void rejectedYieldsRejectedWithTheStatusMessageText() {
        Message rejection = new Message.Builder()
                .role(Message.Role.AGENT)
                .messageId("nachricht-2")
                .parts(List.<Part<?>>of(new TextPart("Das kann ich nicht bearbeiten.")))
                .build();
        Task task = new Task.Builder()
                .id("aufgabe-3")
                .contextId("kontext-3")
                .status(new TaskStatus(TaskState.REJECTED, rejection, OffsetDateTime.now()))
                .build();

        SubagentResponse response = SubagentResponse.of(task, "Beratungsagent");

        assertThat(response.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(response.artifactName()).isNull();
        assertThat(response.text()).isEqualTo("Das kann ich nicht bearbeiten.");
    }

    @Test
    void failedThrowsAgentUnreachableWithTheRealAgentName() {
        Task task = new Task.Builder()
                .id("aufgabe-4")
                .contextId("kontext-4")
                .status(new TaskStatus(TaskState.FAILED))
                .build();

        assertThatThrownBy(() -> SubagentResponse.of(task, "Beratungsagent"))
                .isInstanceOf(AgentUnreachableException.class)
                .satisfies(exception -> assertThat(((AgentUnreachableException) exception).agent())
                        .isEqualTo("Beratungsagent"));
    }

    @Test
    void failedCarriesTheSubagentSentenceIntoTheException() {
        Message failure = new Message.Builder()
                .role(Message.Role.AGENT)
                .messageId("nachricht-6")
                .parts(List.<Part<?>>of(new TextPart("Ich komme bei Ihrer Frage nicht weiter.")))
                .build();
        Task task = new Task.Builder()
                .id("aufgabe-6")
                .contextId("kontext-6")
                .status(new TaskStatus(TaskState.FAILED, failure, OffsetDateTime.now()))
                .build();

        assertThatThrownBy(() -> SubagentResponse.of(task, "Beratungsagent"))
                .isInstanceOf(AgentUnreachableException.class)
                .hasMessageContaining("Ich komme bei Ihrer Frage nicht weiter.");
    }

    @Test
    void canceledThrowsAgentUnreachableWithTheRealAgentName() {
        Task task = new Task.Builder()
                .id("aufgabe-5")
                .contextId("kontext-5")
                .status(new TaskStatus(TaskState.CANCELED))
                .build();

        assertThatThrownBy(() -> SubagentResponse.of(task, "Orchestrator"))
                .isInstanceOf(AgentUnreachableException.class)
                .satisfies(exception -> assertThat(((AgentUnreachableException) exception).agent())
                        .isEqualTo("Orchestrator"));
    }
}
