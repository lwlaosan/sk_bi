package com.ruoyi.bi.datasource;

import com.ruoyi.bi.config.BiProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BusinessConnectionFactory {
    private final CredentialCipher cipher;
    private final JdbcUrlFactory urls;
    private final BiProperties properties;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<Long, PoolEntry> pools = new ConcurrentHashMap<>();

    @Value("${bi.datasource.enforce-read-only:true}")
    private boolean enforceReadOnly = true;

    public BusinessConnectionFactory(CredentialCipher cipher, JdbcUrlFactory urls, BiProperties properties, MeterRegistry meterRegistry) {
        this.cipher = cipher; this.urls = urls; this.properties = properties; this.meterRegistry = meterRegistry;
    }

    public Connection open(DatasourceRecord source) throws SQLException {
        return open(source, enforceReadOnly);
    }

    public Connection open(DatasourceRecord source, boolean readOnly) throws SQLException {
        PoolEntry entry = pools.compute(source.id(), (id, current) -> {
            if (current != null && current.rowVersion == source.rowVersion()) return current;
            if (current != null) current.close();
            return new PoolEntry(source.rowVersion(), createPool(source));
        });
        Connection connection = entry.dataSource.getConnection();
        // JDBC 只读标志会把 CALL 当成写操作直接拒绝，因此存储过程调用不能走这条路径
        try {
            connection.setReadOnly(readOnly);
            return connection;
        } catch (SQLException ex) {
            try { connection.close(); } catch (SQLException closeFailure) { ex.addSuppressed(closeFailure); }
            throw ex;
        }
    }

    private HikariDataSource createPool(DatasourceRecord source) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("bi-datasource-" + source.id());
        config.setJdbcUrl(urls.create(source));
        config.setUsername(source.username());
        config.setPassword(cipher.decrypt(source.passwordCiphertext()));
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(Math.max(1, properties.query().perDatasourceConcurrency()));
        long connectionTimeout = Math.max(250, properties.datasource().connectionTimeoutMs());
        long validationTimeout = Math.max(250, Math.min(properties.datasource().validationTimeoutMs(), connectionTimeout));
        config.setConnectionTimeout(connectionTimeout);
        config.setValidationTimeout(validationTimeout);
        config.setIdleTimeout(300_000);
        config.setMaxLifetime(900_000);
        config.setInitializationFailTimeout(-1);
        config.setMetricRegistry(meterRegistry);
        return new HikariDataSource(config);
    }

    @PreDestroy
    void closePools() {
        pools.values().forEach(PoolEntry::close);
        pools.clear();
    }

    void invalidate(long datasourceId) {
        PoolEntry removed = pools.remove(datasourceId);
        if (removed != null) removed.close();
    }

    private static final class PoolEntry {
        private final long rowVersion;
        private final HikariDataSource dataSource;
        private PoolEntry(long rowVersion, HikariDataSource dataSource) {
            this.rowVersion = rowVersion; this.dataSource = dataSource;
        }
        private void close() { dataSource.close(); }
    }
}
