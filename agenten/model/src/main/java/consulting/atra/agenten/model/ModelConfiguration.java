package consulting.atra.agenten.model;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ModelConfiguration {

    @Bean
    @ConditionalOnMissingBean(ModelClient.class)
    ModelClient modellClient(ChatClient.Builder erbauer) {
        return new GeminiClient(erbauer.build());
    }
}
