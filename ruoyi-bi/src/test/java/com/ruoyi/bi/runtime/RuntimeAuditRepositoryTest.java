package com.ruoyi.bi.runtime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RuntimeAuditRepositoryTest {
    @Test
    void recordsFailedOutcomeAndErrorCode() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RuntimeAuditRepository repository = new RuntimeAuditRepository(jdbc);

        repository.failure(1,"report-uuid","sales",2,3,"sp_sales","request-1","trace-1",45,"BI_QUERY_TIMEOUT");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), arguments.capture());
        assertTrue(sql.getValue().contains("'FAILED'"));
        assertEquals("BI_QUERY_TIMEOUT", arguments.getValue()[9]);
    }
}
