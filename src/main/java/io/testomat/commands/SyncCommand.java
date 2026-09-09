package io.testomat.commands;

import io.testomat.client.CliClient;
import io.testomat.exception.CliException;
import io.testomat.model.ProcessingResult;
import io.testomat.model.TestCase;
import io.testomat.progressbar.ProgressBar;
import io.testomat.service.DirectoryValidator;
import io.testomat.service.ResponseParser;
import io.testomat.service.TestExportService;
import io.testomat.service.TestFileScanner;
import io.testomat.service.TestIdAnnotationManager;
import io.testomat.service.TestIdSyncService;
import io.testomat.service.VerboseLogger;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import picocli.CommandLine;
import picocli.CommandLine.Option;

@CommandLine.Command(
        name = "sync",
        aliases = {"update-ids"},
        description = "Run export then importId",
        mixinStandardHelpOptions = true)
public class SyncCommand implements Runnable {
    private static final String VERSION = "v.0.1.1";

    private static final String CURRENT_DIRECTORY = ".";
    private static final String DEFAULT_URL = "https://app.testomat.io";

    private final DirectoryValidator validator;
    private final TestFileScanner scanner;
    private final TestExportService exportService;

    @Option(
            names = {"-d", "--directory"},
            description = "Directory to scan for test files (default: current directory)",
            defaultValue = ".")
    private File directory = new File(CURRENT_DIRECTORY);

    @Option(
            names = {"-key", "--apikey"},
            description = "API key for testomat.io",
            defaultValue = "${env:TESTOMATIO}")
    private String apiKey;

    @Option(
            names = "--url",
            description = "Testomat server URL")
    private String serverUrl;

    @Option(
            names = {"-v", "--verbose"},
            description = "Enable verbose output")
    private boolean verbose = false;

    @Option(
            names = {"--dry-run"},
            description = "Show what would be exported without sending")
    private boolean dryRun = false;

    @Option(
            names = {"-s", "--keep-structure"},
            description = "Prefer structure of source code over structure in Testomat.io")
    private boolean structure = false;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    public SyncCommand() {
        this.exportService = new TestExportService();
        this.validator = new DirectoryValidator();
        this.scanner = new TestFileScanner();
    }

    public SyncCommand(DirectoryValidator validator,
            TestFileScanner scanner,
            TestExportService exportService) {
        this.validator = validator;
        this.scanner = scanner;
        this.exportService = exportService;
    }

    @Override
    public void run() {
        System.out.println("KOTLIN-CHECK-TESTS " + VERSION);

        try {
            if (serverUrl == null || serverUrl.trim().isEmpty()) {
                String envUrl = System.getenv("TESTOMATIO_URL");
                if (envUrl == null || envUrl.trim().isEmpty()) {
                    serverUrl = DEFAULT_URL;
                } else {
                    serverUrl = envUrl;
                }
            }

            boolean hasApiKey = apiKey != null && !apiKey.trim().isEmpty();

            if (!hasApiKey && !dryRun) {
                System.out.println("TESTOMATIO API key not provided, running in dry-run mode");
                dryRun = true;
            }

            VerboseLogger logger = new VerboseLogger(verbose);

            logger.log("Starting test export from directory: " + directory.getAbsolutePath());

            validator.validateDirectory(directory);

            List<File> testFiles = scanner.findTestFiles(directory);
            logger.log("Found " + testFiles.size() + " test files");

            if (testFiles.isEmpty()) {
                System.out.println("No test files found!");
                return;
            }

            TestIdSyncService syncService = new TestIdSyncService(
                    new CliClient(),
                    new ResponseParser(),
                    new TestIdAnnotationManager()
            );

            ProgressBar progressBar = new ProgressBar(testFiles.size(),
                    "Parsing " + testFiles.size() + " files");

            ProcessingResult processingResult =
                    exportService.processAllFiles(testFiles, verbose, progressBar);

            Map<String, String> syncResults = hasApiKey
                    ? syncService.syncTestIds(apiKey, serverUrl)
                    : Map.of();

            Set<String> validIds = new HashSet<>(syncResults.values());

            List<TestCase> filteredTestCases = processingResult.allTestCases().stream()
                    .filter(tc -> syncResults.isEmpty() || validIds.contains(tc.getId()))
                    .toList();

            ProcessingResult filteredResult =
                    new ProcessingResult(filteredTestCases, processingResult.primaryFramework());

            int totalExported = exportService.handleProcessingResult(
                    filteredResult.allTestCases(), filteredResult.primaryFramework(),
                        apiKey, serverUrl, dryRun, structure);

            printCompletionMessage(totalExported);

            if (hasApiKey) {
                CommandLine parent = spec.parent().commandLine();
                handeCommandExecution(parent, getImportArgsForCommand("pull-ids"));
            } else {
                System.out.println("Skipping pull-ids: API key not provided");
            }
        } catch (Exception e) {
            System.err.println("Export failed: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }
    }

    private void printCompletionMessage(int totalExported) {
        if (dryRun) {
            System.out.println("\nDry run completed. No data was sent to server.");
            System.out.println("Found " + totalExported + " test methods");
            if (apiKey == null || apiKey.trim().isEmpty()) {
                System.out.println("Run the same command with apikey and url provided to execute.");
            }
        } else {
            System.out.println("\n");
        }
    }

    private String[] getImportArgsForCommand(String command) {
        List<String> args = new ArrayList<>();
        args.add(command);
        args.add("--apikey=" + apiKey);
        args.add("--url=" + serverUrl);
        args.add("--directory=" + directory);
        args.add("--keep-structure=" + structure);

        if (verbose) {
            args.add("-v");
        }

        return args.toArray(new String[0]);
    }

    private void handeCommandExecution(CommandLine parent, String[] args) {
        System.out.println("Running " + args[0] + " command...");
        int code1 = parent.execute(args);
        if (code1 != 0) {
            throw new CliException("pull-ids failed with code " + code1);
        }
    }
}
