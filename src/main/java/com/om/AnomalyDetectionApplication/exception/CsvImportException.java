package com.om.AnomalyDetectionApplication.exception;

/** Thrown when a CSV upload cannot be read at all (as opposed to individual malformed rows, which are skipped and reported). */
public class CsvImportException extends RuntimeException {

    public CsvImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
