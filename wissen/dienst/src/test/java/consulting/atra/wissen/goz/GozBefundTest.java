package consulting.atra.wissen.goz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GozBefundTest {

    @Test
    @DisplayName("a Quote or limits without an assurance are rejected")
    void assuranceOnlyWhenEnthalten() {
        assertThatThrownBy(() -> new GozBefund("9010", "Implantatinsertion", "K", "IMP",
                GozStatus.NICHT_ENTHALTEN, 85, null, "Begruendung"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NICHT_ENTHALTEN mit Quote");

        assertThatThrownBy(() -> new GozBefund("9010", "Implantatinsertion", "K", "IMP",
                GozStatus.NICHT_ENTHALTEN, null, Leistungsgrenzen.none(), "Begruendung"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NICHT_ENTHALTEN mit Grenzen");
    }

    @Test
    @DisplayName("ENTHALTEN without a Quote or without limits is incomplete")
    void enthaltenNeedsAssurance() {
        assertThatThrownBy(() -> new GozBefund("9010", "Implantatinsertion", "K", "IMP",
                GozStatus.ENTHALTEN, null, Leistungsgrenzen.none(), "Begruendung"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ENTHALTEN ohne Quote");

        assertThatThrownBy(() -> new GozBefund("9010", "Implantatinsertion", "K", "IMP",
                GozStatus.ENTHALTEN, 85, null, "Begruendung"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ENTHALTEN ohne Grenzen");
    }

    @Test
    @DisplayName("without a Leistungsbereich no status that requires one")
    void leistungsbereichMatchesTheStatus() {
        assertThatThrownBy(() -> GozBefund.notIncluded("9010", "Implantatinsertion", "K",
                null, "Begruendung"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NICHT_ENTHALTEN ohne Leistungsbereich");

        assertThatThrownBy(() -> new GozBefund("0010", "Untersuchung", "A", "ZE",
                GozStatus.NICHT_BESTIMMBAR, null, null, "Begruendung"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NICHT_BESTIMMBAR mit Leistungsbereich ZE");

        assertThatThrownBy(() -> new GozBefund("9999", "Erfunden", "K", null,
                GozStatus.UNBEKANNT, null, null, "Begruendung"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNBEKANNT mit Angaben aus der GOZ");
    }

    @Test
    @DisplayName("Nummer, status and Begruendung are required")
    void requiredFields() {
        assertThatThrownBy(() -> GozBefund.unknown("  ", "Begruendung"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Gebuehrennummer");

        assertThatThrownBy(() -> GozBefund.unknown("9999", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Begruendung");

        assertThatThrownBy(() -> new GozBefund("9999", null, null, null, null, null, null, "Text"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Status");
    }

    @Test
    @DisplayName("a Quote outside 0 to 100 is no Erstattungsquote")
    void quotenRange() {
        assertThatThrownBy(() -> GozBefund.included("9010", "Implantatinsertion", "K", "IMP",
                120, Leistungsgrenzen.none(), "Begruendung"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quote ausserhalb von 0 bis 100");
    }

    @Test
    @DisplayName("Leistungsgrenzen without content are empty and not null")
    void emptyLimits() {
        Leistungsgrenzen none = Leistungsgrenzen.none();

        assertThat(none.istLeer()).isTrue();
        assertThat(none.limitProJahr()).isNull();
        assertThat(Begruendung.restrictions(none)).isEmpty();
    }
}
