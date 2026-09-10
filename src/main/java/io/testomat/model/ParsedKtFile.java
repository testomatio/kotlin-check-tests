package io.testomat.model;

import java.nio.file.Path;
import org.jetbrains.kotlin.psi.KtFile;

public class ParsedKtFile {
    private final KtFile ktFile;
    private final Path path;
    private final String lineEnding;

    public ParsedKtFile(KtFile ktFile, Path path, String lineEnding) {
        this.ktFile = ktFile;
        this.path = path;
        this.lineEnding = lineEnding != null ? lineEnding : "\n";
    }

    public KtFile getKtFile() {
        return ktFile;
    }

    public Path getPath() {
        return path;
    }

    public String getLineEnding() {
        return lineEnding;
    }
}
