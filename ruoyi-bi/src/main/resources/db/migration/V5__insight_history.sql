CREATE TABLE bi_insight_history (
 id BIGINT PRIMARY KEY,
 report_id BIGINT NOT NULL,
 config_version BIGINT NOT NULL,
 request_id VARCHAR(64) NOT NULL,
 provider VARCHAR(16) NOT NULL,
 model VARCHAR(100) NOT NULL,
 content MEDIUMTEXT NOT NULL,
 context_snapshot_json JSON NOT NULL,
 route_summary VARCHAR(1000) NOT NULL,
 input_rows INT NOT NULL,
 generated_by BIGINT NOT NULL,
 generated_by_name VARCHAR(100) NOT NULL,
 generated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 CONSTRAINT fk_bi_insight_history_report FOREIGN KEY (report_id) REFERENCES bi_report(id),
 UNIQUE KEY uk_bi_insight_history_request (report_id,request_id),
 KEY idx_bi_insight_history_report_time (report_id,generated_at),
 KEY idx_bi_insight_history_user_time (generated_by,generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
