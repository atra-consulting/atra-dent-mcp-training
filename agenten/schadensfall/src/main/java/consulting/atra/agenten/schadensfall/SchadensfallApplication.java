package consulting.atra.agenten.schadensfall;

import consulting.atra.agenten.model.ApiKeyCheck;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "consulting.atra.agenten")
@ConfigurationPropertiesScan
@EnableScheduling
public class SchadensfallApplication {

    static void main(String[] args) {
        ApiKeyCheck.checkOrStop("Schadensfallagent");
        SpringApplication.run(SchadensfallApplication.class, args);
    }
}
