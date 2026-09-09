package io.testomat.service;

import io.testomat.model.TestCase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
    private static final Pattern TITLE_PATTERN =
            Pattern.compile("@Title\\s*\\(\\s*\"([^\"]+)\"");

    public List<TestCase> extractTestCases(KtFile file, String filepath, String framework) {
        List<TestCase> result = new ArrayList<>();

        file.accept(new KtTreeVisitorVoid() {
            @Override
            public void visitNamedFunction(@NotNull KtNamedFunction function) {
                super.visitNamedFunction(function);

                String header = buildHeader(function);

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
        testCase.setSuites(getSuitesForMethod(method, filepath));
        testCase.setLabels(getLabels(method, framework));
        testCase.setFile(PathUtils.extractRelativeFilePath(filepath));

        return testCase;
    }

    private String buildHeader(KtNamedFunction method) {
        return method.getAnnotationEntries().stream()
                .map(entry -> entry.getText().trim())
                .collect(Collectors.joining("\n"));
    }

    private List<String> getSuitesForMethod(KtNamedFunction method, String filepath) {
        List<String> suites = getSuites(method);

        if (!suites.isEmpty()) {
            return suites;
        }

        return getFileDirectorySuites(filepath);
    }

    private List<String> getFileDirectorySuites(String filepath) {
        String relative = PathUtils.extractRelativeFilePath(filepath);
        int lastSlash = relative.lastIndexOf('/');

        if (lastSlash == -1) {
            return new ArrayList<>();
        }

        String directory = relative.substring(0, lastSlash);
        return new ArrayList<>(List.of(directory.split("/")));
    }

    private String getTestName(KtNamedFunction method, String header) {
        String title = extractTitle(header);

        if (title != null && !title.isBlank()) {
            return title;
        }

        return method.getName();
    }

    private String extractTitle(String header) {
        Matcher matcher = TITLE_PATTERN.matcher(header);

        return matcher.find() ? matcher.group(1) : null;
    }

    private String getMethodCode(KtNamedFunction method, String header) {
        String methodText = deindentToMethodLevel(stripAnnotations(method), method);

        if (header.isBlank()) {
            return methodText;
        }

        return header + "\n" + methodText;
    }

    private String deindentToMethodLevel(String text, KtNamedFunction method) {
        KtFile file = method.getContainingKtFile();
        String[] fileLines = file.getText().split("\n");
        int methodLine = TextUtils.getLine(method, file);

        if (methodLine >= fileLines.length) {
            return text;
        }

        String methodLineText = fileLines[methodLine];
        int indent = 0;
        while (indent < methodLineText.length()
                && (methodLineText.charAt(indent) == ' '
                || methodLineText.charAt(indent) == '\t')) {
            indent++;
        }

        if (indent == 0) {
            return text;
        }

        String[] textLines = text.split("\n", -1);
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < textLines.length; i++) {
            String line = textLines[i];

            int remove = 0;
            while (remove < indent && remove < line.length()
                    && (line.charAt(remove) == ' ' || line.charAt(remove) == '\t')) {
                remove++;
            }

            if (i > 0) {
                result.append("\n");
            }

            result.append(line.substring(remove));
        }

        return result.toString();
    }

    private String stripAnnotations(KtNamedFunction method) {
        String text = method.getText();
        int baseOffset = method.getTextRange().getStartOffset();

        List<TextRange> ranges = new ArrayList<>();

        for (KtAnnotationEntry entry : method.getAnnotationEntries()) {
            TextRange range = entry.getTextRange().shiftLeft(baseOffset);

            int end = range.getEndOffset();
            if (end < text.length() && text.charAt(end) == '\r') {
                end++;
            }
            if (end < text.length() && text.charAt(end) == '\n') {
                end++;
            }
            while (end < text.length() && (text.charAt(end) == ' '
                    || text.charAt(end) == '\t')) {
                end++;
            }

            ranges.add(new TextRange(range.getStartOffset(), end));
        }

        ranges.sort(Comparator.comparingInt(TextRange::getStartOffset).reversed());

        StringBuilder result = new StringBuilder(text);

        for (TextRange range : ranges) {
            result.delete(range.getStartOffset(), range.getEndOffset());
        }

        while (result.length() > 0 && Character.isWhitespace(result.charAt(0))) {
            result.deleteCharAt(0);
        }

        return result.toString();
    }

    private boolean isTestSkipped(KtNamedFunction method, @NotNull String header) {
        for (String line : header.split("\n")) {
            String annName = TextUtils.extractAnnotationName(line);

            if ("Disabled".equals(annName) || "Ignore".equals(annName)) {
                return true;
            }
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

        List<String> annotations = method.getAnnotationEntries().stream()
                .map(entry -> entry.getText().trim())
                .collect(Collectors.toList());

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
