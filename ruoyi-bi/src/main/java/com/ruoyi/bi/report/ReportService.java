package com.ruoyi.bi.report;

import com.ruoyi.bi.api.BiException;
import com.ruoyi.bi.datasource.DatasourceDtos;
import com.ruoyi.bi.datasource.DatasourceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.bi.datasource.DatasourceDtos.ProcedureMetadata;
import java.util.Map;
import com.ruoyi.common.utils.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ReportService {
    private final ReportRepository repository;
    private final ReportAccess access;
    private final DatasourceService datasources;
    private final ReportConfigValidator validator;
    @Autowired(required = false)
    private ReportRuntimeCache runtimeCache;

    public ReportService(ReportRepository repository, ReportAccess access, DatasourceService datasources,
                         ReportConfigValidator validator) {
        this.repository = repository; this.access = access; this.datasources = datasources; this.validator = validator;
    }

    public ReportDtos.Page page(int page, int pageSize, String keyword, ReportStatus status, Long createdBy) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        boolean admin = access.isAdmin();
        long userId = access.userId();
        List<Long> roleIds = access.roleIds();
        var items = repository.findPage((safePage - 1) * safeSize, safeSize, keyword, status, createdBy,
            admin, userId, roleIds);
        return new ReportDtos.Page(items, safePage, safeSize,
            repository.count(keyword, status, createdBy, admin, userId, roleIds));
    }

    public ReportDtos.Created create(ReportDtos.CreateRequest request, long userId) {
        DatasourceDtos.ProcedureMetadata metadata = datasources.parameters(
            request.defaultDatasourceId(), request.defaultProcedureName());
        if (!metadata.supported()) {
            throw new BiException(HttpStatus.BAD_REQUEST, "BI_UNSUPPORTED_SIGNATURE",
                "存储过程签名不受支持: " + String.join("；", metadata.unsupportedReasons()));
        }
        return repository.create(request, metadata, userId);
    }

    public void changeStatus(long reportId, ReportDtos.StatusRequest request, long userId) {
        access.requireReadable(reportId, repository);
        if (request.status() == ReportStatus.ENABLED) validateActivation(reportId);
        repository.changeStatus(reportId, request, userId);
        invalidate(repository.uuid(reportId));
    }

    public void delete(long reportId, long userId) {
        access.requireReadable(reportId, repository);
        String uuid=repository.uuid(reportId); repository.delete(reportId, userId); invalidate(uuid);
    }
    public ReportDtos.Created copy(long reportId,long userId){access.requireReadable(reportId,repository);return repository.copy(reportId,userId);}

    public ObjectNode configuration(long reportId) {
        access.requireReadable(reportId, repository);
        return repository.configuration(reportId);
    }

    public ReportDtos.ValidationResult validate(long reportId, ObjectNode config) {
        access.requireReadable(reportId, repository);
        return validateConfig(reportId, config, false);
    }

    public ReportDtos.Saved saveConfiguration(long reportId, ObjectNode config, long userId) {
        access.requireReadable(reportId, repository);
        ReportDtos.ValidationResult validation = validateConfig(reportId, config, true);
        if (!validation.valid()) {
            throw new BiException(HttpStatus.BAD_REQUEST, "BI_CONFIG_INVALID", "报表配置校验失败",
                Map.of("errors", validation.errors()));
        }
        long expected = config.path("expectedVersion").asLong(-1);
        if (expected < 1) throw new BiException(HttpStatus.BAD_REQUEST, "BI_REQUEST_INVALID", "expectedVersion 不合法");
        config.put("reportId", String.valueOf(reportId));
        String changeSummary = config.path("changeSummary").asText(null);
        config.remove("changeSummary");
        ReportDtos.Saved saved = repository.saveConfiguration(reportId, expected, config, changeSummary, userId);
        invalidate(saved.reportUuid());
        return saved;
    }

    public ReportDtos.VersionPage versions(long reportId, int page, int pageSize) {
        access.requireReadable(reportId, repository);
        int safePage = Math.max(1, page); int safeSize = Math.min(100, Math.max(1, pageSize));
        return repository.versions(reportId, safePage, safeSize);
    }

    public ReportDtos.VersionDetail version(long reportId, long versionNo) {
        access.requireReadable(reportId, repository);
        return repository.version(reportId, versionNo);
    }

    public ReportDtos.VersionDiff diff(long reportId, long versionNo, long against) {
        access.requireReadable(reportId, repository);
        JsonNode before = repository.version(reportId, against).snapshot();
        JsonNode after = repository.version(reportId, versionNo).snapshot();
        return new ReportDtos.VersionDiff(against, versionNo, ReportConfigDiff.compare(before, after));
    }

    public ReportDtos.Saved rollback(long reportId, long sourceVersion, ReportDtos.RollbackRequest request, long userId) {
        access.requireReadable(reportId, repository);
        ObjectNode target = repository.snapshot(reportId, sourceVersion);
        target.put("expectedVersion", request.expectedVersion());
        ReportDtos.ValidationResult validation = validateConfig(reportId, target, true);
        if (!validation.valid()) throw new BiException(HttpStatus.BAD_REQUEST, "BI_CONFIG_INVALID", "回滚版本已失效",
            Map.of("errors", validation.errors()));
        ReportDtos.Saved saved = repository.rollback(reportId, sourceVersion, request.expectedVersion(),
            request.changeSummary(), userId);
        invalidate(saved.reportUuid());
        return saved;
    }

    private void invalidate(String uuid) { if (runtimeCache != null) runtimeCache.invalidate(uuid); }

    private ReportDtos.ValidationResult validateConfig(long reportId, ObjectNode config, boolean saving) {
        ReportDtos.ValidationResult structural = validator.validate(config);
        if (!structural.valid()) return structural;
        boolean hasSqlControl = java.util.stream.StreamSupport.stream(config.path("controls").spliterator(), false)
            .anyMatch(node -> "SQL".equals(node.path("optionSource").asText()));
        if (hasSqlControl && !SecurityUtils.isAdmin() && !SecurityUtils.hasPermi("bi:control:sql")) {
            return new ReportDtos.ValidationResult(false, List.of(new ReportDtos.ValidationIssue("controls",
                "OPTION_SQL_FORBIDDEN", "只有系统管理员或具有 SQL 选项权限的开发者可以编辑 SQL 选项")), List.of());
        }
        var base = config.withObject("baseInfo");
        ProcedureMetadata metadata = datasources.parameters(base.path("defaultDatasourceId").asLong(),
            base.path("defaultProcedureName").asText());
        if (!metadata.supported()) return new ReportDtos.ValidationResult(false,
            List.of(new ReportDtos.ValidationIssue("baseInfo.defaultProcedureName", "BI_UNSUPPORTED_SIGNATURE",
                String.join("；", metadata.unsupportedReasons()))), List.of());
        base.put("defaultSignatureHash", metadata.signatureHash());
        List<JsonNode> defaultMappings = java.util.stream.StreamSupport.stream(
            config.path("parameterMappings").spliterator(), false)
            .filter(node -> !node.hasNonNull("componentKey") || node.path("componentKey").asText().isBlank()).toList();
        for (JsonNode mapping : defaultMappings) {
            int ordinal = mapping.path("parameterOrdinal").asInt();
            var parameter = metadata.parameters().stream().filter(item -> item.ordinal() == ordinal).findFirst().orElse(null);
            if (parameter == null || !parameter.name().equals(mapping.path("parameterName").asText())
                || !metadata.signatureHash().equals(mapping.path("signatureHash").asText())
                || base.path("defaultDatasourceId").asLong() != mapping.path("datasourceId").asLong()
                || !base.path("defaultProcedureName").asText().equals(mapping.path("procedureName").asText())) {
                return new ReportDtos.ValidationResult(false, List.of(new ReportDtos.ValidationIssue("parameterMappings",
                    "PARAMETER_SIGNATURE_MISMATCH", "默认过程参数映射与实时签名不一致，请重新同步")), List.of());
            }
        }
        if ("ENABLED".equals(base.path("status").asText())) {
            if (defaultMappings.size() != metadata.parameters().size()) return new ReportDtos.ValidationResult(false,
                List.of(new ReportDtos.ValidationIssue("parameterMappings", "PARAMETER_MAPPING_INCOMPLETE",
                    "启用前必须完整配置默认存储过程参数映射")), List.of());
        }
        for (JsonNode component : config.path("components")) {
            boolean hasDatasource = component.path("datasourceIdOverride").asLong(0) > 0;
            boolean hasProcedure = !component.path("procedureNameOverride").asText().isBlank();
            if (hasDatasource != hasProcedure) return invalidIssue("components", "COMPONENT_OVERRIDE_INCOMPLETE", "组件覆盖必须同时选择数据源和存储过程");
            if (!hasProcedure) continue;
            long datasourceId = component.path("datasourceIdOverride").asLong(); String procedure = component.path("procedureNameOverride").asText();
            ProcedureMetadata overrideMetadata = datasources.parameters(datasourceId, procedure);
            String key = component.path("componentKey").asText();
            List<JsonNode> mappings = java.util.stream.StreamSupport.stream(config.path("parameterMappings").spliterator(), false)
                .filter(node -> key.equals(node.path("componentKey").asText())).toList();
            if (!overrideMetadata.supported() || !overrideMetadata.signatureHash().equals(component.path("signatureHashOverride").asText())
                || mappings.size() != overrideMetadata.parameters().size())
                return invalidIssue("components." + key, "PARAMETER_SIGNATURE_MISMATCH", "组件覆盖过程参数未同步或签名已变化");
            for (JsonNode mapping : mappings) {
                var parameter = overrideMetadata.parameters().stream().filter(item -> item.ordinal() == mapping.path("parameterOrdinal").asInt()).findFirst().orElse(null);
                if (parameter == null || !parameter.name().equals(mapping.path("parameterName").asText())
                    || datasourceId != mapping.path("datasourceId").asLong() || !procedure.equals(mapping.path("procedureName").asText()))
                    return invalidIssue("parameterMappings", "PARAMETER_SIGNATURE_MISMATCH", "组件覆盖参数与实时签名不一致");
            }
        }
        return structural;
    }

    private static ReportDtos.ValidationResult invalidIssue(String path, String code, String message) {
        return new ReportDtos.ValidationResult(false, List.of(new ReportDtos.ValidationIssue(path, code, message)), List.of());
    }

    private void validateActivation(long reportId) {
        ReportRepository.ActivationInfo info = repository.activationInfo(reportId);
        DatasourceDtos.ProcedureMetadata metadata = datasources.parameters(info.datasourceId(), info.procedureName());
        if (!metadata.supported() || !metadata.signatureHash().equals(info.signatureHash())) {
            throw new BiException(HttpStatus.CONFLICT, "BI_PROCEDURE_SIGNATURE_CHANGED", "存储过程签名已变化，请重新同步参数");
        }
        if (repository.defaultMappingCount(reportId) != metadata.parameters().size()) {
            throw new BiException(HttpStatus.BAD_REQUEST, "BI_CONFIG_INVALID", "默认存储过程参数映射尚未配置完整");
        }
    }
}
