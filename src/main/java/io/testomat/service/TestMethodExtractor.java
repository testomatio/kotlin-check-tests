package io.testomat.service;

import io.testomat.model.AnnotationBlock;
import io.testomat.model.TestCase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid;

public class TestMethodExtractor {

    private static final Pattern TEST_ANNOTATION_PATTERN =
            Pattern.compile("@(?:[\\w.]+\\.)?(Test|ParameterizedTest|RepeatedTest|TestFactory)\\b");
    private static final Pattern TAG_PATTERN =
            Pattern.compile("@Tag\\(\"([^\"]+)\"\\)");

    public List<TestCase> extractTestCases(KtFile file, String filepath, String framework) {
        List<TestCase> result = new ArrayList<>();

        String[] lines = file.getText().split("\n");
        List<AnnotationBlock> blocks = AnnotationUtils.collectAnnotationBlocks(lines);

        file.accept(new KtTreeVisitorVoid() {
            @Override
            public void visitNamedFunction(@NotNull KtNamedFunction function) {
                super.visitNamedFunction(function);

                String header = AnnotationUtils.findHeaderForMethod(function, blocks, lines);

                if (!isTestMethod(function, header)) {
                    return;
                }

                result.add(createTestCase(function, filepath, framework, header));
            }
        });

        return result;
    }

    private boolean isTestMethod(KtNamedFunction method, String header) {
        String methodText = method.getText();

        return containsTestAnnotation(header)
            || containsTestAnnotation(methodText);
    }

    private boolean containsTestAnnotation(String text) {
        return TEST_ANNOTATION_PATTERN.matcher(text).find();
    }

    private TestCase createTestCase(
            KtNamedFunction method, String filepath, String framework, String header) {
        String testId = getTestId(header).orElse("");
        String title = getTestName(method, header);

        TestCase testCase = new TestCase();
        testCase.setId(testId);
        testCase.setName(testId.isBlank() ? title : title + " " + testId);
        testCase.setTitle(title);
        testCase.setCode(getMethodCode(method, header));
        testCase.setSkipped(isTestSkipped(method, header));
        testCase.setSuites(getSuites(method));
        testCase.setLabels(getLabels(method, framework));
        testCase.setFile(PathUtils.extractRelativeFilePath(filepath));

        return testCase;
    }

    private String getTestName(KtNamedFunction method, String header) {
        String title = AnnotationUtils.extractTitle(header);

        if (title != null && !title.isBlank()) {
            return title;
        }

        return method.getName();
    }

    private String getMethodCode(KtNamedFunction method, String header) {
        String cleanHeader = normalizeHeader(header);
        String cleanMethod = formatMethod(stripAnnotations(method));

        return cleanHeader + cleanMethod;
    }

    private String stripAnnotations(KtNamedFunction method) {
        String text = method.getText();
        int baseOffset = method.getTextRange().getStartOffset();

        List<TextRange> ranges = new ArrayList<>();

        for (KtAnnotationEntry entry : method.getAnnotationEntries()) {
            TextRange range = entry.getTextRange().shiftLeft(baseOffset);

            int start = range.getStartOffset();
            while (start > 0 && (text.charAt(start - 1) == ' '
                    || text.charAt(start - 1) == '\t')) {
                start--;
            }

            int end = range.getEndOffset();
            if (end < text.length() && text.charAt(end) == '\r') {
                end++;
            }
            if (end < text.length() && text.charAt(end) == '\n') {
                end++;
            }

            ranges.add(new TextRange(start, end));
        }

        ranges.sort(Comparator.comparingInt(TextRange::getStartOffset).reversed());

        StringBuilder result = new StringBuilder(text);

        for (TextRange range : ranges) {
            result.delete(range.getStartOffset(), range.getEndOffset());
        }

        return result.toString();
    }

