package io.testomat.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtModifierListOwner;

public final class AnnotationScanner {

    private AnnotationScanner() {

    }

    public static List<String> findAnnotationsAbove(KtElement element) {
        KtFile file = element.getContainingKtFile();

        if (file == null) {
            return Collections.emptyList();
        }

        String text = file.getText();
        int elementLine = TextUtils.getLine(element, file);
        String[] lines = text.split("\n");

        if (elementLine < lines.length && lines[elementLine].trim().startsWith("@")) {
            List<String> result = new ArrayList<>();
            result.add(lines[elementLine].trim());
            return result;
        }

        if (element instanceof KtModifierListOwner) {
            return ((KtModifierListOwner) element).getAnnotationEntries().stream()
                    .map(entry -> entry.getText().trim())
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
