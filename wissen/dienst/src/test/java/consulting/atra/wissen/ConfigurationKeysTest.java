package consulting.atra.wissen;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationKeysTest {

    private static final String PREFIX = "wissen.";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+)");

    @Test
    void theIndexBuildEmbedsEveryPassageAfresh() {
        ConfigurableEnvironment environment = environmentOf(IndexBuildConfiguration.PROFILE);

        assertThat(environment.getProperty("wissen.index.load", Boolean.class, true)).isFalse();
    }

    @Test
    void theServiceLoadsTheShippedIndex() {
        ConfigurableEnvironment environment = environmentOf();

        assertThat(environment.getProperty("wissen.index.load", Boolean.class, true)).isTrue();
    }

    @Test
    void everyWissenKeyInTheConfigurationIsReadByTheCode() throws Exception {
        Set<String> configured = configuredKeys();

        assertThat(configured).contains("wissen.data.directory", "wissen.documents.directory");
        assertThat(configured)
                .as("keys under %s in application*.yaml that no @Value reads", PREFIX)
                .isSubsetOf(keysReadByTheCode());
    }

    private static ConfigurableEnvironment environmentOf(String... profiles) {
        SpringApplication application = new SpringApplication(NoBeans.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.setAdditionalProfiles(profiles);
        try (ConfigurableApplicationContext context = application.run()) {
            return context.getEnvironment();
        }
    }

    private static Set<String> configuredKeys() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        Set<String> keys = new TreeSet<>();
        Resource[] files = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:application*.yaml");
        for (Resource file : files) {
            for (PropertySource<?> source : loader.load(file.getFilename(), file)) {
                for (String key : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                    if (key.startsWith(PREFIX)) {
                        keys.add(key);
                    }
                }
            }
        }
        return keys;
    }

    private static Set<String> keysReadByTheCode() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((reader, factory) -> true);
        Set<String> keys = new TreeSet<>();
        for (BeanDefinition candidate
                : scanner.findCandidateComponents(WissenApplication.class.getPackageName())) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            for (AnnotatedElement element : annotatedElements(type)) {
                Value value = element.getAnnotation(Value.class);
                if (value == null) {
                    continue;
                }
                Matcher placeholder = PLACEHOLDER.matcher(value.value());
                while (placeholder.find()) {
                    keys.add(placeholder.group(1));
                }
            }
        }
        return keys;
    }

    private static List<AnnotatedElement> annotatedElements(Class<?> type) {
        List<Executable> executables = new ArrayList<>(List.of(type.getDeclaredConstructors()));
        executables.addAll(List.of(type.getDeclaredMethods()));
        List<AnnotatedElement> elements = new ArrayList<>(List.of(type.getDeclaredFields()));
        for (Executable executable : executables) {
            elements.add(executable);
            for (Parameter parameter : executable.getParameters()) {
                elements.add(parameter);
            }
        }
        return elements;
    }

    private static final class NoBeans {
    }
}
