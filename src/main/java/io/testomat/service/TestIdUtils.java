package io.testomat.service;

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

        boolean hasTestomatImport = false;
        boolean hasForeignTestIdImport = false;

        for (KtImportDirective importDirective : ktFile.getImportDirectives()) {
            if (importDirective.getImportedFqName() == null) {
                continue;
            }

            String fqName = importDirective.getImportedFqName().asString();

            if (TEST_ID_FQN.equals(fqName) || TEST_ID_WILDCARD_IMPORT.equals(fqName)) {
                hasTestomatImport = true;
            } else if (fqName.endsWith(".TestId") || fqName.equals(TEST_ID_SHORT_NAME)) {
                hasForeignTestIdImport = true;
            }
        }

        return hasTestomatImport || !hasForeignTestIdImport;
    }
}
