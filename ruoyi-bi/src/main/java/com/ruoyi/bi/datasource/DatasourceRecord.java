package com.ruoyi.bi.datasource;

import java.time.LocalDateTime;
import java.util.Map;

public record DatasourceRecord(long id, String datasourceName, String host, int port, String databaseName,
                               String username, String passwordCiphertext, Map<String, Object> connectionProps,
                               int credentialVersion, DatasourceStatus status, String remark,
                               LocalDateTime createdAt, LocalDateTime updatedAt, long rowVersion) {}

