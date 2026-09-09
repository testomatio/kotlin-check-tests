package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.testomat.model.AnnotationBlock;
import io.testomat.service.AnnotationUtils;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnnotationUtilsTest {

    @Test
    @DisplayName("Should extract title from annotation")
    void shouldExtractTitle() {
        String header = """
                @Title("Login test")
                @Test
                """;
        String title = AnnotationUtils.extractTitle(header);
        assertEquals("Login test", title);
    }

    @Test
    @DisplayName("Should return null when title annotation absent")
    void shouldReturnNullWhenTitleAbsent() {
        String header = """
                @Test
                """;
        String title = AnnotationUtils.extractTitle(header);
        assertNull(title);
    }

    @Test
    @DisplayName("Should handle nested parentheses in title")
    void shouldHandleNestedParentheses() {
        String header = """
                @Title("Login test (mobile)")
                """;
        String title = AnnotationUtils.extractTitle(header);
        assertEquals("Login test (mobile)", title);
    }

    @Test
    @DisplayName("Should return null for malformed title annotation")
    void shouldReturnNullForMalformedTitle() {
        String header = """
                @Title(
                """;
        String title = AnnotationUtils.extractTitle(header);
        assertNull(title);
    }

    @Test
    @DisplayName("Should collect single annotation block")
    void shouldCollectSingleAnnotationBlock() {
        String[] lines = {
            "@Test",
            "fun test() {}"
        };
        List<AnnotationBlock> blocks =
            AnnotationUtils.collectAnnotationBlocks(lines);
        assertEquals(1, blocks.size());
        AnnotationBlock block = blocks.get(0);
        assertEquals("@Test\n", block.getText());
        assertEquals(0, block.getStartLine());
        assertEquals(0, block.getEndLine());
        assertTrue(block.isTest());
    }

    @Test
    @DisplayName("Should collect multiple annotation blocks")
    void shouldCollectMultipleAnnotationBlocks() {
        String[] lines = {
            "@Test",
            "fun test1() {}",
            "",
            "@ParameterizedTest",
            "fun test2() {}"
        };
        List<AnnotationBlock> blocks =
            AnnotationUtils.collectAnnotationBlocks(lines);
        assertEquals(2, blocks.size());
        assertTrue(blocks.get(0).isTest());
        assertTrue(blocks.get(1).isTest());
    }

    @Test
    @DisplayName("Should collect multiline annotation block")
    void shouldCollectMultilineAnnotationBlock() {
        String[] lines = {
            "@Title(",
            "    \"Login test\"",
            ")",
            "@Test",
            "fun test() {}"
        };
        List<AnnotationBlock> blocks =
            AnnotationUtils.collectAnnotationBlocks(lines);
        assertEquals(1, blocks.size());
        AnnotationBlock block = blocks.get(0);
        assertTrue(block.getText().contains("@Title("));
        assertTrue(block.getText().contains("@Test"));
    }

    @Test
    @DisplayName("Should ignore non test annotations")
    void shouldIgnoreNonTestAnnotations() {
        String[] lines = {
            "@DisplayName(\"Test\")",
            "fun test() {}"
        };
        List<AnnotationBlock> blocks =
            AnnotationUtils.collectAnnotationBlocks(lines);
        assertEquals(1, blocks.size());
        assertFalse(blocks.get(0).isTest());
    }

    @Test
    @DisplayName("Should detect repeated test annotation")
    void shouldDetectRepeatedTestAnnotation() {
        String[] lines = {
            "@RepeatedTest(3)",
            "fun repeated() {}"
        };
        List<AnnotationBlock> blocks =
            AnnotationUtils.collectAnnotationBlocks(lines);
        assertEquals(1, blocks.size());
        assertTrue(blocks.get(0).isTest());
    }

    @Test
    @DisplayName("Should detect parameterized test annotation")
    void shouldDetectParameterizedTestAnnotation() {
        String[] lines = {
            "@ParameterizedTest",
            "fun parameterized() {}"
        };
        List<AnnotationBlock> blocks =
            AnnotationUtils.collectAnnotationBlocks(lines);
        assertEquals(1, blocks.size());
        assertTrue(blocks.get(0).isTest());
    }

    @Test
    @DisplayName("Should strip inline comments")
    void shouldStripInlineComments() {
        String line = "@Test // comment";
        String result =
            AnnotationUtils.stripStringsAndComments(line);
        assertEquals("@Test ", result);
    }

    @Test
    @DisplayName("Should leave line unchanged when no comments")
    void shouldLeaveLineUnchanged() {
        String line = "@Test";
        String result =
            AnnotationUtils.stripStringsAndComments(line);
        assertEquals("@Test", result);
    }

    @Test
    @DisplayName("Should handle URL inside string")
    void shouldHandleUrlInsideString() {
        String line = "@Title(\"https://test.com\")";
        String result =
            AnnotationUtils.stripStringsAndComments(line);
        assertEquals("@Title(\"https:", result);
    }

    @Test
    @DisplayName("Should create empty blocks list")
    void shouldCreateEmptyBlocksList() {
        String[] lines = {
            "fun test() {}"
        };
        List<AnnotationBlock> blocks =
            AnnotationUtils.collectAnnotationBlocks(lines);
        assertTrue(blocks.isEmpty());
    }

    @Test
    @DisplayName("Should collect annotation block with comments")
    void shouldCollectAnnotationBlockWithComments() {
        String[] lines = {
            "@Test // inline comment",
            "fun test() {}"
        };
        List<AnnotationBlock> blocks =
            AnnotationUtils.collectAnnotationBlocks(lines);
        assertEquals(1, blocks.size());
        assertTrue(blocks.get(0).isTest());
    }

    @Test
    @DisplayName("Should not include class declaration in annotation block")
    void shouldNotIncludeClassDeclarationInBlock() {
        String[] lines = {
            "@Tag(\"api\")",
            "class Tagged {",
            "    @Test",
            "    fun test() {}"
        };
        List<AnnotationBlock> blocks =
            AnnotationUtils.collectAnnotationBlocks(lines);
        assertEquals(2, blocks.size());
        assertFalse(blocks.get(0).getText().contains("class Tagged"));
        assertFalse(blocks.get(1).getText().contains("class Tagged"));
    }

    @Test
    @DisplayName("Should not include previous function body in annotation block")
    void shouldNotIncludePreviousFunctionBody() {
        String[] lines = {
            "@Test",
            "fun first() {",
            "    @Suppress(\"x\")",
            "    val a = 1",
            "    assertEquals(1, a)",
            "}",
            "",
            "@Test",
            "fun second() {}"
        };
        List<AnnotationBlock> blocks =
            AnnotationUtils.collectAnnotationBlocks(lines);
        assertEquals(3, blocks.size());
        assertEquals(0, blocks.get(0).getStartLine());
        assertEquals(2, blocks.get(1).getStartLine());
        assertEquals(7, blocks.get(2).getStartLine());
        assertFalse(blocks.get(2).getText().contains("val a"));
        assertFalse(blocks.get(2).getText().contains("assertEquals"));
    }

    @Test
    @DisplayName("Should keep text block annotation as single block")
    void shouldKeepTextBlockAnnotation() {
        String[] lines = {
            "@ParameterizedTest",
            "@CsvSource(textBlock = \"\"\"",
            "    2, 2, 4",
            "    3, 3, 6",
            "\"\"\")",
            "fun test() {}"
        };
        List<AnnotationBlock> blocks =
            AnnotationUtils.collectAnnotationBlocks(lines);
        assertEquals(1, blocks.size());
        assertTrue(blocks.get(0).getText().contains("@CsvSource"));
        assertTrue(blocks.get(0).getText().contains("2, 2, 4"));
    }
}
