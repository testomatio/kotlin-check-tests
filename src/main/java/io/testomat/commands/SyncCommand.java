package io.testomat.commands;

import io.testomat.client.CliClient;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Option;

@CommandLine.Command(
        name = "sync",
        description = "Run export then importId",
        mixinStandardHelpOptions = true)
public class SyncCommand implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ImportCommand.class);
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
            TestExportService exportService, TestExportService exportService1) {
        this.validator = validator;
        this.scanner = scanner;
        this.exportService = exportService1;
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

            VerboseLogger logger = new VerboseLogger(verbose);

            logger.log("Starting test export from directory: " + directory.getAbsolutePath());

            validator.validateDirectory(directory);

            List<File> testFiles = scanner.findTestFiles(directory);
            logger.log("Found " + testFiles.size() + " test files");

            if (testFiles.isEmpty()) {
                System.out.println("No test files found!");
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
            Map<String, String> syncResults = syncService.syncTestIds(apiKey, serverUrl);

            Set<String> validIds = new HashSet<>(syncResults.values());

            List<TestCase> filteredTestCases = processingResult.allTestCases().stream()
                    .filter(tc -> validIds.contains(tc.getId()))
                    .toList();

            ProcessingResult filteredResult =
                    new ProcessingResult(filteredTestCases, processingResult.primaryFramework());

            int totalExported = exportService.handleProcessingResult(
                    filteredResult.allTestCases(), filteredResult.primaryFramework(),
                        apiKey, serverUrl, dryRun, structure);

            printCompletionMessage(totalExported);

            CommandLine parent = spec.parent().commandLine();
            handeCommandExecution(parent, getImportArgsForCommand("pull-ids"));
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
        return verbose
                ? new String[]{command,
                    "--apikey=" + apiKey,
                    "--url=" + serverUrl,
                    "--directory=" + directory,
                    "--keep-structure=" + structure,
                    "-v"}
            : new String[]{command,
                "--apikey=" + apiKey,
                "--url=" + serverUrl,
                "--directory=" + directory,
                "--keep-structure=" + structure};
    }

    private void handeCommandExecution(CommandLine parent, String[] args) {
        System.out.println("Running " + args[0] + " command...");
        int code1 = parent.execute(args);
        if (code1 != 0) {
            spec.commandLine().getErr().println("import failed with code " + code1);
            System.exit(code1);
        }
    }
}
