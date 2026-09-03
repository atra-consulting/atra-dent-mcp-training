package consulting.atra.rechenkern.api;

import consulting.atra.rechenkern.domain.Erstattungsberechnung;
import consulting.atra.rechenkern.generated.api.ErstattungsberechnungApi;
import consulting.atra.rechenkern.generated.model.ErstattungsberechnungRequest;
import consulting.atra.rechenkern.generated.model.ErstattungsberechnungResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ErstattungsberechnungController implements ErstattungsberechnungApi {

    private final Erstattungsberechnung calculation;

    public ErstattungsberechnungController(Erstattungsberechnung berechnung) {
        this.calculation = berechnung;
    }

    @Override
    public ResponseEntity<ErstattungsberechnungResult> calculateErstattung(
            ErstattungsberechnungRequest erstattungsberechnungRequest) {
        return ResponseEntity.ok(calculation.calculate(erstattungsberechnungRequest));
    }
}
