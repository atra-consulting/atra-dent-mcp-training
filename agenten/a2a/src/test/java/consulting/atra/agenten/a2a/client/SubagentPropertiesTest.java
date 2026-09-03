package consulting.atra.agenten.a2a.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubagentPropertiesTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(4);

    private static final Duration CARD_TIMEOUT = Duration.ofSeconds(60);

    @Test
    @DisplayName("an entry without an address is left out instead of stopping the start")
    void anEntryWithoutAnUrlIsSkipped() {
        Map<String, Subagent> catalog = new LinkedHashMap<>();
        catalog.put("beratung", Subagent.at("http://localhost:8085"));
        catalog.put("arztservice", Subagent.at(""));

        SubagentProperties properties =
                new SubagentProperties(catalog, TIMEOUT, CARD_TIMEOUT);

        assertThat(properties.wired())
                .containsExactly(Subagent.at("http://localhost:8085"));
    }

    @Test
    @DisplayName("a blank api-key is the same as none, and costs the entry nothing")
    void aBlankApiKeyIsNoKey() {
        SubagentProperties properties = new SubagentProperties(
                Map.of("beratung", new Subagent("http://localhost:8085", "   ")),
                TIMEOUT, CARD_TIMEOUT);

        assertThat(properties.wired()).singleElement()
                .extracting(Subagent::apiKey).isEqualTo("");
    }

    @Test
    @DisplayName("a mistyped address is named with the entry it stands in")
    void anInvalidUrlNamesItsEntry() {
        Map<String, Subagent> catalog =
                Map.of("arztservice", Subagent.at("http://exa mple.invalid"));

        assertThatThrownBy(() -> new SubagentProperties(catalog, TIMEOUT, CARD_TIMEOUT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agenten.subagents.catalog.arztservice.url");
    }

    @Test
    @DisplayName("an agent can be added from the environment alone, and the catalog keeps the rest")
    void theEnvironmentAddsAnEntryInsteadOfReplacingTheCatalog() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("agenten.subagents.timeout", "4m");
        yaml.put("agenten.subagents.card-timeout", "60s");
        yaml.put("agenten.subagents.catalog.beratung.url", "http://localhost:8085");
        yaml.put("agenten.subagents.catalog.beratung.api-key", "");

        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("AGENTEN_SUBAGENTS_CATALOG_PRAXISAGENT_URL", "https://praxis.example");
        environment.put("AGENTEN_SUBAGENTS_CATALOG_PRAXISAGENT_API_KEY", "geheim");

        SubagentProperties properties = bind(yaml, environment);

        assertThat(properties.catalog()).containsOnlyKeys("beratung", "praxisagent");
        assertThat(properties.catalog().get("praxisagent"))
                .isEqualTo(new Subagent("https://praxis.example", "geheim"));
        assertThat(properties.catalog().get("beratung").apiKey()).isEmpty();
    }

    private static SubagentProperties bind(Map<String, Object> yaml,
                                           Map<String, Object> environment) {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environment));
        sources.addLast(new MapPropertySource("yaml", yaml));
        return new Binder(ConfigurationPropertySources.from(sources))
                .bind("agenten.subagents", Bindable.of(SubagentProperties.class)).get();
    }

    @Test
    @DisplayName("an agent without peers is a state of its own, not a mistake")
    void anEmptyCatalogIsAllowed() {
        assertThat(new SubagentProperties(Map.of(), TIMEOUT, CARD_TIMEOUT).wired()).isEmpty();
        assertThat(new SubagentProperties(null, TIMEOUT, CARD_TIMEOUT).catalog()).isEmpty();
    }

    @Test
    @DisplayName("an agent without peers still binds when the catalog is left out entirely")
    void aMissingCatalogBinds() {
        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("agenten.subagents.timeout", "60s");
        yaml.put("agenten.subagents.card-timeout", "60s");

        SubagentProperties properties = bind(yaml, Map.of());

        assertThat(properties.catalog()).isEmpty();
        assertThat(properties.wired()).isEmpty();
    }
}
