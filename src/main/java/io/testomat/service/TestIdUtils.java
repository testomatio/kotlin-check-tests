package io.testomat.service;

import java.util.List;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtImportDirective;

public final class TestIdUtils {

    public static final String TEST_ID_FQN = "io.testomat.core.annotation.TestId";
    public static final String TEST_ID_SHORT_NAME = "TestId";
    private static final String TEST_ID_WILDCARD_IMPORT = "io.testomat.core.annotation.*";

    private TestIdUtils() {
    }

    public static boolean isTestIdAnnotation(KtAnnotationEntry entry, KtFile ktFile) {
        if (entry.getShortName() == null
                || !TEST_ID_SHORT_NAME.equals(entry.getShortName().getIdentifier())) {
            return false;
        }

        if (entry.getText().contains(TEST_ID_FQN)) {
            return true;
        }

        List<KtImportDirective> imports = ktFile.getImportDirectives();

        if (imports.isEmpty()) {
            return true;
        }

        for (KtImportDirective importDirective : imports) {
            if (importDirective.getImportedFqName() == null) {
                continue;
            }

            String fqName = importDirective.getImportedFqName().asString();

            if (TEST_ID_FQN.equals(fqName) || TEST_ID_WILDCARD_IMPORT.equals(fqName)) {
                return true;
            }
        }

        return false;
    }
}
