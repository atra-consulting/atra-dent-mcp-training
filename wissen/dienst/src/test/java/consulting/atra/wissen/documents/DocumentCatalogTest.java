package consulting.atra.wissen.documents;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentCatalogTest {

    private static final String HANDBOOK = "atra-dent-beratungshandbuch";

    private final DocumentCatalog catalog = GeneratedDocuments.catalog();

    @Test
    @DisplayName("all six documents are present in both formats")
    void complete() {
        assertThat(catalog.documents()).hasSize(6);
        assertThat(catalog.documents()).allSatisfy(dokument -> {
            assertThat(dokument.title()).isNotBlank();
            assertThat(Files.isReadable(dokument.pdfDatei())).isTrue();
            assertThat(Files.isReadable(dokument.htmlDatei())).isTrue();
            assertThat(dokument.pdfUrl()).isEqualTo("/dokumente/" + dokument.dokumentId() + "/pdf");
            assertThat(dokument.htmlUrl()).isEqualTo("/dokumente/" + dokument.dokumentId() + "/html");
        });
    }

    @Test
    @DisplayName("every document carries a Vertraulichkeit -- the field may not be missing anywhere")
    void everyDocumentHasAStufe() {
        assertThat(catalog.documents())
                .allSatisfy(dokument -> assertThat(dokument.vertraulichkeit()).isNotNull());
        assertThat(catalog.documents(Confidentiality.OEFFENTLICH).size()
                + catalog.documents(Confidentiality.INTERN).size())
                .isEqualTo(catalog.documents().size());
    }

    @Test
    @DisplayName("the five consumer documents are public, the Handbuch is internal")
    void theStufenAreDistributed() {
        assertThat(catalog.documents(Confidentiality.OEFFENTLICH))
                .extracting(CatalogDocument::dokumentId)
                .containsExactlyInAnyOrder(
                        "atra-dent-smart-avb", "atra-dent-balance-avb", "atra-dent-brillant-avb",
                        "atra-dent-tarifvergleich", "atra-dent-goz-zuordnung");
        assertThat(catalog.documents(Confidentiality.INTERN))
                .extracting(CatalogDocument::dokumentId)
                .containsExactly(HANDBOOK);
    }

    @Test
    @DisplayName("the Vertraulichkeit is not the document type, even where they coincide")
    void theStufeIsNotTheKind() {
        CatalogDocument handbuch = catalog.document(HANDBOOK).orElseThrow();

        assertThat(handbuch.art()).isEqualTo(DocumentType.BERATUNGSHANDBUCH);
        assertThat(handbuch.vertraulichkeit()).isEqualTo(Confidentiality.INTERN);
        assertThat(catalog.documents(Confidentiality.INTERN))
                .allSatisfy(dokument -> assertThat(dokument.vertraulichkeit())
                        .isEqualTo(Confidentiality.INTERN));
    }

    @Test
    @DisplayName("the title comes from the document and carries no punctuation any more")
    void titleFromTheDocument() {
        CatalogDocument balance = catalog.document("atra-dent-balance-avb").orElseThrow();

        assertThat(balance.title())
                .isEqualTo("Allgemeine Versicherungsbedingungen atra.dent.balance");
        assertThat(balance.title()).doesNotContain("\u00AD");
    }

    @Test
    @DisplayName("the cross-Tarif documents carry no Tarif but apply to all")
    void acrossTarife() {
        List<CatalogDocument> crossCutting = catalog.documents().stream()
                .filter(dokument -> dokument.art() != DocumentType.BEDINGUNGSWERK)
                .toList();

        assertThat(crossCutting)
                .extracting(CatalogDocument::dokumentId)
                .containsExactlyInAnyOrder(
                        "atra-dent-tarifvergleich", "atra-dent-goz-zuordnung", HANDBOOK);
        assertThat(crossCutting).allSatisfy(dokument -> {
            assertThat(dokument.tarif()).isEmpty();
            assertThat(dokument.gueltigFuer()).containsExactlyInAnyOrder(
                    "ATRA_DENT_S", "ATRA_DENT_B", "ATRA_DENT_X", "ATRA_DENT_X_SB");
        });
    }

    @Test
    @DisplayName("the Handbuch carries no Tarif -- it guides the conversation, not a Vertrag")
    void handbuchWithoutTarif() {
        CatalogDocument handbuch = catalog.document(HANDBOOK).orElseThrow();

        assertThat(handbuch.tarif()).isEmpty();
        assertThat(handbuch.giltFuer("ATRA_DENT_S")).isTrue();
        assertThat(handbuch.giltFuer("ATRA_DENT_X_SB")).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ATRA_DENT_S", "ATRA_DENT_B", "ATRA_DENT_X", "ATRA_DENT_X_SB"})
    @DisplayName("every Tarif has a Bedingungswerk, both cross-Tarif documents and the Handbuch")
    void everyTarifIsCovered(String tarif) {
        List<CatalogDocument> valid = catalog.fuerTarif(tarif);

        assertThat(valid).hasSize(4);
        assertThat(valid).filteredOn(dokument -> dokument.art() == DocumentType.BEDINGUNGSWERK)
                .hasSize(1);
        assertThat(valid).extracting(CatalogDocument::dokumentId).contains(HANDBOOK);
        assertThat(catalog.bedingungswerk(tarif)).isPresent();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ATRA_DENT_S", "ATRA_DENT_B", "ATRA_DENT_X", "ATRA_DENT_X_SB"})
    @DisplayName("Tarif and Vertraulichkeit together: OEFFENTLICH drops the Handbuch, INTERN keeps only it")
    void tarifAndStufeTogether(String tarif) {
        List<CatalogDocument> publicOnes = catalog.fuerTarif(tarif, Confidentiality.OEFFENTLICH);
        List<CatalogDocument> intern = catalog.fuerTarif(tarif, Confidentiality.INTERN);

        assertThat(publicOnes).hasSize(3);
        assertThat(publicOnes).extracting(CatalogDocument::dokumentId).doesNotContain(HANDBOOK);
        assertThat(publicOnes)
                .allSatisfy(dokument -> assertThat(dokument.giltFuer(tarif)).isTrue());
        assertThat(intern).extracting(CatalogDocument::dokumentId).containsExactly(HANDBOOK);
        assertThat(publicOnes.size() + intern.size()).isEqualTo(catalog.fuerTarif(tarif).size());
    }

    @Test
    @DisplayName("the Handbuch stays retrievable on purpose -- the Vertraulichkeit labels, it does not block")
    void theHandbuchStaysRetrievable() {
        assertThat(catalog.document(HANDBOOK)).isPresent();
        assertThat(catalog.document(HANDBOOK).orElseThrow().vertraulichkeit())
                .isEqualTo(Confidentiality.INTERN);
    }

    @Test
    @DisplayName("the Selbstbehalt variant follows the Bedingungen of brillant")
    void selbstbehaltVariant() {
        CatalogDocument work = catalog.bedingungswerk("ATRA_DENT_X_SB").orElseThrow();

        assertThat(work.dokumentId()).isEqualTo("atra-dent-brillant-avb");
        assertThat(work.tarif()).contains("ATRA_DENT_X");
        assertThat(work.giltFuer("ATRA_DENT_X_SB")).isTrue();
        assertThat(work.giltFuer("ATRA_DENT_B")).isFalse();
    }

    @Test
    @DisplayName("an unknown Tarif is an error and not an empty list -- even with a Vertraulichkeit")
    void unknownTarif() {
        assertThatThrownBy(() -> catalog.fuerTarif("ATRA_DENT_Z"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ATRA_DENT_Z")
                .hasMessageContaining("ATRA_DENT_S");
        assertThatThrownBy(() -> catalog.fuerTarif("ATRA_DENT_Z", Confidentiality.OEFFENTLICH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ATRA_DENT_Z");
    }

    @Test
    @DisplayName("a missing generated directory says what to do")
    void missingDirectory() {
        assertThatThrownBy(() -> DocumentCatalog.read(Path.of("zielt", "ins", "leere")))
                .hasMessageContaining("build.sh");
    }
}
