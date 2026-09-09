package io.testomat.service;

import io.testomat.model.CleanupResult;
import io.testomat.model.ParsedKtFile;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtImportDirective;

public class AnnotationCleaner {

    private static final String TEST_ID_IMPORT = TestIdUtils.TEST_ID_FQN;

    public CleanupResult cleanTestIdAnnotations(ParsedKtFile parsedKtFile, boolean dryRun) {
        KtFile ktFile = parsedKtFile.getKtFile();
        List<KtAnnotationEntry> annotations = findTestIdAnnotations(ktFile);
        List<KtImportDirective> imports = findTestIdImports(ktFile);

        if (!dryRun) {
            String newText = applyDirectModifications(ktFile, annotations, imports);
            try {
                String output = newText.replace("\n", parsedKtFile.getLineEnding());
                Files.writeString(parsedKtFile.getPath(), output);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return new CleanupResult(annotations.size(), imports.size());
    }

    private String applyDirectModifications(
            KtFile ktFile, List<KtAnnotationEntry> annotations, List<KtImportDirective> imports) {
        String text = ktFile.getText();

        List<TextRange> ranges = new ArrayList<>();

        for (KtAnnotationEntry a : annotations) {
            ranges.add(expandToLineWithNewline(text, a.getTextRange()));
        }
        for (KtImportDirective i : imports) {
            ranges.add(expandToLineWithNewline(text, i.getTextRange()));
        }

        ranges.sort((r1, r2) -> Integer.compare(r2.getStartOffset(), r1.getStartOffset()));

        for (TextRange range : ranges) {
            text = text.substring(0, range.getStartOffset())
                + text.substring(range.getEndOffset());
        }

        return text;
    }

    private List<KtAnnotationEntry> findTestIdAnnotations(KtFile ktFile) {
        Collection<KtAnnotationEntry> annotations =
                PsiTreeUtil.findChildrenOfType(ktFile, KtAnnotationEntry.class);

        return annotations.stream()
            .filter(a -> TestIdUtils.isTestIdAnnotation(a, ktFile))
            .toList();
    }

    private List<KtImportDirective> findTestIdImports(KtFile ktFile) {
        List<KtImportDirective> result = new ArrayList<>();

        for (KtImportDirective importDirective : ktFile.getImportDirectives()) {

            if (importDirective.getImportedFqName() == null) {
                continue;
            }

            String fqName = importDirective.getImportedFqName().asString();

            if (TEST_ID_IMPORT.equals(fqName)) {
                result.add(importDirective);
            }
        }

        return result;
    }

    private TextRange expandToLineWithNewline(String text, TextRange range) {
        int start = range.getStartOffset();
        int end = range.getEndOffset();

        while (start > 0 && (text.charAt(start - 1) == ' ' || text.charAt(start - 1) == '\t')) {
            start--;
        }

        if (end < text.length() && text.charAt(end) == '\r') {
            end++;
        }
        if (end < text.length() && text.charAt(end) == '\n') {
            end++;
        }

        return new TextRange(start, end);
    }
}
