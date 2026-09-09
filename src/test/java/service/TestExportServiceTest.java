package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.testomat.client.TestomatHttpClient;
import io.testomat.exception.CliException;
import io.testomat.model.ParsedKtFile;
import io.testomat.model.ProcessingResult;
import io.testomat.model.TestCase;
import io.testomat.progressbar.LoadingSpinner;
import io.testomat.progressbar.ProgressBar;
import io.testomat.service.JsonBuilder;
import io.testomat.service.KotlinFileParser;
import io.testomat.service.TestExportService;
import io.testomat.service.TestFrameworkDetector;
import io.testomat.service.TestMethodExtractor;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.kotlin.psi.KtFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestExportServiceTest {

    @Mock
    private TestMethodExtractor extractor;

    @Mock
    private TestFrameworkDetector detector;

    @Mock
    private JsonBuilder jsonBuilder;

    @Mock
    private TestomatHttpClient httpClient;

    @Mock
    private LoadingSpinner spinner;

    @Mock
    private ProgressBar progressBar;

    @Mock
    private ParsedKtFile parsedKtFile;

    @Mock
    private KtFile ktFile;

    private TestExportService service;

    @BeforeEach
    void setUp() {
        service = new TestExportService(
            extractor,
            detector,
            jsonBuilder,
            httpClient,
            spinner
        );
    }

    @Test
    @DisplayName("Should return zero when no test cases found")
    void shouldReturnZeroWhenNoTestCasesFound() {
        int result = service.handleProcessingResult(
            List.of(),
            "junit5",
            "key",
            "http://localhost",
            false,
            false
        );
        assertEquals(0, result);
        verify(httpClient, never())
            .sendPostRequest(any(), any());
    }

    @Test
    @DisplayName("Should skip export in dry run mode")
    void shouldSkipExportInDryRunMode() {
        List<TestCase> testCases =
            List.of(createTestCase("test1"));
        int result = service.handleProcessingResult(
            testCases,
            "junit5",
            "key",
            "http://localhost",
            true,
            false
        );
        assertEquals(1, result);
        verifyNoInteractions(httpClient);
        verifyNoInteractions(spinner);
    }

    @Test
    @DisplayName("Should export test cases")
    void shouldExportTestCases() {
        List<TestCase> testCases =
            List.of(createTestCase("test1"));
        when(jsonBuilder.buildRequestBody(
            any(),
            eq("junit5"),
            eq(false)
        )).thenReturn("{json}");
        int result = service.handleProcessingResult(
            testCases,
            "junit5",
            "key",
            "http://localhost",
            false,
            false
        );
        assertEquals(1, result);
        verify(httpClient).sendPostRequest(
            contains("/api/load?api_key=key"),
            eq("{json}")
        );
        verify(spinner).start();
        verify(spinner).stopWithMessage(
            contains("Successfully exported 1")
        );
    }

    @Test
    @DisplayName("Should filter skipped test cases")
    void shouldFilterSkippedTestCases() {
        TestCase skipped = createTestCase("skipped");
        skipped.setSkipped(true);
        TestCase active = createTestCase("active");
        when(jsonBuilder.buildRequestBody(
            any(),
            eq("junit5"),
            eq(false)
        )).thenReturn("{json}");
        int result = service.handleProcessingResult(
            List.of(skipped, active),
            "junit5",
            "key",
            "http://localhost",
            false,
            false
        );
        assertEquals(1, result);
        verify(httpClient, times(1))
            .sendPostRequest(any(), any());
    }

    @Test
    @DisplayName("Should split requests into batches")
    void shouldSplitRequestsIntoBatches() {
        List<TestCase> testCases = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            testCases.add(createTestCase("test-" + i));
        }
        when(jsonBuilder.buildRequestBody(
            any(),
            eq("junit5"),
            eq(false)
        )).thenReturn("{json}");
        int result = service.handleProcessingResult(
            testCases,
            "junit5",
            "key",
            "http://localhost",
            false,
            false
        );
        assertEquals(250, result);
        verify(httpClient, times(3))
            .sendPostRequest(any(), any());
    }

    @Test
    @DisplayName("Should throw exception when server url missing")
    void shouldThrowExceptionWhenServerUrlMissing() {
        List<TestCase> testCases =
            List.of(createTestCase("test1"));
        assertThrows(
            IllegalArgumentException.class,
            () -> service.handleProcessingResult(
                testCases,
                "junit5",
                "key",
                null,
                false,
                false
            )
        );
    }

    @Test
    @DisplayName("Should wrap export exceptions")
    void shouldWrapExportExceptions() {
        List<TestCase> testCases =
            List.of(createTestCase("test1"));
        when(jsonBuilder.buildRequestBody(
            any(),
            eq("junit5"),
            eq(false)
        )).thenReturn("{json}");
        org.mockito.Mockito.doThrow(
                new RuntimeException("network")
            ).when(httpClient)
            .sendPostRequest(any(), any());
        assertThrows(
            CliException.class,
            () -> service.handleProcessingResult(
                testCases,
                "junit5",
                "key",
                "http://localhost",
                false,
                false
            )
        );
    }

    @Test
    @DisplayName("Should process files and update progress")
    void shouldProcessFilesAndUpdateProgress() {
        File file = new File("test.kt");
        try (MockedStatic<KotlinFileParser> mockedParser =
            org.mockito.Mockito.mockStatic(KotlinFileParser.class)) {
            mockedParser.when(() ->
                    KotlinFileParser.parseFile(any(Path.class)))
                .thenReturn(parsedKtFile);
            when(parsedKtFile.getKtFile())
                .thenReturn(ktFile);
            when(detector.detectFramework(ktFile))
                .thenReturn("junit5");
            when(extractor.extractTestCases(
                any(),
                any(),
                any()
            )).thenReturn(
                List.of(createTestCase("test"))
            );
            ProcessingResult result =
                service.processAllFiles(
                    List.of(file),
                    false,
                    progressBar
                );
            assertEquals(1, result.allTestCases().size());
            verify(progressBar).update(1);
            verify(progressBar).finish();
        }
    }

    @Test
    @DisplayName("Should throw exception in verbose mode")
    void shouldThrowExceptionInVerboseMode() {
        File file = new File("broken.kt");
        try (MockedStatic<KotlinFileParser> mockedParser =
            org.mockito.Mockito.mockStatic(KotlinFileParser.class)) {
            mockedParser.when(() ->
                    KotlinFileParser.parseFile(any(Path.class)))
                .thenThrow(new RuntimeException("parse error"));
            assertThrows(
                CliException.class,
                () -> service.processAllFiles(
                    List.of(file),
                    true,
                    null
                )
            );
        }
    }

    @Test
    @DisplayName("Should ignore exceptions in non verbose mode")
    void shouldIgnoreExceptionsInNonVerboseMode() {
        File file = new File("broken.kt");
        try (MockedStatic<KotlinFileParser> mockedParser =
            org.mockito.Mockito.mockStatic(KotlinFileParser.class)) {
            mockedParser.when(() ->
                    KotlinFileParser.parseFile(any(Path.class)))
                .thenThrow(new RuntimeException("parse error"));
            ProcessingResult result =
                service.processAllFiles(
                    List.of(file),
                    false,
                    null
                );
            assertEquals(0, result.allTestCases().size());
        }
    }

    @Test
    @DisplayName("Should exclude skipped tests from processing result")
    void shouldExcludeSkippedFromProcessingResult() {
        File file = new File("test.kt");
        try (MockedStatic<KotlinFileParser> mockedParser =
            org.mockito.Mockito.mockStatic(KotlinFileParser.class)) {
            mockedParser.when(() ->
                    KotlinFileParser.parseFile(any(Path.class)))
                .thenReturn(parsedKtFile);
            when(parsedKtFile.getKtFile())
                .thenReturn(ktFile);
            when(detector.detectFramework(ktFile))
                .thenReturn("junit5");
            TestCase skipped = createTestCase("skipped");
            skipped.setSkipped(true);
            TestCase active = createTestCase("active");
            when(extractor.extractTestCases(
                any(),
                any(),
                any()
            )).thenReturn(List.of(skipped, active));
            ProcessingResult result =
                service.processAllFiles(
                    List.of(file),
                    false,
                    null
                );
            assertEquals(1, result.allTestCases().size());
            assertEquals("active", result.allTestCases().get(0).getName());
        }
    }

    private TestCase createTestCase(String name) {
        TestCase testCase = new TestCase();
        testCase.setName(name);
        testCase.setFile("Test.kt");
        testCase.setLabels(List.of("smoke"));
        return testCase;
    }
}