package service;

import io.testomat.model.ParsedKtFile;
import io.testomat.model.TestCase;
import io.testomat.service.KotlinFileParser;
import io.testomat.service.TestMethodExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class TestMethodExtractorTest {

    private final TestMethodExtractor extractor = new TestMethodExtractor();

    @TempDir
    Path tempDir;

    private ParsedKtFile createFile(String code) throws Exception {
        Path file = tempDir.resolve("test.kt");
        Files.writeString(file, code);
        return KotlinFileParser.parseFile(file);
    }

    @Test
    void shouldExtractSimpleJUnitTest() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
            import org.junit.jupiter.api.Test

            class MyTest {
                @Test
                fun testMethod() {}
            }
        """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(), "test.kt", "junit"
        );

        assertEquals(1, result.size());
        assertEquals("testMethod", result.get(0).getName());
        assertTrue(result.get(0).getLabels().contains("unit"));
    }

    @Test
    void shouldExtractTestId() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
            @TestId("123")
            @Test
            fun testMethod() {}
        """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(), "test.kt", "junit"
        );

        assertEquals(1, result.size());
        assertTrue(result.get(0).getName().contains("@T123"));
    }

    @Test
    void shouldDetectSkippedTest() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
            @Disabled
            @Test
            fun testMethod() {}
        """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(), "test.kt", "junit"
        );

        assertTrue(result.get(0).isSkipped());
    }

    @Test
    void shouldDetectSkippedByName() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
            @Test
            fun skipTestSomething() {}
        """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(), "test.kt", "junit"
        );

        assertTrue(result.get(0).isSkipped());
    }

    @Test
    void shouldExtractLabelsFromAnnotations() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
            @Tag("smoke")
            @Test
            fun testMethod() {}
        """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(), "test.kt", "junit"
        );

        assertTrue(result.get(0).getLabels().contains("smoke"));
    }

    @Test
    void shouldExtractLabelsFromNamePattern() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
            @Test
            fun smokeIntegrationTest() {}
        """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(), "test.kt", "junit"
        );

        assertTrue(result.get(0).getLabels().contains("smoke"));
        assertTrue(result.get(0).getLabels().contains("integration"));
    }

    @Test
    void shouldExtractSuitesFromNestedClasses() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
            class Outer {
                class Inner {
                    @Test
                    fun testMethod() {}
                }
            }
        """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(), "test.kt", "junit"
        );

        List<String> suites = result.get(0).getSuites();

        assertEquals(List.of("Outer", "Inner"), suites);
    }

    @Test
    void shouldExtractMethodCodeWithAnnotations() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
            @Test
            fun testMethod() {}
        """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(), "test.kt", "junit"
        );

        String code = result.get(0).getCode();

        assertTrue(code.contains("@Test"));
        assertTrue(code.contains("fun testMethod"));
    }

    @Test
    void shouldIgnoreNonTestMethods() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
            fun helper() {}
        """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(), "test.kt", "junit"
        );

        assertEquals(0, result.size());
    }

    @Test
    void shouldUseTitleAnnotationAsTestName() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
        @Title("Login test")
        @Test
        fun testMethod() {}
    """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(),
            "test.kt",
            "junit"
        );

        assertEquals(1, result.size());

        assertEquals("Login test", result.get(0).getTitle());

        assertTrue(
            result.get(0).getName().startsWith("Login test")
        );
    }

    @Test
    void shouldExtractMultilineTestId() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
        @TestId(
            "123"
        )
        @Test
        fun testMethod() {}
    """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(),
            "test.kt",
            "junit"
        );

        assertTrue(result.get(0).getName().contains("@T123"));
    }

    @Test
    void shouldDetectFullyQualifiedJUnitTest() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
        @org.junit.Test
        fun testMethod() {}
    """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(),
            "test.kt",
            "junit"
        );

        assertEquals(1, result.size());
    }

    @Test
    void shouldExtractTopLevelFunction() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
        @Test
        fun topLevelTest() {}
    """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(),
            "test.kt",
            "junit"
        );

        assertEquals(1, result.size());

        assertTrue(result.get(0).getSuites().isEmpty());
    }

    @Test
    void shouldExtractMultipleTests() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
        @Test
        fun first() {}

        @Test
        fun second() {}
    """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(),
            "test.kt",
            "junit"
        );

        assertEquals(2, result.size());
    }

    @Test
    void shouldNotDetectNonTestAnnotationContainingTestWord() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
        @NotTest
        fun helper() {}
    """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(),
            "test.kt",
            "junit"
        );

        assertTrue(result.isEmpty());
    }
}
