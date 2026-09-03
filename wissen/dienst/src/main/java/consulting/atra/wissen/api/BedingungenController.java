package consulting.atra.wissen.api;

import consulting.atra.wissen.api.generated.BedingungenApi;
import consulting.atra.wissen.api.generated.model.SucheRequest;
import consulting.atra.wissen.api.generated.model.SucheResult;
import consulting.atra.wissen.search.BedingungenSearch;
import consulting.atra.wissen.search.Hit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
class BedingungenController implements BedingungenApi {

    private final BedingungenSearch search;

    BedingungenController(BedingungenSearch suche) {
        this.search = Objects.requireNonNull(suche, "suche");
    }

    @Override
    public ResponseEntity<SucheResult> searchBedingungswerk(SucheRequest request) {
        int count = request.getAnzahl() == null
                ? BedingungenSearch.DEFAULT_COUNT
                : request.getAnzahl();

        List<Hit> hits = search.search(request.getTarif().getValue(), request.getFrage(), count);

        return ResponseEntity.ok(new SucheResult(
                request.getTarif(),
                request.getFrage(),
                hits.stream().map(Mapping::toApi).toList()));
    }
}
