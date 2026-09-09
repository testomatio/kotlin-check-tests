package io.testomat.service;

import io.testomat.model.AnnotationBlock;
import io.testomat.model.ParsedKtFile;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtImportDirective;
import org.jetbrains.kotlin.psi.KtImportList;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtPsiFactory;
import org.jetbrains.kotlin.resolve.ImportPath;

public class TestIdAnnotationManager {

    private static final String TEST_ID_IMPORT = TestIdUtils.TEST_ID_FQN;
    private static final String TEST_ID_PREFIX = "@T";

    public Optional<KtNamedFunction> findMethodInParsedKtFiles(
            List<ParsedKtFile> parsedKtFiles, TestMethodInfo methodInfo, boolean verbose) {

        if (verbose) {
            System.out.println("  Looking for method: " + methodInfo.getMethodName()
                    + " in class: " + methodInfo.getClassName()
                    + " from file: " + methodInfo.getFilePath());
        }

        Optional<KtNamedFunction> result;

        String expectedFileName = methodInfo.getFilePath() != null
                ? extractFileName(methodInfo.getFilePath()) : null;

        result = parsedKtFiles.stream()
            .filter(file -> expectedFileName == null
                    || isMatchingFile(file.getKtFile(), expectedFileName, verbose))
            .flatMap(ktFile ->
                findMethodsInParsedKtFile(ktFile.getKtFile(), methodInfo, verbose).stream())
            .findFirst();

        if (result.isPresent()) {
            if (verbose) {
                System.out.println("  Found method using exact filename match");
            }
            return result;
        }

        if (methodInfo.getFilePath() == null) {
            if (verbose) {
                System.out.println("  Not found key without file path");
            }
            return Optional.empty();
        }

        result = parsedKtFiles.stream()
            .filter(parsedKtFile ->
                isMatchingFileByPath(parsedKtFile, methodInfo.getFilePath(), verbose))
            .flatMap(ktFile ->
                findMethodsInParsedKtFile(ktFile.getKtFile(), methodInfo, verbose).stream())
            .findFirst();

        if (result.isPresent()) {
            if (verbose) {
                System.out.println("  Found method using path-based matching");
            }
            return result;
        }

        if (verbose) {
            System.out.println("  Method not found in any compilation unit");
        }

        return Optional.empty();
    }

    public void addTestIdAnnotationToMethod(KtNamedFunction method, String testId) {
        String cleanTestId = cleanTestIdValue(testId);

        Optional<KtAnnotationEntry> existingAnnotation =
                method.getAnnotationEntries().stream()
                .filter(a -> TestIdUtils.isTestIdAnnotation(a, method.getContainingKtFile()))
                .findFirst();

        if (existingAnnotation.isPresent()) {
            updateExistingTestIdAnnotation(existingAnnotation.get(), method, cleanTestId);
        } else {
            addNewTestIdAnnotation(method, cleanTestId);
        }
    }

    public void ensureTestIdImportExists(KtFile ktFile) {
        boolean hasImport = ktFile.getImportDirectives().stream()
                .anyMatch(imp ->
                imp.getImportedFqName() != null
                    && TEST_ID_IMPORT.equals(imp.getImportedFqName().asString()));

        if (!hasImport) {
            KtImportList importList = ktFile.getImportList();

            if (importList == null) {
                return;
            }

            KtPsiFactory factory = new KtPsiFactory(ktFile.getProject());
            KtImportDirective importDirective =
                    factory.createImportDirective(new ImportPath(
                            new FqName(TEST_ID_IMPORT), false));
            importList.add(importDirective);
        }
    }

    private boolean isMatchingFile(KtFile ktFile, String expectedFileName,
            boolean verbose) {

        String actualFileName = ktFile.getName();
        boolean matches = actualFileName.equals(expectedFileName);

        if (verbose) {
            System.out.println("    Checking file: " + actualFileName
                    + " vs expected: " + expectedFileName + " -> " + matches);
        }

        return matches;
    }

    private boolean isMatchingFileByPath(ParsedKtFile parsedKtFile,
            String expectedPath,
            boolean verbose) {

        String actualPath = parsedKtFile.getPath()
                .toString()
                .replace('\\', '/');

        String expectedPathStr = Paths.get(expectedPath)
                .normalize()
                .toString()
                .replace('\\', '/');

        boolean match = actualPath.endsWith(expectedPathStr)
                || expectedPathStr.endsWith(actualPath);

        if (verbose) {
            System.out.println("    Path comparison: " + actualPath + " vs "
                    + expectedPathStr + " -> " + match);
        }

        return match;
    }

