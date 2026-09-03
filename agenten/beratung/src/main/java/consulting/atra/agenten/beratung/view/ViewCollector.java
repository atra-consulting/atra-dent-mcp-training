package consulting.atra.agenten.beratung.view;

import consulting.atra.agenten.a2a.agent.TracePoint;
import consulting.atra.agenten.mcp.ObservedTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class ViewCollector {

    public static final String KIND = "kind";

    public static final String TARIFVERGLEICH = "tarifvergleich";
    public static final String BEITRAG_COMPARISON = "beitragsvergleich";
    public static final String TARIFEMPFEHLUNG = "tarifempfehlung";
    public static final String VERTRAG = "vertrag";
    public static final String SCHADENSFAELLE = "schadensfaelle";
    public static final String KONTAKTDATEN = "kontaktdaten";

    public static final String VERTRAG_TARIF = "vertragstarif";

    public static final int MAX_COUNT = 2;

    static final int AT_LEAST_TWO = 2;

    private static final String ASSEMBLED = "lege Ihnen die Angaben daneben";

    private static final String TOOL_COMPARISON = "tarife_vergleichen";
    private static final String TOOL_LIST = "tarife_auflisten";
    private static final String TOOL_TARIF = "tarif_lesen";
    private static final String TOOL_BEITRAG = "beitrag_berechnen";
    private static final String TOOL_OWN_BEITRAG = "mein_beitrag_berechnen";
    private static final String TOOL_RECOMMENDATION = "tarifempfehlung";
    private static final String TOOL_VERTRAG = "mein_vertrag_lesen";
    private static final String TOOL_SCHADENSFAELLE = "meine_schadensfaelle_auflisten";
    private static final String TOOL_CONTACT = "meine_kontaktdaten_aendern";
    static final String TOOL_EINREICHEN = "schadensfall_einreichen";

    private static final Logger log = LoggerFactory.getLogger(ViewCollector.class);

    private final ObjectMapper mapper;

    public ViewCollector(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "abbilder");
    }

    public List<Map<String, Object>> collect(ObservedTools observed) {
        List<Map<String, Object>> views = new ArrayList<>();
        boolean submitted = last(observed, TOOL_EINREICHEN).isPresent();
        kontaktdaten(observed).ifPresent(views::add);
        if (submitted) {
            schadensfaelle(observed).ifPresent(views::add);
        }
        tarifvergleich(observed).ifPresent(views::add);
        beitragComparison(observed).ifPresent(views::add);
        tarifempfehlung(observed).ifPresent(views::add);
        if (!submitted) {
            schadensfaelle(observed).ifPresent(views::add);
        }
        if (views.isEmpty()) {
            vertrag(observed).ifPresent(views::add);
        }
        return List.copyOf(views.size() > MAX_COUNT ? views.subList(0, MAX_COUNT) : views);
    }

    public Optional<TracePoint> trace(String sender, List<Map<String, Object>> views) {
        if (views == null || views.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kinds", views.stream().map(view -> view.get(KIND)).toList());
        alsText(views).ifPresent(text -> data.put("data", TracePoint.truncate(text)));
        return Optional.of(TracePoint.internal(sender, "views", ASSEMBLED,
                "Darstellungen zusammenstellen", data));
    }

    private Optional<String> alsText(List<Map<String, Object>> views) {
        try {
            return Optional.of(mapper.writeValueAsString(views));
        } catch (RuntimeException unschreibbar) {
            log.warn("Darstellungen liessen sich nicht fuer den Trace schreiben", unschreibbar);
            return Optional.empty();
        }
    }


    private Optional<Map<String, Object>> tarifvergleich(ObservedTools observed) {
        return last(observed, TOOL_COMPARISON).map(root -> {
            Map<String, Object> content = new LinkedHashMap<>(simpleObject(root));
            vertragTarif(observed).ifPresent(tarif -> content.put(VERTRAG_TARIF, tarif));
            return view(TARIFVERGLEICH, content);
        });
    }

    private Optional<Map<String, Object>> beitragComparison(ObservedTools observed) {
        Map<String, String> names = tarifNames(observed);
        Map<String, Map<String, Object>> beitraege = new LinkedHashMap<>();

        for (String tool : List.of(TOOL_BEITRAG, TOOL_OWN_BEITRAG)) {
            for (ObservedTools.Toolaufruf call : observed.callsOf(tool)) {
                try {
                    String tarif = text(mapper.readTree(call.arguments()), "tarifId");
                    String monthlyBeitrag =
                            text(mapper.readTree(call.content()), "monatsbeitrag");
                    if (tarif == null || monthlyBeitrag == null) {
                        continue;
                    }
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("tarif", tarif);
                    entry.put("anzeigename", names.get(tarif));
                    entry.put("monatsbeitrag", monthlyBeitrag);
                    beitraege.put(tarif, entry);
                } catch (RuntimeException unreadable) {
                    log.warn("Aufruf von {} liess sich nicht lesen; Beitrag entfaellt",
                            tool, unreadable);
                }
            }
        }

        if (beitraege.size() < AT_LEAST_TWO) {
            return Optional.empty();
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("beitraege", List.copyOf(beitraege.values()));
        vertragTarif(observed).ifPresent(tarif -> content.put(VERTRAG_TARIF, tarif));
        return Optional.of(view(BEITRAG_COMPARISON, content));
    }

    private Optional<Map<String, Object>> tarifempfehlung(ObservedTools observed) {
        return last(observed, TOOL_RECOMMENDATION).flatMap(root -> {
            List<Map<String, Object>> empfehlungen = new ArrayList<>();
            for (JsonNode empfehlung : list(root, "empfehlungen")) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("rang", number(empfehlung, "rang"));
                entry.put("tarif", text(empfehlung, "tarifschluessel"));
                entry.put("anzeigename", text(empfehlung, "anzeigename"));
                empfehlungen.add(entry);
            }
            if (empfehlungen.isEmpty()) {
                return Optional.empty();
            }
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("empfehlungen", empfehlungen);
            vertragTarif(observed).ifPresent(tarif -> content.put(VERTRAG_TARIF, tarif));
            return Optional.of(view(TARIFEMPFEHLUNG, content));
        });
    }

    private Optional<Map<String, Object>> vertrag(ObservedTools observed) {
        return last(observed, TOOL_VERTRAG).map(root -> {
            Map<String, Object> vertrag = new LinkedHashMap<>();
            vertrag.put("vorname", text(root, "vorname"));
            vertrag.put("tarif", text(root, "tarifId"));
            vertrag.put("versicherungsbeginn", text(root, "versicherungsbeginn"));
            vertrag.put("status", text(root, "status"));
            vertrag.put("vorversicherung", truth(root, "vorversicherung"));
            vertrag.put("fehlendeZaehne", number(root, "fehlendeZaehne"));
            vertrag.put("anzeigename", tarifNames(observed).get(text(root, "tarifId")));
            return view(VERTRAG, vertrag);
        });
    }

    private Optional<Map<String, Object>> kontaktdaten(ObservedTools observed) {
        return last(observed, TOOL_CONTACT).map(root -> {
            Map<String, Object> kontakt = new LinkedHashMap<>();
            kontakt.put("vorname", text(root, "vorname"));
            kontakt.put("email", text(root, "email"));
            kontakt.put("telefon", text(root, "telefon"));
            JsonNode adresse = root.get("adresse");
            if (adresse != null && adresse.isObject()) {
                Map<String, Object> anschrift = new LinkedHashMap<>();
                anschrift.put("strasse", text(adresse, "strasse"));
                anschrift.put("plz", text(adresse, "plz"));
                anschrift.put("ort", text(adresse, "ort"));
                anschrift.put("land", text(adresse, "land"));
                kontakt.put("adresse", anschrift);
            }
            return view(KONTAKTDATEN, kontakt);
        });
    }

    private Optional<Map<String, Object>> schadensfaelle(ObservedTools observed) {
        Optional<JsonNode> list = last(observed, TOOL_SCHADENSFAELLE);
        List<JsonNode> source;
        if (list.isPresent()) {
            source = elements(list.get(), "schadensfaelle");
        } else {
            source = last(observed, TOOL_EINREICHEN).map(List::of).orElse(List.of());
        }
        List<Map<String, Object>> faelle = new ArrayList<>();
        for (JsonNode fall : source) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", longNumber(fall, "id"));
            entry.put("behandlungsdatum", text(fall, "behandlungsdatum"));
            entry.put("rechnungsbetrag", text(fall, "rechnungsbetrag"));
            entry.put("erstattungsbetrag", text(fall, "erstattungsbetrag"));
            entry.put("status", text(fall, "status"));
            entry.put("ablehnungsgrund", text(fall, "ablehnungsgrund"));
            entry.put("ablehnungshinweis", text(fall, "ablehnungshinweis"));
            JsonNode bewertung = fall.get("bewertung");
            boolean bewertet = bewertung != null && !bewertung.isNull();
            entry.put("erstattungsvorschlag", bewertet ? text(bewertung, "erstattungsvorschlag") : null);
            entry.put("empfehlung", bewertet ? text(bewertung, "empfehlung") : null);
            faelle.add(entry);
        }
        if (faelle.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(view(SCHADENSFAELLE, Map.of("faelle", faelle)));
    }


    private Optional<Map<String, Object>> vertragTarif(ObservedTools observed) {
        return last(observed, TOOL_VERTRAG)
                .map(root -> text(root, "tarifId"))
                .map(key -> {
                    Map<String, Object> tarif = new LinkedHashMap<>();
                    tarif.put("schluessel", key);
                    tarif.put("anzeigename", tarifNames(observed).get(key));
                    return tarif;
                });
    }

    private Map<String, String> tarifNames(ObservedTools observed) {
        Map<String, String> names = new LinkedHashMap<>();
        last(observed, TOOL_LIST).ifPresent(root -> {
            for (JsonNode tarif : elements(root, "tarife")) {
                record(names, text(tarif, "id"), text(tarif, "name"));
            }
        });
        for (String result : observed.resultsOf(TOOL_TARIF)) {
            read(result, TOOL_TARIF).ifPresent(
                    tarif -> record(names, text(tarif, "id"), text(tarif, "name")));
        }
        last(observed, TOOL_COMPARISON).ifPresent(root -> {
            for (JsonNode tarif : list(root, "tarife")) {
                record(names, text(tarif, "schluessel"), text(tarif, "anzeigename"));
            }
        });
        return names;
    }

    private static void record(Map<String, String> names, String key, String name) {
        if (key != null && name != null) {
            names.put(key, name);
        }
    }

    private static Map<String, Object> view(String kind, Map<String, Object> content) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put(KIND, kind);
        view.putAll(content);
        return view;
    }

    private Optional<JsonNode> last(ObservedTools observed, String tool) {
        List<String> results = observed.resultsOf(tool);
        return results.isEmpty() ? Optional.empty() : read(results.getLast(), tool);
    }

    private Optional<JsonNode> read(String result, String tool) {
        try {
            return Optional.ofNullable(mapper.readTree(result));
        } catch (RuntimeException unreadable) {
            log.warn("Ergebnis von {} liess sich nicht lesen; Darstellung entfaellt",
                    tool, unreadable);
            return Optional.empty();
        }
    }

    private static List<JsonNode> elements(JsonNode root, String field) {
        if (!root.isArray()) {
            return list(root, field);
        }
        List<JsonNode> alle = new ArrayList<>();
        root.forEach(alle::add);
        return alle;
    }

    private static List<JsonNode> list(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<JsonNode> alle = new ArrayList<>();
        value.forEach(alle::add);
        return alle;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static Integer number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? null : value.asInt();
    }

    private static Long longNumber(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.canConvertToLong() ? null : value.asLong();
    }

    private static Boolean truth(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isBoolean() ? null : value.asBoolean();
    }

    private static Object simple(JsonNode node) {
        if (node.isObject()) {
            Map<String, Object> image = new LinkedHashMap<>();
            node.properties().forEach(field -> image.put(field.getKey(), simple(field.getValue())));
            return image;
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(value -> values.add(simple(value)));
            return values;
        }
        if (node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        return node.asString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> simpleObject(JsonNode node) {
        Object image = simple(node);
        return image instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
