package io.testomat.model;

import java.nio.file.Path;
import org.jetbrains.kotlin.psi.KtFile;

public class ParsedKtFile {
    private final KtFile ktFile;
    private final Path path;

    public ParsedKtFile(KtFile ktFile, Path path) {
        this.ktFile = ktFile;
        this.path = path;
    }

    public KtFile getKtFile() {
        return ktFile;
    }

    public Path getPath() {
        return path;
    }
}
