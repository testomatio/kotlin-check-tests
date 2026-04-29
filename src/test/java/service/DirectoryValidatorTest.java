package service;

import io.testomat.exception.CliException;
import io.testomat.service.DirectoryValidator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class DirectoryValidatorTest {

    private final DirectoryValidator validator = new DirectoryValidator();

    @TempDir
    Path tempDir;

    @Test
    void shouldThrowWhenDirectoryIsNull() {
        assertThrows(CliException.class, () ->
            validator.validateDirectory(null)
        );
    }

    @Test
    void shouldThrowWhenDirectoryDoesNotExist() {
        File dir = new File(tempDir.toFile(), "not_exists");

        CliException ex = assertThrows(CliException.class, () ->
            validator.validateDirectory(dir)
        );

        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @Test
    void shouldThrowWhenPathIsFile() throws IOException {
        Path file = Files.createTempFile(tempDir, "test", ".txt");

        CliException ex = assertThrows(CliException.class, () ->
            validator.validateDirectory(file.toFile())
        );

        assertTrue(ex.getMessage().contains("not directory"));
    }

    @Test
    void shouldPassForValidDirectory() {
        File dir = tempDir.toFile();

        assertDoesNotThrow(() ->
            validator.validateDirectory(dir)
        );
    }

}
