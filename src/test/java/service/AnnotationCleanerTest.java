package service;

import io.testomat.model.CleanupResult;
import io.testomat.model.ParsedKtFile;
import io.testomat.service.AnnotationCleaner;
import io.testomat.service.KotlinFileParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationCleanerTest {

    @TempDir
    Path tempDir;

    private AnnotationCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner = new AnnotationCleaner();
    }

    private ParsedKtFile createparsedKtFile(String code) throws IOException {
        Path path = tempDir.resolve("test.kt");
        Files.writeString(path, code);

        return KotlinFileParser.parseFile(path);
    }

    @Test
    void shouldRemoveTestIdAnnotation() throws IOException {
        String code = """
                import io.testomat.core.annotation.TestId

                @TestId("123")
                fun test() {}
            """;

        ParsedKtFile parsedKtFile = createparsedKtFile(code);

        CleanupResult result = cleaner.cleanTestIdAnnotations(parsedKtFile, false);

        String updated = Files.readString(parsedKtFile.getPath());

        assertFalse(updated.contains("@TestId"));
        assertEquals(1, result.getRemovedAnnotations());
    }

    @Test
    void shouldNotModifyFileInDryRun() throws IOException {
        String code = """
                @TestId("123")
                fun test() {}
            """;

        ParsedKtFile parsedKtFile = createparsedKtFile(code);

        cleaner.cleanTestIdAnnotations(parsedKtFile, true);

        String updated = Files.readString(parsedKtFile.getPath());

        assertTrue(updated.contains("@TestId"));
    }

    @Test
    void shouldRemoveImport() throws IOException {
        String code = """
                import io.testomat.core.annotation.TestId

                fun test() {}
            """;

        ParsedKtFile parsedKtFile = createparsedKtFile(code);

        cleaner.cleanTestIdAnnotations(parsedKtFile, false);

        String updated = Files.readString(parsedKtFile.getPath());

        assertFalse(updated.contains("import io.testomat.core.annotation.TestId"));
    }

    @Test
    void shouldRemoveMultilineAnnotation() throws IOException {
        String code = """
                @TestId(
                    "123"
                )
                fun test() {}
            """;

        ParsedKtFile parsedKtFile = createparsedKtFile(code);

        cleaner.cleanTestIdAnnotations(parsedKtFile, false);

        String updated = Files.readString(parsedKtFile.getPath());

        assertFalse(updated.contains("@TestId"));
    }

    @Test
    void shouldNotRemoveOtherAnnotations() throws IOException {
        String code = """
                @Test
                fun test() {}
            """;

        ParsedKtFile parsedKtFile = createparsedKtFile(code);

        cleaner.cleanTestIdAnnotations(parsedKtFile, false);

        String updated = Files.readString(parsedKtFile.getPath());

        assertTrue(updated.contains("@Test"));
    }

    @Test
    void shouldRemoveMultipleTestIdAnnotations() throws IOException {
        String code = """
                @TestId("1")
                fun test1() {}

                @TestId("2")
                fun test2() {}
            """;

        ParsedKtFile parsedKtFile = createparsedKtFile(code);

        CleanupResult result = cleaner.cleanTestIdAnnotations(parsedKtFile, false);

        String updated = Files.readString(parsedKtFile.getPath());

        assertFalse(updated.contains("@TestId"));
        assertEquals(2, result.getRemovedAnnotations());
    }

    @Test
    void shouldRemoveOnlyTestIdKeepOtherAnnotations() throws IOException {
        String code = """
                @Test
                @TestId("123")
                fun test() {}
            """;

        ParsedKtFile parsedKtFile = createparsedKtFile(code);

        cleaner.cleanTestIdAnnotations(parsedKtFile, false);

        String updated = Files.readString(parsedKtFile.getPath());

        assertFalse(updated.contains("@TestId"));
        assertTrue(updated.contains("@Test"));
    }

    @Test
    void shouldNotLeaveExtraEmptyLines() throws IOException {
        String code = """
                @TestId("123")

                fun test() {}
            """;

        ParsedKtFile parsedKtFile = createparsedKtFile(code);

        cleaner.cleanTestIdAnnotations(parsedKtFile, false);

        String updated = Files.readString(parsedKtFile.getPath());

        assertFalse(updated.contains("\n\n\n"));
    }

    @Test
    void shouldRemoveAnnotationAndImportTogether() throws IOException {
        String code = """
                import io.testomat.core.annotation.TestId

                @TestId("123")
                fun test() {}
            """;

        ParsedKtFile parsedKtFile = createparsedKtFile(code);

        CleanupResult result = cleaner.cleanTestIdAnnotations(parsedKtFile, false);

        String updated = Files.readString(parsedKtFile.getPath());

        assertFalse(updated.contains("@TestId"));
        assertFalse(updated.contains("import io.testomat.core.annotation.TestId"));

        assertEquals(1, result.getRemovedAnnotations());
        assertEquals(1, result.getRemovedImports());
    }

    @Test
    void shouldRemoveAnnotationWithoutArguments() throws IOException {
        String code = """
                @TestId
                fun test() {}
            """;

        ParsedKtFile parsedKtFile = createparsedKtFile(code);

        cleaner.cleanTestIdAnnotations(parsedKtFile, false);

        String updated = Files.readString(parsedKtFile.getPath());

        assertFalse(updated.contains("@TestId"));
    }
}