    private List<KtNamedFunction> findMethodsInParsedKtFile(
            KtFile ktFile,
            TestMethodInfo methodInfo,
            boolean verbose
    ) {

        String[] lines = ktFile.getText().split("\n");

        List<AnnotationBlock> blocks = AnnotationUtils.collectAnnotationBlocks(lines);

        Collection<KtNamedFunction> allMethods =
                PsiTreeUtil.findChildrenOfType(ktFile, KtNamedFunction.class);

        List<KtNamedFunction> matchingMethods = allMethods.stream()
                .filter(m -> matchesByNameOrTitle(
                m,
                methodInfo.getMethodName(),
                blocks,
                lines
            ))
                .filter(m -> isMethodInCorrectClass(
                m,
                methodInfo.getClassName(),
                verbose
            ))
                .collect(Collectors.toList());

        if (verbose) {
            System.out.println("    Found " + allMethods.size() + " total methods, "
                    + matchingMethods.size() + " matching methods");
        }

        return matchingMethods;
    }

    private boolean matchesByNameOrTitle(
            KtNamedFunction method,
            String expectedName,
            List<AnnotationBlock> blocks,
            String[] lines
    ) {

        if (expectedName.equals(method.getName())) {
            return true;
        }

        String block = AnnotationUtils.findHeaderForMethod(method, blocks, lines);

        if (block == null) {
            return false;
        }

        String title = AnnotationUtils.extractTitle(block);

        return title != null && expectedName.equals(title);
    }

    private boolean isMethodInCorrectClass(KtNamedFunction method, String expectedClassName,
            boolean verbose) {

        KtClass clazz = PsiTreeUtil.getParentOfType(method, KtClass.class);

        if (clazz != null) {
            boolean matches = matchesClassChain(method, clazz, expectedClassName);

            if (verbose) {
                System.out.println("      Class match: " + clazz.getName() + " vs "
                        + expectedClassName + " -> " + matches);
            }

            return matches;
        }

        boolean matches = isTopLevelClassMatch(method, expectedClassName);

        if (verbose) {
            System.out.println("      Top-level method, expected class " + expectedClassName
                    + " -> " + matches);
        }

        return matches;
    }

    private boolean matchesClassChain(KtNamedFunction method, KtClass clazz,
            String expectedClassName) {
        if (expectedClassName == null || expectedClassName.isEmpty()
                || "Unknown".equals(expectedClassName)) {
            return true;
        }

        List<String> expectedChain = tokenizeClassName(expectedClassName);
        List<String> actualChain = getClassChain(method);

        return expectedChain.equals(actualChain);
    }

    private List<String> getClassChain(KtNamedFunction method) {
        List<String> chain = new ArrayList<>();

        KtClass currentClass = PsiTreeUtil.getParentOfType(method, KtClass.class);

        while (currentClass != null) {
            if (currentClass.getName() != null) {
                chain.add(0, currentClass.getName());
            }
            currentClass = PsiTreeUtil.getParentOfType(currentClass, KtClass.class);
        }

        return chain;
    }

    private List<String> tokenizeClassName(String className) {
        String normalized = className.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();

        if (normalized.isEmpty()) {
            return new ArrayList<>();
        }

        return new ArrayList<>(List.of(normalized.split("\\s+")));
    }

    private boolean isTopLevelClassMatch(KtNamedFunction method, String expectedClassName) {
        if (expectedClassName == null || expectedClassName.isEmpty()
                || "Unknown".equals(expectedClassName)) {
            return true;
        }

        String fileName = method.getContainingKtFile().getName();
        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));

        return expectedClassName.equals(baseName)
                || expectedClassName.equals(baseName + "Kt")
                || tokenizeClassName(expectedClassName).size() <= 3;
    }

    private String extractFileName(String filePath) {
        return Paths.get(filePath).getFileName().toString();
    }

    private String cleanTestIdValue(String testId) {
        return testId.replace(TEST_ID_PREFIX, "");
    }

    private void updateExistingTestIdAnnotation(KtAnnotationEntry annotation,
            KtNamedFunction method, String cleanTestId) {

        annotation.delete();
        addNewTestIdAnnotation(method, cleanTestId);
    }

    private void addNewTestIdAnnotation(KtNamedFunction method, String cleanTestId) {
        KtPsiFactory factory = new KtPsiFactory(method.getProject());

        KtAnnotationEntry annotation =
                factory.createAnnotationEntry("@TestId(\"" + cleanTestId + "\")");

        method.addAnnotationEntry(annotation);
    }

    public static class TestMethodInfo {
        private final String filePath;
        private final String className;
        private final String methodName;

        public TestMethodInfo(String filePath, String className, String methodName) {
            this.filePath = filePath;
            this.className = className;
            this.methodName = methodName;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getClassName() {
            return className;
        }

        public String getMethodName() {
            return methodName;
        }
    }
}
