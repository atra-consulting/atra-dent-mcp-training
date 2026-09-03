package de.atra.kernsystem.domain;

import de.atra.kernsystem.generated.model.Protokollakteur;
import de.atra.kernsystem.generated.model.Schadensfallstatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static de.atra.kernsystem.domain.StatusTransition.Route.BEWERTUNG;
import static de.atra.kernsystem.domain.StatusTransition.Route.PATCH;
import static de.atra.kernsystem.generated.model.Schadensfallstatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class StatusTransitionTest {

    @ParameterizedTest(name = "{0} -> {1} per {2} ist erlaubt, Akteur {3}")
    @CsvSource({
            "EINGEREICHT, IN_PRUEFUNG, PATCH, AGENT",
            "IN_PRUEFUNG, GEPRUEFT_FREIGABE, BEWERTUNG, AGENT",
            "IN_PRUEFUNG, GEPRUEFT_ESKALATION, BEWERTUNG, AGENT",
            "IN_PRUEFUNG, EINGEREICHT, PATCH, SYSTEM",
            "GEPRUEFT_FREIGABE, EINGEREICHT, PATCH, SACHBEARBEITUNG",
            "GEPRUEFT_ESKALATION, EINGEREICHT, PATCH, SACHBEARBEITUNG",
            "EINGEREICHT, GENEHMIGT, PATCH, SACHBEARBEITUNG",
            "EINGEREICHT, ABGELEHNT, PATCH, SACHBEARBEITUNG",
            "IN_PRUEFUNG, GENEHMIGT, PATCH, SACHBEARBEITUNG",
            "IN_PRUEFUNG, ABGELEHNT, PATCH, SACHBEARBEITUNG",
            "GEPRUEFT_FREIGABE, GENEHMIGT, PATCH, SACHBEARBEITUNG",
            "GEPRUEFT_FREIGABE, ABGELEHNT, PATCH, SACHBEARBEITUNG",
            "GEPRUEFT_ESKALATION, GENEHMIGT, PATCH, SACHBEARBEITUNG",
            "GEPRUEFT_ESKALATION, ABGELEHNT, PATCH, SACHBEARBEITUNG",
            "GENEHMIGT, AUSGEZAHLT, PATCH, SACHBEARBEITUNG",
    })
    void allowedTransitions(Schadensfallstatus von, Schadensfallstatus nach,
                             StatusTransition.Route weg, Protokollakteur akteur) {
        var result = StatusTransition.check(von, nach, weg);
        assertThat(result.allowed()).isTrue();
        assertThat(result.akteur()).isEqualTo(akteur);
    }

    @ParameterizedTest(name = "{0} -> {1} per {2} ist verboten")
    @CsvSource({
            "IN_PRUEFUNG, GEPRUEFT_FREIGABE, PATCH",
            "IN_PRUEFUNG, GEPRUEFT_ESKALATION, PATCH",
            "IN_PRUEFUNG, GENEHMIGT, BEWERTUNG",
            "EINGEREICHT, GEPRUEFT_FREIGABE, BEWERTUNG",
            "GEPRUEFT_FREIGABE, GEPRUEFT_ESKALATION, BEWERTUNG",
            "GEPRUEFT_FREIGABE, GEPRUEFT_FREIGABE, BEWERTUNG",
            "GEPRUEFT_ESKALATION, GEPRUEFT_ESKALATION, BEWERTUNG",
            "GEPRUEFT_ESKALATION, IN_PRUEFUNG, PATCH",
            "GENEHMIGT, IN_PRUEFUNG, PATCH",
            "ABGELEHNT, GENEHMIGT, PATCH",
            "ABGELEHNT, EINGEREICHT, PATCH",
            "AUSGEZAHLT, GENEHMIGT, PATCH",
            "AUSGEZAHLT, EINGEREICHT, PATCH",
            "EINGEREICHT, AUSGEZAHLT, PATCH",
            "GEPRUEFT_FREIGABE, AUSGEZAHLT, PATCH",
            "GENEHMIGT, EINGEREICHT, PATCH",
            "GENEHMIGT, ABGELEHNT, PATCH",
    })
    void forbiddenTransitions(Schadensfallstatus von, Schadensfallstatus nach, StatusTransition.Route weg) {
        var result = StatusTransition.check(von, nach, weg);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void the_same_status_is_no_transition_but_no_error_either() {
        var result = StatusTransition.check(EINGEREICHT, EINGEREICHT, PATCH);
        assertThat(result.allowed()).isTrue();
        assertThat(result.akteur()).isNull();
    }

    @Test
    void without_a_named_akteur_the_default_of_the_edge_applies() {
        assertThat(StatusTransition.check(IN_PRUEFUNG, EINGEREICHT, PATCH, null).akteur())
                .isEqualTo(Protokollakteur.SYSTEM);
    }

    @Test
    void the_named_akteur_beats_the_default_of_the_edge() {
        var result = StatusTransition.check(IN_PRUEFUNG, EINGEREICHT, PATCH,
                Protokollakteur.SACHBEARBEITUNG);
        assertThat(result.allowed()).isTrue();
        assertThat(result.akteur()).isEqualTo(Protokollakteur.SACHBEARBEITUNG);
    }

    @Test
    void a_named_akteur_does_not_allow_a_forbidden_transition() {
        var result = StatusTransition.check(AUSGEZAHLT, EINGEREICHT, PATCH,
                Protokollakteur.SACHBEARBEITUNG);
        assertThat(result.allowed()).isFalse();
        assertThat(result.akteur()).isNull();
    }

    @Test
    void without_a_transition_there_is_no_protokolleintrag_even_with_a_named_akteur() {
        var result = StatusTransition.check(EINGEREICHT, EINGEREICHT, PATCH,
                Protokollakteur.SACHBEARBEITUNG);
        assertThat(result.allowed()).isTrue();
        assertThat(result.akteur()).isNull();
    }

    @Test
    void the_bewertung_stays_the_action_of_the_agent() {
        assertThat(StatusTransition.check(IN_PRUEFUNG, GEPRUEFT_FREIGABE, BEWERTUNG, null).akteur())
                .isEqualTo(Protokollakteur.AGENT);
    }
}
