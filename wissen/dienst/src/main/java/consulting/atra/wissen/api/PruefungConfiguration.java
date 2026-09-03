package consulting.atra.wissen.api;

import org.hibernate.validator.HibernateValidatorConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Locale;

@Configuration(proxyBeanMethods = false)
class PruefungConfiguration {

    @Bean
    LocalValidatorFactoryBean defaultValidator() {
        return new LocalValidatorFactoryBean() {
            @Override
            protected void postProcessConfiguration(jakarta.validation.Configuration<?> konfiguration) {
                if (konfiguration instanceof HibernateValidatorConfiguration hibernate) {
                    hibernate.defaultLocale(Locale.GERMAN);
                }
            }
        };
    }
}
