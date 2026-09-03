# Rechnung generator

Produces dental Rechnungen as PDFs — the incoming documents an extraction or
Pruefung agent has to prove itself against in the lab. Five fictitious practices
set the same case data in five different hands.

The case file is at the same time the **ground truth**: it holds exactly what an
agent is meant to recover from the PDF.

**The templates live here, the products do not.** Case files and finished PDFs
sit under [`submissions/rechnungen/`](../../submissions/README.md), where a
participant finds them without ever opening this directory; how the two halves
relate is described in [`generator/README.md`](../README.md).

The finished PDFs under `submissions/rechnungen/pdf/` are checked in, as
`wissen/generated/` is; Typst is needed only by whoever changes a case or a
template. Every PDF is produced as **PDF/A-2b**. The cases `mueller-*`,
`schuster-*`, `weiss-*`, `papadakis-*` and `yilmaz-*` belong to the existing
customers 10001–10005 in `kernsystem/data/kunden.json` — name, address and date
of birth copied verbatim from there — so that a Rechnung can be matched to a
Kunde in the system.

Next to the agent there is now a **deterministic counterpart**: the Kernsystem
extracts the structured data from a PDF/A Rechnung through
`POST /api/v1/rechnungsextraktion`, or through the MCP tool
`rechnung_extrahieren` — plain text extraction plus a generic rule set, no
model. It does not take the exercise away from the lab; it supplies the
reference an agentic extraction can be measured against, and its test runs
against exactly the ground truth in `submissions/rechnungen/cases/`. See
`kernsystem/README.md`, section Rechnungsextraktion.

## Commands

Every one of them is run from the repository root; Typst is called with
`--root .` there, because the templates reach into `wissen/` for formatting and
for the GOZ mapping.

```bash
./generator/rechnungen/build.sh           # all cases -> submissions/rechnungen/pdf/
./generator/rechnungen/build.sh --pruefen # the stress case in all five styles (wrapping test)
./generator/rechnungen/tests/pruefen.sh   # good and faulty cases against lib/fall.typ
./generator/reconcile.sh                   # the checked-in PDFs still match their sources
python3 generator/rechnungen/frontend.py  # form on http://127.0.0.1:8080
python3 -m unittest discover -s generator/rechnungen/tests -p 'test_*.py'
```

## The five layouts

Who the five practices are is in [`generator/README.md`](../README.md); what
their layouts do is here.

| Style | Practice | Marks |
|---|---|---|
| `klassisch` | `beisser` | chamber form, every column including the Faktor |
| `modern` | `weissraum` | grouped by day of treatment, no date column |
| `verrechnungsstelle` | `dds` | sender ≠ Behandler, Faktor inside the Leistung text |
| `landpraxis` | `plombeck` | no table, **no Faktor shown** |
| `klinik` | `kiefernwald` | multi-page, cover sheet, Labor as an annex |

The style belongs to the practice, not to the case — a practice always bills the
same way. The case file names only the practice.

That `landpraxis` does not show the Steigerungssatz is deliberate. It puts a
piece of information into the ground truth that does not exist in the PDF, and
that is exactly what shows whether an agent reports `nicht angegeben` instead of
inventing a value.

### Positionen without a Gebuehrennummer

Material, Labor and Verlangensleistungen carry no GOZ number, so the parser has
to recognise them by something else: by the **date** (`klassisch`, `klinik` and
`verrechnungsstelle` carry a date column) or by the **row of dots**
(`landpraxis`). Two styles have gaps here, and both are reported:

