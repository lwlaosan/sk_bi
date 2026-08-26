package com.ruoyi.bi.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.bi.api.BiException;
import com.ruoyi.bi.datasource.DatasourceDtos;
import com.ruoyi.bi.support.IdGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;

@Repository
public class ReportRepository {
    private final JdbcTemplate jdbc;
    private final IdGenerator ids;
    private final ObjectMapper mapper;

    public ReportRepository(JdbcTemplate jdbc, IdGenerator ids, ObjectMapper mapper) {
        this.jdbc = jdbc; this.ids = ids; this.mapper = mapper;
    }

    public List<ReportDtos.Summary> findPage(int offset, int limit, String keyword, ReportStatus status,
                                             Long createdBy, boolean admin, long userId, List<Long> roleIds) {
        Query query = selectQuery(keyword, status, createdBy, admin, userId, roleIds, false);
        query.sql.append(" ORDER BY r.updated_at DESC LIMIT ? OFFSET ?");
        query.args.add(limit); query.args.add(offset);
        return jdbc.query(query.sql.toString(), this::mapSummary, query.args.toArray());
    }

    public long count(String keyword, ReportStatus status, Long createdBy,
                      boolean admin, long userId, List<Long> roleIds) {
        Query query = selectQuery(keyword, status, createdBy, admin, userId, roleIds, true);
        return Objects.requireNonNull(jdbc.queryForObject(query.sql.toString(), Long.class, query.args.toArray()));
    }

    public boolean exists(long reportId) {
        return Objects.requireNonNull(jdbc.queryForObject(
            "SELECT COUNT(*) FROM bi_report WHERE id=? AND deleted=0", Long.class, reportId)) > 0;
    }
    public String uuid(long reportId) { return jdbc.queryForObject("SELECT report_uuid FROM bi_report WHERE id=?",String.class,reportId); }

