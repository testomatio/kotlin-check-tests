package io.testomat.service;

import java.nio.file.Paths;

public class PathUtils {

    public static String extractRelativeFilePath(String filepath) {
        if (filepath == null || filepath.isEmpty()) {
            return "UnknownFile.kt";
        }

        String normalized = Paths.get(filepath)
                .normalize()
                .toString()
                .replace('\\', '/');

        if (normalized.matches("^[A-Za-z]:/.*")) {
            normalized = normalized.substring(3);
        }

        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (normalized.contains("src/test/kotlin/")) {
            return normalized.substring(
                normalized.indexOf("src/test/kotlin/") + "src/test/kotlin/".length()
            );
        }

        if (normalized.contains("src/main/kotlin/")) {
            return normalized.substring(
                normalized.indexOf("src/main/kotlin/") + "src/main/kotlin/".length()
            );
        }

        if (normalized.contains("src/") && normalized.contains("/kotlin/")) {
            int idx = normalized.lastIndexOf("/kotlin/");
            return normalized.substring(idx + "/kotlin/".length());
        }

        return normalized;
    }
}
