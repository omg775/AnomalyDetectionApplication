package com.om.AnomalyDetectionApplication.exception;

/** Thrown when the FastAPI ML service is unreachable, errors out, or has no trained model yet. */
public class MlServiceException extends RuntimeException {

    public MlServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
