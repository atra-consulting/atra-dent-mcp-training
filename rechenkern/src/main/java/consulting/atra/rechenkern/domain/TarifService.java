package consulting.atra.rechenkern.domain;

import consulting.atra.rechenkern.generated.model.Tarif;
import consulting.atra.rechenkern.generated.model.TarifId;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TarifService {

    private final List<Tarif> tarife;
    private final Map<TarifId, Tarif> byId;

    public TarifService(ObjectMapper mapper, BeitragsService beitraege) {
        try (InputStream stream = new ClassPathResource("tarife.json").getInputStream()) {
            this.tarife = List.copyOf(mapper.readValue(stream, new TypeReference<List<Tarif>>() {}));
        } catch (Exception cause) {
            throw new IllegalStateException("tarife.json could not be read", cause);
        }
        tarife.forEach(tarif ->
                tarif.setBasisbeitragMonatlich(beitraege.monthlyBasisbeitrag(tarif.getId())));
        this.byId = tarife.stream().collect(Collectors.toMap(Tarif::getId, Function.identity()));
    }

    public List<Tarif> all() {
        return tarife;
    }

    public Tarif read(TarifId id) {
        Tarif tarif = byId.get(id);
        if (tarif == null) {
            throw new NotFoundException("Tarif", id);
        }
        return tarif;
    }
}
