package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.testomat.service.AnnotationScanner;
import io.testomat.service.KotlinFileParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnnotationScannerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should find single annotation above method")
    void shouldFindSingleAnnotation() throws Exception {

        String code = """
                @Test
                fun testMethod() {}
                """;

        KtNamedFunction function = parseFunction(code);

        List<String> annotations =
            AnnotationScanner.findAnnotationsAbove(function);

        assertEquals(1, annotations.size());
        assertEquals("@Test", annotations.get(0));
    }

    @Test
    @DisplayName("Should find multiple annotations in correct order")
    void shouldFindMultipleAnnotations() throws Exception {

        String code = """
                @Test
                @TestId("123")
                fun testMethod() {}
                """;

        KtNamedFunction function = parseFunction(code);

        List<String> annotations =
            AnnotationScanner.findAnnotationsAbove(function);

        assertEquals(2, annotations.size());

        assertEquals("@Test", annotations.get(0));
        assertEquals("@TestId(\"123\")", annotations.get(1));
    }

    @Test
    @DisplayName("Should return empty list when no annotations present")
    void shouldReturnEmptyListWhenNoAnnotations() throws Exception {

        String code = """
                fun testMethod() {}
                """;

        KtNamedFunction function = parseFunction(code);

        List<String> annotations =
            AnnotationScanner.findAnnotationsAbove(function);

        assertTrue(annotations.isEmpty());
    }

    @Test
    @DisplayName("Should stop scanning on non annotation line")
    void shouldStopScanningOnNonAnnotationLine() throws Exception {

        String code = """
                val value = 123

                @Test
                fun testMethod() {}
                """;

        KtNamedFunction function = parseFunction(code);

        List<String> annotations =
            AnnotationScanner.findAnnotationsAbove(function);

        assertEquals(1, annotations.size());
        assertEquals("@Test", annotations.get(0));
    }

    @Test
    @DisplayName("Should handle empty lines between annotation and method")
    void shouldHandleEmptyLines() throws Exception {

        String code = """
                @Test


                fun testMethod() {}
                """;

        KtNamedFunction function = parseFunction(code);

        List<String> annotations =
            AnnotationScanner.findAnnotationsAbove(function);

        assertEquals(1, annotations.size());
        assertEquals("@Test", annotations.get(0));
    }

    @Test
    @DisplayName("Should handle indented annotations")
    void shouldHandleIndentedAnnotations() throws Exception {

        String code = """
                class TestClass {

                    @Test
                    fun testMethod() {}
                }
                """;

        KtNamedFunction function = parseFunction(code);

        List<String> annotations =
            AnnotationScanner.findAnnotationsAbove(function);

        assertEquals(1, annotations.size());
        assertEquals("@Test", annotations.get(0));
    }

    @Test
    @DisplayName("Should ignore annotations below method")
    void shouldIgnoreAnnotationsBelowMethod() throws Exception {

        String code = """
                fun testMethod() {}

                @Test
                fun anotherMethod() {}
                """;

        KtNamedFunction function = parseFunction(code);

        List<String> annotations =
            AnnotationScanner.findAnnotationsAbove(function);

        assertTrue(annotations.isEmpty());
    }

    @Test
    @DisplayName("Should handle inline annotations")
    void shouldHandleInlineAnnotations() throws Exception {

        String code = """
                @Test fun testMethod() {}
                """;

        KtNamedFunction function = parseFunction(code);

        List<String> annotations =
            AnnotationScanner.findAnnotationsAbove(function);

        assertEquals(1, annotations.size());
        assertEquals("@Test fun testMethod() {}", annotations.get(0));
    }

    private KtNamedFunction parseFunction(String code) throws Exception {
        Path path = tempDir.resolve("test.kt");

        Files.writeString(path, code);

        KtFile ktFile = KotlinFileParser.parseFile(path).getKtFile();

        return PsiTreeUtil.findChildOfType(
            ktFile,
            KtNamedFunction.class
        );
    }
}
