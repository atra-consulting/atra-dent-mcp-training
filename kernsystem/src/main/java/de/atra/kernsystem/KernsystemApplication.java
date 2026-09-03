package de.atra.kernsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KernsystemApplication {

    static void main(String[] args) {
        SpringApplication.run(KernsystemApplication.class, args);
    }
}
