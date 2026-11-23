package com.om.AnomalyDetectionApplication.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IpAddressesTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "10.0.0.1", "192.168.1.255", "255.255.255.255", "8.8.8.8"})
    void acceptsValidIpv4(String address) {
        assertThat(IpAddresses.isValid(address)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"256.0.0.1", "1.2.3", "1.2.3.4.5", "01.2.3.4", "1.2.3.-4", "1..2.3", "a.b.c.d",
            "1.2.3.4 ", "", "localhost", "deadbeef"})
    void rejectsInvalidIpv4(String address) {
        assertThat(IpAddresses.isValid(address)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2001:0db8:85a3:0000:0000:8a2e:0370:7334", "2001:db8:85a3::8a2e:370:7334", "::1", "::",
            "fe80::", "::ffff:192.0.2.128", "64:ff9b::192.0.2.33", "1:2:3:4:5:6:7::", "::2:3:4:5:6:7:8",
            "1:2:3:4:5:6:192.0.2.1", "ABCD:EF01:2345:6789:ABCD:EF01:2345:6789"})
    void acceptsValidIpv6(String address) {
        assertThat(IpAddresses.isValid(address)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2001:db8::85a3::7334", "1:2:3:4:5:6:7:8:9", "1:2:3:4:5:6:7", "12345::1", "g::1",
            ":1:2:3:4:5:6:7", "1:2:3:4:5:6:7:", "fe80::1%eth0", "[::1]", "::ffff:999.0.2.128",
            "1:2:3:4:5:6:7:192.0.2.1", "::1.2.3.4:5", ":"})
    void rejectsInvalidIpv6(String address) {
        assertThat(IpAddresses.isValid(address)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void rejectsNull() {
        assertThat(IpAddresses.isValid(null)).isFalse();
    }
}
