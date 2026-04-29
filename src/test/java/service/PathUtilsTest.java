package service;

import io.testomat.service.PathUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathUtilsTest {

    @Test
    void shouldReturnUnknownForNull() {
        assertEquals("UnknownFile.kt",
            PathUtils.extractRelativeFilePath(null));
    }

    @Test
    void shouldReturnUnknownForEmpty() {
        assertEquals("UnknownFile.kt",
            PathUtils.extractRelativeFilePath(""));
    }

    @Test
    void shouldExtractFromTestKotlin() {
        String path = "/project/src/test/kotlin/com/test/MyTest.kt";

        String result = PathUtils.extractRelativeFilePath(path);

        assertEquals("com/test/MyTest.kt", result);
    }

    @Test
    void shouldExtractFromMainKotlin() {
        String path = "/project/src/main/kotlin/com/app/App.kt";

        String result = PathUtils.extractRelativeFilePath(path);

        assertEquals("com/app/App.kt", result);
    }

    @Test
    void shouldExtractFromGenericKotlinFolder() {
        String path = "/project/module/src/custom/kotlin/com/x/File.kt";

        String result = PathUtils.extractRelativeFilePath(path);

        assertEquals("com/x/File.kt", result);
    }

    @Test
    void shouldReturnFullPathIfNoSrc() {
        String path = "/random/path/File.kt";

        String result = PathUtils.extractRelativeFilePath(path);

        assertEquals("random/path/File.kt", result);
    }

    @Test
    void shouldNormalizeWindowsPath() {
        String path = "C:\\project\\src\\test\\kotlin\\com\\test\\MyTest.kt";

        String result = PathUtils.extractRelativeFilePath(path);

        assertEquals("com/test/MyTest.kt", result);
    }

    @Test
    void shouldHandleWeirdPathGracefully() {
        String path = "///weird\\\\path///File.kt";

        String result = PathUtils.extractRelativeFilePath(path);

        assertTrue(result.contains("File.kt"));
    }
}
