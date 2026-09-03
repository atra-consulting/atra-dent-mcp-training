package de.atra.kernsystem.domain;

import de.atra.kernsystem.generated.model.Bewertung;
import de.atra.kernsystem.generated.model.Bewertungsempfehlung;
import de.atra.kernsystem.generated.model.Bewertungsposition;
import de.atra.kernsystem.generated.model.Protokollakteur;
import de.atra.kernsystem.generated.model.Protokolleintrag;
import de.atra.kernsystem.generated.model.Schaden;
import de.atra.kernsystem.generated.model.Schadenposition;
import de.atra.kernsystem.generated.model.Schadensfall;
import de.atra.kernsystem.generated.model.SchadensfallAendern;
import de.atra.kernsystem.generated.model.SchadensfallBewerten;
import de.atra.kernsystem.generated.model.Schadensfallstatus;
import de.atra.kernsystem.persistence.SchadensfaelleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class SchadensfallService {

    private final SchadensfaelleRepository repository;
    private final KundenService kunden;

    public SchadensfallService(SchadensfaelleRepository repository, KundenService kunden) {
        this.repository = repository;
        this.kunden = kunden;
    }

    public List<Schadensfall> search(Long kundenId, List<Schadensfallstatus> status) {
        boolean withoutStatusFilter = status == null || status.isEmpty();
        return repository.all().stream()
                .filter(f -> kundenId == null || kundenId.equals(f.getKundenId()))
                .filter(f -> withoutStatusFilter || status.contains(f.getStatus()))
                .map(SchadensfallService::withProtokoll)
                .toList();
    }

    public List<Schadensfall> history(long kundenId) {
        kunden.read(kundenId);
        return search(kundenId, List.of());
    }

    public List<Schadensfall> history(long kundenId, List<Schadensfallstatus> status) {
        kunden.read(kundenId);
        return search(kundenId, status);
    }

    public Schadensfall read(long id) {
        return repository.find(id).map(SchadensfallService::withProtokoll)
                .orElseThrow(() -> new NotFoundException("Schadensfall", id));
    }

    public Schadensfall readFor(long kundenId, long id) {
        Schadensfall schadensfall = read(id);
        if (!Long.valueOf(kundenId).equals(schadensfall.getKundenId())) {
            throw new NotFoundException("Schadensfall", id);
        }
        return schadensfall;
    }

    public Schadensfall submit(Schaden input, Protokollakteur akteur) {
        try {
            kunden.read(input.getKundenId());
        } catch (NotFoundException _) {
            throw new DomainException(
                    "Feld 'kundenId' verweist auf keinen vorhandenen Kunden: " + input.getKundenId());
        }

        for (int i = 0; i < input.getPositionen().size(); i++) {
            checkGeldbetrag(input.getPositionen().get(i).getBetrag(),
                    "positionen[" + i + "].betrag");
        }
        if (input.getRechnung() != null) {
            checkGeldbetrag(input.getRechnung().getGesamtbetrag(), "rechnung.gesamtbetrag");
        }

        BigDecimal rechnungTotal = input.getPositionen().stream()
                .map(Schadenposition::getBetrag)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return repository.add(id -> new Schadensfall()
                .id(id)
                .kundenId(input.getKundenId())
                .behandlungsdatum(input.getBehandlungsdatum())
                .positionen(input.getPositionen())
                .rechnung(input.getRechnung())
                .rechnungsbetrag(rechnungTotal)
                .status(Schadensfallstatus.EINGEREICHT)
                .eingereichtAm(OffsetDateTime.now())
                .bearbeitungsprotokoll(new ArrayList<>(List.of(
                        protokolleintrag(akteur, "Schadensfall eingereicht",
                                input.getPositionen().size() + " Positionen, "
                                        + rechnungTotal.toPlainString() + " EUR")))));
    }

    public Schadensfall update(long id, SchadensfallAendern input) {
        checkGeldbetrag(input.getErstattungsbetrag(), "erstattungsbetrag");
        try {
            return withProtokoll(repository.update(id, stored -> {
                Schadensfall schadensfall = withProtokoll(stored);
                if (input.getErwarteterStatus() != null
                        && input.getErwarteterStatus() != schadensfall.getStatus()) {
                    throw new ConflictException("Fall steht auf "
                            + schadensfall.getStatus().getValue()
                            + ", erwartet war " + input.getErwarteterStatus().getValue());
                }
                if (input.getStatus() != null) {
                    var transition = StatusTransition.check(schadensfall.getStatus(),
                            input.getStatus(), StatusTransition.Route.PATCH, input.getAkteur());
                    if (!transition.allowed()) {
                        throw new ConflictException(transition.reason());
                    }
                    if (transition.akteur() != null) {
                        String detail = input.getStatus() == Schadensfallstatus.ABGELEHNT
                                ? input.getAblehnungshinweis() : null;
                        schadensfall.getBearbeitungsprotokoll().add(
                                protokolleintrag(transition.akteur(),
                                        "Status " + schadensfall.getStatus().getValue() + " -> "
                                                + input.getStatus().getValue(),
                                        detail));
                        schadensfall.setStatus(input.getStatus());
                    }
                }
                Optional.ofNullable(input.getErstattungsbetrag())
                        .ifPresent(schadensfall::setErstattungsbetrag);
                Optional.ofNullable(input.getAblehnungsgrund())
                        .ifPresent(schadensfall::setAblehnungsgrund);
                Optional.ofNullable(input.getAblehnungshinweis())
                        .ifPresent(schadensfall::setAblehnungshinweis);
                return schadensfall;
            }));
        } catch (NoSuchElementException _) {
            throw new NotFoundException("Schadensfall", id);
        }
    }

    public Schadensfall assess(long id, SchadensfallBewerten input) {
        Bewertung bewertung = input.getBewertung();
        if (bewertung == null) {
            throw new DomainException("Feld 'bewertung' ist Pflicht");
        }
        checkGeldbetrag(bewertung.getErstattungsvorschlag(), "bewertung.erstattungsvorschlag");
        if (bewertung.getEmpfehlung() == Bewertungsempfehlung.ESKALATION
                && (bewertung.getEskalationsgruende() == null
                        || bewertung.getEskalationsgruende().isEmpty())) {
            throw new DomainException("Eine Eskalation braucht mindestens einen Eskalationsgrund");
        }
        if (bewertung.getEmpfehlung() == Bewertungsempfehlung.FREIGABE
                && bewertung.getErstattungsvorschlag() == null) {
            throw new DomainException("Eine Freigabe braucht einen Erstattungsvorschlag");
        }
        Schadensfallstatus target = bewertung.getEmpfehlung() == Bewertungsempfehlung.FREIGABE
                ? Schadensfallstatus.GEPRUEFT_FREIGABE
                : Schadensfallstatus.GEPRUEFT_ESKALATION;

        try {
            return withProtokoll(repository.update(id, stored -> {
                Schadensfall schadensfall = withProtokoll(stored);
                var transition = StatusTransition.check(schadensfall.getStatus(), target,
                        StatusTransition.Route.BEWERTUNG);
                if (!transition.allowed()) {
                    throw new ConflictException(transition.reason());
                }
                fillInLeistungsbereiche(schadensfall, bewertung.getPositionen());
                bewertung.setZeitpunkt(OffsetDateTime.now());
                schadensfall.setBewertung(bewertung);
                if (input.getProtokoll() != null) {
                    schadensfall.getBearbeitungsprotokoll().addAll(input.getProtokoll());
                }
                schadensfall.getBearbeitungsprotokoll().add(
                        protokolleintrag(transition.akteur(),
                                "Status " + schadensfall.getStatus().getValue() + " -> "
                                        + target.getValue(),
                                bewertung.getEmpfehlung().getValue()
                                        + (bewertung.getErstattungsvorschlag() == null ? ""
                                        : ", Vorschlag "
                                                + bewertung.getErstattungsvorschlag()
                                                        .toPlainString() + " EUR")));
                schadensfall.setStatus(target);
                return schadensfall;
            }));
        } catch (NoSuchElementException cause) {
            throw new NotFoundException("Schadensfall", id);
        }
    }

    private static void fillInLeistungsbereiche(Schadensfall schadensfall,
                                                List<Bewertungsposition> assessed) {
        if (assessed == null) {
            return;
        }
        List<Schadenposition> positionen = schadensfall.getPositionen();
        for (int i = 0; i < assessed.size(); i++) {
            Integer index = assessed.get(i).getIndex();
            if (index != null && (index < 0 || index >= positionen.size())) {
                throw new DomainException("bewertung.positionen[" + i + "].index " + index
                        + " zeigt auf keine Position des Schadensfalls; der Fall hat "
                        + positionen.size() + " Positionen");
            }
        }
        for (Bewertungsposition finding : assessed) {
            Schadenposition target = targetPosition(positionen, finding);
            if (target != null && finding.getLeistungsbereich() != null
                    && target.getLeistungsbereich() == null) {
                target.setLeistungsbereich(finding.getLeistungsbereich());
            }
        }
    }

    private static Schadenposition targetPosition(List<Schadenposition> positionen,
                                                  Bewertungsposition finding) {
        if (finding.getIndex() != null) {
            return positionen.get(finding.getIndex());
        }
        if (finding.getGoz() == null) {
            return null;
        }
        return positionen.stream()
                .filter(position -> finding.getGoz().equals(position.getGoz())
                        && position.getLeistungsbereich() == null)
                .findFirst()
                .orElse(null);
    }

    public static Protokolleintrag protokolleintrag(Protokollakteur akteur, String schritt,
                                                    String detail) {
        return new Protokolleintrag()
                .zeitpunkt(OffsetDateTime.now())
                .akteur(akteur)
                .schritt(schritt)
                .detail(detail);
    }

    static Schadensfall withProtokoll(Schadensfall schadensfall) {
        if (schadensfall.getBearbeitungsprotokoll() == null) {
            schadensfall.setBearbeitungsprotokoll(new ArrayList<>());
        }
        return schadensfall;
    }

    private void checkGeldbetrag(BigDecimal betrag, String field) {
        if (betrag != null && betrag.stripTrailingZeros().scale() > 2) {
            throw new DomainException("Feld '" + field
                    + "' hat mehr als zwei Nachkommastellen: " + betrag.toPlainString());
        }
    }
}
