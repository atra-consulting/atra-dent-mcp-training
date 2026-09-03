#!/usr/bin/env python3
"""Lokaler Formularserver für den Rechnungsgenerator.

Start:  python3 generator/rechnungen/frontend.py
Dann:   http://127.0.0.1:8080

Ausschließlich Standardbibliothek. Kein pip install, kein Build.

Python bekommt bewusst keinen YAML-Parser: gelesen wird über `typst eval`,
das JSON auf der Standardausgabe liefert. Damit ist Typst die einzige
Komponente im Projekt, die YAML auslegt — ein zweiter Parser in einer zweiten
Sprache wäre eine zweite Auslegung desselben Formats.

Der Server bindet auf 127.0.0.1, kennt keine Authentifizierung und gehört
nicht ins Netz.
"""

import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ADHOC = ROOT / "generator/rechnungen/adhoc"
CASES = ROOT / "submissions/rechnungen/cases"


def read_yaml(path_from_root):
    """Liest eine YAML-Datei über Typst und gibt sie als Python-Objekt zurück.

    path_from_root ist absolut ab der Repo-Wurzel, also '/generator/rechnungen/…'.
    """
    result = subprocess.run(
        ["typst", "eval", f'yaml("{path_from_root}")', "--root", str(ROOT)],
        capture_output=True,
        text=True,
        check=True,
    )
    return json.loads(result.stdout)


def _scalar(value):
    """Ein einzelner YAML-Wert."""
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return repr(value)
    text = str(value).replace("\\", "\\\\").replace('"', '\\"')
    text = text.replace("\n", " ")
    return f'"{text}"'


def to_yaml(data, indent=0):
    """Schreibt ein Dictionary als YAML.

    Bewusst klein gehalten: das Fallschema ist flach, es gibt keine Anker,
    keine mehrzeiligen Blockskalare und keine Sonderfälle. Alle
    Zeichenketten werden gequotet — dann kann kein Wert versehentlich als
    Zahl, Datum oder Bool gelesen werden, und führende Nullen in
    GOZ-Nummern bleiben erhalten.
    """
    lines = []
    fuellung = "  " * indent

    for schluessel, value in data.items():
        if isinstance(value, dict):
            lines.append(f"{fuellung}{schluessel}:")
            lines.append(to_yaml(value, indent + 1))
        elif isinstance(value, list):
            lines.append(f"{fuellung}{schluessel}:")
            for eintrag in value:
                if isinstance(eintrag, dict):
                    block = to_yaml(eintrag, indent + 2).split("\n")
                    erste = block[0].lstrip()
                    lines.append(f"{fuellung}  - {erste}")
                    lines.extend(block[1:])
                else:
                    lines.append(f"{fuellung}  - {_scalar(eintrag)}")
        else:
            lines.append(f"{fuellung}{schluessel}: {_scalar(value)}")

    text = "\n".join(z for z in lines if z != "")
    return text + "\n" if indent == 0 else text



import http.server
import re
import socketserver
import urllib.parse

PORT = 8080
PAGE = None


