#!/usr/bin/env python3
"""Tests for tools/strip-comments.py.

Runs without pytest: python3 tools/tests/test_strip_comments.py
"""

import importlib.util
import sys
from pathlib import Path
from textwrap import dedent

ROOT = Path(__file__).resolve().parents[1]
_spec = importlib.util.spec_from_file_location("sc", ROOT / "strip-comments.py")
sc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(sc)


def strip(source: str, name: str, **options) -> str:
    stripped, _, _ = sc.strip_comments(dedent(source).lstrip("\n"), Path(name), **options)
    return stripped


def test_java_string_with_double_slash():
    source = '''
        class A {
            String url = "https://atra.example/tarife"; // Kommentar weg
            /* Blockkommentar
               ueber zwei Zeilen */
            String s = "/* kein Kommentar */";
        }
    '''
    result = strip(source, "A.java")
    assert '"https://atra.example/tarife";' in result
    assert "Kommentar weg" not in result
    assert "Blockkommentar" not in result
    assert '"/* kein Kommentar */"' in result


def test_java_text_block_stays_untouched():
    source = '''
        class A {
            String json = """
                { "pfad": "a//b", "hinweis": "/* nein */" }
                """; // weg
        }
    '''
    result = strip(source, "A.java")
    assert '"a//b"' in result
    assert "/* nein */" in result
    assert "// weg" not in result


def test_javadoc_and_pure_comment_lines_are_removed():
    source = '''
        /**
         * Javadoc.
         */
        class A {

            int x = 1;
        }
    '''
    result = strip(source, "A.java")
    assert "Javadoc" not in result
    assert result.startswith("class A {")
    assert "{\n\n    int x = 1;" in result


def test_typescript_regex_and_division():
    source = '''
        const muster = /\\/\\/ nicht/g; // weg
        const anteil = summe / anzahl / 2;
        const pfad = `${basis}//api`; // auch weg
    '''
    result = strip(source, "a.ts")
    assert "const muster = /\\/\\/ nicht/g;" in result
    assert "summe / anzahl / 2;" in result
    assert "`${basis}//api`;" in result
    assert "weg" not in result


def test_typescript_template_with_comment_in_expression():
    source = '''
        const t = `wert: ${wert /* weg */} ende`;
    '''
    result = strip(source, "a.ts")
    assert "`wert: ${wert } ende`" in result


def test_directives_are_kept():
    source = '''
        // eslint-disable-next-line no-control-regex
        const a = 1; // gewoehnlich, weg
        // @ts-expect-error duplex fehlt in den DOM-Typen
        const b = 2;
    '''
    result = strip(source, "a.ts")
    assert "eslint-disable-next-line" in result
    assert "@ts-expect-error" in result
    assert "gewoehnlich" not in result
    strict = strip(source, "a.ts", also_directives=True)
    assert "eslint" not in strict and "@ts-expect-error" not in strict


def test_svelte_markup_script_and_style():
    source = '''
        <script lang="ts">
            // weg
            let n = $state(0);
        </script>

        <!-- weg -->
        <!-- svelte-ignore a11y_media_has_caption -->
        <p>Text <!-- weg --> mehr</p>

        <style>
            /* weg */
            a { background: url(https://atra.example/b.png); }
        </style>
    '''
    result = strip(source, "A.svelte")
    assert "let n = $state(0);" in result
    assert "svelte-ignore a11y_media_has_caption" in result
    assert "url(https://atra.example/b.png)" in result
    assert "weg" not in result
    assert "<p>Text  mehr</p>" in result


def test_shell_heredoc_variables_and_shebang():
    source = '''
        #!/usr/bin/env bash
        # weg
        cat <<'ENDE'
        # kein Kommentar, sondern Ausgabe
        ENDE
        anzahl=${#argumente}   # weg
        echo "raute # im String"
    '''
    result = strip(source, "start.sh")
    assert result.startswith("#!/usr/bin/env bash")
    assert "# kein Kommentar, sondern Ausgabe" in result
    assert "anzahl=${#argumente}" in result
    assert '"raute # im String"' in result
    assert "weg" not in result


def test_yaml_block_scalar_and_quoted_hash():
    source = '''
        # weg
        spring:
          ai:
            instructions: >
              Ein Prompt mit # Raute und einem : Doppelpunkt.
              Zweite Zeile.
            titel: "atra # dent"   # weg
            farbe: '#a01c2d'
        liste:
          - eins   # weg
    '''
    result = strip(source, "application.yaml")
    assert "Ein Prompt mit # Raute" in result
    assert '"atra # dent"' in result
    assert "'#a01c2d'" in result
    assert "- eins" in result
    assert "weg" not in result


def test_python_docstring_bleibt():
    source = '''
        #!/usr/bin/env python3
        """Docstring bleibt."""
        # weg
        pfad = "a # b"  # weg
    '''
    result = strip(source, "a.py")
    assert result.startswith("#!/usr/bin/env python3")
    assert '"""Docstring bleibt."""' in result
    assert 'pfad = "a # b"' in result
    assert "weg" not in result


def test_xml_cdata_is_kept():
    source = '''
        <projekt>
          <!-- weg -->
          <text><![CDATA[ <!-- bleibt --> ]]></text>
        </projekt>
    '''
    result = strip(source, "pom.xml")
    assert "<!-- bleibt -->" in result
    assert "weg" not in result


def test_leerzeilen_straffen_ist_freiwillig():
    source = "a = 1\n\n\n\nb = 2  # weg\n"
    behalten = strip(source, "a.py")
    assert behalten == "a = 1\n\n\n\nb = 2\n"
    collapsed = strip(source, "a.py", collapse_blank_lines=True)
    assert collapsed == "a = 1\n\nb = 2\n"


def test_second_pass_changes_nothing():
    source = dedent('''
        class A {
            // weg
            int x = 1; /* weg */
        }
    ''')
    einmal, _, _ = sc.strip_comments(source, Path("A.java"))
    zweimal, count, _ = sc.strip_comments(einmal, Path("A.java"))
    assert einmal == zweimal and count == 0


def test_unknown_extension_stays_untouched():
    source = '{"a": 1}\n'
    assert strip(source, "daten.json") == source


if __name__ == "__main__":
    failures = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print(f"ok   {name}")
            except AssertionError as e:
                failures += 1
                print(f"FEHL {name}: {e}")
    print(f"\n{failures} failures")
    sys.exit(1 if failures else 0)
