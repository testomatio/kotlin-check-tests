package io.testomat.service;

import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtFile;

public final class TextUtils {

    private TextUtils() {

    }

    public static int getLineByOffset(String text, int offset) {
        int line = 0;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    public static int getLine(KtElement element, KtFile file) {
        return getLineByOffset(file.getText(), element.getTextOffset());
    }

    public static String extractAnnotationName(String line) {
        if (line == null) {
            return null;
        }

        line = line.trim();

        if (!line.startsWith("@")) {
            return null;
        }

        line = line.substring(1);

        int idx = line.indexOf("(");
        if (idx != -1) {
            line = line.substring(0, idx);
        }

        int dotIndex = line.lastIndexOf(".");
        if (dotIndex != -1) {
            line = line.substring(dotIndex + 1);
        }

        return line.trim();
    }
}
