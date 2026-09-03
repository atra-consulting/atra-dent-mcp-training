package de.atra.kernsystem.persistence;

import de.atra.kernsystem.generated.model.Kunde;
import de.atra.kernsystem.configuration.KernsystemProperties;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;

@Repository
public class KundenRepository extends JsonRepository<Kunde> {

    static final long FIRST_KUNDENNUMMER = 10001L;

    public KundenRepository(KernsystemProperties properties, ObjectMapper mapper) {
        super(Path.of(properties.dataDirectory(), "kunden.json"),
                Kunde.class, Kunde::getId, FIRST_KUNDENNUMMER, mapper);
    }
}
