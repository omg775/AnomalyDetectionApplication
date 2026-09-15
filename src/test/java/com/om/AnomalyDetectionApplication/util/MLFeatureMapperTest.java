package com.om.AnomalyDetectionApplication.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MLFeatureMapperTest {

    @ParameterizedTest
    @CsvSource({
            "LOW, 1",
            "low, 1",
            "MEDIUM, 2",
            "medium, 2",
            "HIGH, 3",
            "high, 3"
    })
    void mapSeverity_knownValues(String input, int expected) {
        assertThat(MLFeatureMapper.mapSeverity(input)).isEqualTo(expected);
    }

    @Test
    void mapSeverity_nullDefaultsToLow() {
        assertThat(MLFeatureMapper.mapSeverity(null)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "UNKNOWN", "CRITICAL"})
    void mapSeverity_unrecognizedDefaultsToLow(String input) {
        assertThat(MLFeatureMapper.mapSeverity(input)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "HIGH_TRAFFIC_VOLUME, 1",
            "high_traffic_volume, 1",
            "SUSPICIOUS_PORT_ACCESS, 2",
            "suspicious_port_access, 2"
    })
    void mapAnomalyType_knownValues(String input, int expected) {
        assertThat(MLFeatureMapper.mapAnomalyType(input)).isEqualTo(expected);
    }

    @Test
    void mapAnomalyType_nullMapsToZero() {
        assertThat(MLFeatureMapper.mapAnomalyType(null)).isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPICIOUS_PROTOCOL", "UNUSUAL_PORT_ACCESS", "SOMETHING_ELSE"})
    void mapAnomalyType_typesNeverProducedByDetectionServiceMapToZero(String input) {
        // Guards against regressing to the old AnomalyAlertService encoding,
        // which used types DetectionService.detectAnomalies() never emits.
        assertThat(MLFeatureMapper.mapAnomalyType(input)).isEqualTo(0);
    }

    @Test
    void hashIpAddress_validIpv4IsStableAndNonNegative() {
        int first = MLFeatureMapper.hashIpAddress("192.168.1.10");
        int second = MLFeatureMapper.hashIpAddress("192.168.1.10");

        assertThat(first).isEqualTo(second);
        assertThat(first).isGreaterThanOrEqualTo(0);
    }

    @Test
    void hashIpAddress_differentIpsHashDifferently() {
        int a = MLFeatureMapper.hashIpAddress("10.0.0.1");
        int b = MLFeatureMapper.hashIpAddress("10.0.0.2");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void hashIpAddress_nullReturnsZero() {
        assertThat(MLFeatureMapper.hashIpAddress(null)).isEqualTo(0);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void hashIpAddress_nullOrEmptyDoesNotThrow(String input) {
        assertThat(MLFeatureMapper.hashIpAddress(input)).isEqualTo(0);
    }

    @Test
    void hashIpAddress_stringWithNoDotsReturnsZero() {
        // Strings with no "." at all (no dotted-quad shape) are treated like null: 0.
        assertThat(MLFeatureMapper.hashIpAddress("not-an-ip-address")).isEqualTo(0);
    }

    @Test
    void hashIpAddress_dotContainingButMalformedFallsBackToHashCode() {
        String malformed = "1.2.3.4.abc";
        assertThat(MLFeatureMapper.hashIpAddress(malformed)).isEqualTo(Math.abs(malformed.hashCode()));
    }

    @Test
    void hashIpAddress_ipv6DoesNotThrow() {
        // Contains no dots, so it's treated as non-IPv4 rather than crashing.
        int result = MLFeatureMapper.hashIpAddress("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        assertThat(result).isGreaterThanOrEqualTo(0);
    }

    @Test
    void hashIpAddress_malformedTooFewOctetsFallsBackGracefully() {
        int result = MLFeatureMapper.hashIpAddress("1.2.3");
        assertThat(result).isGreaterThanOrEqualTo(0);
    }

    @Test
    void hashIpAddress_malformedTooManyOctetsFallsBackGracefully() {
        int result = MLFeatureMapper.hashIpAddress("1.2.3.4.5");
        assertThat(result).isGreaterThanOrEqualTo(0);
    }

    @Test
    void hashIpAddress_octetOutOfRangeFallsBackGracefully() {
        int result = MLFeatureMapper.hashIpAddress("999.1.1.1");
        assertThat(result).isGreaterThanOrEqualTo(0);
    }

    @Test
    void hashIpAddress_nonNumericOctetDoesNotThrow() {
        int result = MLFeatureMapper.hashIpAddress("a.b.c.d");
        assertThat(result).isGreaterThanOrEqualTo(0);
    }
}
