package consulting.atra.agenten.schadensfall;

import consulting.atra.agenten.a2a.client.SubagentProperties;
import consulting.atra.agenten.mcp.ObservedTools;
import consulting.atra.agenten.schadensfall.agent.SchadensfallPoller;
import consulting.atra.agenten.schadensfall.kernsystem.KernsystemClient;
import consulting.atra.agenten.schadensfall.check.SchadensfallProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {TestModel.WITHOUT_API_KEY, "schadensfall.poll-interval=1h"})
@Import({TestModel.class, TestTools.class})
class SchadensfallContextTest {

    @MockitoBean
    @SuppressWarnings("unused")
    private KernsystemClient kernsystem;

    @Autowired
    private ScheduledTaskHolder tasks;

    @Autowired
    private SchadensfallProperties properties;

    @Autowired
    private SubagentProperties subagents;

    @Test
    @DisplayName("the poller's tick really is registered as a task")
    void thePollerIsRegistered() {
        assertThat(tasks.getScheduledTasks())
                .extracting(ScheduledTask::toString)
                .anyMatch(aufgabe -> aufgabe.contains(SchadensfallPoller.class.getName())
                        && aufgabe.contains("run"));
    }

    @Test
    @DisplayName("the reconcile timeout is longer than the longest possible check run")
    void theReconcileTimeoutCoversTheLongestCheckRun() {
        Duration longestCheckRun = subagents.timeout().multipliedBy(ObservedTools.MAX_COUNT);

        assertThat(properties.reconcileTimeout()).isGreaterThan(longestCheckRun);
    }
}
