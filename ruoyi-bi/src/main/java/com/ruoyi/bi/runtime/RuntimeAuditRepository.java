package com.ruoyi.bi.runtime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RuntimeAuditRepository {
    private final JdbcTemplate jdbc;
    public RuntimeAuditRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    void success(long reportId,String uuid,String component,long userId,long datasourceId,String procedure,
                 String requestId,String traceId,long elapsed,int rows,boolean truncated){
        try{jdbc.update("""
            INSERT INTO bi_query_audit(report_id,report_uuid,component_key,user_id,datasource_id,procedure_name,
              request_id,trace_id,elapsed_ms,row_count,truncated,outcome)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,'SUCCESS')
            """,reportId,uuid,component,userId,datasourceId,procedure,requestId,traceId,elapsed,rows,truncated);}catch(Exception ignored){}
    }
}
