package service;

import io.testomat.model.ParsedKtFile;
import io.testomat.service.KotlinFileParser;
import io.testomat.service.TestIdAnnotationManager;
import io.testomat.service.TestIdAnnotationManager.TestMethodInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class TestIdAnnotationManagerTest {

    private final TestIdAnnotationManager manager = new TestIdAnnotationManager();

    @TempDir
    Path tempDir;

    private ParsedKtFile createParsedFile(String fileName, String code) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, code);
        return KotlinFileParser.parseFile(file);
    }

    @Test
    void shouldFindMethodInCorrectClass() throws IOException {
        ParsedKtFile parsedKtFile = createParsedFile("MyTest.kt", """
            class MyTest {
                fun testMethod() {}
            }
        """);

        TestMethodInfo info = new TestMethodInfo(
            "MyTest.kt",
            "MyTest",
            "testMethod"
        );

        Optional<KtNamedFunction> result =
            manager.findMethodInParsedKtFiles(List.of(parsedKtFile), info, false);

        assertTrue(result.isPresent());
        assertEquals("testMethod", result.get().getName());
    }

    @Test
    void shouldNotFindMethodIfNameDifferent() throws IOException {
        ParsedKtFile parsedKtFile = createParsedFile("MyTest.kt", """
            class MyTest {
                fun otherMethod() {}
            }
        """);

        TestMethodInfo info = new TestMethodInfo(
            "MyTest.kt",
            "MyTest",
            "testMethod"
        );

        Optional<KtNamedFunction> result =
            manager.findMethodInParsedKtFiles(List.of(parsedKtFile), info, false);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldNotFindMethodIfClassDifferent() throws IOException {
        ParsedKtFile parsedKtFile = createParsedFile("MyTest.kt", """
            class AnotherClass {
                fun testMethod() {}
            }
        """);

        TestMethodInfo info = new TestMethodInfo(
            "MyTest.kt",
            "MyTest",
            "testMethod"
        );

        Optional<KtNamedFunction> result =
            manager.findMethodInParsedKtFiles(List.of(parsedKtFile), info, false);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFindMethodAcrossMultipleFiles() throws IOException {
        ParsedKtFile file1 = createParsedFile("A.kt", """
            class A {
                fun testMethod() {}
            }
        """);

        ParsedKtFile file2 = createParsedFile("B.kt", """
            class B {
                fun anotherMethod() {}
            }
        """);

        TestMethodInfo info = new TestMethodInfo(
            "A.kt",
            "A",
            "testMethod"
        );

        Optional<KtNamedFunction> result =
            manager.findMethodInParsedKtFiles(List.of(file1, file2), info, false);

        assertTrue(result.isPresent());
        assertEquals("testMethod", result.get().getName());
    }

    @Test
    void shouldMatchByPathWhenFilenameDoesNotMatch() throws IOException {
        ParsedKtFile parsedKtFile = createParsedFile("MyTest.kt", """
            class MyTest {
                fun testMethod() {}
            }
        """);

        TestMethodInfo info = new TestMethodInfo(
            tempDir.resolve("MyTest.kt").toString(),
            "MyTest",
            "testMethod"
        );

        Optional<KtNamedFunction> result =
            manager.findMethodInParsedKtFiles(List.of(parsedKtFile), info, false);

        assertTrue(result.isPresent());
    }

    @Test
    void shouldReturnEmptyWhenNoFilesMatch() throws IOException {
        ParsedKtFile parsedKtFile = createParsedFile("Other.kt", """
            class Other {
                fun testMethod() {}
            }
        """);

        TestMethodInfo info = new TestMethodInfo(
            "MyTest.kt",
            "MyTest",
            "testMethod"
        );

        Optional<KtNamedFunction> result =
            manager.findMethodInParsedKtFiles(List.of(parsedKtFile), info, false);

        assertTrue(result.isEmpty());
    }

}
