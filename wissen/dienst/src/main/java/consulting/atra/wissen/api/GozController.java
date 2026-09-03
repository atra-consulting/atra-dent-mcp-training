package consulting.atra.wissen.api;

import consulting.atra.wissen.api.generated.GozApi;
import consulting.atra.wissen.api.generated.model.GozPruefungRequest;
import consulting.atra.wissen.api.generated.model.GozPruefungResult;
import consulting.atra.wissen.goz.GozBefund;
import consulting.atra.wissen.goz.GozPruefung;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
class GozController implements GozApi {

    private final GozPruefung pruefung;

    GozController(GozPruefung pruefung) {
        this.pruefung = Objects.requireNonNull(pruefung, "pruefung");
    }

    @Override
    public ResponseEntity<GozPruefungResult> checkGoz(GozPruefungRequest request) {
        List<GozBefund> findings = pruefung.check(request.getTarif().getValue(), request.getNummern());

        return ResponseEntity.ok(new GozPruefungResult(
                request.getTarif(),
                findings.stream().map(Mapping::toApi).toList()));
    }
}
