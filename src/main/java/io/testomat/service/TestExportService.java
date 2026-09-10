package io.testomat.service;

import io.testomat.client.CliClient;
import io.testomat.client.TestomatHttpClient;
import io.testomat.exception.CliException;
import io.testomat.model.ProcessingResult;
import io.testomat.model.TestCase;
import io.testomat.progressbar.LoadingSpinner;
import io.testomat.progressbar.ProgressBar;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jetbrains.kotlin.psi.KtFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestExportService {
    private static final Logger log = LoggerFactory.getLogger(TestExportService.class);

    private final TestMethodExtractor extractor;
    private final TestFrameworkDetector detector;
    private final JsonBuilder jsonBuilder;
    private final TestomatHttpClient httpClient;
    private final LoadingSpinner spinner;
    private final int batchSize = 100;

    public TestExportService() {
        this.extractor = new TestMethodExtractor();
        this.detector = new TestFrameworkDetector();
        this.jsonBuilder = new JsonBuilder();
        this.httpClient = new CliClient();
        this.spinner = new LoadingSpinner("Sending test data to server...");
    }

    public TestExportService(TestMethodExtractor extractor,
            TestFrameworkDetector detector, JsonBuilder jsonBuilder,
            TestomatHttpClient httpClient, LoadingSpinner spinner) {
        this.extractor = extractor;
        this.detector = detector;
        this.jsonBuilder = jsonBuilder;
        this.httpClient = httpClient;
        this.spinner = spinner;
    }

    public int processTestFilesWithProgress(List<File> testFiles, String apiKey,
            String serverUrl, boolean dryRun,
            boolean verbose, ProgressBar progressBar,
            boolean structure) {
        ProcessingResult result = processAllFiles(testFiles, verbose, progressBar);

        return handleProcessingResult(result.allTestCases(), result.primaryFramework(),
            apiKey, serverUrl, dryRun, structure);
    }

    private int exportAllTestCases(List<TestCase> allTestCases, String framework,
            String apiKey, String serverUrl, boolean structure) {
        validateExportConfig(serverUrl);

        List<TestCase> filteredTestCases = allTestCases.stream()
                .filter(testCase -> !testCase.isSkipped())
                .toList();

        Stream<String> batchJsonBodies =
                IntStream.iterate(0, i -> i < filteredTestCases.size(), i -> i + batchSize)
                .mapToObj(i ->
                    jsonBuilder.buildRequestBody(
                        filteredTestCases.subList(i,
                            Math.min(i + batchSize, filteredTestCases.size())),
                        framework,
                        structure
                    )
                );

        String requestUrl = serverUrl + "/api/load?api_key=" + apiKey;

        spinner.start();

        try {
            batchJsonBodies.forEach(jsonBody ->
                    httpClient.sendPostRequest(requestUrl, jsonBody));
        } catch (CliException e) {
            spinner.stop();
            throw e;
        } catch (Exception e) {
            spinner.stop();
            throw new CliException("Error while executing request", e);
        }

        spinner.stopWithMessage("Successfully exported " + filteredTestCases.size()
                + " test methods");

        return filteredTestCases.size();
    }

    private void printAllTestCases(List<TestCase> testCases) {
        log.info("All test methods found:");
        for (TestCase testCase : testCases) {
            log.info("  - {} [{}] ({})", testCase.getName(),
                    String.join(", ", testCase.getLabels()), testCase.getFile());
        }
    }

    private void validateExportConfig(String serverUrl) {
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("TESTOMATIO_URL is required for actual execution");
        }

        CliClient.validateServerUrl(serverUrl);
    }

    public ProcessingResult processAllFiles(List<File> testFiles, boolean verbose,
            ProgressBar progressBar) {
        List<TestCase> allTestCases = new ArrayList<>();
        String primaryFramework = null;
        int processedFilesCount = 0;

        for (File testFile : testFiles) {
            try {
                KtFile ktFile = KotlinFileParser.parseFile(
                        Path.of(testFile.getAbsolutePath())).getKtFile();

                if (ktFile == null) {
                    continue;
                }

                String framework = detector.detectFramework(ktFile);

                if (framework == null) {
                    continue;
                }

                List<TestCase> testCases =
                        extractor.extractTestCases(ktFile, testFile.getAbsolutePath(), framework);

                if (!testCases.isEmpty()) {
                    allTestCases.addAll(testCases.stream()
                            .filter(testCase -> !testCase.isSkipped())
                            .toList());
                    if (primaryFramework == null) {
                        primaryFramework = framework;
                    }
                }
            } catch (Exception e) {
                if (verbose) {
                    throw new CliException("Error processing file " + testFile.getName(), e);
                }
            } finally {
                processedFilesCount++;
                if (progressBar != null) {
                    progressBar.update(processedFilesCount);
                }
            }
        }

        if (progressBar != null) {
            progressBar.finish();
        }

        return new ProcessingResult(allTestCases, primaryFramework);
    }

    public int handleProcessingResult(List<TestCase> allTestCases, String primaryFramework,
            String apiKey, String serverUrl, boolean dryRun,
            boolean structure) {
        if (allTestCases.isEmpty()) {
            log.info("No test methods found across all files");
            return 0;
        }

        log.info("Found {} total test methods", allTestCases.size());

        if (dryRun) {
            printAllTestCases(allTestCases);
            return allTestCases.size();
        } else {
            return exportAllTestCases(allTestCases, primaryFramework, apiKey, serverUrl, structure);
        }
    }
}
