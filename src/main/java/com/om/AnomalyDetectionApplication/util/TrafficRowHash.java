package com.om.AnomalyDetectionApplication.util;

import com.om.AnomalyDetectionApplication.entity.TrafficRecord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Fingerprint of a traffic record's content, used to skip exact duplicate rows on import.
 * The canonical form must stay identical to the SQL in
 * {@code db/migration/postgresql/V3_1__backfill_traffic_row_hash.sql}.
 */
public final class TrafficRowHash {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS");

    private TrafficRowHash() {
    }

    /** SHA-256 (lowercase hex) of timestamp|source_ip|destination_ip|protocol|destination_port|bytes. */
    public static String of(TrafficRecord record) {
        String canonical = String.join("|",
                TIMESTAMP.format(record.getTimestamp()),
                record.getSourceIp(),
                record.getDestinationIp() == null ? "" : record.getDestinationIp(),
                record.getProtocol(),
                String.valueOf(record.getDestinationPort()),
                String.valueOf(record.getBytesTransferred()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
