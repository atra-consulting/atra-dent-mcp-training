package de.atra.kernsystem.rechenkern;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "kernsystem.rechenkern")
public record RechenkernProperties(String url, Duration timeout) {

    public RechenkernProperties {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("kernsystem.rechenkern.url fehlt: Das Kernsystem "
                    + "berechnet keinen Beitrag mehr selbst und kann mein_beitrag_berechnen "
                    + "ohne den Rechenkern nicht beantworten");
        }
        url = url.strip();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
    }
}
