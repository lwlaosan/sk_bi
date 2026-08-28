package com.ruoyi.bi.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.bi.api.BiException;
import com.ruoyi.bi.support.IdGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Repository
public class InsightHistoryRepository {
    private final JdbcTemplate jdbc;
    private final IdGenerator ids;
    private final ObjectMapper mapper;

    public InsightHistoryRepository(JdbcTemplate jdbc,IdGenerator ids,ObjectMapper mapper) {
        this.jdbc=jdbc;this.ids=ids;this.mapper=mapper;
    }

    @Transactional
    public Saved save(long reportId,long configVersion,String requestId,String provider,String model,
                      String content,String contextJson,String routeSummary,int inputRows,long userId) {
        long id=ids.nextId();String userName=currentUserName(userId);
        jdbc.update("""
            INSERT INTO bi_insight_history(id,report_id,config_version,request_id,provider,model,content,
              context_snapshot_json,route_summary,input_rows,generated_by,generated_by_name)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,reportId,configVersion,requestId,provider,model,content,contextJson,routeSummary,inputRows,userId,userName);
        return new Saved(id,userName,LocalDateTime.now());
    }

    public RuntimeDtos.InsightHistoryPage page(long reportId,int page,int pageSize) {
        int safePage=Math.max(1,page),safeSize=Math.max(1,Math.min(100,pageSize));
        long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM bi_insight_history WHERE report_id=?",Long.class,reportId));
        List<RuntimeDtos.InsightHistorySummary> items=jdbc.query("""
            SELECT id,provider,model,route_summary,input_rows,generated_by_name,generated_at
              FROM bi_insight_history WHERE report_id=? ORDER BY generated_at DESC,id DESC LIMIT ? OFFSET ?
            """,this::summary,reportId,safeSize,(safePage-1)*safeSize);
        return new RuntimeDtos.InsightHistoryPage(items,safePage,safeSize,total);
    }

    public RuntimeDtos.InsightHistoryDetail detail(long reportId,long id) {
        return jdbc.query("""
            SELECT id,config_version,request_id,provider,model,content,context_snapshot_json,route_summary,
                   input_rows,generated_by_name,generated_at
              FROM bi_insight_history WHERE report_id=? AND id=?
            """,this::detail,reportId,id).stream().findFirst().orElseThrow(()->
            new BiException(HttpStatus.NOT_FOUND,"BI_INSIGHT_HISTORY_NOT_FOUND","洞察历史不存在"));
    }

    private String currentUserName(long userId) {
        return jdbc.query("SELECT nick_name,user_name FROM sys_user WHERE user_id=?",(rs,row)->{
            String nick=rs.getString(1);return nick==null||nick.isBlank()?rs.getString(2):nick;
        },userId).stream().findFirst().orElse(String.valueOf(userId));
    }
    private RuntimeDtos.InsightHistorySummary summary(ResultSet rs,int row)throws SQLException {
        return new RuntimeDtos.InsightHistorySummary(String.valueOf(rs.getLong(1)),rs.getString(2),rs.getString(3),
            rs.getString(4),rs.getInt(5),rs.getString(6),rs.getTimestamp(7).toLocalDateTime().toString());
    }
    private RuntimeDtos.InsightHistoryDetail detail(ResultSet rs,int row)throws SQLException {
        return new RuntimeDtos.InsightHistoryDetail(String.valueOf(rs.getLong(1)),rs.getLong(2),rs.getString(3),
            rs.getString(4),rs.getString(5),rs.getString(6),json(rs.getString(7)),rs.getString(8),rs.getInt(9),
            rs.getString(10),rs.getTimestamp(11).toLocalDateTime().toString());
    }
    private JsonNode json(String text) { try{return mapper.readTree(text);}catch(Exception ex){return mapper.createObjectNode();} }
    public record Saved(long id,String userName,LocalDateTime generatedAt) {}
}
