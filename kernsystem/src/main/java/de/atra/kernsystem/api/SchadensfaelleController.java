package de.atra.kernsystem.api;

import de.atra.kernsystem.domain.SchadensfallService;
import de.atra.kernsystem.generated.api.SchadensfaelleApi;
import de.atra.kernsystem.generated.model.Protokollakteur;
import de.atra.kernsystem.generated.model.Schaden;
import de.atra.kernsystem.generated.model.Schadensfall;
import de.atra.kernsystem.generated.model.SchadensfallAendern;
import de.atra.kernsystem.generated.model.SchadensfallBewerten;
import de.atra.kernsystem.generated.model.Schadensfallstatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class SchadensfaelleController implements SchadensfaelleApi {

    private final SchadensfallService service;

    public SchadensfaelleController(SchadensfallService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<Schadensfall>> listSchadensfaelle(Long kundenId,
                                                                      List<Schadensfallstatus> status) {
        return ResponseEntity.ok(service.search(kundenId, status));
    }

    @Override
    public ResponseEntity<List<Schadensfall>> listKundenSchadensfaelle(Long kundenId) {
        return ResponseEntity.ok(service.history(kundenId));
    }

    @Override
    public ResponseEntity<Schadensfall> readSchadensfall(Long schadensfallId) {
        return ResponseEntity.ok(service.read(schadensfallId));
    }

    @Override
    public ResponseEntity<Schadensfall> submitSchadensfall(Schaden schaden) {
        Schadensfall submitted = service.submit(schaden, Protokollakteur.SACHBEARBEITUNG);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(submitted.getId()).toUri();
        return ResponseEntity.created(location).body(submitted);
    }

    @Override
    public ResponseEntity<Schadensfall> updateSchadensfall(Long schadensfallId,
                                                                  SchadensfallAendern schadensfallAendern) {
        return ResponseEntity.ok(service.update(schadensfallId, schadensfallAendern));
    }

    @Override
    public ResponseEntity<Schadensfall> assessSchadensfall(Long schadensfallId,
                                                             SchadensfallBewerten input) {
        return ResponseEntity.ok(service.assess(schadensfallId, input));
    }
}
