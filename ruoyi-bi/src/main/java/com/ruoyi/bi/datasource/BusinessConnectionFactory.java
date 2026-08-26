package com.ruoyi.bi.datasource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Component
public class BusinessConnectionFactory {
    private final CredentialCipher cipher;
    private final JdbcUrlFactory urls;

    @Value("${bi.datasource.enforce-read-only:true}")
    private boolean enforceReadOnly = true;

    public BusinessConnectionFactory(CredentialCipher cipher, JdbcUrlFactory urls) {
        this.cipher = cipher; this.urls = urls;
    }

    public Connection open(DatasourceRecord source) throws SQLException {
        return open(source, enforceReadOnly);
    }

    public Connection open(DatasourceRecord source, boolean readOnly) throws SQLException {
        Connection connection = DriverManager.getConnection(urls.create(source), source.username(), cipher.decrypt(source.passwordCiphertext()));
        // JDBC 只读标志会把 CALL 当成写操作直接拒绝，因此存储过程调用不能走这条路径
        if (readOnly) connection.setReadOnly(true);
        return connection;
    }
}
