CREATE TABLE bi_datasource (
 id BIGINT PRIMARY KEY, datasource_name VARCHAR(100) NOT NULL, host VARCHAR(255) NOT NULL,
 port INT NOT NULL DEFAULT 3306, database_name VARCHAR(128) NOT NULL, username VARCHAR(128) NOT NULL,
 password_ciphertext TEXT NOT NULL, connection_props JSON NULL, credential_version INT NOT NULL DEFAULT 1,
 status VARCHAR(16) NOT NULL DEFAULT 'ENABLED', remark VARCHAR(500) NULL, created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 row_version BIGINT NOT NULL DEFAULT 0, deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_bi_datasource_name (datasource_name), KEY idx_bi_datasource_status (status, deleted),
 CONSTRAINT ck_bi_datasource_status CHECK (status IN ('ENABLED','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_datasource_acl (
 id BIGINT PRIMARY KEY, datasource_id BIGINT NOT NULL, subject_type VARCHAR(16) NOT NULL, subject_id BIGINT NOT NULL,
 created_by BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_bi_ds_acl (datasource_id,subject_type,subject_id),
 CONSTRAINT fk_bi_ds_acl_ds FOREIGN KEY (datasource_id) REFERENCES bi_datasource(id),
 CONSTRAINT ck_bi_ds_acl_subject CHECK (subject_type IN ('ROLE','USER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_report (
 id BIGINT PRIMARY KEY, report_uuid CHAR(36) NOT NULL, report_name VARCHAR(150) NOT NULL,
 description VARCHAR(1000) NULL, status VARCHAR(16) NOT NULL DEFAULT 'DISABLED', default_datasource_id BIGINT NOT NULL,
 default_procedure_name VARCHAR(128) NOT NULL, default_signature_hash CHAR(64) NULL, max_rows INT NOT NULL DEFAULT 50000,
 timeout_seconds INT NOT NULL DEFAULT 60, current_config_version BIGINT NOT NULL DEFAULT 1, created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 row_version BIGINT NOT NULL DEFAULT 0, deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_bi_report_uuid (report_uuid), KEY idx_bi_report_name (report_name),
 KEY idx_bi_report_status (status,deleted),
 CONSTRAINT fk_bi_report_ds FOREIGN KEY (default_datasource_id) REFERENCES bi_datasource(id),
 CONSTRAINT ck_bi_report_status CHECK (status IN ('ENABLED','DISABLED')),
 CONSTRAINT ck_bi_report_limit CHECK (max_rows BETWEEN 1 AND 200000),
 CONSTRAINT ck_bi_report_timeout CHECK (timeout_seconds BETWEEN 1 AND 600)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_report_acl (
 id BIGINT PRIMARY KEY, report_id BIGINT NOT NULL, subject_type VARCHAR(16) NOT NULL, subject_id BIGINT NOT NULL,
 created_by BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_bi_report_acl (report_id,subject_type,subject_id), KEY idx_bi_report_acl_subject (subject_type,subject_id),
 CONSTRAINT fk_bi_report_acl_report FOREIGN KEY (report_id) REFERENCES bi_report(id),
 CONSTRAINT ck_bi_report_acl_subject CHECK (subject_type IN ('ROLE','USER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_control (
 id BIGINT PRIMARY KEY, report_id BIGINT NOT NULL, control_key VARCHAR(64) NOT NULL, label VARCHAR(100) NOT NULL,
 control_type VARCHAR(24) NOT NULL, required TINYINT(1) NOT NULL DEFAULT 0, display_order INT NOT NULL DEFAULT 0,
 option_source VARCHAR(16) NOT NULL DEFAULT 'NONE', option_datasource_id BIGINT NULL, option_sql TEXT NULL,
 default_value_json JSON NULL, config_json JSON NULL, created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), row_version BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_bi_control_key (report_id,control_key),
 CONSTRAINT fk_bi_control_report FOREIGN KEY (report_id) REFERENCES bi_report(id),
 CONSTRAINT fk_bi_control_ds FOREIGN KEY (option_datasource_id) REFERENCES bi_datasource(id),
 CONSTRAINT ck_bi_control_type CHECK (control_type IN ('TEXT','SINGLE_SELECT','MULTI_SELECT','DATE','DATE_RANGE','NUMBER','NUMBER_RANGE')),
 CONSTRAINT ck_bi_control_source CHECK (option_source IN ('NONE','STATIC','SQL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_control_option (
 id BIGINT PRIMARY KEY, control_id BIGINT NOT NULL, option_value VARCHAR(500) NOT NULL, option_label VARCHAR(500) NOT NULL,
 display_order INT NOT NULL DEFAULT 0, enabled TINYINT(1) NOT NULL DEFAULT 1,
 UNIQUE KEY uk_bi_control_option (control_id,option_value), KEY idx_bi_control_option_order (control_id,display_order),
 CONSTRAINT fk_bi_control_option_control FOREIGN KEY (control_id) REFERENCES bi_control(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_component (
 id BIGINT PRIMARY KEY, report_id BIGINT NOT NULL, region_type VARCHAR(16) NOT NULL,
 table_region_guard TINYINT GENERATED ALWAYS AS (CASE WHEN region_type='TABLE' THEN 1 ELSE NULL END) STORED,
 component_key VARCHAR(64) NOT NULL, component_name VARCHAR(100) NOT NULL, title_visible TINYINT(1) NOT NULL DEFAULT 1,
 datasource_id_override BIGINT NULL, procedure_name_override VARCHAR(128) NULL, signature_hash_override CHAR(64) NULL,
 layout_json JSON NOT NULL, display_order INT NOT NULL DEFAULT 0, created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), row_version BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_bi_component_key (report_id,component_key), UNIQUE KEY uk_bi_single_table_region (report_id,table_region_guard),
 CONSTRAINT fk_bi_component_report FOREIGN KEY (report_id) REFERENCES bi_report(id),
 CONSTRAINT fk_bi_component_ds FOREIGN KEY (datasource_id_override) REFERENCES bi_datasource(id),
 CONSTRAINT ck_bi_component_region CHECK (region_type IN ('COMPONENT','TABLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_control_target (
 id BIGINT PRIMARY KEY, control_id BIGINT NOT NULL, component_id BIGINT NOT NULL,
 UNIQUE KEY uk_bi_control_target (control_id,component_id),
 CONSTRAINT fk_bi_target_control FOREIGN KEY (control_id) REFERENCES bi_control(id),
 CONSTRAINT fk_bi_target_component FOREIGN KEY (component_id) REFERENCES bi_component(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_route (
 id BIGINT PRIMARY KEY, component_id BIGINT NOT NULL, route_code VARCHAR(64) NOT NULL, route_name VARCHAR(100) NOT NULL,
 view_type VARCHAR(16) NOT NULL, chart_config_json JSON NULL, created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), row_version BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_bi_route_code (component_id,route_code),
 CONSTRAINT fk_bi_route_component FOREIGN KEY (component_id) REFERENCES bi_component(id),
 CONSTRAINT ck_bi_route_view CHECK (view_type IN ('TABLE','BAR','LINE','KPI'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_route_field (
 id BIGINT PRIMARY KEY, route_id BIGINT NOT NULL, physical_name VARCHAR(128) NOT NULL,
 display_name VARCHAR(150) NOT NULL DEFAULT '', data_type VARCHAR(16) NOT NULL, display_order INT NOT NULL DEFAULT 0,
 visible TINYINT(1) NOT NULL DEFAULT 1, fixed_position VARCHAR(8) NOT NULL DEFAULT 'NONE', width INT NULL,
 align_type VARCHAR(8) NOT NULL DEFAULT 'LEFT', format_pattern VARCHAR(100) NULL,
 style_indicator_field VARCHAR(128) NULL, field_role VARCHAR(16) NOT NULL DEFAULT 'VALUE', created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), row_version BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_bi_route_field (route_id,physical_name), KEY idx_bi_route_field_order (route_id,display_order),
 CONSTRAINT fk_bi_field_route FOREIGN KEY (route_id) REFERENCES bi_route(id),
 CONSTRAINT ck_bi_field_type CHECK (data_type IN ('STRING','NUMBER','DATE','DATETIME','BOOLEAN','JSON')),
 CONSTRAINT ck_bi_field_fixed CHECK (fixed_position IN ('NONE','LEFT','RIGHT')),
 CONSTRAINT ck_bi_field_align CHECK (align_type IN ('LEFT','CENTER','RIGHT')),
 CONSTRAINT ck_bi_field_role CHECK (field_role IN ('DIMENSION','MEASURE','VALUE','PAYLOAD','STYLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_drill_edge (
 id BIGINT PRIMARY KEY, component_id BIGINT NOT NULL, source_route_id BIGINT NOT NULL, target_route_id BIGINT NOT NULL,
 trigger_field VARCHAR(128) NOT NULL, payload_field VARCHAR(128) NOT NULL, route_value VARCHAR(64) NOT NULL,
 display_order INT NOT NULL DEFAULT 0, created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), row_version BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_bi_drill_trigger (source_route_id,trigger_field),
 CONSTRAINT fk_bi_edge_component FOREIGN KEY (component_id) REFERENCES bi_component(id),
 CONSTRAINT fk_bi_edge_source FOREIGN KEY (source_route_id) REFERENCES bi_route(id),
 CONSTRAINT fk_bi_edge_target FOREIGN KEY (target_route_id) REFERENCES bi_route(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_sp_param_mapping (
 id BIGINT PRIMARY KEY, report_id BIGINT NOT NULL, component_id BIGINT NULL,
 scope_component_id BIGINT GENERATED ALWAYS AS (IFNULL(component_id,0)) STORED, datasource_id BIGINT NOT NULL,
 procedure_name VARCHAR(128) NOT NULL, signature_hash CHAR(64) NOT NULL, parameter_ordinal INT NOT NULL,
 parameter_name VARCHAR(128) NOT NULL, mysql_data_type VARCHAR(64) NOT NULL, parameter_mode VARCHAR(8) NOT NULL,
 source_type VARCHAR(16) NOT NULL, source_key VARCHAR(128) NULL, constant_value TEXT NULL, created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_bi_sp_param_scope (report_id,scope_component_id,procedure_name,parameter_ordinal),
 KEY idx_bi_sp_param_report (report_id,component_id),
 CONSTRAINT fk_bi_param_report FOREIGN KEY (report_id) REFERENCES bi_report(id),
 CONSTRAINT fk_bi_param_component FOREIGN KEY (component_id) REFERENCES bi_component(id),
 CONSTRAINT fk_bi_param_ds FOREIGN KEY (datasource_id) REFERENCES bi_datasource(id),
 CONSTRAINT ck_bi_param_mode CHECK (parameter_mode='IN'),
 CONSTRAINT ck_bi_param_source CHECK (source_type IN ('SYSTEM','REGION','COMPONENT','CONTROL','DRILL','CONSTANT','NULL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bi_config_version (
 id BIGINT PRIMARY KEY, report_id BIGINT NOT NULL, version_no BIGINT NOT NULL, snapshot_json JSON NOT NULL,
 snapshot_sha256 CHAR(64) NOT NULL, change_summary VARCHAR(500) NULL, operation_type VARCHAR(16) NOT NULL,
 source_version BIGINT NULL, created_by BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_bi_config_version (report_id,version_no), KEY idx_bi_config_version_time (report_id,created_at DESC),
 CONSTRAINT fk_bi_version_report FOREIGN KEY (report_id) REFERENCES bi_report(id),
 CONSTRAINT ck_bi_version_op CHECK (operation_type IN ('CREATE','SAVE','ROLLBACK'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
