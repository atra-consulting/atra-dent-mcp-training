# Generator

The Typst machinery that produces the documents in
[`submissions/`](../submissions/README.md). Five invented dental practices, five
layouts, one YAML file per case.

**Nothing here runs in the lab, and a participant never needs it.**
`./start.sh` does not start it, `./setup.sh` does not require it, no service
reads a file from this directory. This is trainer-side tooling: it exists so
that the material in `submissions/` can be changed or extended, and the finished
PDFs are checked in precisely so that nobody has to run it in order to work with
them. Whoever only wants to upload a Rechnung into the Kunden-Chat is in the
wrong directory.

Everything here needs Typst; the Rechnung form additionally needs Python 3.
`./setup.sh` reports both as hints rather than as requirements, for that reason.

## Layout

| Path | Contents |
|---|---|
| `rechnungen/` | the Rechnung generator — templates, five styles, the practice master data, the form, the template tests |
| `nachweise/` | the generator for health declarations — three document types, five styles |
| `reconcile.sh` | rebuilds every Rechnung and compares it against the checked-in PDF, then reconciles the GOAe numbers |
| `normalize.py` | strips the timestamps out of two PDFs so that `reconcile.sh` can compare them byte for byte |
| `goae_reconcile.py` | checks the GOAe numbers in `wissen/daten/goae-auszug.yaml` against `submissions/rechnungen/cases/` |

Each generator has its own reference documentation, and the two differ enough to
be worth keeping apart: the case schema of a Rechnung has nothing in common with
that of a findings report, and the sharp edges are in the details.

- [`rechnungen/README.md`](rechnungen/README.md) — case schema, the five layouts
  and what each of them shows, the two known gaps in recognising Positionen
  without a Gebuehrennummer
- [`nachweise/README.md`](nachweise/README.md) — case schema, the four
  assessment criteria, the three deliberate forms of incompleteness

## The five senders

Both generators draw on the same practice master data,
`rechnungen/data/praxen.yaml`. **The same practice that wrote a Kunde his
Rechnung writes his findings report too**, so that the submitted papers fit the
claim history in the Kernsystem. A second list of practices would drift apart
from the first sooner or later.

| Key | Practice | Rechnung style | Findings style |
|---|---|---|---|
| `beisser` | Dr. med. dent. Hartmut Beisser, Kassel | `klassisch` | `klassisch` |
| `weissraum` | weissraum · Zahnästhetik, Hamburg | `modern` | `modern` |
| `dds` | Deutsche Dentalabrechnung Süd AG | `verrechnungsstelle` | `mvz` |
| `plombeck` | Waldemar Plombeck, Klein-Kariesbach | `landpraxis` | `landpraxis` |
| `kiefernwald` | Klinik am Kiefernwald, Bad Molarheim | `klinik` | `klinik` |

**The style belongs to the practice, not to the case.** A case file names only
the practice; which of the five renderers under `<generator>/styles/` runs
follows from that. A practice that billed one way last year and another way this
year would make the layout a property of the case, and then no test could tell a
change of appearance from a change of facts.

The styles diverge as far as they like in appearance and never in the facts,
because no style calculates anything: `rechnungen/lib/fall.typ` and
`nachweise/lib/nachweis.typ` are the only places that read a case file, check it
and compute from it. Every check is an `assert` and aborts the build — a faulty
case yields no PDF but a message, and the form shows that same message as
validation.

## Commands

```bash
./generator/rechnungen/build.sh            # every case -> submissions/rechnungen/pdf/
./generator/nachweise/build.sh             # every case -> submissions/nachweise/pdf/
./generator/reconcile.sh                    # the checked-in PDFs still match their sources
python3 generator/rechnungen/frontend.py   # form on http://127.0.0.1:8080
```

Both `build.sh` scripts write into `submissions/`, and that is the whole
relationship between the two directories: the generator writes, `submissions/`
holds, everything else reads from `submissions/`.

**`--pruefen` is the exception that writes nowhere near the material.** It sets
one case in all five styles side by side so that a page break can be judged by
eye, and it drops the result in `<generator>/pruefung/`, which is not versioned.

```bash
./generator/rechnungen/build.sh --pruefen   # the wrap case, five styles
./generator/nachweise/build.sh --pruefen    # 10002-schuster, five styles
./generator/nachweise/build.sh --pruefen submissions/nachweise/cases/10009-almasri.yaml
```

