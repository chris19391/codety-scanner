package io.codety.scanner.analyzer.eslint.dto;

public class EslintResults {
    EslintResult[] results;

    public EslintResult[] getResults() {
        return results;
    }

    public void setResults(EslintResult[] results) {
        this.results = results;
    }
}