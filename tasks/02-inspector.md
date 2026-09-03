# 02 — Den Wissensdienst im Inspector öffnen

**Dauer:** 15 min
**Werkzeug:** Terminal
**Voraussetzung:** Lab läuft
**Ziel:** Ihr seht die Werkzeuge des Wissensdienstes so, wie ein Modell sie sieht.
**Ergebnis:** Ihr habt ein Werkzeug von Hand aufgerufen und wisst, was in seiner Beschreibung steht.

Der MCP-Inspector ist für MCP, was ein REST-Client für HTTP ist: ein Fenster
auf die Schnittstelle, ohne Modell dazwischen. Er wird nicht installiert,
sondern einmalig ausgeführt.

> **Der Wissensdienst spricht die ältere Protokollfassung, die mit `initialize`.**
> Im Theorieteil hieß es, der Handshake sei seit dem 28.07.2026 weg — beides
> stimmt. Draußen trefft ihr beide Stände an, und für diese Übung ist der ältere
> der lehrreichere: Ihr seht, was beim Verbinden ausgehandelt wird.

## Schritte

1. Startet den Inspector im Terminal und öffnet die Adresse, die er ausgibt.
2. Wählt als Transport Streamable HTTP und tragt die Adresse des
   Wissensdienstes samt Endpunktpfad ein.
3. Lasst euch die Werkzeugliste zeigen und lest **eine** Beschreibung
   vollständig durch.
4. Ruft `bedingungen_suchen` mit einer Frage zur Wartezeit auf.
5. Ruft `beratungsleitfaden_suchen` mit derselben Frage auf und vergleicht.

## Prompt

Der Startbefehl — die Version steht fest, und das aus zwei Gründen: Ältere
Fassungen des Inspectors haben eine bekannte Schwachstelle, und `@latest` holt
an jedem Schulungstag etwas anderes:

```bash
npx @modelcontextprotocol/inspector@2.3.0
```

Die Suchfrage für beide Aufrufe:

```
Wie lange ist die Wartezeit für Zahnersatz?
```

## Troubleshooting

- **Verbindung abgelehnt.** Der Wissensdienst hört auf 8082, der Endpunkt ist
  `/mcp`. Ohne den Pfad verbindet der Inspector nicht.
- **Leere Werkzeugliste.** Prüft im Terminal, ob der Dienst wirklich bereit
  gemeldet hat — er braucht beim Start am längsten.

## Diskussion

Die Beschreibung von `beratungsleitfaden_suchen` beginnt mit einer Warnung, dass
der Inhalt nicht vor den Kunden gehört. Diese Warnung steht in einem Textfeld,
das ein Modell liest — sie ist keine Zugriffskontrolle. Wer verhindert, dass sie
ignoriert wird?
