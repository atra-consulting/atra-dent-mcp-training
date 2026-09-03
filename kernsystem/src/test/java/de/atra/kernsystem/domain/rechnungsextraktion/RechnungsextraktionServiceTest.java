package de.atra.kernsystem.domain.rechnungsextraktion;

import de.atra.kernsystem.domain.DomainException;
import de.atra.kernsystem.generated.model.ExtrahierteRechnungsposition;
import de.atra.kernsystem.generated.model.RechnungsextraktionResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RechnungsextraktionServiceTest {

    static final Path PDF_DIRECTORY = Path.of("..", "submissions", "rechnungen", "pdf");
    static final Path CASE_DIRECTORY = Path.of("..", "submissions", "rechnungen", "cases");

    final RechnungsextraktionService service = new RechnungsextraktionService();

    static Stream<String> cases() throws IOException {
        try (Stream<Path> dateien = Files.list(CASE_DIRECTORY)) {
            return dateien
                    .filter(p -> p.toString().endsWith(".yaml"))
                    .map(p -> p.getFileName().toString().replace(".yaml", ""))
                    .sorted()
                    .toList().stream();
        }
    }

    @ParameterizedTest
    @MethodSource("cases")
    @SuppressWarnings("unchecked")
    void extracts_the_ground_truth_from_the_pdf(String fallName) throws IOException {
        Map<String, Object> fall = new Yaml().load(
                Files.readString(CASE_DIRECTORY.resolve(fallName + ".yaml"), StandardCharsets.UTF_8));
        byte[] pdf = Files.readAllBytes(PDF_DIRECTORY.resolve(fallName + ".pdf"));

        RechnungsextraktionResult result = service.extract(pdf);

        assertThat(result.getRechnungsnummer()).isEqualTo(fall.get("rechnungsnummer"));
        assertThat(result.getRechnungsdatum()).isEqualTo(datum(fall.get("rechnungsdatum")));
        assertThat(result.getAbsender()).isNotBlank();

        Map<String, Object> patient = (Map<String, Object>) fall.get("patient");
        Map<String, Object> anschrift = (Map<String, Object>) patient.get("anschrift");
        assertThat(result.getPatient()).isNotNull();
        assertThat(result.getPatient().getName()).isEqualTo(patient.get("name"));
        assertThat(result.getPatient().getStrasse()).isEqualTo(anschrift.get("strasse"));
        assertThat(result.getPatient().getPlz()).isEqualTo(anschrift.get("plz"));
        assertThat(result.getPatient().getOrt()).isEqualTo(anschrift.get("ort"));

        List<Map<String, Object>> sollPositionen = (List<Map<String, Object>>) fall.get("positionen");
        assertThat(result.getPositionen()).hasSameSizeAs(sollPositionen);

        List<Map<String, Object>> soll = sollPositionen.stream()
                .sorted(Comparator
                        .comparing((Map<String, Object> p) -> amount(p.get("betrag")))
                        .thenComparing(p -> (String) p.get("leistung"))
                        .thenComparing(p -> String.valueOf(p.get("zahn"))))
                .toList();
        List<ExtrahierteRechnungsposition> ist = result.getPositionen().stream()
                .sorted(Comparator
                        .comparing(ExtrahierteRechnungsposition::getBetrag)
                        .thenComparing(ExtrahierteRechnungsposition::getLeistung)
                        .thenComparing(p -> String.valueOf(p.getZahn())))
                .toList();

        for (int i = 0; i < soll.size(); i++) {
            checkPosition(ist.get(i), soll.get(i), fallName + " Position " + i);
        }

        BigDecimal expectedTotal = sollPositionen.stream()
                .map(p -> amount(p.get("betrag")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(result.getGesamtbetrag()).isEqualByComparingTo(expectedTotal);

        BigDecimal expectedAmountDue = "weissraum".equals(fall.get("praxis"))
                ? expectedTotal.subtract(alreadyPaid(fall))
                : expectedTotal;
        assertThat(result.getZahlbetrag()).isEqualByComparingTo(expectedAmountDue);
    }

    private static void checkPosition(ExtrahierteRechnungsposition ist,
                                       Map<String, Object> soll, String wo) {
        assertThat(ist.getLeistung()).as(wo).isEqualTo(soll.get("leistung"));
        assertThat(ist.getBetrag()).as(wo).isEqualByComparingTo(amount(soll.get("betrag")));

        String art = (String) soll.get("art");
        if ("goz".equals(art) || "goae".equals(art)) {
            whenPresent(ist.getZiffer(), soll.get("nummer"), wo + " ziffer");
        } else {
            assertThat(ist.getZiffer()).as(wo + " ziffer").isNull();
        }
        whenPresent(ist.getZahn(), soll.get("zahn"), wo + " zahn");
        whenPresent(ist.getAnzahl(), soll.get("anzahl"), wo + " anzahl");
        whenPresent(ist.getDatum(), datum(soll.get("datum")), wo + " datum");
        Object expectedFaktor = soll.get("faktor");
        whenPresent(ist.getFaktor(),
                expectedFaktor == null ? null : BigDecimal.valueOf(((Number) expectedFaktor).doubleValue()).toPlainString(),
                wo + " faktor");
    }

    private static void whenPresent(Object ist, Object soll, String wo) {
        if (ist != null) {
            assertThat(ist).as(wo).isEqualTo(soll);
        }
    }

    @Test
    void a_pdf_without_a_pdfa_marker_is_rejected_generically() throws IOException {
        byte[] pdfOhneKennzeichnung = leeresPdf();
        assertThatThrownBy(() -> service.extract(pdfOhneKennzeichnung))
                .isInstanceOf(DomainException.class)
                .hasMessage(RechnungsextraktionService.GENERIC_MESSAGE);
    }

    @Test
    void garbage_instead_of_a_pdf_is_rejected_generically() {
        byte[] keinPdf = "kein PDF".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.extract(keinPdf))
                .isInstanceOf(DomainException.class)
                .hasMessage(RechnungsextraktionService.GENERIC_MESSAGE);
    }

    private static byte[] leeresPdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream puffer = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(puffer);
            return puffer.toByteArray();
        }
    }

    private static LocalDate datum(Object value) {
        return value == null ? null : LocalDate.ofInstant(((Date) value).toInstant(), ZoneOffset.UTC);
    }

    private static BigDecimal amount(Object value) {
        return BigDecimal.valueOf(((Number) value).doubleValue());
    }

    @SuppressWarnings("unchecked")
    private static BigDecimal alreadyPaid(Map<String, Object> fall) {
        Map<String, Object> zahlung = (Map<String, Object>) fall.get("zahlung");
        return zahlung == null ? BigDecimal.ZERO : amount(zahlung.get("bereits_gezahlt"));
    }
}
