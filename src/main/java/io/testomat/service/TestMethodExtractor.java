package io.testomat.service;

import io.testomat.model.TestCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid;

public class TestMethodExtractor {

    private static final Pattern COMMENT_LABEL_PATTERN =
            Pattern.compile("@(\\w+)(?::(\\w+))?|#(\\w+)");

    public List<TestCase> extractTestCases(KtFile file, String filepath, String framework) {
        List<TestCase> result = new ArrayList<>();

        file.accept(new KtTreeVisitorVoid() {

            @Override
            public void visitNamedFunction(@NotNull KtNamedFunction function) {
                super.visitNamedFunction(function);

                if (!isTestMethod(function)) {
                    return;
                }

                result.add(createTestCase(function, filepath, framework));
            }
        });

        return result;
    }

    private boolean isTestMethod(KtNamedFunction method) {
        String fileText = method.getContainingKtFile().getText();
        String name = method.getName();

        if (name == null) {
            return false;
        }

        int idx = fileText.indexOf("fun " + name);
        if (idx == -1) {
            return false;
        }

        String before = fileText.substring(Math.max(0, idx - 200), idx);

        return before.contains("@Test")
            || before.contains("@ParameterizedTest")
            || before.contains("@RepeatedTest")
            || before.contains("@TestFactory")
            || before.contains("@org.junit.Test");
    }

    private TestCase createTestCase(KtNamedFunction method, String filepath, String framework) {
        String testId = getTestId(method).orElse("");

        TestCase testCase = new TestCase();
        testCase.setName(getTestName(method) + testId);
        testCase.setCode(getMethodCode(method));
        testCase.setSkipped(isTestSkipped(method));
        testCase.setSuites(getSuites(method));
        testCase.setLabels(getLabels(method, framework));
        testCase.setFile(PathUtils.extractRelativeFilePath(filepath));

        return testCase;
    }

    private String getTestName(KtNamedFunction method) {
        return method.getName();
    }

    private String getMethodCode(KtNamedFunction method) {
        String annotations = extractMethodHeader(method);
        String methodText = method.getText();

        String indent = detectIndent(annotations);
        String fixedMethod = indent + methodText.replace("\n", "\n" + indent);

        return annotations + fixedMethod;
    }

    private boolean isTestSkipped(KtNamedFunction method) {

        KtFile file = method.getContainingKtFile();
        String text = file.getText();
        List<String> lines = List.of(text.split("\n"));

        int methodLine = TextUtils.getLine(method, file);

        for (int i = methodLine - 1; i >= 0; i--) {
            String line = lines.get(i).trim();

            if (!line.startsWith("@")) {
                if (!line.isEmpty()) {
                    break;
                }
                continue;
            }

            String annName = TextUtils.extractAnnotationName(line);

            if ("Disabled".equals(annName) || "Ignore".equals(annName)) {
                return true;
            }
        }

        String methodName = method.getName() != null ? method.getName() : "";

        return methodName.startsWith("ignore") || methodName.startsWith("skip");
    }

    private Optional<String> getTestId(KtNamedFunction method) {

        KtFile file = method.getContainingKtFile();
        String text = file.getText();

        List<String> lines = List.of(text.split("\n"));

        int methodLine = TextUtils.getLine(method, file);

        StringBuilder annotation = new StringBuilder();
        boolean found = false;

        for (int i = methodLine - 1; i >= 0; i--) {
            String line = lines.get(i).trim();

            if (line.startsWith("@TestId")) {
                found = true;
            }

            if (found) {
                annotation.insert(0, line);

                if (line.contains(")")) {
                    break;
                }
            }

            if (!line.isEmpty() && !line.startsWith("@") && !found) {
                break;
            }
        }

        if (!found) {
            return Optional.empty();
        }

        String textAnnotation = annotation.toString();

        int start = textAnnotation.indexOf("(");
        int end = textAnnotation.lastIndexOf(")");

        if (start == -1 || end == -1 || start >= end) {
            return Optional.empty();
        }

        String value = textAnnotation.substring(start + 1, end)
                .replace("\"", "")
                .trim();

        if (!value.startsWith("@T")) {
            value = "@T" + value;
        }

        return Optional.of(" " + value);
    }

    private List<String> getSuites(KtNamedFunction method) {
        List<String> suites = new ArrayList<>();

        KtClass currentClass =
                PsiTreeUtil.getParentOfType(method, KtClass.class);

        List<KtClass> classHierarchy = new ArrayList<>();

        while (currentClass != null) {
            classHierarchy.add(0, currentClass);
            currentClass = PsiTreeUtil.getParentOfType(currentClass, KtClass.class);
        }

        for (KtClass clazz : classHierarchy) {
            if (clazz.getName() != null) {
                suites.add(clazz.getName());
            }
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

            addFrameworkLabels(labels, annName, line, framework);
        }

        addNamePatternLabels(method, labels);

        return labels;
    }

