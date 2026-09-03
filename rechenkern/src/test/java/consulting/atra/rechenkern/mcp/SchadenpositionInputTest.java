package consulting.atra.rechenkern.mcp;

import consulting.atra.rechenkern.domain.DomainException;
import consulting.atra.rechenkern.generated.model.Leistungsbereich;
import consulting.atra.rechenkern.generated.model.Schadenposition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchadenpositionInputTest {

    @Test
    void a_position_without_a_gebuehrennummer_is_accepted() {
        SchadenpositionInput input = new SchadenpositionInput(null, Leistungsbereich.ZE,
                "486.30", "Vollkeramische Krone, Zirkondioxid");

        Schadenposition position = input.toInternal();

        assertThat(position.getGoz()).isNull();
        assertThat(position.getLeistungsbereich()).isEqualTo(Leistungsbereich.ZE);
        assertThat(position.getBetrag()).isEqualByComparingTo("486.30");
        assertThat(position.getBeschreibung()).isEqualTo("Vollkeramische Krone, Zirkondioxid");
    }

    @Test
    void a_set_but_unusable_gebuehrennummer_stays_a_domain_error() {
        SchadenpositionInput input = new SchadenpositionInput("910", Leistungsbereich.IMP,
                "410.20", "Implantatinsertion");

        assertThatThrownBy(input::toInternal)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("910");
    }

    @Test
    void without_a_leistungsbereich_it_stays_a_domain_error_even_without_a_nummer() {
        SchadenpositionInput input = new SchadenpositionInput(null, null,
                "305.00", "Implantatkoerper und Verschlussschraube");

        assertThatThrownBy(input::toInternal)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("leistungsbereich")
                .hasMessageContaining("Implantatkoerper und Verschlussschraube");
    }

    @Test
    void an_unusable_betrag_without_a_nummer_names_the_position_by_its_leistungstext() {
        SchadenpositionInput input = new SchadenpositionInput(null, Leistungsbereich.ZE,
                "486,30", "Vollkeramische Krone, Zirkondioxid");

        assertThatThrownBy(input::toInternal)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Vollkeramische Krone, Zirkondioxid")
                .hasMessageNotContaining("null");
    }

    @Test
    void a_position_with_no_data_at_all_invents_no_betrag_in_the_message() {
        SchadenpositionInput input =
                new SchadenpositionInput(null, Leistungsbereich.ZE, null, null);

        assertThatThrownBy(input::toInternal)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("ohne jede Angabe")
                .hasMessageNotContaining("null EUR");
    }

    @Test
    void a_missing_beschreibung_without_a_nummer_names_the_position_by_its_betrag() {
        SchadenpositionInput input = new SchadenpositionInput(null, Leistungsbereich.ZE,
                "486.30", "  ");

        assertThatThrownBy(input::toInternal)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("beschreibung")
                .hasMessageContaining("486.30");
    }
}
