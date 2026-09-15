package com.om.AnomalyDetectionApplication.util;

/**
 * Canonical label encoding for ML features, shared by every code path that
 * builds {@code MLDataDTO} records (training and prediction). The anomaly
 * type encoding must stay in sync with the types DetectionService.detectAnomalies()
 * actually produces: HIGH_TRAFFIC_VOLUME and SUSPICIOUS_PORT_ACCESS.
 */
public final class MLFeatureMapper {

    private MLFeatureMapper() {
    }

    public static int mapSeverity(String severity) {
        if (severity == null) {
            return 1;
        }
        return switch (severity.toUpperCase()) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            default -> 1;
        };
    }

    public static int mapAnomalyType(String anomalyType) {
        if (anomalyType == null) {
            return 0;
        }
        return switch (anomalyType.toUpperCase()) {
            case "HIGH_TRAFFIC_VOLUME" -> 1;
            case "SUSPICIOUS_PORT_ACCESS" -> 2;
            default -> 0;
        };
    }

    /**
     * Hashes an IPv4 address into a stable non-negative int. Falls back to
     * String#hashCode() for null, non-IPv4, or otherwise malformed input.
     */
    public static int hashIpAddress(String ipAddress) {
        if (ipAddress == null || !ipAddress.contains(".")) {
            return 0;
        }
        try {
            String[] parts = ipAddress.split("\\.");
            if (parts.length != 4) {
                return Math.abs(ipAddress.hashCode());
            }
            long result = 0;
            for (int i = 0; i < parts.length; i++) {
                int octet = Integer.parseInt(parts[i]);
                if (octet < 0 || octet > 255) {
                    return Math.abs(ipAddress.hashCode());
                }
                result += (long) octet * (long) Math.pow(256, 3 - i);
            }
            return (int) (result % Integer.MAX_VALUE);
        } catch (Exception e) {
            return Math.abs(ipAddress.hashCode());
        }
    }
}
