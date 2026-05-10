package io.testomat.commands;

import io.testomat.client.CliClient;
import io.testomat.model.ParsedKtFile;
import io.testomat.progressbar.ProgressBar;
import io.testomat.service.KotlinFileParser;
import io.testomat.service.ResponseParser;
import io.testomat.service.TestIdAnnotationManager;
import io.testomat.service.TestIdSyncService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import picocli.CommandLine;

@CommandLine.Command(name = "pull-ids", description =
        "Pulls IDs into your codebase from testomat.io")
public class PullIdsCommand implements Runnable {
    private static final String DEFAULT_URL = "https://app.testomat.io";

    @CommandLine.Option(
            names = {"--directory", "-d"},
            defaultValue = ".")
    private String directory;

    @CommandLine.Option(
            names = {"--apikey", "-key"},
            description = "Testomat project api key",
            defaultValue = "${env:TESTOMATIO}")
    private String apiKey;

    @CommandLine.Option(
            names = "--url",
            description = "Testomat server URL")
    private String serverUrl;

    @CommandLine.Option(
            names = {"-v", "--verbose"},
            description = "Enable verbose output")
    private boolean verbose = false;

    @CommandLine.Option(
            names = {"-s", "--keep-structure"},
            description = "Prefer structure of source code over structure in Testomat.io")
    private boolean structure = false;

    public PullIdsCommand() {
    }

    @Override
    public void run() {
        // Set default URL if not provided and environment variable is not set
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            String envUrl = System.getenv("TESTOMATIO_URL");
            if (envUrl == null || envUrl.trim().isEmpty()) {
                serverUrl = DEFAULT_URL;
            } else {
                serverUrl = envUrl;
            }
        }

        TestIdSyncService syncService = createSyncService();
        List<ParsedKtFile> parsedKtFile = loadParsedKtFiles();

        if (verbose) {
            System.out.println("Found " + parsedKtFile.size() + " compilation units");
        }

        ProgressBar progressBar = new ProgressBar(100, "Processing test IDs");
        TestIdSyncService.SyncResult result = 
                syncService.syncResult(apiKey, serverUrl, parsedKtFile, verbose, progressBar);

        System.out.println("Processed " + result.getProcessedCount() + " test methods");
        System.out.println("Saved " + result.getModifiedFilesCount() + " modified files");
    }

    private TestIdSyncService createSyncService() {
        return new TestIdSyncService(
                new CliClient(),
                new ResponseParser(),
                new TestIdAnnotationManager()
        );
    }

    private List<ParsedKtFile> loadParsedKtFiles() {
        List<Path> kotlinFiles = findKotlinFiles();
        return parseKotlinFiles(kotlinFiles);
    }

    private List<Path> findKotlinFiles() {
        try (Stream<Path> pathStream = Files.walk(Paths.get(directory))) {
            return pathStream
                    .filter(path -> path.toString().endsWith(".kt"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan directory for Kotlin files", e);
        }
    }

    private List<ParsedKtFile> parseKotlinFiles(List<Path> kotlinFiles) {

        return kotlinFiles.stream()
                .map(this::parseKotlinFile)
                .collect(Collectors.toList());
    }

    private ParsedKtFile parseKotlinFile(Path kotlinFile) {
        try {
            return KotlinFileParser.parseFile(kotlinFile);
        } catch (Exception e) {
            throw new RuntimeException("Could not parse file " + kotlinFile, e);
        }
    }

    public static void main(String[] args) {
        CommandLine.run(new PullIdsCommand(), args);
    }
}
