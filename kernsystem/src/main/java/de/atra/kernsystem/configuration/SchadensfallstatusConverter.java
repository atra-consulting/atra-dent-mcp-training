package de.atra.kernsystem.configuration;

import de.atra.kernsystem.generated.model.Schadensfallstatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class SchadensfallstatusConverter implements Converter<String, Schadensfallstatus> {

    @Override
    public Schadensfallstatus convert(String source) {
        return Schadensfallstatus.fromValue(source);
    }
}
