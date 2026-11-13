package com.om.AnomalyDetectionApplication.util;

import java.util.regex.Pattern;

/**
 * Syntax checks for textual IP addresses. Deliberately does not use {@code InetAddress.getByName},
 * which falls back to a DNS lookup for input that is not a literal address.
 */
public final class IpAddresses {

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern HEX_GROUP = Pattern.compile("^[0-9a-fA-F]{1,4}$");
    private static final int IPV6_GROUPS = 8;

    private IpAddresses() {
    }

    public static boolean isValid(String address) {
        return isIpv4(address) || isIpv6(address);
    }

    /** Dotted-quad IPv4 without leading zeros, for example {@code 192.168.0.1}. */
    public static boolean isIpv4(String address) {
        return address != null && IPV4.matcher(address).matches();
    }

    /**
     * IPv6 in full, compressed ({@code ::}) or IPv4-embedded form, for example {@code 2001:db8::1}
     * or {@code ::ffff:192.0.2.1}. Zone ids ({@code %eth0}) and brackets are rejected.
     */
    public static boolean isIpv6(String address) {
        if (address == null || address.isEmpty() || !address.contains(":")) {
            return false;
        }
        int compression = address.indexOf("::");
        if (compression < 0) {
            return countGroups(split(address), true) == IPV6_GROUPS;
        }
        if (compression != address.lastIndexOf("::")) {
            return false;
        }
        int head = countGroups(split(address.substring(0, compression)), false);
        int tail = countGroups(split(address.substring(compression + 2)), true);
        return head >= 0 && tail >= 0 && head + tail < IPV6_GROUPS;
    }

    private static String[] split(String groups) {
        return groups.isEmpty() ? new String[0] : groups.split(":", -1);
    }

    /** Returns the number of 16-bit groups, or -1 when a group is malformed. */
    private static int countGroups(String[] groups, boolean mayEndWithIpv4) {
        int count = 0;
        for (int i = 0; i < groups.length; i++) {
            String group = groups[i];
            boolean last = i == groups.length - 1;
            if (last && mayEndWithIpv4 && group.contains(".")) {
                if (!isIpv4(group)) {
                    return -1;
                }
                count += 2;
            } else if (HEX_GROUP.matcher(group).matches()) {
                count++;
            } else {
                return -1;
            }
        }
        return count;
    }
}