- **`modern` carries neither a date nor a row of dots** (its columns are Ziffer,
  Zahn, Leistung, Anzahl, Faktor, Betrag). A Material or Labor line has nothing
  there to be recognised by and is lost entirely — [#25].
- **`landpraxis` loses its row of dots to the second line** as soon as the
  Leistung text wraps; the first line then carries no mark at all — [#24].

So write a new case file with Material or Labor lines against a practice that
has a date column (`beisser`, `dds`, `kiefernwald`). Otherwise
`RechnungsextraktionServiceTest` turns red — it compares the number of
Positionen, so a lost one surfaces at build time rather than in a demo.

[#24]: https://github.com/atra-consulting/atra-mcp-a2a-lab/issues/24
[#25]: https://github.com/atra-consulting/atra-mcp-a2a-lab/issues/25

## Case schema

One file per case under `submissions/rechnungen/cases/<name>.yaml`; the file
name becomes the name of the PDF. The document itself is a German dental
invoice, so the keys and the text in it are German.

```yaml
rechnungsnummer: "2026-04711"
rechnungsdatum: 2026-03-14
praxis: beisser                    # key from generator/rechnungen/data/praxen.yaml
patient:
  name: Katrin Vogel
  anschrift: { strasse: …, plz: "34117", ort: Kassel }
  geburtsdatum: 1979-06-02
vorgang:                           # optional, for billing agencies only
  kundennummer: "4471902"
  vorgangsnummer: "2026/PA/8831"
positionen:
  - { datum: 2026-01-08, art: goz, nummer: "2197", zahn: "36",
      leistung: Adhäsive Befestigung, anzahl: 1, faktor: 2.3, betrag: 17.85 }
zahlung: { frist_tage: 30, bereits_gezahlt: 0 }
```

Any number of Positionen. Required on each one: `art`, `leistung`, `betrag`, and
with `art: goz` also `nummer`.

| `art` | Meaning | Leistungsbereich |
|---|---|---|
| `goz` | Gebuehrennummer from Anlage 1 to the GOZ | from `wissen/daten/goz-zuordnung.yaml` |
| `goae` | Leistung under the GOÄ, § 6 Abs. 2 GOZ | not assignable |
| `material` | material under § 9 GOZ | not assignable |
| `labor` | dental laboratory work | not assignable |
| `verlangen` | Verlangensleistung, § 2 Abs. 3 GOZ | not assignable |

The four kinds that cannot be assigned are not a shortcoming. They are the gap
that `wissen/daten/goz-zuordnung.yaml` names in its own header comment: an agent
has to ask back there instead of guessing.

**Always quote GOZ numbers** — otherwise leading zeros are lost (`"0010"`).

**A Leistung text may wrap.** One case per style spells the Leistung out as
fully as Anlage 1 to the GOZ writes it, and it therefore no longer fits on one
line: `mueller-fuellung` (klassisch), `weissraum-prophylaxe` (modern),
`weiss-parodontalbehandlung` (verrechnungsstelle), `weiss-fuellung`
(landpraxis) and `kiefernwald-sanierung` (klinik). That is deliberate. The
ground truth holds the whole text and the extraction has to return the whole
text; trimming a text to a single line would be a tacit layout rule that nobody
enforces. So whoever changes a case writes it out rather than shortening it.

## Layout

| File | Responsibility |
|---|---|
| `lib/fall.typ` | loads, checks, calculates, resolves Leistungsbereiche — the **only** place that calculates |
| `lib/bausteine.typ` | page frame, address field, Positionen table, totals block |
| `styles/*.typ` | five renderers, one `setze(f)` function each |
| `rechnung.typ` | entry point, picks the style from the practice |
| `data/praxen.yaml` | the five practices — address, palette, style; read by the health declarations too |
| `tests/` | the good and the faulty cases the template checks are held against, and the tests of `frontend.py` |

No style calculates anything itself. That is why the five may drift as far apart
in appearance as they like, but never in the facts.

Every check is an `assert` and aborts the build. A faulty case produces no PDF
but a message — the same message the frontend shows as form validation.

## Corporate design

`assets/corporate-design.yaml` does **not** apply here. These documents come
from fictitious outside senders; five Rechnungen in atra blue would be the
opposite of their purpose. Each practice brings its own palette in
`data/praxen.yaml`.

## Design

`docs/superpowers/specs/2026-08-11-rechnungsgenerator-design.md`