    private void addFrameworkLabels(
            List<String> labels,
            String annName,
            String annotationLine,
            String framework
    ) {

        if ("junit".equals(framework)) {
            switch (annName) {
                case "Test":
                    labels.add("unit");
                    break;

                case "IntegrationTest":
                case "SpringBootTest":
                    labels.add("integration");
                    break;

                case "ParameterizedTest":
                    labels.add("parameterized");
                    break;

                case "RepeatedTest":
                    labels.add("repeated");
                    break;

                case "TestFactory":
                    labels.add("dynamic");
                    break;

                case "Disabled":
                case "Ignore":
                    labels.add("disabled");
                    break;

                case "Timeout":
                    labels.add("timeout");
                    break;

                case "WebMvcTest":
                    labels.add("web");
                    break;

                case "DataJpaTest":
                    labels.add("jpa");
                    break;

                case "JsonTest":
                    labels.add("json");
                    break;

                case "Tag":
                    extractValue(annotationLine).ifPresent(labels::add);
                    break;

                default:
                    if (annName.endsWith("Test")) {
                        labels.add(
                                annName.toLowerCase().replace("test", "")
                        );
                    }
            }
        } else if ("testng".equals(framework)) {
            switch (annName) {
                case "Test":
                    labels.add("unit");

                    extractGroups(annotationLine).ifPresent(groups -> {
                        for (String group : groups.split(",")) {
                            labels.add(group.trim());
                        }
                    });
                    break;

                case "DataProvider":
                    labels.add("parameterized");
                    break;

                default:
                    if (annName.endsWith("Test")) {
                        labels.add(
                                annName.toLowerCase().replace("test", "")
                        );
                    }
            }
        }
    }

    private void addNamePatternLabels(KtNamedFunction method, List<String> labels) {
        String methodName = method.getName() != null
                ? method.getName().toLowerCase() : "";

        if (methodName.contains("integration")) {
            labels.add("integration");
        }
        if (methodName.contains("smoke")) {
            labels.add("smoke");
        }
        if (methodName.contains("performance")) {
            labels.add("performance");
        }
        if (methodName.contains("acceptance")) {
            labels.add("acceptance");
        }
        if (methodName.contains("regression")) {
            labels.add("regression");
        }
    }

    private Optional<String> extractValue(String line) {
        int start = line.indexOf("(");
        int end = line.lastIndexOf(")");

        if (start == -1 || end == -1 || start >= end) {
            return Optional.empty();
        }

        String value = line.substring(start + 1, end)
                .replace("\"", "")
                .trim();

        return Optional.of(value);
    }

    private Optional<String> extractGroups(String line) {
        int idx = line.indexOf("groups");

        if (idx == -1) {
            return Optional.empty();
        }

        int start = line.indexOf("{", idx);
        int end = line.indexOf("}", idx);

        if (start == -1 || end == -1 || start >= end) {
            return Optional.empty();
        }

        String value = line.substring(start + 1, end)
                .replace("\"", "")
                .trim();

        return Optional.of(value);
    }

    private String extractMethodHeader(KtNamedFunction method) {
        KtFile file = method.getContainingKtFile();
        List<String> lines = List.of(file.getText().split("\n"));
        int methodLine = TextUtils.getLine(method, file);

        StringBuilder header = new StringBuilder();
        boolean insideAnnotation = false;

        for (int i = methodLine - 1; i >= 0; i--) {
            String line = lines.get(i);
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                continue;
            }

            if (!trimmed.startsWith("@")
                    && !trimmed.startsWith(")")
                    && !insideAnnotation) {
                break;
            }

            if (trimmed.endsWith(")")) {
                insideAnnotation = true;
                header.insert(0, line + "\n");
                continue;
            }

            if (insideAnnotation) {
                header.insert(0, line + "\n");

                if (trimmed.startsWith("@")) {
                    insideAnnotation = false;
                }

                continue;
            }

            if (trimmed.startsWith("@")) {
                header.insert(0, line + "\n");
            }
        }

        return header.toString();
    }

    private String detectIndent(String text) {
        String[] lines = text.split("\n");

        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i];

            if (!line.trim().isEmpty()) {
                int j = 0;
                while (j < line.length() && Character.isWhitespace(line.charAt(j))) {
                    j++;
                }
                return line.substring(0, j);
            }
        }

        return "";
    }
}
