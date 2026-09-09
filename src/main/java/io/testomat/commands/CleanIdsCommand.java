package io.testomat.commands;

import io.testomat.model.CleanupResult;
import io.testomat.model.FilesProcessingResult;
import io.testomat.model.ParsedKtFile;
import io.testomat.service.AnnotationCleaner;
import io.testomat.service.KotlinFileParser;
import io.testomat.service.TestFileScanner;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "clean-ids",
        description = "Remove @TestId annotations and imports from test files"
)
public class CleanIdsCommand implements Callable<Integer> {
    private static final Logger log = LoggerFactory.getLogger(CleanIdsCommand.class);

    private static final int SUCCESS_EXIT_CODE = 0;
    private static final int ERROR_EXIT_CODE = 1;

    private final TestFileScanner scanner;
    private final AnnotationCleaner cleaner;

    @Option(
            names = {"-d", "--directory"},
            description = "Directory to scan for test files (default: current directory)",
            defaultValue = ".")
    private String directory;

    @Option(
            names = {"-v", "--verbose"},
            description = "Enable verbose output")
    private boolean verbose = false;

    @Option(
            names = {"--dry-run"},
            description = "Show what would be removed without making changes")
    private boolean dryRun = false;

    public CleanIdsCommand() {
        this.scanner = new TestFileScanner();
        this.cleaner = new AnnotationCleaner();
    }

    public CleanIdsCommand(TestFileScanner scanner,
                           AnnotationCleaner cleaner) {
        this.scanner = scanner;
        this.cleaner = cleaner;
    }

    @Override
    public Integer call() {
        try {
            log.info("Starting @TestId cleanup from directory: {}",
                    Paths.get(directory).toAbsolutePath());

            List<File> kotlinFiles = scanner.findTestFiles(new File(directory));
            log.info("Found {} Kotlin files", kotlinFiles.size());

            if (kotlinFiles.isEmpty()) {
                log.info("No Kotlin files found!");
                return SUCCESS_EXIT_CODE;
            }

            FilesProcessingResult result = processFiles(kotlinFiles, cleaner);
            printSummary(result);

            return SUCCESS_EXIT_CODE;

        } catch (Exception e) {
            System.err.println("Cleanup failed: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return ERROR_EXIT_CODE;
        }
    }

    private FilesProcessingResult processFiles(List<File> kotlinFiles, AnnotationCleaner cleaner) {
        FilesProcessingResult totalResult = new FilesProcessingResult();

        for (File kotlinFile : kotlinFiles) {
            try {
                processSingleFile(kotlinFile, cleaner, totalResult);
            } catch (Exception e) {
                handleFileError(kotlinFile, e);
            }
        }

        return totalResult;
    }

    private void processSingleFile(File kotlinFile, AnnotationCleaner cleaner,
                                   FilesProcessingResult totalResult) {
        if (verbose) {
            log.info("Processing: {}", kotlinFile.getName());
        }

        ParsedKtFile parsedKtFile = KotlinFileParser.parseFile(
                Path.of(kotlinFile.getAbsolutePath()));

        if (parsedKtFile.getKtFile() == null) {
            log.info("  Skipped: Could not parse file");
            return;
        }

        CleanupResult result = cleaner.cleanTestIdAnnotations(parsedKtFile, dryRun);

        if (result.getRemovedAnnotations() > 0 || result.getRemovedImports() > 0) {
            if (verbose) {
                log.info("  Removed {} @TestId annotations", result.getRemovedAnnotations());
                log.info("  Removed {} TestId imports", result.getRemovedImports());
            }

            totalResult.addResults(result.getRemovedAnnotations(), result.getRemovedImports());
        } else {
            log.info("  No @TestId annotations or imports found");
        }
    }

    private void printSummary(FilesProcessingResult result) {
        if (dryRun) {
            log.info("\nDry run completed. No files were modified.");
            log.info("Would remove:");
        } else {
            log.info("\n Cleanup completed!");
            log.info("Removed:");
        }

        log.info("  - @TestId annotations: {}", result.getTotalAnnotations());
        log.info("  - TestId imports: {}", result.getTotalImports());
        log.info("  - Modified files: {}", result.getModifiedFiles());
    }

    private void handleFileError(File kotlinFile, Exception e) {
        System.err.println("Error processing " + kotlinFile.getName() + ": " + e.getMessage());
        if (verbose) {
            e.printStackTrace();
        }
    }
}
