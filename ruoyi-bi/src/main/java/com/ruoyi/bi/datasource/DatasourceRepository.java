package com.ruoyi.bi.datasource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.bi.api.BiException;
import com.ruoyi.bi.support.IdGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
public class DatasourceRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final IdGenerator ids;

    public DatasourceRepository(JdbcTemplate jdbc, ObjectMapper mapper, IdGenerator ids) {
        this.jdbc = jdbc; this.mapper = mapper; this.ids = ids;
    }

    public Optional<DatasourceRecord> find(long id) {
        return jdbc.query("SELECT * FROM bi_datasource WHERE id=? AND deleted=0", rowMapper(), id).stream().findFirst();
    }

    public List<DatasourceRecord> findPage(int offset, int limit, String keyword, DatasourceStatus status,
                                           boolean admin, long userId, List<Long> roleIds) {
        StringBuilder sql = new StringBuilder("SELECT d.* FROM bi_datasource d WHERE d.deleted=0");
        List<Object> args = new ArrayList<>();
        appendAccess(sql, args, admin, userId, roleIds);
        if (keyword != null && !keyword.isBlank()) { sql.append(" AND d.datasource_name LIKE ?"); args.add("%" + keyword.trim() + "%"); }
        if (status != null) { sql.append(" AND d.status=?"); args.add(status.name()); }
        sql.append(" ORDER BY d.updated_at DESC LIMIT ? OFFSET ?"); args.add(limit); args.add(offset);
        return jdbc.query(sql.toString(), rowMapper(), args.toArray());
    }

    public long count(String keyword, DatasourceStatus status, boolean admin, long userId, List<Long> roleIds) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM bi_datasource d WHERE d.deleted=0");
        List<Object> args = new ArrayList<>();
        appendAccess(sql, args, admin, userId, roleIds);
        if (keyword != null && !keyword.isBlank()) { sql.append(" AND d.datasource_name LIKE ?"); args.add("%" + keyword.trim() + "%"); }
        if (status != null) { sql.append(" AND d.status=?"); args.add(status.name()); }
        return Objects.requireNonNull(jdbc.queryForObject(sql.toString(), Long.class, args.toArray()));
    }

    public boolean hasAccess(long datasourceId, long userId, List<Long> roleIds) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*) FROM bi_datasource_acl
             WHERE datasource_id=? AND ((subject_type='USER' AND subject_id=?)
            """);
        List<Object> args = new ArrayList<>(List.of(datasourceId, userId));
        if (roleIds != null && !roleIds.isEmpty()) {
            sql.append(" OR (subject_type='ROLE' AND subject_id IN (")
                .append(String.join(",", Collections.nCopies(roleIds.size(), "?"))).append("))");
            args.addAll(roleIds);
        }
        sql.append(")");
        return Objects.requireNonNull(jdbc.queryForObject(sql.toString(), Long.class, args.toArray())) > 0;
    }

    private static void appendAccess(StringBuilder sql, List<Object> args, boolean admin,
                                     long userId, List<Long> roleIds) {
        if (admin) return;
        sql.append(" AND EXISTS (SELECT 1 FROM bi_datasource_acl a WHERE a.datasource_id=d.id")
            .append(" AND ((a.subject_type='USER' AND a.subject_id=?)");
        args.add(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            sql.append(" OR (a.subject_type='ROLE' AND a.subject_id IN (")
                .append(String.join(",", Collections.nCopies(roleIds.size(), "?"))).append("))");
            args.addAll(roleIds);
        }
        sql.append("))");
    }

    @Transactional
    public long create(DatasourceDtos.SaveRequest request, String ciphertext, long userId) {
        long id = ids.nextId();
        try {
            jdbc.update("""
                INSERT INTO bi_datasource(id,datasource_name,host,port,database_name,username,password_ciphertext,
                  connection_props,credential_version,status,remark,created_by,updated_by)
                VALUES(?,?,?,?,?,?,?,?,1,?,?,?,?)
                """, id, request.datasourceName(), request.host(), request.port(), request.databaseName(),
                request.username(), ciphertext, toJson(connectionProps(request)), request.status().name(),
                request.remark(), userId, userId);
            replaceAcl(id, request.roleIds(), request.userIds(), userId);
            return id;
        } catch (DuplicateKeyException ex) {
            throw new BiException(HttpStatus.BAD_REQUEST, "BI_REQUEST_INVALID", "数据源名称已存在");
        }
    }

    @Transactional
    public void update(long id, DatasourceDtos.SaveRequest request, String ciphertext, boolean passwordChanged, long userId) {
        long expected = request.expectedRowVersion() == null ? -1 : request.expectedRowVersion();
        String passwordSql = passwordChanged ? ",password_ciphertext=?,credential_version=credential_version+1" : "";
        String sql = "UPDATE bi_datasource SET datasource_name=?,host=?,port=?,database_name=?,username=?,connection_props=?,status=?,remark=?,updated_by=?,row_version=row_version+1"
            + passwordSql + " WHERE id=? AND deleted=0" + (expected >= 0 ? " AND row_version=?" : "");
        List<Object> args = new ArrayList<>();
        args.add(request.datasourceName()); args.add(request.host()); args.add(request.port());
        args.add(request.databaseName()); args.add(request.username()); args.add(toJson(connectionProps(request)));
        args.add(request.status().name()); args.add(request.remark()); args.add(userId);
        if (passwordChanged) args.add(ciphertext);
        args.add(id); if (expected >= 0) args.add(expected);
        try {
            int changed = jdbc.update(sql, args.toArray());
            if (changed == 0) throw new BiException(HttpStatus.CONFLICT, "BI_CONFIG_VERSION_CONFLICT", "数据源已被其他用户更新或不存在");
            replaceAcl(id, request.roleIds(), request.userIds(), userId);
        } catch (DuplicateKeyException ex) {
            throw new BiException(HttpStatus.BAD_REQUEST, "BI_REQUEST_INVALID", "数据源名称已存在");
        }
    }

    public Map<SubjectType, List<Long>> acl(long datasourceId) {
        Map<SubjectType, List<Long>> result = new EnumMap<>(SubjectType.class);
        result.put(SubjectType.ROLE, new ArrayList<>()); result.put(SubjectType.USER, new ArrayList<>());
        jdbc.query("SELECT subject_type,subject_id FROM bi_datasource_acl WHERE datasource_id=? ORDER BY subject_id", rs -> {
            result.get(SubjectType.valueOf(rs.getString(1))).add(rs.getLong(2));
        }, datasourceId);
        return result;
    }

    private void replaceAcl(long datasourceId, List<Long> roles, List<Long> users, long createdBy) {
        jdbc.update("DELETE FROM bi_datasource_acl WHERE datasource_id=?", datasourceId);
        insertAcl(datasourceId, SubjectType.ROLE, roles, createdBy);
        insertAcl(datasourceId, SubjectType.USER, users, createdBy);
    }

    private void insertAcl(long datasourceId, SubjectType type, List<Long> subjects, long createdBy) {
        if (subjects == null) return;
        subjects.stream().distinct().forEach(subject -> jdbc.update(
            "INSERT INTO bi_datasource_acl(id,datasource_id,subject_type,subject_id,created_by) VALUES(?,?,?,?,?)",
            ids.nextId(), datasourceId, type.name(), subject, createdBy));
    }

    private RowMapper<DatasourceRecord> rowMapper() {
        return (rs, rowNum) -> new DatasourceRecord(rs.getLong("id"), rs.getString("datasource_name"),
            rs.getString("host"), rs.getInt("port"), rs.getString("database_name"), rs.getString("username"),
            rs.getString("password_ciphertext"), readJson(rs), rs.getInt("credential_version"),
            DatasourceStatus.valueOf(rs.getString("status")), rs.getString("remark"),
            rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("updated_at").toLocalDateTime(),
            rs.getLong("row_version"));
    }

    private Map<String, Object> readJson(ResultSet rs) throws SQLException {
        String json = rs.getString("connection_props");
        if (json == null || json.isBlank()) return Map.of();
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception ex) { throw new SQLException("invalid connection_props", ex); }
    }

    private String toJson(Map<String, Object> value) {
        try { return mapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (Exception ex) { throw new BiException(HttpStatus.BAD_REQUEST, "BI_REQUEST_INVALID", "connectionProps 无法序列化"); }
    }

    private Map<String, Object> connectionProps(DatasourceDtos.SaveRequest request) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (request.connectionProps() != null) props.putAll(request.connectionProps());
        props.put("databaseType", request.effectiveDatabaseType().name());
        return props;
    }
}
