package com.om.AnomalyDetectionApplication.dto;

import java.util.List;

public class CsvImportResult {

    private final int importedCount;
    private final int skippedCount;
    private final List<String> errors;

    public CsvImportResult(int importedCount, int skippedCount, List<String> errors) {
        this.importedCount = importedCount;
        this.skippedCount = skippedCount;
        this.errors = errors;
    }

    public int getImportedCount() {
        return importedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public List<String> getErrors() {
        return errors;
    }
}