    private String formatMethod(String text) {
        String[] lines = text.split("\n");

        if (lines.length == 0) {
            return text;
        }

        StringBuilder result = new StringBuilder();

        result.append(lines[0].trim());

        int baseIndent = -1;

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];

            if (line.trim().isEmpty()) {
                result.append("\n");
                continue;
            }

            int j = 0;
            while (j < line.length() && Character.isWhitespace(line.charAt(j))) {
                j++;
            }

            if (baseIndent == -1) {
                baseIndent = j;
            }

            int relativeIndent = j - baseIndent;
            if (relativeIndent < 0) {
                relativeIndent = 0;
            }

            String trimmed = line.trim();

            result.append("\n");

            if (isClosingDelimiter(trimmed) && i == lines.length - 1) {
                result.append("");
            } else if (trimmed.equals("}")) {
                result.append(" ".repeat(4));
            } else {
                result.append(" ".repeat(4 + relativeIndent));
            }

            result.append(trimmed);
        }

        return result.toString();
    }

    private boolean isClosingDelimiter(String trimmed) {
        return trimmed.equals("}") || trimmed.equals(")") || trimmed.equals("]");
    }

    private String normalizeHeader(String text) {
        String[] lines = text.split("\n");

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (i > 0) {
                result.append("\n");
            }
            result.append(line);
        }

        return !result.isEmpty() ? result.append("\n").toString() : "";
    }

    private boolean isTestSkipped(KtNamedFunction method, @NotNull String header) {
        if (header.contains("@Disabled") || header.contains("@Ignore")) {
            return true;
        }

        String name = method.getName() != null ? method.getName().toLowerCase() : "";
        return name.startsWith("ignore") || name.startsWith("skip");
    }

    private Optional<String> getTestId(String header) {
        Matcher m = Pattern.compile("@TestId\\s*\\((.*?)\\)", Pattern.DOTALL).matcher(header);

        if (!m.find()) {
            return Optional.empty();
        }

        String value = m.group(1).replace("\"", "").trim();

        if (!value.startsWith("@T")) {
            value = "@T" + value;
        }

        return Optional.of(value);
    }

    private List<String> getSuites(KtNamedFunction method) {
        List<String> suites = new ArrayList<>();

        KtClass currentClass = PsiTreeUtil.getParentOfType(method, KtClass.class);

        while (currentClass != null) {
            if (currentClass.getName() != null) {
                suites.add(0, currentClass.getName());
            }
            currentClass = PsiTreeUtil.getParentOfType(currentClass, KtClass.class);
        }

        return suites;
    }

    private List<String> getLabels(KtNamedFunction method, String framework) {
        List<String> labels = new ArrayList<>();

        List<String> annotations = AnnotationScanner.findAnnotationsAbove(method);

        for (String line : annotations) {
            String annName = TextUtils.extractAnnotationName(line);
            if (annName == null) {
                continue;
            }

            addFrameworkLabels(labels, annName, framework);
            addAnnotationLabels(labels, line);
        }

        addNamePatternLabels(method, labels);

        return labels;
    }

    private void addFrameworkLabels(List<String> labels, String annName, String framework) {
        if ("junit".equals(framework)) {
            if ("Test".equals(annName)) {
                labels.add("unit");
            }
        } else if ("testng".equals(framework)) {
            if ("Test".equals(annName)) {
                labels.add("unit");
            }
        }
    }

    private void addNamePatternLabels(KtNamedFunction method, List<String> labels) {
        String name = method.getName() != null ? method.getName().toLowerCase() : "";

        if (name.contains("integration")) {
            labels.add("integration");
        }
        if (name.contains("smoke")) {
            labels.add("smoke");
        }
    }

    private void addAnnotationLabels(List<String> labels, String line) {
        Matcher matcher = TAG_PATTERN.matcher(line);

        if (matcher.find()) {
            String value = matcher.group(1);

            if (!labels.contains(value)) {
                labels.add(value);
            }
        }
    }
}
