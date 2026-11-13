package com.om.AnomalyDetectionApplication.dto;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Outcome of a CSV upload. {@code errors} lists invalid rows as "Row N: reason" (unchanged format);
 * {@code skippedRows} lists every skipped row, invalid or duplicate, with its reason.
 * Row numbers count data rows, so row 1 is the first line after the header.
 */
public class CsvImportResult {

    private final int importedCount;
    private final int skippedCount;
    private final int duplicateCount;
    private final List<String> errors;
    private final List<SkippedRow> skippedRows;

    public CsvImportResult(int importedCount, List<SkippedRow> invalidRows, List<SkippedRow> duplicateRows) {
        this.importedCount = importedCount;
        this.skippedCount = invalidRows.size() + duplicateRows.size();
        this.duplicateCount = duplicateRows.size();
        this.errors = invalidRows.stream().map(row -> "Row " + row.row() + ": " + row.reason()).toList();
        this.skippedRows = Stream.concat(invalidRows.stream(), duplicateRows.stream())
                .sorted(Comparator.comparingLong(SkippedRow::row))
                .toList();
    }

    public int getImportedCount() {
        return importedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<SkippedRow> getSkippedRows() {
        return skippedRows;
    }

    /** A row that was not imported and why. */
    public record SkippedRow(long row, String reason) {
    }
}
