package consulting.atra.rechenkern.domain;

import consulting.atra.rechenkern.generated.model.TarifId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BeitragsServiceTest {

    static final LocalDate START = LocalDate.of(2026, 1, 1);

    @Autowired
    BeitragsService service;

    private java.math.BigDecimal beitrag(int birthYear, TarifId tarif) {
        return service.calculateMonthly(LocalDate.of(birthYear, 1, 1), tarif, START);
    }

    @Test
    void the_eintrittsalter_counts_to_the_desired_beginn_not_to_today() {
        assertThat(service.calculateMonthly(LocalDate.of(1995, 1, 1), TarifId.ATRA_DENT_B, START))
                .isEqualByComparingTo("20.90");
    }

    @Test
    void the_band_boundary_30_31_separates_cleanly() {
        assertThat(beitrag(1996, TarifId.ATRA_DENT_B)).isEqualByComparingTo("15.90");
        assertThat(beitrag(1995, TarifId.ATRA_DENT_B)).isEqualByComparingTo("20.90");
    }

    @Test
    void the_band_boundary_60_61_separates_cleanly() {
        assertThat(beitrag(1966, TarifId.ATRA_DENT_S)).isEqualByComparingTo("21.90");
        assertThat(beitrag(1965, TarifId.ATRA_DENT_S)).isEqualByComparingTo("28.90");
    }

    @Test
    void every_tarif_returns_its_entry_beitrag_in_the_lowest_band() {
        assertThat(beitrag(2000, TarifId.ATRA_DENT_S)).isEqualByComparingTo("8.90");
        assertThat(beitrag(2000, TarifId.ATRA_DENT_B)).isEqualByComparingTo("15.90");
        assertThat(beitrag(2000, TarifId.ATRA_DENT_X)).isEqualByComparingTo("24.90");
        assertThat(beitrag(2000, TarifId.ATRA_DENT_X_SB)).isEqualByComparingTo("19.90");
    }

    @Test
    void under_18_is_not_allowed_the_model_knows_only_adults() {
        assertThatThrownBy(() -> beitrag(2009, TarifId.ATRA_DENT_B))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Eintrittsalter");
    }

    @Test
    void brillant_ends_at_65_balance_only_at_70() {
        assertThat(beitrag(1961, TarifId.ATRA_DENT_X)).isEqualByComparingTo("69.90");
        assertThatThrownBy(() -> beitrag(1960, TarifId.ATRA_DENT_X))
                .isInstanceOf(DomainException.class);
        assertThat(beitrag(1956, TarifId.ATRA_DENT_B)).isEqualByComparingTo("47.90");
        assertThatThrownBy(() -> beitrag(1955, TarifId.ATRA_DENT_B))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void a_geburtsdatum_after_the_beginn_is_not_allowed() {
        assertThatThrownBy(() -> service.calculateMonthly(
                LocalDate.of(2027, 5, 1), TarifId.ATRA_DENT_B, START))
                .isInstanceOf(DomainException.class);
    }
}
