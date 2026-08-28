package com.ruoyi.bi.datasource;

import com.ruoyi.bi.api.BiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class JdbcUrlFactory {
    private static final Set<String> COMMON = Set.of("databaseType", "schema");
    private static final Set<String> MYSQL = Set.of("useUnicode", "characterEncoding", "serverTimezone");

    @Value("${bi.datasource.ssl-enabled:true}")
    private boolean sslEnabled = true;

    @Value("${bi.datasource.verify-server-certificate:true}")
    private boolean verifyServerCertificate = true;

    public String create(DatasourceRecord source) {
        String host = source.host();
        if (!host.matches("[A-Za-z0-9._:-]+") || host.contains("?") || host.contains("/")) {
            throw invalid("数据源主机格式不合法");
        }
        TreeMap<String, Object> props = new TreeMap<>();
        if (source.connectionProps() != null) props.putAll(source.connectionProps());
        Set<String> allowed = new HashSet<>(COMMON);
        if (source.databaseType() == DatabaseType.MYSQL) allowed.addAll(MYSQL);
        if (!allowed.containsAll(props.keySet())) throw invalid("connectionProps 包含不允许的参数");
        return switch (source.databaseType()) {
            case MYSQL -> mysql(source, props);
            case SQLSERVER -> sqlServer(source);
            case POSTGRESQL -> postgresql(source);
        };
    }

    private String mysql(DatasourceRecord source, TreeMap<String, Object> props) {
        props.keySet().removeAll(COMMON);
        props.put("allowMultiQueries", false);
        props.put("useSSL", sslEnabled);
        props.put("verifyServerCertificate", sslEnabled && verifyServerCertificate);
        props.put("connectTimeout", 5000);
        props.put("socketTimeout", 600000);
        String query = props.entrySet().stream()
            .map(e -> encode(e.getKey()) + "=" + encode(String.valueOf(e.getValue())))
            .reduce((a, b) -> a + "&" + b).orElse("");
        return "jdbc:mysql://" + source.host() + ":" + source.port() + "/" + source.databaseName() + "?" + query;
    }

    private String sqlServer(DatasourceRecord source) {
        return "jdbc:sqlserver://" + source.host() + ":" + source.port()
            + ";databaseName=" + source.databaseName()
            + ";encrypt=" + sslEnabled
            + ";trustServerCertificate=" + !(sslEnabled && verifyServerCertificate)
            + ";loginTimeout=5;socketTimeout=600000";
    }

    private String postgresql(DatasourceRecord source) {
        String sslMode = !sslEnabled ? "disable" : verifyServerCertificate ? "verify-full" : "require";
        String schema = source.schemaName();
        return "jdbc:postgresql://" + source.host() + ":" + source.port() + "/" + source.databaseName()
            + "?connectTimeout=5&socketTimeout=600&sslmode=" + sslMode
            + (schema == null ? "" : "&currentSchema=" + encode(schema));
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static BiException invalid(String message) { return new BiException(HttpStatus.BAD_REQUEST, "BI_REQUEST_INVALID", message); }
}
