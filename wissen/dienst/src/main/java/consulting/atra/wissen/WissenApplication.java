package consulting.atra.wissen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;

@SpringBootApplication
public class WissenApplication {

    static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(WissenApplication.class, args);
        if (context.getEnvironment().acceptsProfiles(Profiles.of("indexbau"))) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
