package consulting.atra.wissen.api;

import consulting.atra.wissen.api.generated.model.Dokument;
import consulting.atra.wissen.api.generated.model.Dokumentart;
import consulting.atra.wissen.api.generated.model.Fundstelle;
import consulting.atra.wissen.api.generated.model.GozBefund;
import consulting.atra.wissen.api.generated.model.Gozstatus;
import consulting.atra.wissen.api.generated.model.Leistungsbereich;
import consulting.atra.wissen.api.generated.model.Leistungsgrenzen;
import consulting.atra.wissen.api.generated.model.Tarifschluessel;
import consulting.atra.wissen.api.generated.model.Vertraulichkeit;
import consulting.atra.wissen.search.Hit;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Mapping {

    private Mapping() {
    }

    public static Dokument toApi(consulting.atra.wissen.documents.CatalogDocument document) {
        Dokument outer = new Dokument(
                document.dokumentId(),
                document.title(),
                Dokumentart.fromValue(document.art().name()),
                toApi(document.vertraulichkeit()),
                document.pdfUrl(),
                document.htmlUrl());
        document.tarif().map(Tarifschluessel::fromValue).ifPresent(outer::setTarif);
        return outer;
    }

    public static Vertraulichkeit toApi(
            consulting.atra.wissen.documents.Confidentiality confidentiality) {
        return Vertraulichkeit.fromValue(confidentiality.name());
    }

    public static consulting.atra.wissen.documents.Confidentiality toInternal(
            Vertraulichkeit vertraulichkeit) {
        return consulting.atra.wissen.documents.Confidentiality.valueOf(vertraulichkeit.getValue());
    }

    public static Fundstelle toApi(Hit hit) {
        return new Fundstelle(
                hit.dokumentId(),
                hit.abschnittId(),
                hit.ueberschrift(),
                hit.text(),
                hit.bewertung(),
                hit.htmlUrl(),
                hit.pdfUrl());
    }

    public static GozBefund toApi(consulting.atra.wissen.goz.GozBefund befund) {
        GozBefund outer = new GozBefund(
                befund.nummer(),
                Gozstatus.fromValue(befund.status().name()),
                befund.begruendung());
        outer.setBezeichnung(befund.bezeichnung());
        outer.setAbschnitt(befund.abschnitt());
        if (befund.leistungsbereich() != null) {
            outer.setLeistungsbereich(Leistungsbereich.fromValue(befund.leistungsbereich()));
        }
        outer.setQuote(befund.quote());
        if (befund.grenzen() != null) {
            outer.setGrenzen(toApi(befund.grenzen()));
        }
        return outer;
    }

    public static Leistungsgrenzen toApi(consulting.atra.wissen.goz.Leistungsgrenzen grenzen) {
        return new Leistungsgrenzen()
                .limitProJahr(geldbetrag(grenzen.limitProJahr()))
                .limitGesamt(geldbetrag(grenzen.limitGesamt()))
                .faelleProJahr(grenzen.faelleProJahr())
                .maxFaelle(grenzen.maxFaelle())
                .zeitraumJahre(grenzen.zeitraumJahre())
                .wartezeitMonate(grenzen.wartezeitMonate())
                .bedingung(grenzen.bedingung());
    }

    private static BigDecimal geldbetrag(BigDecimal betrag) {
        return betrag == null ? null : betrag.setScale(2, RoundingMode.HALF_UP);
    }
}
