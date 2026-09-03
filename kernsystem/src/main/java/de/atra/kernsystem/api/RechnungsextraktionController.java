package de.atra.kernsystem.api;

import de.atra.kernsystem.domain.DomainException;
import de.atra.kernsystem.domain.rechnungsextraktion.RechnungsextraktionService;
import de.atra.kernsystem.generated.api.RechnungsextraktionApi;
import de.atra.kernsystem.generated.model.RechnungsextraktionResult;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class RechnungsextraktionController implements RechnungsextraktionApi {

    private final RechnungsextraktionService service;

    public RechnungsextraktionController(RechnungsextraktionService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<RechnungsextraktionResult> extractRechnung(Resource body) {
        try {
            return ResponseEntity.ok(service.extract(body.getContentAsByteArray()));
        } catch (IOException cause) {
            throw new DomainException(RechnungsextraktionService.GENERIC_MESSAGE);
        }
    }
}
