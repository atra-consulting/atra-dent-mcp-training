package consulting.atra.rechenkern.mcp;

import consulting.atra.rechenkern.domain.DomainException;
import consulting.atra.rechenkern.generated.model.Vorverbrauch;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VorverbrauchInputTest {

    @Test
    void an_unknown_key_in_leistungsbereiche_is_a_domain_error() {
        VorverbrauchInput input = new VorverbrauchInput(null, null, null,
                Map.of("Zahnersatz", "450.00"), null);

        assertThatThrownBy(input::toInternal)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Zahnersatz")
                .hasMessageContaining("leistungsbereiche")
                .hasMessageContaining("ZE")
                .hasMessageContaining("AKUT");
    }

    @Test
    void an_unknown_key_in_leistungsbereiche_seit_versicherungsbeginn_is_a_domain_error() {
        VorverbrauchInput input = new VorverbrauchInput(null, null, null, null,
                Map.of("KIEFERORTHOPAEDIE", "650.00"));

        assertThatThrownBy(input::toInternal)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("KIEFERORTHOPAEDIE")
                .hasMessageContaining("leistungsbereicheSeitVersicherungsbeginn")
                .hasMessageContaining("KFO");
    }

    @Test
    void valid_keys_are_taken_over_as_before() {
        VorverbrauchInput input = new VorverbrauchInput("300.00", "40.00", "500.00",
                Map.of("PZR", "80.00", "ZE", "1200.00"),
                Map.of("KFO", "650.00"));

        Vorverbrauch verbrauch = input.toInternal();

        assertThat(verbrauch.getStaffel()).isEqualByComparingTo("300.00");
        assertThat(verbrauch.getSelbstbehalt()).isEqualByComparingTo("40.00");
        assertThat(verbrauch.getJahr()).isEqualByComparingTo("500.00");
        assertThat(verbrauch.getLeistungsbereiche())
                .containsEntry("PZR", new BigDecimal("80.00"))
                .containsEntry("ZE", new BigDecimal("1200.00"));
        assertThat(verbrauch.getLeistungsbereicheSeitVersicherungsbeginn())
                .containsEntry("KFO", new BigDecimal("650.00"));
    }
}
