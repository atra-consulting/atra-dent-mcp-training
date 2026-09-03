"""Tests the data layer of frontend.py.

The HTTP part is not tested: it has no logic of its own, it only passes data
through between these two functions and typst compile.
"""

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import frontend


class AlsYaml(unittest.TestCase):
    def test_writes_flat_case(self):
        result = frontend.to_yaml({
            "rechnungsnummer": "2026-0001",
            "rechnungsdatum": "2026-03-14",
            "praxis": "beisser",
        })
        self.assertIn('rechnungsnummer: "2026-0001"', result)
        self.assertIn('praxis: "beisser"', result)

    def test_quotes_strings_with_colon(self):
        result = frontend.to_yaml({"leistung": "Krone: Zahn 36"})
        self.assertIn('leistung: "Krone: Zahn 36"', result)

    def test_escapes_quotation_marks(self):
        result = frontend.to_yaml({"leistung": 'Krone "vollkeramisch"'})
        self.assertIn(r"\"vollkeramisch\"", result)

    def test_writes_numbers_without_quotes(self):
        result = frontend.to_yaml({"betrag": 17.85, "anzahl": 2})
        self.assertIn("betrag: 17.85", result)
        self.assertIn("anzahl: 2", result)

    def test_writes_none_as_null(self):
        self.assertIn("zahn: null", frontend.to_yaml({"zahn": None}))

    def test_writes_nested_mapping(self):
        result = frontend.to_yaml({
            "patient": {"name": "Katrin Vogel", "anschrift": {"ort": "Kassel"}},
        })
        self.assertIn("patient:\n", result)
        self.assertIn('  name: "Katrin Vogel"', result)
        self.assertIn('    ort: "Kassel"', result)

    def test_writes_list_of_mappings(self):
        result = frontend.to_yaml({
            "positionen": [
                {"art": "goz", "betrag": 1.5},
                {"art": "labor", "betrag": 2.0},
            ],
        })
        self.assertIn('  - art: "goz"', result)
        self.assertIn("    betrag: 1.5", result)
        self.assertIn('  - art: "labor"', result)

    def test_result_is_readable_by_typst(self):
        """The real proof: Typst must get back what we write."""
        data = {
            "rechnungsnummer": "2026-0009",
            "patient": {"name": 'Anna "Anni" Groß'},
            "positionen": [
                {"art": "goz", "nummer": "0010", "betrag": 10.72, "zahn": None},
            ],
        }
        frontend.ADHOC.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(
            "w", suffix=".yaml", dir=frontend.ADHOC, delete=False, encoding="utf-8"
        ) as file:
            file.write(frontend.to_yaml(data))
            path = Path(file.name)
        try:
            gelesen = frontend.read_yaml("/" + str(path.relative_to(frontend.ROOT)))
        finally:
            path.unlink()
        self.assertEqual(gelesen["rechnungsnummer"], "2026-0009")
        self.assertEqual(gelesen["patient"]["name"], 'Anna "Anni" Groß')
        self.assertEqual(gelesen["positionen"][0]["betrag"], 10.72)
        self.assertIsNone(gelesen["positionen"][0]["zahn"])


class LeseYaml(unittest.TestCase):
    def test_reads_praxen(self):
        praxen = frontend.read_yaml("/generator/rechnungen/data/praxen.yaml")["praxen"]
        self.assertEqual(len(praxen), 5)
        self.assertEqual(
            sorted(p["schluessel"] for p in praxen),
            ["beisser", "dds", "kiefernwald", "plombeck", "weissraum"],
        )

    def test_reads_goz_catalog(self):
        katalog = frontend.read_yaml("/wissen/daten/goz-zuordnung.yaml")
        eintrag = next(p for p in katalog["positionen"] if p["nummer"] == "9010")
        self.assertEqual(eintrag["leistungsbereich"], "IMP")


if __name__ == "__main__":
    unittest.main()
