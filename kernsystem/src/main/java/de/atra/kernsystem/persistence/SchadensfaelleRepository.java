package de.atra.kernsystem.persistence;

import de.atra.kernsystem.generated.model.Schadensfall;
import de.atra.kernsystem.configuration.KernsystemProperties;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;

@Repository
public class SchadensfaelleRepository extends JsonRepository<Schadensfall> {

    static final long FIRST_SCHADENSFALL_NUMBER = 50001L;

    public SchadensfaelleRepository(KernsystemProperties properties, ObjectMapper mapper) {
        super(Path.of(properties.dataDirectory(), "schadensfaelle.json"),
                Schadensfall.class, Schadensfall::getId, FIRST_SCHADENSFALL_NUMBER, mapper);
    }
}
