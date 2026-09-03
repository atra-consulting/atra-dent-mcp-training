# Health declarations

Produces the health declarations a customer submits for the **risk assessment**
behind an upgrade to a higher Tarif: a findings report, a treatment history and
a health questionnaire the customer fills in himself. Built like the
[Rechnung generator](../rechnungen/README.md) — Typst templates, one style per
practice, one YAML file per case.

**The templates live here, the products do not.** Case files and finished PDFs
sit under [`submissions/nachweise/`](../../submissions/README.md); how the two
halves relate is described in [`generator/README.md`](../README.md).

> Every practice, person, finding and course of treatment here is **invented**.
> None of it is a medical statement; it serves neither for diagnosis nor as a
> model for a real findings report. atra.dent is a fictitious dental
> supplementary insurance.

**Nothing in the lab consumes this material.** No service reads these documents,
no building block is wired to them: there is no risk assessment screen in the
backoffice and no route to one in the chat. This is a generator for material,
not a running part of the lab.

The documents themselves are German — German practices write German findings
reports, and the questionnaire is a German form.

## What for

A fictitious specialist — a language model in the Arztservice — is meant to read
these papers and return one value per criterion:

| Criterion | Weight |
|---|---|
| `ANGERATENE_BEHANDLUNG` | 40 |
| `ERSATZBEDARF` | 25 |
| `SANIERUNGSGRAD` | 20 |
| `PARODONTALBEFUND` | 15 |

The score is the weighted sum. The bands are 0–29 green, 30–69 yellow, 70 and
above red.

**The most important state is none of those.** Every criterion can come back
`NICHT_BEURTEILBAR` when the papers do not answer the question — and a single
one of them invalidates the score. A criterion that cannot be judged counts
neither as 0 nor as 100: either would be a statement the papers do not support.
Turn not knowing into a number and you get a number that afterwards looks like
knowledge. Same lesson as `NICHT_BESTIMMBAR` in `goz_pruefen` in the
Wissensdienst.

From that follows the real property of this material: **the documents are meant
to read with varying difficulty.** Incompleteness is not a defect here. It is
the thing being tested.

