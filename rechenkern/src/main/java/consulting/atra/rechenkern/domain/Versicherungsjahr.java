package consulting.atra.rechenkern.domain;

import java.time.LocalDate;
import java.time.Period;

public final class Versicherungsjahr {

    private Versicherungsjahr() {
    }

    public static int derive(LocalDate versicherungsbeginn, LocalDate behandlungsdatum) {
        if (behandlungsdatum.isBefore(versicherungsbeginn)) {
            throw new DomainException("Das Behandlungsdatum " + behandlungsdatum
                    + " liegt vor dem Versicherungsbeginn " + versicherungsbeginn);
        }
        return Period.between(versicherungsbeginn, behandlungsdatum).getYears() + 1;
    }
}
