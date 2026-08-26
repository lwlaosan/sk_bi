CREATE TABLE bi_query_audit (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 report_id BIGINT NOT NULL, report_uuid CHAR(36) NOT NULL, component_key VARCHAR(64) NOT NULL,
 user_id BIGINT NOT NULL, datasource_id BIGINT NOT NULL, procedure_name VARCHAR(128) NOT NULL,
 request_id VARCHAR(64) NOT NULL, trace_id VARCHAR(64) NULL, elapsed_ms BIGINT NOT NULL,
 row_count INT NOT NULL DEFAULT 0, truncated TINYINT(1) NOT NULL DEFAULT 0,
 outcome VARCHAR(16) NOT NULL, error_code VARCHAR(64) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_bi_query_audit_report_time (report_id,created_at DESC),
 KEY idx_bi_query_audit_user_time (user_id,created_at DESC),
 CONSTRAINT fk_bi_query_audit_report FOREIGN KEY (report_id) REFERENCES bi_report(id),
 CONSTRAINT ck_bi_query_audit_outcome CHECK (outcome IN ('SUCCESS','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
