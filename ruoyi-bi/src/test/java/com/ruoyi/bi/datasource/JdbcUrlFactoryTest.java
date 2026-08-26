package com.ruoyi.bi.datasource;

import com.ruoyi.bi.api.BiException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class JdbcUrlFactoryTest {
    private final JdbcUrlFactory factory = new JdbcUrlFactory();

    @Test
    void forcesSecurityPropertiesAndRejectsUnapprovedProperties() {
        DatasourceRecord source = source(Map.of("characterEncoding", "utf8"));
        assertThat(factory.create(source))
            .contains("allowMultiQueries=false", "useSSL=true", "verifyServerCertificate=true")
            .doesNotContain("password");
        assertThatThrownBy(() -> factory.create(source(Map.of("allowMultiQueries", true))))
            .isInstanceOf(BiException.class);
    }

    private DatasourceRecord source(Map<String, Object> props) {
        return new DatasourceRecord(1, "test", "mysql.internal", 3306, "erp", "reader", "cipher",
            props, 1, DatasourceStatus.ENABLED, null, null, null, 0);
    }
}

