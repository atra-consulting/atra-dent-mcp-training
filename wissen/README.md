# Wissensdienst atra.dent

## Purpose

The Wissensdienst makes the insurance conditions machine-readable. It answers five questions:

- **What do the conditions say?** — semantic search across the paragraphs of a Bedingungswerk, with a citable Fundstelle
- **Is this Leistung covered by the Tarif?** — checking GOZ-Gebuehrennummern against a Tarif
- **How do the Tarife differ?** — several Tarife from the product model set side by side, row by row
- **How do I steer the conversation?** — semantic search in the internal Beratungshandbuch
- **Which Tarif fits this situation?** — a recommendation derived from rules, with its reasoning

Only the first two answers are citable. The last two are internal steering knowledge and are never put in front of a customer at all; why that difference reaches all the way into the construction is described under [Two kinds of knowledge](#two-kinds-of-knowledge). The comparison in between is a third case: it may be shown to a customer unchanged — it is the same product model the Bedingungswerke are generated from — but it **proves** nothing. A Quote taken from it is a statement, not a Fundstelle. That is why every Tarif in the result carries the id of its Bedingungswerk: the reference is where to go and read it.

On top of that the service delivers the documents themselves: as PDF for a human to look at, as HTML for further processing.

It knows nothing of Kunden, Vertraege or Beitraege. Kunden and Vertraege belong to the Kernsystem (`oas/openapi.yaml`), the Beitrag to the Rechenkern (`oas/rechenkern.yaml`). A Beratungsagent combines all three: the Kernsystem says what a Kunde has, the Rechenkern works out what it costs, the Wissensdienst says what it means. Bringing those together is exactly the exercise the lab is about.

## Layout

| Directory | Contents |
|---|---|
| `dokumente/` | Typst sources of the Bedingungswerke, the Tarifvergleich, the GOZ mapping and the Beratungshandbuch |
| `daten/` | `tarife.yaml` (product model), `goz-zuordnung.yaml` (215 Gebuehrennummern and the rule for Positionen with no number at all) and `goae-auszug.yaml` (the few GOÄ numbers that occur in the lab) |
| `daten/beratung/` | `leitfaden.yaml` — the machine-readable rule part of the Beratung |
| `generated/` | PDF, HTML and vector indexes (`index/`), produced by `build.sh` and checked in; `stand.txt` carries the checksum of the sources |
| `dienst/` | Spring Boot application with REST and MCP |

The authoritative REST contract is `oas/wissen.yaml` at the repository root, next to the Kernsystem's. Interfaces and DTOs are generated from it; the controllers implement them.

## Interfaces

Both protocols are thin adapters over the same domain logic — the controllers and the tool classes hold no domain logic, they pass through to the same services.

| MCP tool | REST operation |
|---|---|
| `bedingungen_suchen` | `POST /api/v1/bedingungen/suche` |
| `goz_pruefen` | `POST /api/v1/goz/pruefung` |
| `tarife_vergleichen` | — |
| `beratungsleitfaden_suchen` | — |
| `tarifempfehlung` | — |
| — | `GET /api/v1/dokumente` and delivery as PDF and HTML |

The MCP server runs over Streamable HTTP at `/mcp`, stateless: no
`Mcp-Session-Id`, every request stands on its own. None of the five tools
carries a Kundenbezug, so none of them reads a header or needs one.

**The two Beratung tools have no REST counterpart on purpose.** `oas/wissen.yaml` is the contract for answering out of the published conditions; internal sales steering does not belong in it. Over MCP it becomes available to an agent that is on the adviser's side anyway — an open HTTP endpoint, by contrast, would be exactly the route by which steering knowledge ends up with the customer. For the same reason `bedingungen_suchen` requires a Tarif and `beratungsleitfaden_suchen` does not: the Bedingungswerke contradict one another from Tarif to Tarif, while the handbook applies to all four Tarife alike.

**`tarife_vergleichen` has no REST counterpart for a different reason.** It is not confidential — a REST caller has had the Tarifvergleich as a document all along (`atra-dent-tarifvergleich`, as PDF and as HTML). The tool is the machine-readable form of the same material, meant for a model that should not have to work through ten pages of prose to put two Quoten side by side. Consistently, it does not require a Tarif: with none given, all four come back, since a comparison is precisely the case where more than one Tarif is meant.

The document list takes a `vertraulichkeit` filter. Without it, `GET /api/v1/dokumente` returns all six documents; `?vertraulichkeit=OEFFENTLICH` returns the five consumer documents. The level is a mandatory field on every document — there is no document without a confidentiality level, because otherwise leaving it out would amount to silent clearance.

```bash
curl -X POST http://localhost:8082/api/v1/goz/pruefung \
  -H 'Content-Type: application/json' \
  -d '{"tarif":"ATRA_DENT_B","nummern":["9010","1040","0010","9999"]}'
```

## Two kinds of knowledge

The service holds six documents. Five of them are published consumer documents — the three Bedingungswerke, the Tarifvergleich and the GOZ mapping. They are contract text or explanation of it, and what is quoted from them holds towards the customer.

The sixth is the **Beratungshandbuch** (`atra-dent-beratungshandbuch`, type `BERATUNGSHANDBUCH`, confidentiality `INTERN`) — 51 sections in three chapters on conversation, needs analysis and handling objections. It steers the adviser. It is not shown to the customer and not quoted, and where it contradicts a Bedingungswerk, the Bedingungswerk wins without exception. It therefore carries the marking in the document itself as well: as a note on the title page, and as a footer line on every page after it.

**The two search spaces are separate by construction, not by filter.** There are two vector indexes: `BedingungenSearch` indexes what the catalogue lists as `OEFFENTLICH`, `BeratungSearch` what it lists as `INTERN`. Neither can return a passage from the other, because it does not hold one.

A single index with a filter applied afterwards would be the obvious construction and the wrong one. "Wartezeit", "Selbstbehalt" and "fehlende Zaehne" appear in both bodies of material — to the same question the handbook has an answer for the adviser and the Bedingungswerk one for the contract. A filter that someone forgets or sets wrongly just once then hands out a sales instruction as a contract condition. Nobody notices, because the answer looks plausible. That exact mistake did happen while the search was being wired up, and two tests reported it before the separation was in place; `SearchSpaceSeparationTest` puts the same ambiguous questions to both searches and checks that no answer crosses the line.

For the same reason the hits from `BeratungSearch` carry no links to PDF and HTML, the way hits from `BedingungenSearch` do: those exist to prove a point to the customer, and proving anything is precisely what this material must not do.

## The recommendation is computed, not searched

`tarifempfehlung` does not search, it derives: from the rules in `daten/beratung/leitfaden.yaml` and the values in `daten/tarife.yaml`. The same input always produces the same answer, and every ranking carries its reasoning.

**Need comes first.** The `EmpfehlungOrder` from the Leitfaden — brillant, brillant with Selbstbehalt, balance, smart — applies only where suitability is equal. It settles the tie, not the case: as soon as one piece of information tells the remaining Tarife apart on the merits, need does the ordering, even against the order. The result says in the field `weg` which rule carried it (`BEDARF`, `REIHENFOLGE`, `KEIN_TARIF`), and puts `rang` next to `reihenfolgeplatz` so that it stays visible where the two diverge. An order that still ranked when need said otherwise would be a sales instruction, not Beratung.

What differs is also kept apart: `ausgeschlossene` are Tarife that fail a hard criterion — not weaker recommendations, but none at all. `hinweise` is what has to be raised unprompted; a question not yet asked about angeratene Behandlungen or fehlende Zaehne shows up there as a point of its own, taking precedence. That is also why the input fields have three values rather than being `boolean`: "not asked" is something different from "no", and only that way can the unasked question be told from the answered one.

Beitraege are not a parameter and appear in no answer. They are not part of the product model, the Rechenkern holds them — logic that accepted a budget and could not compute against it would only pretend to check something.

## The four GOZ states

The check says **whether** a Leistung is covered in principle. It calculates no Erstattung — Selbstbehalt, Zahnstaffel and the Vorleistung of the statutory health insurance hang on the Vertrag and on what has been used so far, which puts them with `erstattung_berechnen` in the Rechenkern.

| State | Meaning |
|---|---|
| `ENTHALTEN` | The number belongs to a Leistungsbereich that this Tarif covers |
| `NICHT_ENTHALTEN` | This Tarif does not cover the Leistungsbereich |
| `NICHT_BESTIMMBAR` | The number cannot be assigned to a Leistungsbereich |
| `UNBEKANNT` | The number is not in Anlage 1 to the GOZ |

`NICHT_BESTIMMBAR` is the most valuable case of the four, and not a gap in the data. An Untersuchung, a Heil- und Kostenplan or an Abformung serves the treatment it belongs to and has no Leistungsbereich of its own. The same number can be Zahnersatz in one case and Kieferorthopaedie in another.

**Answering that with "not covered" is answering wrongly.** The right response is to ask which treatment the Position belongs to. The same goes for `UNBEKANNT` — a general anaesthetic, for instance, is billed by the dentist under the fee schedule for physicians and therefore does not appear in the GOZ annex. Those two states are the reason the service is instructive at all: an agent that knows only two answers will give wrong information here.

## Start

**Locally** — `./start.sh` from the repository root. Nothing is built along the way: documents and search indexes are checked in under `generated/`. The script only compares `generated/stand.txt` against the sources and warns if someone changed a source without running `./wissen/build.sh`.

**In a container** — the build context is the repository root, because the Maven build generates from `oas/wissen.yaml`:

```bash
./wissen/build.sh                       # only after source changes; needs Typst, Java, Maven
docker build -f wissen/Dockerfile -t atra-dent-wissen .
```

`build.sh` is preparatory work and runs **before** the Docker build — normally, though, the artefacts are already checked in, so a rebuild is only needed after a source change. If the generated documents are missing, the build stops with a message saying so instead of producing an image with an empty document set.

`build.sh` is the roof over two steps, and finally writes the checksum of the sources to `generated/stand.txt`:

| Script | produces | needs |
|---|---|---|
| `dokumente-build.sh` | `generated/pdf/`, `generated/html/` | Typst |
| `index-build.sh` | `generated/index/*.json` | Java 25, Maven; downloads the embedding model (~490 MB) on the first run |

The service loads the indexes at startup only if their ids, passage text and embedding dimension all match the documents and the model — otherwise it embeds afresh and warns in the log. A stale index is therefore refused rather than silently reused, so it makes the lab slower, but not wrong.

## Decisions, and why

**Tools instead of MCP resources.** The Bedingungswerke are 8 to 10 MB in size. Base64-encoded over JSON-RPC that would be roughly 13 MB per `resources/read` — not a workable route. The documents therefore go to the surface over REST, and MCP offers tools only. `resource: false` is set explicitly in the configuration.

**The Tarif is mandatory — wherever there is a Tarif.** The four Tarife differ in Quoten, Wartezeiten and exclusions, and the Bedingungswerke contradict one another accordingly. An answer without a Tarif would not be imprecise for `bedingungen_suchen` and `goz_pruefen`, it would be arbitrary. For `tarifempfehlung` it is the Eintrittsalter that is mandatory instead: it is the one piece of information that can rule out a Tarif on its own, and a guessed age yields a recommendation that fails at application.

**Three tool classes, not one with five methods.** The separation that holds in the search index holds in the code too, and it follows the source: `WissenTools` answers from published documents and delivers a citable Fundstelle with every hit, `BeratungTools` answers from internal steering knowledge, `TarifwerkTools` answers from the product model. Keeping that in one class is an invitation to keep it in one answer too. The marking therefore appears twice over: in the tool description, which a foreign model reads before calling, and in the result itself — because the result later sits in the context alongside passages from the conditions, and there it has to stay clear which passage is which. For the Tarifvergleich that second marking is the `dokumentId` of the Bedingungswerk on each Tarif: it says where to read up on what the table asserts without a Fundstelle.

**The chunk boundary is the paragraph, not the page.** Hence the detour through HTML: the Typst template emits a `section` element with a stable id for every paragraph and every annex, and the splitter never cuts across such a boundary. A Fundstelle therefore reads "§ 5 Umfang der Leistung" and not "page 12" — paragraphs stay stable when the layout changes.

**Chunk size differs per search space, and that was measured.** `BedingungenSearch` cuts at 1200 characters, `BeratungSearch` at 400. The Bedingungswerke consist of short, clearly delimited paragraphs; the handbook is prose whose sections run over several pages and cover several thoughts along the way. A long chunk averages those thoughts into one vector that fits everything a little and nothing exactly. Measured against the same 15 adviser questions: at 1200 characters the right section landed in the top three in 9 cases, at 400 characters in 12.

There was a trap to avoid along the way: Typst discards `grid` on HTML export, contents and all. Since the paragraph and lettered structure of the conditions was built on `grid`, every paragraph was at first an empty `div` in the HTML — the entire text of the conditions would have been missing from the index. The functions affected now branch on `target()`.

**Typst stays outside the Docker build.** Producing the documents is preparatory work. That keeps the image free of the document toolchain and the build fast.

**Java 25 and a plain Temurin image.** Deliberately no distroless and no jlink: the Dockerfile is meant to be read and understood in the lab, not optimised for bytes.

**A multilingual embedding model.** The Spring AI default `all-MiniLM-L6-v2` is English-only and useless for German insurance conditions. `intfloat/multilingual-e5-small` is used instead, baked into the image at a pinned revision so the container starts without a network.

**API-first, right up to the confidentiality boundary.** Interfaces and DTOs are generated from `oas/wissen.yaml`. A divergence between code and contract therefore breaks compilation rather than surfacing in production. The contract describes everything that answers out of the conditions, including the confidentiality level on every document. It does not describe the Beratung material — not because it could not model it, but because an interface that offers it also delivers it.
