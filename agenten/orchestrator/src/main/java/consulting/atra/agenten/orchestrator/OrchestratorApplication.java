package consulting.atra.agenten.orchestrator;

import consulting.atra.agenten.model.ApiKeyCheck;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "consulting.atra.agenten")
public class OrchestratorApplication {

    static void main(String[] args) {
        ApiKeyCheck.checkOrStop("Orchestrator");
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
