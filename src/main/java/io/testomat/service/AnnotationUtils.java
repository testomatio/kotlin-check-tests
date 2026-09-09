package io.testomat.service;

import io.testomat.model.AnnotationBlock;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.kotlin.psi.KtNamedFunction;

public class AnnotationUtils {

    public static String extractTitle(String header) {
        int idx = header.indexOf("@Title");
        if (idx == -1) {
            return null;
        }

        int start = header.indexOf("(", idx);
        if (start == -1) {
            return null;
        }

        int i = start + 1;
        int balance = 1;

        StringBuilder content = new StringBuilder();

        while (i < header.length() && balance > 0) {
            char c = header.charAt(i);

            if (c == '(') {
                balance++;
            } else if (c == ')') {
                balance--;
            }

            if (balance > 0) {
                content.append(c);
            }

            i++;
        }

        String inside = content.toString();

        int q1 = inside.indexOf('"');
        int q2 = inside.lastIndexOf('"');

        if (q1 != -1 && q2 != -1 && q2 > q1) {
            return inside.substring(q1 + 1, q2);
        }

        return null;
    }

    public static String findHeaderForMethod(
            KtNamedFunction method,
            List<AnnotationBlock> blocks,
            String[] lines
    ) {
        int methodLine = TextUtils.getLine(method, method.getContainingKtFile());

        for (AnnotationBlock block : blocks) {
            if (block.getEndLine() >= methodLine) {
                continue;
            }
            if (!block.isTest()) {
                continue;
            }

            if (isDirectlyAbove(lines, block.getEndLine(), methodLine)) {
                return block.getText();
            }
        }

        return "";
    }

    public static List<AnnotationBlock> collectAnnotationBlocks(String[] lines) {
        List<AnnotationBlock> blocks = new ArrayList<>();

        StringBuilder current = new StringBuilder();
        int start = -1;
        int balance = 0;
        boolean[] stringState = new boolean[] {false, false};

        for (int i = 0; i < lines.length; i++) {
            String cleaned = AnnotationUtils.stripStringsAndComments(lines[i]).trim();

            if (cleaned.isEmpty()) {
                if (start != -1) {
                    current.append(lines[i]).append("\n");
                }
                continue;
            }

            if (cleaned.startsWith("@")) {
                if (start == -1) {
                    start = i;
                    balance = 0;
                }
                current.append(lines[i]).append("\n");
                balance += parenBalance(cleaned, stringState);
                continue;
            }

            if (start != -1 && balance > 0) {
                current.append(lines[i]).append("\n");
                balance += parenBalance(cleaned, stringState);
                continue;
            }

            if (start != -1) {
                blocks.add(createBlock(current.toString(), start, i - 1));
                current.setLength(0);
                start = -1;
                balance = 0;
                stringState[0] = false;
                stringState[1] = false;
            }
        }

        return blocks;
    }

    private static int parenBalance(String line, boolean[] stringState) {
        boolean inString = stringState[0];
        boolean inTextBlock = stringState[1];
        int balance = 0;
        int i = 0;

        while (i < line.length()) {
            char c = line.charAt(i);

            if (inTextBlock) {
                if (c == '"' && isTripleQuote(line, i)) {
                    inTextBlock = false;
                    i += 3;
                } else {
                    i++;
                }
                continue;
            }

            if (c == '"') {
                if (isTripleQuote(line, i)) {
                    inTextBlock = true;
                    i += 3;
                } else {
                    inString = !inString;
                    i++;
                }
                continue;
            }

            if (!inString) {
                if (c == '(') {
                    balance++;
                } else if (c == ')') {
                    balance--;
                }
            }

            i++;
        }

        stringState[0] = inString;
        stringState[1] = inTextBlock;
        return balance;
    }

    private static boolean isTripleQuote(String line, int index) {
        return index + 2 < line.length()
                && line.charAt(index + 1) == '"'
                && line.charAt(index + 2) == '"';
    }

    private static boolean isDirectlyAbove(String[] lines, int from, int to) {
        for (int i = from + 1; i < to; i++) {
            String cleaned = stripStringsAndComments(lines[i]).trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            return false;
        }
        return true;
    }

    public static String stripStringsAndComments(String line) {
        int comment = line.indexOf("//");
        if (comment != -1) {
            line = line.substring(0, comment);
        }
        return line;
    }

    private static AnnotationBlock createBlock(String text, int start, int end) {
        AnnotationBlock block = new AnnotationBlock();
        block.setText(text);
        block.setStartLine(start);
        block.setEndLine(end);

        block.setTest(text.contains("@Test")
                || text.contains("@ParameterizedTest")
                || text.contains("@RepeatedTest")
                || text.contains("@TestFactory"));

        return block;
    }

}
