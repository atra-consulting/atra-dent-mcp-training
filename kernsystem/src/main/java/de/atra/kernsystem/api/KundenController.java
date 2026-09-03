package de.atra.kernsystem.api;

import de.atra.kernsystem.domain.KundenService;
import de.atra.kernsystem.generated.api.KundenApi;
import de.atra.kernsystem.generated.model.*;
import de.atra.kernsystem.mcp.Mandant;
import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
public class KundenController implements KundenApi {

    private static final String EMAIL = "Neue E-Mail-Adresse.";
    private static final String PHONE = "Neue Telefonnummer, etwa \"+49 30 1234567\".";
    private static final String STREET = "Straße und Hausnummer der neuen Anschrift.";
    private static final String POSTAL_CODE = "Postleitzahl der neuen Anschrift, als String (\"10115\").";
    private static final String ORT = "Ort der neuen Anschrift.";
    private static final String LAND = "Land der neuen Anschrift als Länderkürzel, etwa \"DE\".";

    private static final String DESCRIPTION_CONTACT = """
            Ändert die Kontaktdaten der eigenen Akte: E-Mail, Telefonnummer, \
            Anschrift. Es wird nur geändert, was angegeben ist; was fehlt, \
            bleibt stehen.
            
            Die vier Adressfelder gehören zusammen und sind nur gemeinsam \
            änderbar — eine Anschrift aus alter Straße und neuem Ort wäre \
            keine Anschrift. Wer umzieht, gibt alle vier an, auch die, die \
            gleich bleiben.
            
            Nur Kontaktdaten. Name, Geburtsdatum, Angabe zur Vorversicherung \
            und fehlende Zähne sind Antragsangaben, an denen Beitrag, \
            Wartezeit und Ausschlüsse hängen; sie lassen sich über dieses \
            Tool nicht ändern und über kein anderes. Eine Korrektur daran \
            läuft über die Sachbearbeitung. Für den Tarifwechsel gibt es \
            meinen_tarif_wechseln.""";

    private final KundenService service;

    public KundenController(KundenService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<Kunde>> listKunden(String vorname, String nachname,
                                                  LocalDate geburtsdatum, Kundenstatus status) {
        return ResponseEntity.ok(service.search(vorname, nachname, geburtsdatum, status));
    }

    @Override
    public ResponseEntity<Kunde> createKunde(KundeSchreiben kundeSchreiben) {
        Kunde created = service.create(kundeSchreiben);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Override
    public ResponseEntity<Kunde> readKunde(Long kundenId) {
        return ResponseEntity.ok(service.read(kundenId));
    }

    @Override
    public ResponseEntity<Kunde> replaceKunde(Long kundenId, KundeSchreiben kundeSchreiben) {
        return ResponseEntity.ok(service.replace(kundenId, kundeSchreiben));
    }

    //TODO Workshop

    // Update Kunde (Rest)
    @Override
    public ResponseEntity<Kunde> updateKunde(Long kundenId, KundeAendern kundeAendern) {
        return ResponseEntity.ok(doUpdate(kundenId, kundeAendern));
    }

    // Update Kunde (MCP)
    public Kunde updateKundeMcp(
            McpTransportContext context,
            String email,
            String telefon,
            String strasse,
            String plz,
            String ort,
            String land) {
        return null;
    }

    private Kunde doUpdate(Long kundenId, KundeAendern kundeAendern) {
        return service.update(kundenId, kundeAendern);
    }
}
