package io.testomat.service;

import io.testomat.client.TestomatHttpClient;
import io.testomat.model.ParsedKtFile;
import io.testomat.progressbar.LoadingSpinner;
import io.testomat.progressbar.ProgressBar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNamedFunction;

public class TestIdSyncService {

    private static final int PATH_INDEX = 0;
    private static final int CLASS_NAME_INDEX = 1;
    private static final int METHOD_NAME_INDEX = 2;
    private static final int EXPECTED_PARTS_COUNT = 3;
    private static final String SPLIT_DELIMITER = "#";
    private static final String TEST_ID_IMPORT = "io.testomat.core.annotation.TestId";

    private final TestomatHttpClient httpClient;
    private final ResponseParser responseParser;
    private final TestIdAnnotationManager annotationManager;
    private final MinimalFileModificationService fileModificationService;

    public TestIdSyncService(TestomatHttpClient httpClient, ResponseParser responseParser,
            TestIdAnnotationManager annotationManager) {
        this.httpClient = httpClient;
        this.responseParser = responseParser;
        this.annotationManager = annotationManager;
        this.fileModificationService = new MinimalFileModificationService();
    }

    public Map<String, String> syncTestIds(String apiKey, String serverUrl) {

        LoadingSpinner spinner = new LoadingSpinner("Fetching test data from server...");
        spinner.start();

        String response = httpClient.sendGetRequest(apiKey, serverUrl);
        Map<String, String> testsMap = responseParser.parseTestsFromResponse(response);

        spinner.stopWithMessage("Received test data from server");

        System.out.println("Received " + testsMap.size() + " test entries from API");

        return testsMap;
    }

    public SyncResult syncResult(String apiKey, String serverUrl,
                List<ParsedKtFile> parsedKtFiles, boolean verbose,
                ProgressBar progressBar) {

        Map<String, String> testsMap = syncTestIds(apiKey, serverUrl);

        if (progressBar != null && testsMap.size() != progressBar.getTotal()) {
            progressBar = new ProgressBar(testsMap.size(), "Processing test IDs");
        }

        Map<KtFile, MinimalFileModificationService.FileModification> modifications =
                new HashMap<>();

        int processedCount = processTestMethods(
                parsedKtFiles, testsMap, modifications, verbose, progressBar);

        int modifiedFilesCount = applyFileModifications(modifications);

        return new SyncResult(processedCount, modifiedFilesCount);
    }

    private int processTestMethods(
            List<ParsedKtFile> parsedKtFiles,
            Map<String, String> testsMap,
            Map<KtFile, MinimalFileModificationService.FileModification> modifications,
            boolean verbose,
            ProgressBar progressBar) {

        int processedCount = 0;
        int skippedCount = 0;
        int currentEntry = 0;

        for (Map.Entry<String, String> testEntry : testsMap.entrySet()) {
            currentEntry++;

            String testKey = testEntry.getKey();
            String testId = testEntry.getValue();

            TestIdAnnotationManager.TestMethodInfo methodInfo =
                    parseTestKey(testKey, verbose);

            if (methodInfo == null) {
                skippedCount++;
                continue;
            }

            Optional<KtNamedFunction> methodOptional =
                    annotationManager.findMethodInParsedKtFiles(
                    parsedKtFiles, methodInfo, verbose);

            if (methodOptional.isPresent()) {

                KtNamedFunction method = methodOptional.get();
                KtFile ktFile = method.getContainingKtFile();

                if (ktFile != null) {

                    boolean canUseMinimalMod = method.getTextOffset() >= 0;

                    if (canUseMinimalMod) {

                        MinimalFileModificationService.FileModification modification =
                                modifications.computeIfAbsent(
                                ktFile,
                                MinimalFileModificationService.FileModification::new);

                        modification.addMethodAnnotation(method, testId);

                        parsedKtFiles.stream()
                                .filter(file ->
                                file.getKtFile().equals(ktFile))
                                .findFirst()
                                .ifPresent(parsedKtFile ->
                                modification.setFilePath(parsedKtFile.getPath()));

                        boolean hasImport = ktFile.getImportDirectives().stream()
                                .anyMatch(imp ->
                                    imp.getImportedFqName() != null
                                        && TEST_ID_IMPORT.equals(
                                        imp.getImportedFqName().asString()));

                        if (!hasImport) {
                            modification.setNeedsImport(true);
                        }

                    } else {
                        annotationManager.addTestIdAnnotationToMethod(method, testId);
                        annotationManager.ensureTestIdImportExists(ktFile);
                    }

                    processedCount++;
                } else {
                    skippedCount++;
                }

            } else {
                skippedCount++;
            }

            if (progressBar != null) {
                progressBar.update(currentEntry);
            }
        }

        if (progressBar != null) {
            progressBar.finish();
        }

        if (skippedCount > 0) {
            System.out.println("Skipped " + skippedCount + " test methods");
        }

        return processedCount;
    }

    private TestIdAnnotationManager.TestMethodInfo parseTestKey(String testKey, boolean verbose) {

        String[] parts = testKey.split(SPLIT_DELIMITER);

        if (parts.length != EXPECTED_PARTS_COUNT) {
            return null;
        }

        String filePath = parts[PATH_INDEX].trim();
        String className = parts[CLASS_NAME_INDEX].trim();
        String methodName = parts[METHOD_NAME_INDEX].trim();

        return new TestIdAnnotationManager.TestMethodInfo(
            filePath, className, methodName);
    }

    private int applyFileModifications(
            Map<KtFile, MinimalFileModificationService.FileModification> modifications) {

        int modifiedCount = 0;

        for (MinimalFileModificationService.FileModification modification
                : modifications.values()) {

            if (modification.hasModifications()) {
                fileModificationService.applyModifications(modification);
                modifiedCount++;
            }
        }

        return modifiedCount;
    }

    public static class SyncResult {
        private final int processedCount;
        private final int modifiedFilesCount;

        public SyncResult(int processedCount) {
            this(processedCount, 0);
        }

        public SyncResult(int processedCount, int modifiedFilesCount) {
            this.processedCount = processedCount;
            this.modifiedFilesCount = modifiedFilesCount;
        }

        public int getProcessedCount() {
            return processedCount;
        }

        public int getModifiedFilesCount() {
            return modifiedFilesCount;
        }
    }
}
