# Submissions

What arrives at atra. Dental Rechnungen as PDF/A, and the health declarations a
customer hands in for a risk assessment. These are incoming documents from
outside senders — the material an extraction, a Pruefung or an assessor has to
prove itself against.

**Nothing here needs Typst, and nothing here is built.** The PDFs are checked
in, exactly as `wissen/generated/` is. The machinery that produced them lives in
[`generator/`](../generator/README.md), it is the trainer's business, and a
participant never opens it. What is needed to work with this material is a PDF
viewer and a browser.

**These are the documents a participant uploads into the Kunden-Chat**
(<http://localhost:5174>). Take a file from `rechnungen/pdf/`, drop it into the
chat, and the whole of Journey 3 runs off it: the chat route hands the PDF
straight to the Kernsystem, which extracts it deterministically and without a
model; the Beratungsagent receives the structured result and turns it into a
Schadensfall — **its model never sees the document**, only that a Beleg is
attached, which is one of the points the lab is making; the Schadensfallagent
then picks the case up from `eingereicht` and writes its Bewertung. The
Sachbearbeiter-UI shows it under `/faelle/[id]`.

The lab has to be running for any of that: `./start.sh` from the repository
root, with the prerequisites and the addresses in the [root
README](../README.md). Journey 3 in particular needs `GEMINI_API_KEY` in `.env`
for the three agents, and a network plus `AGENTEN_ARZTSERVICE_API_KEY` for the
externally hosted Arztservice — without those the chain still runs, but every
case ends on the Eskalationsgrund `ARZT_UNAVAILABLE`.

## Layout

| Path | Contents |
|---|---|
| `rechnungen/pdf/` | 18 dental Rechnungen, one PDF/A-2b file each |
| `rechnungen/cases/` | the 18 case files, one YAML per PDF of the same name |
| `nachweise/pdf/<id>-<surname>/` | one health declaration as three PDF/A-2b files: `befund`, `historie`, `fragebogen` |
| `nachweise/cases/` | 15 case files, one YAML per folder of the same name |

## The YAML beside a PDF is the ground truth for it

A case file is not a description of its PDF. It is what the PDF was made from,
and it therefore holds exactly what a reader is meant to recover from the
document — no more and, in one telling place, more than the document shows.

For a Rechnung that is the whole case file: `rechnungsnummer`, patient, every
Position with its GOZ-Gebuehrennummer, Zahn, Steigerungssatz and amount.
`RechnungsextraktionServiceTest` in the Kernsystem runs the whole of
`rechnungen/cases/` against the whole of `rechnungen/pdf/` and compares field by
field, which is why a case and its PDF may never drift apart.

For a health declaration the ground truth sits under the key `erwartung` and
**appears in no PDF**. It is what a careful assessor ought to derive from the
papers: one value per criterion, or `NICHT_BEURTEILBAR` where the papers do not
answer the question. A declaration that carried its own assessment would no
longer test the assessor.

The most instructive gap is deliberate. The `landpraxis` layout prints no
Steigerungssatz, so `plombeck-fuellung`, `weiss-fuellung`, `mueller-befestigung`
and `yilmaz-weisheitszaehne` hold a Faktor in the case file that is nowhere in
the PDF. That is what separates an agent which reports the field as not given
from one which invents a plausible 2.3.

## Which PDF belongs to which case

For Rechnungen the name is the whole answer: `cases/mueller-fuellung.yaml`
produced `pdf/mueller-fuellung.pdf`.

For health declarations the case file names a folder, and what is submitted
together is read together: `cases/10002-schuster.yaml` produced
`pdf/10002-schuster/` with `befund.pdf`, `historie.pdf` and `fragebogen.pdf` in
it.

## Which case belongs to which Kunde

The health declarations map one to one onto the seed data in
`kernsystem/data/kunden.json`: 15 cases for the 15 Kunden, the Kundennummer in
the file name, and name, address and date of birth copied verbatim from the
record.

The Rechnungen split in two. Thirteen of them are named after a customer —
`mueller-*`, `schuster-*`, `weiss-*`, `papadakis-*`, `yilmaz-*` — and bill Kunden
10001 to 10005, so a Rechnung can be matched to a Kunde and to that Kunde's
claim history. `mueller-kind` is the one exception inside that group: the
patient is a child in the same household, not the Kunde herself.

The remaining five are named after the practice that wrote them —
`beisser-zahnersatz`, `dds-parodontose`, `kiefernwald-sanierung`,
`plombeck-fuellung`, `weissraum-prophylaxe`. Their patients appear in no
customer record at all, and that is what they are for: an Einreichung whose
patient cannot be resolved is a case the chain has to handle rather than assume
away.

A customer's Rechnungen are deliberately spread over several practices. One
Kunde therefore submits documents in several different hands, which is the
realistic case and the harder one.

## What the five hands are

Each Rechnung comes from one of five invented practices, and a practice always
bills the same way — the layout belongs to the sender, not to the case. Three of
the five carry a date column and two do not; one prints the Steigerungssatz
inside the Leistung text and one prints none at all; one runs over several pages
with the Labor share as an annex. The same case data therefore look thoroughly
different from file to file, which is the point of having five.

The same five practices write the findings reports and treatment histories: the
practice that billed a Kunde also examined him, so the submitted papers fit the
claim history in the Kernsystem. Which practice writes in which style, and why
each style is cut the way it is, is documented with the machinery in
[`generator/README.md`](../generator/README.md).

## The health declarations are wired to no service

No building block in the lab reads them. There is no risk assessment screen in
the Sachbearbeiter-UI and no route to one in the chat. They are stock on the
shelf: material for an exercise about reading documents of varying difficulty,
not a part of the running lab. The Arztservice promises a matching skill
(`risikoeinschaetzung`), and five of these cases sit in its repository as test
fixtures — but nothing in this repository calls it with them.

The Rechnungen, by contrast, are load-bearing. Journey 3 does not run without
them.

---

> **All invented.** Practices, patients, findings, courses of treatment and
> amounts are made up entirely. Nothing here is a medical statement, and nothing
> here has any connection to real practices, products or people.