**That specialist now exists.** The Arztservice has been hosted externally since
21 August 2026
([atra-arzt-service](https://github.com/atra-consulting/atra-arzt-service)) and
promises the skill `risikoeinschaetzung` there — these four criteria exactly,
one value or `NICHT_BEURTEILBAR` each. Five cases from here sit in that
repository as test fixtures, and what was stripped from them on the way is the
point of the exercise: `kundenId`, name and address, because an outside service
needs no identity to judge a finding — and `erwartete_zone`, because an assessor
who knows the threshold computes towards the threshold instead of towards the
finding.

That changes nothing here. **The generator stays in this repository**, and the
lab still binds the skill nowhere. The material remains stock on the shelf —
stock with a taker, but stock.

## Building

From the repository root, because the templates reach into `wissen/` for
formatting:

```bash
./generator/nachweise/build.sh            # all cases
./generator/nachweise/build.sh --pruefen  # 10002-schuster in all five styles
./generator/nachweise/build.sh --pruefen submissions/nachweise/cases/10009-almasri.yaml
```

Each case yields three PDF/A-2b files under
`submissions/nachweise/pdf/<id>-<surname>/{befund,historie,fragebogen}.pdf`. The
folder is the Einreichung: what is submitted together is read together.
`--pruefen` is the one mode that writes elsewhere — into
`generator/nachweise/pruefung/`, which is not versioned, because a style
comparison is not material.

There is **no HTML version**, unlike the Bedingungswerke under `wissen/`. A
machine-readable finding would take away precisely the exercise this material
exists for: a Rechnung is read out deterministically, a finding has to be read.
The contrast is deliberate.

## Who writes

Findings report and history draw on `generator/rechnungen/data/praxen.yaml` —
**the same practice that wrote a customer his Rechnung writes his findings
report too.** That way the submitted papers fit the history of Schadensfaelle
in the Kernsystem and an assessor can lay both side by side. A second list of
practices would drift apart from the first sooner or later.

| Findings style | Practice | Character |
|---|---|---|
| `klassisch` | `beisser` | sober chamber form, tooth chart as a grid |
| `modern` | `weissraum` | plenty of air, Zahnstatus as a list instead of a grid |
| `mvz` | `dds` | sender and author come apart — the billing agency transmits, the MVZ examined |
| `landpraxis` | `plombeck` | tight, serifs, facts in running text instead of fields |
| `klinik` | `kiefernwald` | thorough, Zahnstatus twice over (grid and plain text) |

The style belongs to the practice, not to the case: a practice always writes the
same way. `--pruefen` overrides it for the wrapping test only.

The **questionnaire** is the only one of the three documents that follows the
corporate design (`assets/corporate-design.yaml`) — for exactly the reason the
other two do not: atra sends this form out itself, whereas the practices are
outside senders.

## Case file schema

One file per case under
`submissions/nachweise/cases/<kundenId>-<surname>.yaml`. The file name
determines the output folder.

```yaml
kundenId: 10002
praxis: dds                  # key from generator/rechnungen/data/praxen.yaml

patient:
  name: Bernd Schuster
  geburtsdatum: 1958-09-30
  anschrift: { strasse: …, plz: "20457", ort: Hamburg }

befund:
  datum: 2026-07-28
  aktenzeichen: "WW-2026-3318"     # optional
  zahnstatus:                       # FDI, only what stands out; anything absent counts as unremarkable
    - { zahn: "36", zustand: fehlend }
  diagnosen: [ … ]
  parodontalbefund: …               # optional — without it the criterion cannot be judged
  empfehlung: …                     # optional — carries ANGERATENE_BEHANDLUNG
  hkp_beiliegend: true

maengel:                            # steers the incompleteness
  seite_fehlt: 2                    # document breaks off after page 1
  seiten_gesamt: 3                  # required once seite_fehlt is set
  unleserlich: [ diagnose1, empfehlung, parodontalbefund, historie0 ]
  veraltet: true                    # finding marked as an old file copy

historie:
  eintraege:
    - { datum: 2026-03-10, zahn: "46", behandlung: Trepanation }

fragebogen:
  ausgefuellt_am: 2026-08-06
  antworten:
    - frage: Fehlen Ihnen Zaehne?
      typ: ja_nein                  # ja_nein (default) or offen
      ankreuz: ja                   # ja | nein | null
      freitext: Drei Backenzaehne.

erwartung:                          # ground truth — appears in NO PDF
  merkmale:
    ANGERATENE_BEHANDLUNG: { wert: 95, begruendung: … }
    PARODONTALBEFUND:      { wert: NICHT_BEURTEILBAR, begruendung: … }
  erwartete_zone: rot               # gruen | gelb | rot | kein_score
  hinweis: …
```

**States in the Zahnstatus:** `gesund`, `fuellung`, `inlay`, `teilkrone`,
`krone`, `bruecke`, `implantat`, `wurzelgefuellt`, `ersatzbeduerftig`,
`fehlend`.

**The three forms of incompleteness** are properties of the case, not faults of
the generator: an illegible passage (`unleserlich` names keys such as
`diagnose0`, `empfehlung`, `parodontalbefund`, `historie2`), a missing
continuation page, an out-of-date state. A yes/no question with `ankreuz: null`
is unanswered — an open question (`typ: offen`) carries no boxes at all and is
complete without a mark.

## What `erwartung` is

The ground truth from human hands: what a careful assessor ought to derive from
these papers. It is the yardstick for the language model's assessment, and not a
test target — no test demands that exactly this number come out. A model never
hits it exactly, and a test that required it would be testing the model instead
of the service.

`lib/nachweis.typ` reads it but puts it into **no** document. A declaration that
brings its own assessment along would no longer test the specialist.

The build recomputes the score from the criteria and aborts when
`erwartete_zone` does not match — an `assert` like all the others. A faulty case
produces no PDF but a message. That matters more here than elsewhere, because
otherwise a real error would look like an intended defect.

## Consistency with the record

The material has to fit `kernsystem/data/kunden.json` and
`kernsystem/data/schadensfaelle.json`: whoever is recorded there with
`fehlendeZaehne: 3` has three missing teeth in the findings report, and a bridge
billed for regio 24–26 is in the mouth. Contradictions **with the record** are
errors.

Contradictions **between questionnaire and findings report**, on the other hand,
are the whole point — they are where it is decided whether an assessor says
`NICHT_BEURTEILBAR` or guesses instead. The build cannot check any of this:
`kunden.json` does not belong to this generator.

## Layout

| Path | Contents |
|---|---|
| `build.sh` | builds all cases, `--pruefen` for the style comparison |
| `befund.typ`, `historie.typ`, `fragebogen.typ` | entry points, they decide nothing about appearance |
| `lib/nachweis.typ` | loads and checks the case file — the only place that reads it |
| `lib/bausteine.typ` | page layout, tooth chart, findings table, the three forms of defect |
| `styles/*.typ` | the five hands |

The cases and their ground truth are not here. They live with the finished PDFs
under `submissions/nachweise/`, because that is what a reader of this material
needs and this directory is not.
