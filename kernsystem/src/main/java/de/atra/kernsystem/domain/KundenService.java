package de.atra.kernsystem.domain;

import de.atra.kernsystem.generated.model.Kunde;
import de.atra.kernsystem.generated.model.KundeAendern;
import de.atra.kernsystem.generated.model.KundeSchreiben;
import de.atra.kernsystem.generated.model.Kundenstatus;
import de.atra.kernsystem.persistence.KundenRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class KundenService {

    private final KundenRepository repository;

    public KundenService(KundenRepository repository) {
        this.repository = repository;
    }

    public List<Kunde> search(String vorname, String nachname,
                              LocalDate geburtsdatum, Kundenstatus status) {
        return repository.all().stream()
                .filter(k -> vorname == null || vorname.equals(k.getVorname()))
                .filter(k -> nachname == null || nachname.equals(k.getNachname()))
                .filter(k -> geburtsdatum == null || geburtsdatum.equals(k.getGeburtsdatum()))
                .filter(k -> status == null || status.equals(k.getStatus()))
                .toList();
    }

    public Kunde read(long id) {
        return repository.find(id).orElseThrow(() -> new NotFoundException("Kunde", id));
    }

    public Kunde create(KundeSchreiben input) {
        OffsetDateTime now = OffsetDateTime.now();
        return repository.add(id -> applyTo(new Kunde().id(id).erstelltAm(now), input,
                Kundenstatus.AKTIV).geaendertAm(now));
    }

    public Kunde replace(long id, KundeSchreiben input) {
        Kunde existing = read(id);
        Kunde replacement = applyTo(
                new Kunde().id(id).erstelltAm(existing.getErstelltAm()), input,
                Objects.requireNonNullElse(existing.getStatus(), Kundenstatus.AKTIV))
                .geaendertAm(OffsetDateTime.now());
        return repository.replace(id, replacement);
    }

    public Kunde update(long id, KundeAendern input) {
        Kunde existing = read(id);

        Optional.ofNullable(input.getVorname()).ifPresent(existing::setVorname);
        Optional.ofNullable(input.getNachname()).ifPresent(existing::setNachname);
        Optional.ofNullable(input.getGeburtsdatum()).ifPresent(existing::setGeburtsdatum);
        Optional.ofNullable(input.getEmail()).ifPresent(existing::setEmail);
        Optional.ofNullable(input.getTelefon()).ifPresent(existing::setTelefon);
        Optional.ofNullable(input.getAdresse()).ifPresent(existing::setAdresse);
        Optional.ofNullable(input.getTarifId()).ifPresent(existing::setTarifId);
        Optional.ofNullable(input.getVersicherungsbeginn())
                .ifPresent(existing::setVersicherungsbeginn);
        Optional.ofNullable(input.getVorversicherung()).ifPresent(existing::setVorversicherung);
        Optional.ofNullable(input.getFehlendeZaehne()).ifPresent(existing::setFehlendeZaehne);
        Optional.ofNullable(input.getStatus()).ifPresent(existing::setStatus);
        existing.setGeaendertAm(OffsetDateTime.now());

        return repository.replace(id, existing);
    }

    private Kunde applyTo(Kunde target, KundeSchreiben source, Kundenstatus fallback) {
        return target
                .vorname(source.getVorname())
                .nachname(source.getNachname())
                .geburtsdatum(source.getGeburtsdatum())
                .email(source.getEmail())
                .telefon(source.getTelefon())
                .adresse(source.getAdresse())
                .tarifId(source.getTarifId())
                .versicherungsbeginn(source.getVersicherungsbeginn())
                .vorversicherung(source.getVorversicherung())
                .fehlendeZaehne(source.getFehlendeZaehne())
                .status(source.getStatus() == null ? fallback : source.getStatus());
    }
}
