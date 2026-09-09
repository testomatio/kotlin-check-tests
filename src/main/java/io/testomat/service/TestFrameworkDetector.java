package io.testomat.service;

import java.util.Collection;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtImportDirective;
import org.jetbrains.kotlin.psi.KtNamedFunction;

public class TestFrameworkDetector {

    public String detectFramework(KtFile ktFile) {
        String framework = detectFromImports(ktFile);
        if (framework != null) {
            return framework;
        }

        framework = detectFromAnnotations(ktFile);
        if (framework != null) {
            return framework;
        }

        return detectFromPatterns(ktFile);
    }

    private String detectFromImports(KtFile ktFile) {
        for (KtImportDirective imp : ktFile.getImportDirectives()) {

            if (imp.getImportedFqName() == null) {
                continue;
            }

            String importName = imp.getImportedFqName().asString();

            if (importName.startsWith("org.junit.jupiter")) {
                return "junit";
            }

            if (importName.equals("org.junit.Test")
                    || importName.startsWith("org.junit.")
                    && !importName.startsWith("org.junit.jupiter")) {
                return "junit";
            }

            if (importName.startsWith("org.testng")) {
                return "testng";
            }

            if (importName.startsWith("org.springframework.test")
                    && hasJUnitImports(ktFile)) {
                return "junit";
            }
        }
        return null;
    }

    private String detectFromAnnotations(KtFile ktFile) {
        Collection<KtClass> classes =
                PsiTreeUtil.findChildrenOfType(ktFile, KtClass.class);

        for (KtClass clazz : classes) {
            String framework = checkClassAnnotations(clazz);
            if (framework != null) {
                return framework;
            }
        }

        Collection<KtNamedFunction> methods =
                PsiTreeUtil.findChildrenOfType(ktFile, KtNamedFunction.class);

        for (KtNamedFunction method : methods) {
            String framework = checkMethodAnnotations(method);
            if (framework != null) {
                return framework;
            }
        }

        return null;
    }

    private String checkClassAnnotations(KtClass clazz) {
        for (KtAnnotationEntry entry : clazz.getAnnotationEntries()) {
            String annName = getAnnotationName(entry);

            if ("SpringBootTest".equals(annName)
                    || "WebMvcTest".equals(annName)
                    || "DataJpaTest".equals(annName)
                    || "JsonTest".equals(annName)) {
                return "junit";
            }

            if ("Test".equals(annName)) {
                return detectFromAnnotationContext(clazz.getContainingKtFile());
            }
        }

        return null;
    }

    private String checkMethodAnnotations(KtNamedFunction method) {
        for (KtAnnotationEntry entry : method.getAnnotationEntries()) {
            String annName = getAnnotationName(entry);

            if (annName.equals("ParameterizedTest")
                    || annName.equals("RepeatedTest")
                    || annName.equals("TestFactory")
                    || annName.equals("DisplayName")
                    || annName.equals("BeforeEach")
                    || annName.equals("AfterEach")
                    || annName.equals("BeforeAll")
                    || annName.equals("AfterAll")) {
                return "junit";
            }

            if (annName.equals("DataProvider")
                    || annName.equals("BeforeMethod")
                    || annName.equals("AfterMethod")
                    || annName.equals("BeforeClass")
                    || annName.equals("AfterClass")
                    || annName.equals("BeforeTest")
                    || annName.equals("AfterTest")
                    || annName.equals("BeforeSuite")
                    || annName.equals("AfterSuite")) {
                return "testng";
            }

            if (annName.equals("Test")) {
                return detectFromAnnotationContext(method.getContainingKtFile());
            }
        }

        return null;
    }

    private String getAnnotationName(KtAnnotationEntry entry) {
        return entry.getShortName() != null
                ? entry.getShortName().getIdentifier()
                : "";
    }

    private String detectFromPatterns(KtFile ktFile) {

        Collection<KtNamedFunction> methods =
                PsiTreeUtil.findChildrenOfType(ktFile, KtNamedFunction.class);

        for (KtNamedFunction method : methods) {
            String methodName = method.getName();

            if (methodName != null
                    && (methodName.contains("dataProvider")
                    || methodName.contains("DataProvider"))) {
                return "testng";
            }
        }

        return null;
    }

    private String detectFromAnnotationContext(KtFile ktFile) {

        if (ktFile == null) {
            return "junit";
        }

        boolean hasJunit5 = ktFile.getImportDirectives().stream()
                .anyMatch(imp ->
                imp.getImportedFqName() != null
                    && imp.getImportedFqName().asString().startsWith("org.junit.jupiter")
            );

        if (hasJunit5) {
            return "junit";
        }

        boolean hasTestNG = ktFile.getImportDirectives().stream()
                .anyMatch(imp ->
                imp.getImportedFqName() != null
                    && imp.getImportedFqName().asString().startsWith("org.testng")
            );

        if (hasTestNG) {
            return "testng";
        }

        String text = ktFile.getText();

        if (text.contains("ParameterizedTest")
                || text.contains("RepeatedTest")
                || text.contains("TestFactory")
                || text.contains("DisplayName")) {
            return "junit";
        }

        if (text.contains("DataProvider")
                || text.contains("BeforeMethod")
                || text.contains("AfterMethod")) {
            return "testng";
        }

        return "junit";
    }

    private boolean hasJUnitImports(KtFile ktFile) {
        return ktFile.getImportDirectives().stream()
                .anyMatch(imp ->
                imp.getImportedFqName() != null
                    && imp.getImportedFqName().asString().startsWith("org.junit"));
    }
}
