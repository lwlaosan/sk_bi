package com.ruoyi.bi.datasource;

import com.ruoyi.bi.api.BiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Service
public class DatasourceService {
    private final DatasourceRepository repository;
    private final CredentialCipher cipher;
    private final BusinessConnectionFactory connections;
    private final ProcedureMetadataService metadata;
    private final DatasourceAccess access;

    public DatasourceService(DatasourceRepository repository, CredentialCipher cipher,
                             BusinessConnectionFactory connections, ProcedureMetadataService metadata,
                             DatasourceAccess access) {
        this.repository = repository; this.cipher = cipher; this.connections = connections; this.metadata = metadata;
        this.access = access;
    }

    public DatasourceDtos.Page page(int page, int pageSize, String keyword, DatasourceStatus status) {
        int safePage = Math.max(1, page); int safeSize = Math.min(100, Math.max(1, pageSize));
        boolean admin = access.isAdmin();
        long userId = access.userId();
        List<Long> roleIds = access.roleIds();
        List<DatasourceDtos.View> items = repository.findPage((safePage - 1) * safeSize, safeSize, keyword, status,
                admin, userId, roleIds)
            .stream().map(this::view).toList();
        return new DatasourceDtos.Page(items, safePage, safeSize,
                repository.count(keyword, status, admin, userId, roleIds));
    }

    public DatasourceDtos.View get(long id) { return view(requireReadable(id)); }

    public DatasourceDtos.View create(DatasourceDtos.SaveRequest request, long userId) {
        access.requireAdmin();
        if (request.password() == null || request.password().isBlank()) throw invalid("新建数据源必须提供密码");
        long id = repository.create(request, cipher.encrypt(request.password()), userId);
        return get(id);
    }

    public DatasourceDtos.View update(long id, DatasourceDtos.SaveRequest request, long userId) {
        access.requireAdmin();
        require(id);
        boolean changed = request.password() != null && !request.password().isBlank();
        repository.update(id, request, changed ? cipher.encrypt(request.password()) : null, changed, userId);
        return get(id);
    }

    public DatasourceDtos.ConnectionTest test(long id) {
        access.requireAdmin();
        DatasourceRecord source = require(id); long started = System.nanoTime();
        try (Connection connection = connections.open(source)) {
            boolean valid = connection.isValid(10);
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            if (!valid) return new DatasourceDtos.ConnectionTest(false, elapsed, "VALIDATION_FAILED", List.of());
            return new DatasourceDtos.ConnectionTest(true, elapsed, "OK", List.of("请确认账号仅拥有元数据读取、指定过程 EXECUTE 和必要只读权限"));
        } catch (SQLException | IllegalStateException ex) {
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            // 主密钥缺失/解密失败时把原因带回前端，避免被笼统归类成“连接失败”
            List<String> hints = ex instanceof IllegalStateException && ex.getMessage() != null
                ? List.of(ex.getMessage()) : List.of();
            return new DatasourceDtos.ConnectionTest(false, elapsed, classify(ex), hints);
        }
    }

    public List<DatasourceDtos.ProcedureSummary> procedures(long id, String keyword) { return metadata.list(requireEnabled(id), keyword); }
    public DatasourceDtos.ProcedureMetadata parameters(long id, String name) { return metadata.parameters(requireEnabled(id), name); }

    /** 仅供已完成报表 ACL 校验的运行引擎使用。CALL 存储过程不能使用 JDBC 只读连接。 */
    public Connection openForRuntime(long id) throws SQLException {
        return connections.open(requireEnabledInternal(id), false);
    }
    public DatasourceDtos.ProcedureMetadata parametersForRuntime(long id, String name) {
        return metadata.parameters(requireEnabledInternal(id), name);
    }

    private DatasourceRecord requireEnabledInternal(long id) {
        DatasourceRecord source = require(id);
        if (source.status() != DatasourceStatus.ENABLED) throw new BiException(HttpStatus.SERVICE_UNAVAILABLE,
            "BI_DATASOURCE_UNAVAILABLE", "数据源不可用");
        return source;
    }

    private DatasourceRecord requireEnabled(long id) {
        DatasourceRecord source = requireReadable(id);
        if (source.status() != DatasourceStatus.ENABLED) throw new BiException(HttpStatus.SERVICE_UNAVAILABLE, "BI_DATASOURCE_UNAVAILABLE", "数据源已停用");
        return source;
    }

    private DatasourceRecord require(long id) {
        return repository.find(id).orElseThrow(() -> new BiException(HttpStatus.NOT_FOUND, "BI_REQUEST_INVALID", "数据源不存在"));
    }

    private DatasourceRecord requireReadable(long id) {
        access.requireReadable(id, repository);
        return require(id);
    }

    private DatasourceDtos.View view(DatasourceRecord source) {
        Map<SubjectType, List<Long>> acl = repository.acl(source.id());
        boolean admin = access.isAdmin();
        return new DatasourceDtos.View(String.valueOf(source.id()), source.datasourceName(), source.databaseType(),
            admin ? source.host() : "******", admin ? source.port() : 0,
            admin ? source.databaseName() : "******", admin ? source.username() : "******",
            admin && source.passwordCiphertext() != null && !source.passwordCiphertext().isBlank(),
            admin ? source.connectionProps() : Map.of(), source.credentialVersion(), source.status(), admin ? source.remark() : null,
            admin ? acl.get(SubjectType.ROLE).stream().map(String::valueOf).toList() : List.of(),
            admin ? acl.get(SubjectType.USER).stream().map(String::valueOf).toList() : List.of(),
            source.createdAt(), source.updatedAt(), source.rowVersion());
    }

    private static String classify(Exception ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        String lower = message.toLowerCase();
        if (ex instanceof IllegalStateException) {
            if (message.contains("未配置") || lower.contains("master_key")) return "MASTER_KEY_MISSING";
            if (message.contains("无法解密")) return "DECRYPT_FAILED";
            return "CREDENTIAL_ERROR";
        }
        if (lower.contains("access denied")) return "AUTHENTICATION_FAILED";
        if (lower.contains("timeout") || lower.contains("timed out")) return "TIMEOUT";
        return "CONNECTION_FAILED";
    }

    private static BiException invalid(String message) { return new BiException(HttpStatus.BAD_REQUEST, "BI_REQUEST_INVALID", message); }
}
