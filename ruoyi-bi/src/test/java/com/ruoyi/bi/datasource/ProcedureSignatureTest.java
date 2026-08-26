package com.ruoyi.bi.datasource;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcedureSignatureTest {
    @Test
    void signatureIsStableButSensitiveToOrdinalAndType() {
        var first = List.of(
            new DatasourceDtos.ProcedureParameter(1, "p_user_id", "IN", "varchar", "varchar(64)"),
            new DatasourceDtos.ProcedureParameter(2, "p_region_key", "IN", "varchar", "varchar(16)"));
        var same = List.copyOf(first);
        var renamed = List.of(
            new DatasourceDtos.ProcedureParameter(1, "P_USER_ID", "IN", "varchar", "varchar(64)"),
            new DatasourceDtos.ProcedureParameter(2, "p_region_key", "IN", "varchar", "varchar(16)"));
        var changed = List.of(
            new DatasourceDtos.ProcedureParameter(1, "p_user_id", "IN", "varchar", "varchar(64)"),
            new DatasourceDtos.ProcedureParameter(2, "p_region_key", "IN", "varchar", "varchar(32)"));
        assertThat(ProcedureSignature.sha256(first)).isEqualTo(ProcedureSignature.sha256(same));
        assertThat(ProcedureSignature.sha256(first)).isNotEqualTo(ProcedureSignature.sha256(renamed));
        assertThat(ProcedureSignature.sha256(first)).isNotEqualTo(ProcedureSignature.sha256(changed));
        assertThat(ProcedureSignature.sha256(first)).hasSize(64);
    }
}
