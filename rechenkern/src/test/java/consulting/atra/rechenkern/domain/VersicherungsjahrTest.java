package consulting.atra.rechenkern.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersicherungsjahrTest {

    static final LocalDate START = LocalDate.of(2025, 3, 1);

    @Test
    void the_day_of_the_beginn_falls_in_the_first_versicherungsjahr() {
        assertThat(Versicherungsjahr.derive(START, START)).isEqualTo(1);
    }

    @Test
    void the_day_before_the_first_anniversary_is_still_the_first_versicherungsjahr() {
        assertThat(Versicherungsjahr.derive(START, LocalDate.of(2026, 2, 28))).isEqualTo(1);
    }

    @Test
    void the_anniversary_opens_the_second_versicherungsjahr() {
        assertThat(Versicherungsjahr.derive(START, LocalDate.of(2026, 3, 1))).isEqualTo(2);
    }

    @Test
    void a_good_three_years_later_is_the_fourth_versicherungsjahr() {
        assertThat(Versicherungsjahr.derive(START, LocalDate.of(2028, 6, 15))).isEqualTo(4);
    }

    @Test
    void calendar_years_do_not_count_the_turn_of_the_year_has_no_effect() {
        assertThat(Versicherungsjahr.derive(START, LocalDate.of(2025, 12, 31))).isEqualTo(1);
        assertThat(Versicherungsjahr.derive(START, LocalDate.of(2026, 1, 1))).isEqualTo(1);
    }

    @Test
    void a_behandlung_before_the_versicherungsbeginn_is_not_allowed() {
        assertThatThrownBy(() -> Versicherungsjahr.derive(START, LocalDate.of(2025, 2, 28)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Versicherungsbeginn");
    }
}
