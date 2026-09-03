# 06 — Einen Agenten vorsetzen und bekannt machen

**Dauer:** 30 min
**Werkzeug:** Claude Code
**Voraussetzung:** Aufgabe 05 abgeschlossen
**Ziel:** Vor dem Kernsystem steht ein eigener Agent, der Schadensfälle entgegennimmt und sich selbst beschreibt.
**Ergebnis:** Eine Agent Card unter dem Well-Known-Pfad und ein Agent, der auf eine A2A-Nachricht antwortet. **Mindestens:** die abrufbare Karte — ohne sie läuft Übung 07 nicht.

Das Gerüst ist gestellt: Maven-Modul, Beans und die beiden Controller liegen
fertig im Übungszweig. Ihr füllt die zwei Stellen, an denen die Fachlichkeit
sitzt — die Karte, mit der der Agent sich vorstellt, und den Prompt, nach dem er
arbeitet.

## Schritte

1. Schreibt die Agent Card: Name, Beschreibung und genau eine Fertigkeit.
2. Beschreibt die Fertigkeit so, dass ein fremder Orchestrator erkennt, wann er
   sie braucht — und wann nicht.
3. Schreibt den Systemprompt und tragt ein, welche MCP-Werkzeuge der Agent
   nutzen darf.
4. Startet den Agenten und ruft seine Karte im Browser ab.
5. Schickt ihm eine A2A-Nachricht und lest die Antwort.

## Prompt

**1 — Die Karte.** Sie liegt als YAML unter `agenten/cards/`. Nehmt die
vorhandenen Karten als Muster, aber schreibt die Beschreibung selbst.

**2 — Die Werkzeugauswahl.** Sie ist eine Positivliste und wird beim Start hart
geprüft:

```
In agenten/<euer-modul>/ liegt das Gerüst. Trägt in die Werkzeugauswahl genau
die MCP-Werkzeuge ein, die der Agent für das Einreichen eines Schadensfalls
braucht — nicht mehr.
```

**3 — Die Probe.** Der Well-Known-Pfad ist derselbe wie bei den vorhandenen
Agenten; schaut euch dort die Adresse ab.

## Troubleshooting

- **Der Agent startet nicht.** Die Werkzeugauswahl ist eine Positivliste: Ein
  Name, den es nicht gibt, bricht den Start ab — mit Absicht.
- **Die Karte ist leer oder unvollständig.** Unbekannte Felder werden still
  verworfen. Vergleicht Feld für Feld mit einer vorhandenen Karte.
- **Kein Modellschlüssel.** Agenten brauchen ihn, die Fachdienste nicht.

## Diskussion

Ihr habt keine Schnittstellenbeschreibung im Sinne von OpenAPI geschrieben,
sondern einen Text in Prosa. Was ist daran besser, was schlechter? Und wer
prüft, dass die Karte stimmt?
