package de.atra.kernsystem.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class ApiKeyFilterTest {

    @Autowired
    MockMvcTester mvc;

    @TestConfiguration
    static class NurFuerDenTest {
        @RestController
        static class Testendpoint {
            @GetMapping("/testpfad")
            String hello() {
                return "durchgelassen";
            }

            @GetMapping("/fehler")
            String throwsError() {
                throw new RuntimeException("Unerwarteter Fehler im Test");
            }
        }
    }

    @Test
    void ohne_schluessel_kommt_401_als_problem_json() {
        assertThat(mvc.get().uri("/api/v1/testpfad"))
                .hasStatus(401)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.status").isEqualTo(401);
    }

    @Test
    void mit_falschem_schluessel_kommt_401() {
        assertThat(mvc.get().uri("/api/v1/testpfad").header("x-api-key", "falsch"))
                .hasStatus(401);
    }

    @Test
    void with_the_right_key_the_request_gets_through() {
        assertThat(mvc.get().uri("/api/v1/testpfad").header("x-api-key", "atra-lab-2026"))
                .hasStatusOk()
                .hasBodyTextEqualTo("durchgelassen");
    }

    @Test
    void the_readiness_probe_needs_no_key() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status").isEqualTo("UP");
    }

    @Test
    void the_mcp_endpoint_is_not_exempt() {
        assertThat(mvc.post().uri("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .hasStatus(401);
    }

    @Test
    void unerwartete_exception_kommt_als_500_problem_json() {
        assertThat(mvc.get().uri("/api/v1/fehler").header("x-api-key", "atra-lab-2026"))
                .hasStatus(500)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.type").isEqualTo("https://atra.example/fehler/interner-fehler");

        assertThat(mvc.get().uri("/api/v1/fehler").header("x-api-key", "atra-lab-2026"))
                .bodyJson()
                .extractingPath("$.title").isEqualTo("Interner Fehler");

        assertThat(mvc.get().uri("/api/v1/fehler").header("x-api-key", "atra-lab-2026"))
                .bodyJson()
                .extractingPath("$.status").isEqualTo(500);

        assertThat(mvc.get().uri("/api/v1/fehler").header("x-api-key", "atra-lab-2026"))
                .bodyJson()
                .extractingPath("$.detail").asString()
                .doesNotContain("Unerwarteter Fehler im Test")
                .doesNotContain("RuntimeException");
    }
}
