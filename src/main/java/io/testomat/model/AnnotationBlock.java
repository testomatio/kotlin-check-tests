package io.testomat.model;

import lombok.Data;

@Data
public class AnnotationBlock {
    private int startLine;
    private int endLine;
    private String text;
    private boolean isTest;

}
