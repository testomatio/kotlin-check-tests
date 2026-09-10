package service;

import io.testomat.service.KotlinFileParser;
import io.testomat.service.TestIdUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;
import org.jetbrains.kotlin.psi.KtFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestIdUtilsTest {

    @TempDir
    Path tempDir;

    private KtFile createFile(String code) throws Exception {
        Path file = tempDir.resolve("test.kt");
        Files.writeString(file, code);
        return KotlinFileParser.parseFile(file).getKtFile();
    }

    private KtAnnotationEntry firstAnnotation(KtFile ktFile) {
        return PsiTreeUtil.findChildOfType(ktFile, KtAnnotationEntry.class);
    }

    @Test
    void shouldMatchWithTestomatImport() throws Exception {
        KtFile ktFile = createFile("""
            import io.testomat.core.annotation.TestId

            @TestId("1")
            fun test() {}
        """);

        assertTrue(TestIdUtils.isTestIdAnnotation(firstAnnotation(ktFile), ktFile));
    }

    @Test
    void shouldMatchWithoutImports() throws Exception {
        KtFile ktFile = createFile("""
            @TestId("1")
            fun test() {}
        """);

        assertTrue(TestIdUtils.isTestIdAnnotation(firstAnnotation(ktFile), ktFile));
    }

    @Test
    void shouldMatchWhenNoTestIdImportDespiteOtherImports() throws Exception {
        KtFile ktFile = createFile("""
            import org.junit.jupiter.api.Test

            @TestId("1")
            fun test() {}
        """);

        assertTrue(TestIdUtils.isTestIdAnnotation(firstAnnotation(ktFile), ktFile));
    }

    @Test
    void shouldMatchFullyQualifiedAnnotation() throws Exception {
        KtFile ktFile = createFile("""
            @io.testomat.core.annotation.TestId("1")
            fun test() {}
        """);

        assertTrue(TestIdUtils.isTestIdAnnotation(firstAnnotation(ktFile), ktFile));
    }

    @Test
    void shouldNotMatchForeignTestId() throws Exception {
        KtFile ktFile = createFile("""
            import com.example.TestId

            @TestId("1")
            fun test() {}
        """);

        assertFalse(TestIdUtils.isTestIdAnnotation(firstAnnotation(ktFile), ktFile));
    }

    @Test
    void shouldNotMatchOtherAnnotation() throws Exception {
        KtFile ktFile = createFile("""
            @Test
            fun test() {}
        """);

        assertFalse(TestIdUtils.isTestIdAnnotation(firstAnnotation(ktFile), ktFile));
    }
}
