package consulting.atra.wissen.search;

import consulting.atra.wissen.documents.GeneratedText;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class HtmlParser {

    public static final int DEFAULT_MAX_CHARS = 1200;

    public static final int DEFAULT_OVERLAP = 200;

    private static final Set<String> BLOCK_ELEMENTS = Set.of(
            "address", "article", "aside", "blockquote", "dd", "div", "dl", "dt",
            "figcaption", "figure", "footer", "h1", "h2", "h3", "h4", "h5", "h6",
            "header", "hr", "li", "main", "nav", "ol", "p", "pre", "section", "ul");

    private static final Set<String> SKIPPED = Set.of("img", "script", "style", "svg");

    private final int maxChars;
    private final int overlap;

    public HtmlParser() {
        this(DEFAULT_MAX_CHARS, DEFAULT_OVERLAP);
    }

    public HtmlParser(int maxChars, int overlap) {
        if (maxChars < 100) {
            throw new IllegalArgumentException(
                    "maxZeichen muss mindestens 100 sein, war " + maxChars);
        }
        if (overlap < 0 || overlap > maxChars / 2) {
            throw new IllegalArgumentException("ueberlappung muss zwischen 0 und "
                    + maxChars / 2 + " liegen, war " + overlap);
        }
        this.maxChars = maxChars;
        this.overlap = overlap;
    }

    public List<Passage> split(String dokumentId, Path htmlDatei) throws IOException {
        Document document = Jsoup.parse(htmlDatei, StandardCharsets.UTF_8.name());

        List<Passage> passages = new ArrayList<>();
        for (Element abschnitt : document.select("section[id]")) {
            passages.addAll(splitSection(dokumentId, abschnitt, htmlDatei));
        }
        if (passages.isEmpty()) {
            throw new IOException("Keine Abschnitte gefunden in "
                    + htmlDatei.toAbsolutePath().normalize()
                    + ". Erwartet werden section-Elemente mit id.");
        }
        return List.copyOf(passages);
    }

    private List<Passage> splitSection(String dokumentId, Element abschnitt, Path htmlDatei)
            throws IOException {
        if (abschnitt.selectFirst("nav[role=doc-toc]") != null) {
            return List.of();
        }

        Element copy = abschnitt.clone();

        Element title = copy.selectFirst("h1, h2, h3");
        if (title == null) {
            throw new IOException("Abschnitt '" + abschnitt.id() + "' ohne Ueberschrift in "
                    + htmlDatei.toAbsolutePath().normalize());
        }
        String heading = GeneratedText.normalize(title.text());
        title.remove();

        List<String> lines = collectLines(copy);
        if (lines.isEmpty()) {
            return List.of();
        }

        List<String> partPieces = cut(lines);
        List<Passage> passages = new ArrayList<>(partPieces.size());
        for (int nummer = 0; nummer < partPieces.size(); nummer++) {
            passages.add(new Passage(dokumentId, abschnitt.id(), heading,
                    partPieces.get(nummer), nummer + 1, partPieces.size()));
        }
        return passages;
    }

    private static List<String> collectLines(Element wurzel) {
        List<String> lines = new ArrayList<>();
        StringBuilder running = new StringBuilder();
        collect(wurzel, lines, running);
        finish(lines, running);
        return lines;
    }

    private static void collect(Node node, List<String> lines, StringBuilder laufend) {
        if (node instanceof TextNode text) {
            laufend.append(text.text());
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }

        String mark = element.normalName();
        if (SKIPPED.contains(mark)) {
            return;
        }
        if (mark.equals("br")) {
            laufend.append(' ');
            return;
        }
        if (mark.equals("table")) {
            finish(lines, laufend);
            collectTable(element, lines);
            return;
        }

        boolean block = BLOCK_ELEMENTS.contains(mark);
        if (block) {
            finish(lines, laufend);
        }
        for (Node kind : element.childNodes()) {
            collect(kind, lines, laufend);
        }
        if (block) {
            finish(lines, laufend);
        }
    }

    private static void collectTable(Element tabelle, List<String> lines) {
        Elements headerRows = tabelle.select("thead tr");
        List<String> columns = headerRows.isEmpty()
                ? List.of()
                : headerRows.last().select("th, td").stream().map(HtmlParser::cellText).toList();

        Elements dataRows = tabelle.select("tbody tr");
        if (dataRows.isEmpty()) {
            dataRows = tabelle.select("tr").not("thead tr");
        }

        if (!columns.isEmpty()) {
            lines.add(String.join(" | ", columns));
        }

        for (Element reihe : dataRows) {
            List<String> values = reihe.select("th, td").stream()
                    .map(HtmlParser::cellText)
                    .toList();
            if (values.stream().allMatch(String::isEmpty)) {
                continue;
            }
            lines.add(lineFrom(columns, values));
        }
    }

    private static String lineFrom(List<String> spalten, List<String> values) {
        if (spalten.size() != values.size()) {
            return String.join(" | ", values);
        }
        StringBuilder line = new StringBuilder();
        for (int spalte = 0; spalte < values.size(); spalte++) {
            if (values.get(spalte).isEmpty()) {
                continue;
            }
            if (!line.isEmpty()) {
                line.append("; ");
            }
            if (!spalten.get(spalte).isEmpty()) {
                line.append(spalten.get(spalte)).append(": ");
            }
            line.append(values.get(spalte));
        }
        return line.toString();
    }

    private static String cellText(Element zelle) {
        return GeneratedText.normalize(zelle.text());
    }

    private static void finish(List<String> lines, StringBuilder laufend) {
        String line = GeneratedText.normalize(laufend.toString());
        laufend.setLength(0);
        if (!line.isEmpty()) {
            lines.add(line);
        }
    }

    private List<String> cut(List<String> lines) {
        List<String> partPieces = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int laenge = 0;

        for (String line : lines) {
            for (String stueck : splitLine(line)) {
                if (!current.isEmpty() && laenge + 1 + stueck.length() > maxChars) {
                    partPieces.add(String.join("\n", current));
                    current = overlapOf(current);
                    laenge = totalLength(current);
                }
                laenge += (current.isEmpty() ? 0 : 1) + stueck.length();
                current.add(stueck);
            }
        }
        if (!current.isEmpty()) {
            partPieces.add(String.join("\n", current));
        }
        return partPieces;
    }

    private List<String> overlapOf(List<String> abgeschlossen) {
        List<String> adopted = new ArrayList<>();
        int laenge = 0;
        for (int position = abgeschlossen.size() - 1; position >= 0; position--) {
            String line = abgeschlossen.get(position);
            if (laenge + line.length() > overlap) {
                if (adopted.isEmpty()) {
                    String end = endOf(line, overlap);
                    if (!end.isEmpty()) {
                        adopted.add(end);
                    }
                }
                break;
            }
            adopted.addFirst(line);
            laenge += line.length();
        }
        return adopted;
    }

    private List<String> splitLine(String line) {
        if (line.length() <= maxChars) {
            return List.of(line);
        }
        List<String> pieces = new ArrayList<>();
        int von = 0;
        while (von < line.length()) {
            int bis = Math.min(von + maxChars, line.length());
            if (bis < line.length()) {
                int trennung = line.lastIndexOf(' ', bis);
                if (trennung > von) {
                    bis = trennung;
                }
            }
            pieces.add(line.substring(von, bis).strip());
            if (bis >= line.length()) {
                break;
            }
            von = Math.max(von + 1, bis - overlap);
        }
        return pieces;
    }

    private static String endOf(String line, int chars) {
        if (line.length() <= chars) {
            return line;
        }
        int von = line.length() - chars;
        int trennung = line.indexOf(' ', von);
        return line.substring(trennung >= 0 ? trennung + 1 : von).strip();
    }

    private static int totalLength(List<String> lines) {
        int laenge = 0;
        for (String line : lines) {
            laenge += (laenge == 0 ? 0 : 1) + line.length();
        }
        return laenge;
    }
}
