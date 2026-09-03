package de.atra.kernsystem;

import de.atra.kernsystem.persistence.JsonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class WithWorkingCopy {

    protected static final String API_KEY = "atra-lab-2026";
    private static Path workingCopyDir;

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    private ApplicationContext applicationContext;

    @DynamicPropertySource
    static void workingCopy(DynamicPropertyRegistry registry) throws Exception {
        workingCopyDir = Files.createTempDirectory("kernsystem-test");
        copyFiles();
        registry.add("kernsystem.data-directory", workingCopyDir::toString);
    }

    @BeforeEach
    void resetWorkingCopyBeforeEachTest() throws Exception {
        copyFiles();
        applicationContext.getBeansOfType(JsonRepository.class).values().forEach(JsonRepository::reload);
    }

    private static void copyFiles() throws Exception {
        if (workingCopyDir != null) {
            for (String file : new String[]{"kunden.json", "schadensfaelle.json"}) {
                Files.copy(Path.of("data", file), workingCopyDir.resolve(file),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
