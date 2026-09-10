package service;

import io.testomat.client.TestomatHttpClient;
import io.testomat.model.ParsedKtFile;
import io.testomat.service.KotlinFileParser;
import io.testomat.service.ResponseParser;
import io.testomat.service.TestIdAnnotationManager;
import io.testomat.service.TestIdSyncService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestIdSyncServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSkipSuiteEntries() throws Exception {
        TestomatHttpClient httpClient = mock(TestomatHttpClient.class);
        ResponseParser parser = mock(ResponseParser.class);
        TestIdAnnotationManager manager = mock(TestIdAnnotationManager.class);

        TestIdSyncService service =
            new TestIdSyncService(httpClient, parser, manager);

        when(httpClient.sendGetRequest(any(), any()))
            .thenReturn("response");

        Map<String, String> apiMap = new HashMap<>();
        apiMap.put("test.kt#MyClass", "@Ssuite-id");
        apiMap.put("test.kt#MyClass#testMethod", "@Tabc123");

        when(parser.parseTestsFromResponse("response"))
            .thenReturn(apiMap);

        ParsedKtFile parsedKtFile = createParsedFile("""
            import org.junit.jupiter.api.Test

            class MyClass {
                fun testMethod() {}
            }
        """);

        KtFile file = parsedKtFile.getKtFile();

        KtNamedFunction method =
            PsiTreeUtil.findChildrenOfType(file, KtNamedFunction.class)
                .iterator().next();

        when(manager.findMethodInParsedKtFiles(any(), any(), anyBoolean()))
            .thenReturn(Optional.of(method));

        TestIdSyncService.SyncResult result =
            service.syncResult("key", "url", List.of(parsedKtFile), false, null);

        assertEquals(1, result.getProcessedCount());

        String updated = Files.readString(parsedKtFile.getPath());

        assertTrue(updated.contains("@TestId(\"abc123\")"));
        assertFalse(updated.contains("suite-id"));
    }

    private ParsedKtFile createParsedFile(String code) throws Exception {
        Path file = tempDir.resolve("test.kt");
        Files.writeString(file, code);
        return KotlinFileParser.parseFile(file);
    }

    @Test
    void shouldProcessSingleTestAndModifyFile() throws Exception {

        TestomatHttpClient httpClient = mock(TestomatHttpClient.class);
        ResponseParser parser = mock(ResponseParser.class);
        TestIdAnnotationManager manager = mock(TestIdAnnotationManager.class);

        TestIdSyncService service =
            new TestIdSyncService(httpClient, parser, manager);

        when(httpClient.sendGetRequest(any(), any()))
            .thenReturn("response");

        Map<String, String> apiMap = Map.of(
            "test.kt#MyClass#testMethod", "123"
        );

        when(parser.parseTestsFromResponse("response"))
            .thenReturn(apiMap);

        ParsedKtFile parsedKtFile = createParsedFile("""
                class MyClass {
                    fun testMethod() {}
                }
            """);

        KtFile file = parsedKtFile.getKtFile();

        KtNamedFunction method =
            PsiTreeUtil.findChildrenOfType(file, KtNamedFunction.class)
                .iterator().next();

        when(manager.findMethodInParsedKtFiles(any(), any(), anyBoolean()))
            .thenReturn(Optional.of(method));

        TestIdSyncService.SyncResult result =
            service.syncResult("key", "url", List.of(parsedKtFile), false, null);

        assertEquals(1, result.getProcessedCount());
        assertEquals(1, result.getModifiedFilesCount());
    }

    @Test
    void shouldSkipWhenMethodNotFound() {
        TestomatHttpClient httpClient = mock(TestomatHttpClient.class);
        ResponseParser parser = mock(ResponseParser.class);
        TestIdAnnotationManager manager = mock(TestIdAnnotationManager.class);

        TestIdSyncService service =
            new TestIdSyncService(httpClient, parser, manager);

        when(httpClient.sendGetRequest(any(), any()))
            .thenReturn("response");

        when(parser.parseTestsFromResponse(any()))
            .thenReturn(Map.of("file.kt#MyClass#test", "1"));

        when(manager.findMethodInParsedKtFiles(any(), any(), anyBoolean()))
            .thenReturn(Optional.empty());

        TestIdSyncService.SyncResult result =
            service.syncResult("key", "url", List.of(), false, null);

        assertEquals(0, result.getProcessedCount());
        assertEquals(0, result.getModifiedFilesCount());
    }

    @Test
    void shouldSkipInvalidKey() {
        TestomatHttpClient httpClient = mock(TestomatHttpClient.class);
        ResponseParser parser = mock(ResponseParser.class);
        TestIdAnnotationManager manager = mock(TestIdAnnotationManager.class);

        TestIdSyncService service =
            new TestIdSyncService(httpClient, parser, manager);

        when(httpClient.sendGetRequest(any(), any()))
            .thenReturn("response");

        when(parser.parseTestsFromResponse(any()))
            .thenReturn(Map.of("invalid-key", "1"));

        TestIdSyncService.SyncResult result =
            service.syncResult("key", "url", List.of(), false, null);

        assertEquals(0, result.getProcessedCount());
    }

    @Test
    void shouldProcessServerKeyVariantsOnce() throws Exception {
        TestomatHttpClient httpClient = mock(TestomatHttpClient.class);
        ResponseParser parser = mock(ResponseParser.class);
        TestIdAnnotationManager manager = mock(TestIdAnnotationManager.class);

        TestIdSyncService service =
            new TestIdSyncService(httpClient, parser, manager);

        when(httpClient.sendGetRequest(any(), any()))
            .thenReturn("response");

        Map<String, String> apiMap = new HashMap<>();
        apiMap.put("test.kt#MyClass#testMethod", "same-id");
        apiMap.put("MyClass#testMethod", "same-id");
        apiMap.put("testMethod", "same-id");

        when(parser.parseTestsFromResponse("response"))
            .thenReturn(apiMap);

        ParsedKtFile parsedKtFile = createParsedFile("""
            import org.junit.jupiter.api.Test

            class MyClass {
                fun testMethod() {}
            }
        """);

        KtFile file = parsedKtFile.getKtFile();

        KtNamedFunction method =
            PsiTreeUtil.findChildrenOfType(file, KtNamedFunction.class)
                .iterator().next();

        when(manager.findMethodInParsedKtFiles(any(), any(), anyBoolean()))
            .thenReturn(Optional.of(method));

        TestIdSyncService.SyncResult result =
            service.syncResult("key", "url", List.of(parsedKtFile), false, null);

        assertEquals(1, result.getProcessedCount());
        assertEquals(1, result.getModifiedFilesCount());

        String updated = Files.readString(parsedKtFile.getPath());

        int count = updated.split("@TestId", -1).length - 1;
        assertEquals(1, count);
    }

    @Test
    void shouldHandleMultipleEntries() throws Exception {

        TestomatHttpClient httpClient = mock(TestomatHttpClient.class);
        ResponseParser parser = mock(ResponseParser.class);
        TestIdAnnotationManager manager = mock(TestIdAnnotationManager.class);

        TestIdSyncService service =
            new TestIdSyncService(httpClient, parser, manager);

        when(httpClient.sendGetRequest(any(), any()))
            .thenReturn("response");

        Map<String, String> apiMap = new HashMap<>();
        apiMap.put("file.kt#C1#m1", "1");
        apiMap.put("file.kt#C2#m2", "2");

        when(parser.parseTestsFromResponse(any()))
            .thenReturn(apiMap);

        ParsedKtFile parsedKtFile = createParsedFile("""
                class C1 { fun m1() {} }
                class C2 { fun m2() {} }
            """);

        KtFile file = parsedKtFile.getKtFile();

        Collection<KtNamedFunction> methods =
            PsiTreeUtil.findChildrenOfType(file, KtNamedFunction.class);

        Iterator<KtNamedFunction> it = methods.iterator();
        KtNamedFunction m1 = it.next();
        KtNamedFunction m2 = it.next();

        when(manager.findMethodInParsedKtFiles(any(), any(), anyBoolean()))
            .thenReturn(Optional.of(m1))
            .thenReturn(Optional.of(m2));

        TestIdSyncService.SyncResult result =
            service.syncResult("key", "url", List.of(parsedKtFile), false, null);

        assertEquals(2, result.getProcessedCount());
        assertEquals(1, result.getModifiedFilesCount());
    }

    @Test
    void shouldNotAddImportIfAlreadyExists() throws Exception {
        TestomatHttpClient httpClient = mock(TestomatHttpClient.class);
        ResponseParser parser = mock(ResponseParser.class);
        TestIdAnnotationManager manager = mock(TestIdAnnotationManager.class);

        TestIdSyncService service =
            new TestIdSyncService(httpClient, parser, manager);

        when(httpClient.sendGetRequest(any(), any()))
            .thenReturn("response");

        when(parser.parseTestsFromResponse(any()))
            .thenReturn(Map.of("test.kt#MyClass#test", "1"));

        ParsedKtFile parsedKtFile = createParsedFile("""
        import io.testomat.core.annotation.TestId

        class MyClass {
            fun test() {}
        }
    """);

        KtFile file = parsedKtFile.getKtFile();

        KtNamedFunction method =
            PsiTreeUtil.findChildrenOfType(file, KtNamedFunction.class)
                .iterator().next();

        when(manager.findMethodInParsedKtFiles(any(), any(), anyBoolean()))
            .thenReturn(Optional.of(method));

        TestIdSyncService.SyncResult result =
            service.syncResult("key", "url", List.of(parsedKtFile), false, null);

        assertEquals(1, result.getProcessedCount());
        assertEquals(1, result.getModifiedFilesCount());

        String updated = Files.readString(parsedKtFile.getPath());

        int count = updated.split("import io.testomat.core.annotation.TestId", -1).length - 1;
        assertEquals(1, count);
    }

    @Test
    void shouldInsertImportAfterPackageDirective() throws Exception {
        TestomatHttpClient httpClient = mock(TestomatHttpClient.class);
        ResponseParser parser = mock(ResponseParser.class);
        TestIdAnnotationManager manager = mock(TestIdAnnotationManager.class);

        TestIdSyncService service =
            new TestIdSyncService(httpClient, parser, manager);

        when(httpClient.sendGetRequest(any(), any()))
            .thenReturn("response");

        when(parser.parseTestsFromResponse("response"))
            .thenReturn(Map.of("test.kt#MyClass#testMethod", "123"));

        ParsedKtFile parsedKtFile = createParsedFile("""
            package com.example

            class MyClass {
                fun testMethod() {}
            }
        """);

        KtFile file = parsedKtFile.getKtFile();

        KtNamedFunction method =
            PsiTreeUtil.findChildrenOfType(file, KtNamedFunction.class)
                .iterator().next();

        when(manager.findMethodInParsedKtFiles(any(), any(), anyBoolean()))
            .thenReturn(Optional.of(method));

        service.syncResult("key", "url", List.of(parsedKtFile), false, null);

        String updated = Files.readString(parsedKtFile.getPath());

        int packageIndex = updated.indexOf("package com.example");
        int importIndex = updated.indexOf("import io.testomat.core.annotation.TestId");

        assertTrue(packageIndex >= 0);
        assertTrue(importIndex > packageIndex);
    }

    @Test
    void shouldReplaceMultilineTestIdAnnotation() throws Exception {
        TestomatHttpClient httpClient = mock(TestomatHttpClient.class);
        ResponseParser parser = mock(ResponseParser.class);
        TestIdAnnotationManager manager = mock(TestIdAnnotationManager.class);

        TestIdSyncService service =
            new TestIdSyncService(httpClient, parser, manager);

        when(httpClient.sendGetRequest(any(), any()))
            .thenReturn("response");

        when(parser.parseTestsFromResponse("response"))
            .thenReturn(Map.of("test.kt#MyClass#testMethod", "new-id"));

        ParsedKtFile parsedKtFile = createParsedFile("""
            class MyClass {
                @TestId(
                    "old-id"
                )
                fun testMethod() {}
            }
        """);

        KtFile file = parsedKtFile.getKtFile();

        KtNamedFunction method =
            PsiTreeUtil.findChildrenOfType(file, KtNamedFunction.class)
                .iterator().next();

        when(manager.findMethodInParsedKtFiles(any(), any(), anyBoolean()))
            .thenReturn(Optional.of(method));

        service.syncResult("key", "url", List.of(parsedKtFile), false, null);

        String updated = Files.readString(parsedKtFile.getPath());

        assertFalse(updated.contains("old-id"));
        assertTrue(updated.contains("@TestId(\"new-id\")"));

        int count = updated.split("@TestId", -1).length - 1;
        assertEquals(1, count);
    }

    @Test
    void shouldReplaceExistingTestIdWhenImportMissing() throws Exception {
        TestomatHttpClient httpClient = mock(TestomatHttpClient.class);
        ResponseParser parser = mock(ResponseParser.class);
        TestIdAnnotationManager manager = mock(TestIdAnnotationManager.class);

        TestIdSyncService service =
            new TestIdSyncService(httpClient, parser, manager);

        when(httpClient.sendGetRequest(any(), any()))
            .thenReturn("response");

        when(parser.parseTestsFromResponse("response"))
            .thenReturn(Map.of("test.kt#MyClass#testMethod", "new-id"));

        ParsedKtFile parsedKtFile = createParsedFile("""
            import org.junit.jupiter.api.Test

            class MyClass {
                @TestId("old-id")
                fun testMethod() {}
            }
        """);

        KtFile file = parsedKtFile.getKtFile();

        KtNamedFunction method =
            PsiTreeUtil.findChildrenOfType(file, KtNamedFunction.class)
                .iterator().next();

        when(manager.findMethodInParsedKtFiles(any(), any(), anyBoolean()))
            .thenReturn(Optional.of(method));

        service.syncResult("key", "url", List.of(parsedKtFile), false, null);

        String updated = Files.readString(parsedKtFile.getPath());

        assertFalse(updated.contains("old-id"));
        assertTrue(updated.contains("@TestId(\"new-id\")"));
        assertTrue(updated.contains("import io.testomat.core.annotation.TestId"));

        int count = updated.split("@TestId", -1).length - 1;
        assertEquals(1, count);
    }

    @Test
    void shouldReplaceTestIdInCrLfFile() throws Exception {
        TestomatHttpClient httpClient = mock(TestomatHttpClient.class);
        ResponseParser parser = mock(ResponseParser.class);
        TestIdAnnotationManager manager = mock(TestIdAnnotationManager.class);

        TestIdSyncService service =
            new TestIdSyncService(httpClient, parser, manager);

        when(httpClient.sendGetRequest(any(), any()))
            .thenReturn("response");

        when(parser.parseTestsFromResponse("response"))
            .thenReturn(Map.of("test.kt#MyClass#testMethod", "new-id"));

        Path filePath = tempDir.resolve("test.kt");
        String content = "import org.junit.jupiter.api.Test\r\n"
                + "\r\n"
                + "class MyClass {\r\n"
                + "    @TestId(\"old-id\")\r\n"
                + "    fun testMethod() {}\r\n"
                + "}\r\n";
        Files.writeString(filePath, content);

        ParsedKtFile parsedKtFile = KotlinFileParser.parseFile(filePath);
        KtFile file = parsedKtFile.getKtFile();

        KtNamedFunction method =
            PsiTreeUtil.findChildrenOfType(file, KtNamedFunction.class)
                .iterator().next();

        when(manager.findMethodInParsedKtFiles(any(), any(), anyBoolean()))
            .thenReturn(Optional.of(method));

        service.syncResult("key", "url", List.of(parsedKtFile), false, null);

        String updated = Files.readString(filePath);

        int count = updated.split("@TestId", -1).length - 1;
        assertEquals(1, count);
        assertTrue(updated.contains("@TestId(\"new-id\")"));
    }

    @Test
    void shouldNotAccumulateDuplicatesAcrossRuns() throws Exception {
        TestomatHttpClient httpClient = mock(TestomatHttpClient.class);
        ResponseParser parser = mock(ResponseParser.class);
        TestIdAnnotationManager manager = mock(TestIdAnnotationManager.class);

        TestIdSyncService service =
            new TestIdSyncService(httpClient, parser, manager);

        when(httpClient.sendGetRequest(any(), any()))
            .thenReturn("response");

        when(parser.parseTestsFromResponse("response"))
            .thenReturn(Map.of("test.kt#MyClass#testMethod", "some-id"));

        Path filePath = tempDir.resolve("test.kt");
        Files.writeString(filePath, """
            import org.junit.jupiter.api.Test

            class MyClass {
                fun testMethod() {}
            }
        """);

        for (int run = 0; run < 3; run++) {
            ParsedKtFile parsedKtFile = KotlinFileParser.parseFile(filePath);
            KtFile file = parsedKtFile.getKtFile();

            KtNamedFunction method =
                PsiTreeUtil.findChildrenOfType(file, KtNamedFunction.class)
                    .iterator().next();

            when(manager.findMethodInParsedKtFiles(any(), any(), anyBoolean()))
                .thenReturn(Optional.of(method));

            service.syncResult("key", "url", List.of(parsedKtFile), false, null);
        }

        String updated = Files.readString(filePath);

        int count = updated.split("@TestId", -1).length - 1;
        assertEquals(1, count);
    }

    @Test
    void shouldCollapseExistingDuplicatesToSingle() throws Exception {
        TestomatHttpClient httpClient = mock(TestomatHttpClient.class);
        ResponseParser parser = mock(ResponseParser.class);
        TestIdAnnotationManager manager = mock(TestIdAnnotationManager.class);

        TestIdSyncService service =
            new TestIdSyncService(httpClient, parser, manager);

        when(httpClient.sendGetRequest(any(), any()))
            .thenReturn("response");

        when(parser.parseTestsFromResponse("response"))
            .thenReturn(Map.of("test.kt#MyClass#testMethod", "fresh-id"));

        ParsedKtFile parsedKtFile = createParsedFile("""
            import org.junit.jupiter.api.Test
            import io.testomat.core.annotation.TestId

            class MyClass {
                @TestId("dup")
                @TestId("dup")
                @TestId("dup")
                @TestId("dup")
                fun testMethod() {}
            }
        """);

        KtFile file = parsedKtFile.getKtFile();

        KtNamedFunction method =
            PsiTreeUtil.findChildrenOfType(file, KtNamedFunction.class)
                .iterator().next();

        when(manager.findMethodInParsedKtFiles(any(), any(), anyBoolean()))
            .thenReturn(Optional.of(method));

        service.syncResult("key", "url", List.of(parsedKtFile), false, null);

        String updated = Files.readString(parsedKtFile.getPath());

        int count = updated.split("@TestId", -1).length - 1;
        assertEquals(1, count);
        assertTrue(updated.contains("@TestId(\"fresh-id\")"));
    }
}