    public boolean hasManagementAccess(long reportId, long userId, List<Long> roleIds) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*) FROM bi_report r WHERE r.id=? AND r.deleted=0
             AND (r.created_by=? OR EXISTS (SELECT 1 FROM bi_report_acl a
              WHERE a.report_id=r.id AND ((a.subject_type='USER' AND a.subject_id=?)
            """);
        List<Object> args = new ArrayList<>(List.of(reportId, userId, userId));
        appendRoleClause(sql, args, roleIds, "a");
        sql.append(")))");
        return Objects.requireNonNull(jdbc.queryForObject(sql.toString(), Long.class, args.toArray())) > 0;
    }

    @Transactional
    public ReportDtos.Created create(ReportDtos.CreateRequest request, DatasourceDtos.ProcedureMetadata metadata,
                                     long userId) {
        long reportId = ids.nextId();
        long componentId = ids.nextId();
        long routeId = ids.nextId();
        String uuid = UUID.randomUUID().toString();
        int maxRows = request.maxRows() == null ? 50_000 : request.maxRows();
        int timeout = request.timeoutSeconds() == null ? 60 : request.timeoutSeconds();
        jdbc.update("""
            INSERT INTO bi_report(id,report_uuid,report_name,description,status,default_datasource_id,
              default_procedure_name,default_signature_hash,max_rows,timeout_seconds,current_config_version,
              created_by,updated_by) VALUES(?,?,?,?,'DISABLED',?,?,?,?,?,1,?,?)
            """, reportId, uuid, request.reportName().trim(), request.description(), request.defaultDatasourceId(),
            request.defaultProcedureName(), metadata.signatureHash(), maxRows, timeout, userId, userId);
        jdbc.update("""
            INSERT INTO bi_component(id,report_id,region_type,component_key,component_name,title_visible,
              layout_json,display_order,created_by,updated_by)
            VALUES(?,?,'TABLE','main_table','主表格',1,'{}',0,?,?)
            """, componentId, reportId, userId, userId);
        jdbc.update("""
            INSERT INTO bi_route(id,component_id,route_code,route_name,view_type,created_by,updated_by)
            VALUES(?,?,'ROOT','根层级','TABLE',?,?)
            """, routeId, componentId, userId, userId);
        saveVersion(reportId, 1, initialSnapshot(reportId, uuid, componentId, routeId, request, metadata,
            maxRows, timeout), "创建报表", "CREATE", null, userId);
        return new ReportDtos.Created(String.valueOf(reportId), uuid, 1);
    }

    @Transactional
    public void changeStatus(long reportId, ReportDtos.StatusRequest request, long userId) {
        int changed = jdbc.update("""
            UPDATE bi_report SET status=?,updated_by=?,row_version=row_version+1,
              current_config_version=current_config_version+1
             WHERE id=? AND deleted=0 AND current_config_version=?
            """, request.status().name(), userId, reportId, request.expectedVersion());
        if (changed == 0) throw conflict();
        long version = request.expectedVersion() + 1;
        ObjectNode snapshot = latestSnapshot(reportId);
        snapshot.put("expectedVersion", version);
        snapshot.withObject("baseInfo").put("status", request.status().name());
        saveVersion(reportId, version, snapshot, "切换报表状态为" + request.status().name(), "SAVE", null, userId);
    }

    @Transactional
    public void delete(long reportId, long userId) {
        int changed = jdbc.update("""
            UPDATE bi_report SET deleted=1,status='DISABLED',updated_by=?,row_version=row_version+1
             WHERE id=? AND deleted=0
            """, userId, reportId);
        if (changed == 0) throw notFound();
    }

    public int defaultMappingCount(long reportId) {
        return Objects.requireNonNull(jdbc.queryForObject(
            "SELECT COUNT(*) FROM bi_sp_param_mapping WHERE report_id=? AND component_id IS NULL",
            Integer.class, reportId));
    }

    public ActivationInfo activationInfo(long reportId) {
        return jdbc.query("""
            SELECT default_datasource_id,default_procedure_name,default_signature_hash
              FROM bi_report WHERE id=? AND deleted=0
            """, (rs, row) -> new ActivationInfo(rs.getLong(1), rs.getString(2), rs.getString(3)), reportId)
            .stream().findFirst().orElseThrow(ReportRepository::notFound);
    }

    public ObjectNode configuration(long reportId) {
        return latestSnapshot(reportId);
    }

    @Transactional
    public ReportDtos.Saved saveConfiguration(long reportId, long expectedVersion, ObjectNode snapshot,
                                               String changeSummary, long userId) {
        return persistConfiguration(reportId, expectedVersion, snapshot, changeSummary, "SAVE", null, userId);
    }

    @Transactional
    public ReportDtos.Saved rollback(long reportId, long sourceVersion, long expectedVersion,
                                     String changeSummary, long userId) {
        ObjectNode snapshot = (ObjectNode) version(reportId, sourceVersion).snapshot().deepCopy();
        return persistConfiguration(reportId, expectedVersion, snapshot, changeSummary, "ROLLBACK", sourceVersion, userId);
    }

    private ReportDtos.Saved persistConfiguration(long reportId, long expectedVersion, ObjectNode snapshot,
                                                  String changeSummary, String operation, Long sourceVersion, long userId) {
        var base = snapshot.path("baseInfo");
        int changed = jdbc.update("""
            UPDATE bi_report SET report_name=?,description=?,status=?,default_datasource_id=?,
              default_procedure_name=?,default_signature_hash=?,max_rows=?,timeout_seconds=?,updated_by=?,
              current_config_version=current_config_version+1,row_version=row_version+1
             WHERE id=? AND deleted=0 AND current_config_version=?
            """, base.path("reportName").asText().trim(), nullableText(base.get("description")),
            base.path("status").asText(), base.path("defaultDatasourceId").asLong(),
            base.path("defaultProcedureName").asText(), nullableText(base.get("defaultSignatureHash")),
            base.path("maxRows").asInt(), base.path("timeoutSeconds").asInt(), userId, reportId, expectedVersion);
        if (changed == 0) throw conflict();
        replaceReportAcl(reportId, snapshot.path("acl").path("roleIds"), snapshot.path("acl").path("userIds"), userId);
        replaceStructure(reportId, snapshot, userId);
        long nextVersion = expectedVersion + 1;
        snapshot.put("expectedVersion", nextVersion);
        saveVersion(reportId, nextVersion, snapshot,
            changeSummary == null || changeSummary.isBlank() ? "保存报表配置" : changeSummary.trim(),
            operation, sourceVersion, userId);
        String uuid = jdbc.queryForObject("SELECT report_uuid FROM bi_report WHERE id=?", String.class, reportId);
        return new ReportDtos.Saved(String.valueOf(reportId), uuid, nextVersion, LocalDateTime.now());
    }

    public RuntimeHead runtimeHead(String uuid) {
        return jdbc.query("""
            SELECT id,report_uuid,report_name,status,current_config_version,max_rows,timeout_seconds
              FROM bi_report WHERE report_uuid=? AND deleted=0
            """, (rs, row) -> new RuntimeHead(rs.getLong(1), rs.getString(2), rs.getString(3),
            ReportStatus.valueOf(rs.getString(4)), rs.getLong(5), rs.getInt(6), rs.getInt(7)), uuid)
            .stream().findFirst().orElseThrow(ReportRepository::notFound);
    }

    @Transactional
    public ReportDtos.Created copy(long sourceReportId,long userId) {
        ObjectNode snapshot=latestSnapshot(sourceReportId); ObjectNode base=snapshot.withObject("baseInfo");
        long reportId=ids.nextId(); String uuid=UUID.randomUUID().toString(); String name=base.path("reportName").asText()+"（副本）";
        jdbc.update("""
          INSERT INTO bi_report(id,report_uuid,report_name,description,status,default_datasource_id,
            default_procedure_name,default_signature_hash,max_rows,timeout_seconds,current_config_version,created_by,updated_by)
          VALUES(?,?,?,?,'DISABLED',?,?,?,?,?,1,?,?)
          """,reportId,uuid,name,nullableText(base.get("description")),base.path("defaultDatasourceId").asLong(),
          base.path("defaultProcedureName").asText(),nullableText(base.get("defaultSignatureHash")),base.path("maxRows").asInt(),base.path("timeoutSeconds").asInt(),userId,userId);
        snapshot.put("reportId",String.valueOf(reportId));snapshot.put("reportUuid",uuid);snapshot.put("expectedVersion",1);
        base.put("reportName",name);base.put("status","DISABLED");snapshot.withObject("acl").putArray("userIds");
        replaceReportAcl(reportId,snapshot.path("acl").path("roleIds"),snapshot.path("acl").path("userIds"),userId);
        replaceStructure(reportId,snapshot,userId);saveVersion(reportId,1,snapshot,"复制报表","CREATE",null,userId);
        return new ReportDtos.Created(String.valueOf(reportId),uuid,1);
    }

    public ObjectNode snapshot(long reportId, long version) {
        return (ObjectNode) version(reportId, version).snapshot().deepCopy();
    }

    public ReportDtos.VersionPage versions(long reportId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<ReportDtos.VersionSummary> items = jdbc.query("""
            SELECT version_no,operation_type,source_version,change_summary,created_by,created_at
              FROM bi_config_version WHERE report_id=? ORDER BY version_no DESC LIMIT ? OFFSET ?
            """, (rs, row) -> new ReportDtos.VersionSummary(rs.getLong(1), rs.getString(2),
            nullableLong(rs, 3), rs.getString(4), String.valueOf(rs.getLong(5)),
            rs.getTimestamp(6).toLocalDateTime()), reportId, pageSize, offset);
        long total = Objects.requireNonNull(jdbc.queryForObject(
            "SELECT COUNT(*) FROM bi_config_version WHERE report_id=?", Long.class, reportId));
        return new ReportDtos.VersionPage(items, page, pageSize, total);
    }

    public ReportDtos.VersionDetail version(long reportId, long versionNo) {
        return jdbc.query("""
            SELECT version_no,operation_type,source_version,change_summary,snapshot_sha256,snapshot_json,created_by,created_at
              FROM bi_config_version WHERE report_id=? AND version_no=?
            """, (rs, row) -> new ReportDtos.VersionDetail(rs.getLong(1), rs.getString(2), nullableLong(rs, 3),
            rs.getString(4), rs.getString(5), readTree(rs.getString(6)), String.valueOf(rs.getLong(7)),
            rs.getTimestamp(8).toLocalDateTime()), reportId, versionNo).stream().findFirst()
            .orElseThrow(() -> new BiException(HttpStatus.NOT_FOUND, "BI_REQUEST_INVALID", "版本不存在"));
    }

    private Query selectQuery(String keyword, ReportStatus status, Long createdBy, boolean admin,
                              long userId, List<Long> roleIds, boolean count) {
        String fields = count ? "SELECT COUNT(*)" : """
            SELECT r.*,(SELECT COUNT(*) FROM bi_component c WHERE c.report_id=r.id) component_count
            """;
        Query query = new Query(new StringBuilder(fields + " FROM bi_report r WHERE r.deleted=0"), new ArrayList<>());
        if (!admin) {
            query.sql.append(" AND (r.created_by=? OR EXISTS (SELECT 1 FROM bi_report_acl a WHERE a.report_id=r.id")
                .append(" AND ((a.subject_type='USER' AND a.subject_id=?)");
            query.args.add(userId); query.args.add(userId);
            appendRoleClause(query.sql, query.args, roleIds, "a");
            query.sql.append(")))");
        }
        if (keyword != null && !keyword.isBlank()) {
            query.sql.append(" AND r.report_name LIKE ?"); query.args.add("%" + keyword.trim() + "%");
        }
        if (status != null) { query.sql.append(" AND r.status=?"); query.args.add(status.name()); }
        if (createdBy != null) { query.sql.append(" AND r.created_by=?"); query.args.add(createdBy); }
        return query;
    }

    private static void appendRoleClause(StringBuilder sql, List<Object> args, List<Long> roleIds, String alias) {
        if (roleIds != null && !roleIds.isEmpty()) {
            sql.append(" OR (").append(alias).append(".subject_type='ROLE' AND ").append(alias)
                .append(".subject_id IN (").append(String.join(",", Collections.nCopies(roleIds.size(), "?"))).append("))");
            args.addAll(roleIds);
        }
    }

    private ReportDtos.Summary mapSummary(ResultSet rs, int row) throws SQLException {
        long id = rs.getLong("id");
        return new ReportDtos.Summary(String.valueOf(id), rs.getString("report_uuid"), rs.getString("report_name"),
            rs.getString("description"), ReportStatus.valueOf(rs.getString("status")),
            String.valueOf(rs.getLong("default_datasource_id")), rs.getString("default_procedure_name"),
            rs.getInt("component_count"), rs.getLong("current_config_version"),
            String.valueOf(rs.getLong("created_by")), timestamp(rs, "updated_at"), rs.getLong("row_version"),
            "/bi/report/" + rs.getString("report_uuid"));
    }

    private ObjectNode initialSnapshot(long reportId, String uuid, long componentId, long routeId,
                                       ReportDtos.CreateRequest request, DatasourceDtos.ProcedureMetadata metadata,
                                       int maxRows, int timeout) {
        ObjectNode root = mapper.createObjectNode();
        root.put("reportId", String.valueOf(reportId)); root.put("reportUuid", uuid); root.put("expectedVersion", 1);
        ObjectNode base = root.putObject("baseInfo");
        base.put("reportName", request.reportName().trim()); base.put("description", request.description());
        base.put("status", "DISABLED"); base.put("defaultDatasourceId", String.valueOf(request.defaultDatasourceId()));
        base.put("defaultProcedureName", request.defaultProcedureName()); base.put("defaultSignatureHash", metadata.signatureHash());
        base.put("maxRows", maxRows); base.put("timeoutSeconds", timeout);
        ObjectNode acl = root.putObject("acl"); acl.putArray("roleIds"); acl.putArray("userIds");
        root.putArray("controls"); root.putArray("parameterMappings");
        ObjectNode component = root.putArray("components").addObject();
        component.put("id", String.valueOf(componentId)); component.put("regionType", "TABLE");
        component.put("componentKey", "main_table"); component.put("componentName", "主表格");
        component.putObject("layout");
        ObjectNode route = component.putArray("routes").addObject();
        route.put("id", String.valueOf(routeId)); route.put("routeCode", "ROOT");
        route.put("routeName", "根层级"); route.put("viewType", "TABLE");
        route.putArray("fields"); route.putArray("drillEdges");
        return root;
    }

    private void saveVersion(long reportId, long version, ObjectNode snapshot, String summary,
                             String operation, Long sourceVersion, long userId) {
        try {
            String json = mapper.writeValueAsString(snapshot);
            String sha = com.ruoyi.bi.datasource.ProcedureSignature.sha256Text(json);
            jdbc.update("""
                INSERT INTO bi_config_version(id,report_id,version_no,snapshot_json,snapshot_sha256,
                  change_summary,operation_type,source_version,created_by) VALUES(?,?,?,?,?,?,?,?,?)
                """, ids.nextId(), reportId, version, json, sha, summary, operation, sourceVersion, userId);
        } catch (Exception ex) {
            throw new IllegalStateException("报表版本快照生成失败", ex);
        }
    }

    private ObjectNode latestSnapshot(long reportId) {
        String json = jdbc.queryForObject("""
            SELECT snapshot_json FROM bi_config_version WHERE report_id=? ORDER BY version_no DESC LIMIT 1
            """, String.class, reportId);
        try { return (ObjectNode) mapper.readTree(json); }
        catch (Exception ex) { throw new IllegalStateException("报表版本快照损坏", ex); }
    }

    private void replaceReportAcl(long reportId, com.fasterxml.jackson.databind.JsonNode roles,
                                  com.fasterxml.jackson.databind.JsonNode users, long createdBy) {
        jdbc.update("DELETE FROM bi_report_acl WHERE report_id=?", reportId);
        insertReportAcl(reportId, "ROLE", roles, createdBy);
        insertReportAcl(reportId, "USER", users, createdBy);
    }

    private void replaceStructure(long reportId, ObjectNode snapshot, long userId) {
        jdbc.update("DELETE FROM bi_sp_param_mapping WHERE report_id=?", reportId);
        jdbc.update("DELETE t FROM bi_control_target t JOIN bi_control c ON c.id=t.control_id WHERE c.report_id=?", reportId);
        jdbc.update("DELETE o FROM bi_control_option o JOIN bi_control c ON c.id=o.control_id WHERE c.report_id=?", reportId);
        jdbc.update("DELETE e FROM bi_drill_edge e JOIN bi_component c ON c.id=e.component_id WHERE c.report_id=?", reportId);
        jdbc.update("DELETE f FROM bi_route_field f JOIN bi_route r ON r.id=f.route_id JOIN bi_component c ON c.id=r.component_id WHERE c.report_id=?", reportId);
        jdbc.update("DELETE r FROM bi_route r JOIN bi_component c ON c.id=r.component_id WHERE c.report_id=?", reportId);
        jdbc.update("DELETE FROM bi_control WHERE report_id=?", reportId);
        jdbc.update("DELETE FROM bi_component WHERE report_id=?", reportId);

        Map<String, Long> componentIds = new LinkedHashMap<>();
        Map<String, Long> routeIds = new LinkedHashMap<>();
        JsonNode components = snapshot.path("components");
        for (int i = 0; i < components.size(); i++) {
            ObjectNode component = (ObjectNode) components.get(i);
            long componentId = ids.nextId();
            String componentKey = component.path("componentKey").asText();
            componentIds.put(componentKey, componentId); component.put("id", String.valueOf(componentId));
            jdbc.update("""
                INSERT INTO bi_component(id,report_id,region_type,component_key,component_name,title_visible,
                  datasource_id_override,procedure_name_override,signature_hash_override,layout_json,display_order,
                  created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, componentId, reportId, component.path("regionType").asText(), componentKey,
                component.path("componentName").asText(componentKey), component.path("titleVisible").asBoolean(true),
                nullableId(component.get("datasourceIdOverride")), nullableText(component.get("procedureNameOverride")),
                nullableText(component.get("signatureHashOverride")), toJson(component.path("layout")),
                component.path("displayOrder").asInt(i), userId, userId);
            JsonNode routes = component.path("routes");
            for (int j = 0; j < routes.size(); j++) {
                ObjectNode route = (ObjectNode) routes.get(j);
                long routeId = ids.nextId();
                String routeCode = route.path("routeCode").asText();
                routeIds.put(componentKey + "|" + routeCode, routeId); route.put("id", String.valueOf(routeId));
                jdbc.update("""
                    INSERT INTO bi_route(id,component_id,route_code,route_name,view_type,chart_config_json,created_by,updated_by)
                    VALUES(?,?,?,?,?,?,?,?)
                    """, routeId, componentId, routeCode, route.path("routeName").asText(routeCode),
                    route.path("viewType").asText(), jsonOrNull(route.get("chartConfig")), userId, userId);
                insertFields(routeId, route.path("fields"), userId);
            }
        }
        insertDrillEdges(snapshot.path("components"), componentIds, routeIds, userId);
        insertControls(reportId, snapshot.path("controls"), componentIds, userId);
        insertMappings(reportId, snapshot.path("parameterMappings"), componentIds, userId);
    }

    private void insertFields(long routeId, JsonNode fields, long userId) {
        for (int i = 0; i < fields.size(); i++) {
            JsonNode field = fields.get(i);
            jdbc.update("""
                INSERT INTO bi_route_field(id,route_id,physical_name,display_name,data_type,display_order,visible,
                  fixed_position,width,align_type,format_pattern,style_indicator_field,field_role,created_by,updated_by)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, ids.nextId(), routeId, field.path("physicalName").asText(), field.path("displayName").asText(""),
                field.path("dataType").asText("STRING"), field.path("displayOrder").asInt(i),
                field.path("visible").asBoolean(true), field.path("fixedPosition").asText("NONE"),
                nullableInt(field.get("width")), field.path("alignType").asText("LEFT"),
                nullableText(field.get("formatPattern")), nullableText(field.get("styleIndicatorField")),
                field.path("fieldRole").asText("VALUE"), userId, userId);
        }
    }

    private void insertDrillEdges(JsonNode components, Map<String, Long> componentIds,
                                  Map<String, Long> routeIds, long userId) {
        for (JsonNode component : components) {
            String componentKey = component.path("componentKey").asText();
            for (JsonNode route : component.path("routes")) {
                String sourceCode = route.path("routeCode").asText();
                JsonNode edges = route.path("drillEdges");
                for (int i = 0; i < edges.size(); i++) {
                    JsonNode edge = edges.get(i);
                    Long targetId = routeIds.get(componentKey + "|" + edge.path("targetRouteCode").asText());
                    jdbc.update("""
                        INSERT INTO bi_drill_edge(id,component_id,source_route_id,target_route_id,trigger_field,
                          payload_field,route_value,display_order,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?)
                        """, ids.nextId(), componentIds.get(componentKey), routeIds.get(componentKey + "|" + sourceCode),
                        targetId, edge.path("triggerField").asText(), edge.path("payloadField").asText(),
                        edge.path("routeValue").asText(), edge.path("displayOrder").asInt(i), userId, userId);
                }
            }
        }
    }

    private void insertControls(long reportId, JsonNode controls, Map<String, Long> componentIds, long userId) {
        for (int i = 0; i < controls.size(); i++) {
            JsonNode control = controls.get(i); long controlId = ids.nextId();
            jdbc.update("""
                INSERT INTO bi_control(id,report_id,control_key,label,control_type,required,display_order,option_source,
                  option_datasource_id,option_sql,default_value_json,config_json,created_by,updated_by)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, controlId, reportId, control.path("controlKey").asText(), control.path("label").asText(),
                control.path("controlType").asText(), control.path("required").asBoolean(false),
                control.path("displayOrder").asInt(i), control.path("optionSource").asText("NONE"),
                nullableId(control.get("optionDatasourceId")), nullableText(control.get("optionSql")),
                jsonOrNull(control.get("defaultValue")), jsonOrNull(control.get("config")), userId, userId);
            for (JsonNode option : control.path("options")) jdbc.update("""
                INSERT INTO bi_control_option(id,control_id,option_value,option_label,display_order,enabled)
                VALUES(?,?,?,?,?,?)
                """, ids.nextId(), controlId, option.path("value").asText(), option.path("label").asText(),
                option.path("displayOrder").asInt(0), option.path("enabled").asBoolean(true));
            for (JsonNode target : control.path("targetComponentKeys")) {
                Long componentId = componentIds.get(target.asText());
                if (componentId != null) jdbc.update("INSERT INTO bi_control_target(id,control_id,component_id) VALUES(?,?,?)",
                    ids.nextId(), controlId, componentId);
            }
        }
    }

    private void insertMappings(long reportId, JsonNode mappings, Map<String, Long> componentIds, long userId) {
        for (JsonNode mapping : mappings) jdbc.update("""
            INSERT INTO bi_sp_param_mapping(id,report_id,component_id,datasource_id,procedure_name,signature_hash,
              parameter_ordinal,parameter_name,mysql_data_type,parameter_mode,source_type,source_key,constant_value,
              created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, ids.nextId(), reportId, componentIds.get(nullableText(mapping.get("componentKey"))),
            mapping.path("datasourceId").asLong(), mapping.path("procedureName").asText(),
            mapping.path("signatureHash").asText(), mapping.path("parameterOrdinal").asInt(),
            mapping.path("parameterName").asText(), mapping.path("mysqlDataType").asText(),
            mapping.path("parameterMode").asText("IN"), mapping.path("sourceType").asText(),
            nullableText(mapping.get("sourceKey")), nullableText(mapping.get("constantValue")), userId, userId);
    }

    private String toJson(JsonNode node) {
        try { return mapper.writeValueAsString(node == null || node.isMissingNode() ? mapper.createObjectNode() : node); }
        catch (Exception ex) { throw new IllegalStateException("配置 JSON 序列化失败", ex); }
    }
    private String jsonOrNull(JsonNode node) { return node == null || node.isNull() || node.isMissingNode() ? null : toJson(node); }
    private static Long nullableId(JsonNode node) { if (node == null || node.isNull() || node.asText().isBlank()) return null; long id = node.asLong(); return id > 0 ? id : null; }
    private static Integer nullableInt(JsonNode node) { return node == null || node.isNull() ? null : node.asInt(); }

    private void insertReportAcl(long reportId, String type, com.fasterxml.jackson.databind.JsonNode subjects, long createdBy) {
        if (!subjects.isArray()) return;
        java.util.HashSet<Long> unique = new java.util.HashSet<>();
        subjects.forEach(node -> {
            long subjectId = node.asLong();
            if (subjectId > 0 && unique.add(subjectId)) jdbc.update(
                "INSERT INTO bi_report_acl(id,report_id,subject_type,subject_id,created_by) VALUES(?,?,?,?,?)",
                ids.nextId(), reportId, type, subjectId, createdBy);
        });
    }

    private com.fasterxml.jackson.databind.JsonNode readTree(String json) {
        try { return mapper.readTree(json); }
        catch (Exception ex) { throw new IllegalStateException("报表版本快照损坏", ex); }
    }

    private static String nullableText(com.fasterxml.jackson.databind.JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static Long nullableLong(ResultSet rs, int column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toLocalDateTime();
    }

    private static BiException conflict() {
        return new BiException(HttpStatus.CONFLICT, "BI_CONFIG_VERSION_CONFLICT", "配置已被其他用户更新，请重新加载");
    }
    private static BiException notFound() {
        return new BiException(HttpStatus.NOT_FOUND, "BI_REQUEST_INVALID", "报表不存在");
    }
    private record Query(StringBuilder sql, List<Object> args) {}
    public record ActivationInfo(long datasourceId, String procedureName, String signatureHash) {}
    public record RuntimeHead(long id, String uuid, String name, ReportStatus status, long version,
                              int maxRows, int timeoutSeconds) {}
}
