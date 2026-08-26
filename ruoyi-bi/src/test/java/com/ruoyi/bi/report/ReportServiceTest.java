package com.ruoyi.bi.report;

import com.ruoyi.bi.api.BiException;
import com.ruoyi.bi.datasource.DatasourceDtos;
import com.ruoyi.bi.datasource.DatasourceService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceTest {
    private final ReportRepository repository = mock(ReportRepository.class);
    private final ReportAccess access = mock(ReportAccess.class);
    private final DatasourceService datasources = mock(DatasourceService.class);
    private final ReportService service = new ReportService(repository, access, datasources, new ReportConfigValidator());

    @Test
    void rejectsUnsupportedProcedureWhenCreating() {
        var request = new ReportDtos.CreateRequest("销售分析", null, 1001, "sp_sales", 50_000, 60);
        when(datasources.parameters(1001, "sp_sales")).thenReturn(new DatasourceDtos.ProcedureMetadata(
            "sp_sales", "hash", false, List.of(), List.of("仅支持 IN 参数: p_out")));

        BiException error = assertThrows(BiException.class, () -> service.create(request, 9));

        assertEquals("BI_UNSUPPORTED_SIGNATURE", error.code());
    }

    @Test
    void refusesActivationUntilAllParametersAreMapped() {
        var request = new ReportDtos.StatusRequest(ReportStatus.ENABLED, 1);
        when(repository.activationInfo(22)).thenReturn(new ReportRepository.ActivationInfo(1001, "sp_sales", "hash"));
        when(datasources.parameters(1001, "sp_sales")).thenReturn(new DatasourceDtos.ProcedureMetadata(
            "sp_sales", "hash", true,
            List.of(new DatasourceDtos.ProcedureParameter(1, "p_user_id", "IN", "bigint", "bigint")), List.of()));
        when(repository.defaultMappingCount(22)).thenReturn(0);

        BiException error = assertThrows(BiException.class, () -> service.changeStatus(22, request, 9));

        assertEquals("BI_CONFIG_INVALID", error.code());
        verify(access).requireReadable(22, repository);
    }
}