The template tests need no PDF and no service:

```bash
./generator/rechnungen/tests/pruefen.sh    # good and faulty cases against lib/fall.typ
python3 -m unittest discover -s generator/rechnungen/tests -p 'test_*.py'
```

### `reconcile.sh`

The PDFs under `submissions/` are checked in, which is what frees a participant
from Typst — and it means they can silently fall out of step with the templates
they came from. `reconcile.sh` is the answer: it rebuilds every Rechnung into a
temporary directory and compares it against the checked-in file.

**It guards the 18 Rechnung PDFs and nothing else.** The 45 health declaration
files have no such check, and for a plain reason: the Kernsystem tests read the
Rechnungen, so a drift there turns a build red somewhere else in the repository
and has to be caught before it does. Nothing reads the declarations, so a drift
in them costs nothing until somebody looks. Whoever wires them to a service
should extend this script in the same change.

A raw byte diff would be red on every run, because Typst stamps each compile
with the current time and a fresh instance id — in the Info dictionary, in the
XMP packet and in the trailer. `normalize.py` removes exactly those stamps
before the comparison and nothing else. Anything that still differs is a real
difference. Its trailer pattern assumes Typst's current literal-string `/ID`
form; a future Typst version that switches to a hex `/ID` would turn every case
into a reported difference. That fails loudly rather than silently, but if a
Typst upgrade ever turns every case red at once, `normalize.py` is where to
look first.

The script does a second job in the same pass: it checks that
`wissen/daten/goae-auszug.yaml` and the GOAe numbers actually used in
`submissions/rechnungen/cases/` name the same set. That reconciliation used to
be a test in the Wissensdienst, which made a service module depend on generator
data. It belongs here, where both sides are in view and nobody's build breaks
because a case file gained a Position. `./generator/reconcile.sh --goae-only`
runs just that check and skips every Typst invocation, since Typst is optional
and this reconciliation should not have to wait on it.

Nothing runs it for you, and it needs Typst except in `--goae-only` mode. Run it
after changing a template or a case, and commit the rebuilt PDFs together with
the change.

### `frontend.py`

A local form for writing a Rechnung case by hand: pick a practice, enter the
Positionen, see the finished PDF. Standard library only — no `pip install`, no
build. It reads and writes YAML through `typst eval` rather than through a
Python YAML parser, so that Typst stays the single component in the project that
interprets this format; a second parser in a second language would be a second
interpretation of the same file.

It binds to `127.0.0.1`, knows no authentication and does not belong on a
network. It saves into `submissions/rechnungen/cases/`; ad-hoc experiments go to
`generator/rechnungen/adhoc/`, which is not versioned.

**Its port is 8080 and cannot be moved**, which is the port of the Kernsystem.
Whoever needs both at once pushes the Kernsystem aside — that is why 8083 stays
free as its fallback port (`KERNSYSTEM_PORT=8083 ./start.sh`).

## `generator/` is not free-standing

Every template imports the number and currency formatting of the lab from
`/wissen/dokumente/lib/daten.typ`, and `rechnungen/lib/fall.typ` resolves the
Leistungsbereich of a Gebuehrennummer out of `/wissen/daten/goz-zuordnung.yaml`.
Both paths are absolute from the repository root, which is why every build calls
`typst compile --root .` from there and not from this directory.

That is a deliberate dependency, not an oversight. An amount in a Rechnung is
set the same way as an amount in a Bedingungswerk, and a Leistungsbereich in a
case file means what the mapping in the Wissensdienst says it means. Copying
either into this directory would give the lab two answers to the same question,
and the copy would be the one nobody keeps up to date. The price is that
`generator/` cannot be lifted out into a repository of its own as it stands
today.

The dependency runs one way only. Nothing under `wissen/` reads anything from
here.

## Corporate design

`assets/corporate-design.yaml` does **not** apply to the Rechnungen or to the
findings reports. Those documents come from invented outside senders, and five
Rechnungen in atra blue would be the opposite of their purpose. Each practice
brings its own palette in `rechnungen/data/praxen.yaml`.

The one exception is the health questionnaire, and it proves the rule: atra
sends that form out itself.

## Design documents

- `docs/superpowers/specs/2026-08-11-rechnungsgenerator-design.md`
- `docs/superpowers/specs/2026-08-25-training-repo-separation-design.md` — why
  the material and the machinery were pulled apart
