package io.testomat.service;

import io.testomat.exception.CliException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtFileAnnotationList;
import org.jetbrains.kotlin.psi.KtImportDirective;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtPackageDirective;

public class MinimalFileModificationService {

    private static final String TEST_ID_IMPORT = TestIdUtils.TEST_ID_FQN;

    public static class FileModification {
        private final KtFile ktFile;
        private final Map<KtNamedFunction, String> methodAnnotations = new IdentityHashMap<>();
        private final List<KtAnnotationEntry> annotationsToRemove = new ArrayList<>();
        private final List<KtImportDirective> importsToRemove = new ArrayList<>();
        private boolean needsImport = false;
        private Path filePath;

        public FileModification(KtFile ktFile) {
            this.ktFile = ktFile;
        }

        public void addMethodAnnotation(KtNamedFunction method, String testId) {
            methodAnnotations.put(method, testId);
        }

        public void removeAnnotation(KtAnnotationEntry annotation) {
            annotationsToRemove.add(annotation);
        }

        public void removeImport(KtImportDirective importDecl) {
            importsToRemove.add(importDecl);
        }

        public void setNeedsImport(boolean needsImport) {
            this.needsImport = needsImport;
        }

        public KtFile getKtFile() {
            return ktFile;
        }

        public Map<KtNamedFunction, String> getMethodAnnotations() {
            return methodAnnotations;
        }

        public List<KtAnnotationEntry> getAnnotationsToRemove() {
            return annotationsToRemove;
        }

        public List<KtImportDirective> getImportsToRemove() {
            return importsToRemove;
        }

        public boolean needsImport() {
            return needsImport;
        }

        public boolean hasModifications() {
            return !methodAnnotations.isEmpty() || needsImport
                || !annotationsToRemove.isEmpty()
                || !importsToRemove.isEmpty();
        }

        public Path getFilePath() {
            return filePath;
        }

        public void setFilePath(Path filePath) {
            this.filePath = filePath;
        }
    }

    public void applyModifications(FileModification modification) {
        if (!modification.hasModifications()) {
            return;
        }

        KtFile ktFile = modification.getKtFile();
        Path filePath = modification.getFilePath();

        try {
            String text = ktFile.getText();
            text = StringUtil.convertLineSeparators(text);

            List<String> lines = new ArrayList<>(List.of(text.split("\n")));
            List<TextModification> modifications = new ArrayList<>();

            for (KtAnnotationEntry annotation : modification.getAnnotationsToRemove()) {
                modifications.add(createAnnotationRemoval(annotation, ktFile));
            }

            for (KtImportDirective importDecl : modification.getImportsToRemove()) {
                modifications.add(createImportRemoval(importDecl, ktFile));
            }

            if (modification.needsImport()) {
                TextModification insert = createImportInsertion(ktFile);
                if (insert != null) {
                    modifications.add(insert);
                }
            }

            for (Map.Entry<KtNamedFunction, String> entry :
                    modification.getMethodAnnotations().entrySet()) {

                KtNamedFunction method = entry.getKey();
                String testId = entry.getValue();

                int methodLine = TextUtils.getLine(method, ktFile);

                List<Integer> existingLines = findExistingTestIdLines(method, ktFile);

                for (Integer lineToDelete : existingLines) {
                    modifications.add(new TextModification(
                            lineToDelete,
                            null,
                            ModificationType.DELETE
                    ));
                }

                modifications.add(createAnnotationInsertion(lines, method, testId, ktFile));
            }

            modifications.sort((a, b) -> Integer.compare(b.lineNumber, a.lineNumber));
            applyModifications(lines, modifications);

            Files.write(modification.getFilePath(), lines, StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new CliException("Failed to modify file: " + filePath, e);
        }
    }

    private void applyModifications(List<String> lines, List<TextModification> modifications) {
        for (TextModification modification : modifications) {
            if (modification.type == ModificationType.INSERT) {
                lines.add(modification.lineNumber, modification.text);
            } else if (modification.type == ModificationType.REPLACE) {
                lines.set(modification.lineNumber, modification.text);
            } else if (modification.type == ModificationType.DELETE) {
                lines.remove(modification.lineNumber);
            }
        }
    }

    private TextModification createAnnotationRemoval(KtAnnotationEntry annotation, KtFile file) {
        int line = TextUtils.getLine(annotation, file);
        return new TextModification(line, null, ModificationType.DELETE);
    }

    private TextModification createImportRemoval(KtImportDirective importDecl, KtFile file) {
        int line = TextUtils.getLine(importDecl, file);
        return new TextModification(line, null, ModificationType.DELETE);
    }

    private TextModification createImportInsertion(KtFile ktFile) {

        boolean hasImport = ktFile.getImportDirectives().stream()
                .anyMatch(imp ->
                imp.getImportedFqName() != null
                    && TEST_ID_IMPORT.equals(imp.getImportedFqName().asString())
            );

        if (hasImport) {
            return null;
        }

        int insertLine = findImportInsertPosition(ktFile);

        return new TextModification(insertLine,
            "import " + TEST_ID_IMPORT,
            ModificationType.INSERT);
    }

    private int findImportInsertPosition(KtFile ktFile) {
        List<KtImportDirective> imports = ktFile.getImportDirectives();

        if (!imports.isEmpty()) {
            return TextUtils.getLine(imports.get(imports.size() - 1), ktFile) + 1;
        }

        KtPackageDirective packageDirective = ktFile.getPackageDirective();

        if (packageDirective != null
                && packageDirective.getQualifiedName() != null
                && !packageDirective.getQualifiedName().isEmpty()) {
            return TextUtils.getLine(packageDirective, ktFile) + 1;
        }

        KtFileAnnotationList fileAnnotationList = ktFile.getFileAnnotationList();

        if (fileAnnotationList != null) {
            return TextUtils.getLineByOffset(
                    ktFile.getText(),
                    fileAnnotationList.getTextRange().getEndOffset()) + 1;
        }

        return 0;
    }

    private TextModification createAnnotationInsertion(List<String> lines,
            KtNamedFunction method, String testId, KtFile file) {
        String cleanTestId = cleanTestId(testId);

        int line = TextUtils.getLine(method, file);
        String indentation = detectIndentation(lines.get(line));

        String annotationLine = indentation + "@TestId(\"" + cleanTestId + "\")";

        return new TextModification(line, annotationLine, ModificationType.INSERT);
    }

    private List<Integer> findExistingTestIdLines(KtNamedFunction method, KtFile file) {
        List<Integer> result = new ArrayList<>();

        int methodLine = TextUtils.getLine(method, file);
        String text = file.getText();

        for (KtAnnotationEntry entry : method.getAnnotationEntries()) {
            if (!TestIdUtils.isTestIdAnnotation(entry, file)) {
                continue;
            }

            int startLine = TextUtils.getLineByOffset(text, entry.getTextRange().getStartOffset());
            int endLine = TextUtils.getLineByOffset(text, entry.getTextRange().getEndOffset());

            if (startLine >= methodLine) {
                continue;
            }

            for (int i = startLine; i <= endLine; i++) {
                result.add(i);
            }
        }

        return result;
    }

    private String detectIndentation(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }

    private String cleanTestId(String testId) {
        return testId.replace("@T", "");
    }

    private static class TextModification {
        private final int lineNumber;
        private final String text;
        private final ModificationType type;

        TextModification(int lineNumber, String text, ModificationType type) {
            this.lineNumber = lineNumber;
            this.text = text;
            this.type = type;
        }
    }

    private enum ModificationType {
        INSERT,
        REPLACE,
        DELETE
    }
}
