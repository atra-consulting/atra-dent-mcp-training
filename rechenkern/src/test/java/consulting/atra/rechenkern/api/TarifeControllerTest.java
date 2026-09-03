package consulting.atra.rechenkern.api;

import consulting.atra.rechenkern.domain.TarifService;
import consulting.atra.rechenkern.generated.model.Tarif;
import consulting.atra.rechenkern.generated.model.TarifId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class TarifeControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TarifService service;

    @Test
    void tarife_json_covers_the_TarifId_enum_completely() {
        assertThat(service.all())
                .extracting(Tarif::getId)
                .containsExactlyInAnyOrder(TarifId.values());
    }

    @Test
    void listing_returns_all_four_tarife() {
        assertThat(mvc.get().uri("/api/v1/tarife"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.length()").isEqualTo(4);
    }

    @Test
    void monthly_basisbeitrag_comes_out_as_a_decimal_string() {
        assertThat(mvc.get().uri("/api/v1/tarife/ATRA_DENT_B"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.basisbeitragMonatlich").isEqualTo("15.90");
    }

    @Test
    void reading_returns_the_anzeigename_from_tarife_json() {
        assertThat(mvc.get().uri("/api/v1/tarife/ATRA_DENT_X"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.name").isEqualTo("atra.dent.brillant");
    }

    @Test
    void an_unknown_tarif_key_is_a_400_because_the_enum_catches_it() {
        assertThat(mvc.get().uri("/api/v1/tarife/GIBT_ES_NICHT"))
                .hasStatus(400);
    }
}
