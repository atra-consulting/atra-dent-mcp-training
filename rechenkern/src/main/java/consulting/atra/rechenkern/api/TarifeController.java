package consulting.atra.rechenkern.api;

import consulting.atra.rechenkern.domain.TarifService;
import consulting.atra.rechenkern.generated.api.TarifeApi;
import consulting.atra.rechenkern.generated.model.Tarif;
import consulting.atra.rechenkern.generated.model.TarifId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TarifeController implements TarifeApi {

    private final TarifService service;

    public TarifeController(TarifService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<Tarif>> listTarife() {
        return ResponseEntity.ok(service.all());
    }

    @Override
    public ResponseEntity<Tarif> readTarif(TarifId tarifId) {
        return ResponseEntity.ok(service.read(tarifId));
    }
}
