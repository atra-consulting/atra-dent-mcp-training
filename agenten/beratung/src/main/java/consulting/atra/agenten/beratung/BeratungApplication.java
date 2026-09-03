package consulting.atra.agenten.beratung;

import consulting.atra.agenten.model.ApiKeyCheck;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "consulting.atra.agenten")
public class BeratungApplication {

    static void main(String[] args) {
        ApiKeyCheck.checkOrStop("Beratungsagent");
        SpringApplication.run(BeratungApplication.class, args);
    }
}
