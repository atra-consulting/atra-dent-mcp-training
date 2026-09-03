package consulting.atra.agenten.schadensfall.kernsystem;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kernsystem.rest")
public record KernsystemProperties(String url, String apiKey) {

    public KernsystemProperties {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("kernsystem.rest.url fehlt");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("kernsystem.rest.api-key fehlt");
        }
    }
}
