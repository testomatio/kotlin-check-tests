package io.testomat.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtFile;

public final class AnnotationScanner {

    private AnnotationScanner() {

    }

    public static List<String> findAnnotationsAbove(KtElement element) {
        KtFile file = element.getContainingKtFile();
        String text = file.getText();

        List<String> lines = List.of(text.split("\n"));
        int methodLine = TextUtils.getLine(element, file);

        List<String> result = new ArrayList<>();

        String currentLine = lines.get(methodLine).trim();

        if (currentLine.startsWith("@")) {
            result.add(currentLine);
        }

        for (int i = methodLine - 1; i >= 0; i--) {
            String line = lines.get(i).trim();

            if (line.startsWith("@")) {
                result.add(line);
            } else if (!line.isEmpty()) {
                break;
            }
        }

        Collections.reverse(result);

        return result;
    }
}
