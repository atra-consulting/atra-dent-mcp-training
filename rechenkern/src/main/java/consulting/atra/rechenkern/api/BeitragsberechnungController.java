package consulting.atra.rechenkern.api;

import consulting.atra.rechenkern.domain.BeitragsService;
import consulting.atra.rechenkern.generated.api.BeitragsberechnungApi;
import consulting.atra.rechenkern.generated.model.BeitragsberechnungRequest;
import consulting.atra.rechenkern.generated.model.BeitragsberechnungResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BeitragsberechnungController implements BeitragsberechnungApi {

    private final BeitragsService service;

    public BeitragsberechnungController(BeitragsService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<BeitragsberechnungResult> calculateBeitrag(
            BeitragsberechnungRequest request) {
        var beitrag = service.calculateMonthly(
                request.getGeburtsdatum(), request.getTarifId(), request.getGewuenschterBeginn());
        return ResponseEntity.ok(new BeitragsberechnungResult().monatsbeitrag(beitrag));
    }
}