def build_pdf(case_yaml, target_name):
    """Schreibt die Falldatei und ruft Typst.

    Gibt (pdf_bytes, None) zurück oder (None, fehlertext). Der Fehlertext ist
    Typsts stderr im Original: die assert-Meldungen aus lib/fall.typ sind die
    Formularvalidierung. Eine zweite Regelmenge im Frontend würde sich
    früher oder später von der ersten unterscheiden.
    """
    ADHOC.mkdir(parents=True, exist_ok=True)
    yaml_pfad = ADHOC / f"{target_name}.yaml"
    pdf_pfad = ADHOC / f"{target_name}.pdf"
    yaml_pfad.write_text(case_yaml, encoding="utf-8")

    result = subprocess.run(
        [
            "typst", "compile", "--root", str(ROOT),
            "--pdf-standard", "a-2b",
            "--input", f"fall=/{yaml_pfad.relative_to(ROOT)}",
            str(ROOT / "generator/rechnungen/rechnung.typ"),
            str(pdf_pfad),
        ],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return None, result.stderr
    return pdf_pfad.read_bytes(), None


class RequestHandler(http.server.BaseHTTPRequestHandler):
    def _sende(self, status, typ, koerper):
        if isinstance(koerper, str):
            koerper = koerper.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", typ)
        self.send_header("Content-Length", str(len(koerper)))
        self.end_headers()
        self.wfile.write(koerper)

    def log_message(self, format, *args):
        pass

    def do_GET(self):
        weg = urllib.parse.urlparse(self.path).path

        if weg == "/":
            self._sende(200, "text/html; charset=utf-8", PAGE)

        elif weg == "/katalog":
            praxen = read_yaml("/generator/rechnungen/data/praxen.yaml")["praxen"]
            katalog = read_yaml("/wissen/daten/goz-zuordnung.yaml")["positionen"]
            self._sende(200, "application/json; charset=utf-8", json.dumps({
                "praxen": [
                    {"schluessel": p["schluessel"], "name": p["name"], "stil": p["stil"]}
                    for p in praxen
                ],
                "goz": [
                    {
                        "nummer": p["nummer"],
                        "bezeichnung": p["bezeichnung"],
                        "leistungsbereich": p["leistungsbereich"],
                    }
                    for p in katalog
                ],
            }))

        elif weg == "/faelle":
            namen = sorted(p.stem for p in CASES.glob("*.yaml"))
            self._sende(200, "application/json; charset=utf-8", json.dumps(namen))

        elif weg.startswith("/faelle/"):
            name = weg[len("/faelle/"):]
            if not re.fullmatch(r"[a-z0-9-]+", name):
                self._sende(400, "text/plain; charset=utf-8", "Ungültiger Name")
                return
            if not (CASES / f"{name}.yaml").is_file():
                self._sende(404, "text/plain; charset=utf-8", "Fall nicht gefunden")
                return
            case = read_yaml(f"/submissions/rechnungen/cases/{name}.yaml")
            self._sende(200, "application/json; charset=utf-8", json.dumps(case))

        else:
            self._sende(404, "text/plain; charset=utf-8", "Nicht gefunden")

    def do_POST(self):
        weg = urllib.parse.urlparse(self.path).path
        laenge = int(self.headers.get("Content-Length", 0))
        data = json.loads(self.rfile.read(laenge).decode("utf-8"))

        if weg == "/erzeugen":
            pdf, error = build_pdf(to_yaml(data), "vorschau")
            if error is not None:
                self._sende(422, "text/plain; charset=utf-8", error)
                return
            self._sende(200, "application/pdf", pdf)

        elif weg == "/speichern":
            name = data.pop("_name", "")
            if not re.fullmatch(r"[a-z0-9-]+", name):
                self._sende(
                    400, "text/plain; charset=utf-8",
                    "Name darf nur Kleinbuchstaben, Ziffern und Bindestriche enthalten",
                )
                return
            pdf, error = build_pdf(to_yaml(data), "vorschau")
            if error is not None:
                self._sende(422, "text/plain; charset=utf-8", error)
                return
            (CASES / f"{name}.yaml").write_text(to_yaml(data), encoding="utf-8")
            self._sende(
                200, "text/plain; charset=utf-8",
                f"Gespeichert als submissions/rechnungen/cases/{name}.yaml",
            )

        else:
            self._sende(404, "text/plain; charset=utf-8", "Nicht gefunden")


def page():
    """Die Formularseite. Kein f-String: der Katalog wird zur Laufzeit
    über fetch('/katalog') geholt und nicht eingebettet."""
    return """<!doctype html>
<html lang="de">
<head>
<meta charset="utf-8">
<title>Rechnungsgenerator</title>
<style>
  * { box-sizing: border-box; }
  body { margin: 0; font: 13px/1.4 "Helvetica Neue", Helvetica, Arial, sans-serif;
         display: grid; grid-template-columns: 470px 1fr; height: 100vh; }
  #formular { overflow-y: auto; padding: 16px; border-right: 1px solid #ddd; }
  #rechts { display: flex; flex-direction: column; min-width: 0; }
  #fehler { display: none; background: #fdf2f2; color: #7a1f2b; padding: 10px;
            margin: 0; font-family: ui-monospace, monospace; font-size: 11px;
            white-space: pre-wrap; max-height: 45vh; overflow-y: auto; }
  #vorschau { flex: 1; border: 0; width: 100%; }
  h2 { font-size: 12px; text-transform: uppercase; letter-spacing: .06em;
       color: #666; margin: 18px 0 8px; }
  h2:first-of-type { margin-top: 0; }
  label { display: block; margin-bottom: 6px; }
  label span { display: block; font-size: 11px; color: #666; }
  input, select { width: 100%; padding: 4px 6px; font: inherit;
                  border: 1px solid #ccc; border-radius: 3px; background: #fff; }
  .reihe { display: grid; gap: 6px; }
  .zwei { grid-template-columns: 1fr 1fr; }
  .drei { grid-template-columns: 1fr 1fr 1fr; }
  .position { border: 1px solid #e2e2e2; border-radius: 4px; padding: 8px;
              margin-bottom: 8px; background: #fafafa; }
  .position .kopf { display: flex; gap: 8px; align-items: center;
                    margin-bottom: 6px; }
  .position .kopf select { width: auto; }
  .bereich { font-size: 11px; color: #0f766e; flex: 1; }
  button { font: inherit; padding: 5px 10px; border: 1px solid #bbb;
           border-radius: 3px; background: #fff; cursor: pointer; }
  button.haupt { background: #264892; color: #fff; border-color: #264892; }
  .werkzeuge { display: flex; gap: 6px; margin-bottom: 16px; flex-wrap: wrap;
               align-items: center; }
  .werkzeuge select { width: auto; }
</style>
</head>
<body>
<div id="formular">
  <div class="werkzeuge">
    <button class="haupt" onclick="erzeugen()">Erzeugen</button>
    <button onclick="speichern()">Speichern unter…</button>
    <select id="laden" onchange="ladeFall(this.value)">
      <option value="">Fall laden…</option>
    </select>
  </div>

  <h2>Rechnung</h2>
  <label><span>Praxis (bringt ihren Stil mit)</span><select id="praxis"></select></label>
  <div class="reihe zwei">
    <label><span>Rechnungsnummer</span><input id="rechnungsnummer" value="2026-0001"></label>
    <label><span>Rechnungsdatum</span><input id="rechnungsdatum" type="date"></label>
  </div>
  <div class="reihe zwei">
    <label><span>Zahlungsfrist (Tage)</span><input id="frist_tage" type="number" value="30"></label>
    <label><span>bereits gezahlt (EUR)</span><input id="bereits_gezahlt" type="number" step="0.01" value="0"></label>
  </div>

  <h2>Patient</h2>
  <label><span>Name</span><input id="p_name" value="Testfall Eins"></label>
  <div class="reihe zwei">
    <label><span>Straße</span><input id="p_strasse"></label>
    <label><span>Geburtsdatum</span><input id="p_geburtsdatum" type="date"></label>
  </div>
  <div class="reihe zwei">
    <label><span>PLZ</span><input id="p_plz"></label>
    <label><span>Ort</span><input id="p_ort"></label>
  </div>

  <h2>Positionen</h2>
  <div id="positionen"></div>
  <button onclick="neuePosition()">+ Position</button>
  <datalist id="gozliste"></datalist>
</div>

<div id="rechts">
  <pre id="fehler"></pre>
  <iframe id="vorschau"></iframe>
</div>

<script>
const ARTEN = ["goz", "goae", "material", "labor", "verlangen"];
let GOZ = {};

// Leere Eingaben werden null, nicht "". Sonst stünde zahn: "" in der
// Falldatei, und das ist etwas anderes als "keine Zahnangabe".
function txt(el) { const w = el.value.trim(); return w === "" ? null : w; }
function num(el) { const w = el.value.trim(); return w === "" ? null : parseFloat(w); }

async function start() {
  const daten = await (await fetch("/katalog")).json();

  const praxis = document.getElementById("praxis");
  for (const p of daten.praxen) {
    praxis.add(new Option(p.name + " (" + p.stil + ")", p.schluessel));
  }

  const liste = document.getElementById("gozliste");
  for (const g of daten.goz) {
    GOZ[g.nummer] = g;
    const o = document.createElement("option");
    o.value = g.nummer;
    o.label = g.bezeichnung;
    liste.appendChild(o);
  }

  const faelle = await (await fetch("/faelle")).json();
  const laden = document.getElementById("laden");
  for (const name of faelle) laden.add(new Option(name, name));

  document.getElementById("rechnungsdatum").value =
    new Date().toISOString().slice(0, 10);
  neuePosition();
}

function neuePosition(werte) {
  const w = werte || {};
  const d = document.createElement("div");
  d.className = "position";
  d.innerHTML = `
    <div class="kopf">
      <select class="art">${ARTEN.map(a => "<option>" + a + "</option>").join("")}</select>
      <span class="bereich"></span>
      <button onclick="this.closest('.position').remove()">×</button>
    </div>
    <div class="reihe drei">
      <label><span>Datum</span><input class="datum" type="date"></label>
      <label><span>Nummer</span><input class="nummer" list="gozliste"></label>
      <label><span>Zahn</span><input class="zahn"></label>
    </div>
    <label><span>Leistung</span><input class="leistung"></label>
    <div class="reihe drei">
      <label><span>Anzahl</span><input class="anzahl" type="number" value="1"></label>
      <label><span>Faktor</span><input class="faktor" type="number" step="0.1"></label>
      <label><span>Betrag (EUR)</span><input class="betrag" type="number" step="0.01"></label>
    </div>`;

  d.querySelector(".art").value = w.art || "goz";
  for (const feld of ["datum", "nummer", "zahn", "leistung", "anzahl", "faktor", "betrag"]) {
    if (w[feld] !== undefined && w[feld] !== null) d.querySelector("." + feld).value = w[feld];
  }

  // GOZ-Hilfe: Leistungstext vorschlagen, Leistungsbereich anzeigen.
  const nummer = d.querySelector(".nummer");
  const zeigeBereich = () => {
    const g = GOZ[nummer.value.trim()];
    const art = d.querySelector(".art").value;
    d.querySelector(".bereich").textContent =
      (art === "goz" && g) ? (g.leistungsbereich || "kein Leistungsbereich") : "";
  };
  nummer.addEventListener("change", () => {
    const g = GOZ[nummer.value.trim()];
    const leistung = d.querySelector(".leistung");
    if (g && leistung.value.trim() === "") leistung.value = g.bezeichnung;
    zeigeBereich();
  });
  d.querySelector(".art").addEventListener("change", zeigeBereich);

  document.getElementById("positionen").appendChild(d);
  zeigeBereich();
}

function sammle() {
  const g = id => document.getElementById(id);
  const positionen = [...document.querySelectorAll(".position")].map(d => ({
    datum: txt(d.querySelector(".datum")),
    art: d.querySelector(".art").value,
    nummer: txt(d.querySelector(".nummer")),
    zahn: txt(d.querySelector(".zahn")),
    leistung: txt(d.querySelector(".leistung")),
    anzahl: num(d.querySelector(".anzahl")),
    faktor: num(d.querySelector(".faktor")),
    betrag: num(d.querySelector(".betrag")),
  }));
  return {
    rechnungsnummer: txt(g("rechnungsnummer")),
    rechnungsdatum: txt(g("rechnungsdatum")),
    praxis: g("praxis").value,
    patient: {
      name: txt(g("p_name")),
      anschrift: {
        strasse: txt(g("p_strasse")),
        plz: txt(g("p_plz")),
        ort: txt(g("p_ort")),
      },
      geburtsdatum: txt(g("p_geburtsdatum")),
    },
    positionen: positionen,
    zahlung: {
      frist_tage: num(g("frist_tage")),
      bereits_gezahlt: num(g("bereits_gezahlt")) || 0,
    },
  };
}

function zeigeFehler(text) {
  const f = document.getElementById("fehler");
  f.textContent = text;
  f.style.display = text ? "block" : "none";
}

async function erzeugen() {
  const antwort = await fetch("/erzeugen", {
    method: "POST",
    body: JSON.stringify(sammle()),
  });
  if (!antwort.ok) { zeigeFehler(await antwort.text()); return; }
  zeigeFehler("");
  const blob = await antwort.blob();
  document.getElementById("vorschau").src = URL.createObjectURL(blob);
}

async function speichern() {
  const name = prompt("Dateiname (Kleinbuchstaben, Ziffern, Bindestriche):");
  if (!name) return;
  const daten = sammle();
  daten._name = name;
  const antwort = await fetch("/speichern", {
    method: "POST",
    body: JSON.stringify(daten),
  });
  const text = await antwort.text();
  if (!antwort.ok) { zeigeFehler(text); return; }
  zeigeFehler("");
  alert(text);
}

async function ladeFall(name) {
  if (!name) return;
  const f = await (await fetch("/faelle/" + name)).json();
  const g = id => document.getElementById(id);
  g("rechnungsnummer").value = f.rechnungsnummer || "";
  g("rechnungsdatum").value = f.rechnungsdatum || "";
  g("praxis").value = f.praxis || "";
  g("p_name").value = (f.patient && f.patient.name) || "";
  const a = (f.patient && f.patient.anschrift) || {};
  g("p_strasse").value = a.strasse || "";
  g("p_plz").value = a.plz || "";
  g("p_ort").value = a.ort || "";
  g("p_geburtsdatum").value = (f.patient && f.patient.geburtsdatum) || "";
  const z = f.zahlung || {};
  g("frist_tage").value = z.frist_tage != null ? z.frist_tage : 30;
  g("bereits_gezahlt").value = z.bereits_gezahlt != null ? z.bereits_gezahlt : 0;
  document.getElementById("positionen").innerHTML = "";
  for (const p of f.positionen || []) neuePosition(p);
}

start();
</script>
</body>
</html>
"""


def main():
    global PAGE
    PAGE = page()
    with socketserver.TCPServer(("127.0.0.1", PORT), RequestHandler) as server:
        print(f"Rechnungsgenerator auf http://127.0.0.1:{PORT}")
        print("Beenden mit Strg-C")
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            print("\nBeendet.")


if __name__ == "__main__":
    main()
