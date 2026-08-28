package com.ruoyi.bi.datasource;

import com.ruoyi.bi.api.BiException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class JdbcUrlFactoryTest {
    private final JdbcUrlFactory factory = new JdbcUrlFactory();

    @Test
    void forcesSecurityPropertiesAndRejectsUnapprovedProperties() {
        DatasourceRecord source = source(3306, Map.of("characterEncoding", "utf8"));
        assertThat(factory.create(source))
            .contains("allowMultiQueries=false", "useSSL=true", "verifyServerCertificate=true")
            .doesNotContain("password");
        assertThatThrownBy(() -> factory.create(source(3306, Map.of("allowMultiQueries", true))))
            .isInstanceOf(BiException.class);
    }

    @Test
    void createsSqlServerUrlWithoutChangingMetadataDatabase() {
        String url = factory.create(source(1433, Map.of("databaseType", "SQLSERVER", "schema", "dbo")));
        assertThat(url).startsWith("jdbc:sqlserver://mysql.internal:1433;databaseName=erp")
            .contains("encrypt=true", "trustServerCertificate=false")
            .doesNotContain("schema=dbo");
    }

    @Test
    void createsPostgresqlUrlWithSchema() {
        String url = factory.create(source(5432, Map.of("databaseType", "POSTGRESQL", "schema", "reporting")));
        assertThat(url).startsWith("jdbc:postgresql://mysql.internal:5432/erp?")
            .contains("sslmode=verify-full", "currentSchema=reporting");
    }

    private DatasourceRecord source(int port, Map<String, Object> props) {
        return new DatasourceRecord(1, "test", "mysql.internal", port, "erp", "reader", "cipher",
            props, 1, DatasourceStatus.ENABLED, null, null, null, 0);
    }
}
