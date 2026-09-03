package consulting.atra.agenten.orchestrator.agent;

import consulting.atra.agenten.a2a.agent.Outcome;
import consulting.atra.agenten.a2a.client.SubagentResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadRegistryTest {

    private final ThreadRegistry threadRegistry = new ThreadRegistry();

    @Test
    void anInputRequiredRecordsTheThreadPerContextAndAgent() {
        threadRegistry.record("gespraech-a", "Beratungsagent",
                new SubagentResponse(Outcome.INPUT_REQUIRED, "Wie alt sind Sie?", "kontext-1", "task-1"));

        assertThat(threadRegistry.thread("gespraech-a", "Beratungsagent"))
                .isEqualTo(new ThreadRegistry.Thread("kontext-1", "task-1"));
        assertThat(threadRegistry.thread("gespraech-a", "Anderer Agent")).isNull();
        assertThat(threadRegistry.thread("anderes-gespraech", "Beratungsagent")).isNull();
    }

    @Test
    void completedRemovesAPreviouslyRecordedThread() {
        threadRegistry.record("gespraech-b", "Beratungsagent",
                new SubagentResponse(Outcome.INPUT_REQUIRED, "Frage", "kontext", "task"));

        threadRegistry.record("gespraech-b", "Beratungsagent",
                new SubagentResponse(Outcome.COMPLETED, "Antwort", "kontext", "task"));

        assertThat(threadRegistry.thread("gespraech-b", "Beratungsagent")).isNull();
    }

    @Test
    void rejectedAlsoRemovesAPreviouslyRecordedThread() {
        threadRegistry.record("gespraech-c", "Beratungsagent",
                new SubagentResponse(Outcome.INPUT_REQUIRED, "Frage", "kontext", "task"));

        threadRegistry.record("gespraech-c", "Beratungsagent",
                new SubagentResponse(Outcome.REJECTED, "Absage", "kontext", "task"));

        assertThat(threadRegistry.thread("gespraech-c", "Beratungsagent")).isNull();
    }

    @Test
    void withoutARecordNothingIsOpen() {
        assertThat(threadRegistry.isOpen("unbekannte-konversation")).isFalse();
    }

    @Test
    void openReportsTrueWhileAnyAgentOfThisContextIsWaiting() {
        threadRegistry.record("gespraech-d", "Beratungsagent",
                new SubagentResponse(Outcome.INPUT_REQUIRED, "Frage", "kontext", "task"));

        assertThat(threadRegistry.isOpen("gespraech-d")).isTrue();
        assertThat(threadRegistry.isOpen("gespraech-e")).isFalse();
    }

    @Test
    void openBecomesFalseOnceTheLastThreadOfTheContextIsClosed() {
        threadRegistry.record("gespraech-f", "Beratungsagent",
                new SubagentResponse(Outcome.INPUT_REQUIRED, "Frage", "kontext", "task"));

        threadRegistry.record("gespraech-f", "Beratungsagent",
                new SubagentResponse(Outcome.COMPLETED, "Antwort", "kontext", "task"));

        assertThat(threadRegistry.isOpen("gespraech-f")).isFalse();
    }

    @Test
    void removingClearsAllThreadsOfThatIdAcrossSeveralAgents() {
        threadRegistry.record("gespraech-g", "Beratungsagent",
                new SubagentResponse(Outcome.INPUT_REQUIRED, "Frage 1", "kontext", "task-1"));
        threadRegistry.record("gespraech-g", "Anderer Agent",
                new SubagentResponse(Outcome.INPUT_REQUIRED, "Frage 2", "kontext", "task-2"));
        threadRegistry.record("anderes-gespraech", "Beratungsagent",
                new SubagentResponse(Outcome.INPUT_REQUIRED, "Frage 3", "kontext", "task-3"));

        threadRegistry.clear("gespraech-g");

        assertThat(threadRegistry.thread("gespraech-g", "Beratungsagent")).isNull();
        assertThat(threadRegistry.thread("gespraech-g", "Anderer Agent")).isNull();
        assertThat(threadRegistry.isOpen("gespraech-g")).isFalse();
        assertThat(threadRegistry.thread("anderes-gespraech", "Beratungsagent"))
                .isEqualTo(new ThreadRegistry.Thread("kontext", "task-3"));
    }

    @Test
    void removingWithoutAPriorRecordHasNoEffect() {
        threadRegistry.clear("nie-vermerkte-konversation");

        assertThat(threadRegistry.isOpen("nie-vermerkte-konversation")).isFalse();
    }
}
