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
    void shouldNotTreatDisabledOnOsAsSkipped() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
            @DisabledOnOs(OS.LINUX)
            @Test
            fun conditionalTest() {}
        """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(), "test.kt", "junit"
        );

        assertFalse(result.get(0).isSkipped());
    }

    @Test
    void shouldNotTreatDisabledOnJreAsSkipped() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
            @DisabledOnJre(JRE.JAVA_8)
            @Test
            fun conditionalJreTest() {}
        """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(), "test.kt", "junit"
        );

        assertFalse(result.get(0).isSkipped());
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
    void shouldUseDirectoryAsSuiteForTopLevelTest() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
        @Test
        fun topLevelTest() {}
    """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(),
            "src/test/kotlin/junit5/basic/Simple.kt",
            "junit"
        );

        assertEquals(List.of("junit5", "basic"), result.get(0).getSuites());
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

    @Test
    void shouldNotDuplicateAnnotationsInCode() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
        @Test
        @TestId("123")
        @DisplayName("x")
        fun testMethod() {}
    """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(),
            "test.kt",
            "junit"
        );

        String code = result.get(0).getCode();

        assertEquals(1, countOccurrences(code, "@Test\n"));
        assertEquals(1, countOccurrences(code, "@TestId"));
        assertEquals(1, countOccurrences(code, "@DisplayName"));
    }

    @Test
    void shouldNotDuplicateAnnotationsInClassMethod() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
        class MyTest {
            @Test
            fun testMethod() {}
        }
    """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(),
            "test.kt",
            "junit"
        );

        String code = result.get(0).getCode();

        assertEquals(1, countOccurrences(code, "@Test\n"));
        assertEquals(1, countOccurrences(code, "fun testMethod"));
    }

    @Test
    void shouldNotIncludeClassContextInCode() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
        @Tag("api")
        class Tagged {
            @Test
            @Tag("smoke")
            fun taggedSmokeTest() {
                assertTrue(true)
            }
        }
    """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(),
            "test.kt",
            "junit"
        );

        String code = result.get(0).getCode();

        assertFalse(code.contains("class Tagged"));
        assertFalse(code.contains("@Tag(\"api\")"));
        assertEquals(1, countOccurrences(code, "@Test"));
        assertEquals(1, countOccurrences(code, "@Tag(\"smoke\")"));
    }

    @Test
    void shouldIncludeAnnotationsForTextBlockParameterizedTest() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
        @ParameterizedTest
        @CsvSource(textBlock = \"""
            2, 2, 4
        \""")
        fun csvTextBlockTest(a: Int, b: Int, expected: Int) {
            assertEquals(expected, a + b)
        }
    """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(),
            "test.kt",
            "junit"
        );

        String code = result.get(0).getCode();

        assertTrue(code.contains("@ParameterizedTest"));
        assertTrue(code.contains("@CsvSource"));
        assertTrue(code.contains("fun csvTextBlockTest"));
    }

    @Test
    void shouldKeepRelativeIndentationOfClassMethod() throws Exception {
        ParsedKtFile parsedKtFile = createFile("""
        class Conditional {
            @Test
            @EnabledOnJre(JRE.JAVA_17)
            fun conditionalTest() {
                assertTrue(true)
            }
        }
    """);

        List<TestCase> result = extractor.extractTestCases(
            parsedKtFile.getKtFile(),
            "test.kt",
            "junit"
        );

        String code = result.get(0).getCode();

        assertTrue(code.contains("@EnabledOnJre(JRE.JAVA_17)"));
        assertTrue(code.contains("fun conditionalTest() {\n    assertTrue(true)\n}"));
        assertFalse(code.contains("\n        assertTrue"));
        assertFalse(code.contains("\n    }\n"));
    }

    private int countOccurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) != -1) {
            count++;
            index += token.length();
        }
        return count;
    }
}