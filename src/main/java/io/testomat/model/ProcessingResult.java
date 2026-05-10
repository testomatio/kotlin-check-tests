package io.testomat.model;

import java.util.List;

public record ProcessingResult(List<TestCase> allTestCases, String primaryFramework) {

}
