package com.om.AnomalyDetectionApplication.service;

import com.om.AnomalyDetectionApplication.dto.CsvImportResult;
import com.om.AnomalyDetectionApplication.exception.CsvImportException;
import com.om.AnomalyDetectionApplication.repository.TrafficRecordRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {

    private static final String HEADER = "timestamp,source_ip,destination_ip,protocol,destination_port,bytes_transferred\n";

    @Mock
    private TrafficRecordRepository repository;

    @Mock
    private EntityManager entityManager;

    private CsvImportService service(int maxRows, int batchSize) {
        return new CsvImportService(repository, entityManager, maxRows, batchSize, List.of("tcp", " UDP "));
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "t.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private static String rows(int count) {
        StringBuilder csv = new StringBuilder(HEADER);
        for (int i = 0; i < count; i++) {
            csv.append("2026-01-01T10:00:00,10.0.0.1,10.0.0.2,TCP,443,").append(i).append('\n');
        }
        return csv.toString();
    }

    @Test
    void moreRowsThanTheMaximum_isRejectedBeforeAnythingIsSaved() {
        assertThatThrownBy(() -> service(3, 10).importCsv(csv(rows(4))))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("maximum of 3 data rows");
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void exactlyTheMaximum_isAccepted() {
        when(repository.findExistingRowHashes(anyCollection())).thenReturn(Set.of());

        CsvImportResult result = service(3, 10).importCsv(csv(rows(3)));

        assertThat(result.getImportedCount()).isEqualTo(3);
    }

    @Test
    void rowsAreSavedInBatches_andThePersistenceContextIsCleared() {
        when(repository.findExistingRowHashes(anyCollection())).thenReturn(Set.of());

        service(100, 2).importCsv(csv(rows(5)));

        verify(repository, times(3)).saveAll(anyList());
        verify(entityManager, times(3)).clear();
    }

    @Test
    void protocolListIsTrimmedAndCaseInsensitive() {
        when(repository.findExistingRowHashes(anyCollection())).thenReturn(Set.of());

        CsvImportResult result = service(10, 10).importCsv(csv(HEADER
                + "2026-01-01T10:00:00,10.0.0.1,10.0.0.2,udp,53,10\n"
                + "2026-01-01T10:00:00,10.0.0.1,10.0.0.2,ICMP,0,10\n"));

        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getSkippedRows()).singleElement()
                .satisfies(row -> assertThat(row.reason()).contains("protocol 'ICMP' is not one of TCP, UDP"));
    }

    @Test
    void headerOnly_importsNothing() {
        CsvImportResult result = service(10, 10).importCsv(csv(HEADER));

        assertThat(result.getImportedCount()).isZero();
        assertThat(result.getSkippedCount()).isZero();
    }

    @Test
    void missingColumns_areAllListed() {
        assertThatThrownBy(() -> service(10, 10).importCsv(csv("timestamp,source_ip\n2026-01-01T10:00:00,10.0.0.1\n")))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("destination_ip, protocol, destination_port, bytes_transferred");
    }
}
