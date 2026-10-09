package com.ruoyi.bi.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.bi.api.BiException;
import com.ruoyi.bi.report.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportPreviewTest {
    private final String uuid = "f6bf8f26-76f8-456f-baa7-3cab7fe1dbf3";
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReportRepository reports = mock(ReportRepository.class);
    private final ReportAccess access = mock(ReportAccess.class);
    private final ReportRuntimeCache cache = mock(ReportRuntimeCache.class);
    private final RuntimeReportService service = new RuntimeReportService(reports, access, cache,
        null, null, null, null, mapper, null, null, null);

    @Test
    void disabledReportIsAvailableOnlyInPreviewAndStillChecksAcl() throws Exception {
        var head = new ReportRepository.RuntimeHead(22, uuid, "销售分析", ReportStatus.DISABLED, 2, 50000, 60);
        when(reports.runtimeHead(uuid)).thenReturn(head);
        when(cache.get(reports, head)).thenReturn((com.fasterxml.jackson.databind.node.ObjectNode)
            mapper.readTree("{\"controls\":[],\"components\":[],\"insight\":{\"enabled\":true}}"));

        assertEquals("BI_REPORT_NOT_FOUND", assertThrows(BiException.class, () -> service.configuration(uuid)).code());
        var config = service.configuration(uuid, true);
        assertEquals("销售分析", config.path("name").asText());
        assertFalse(config.path("insight").path("enabled").asBoolean());
        verify(access, times(2)).requireReadable(22, reports);
        verify(reports, never()).changeStatus(anyLong(), any(), anyLong());
    }

    @Test
    void previewCannotBypassReportAcl() {
        var head = new ReportRepository.RuntimeHead(22, uuid, "销售分析", ReportStatus.DISABLED, 2, 50000, 60);
        when(reports.runtimeHead(uuid)).thenReturn(head);
        doThrow(new BiException(org.springframework.http.HttpStatus.NOT_FOUND, "DENIED", "报表不存在"))
            .when(access).requireReadable(22, reports);
        assertThrows(BiException.class, () -> service.configuration(uuid, true));
        verifyNoInteractions(cache);
    }

    @Test
    void previewEndpointsRequireDesignPermission() {
        assertEquals("@ss.hasPermi('bi:report:design')",
            ReportPreviewController.class.getAnnotation(PreAuthorize.class).value());
    }
}
