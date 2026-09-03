package de.atra.kernsystem.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kernsystem")
public record KernsystemProperties(String apiKey, String dataDirectory, String basePath) {
}
