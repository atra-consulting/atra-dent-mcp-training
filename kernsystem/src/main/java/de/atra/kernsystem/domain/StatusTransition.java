package de.atra.kernsystem.domain;

import de.atra.kernsystem.generated.model.Protokollakteur;
import de.atra.kernsystem.generated.model.Schadensfallstatus;

import java.util.Map;
import java.util.Set;

import static de.atra.kernsystem.generated.model.Schadensfallstatus.*;

public final class StatusTransition {

    public enum Route { PATCH, BEWERTUNG }

    public record Result(boolean allowed, Protokollakteur akteur, String reason) {

        static Result yes(Protokollakteur akteur) {
            return new Result(true, akteur, null);
        }

        static Result no(String reason) {
            return new Result(false, null, reason);
        }
    }

    private record Edge(Schadensfallstatus from, Schadensfallstatus to, Route route) {
    }

    private static final Map<Edge, Protokollakteur> EDGES = Map.ofEntries(
            Map.entry(new Edge(EINGEREICHT, IN_PRUEFUNG, Route.PATCH), Protokollakteur.AGENT),
            Map.entry(new Edge(IN_PRUEFUNG, GEPRUEFT_FREIGABE, Route.BEWERTUNG), Protokollakteur.AGENT),
            Map.entry(new Edge(IN_PRUEFUNG, GEPRUEFT_ESKALATION, Route.BEWERTUNG), Protokollakteur.AGENT),
            Map.entry(new Edge(IN_PRUEFUNG, EINGEREICHT, Route.PATCH), Protokollakteur.SYSTEM),
            Map.entry(new Edge(GEPRUEFT_FREIGABE, EINGEREICHT, Route.PATCH), Protokollakteur.SACHBEARBEITUNG),
            Map.entry(new Edge(GEPRUEFT_ESKALATION, EINGEREICHT, Route.PATCH), Protokollakteur.SACHBEARBEITUNG),
            Map.entry(new Edge(EINGEREICHT, GENEHMIGT, Route.PATCH), Protokollakteur.SACHBEARBEITUNG),
            Map.entry(new Edge(EINGEREICHT, ABGELEHNT, Route.PATCH), Protokollakteur.SACHBEARBEITUNG),
            Map.entry(new Edge(IN_PRUEFUNG, GENEHMIGT, Route.PATCH), Protokollakteur.SACHBEARBEITUNG),
            Map.entry(new Edge(IN_PRUEFUNG, ABGELEHNT, Route.PATCH), Protokollakteur.SACHBEARBEITUNG),
            Map.entry(new Edge(GEPRUEFT_FREIGABE, GENEHMIGT, Route.PATCH), Protokollakteur.SACHBEARBEITUNG),
            Map.entry(new Edge(GEPRUEFT_FREIGABE, ABGELEHNT, Route.PATCH), Protokollakteur.SACHBEARBEITUNG),
            Map.entry(new Edge(GEPRUEFT_ESKALATION, GENEHMIGT, Route.PATCH), Protokollakteur.SACHBEARBEITUNG),
            Map.entry(new Edge(GEPRUEFT_ESKALATION, ABGELEHNT, Route.PATCH), Protokollakteur.SACHBEARBEITUNG),
            Map.entry(new Edge(GENEHMIGT, AUSGEZAHLT, Route.PATCH), Protokollakteur.SACHBEARBEITUNG));

    private static final Set<Schadensfallstatus> FINAL = Set.of(ABGELEHNT, AUSGEZAHLT);

    private StatusTransition() {
    }

    public static Result check(Schadensfallstatus from, Schadensfallstatus to, Route route) {
        return check(from, to, route, null);
    }

    public static Result check(Schadensfallstatus from, Schadensfallstatus to, Route route,
                               Protokollakteur namedAkteur) {
        if (from == to && route == Route.PATCH) {
            return Result.yes(null);
        }
        Protokollakteur defaultActor = EDGES.get(new Edge(from, to, route));
        if (defaultActor != null) {
            return Result.yes(namedAkteur != null ? namedAkteur : defaultActor);
        }
        if (FINAL.contains(from)) {
            return Result.no("Status " + from.getValue() + " ist ein Endzustand");
        }
        if (route == Route.PATCH && (to == GEPRUEFT_FREIGABE || to == GEPRUEFT_ESKALATION)) {
            return Result.no("Status " + to.getValue()
                    + " wird nur ueber die Bewertung gesetzt (PUT /schadensfaelle/{id}/bewertung)");
        }
        if (route == Route.BEWERTUNG) {
            if (to != GEPRUEFT_FREIGABE && to != GEPRUEFT_ESKALATION) {
                return Result.no("Die Bewertung setzt nur geprueft_freigabe oder geprueft_eskalation");
            }
            return Result.no("Eine Bewertung ist nur aus in_pruefung moeglich, der Fall steht auf "
                    + from.getValue());
        }
        return Result.no("Uebergang von " + from.getValue() + " nach " + to.getValue()
                + " ist nicht erlaubt");
    }
}
