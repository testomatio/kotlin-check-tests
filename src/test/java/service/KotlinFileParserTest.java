package service;

import io.testomat.model.ParsedKtFile;
import io.testomat.service.KotlinFileParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class KotlinFileParserTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldParseKotlinFile() throws IOException {
        String code = """
            fun test() {}
        """;

        Path file = tempDir.resolve("test.kt");
        Files.writeString(file, code);

        ParsedKtFile parsedKtFile = KotlinFileParser.parseFile(file);

        assertNotNull(parsedKtFile);
        assertNotNull(parsedKtFile.getKtFile());
        assertEquals(file, parsedKtFile.getPath());

        String text = parsedKtFile.getKtFile().getText();
        assertTrue(text.contains("fun test"));
    }

    @Test
    void shouldHandleEmptyFile() throws IOException {
        Path file = tempDir.resolve("empty.kt");
        Files.writeString(file, "");

        ParsedKtFile parsedKtFile = KotlinFileParser.parseFile(file);

        assertNotNull(parsedKtFile.getKtFile());
    }

    @Test
    void shouldThrowWhenFileNotExists() {
        Path file = tempDir.resolve("not_exists.kt");

        assertThrows(Exception.class, () ->
            KotlinFileParser.parseFile(file)
        );
    }

}
