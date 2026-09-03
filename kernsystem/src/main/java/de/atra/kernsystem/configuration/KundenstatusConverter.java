package de.atra.kernsystem.configuration;

import de.atra.kernsystem.generated.model.Kundenstatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class KundenstatusConverter implements Converter<String, Kundenstatus> {

    @Override
    public Kundenstatus convert(String source) {
        return Kundenstatus.fromValue(source);
    }
}
