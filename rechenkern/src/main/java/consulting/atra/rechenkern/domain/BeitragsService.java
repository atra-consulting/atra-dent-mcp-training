package consulting.atra.rechenkern.domain;

import consulting.atra.rechenkern.generated.model.TarifId;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BeitragsService {

    public record Altersband(int von, int bis, BigDecimal monatsbeitrag) {
    }

    public record Tarifbeitraege(TarifId tarifId, List<Altersband> baender) {
    }

    private final Map<TarifId, Tarifbeitraege> byTarif;

    public BeitragsService(ObjectMapper mapper) {
        List<Tarifbeitraege> read;
        try (InputStream stream = new ClassPathResource("beitraege.json").getInputStream()) {
            read = mapper.readValue(stream, new TypeReference<List<Tarifbeitraege>>() {});
        } catch (Exception cause) {
            throw new IllegalStateException("beitraege.json could not be read", cause);
        }
        this.byTarif = read.stream()
                .collect(Collectors.toMap(Tarifbeitraege::tarifId, Function.identity()));
    }

    public BigDecimal calculateMonthly(LocalDate geburtsdatum, TarifId tarifId,
                                    LocalDate gewuenschterBeginn) {
        if (geburtsdatum.isAfter(gewuenschterBeginn)) {
            throw new DomainException(
                    "Feld 'geburtsdatum' muss vor dem gewuenschten Beginn liegen");
        }
        int eintrittsalter = Period.between(geburtsdatum, gewuenschterBeginn).getYears();

        Tarifbeitraege beitraege = byTarif.get(tarifId);
        if (beitraege == null) {
            throw new DomainException("Fuer den Tarif " + tarifId + " ist kein Beitrag hinterlegt");
        }

        return beitraege.baender().stream()
                .filter(b -> eintrittsalter >= b.von() && eintrittsalter <= b.bis())
                .findFirst()
                .map(Altersband::monatsbeitrag)
                .orElseThrow(() -> new DomainException(
                        "Eintrittsalter " + eintrittsalter + " liegt ausserhalb der fuer "
                                + tarifId + " zulaessigen Grenzen"));
    }

    public BigDecimal monthlyBasisbeitrag(TarifId tarifId) {
        Tarifbeitraege beitraege = byTarif.get(tarifId);
        if (beitraege == null || beitraege.baender().isEmpty()) {
            throw new IllegalStateException("beitraege.json holds no Altersbaender for " + tarifId
                    + " -- without them there is neither a Basisbeitrag nor a"
                    + " Beitragsberechnung");
        }
        return beitraege.baender().stream()
                .min(Comparator.comparingInt(Altersband::von))
                .map(Altersband::monatsbeitrag)
                .orElseThrow();
    }
}
