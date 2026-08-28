CREATE TABLE bi_insight_provider_credential (
 provider VARCHAR(16) PRIMARY KEY,
 api_key_ciphertext TEXT NOT NULL,
 key_suffix VARCHAR(8) NOT NULL,
 credential_version INT NOT NULL DEFAULT 1,
 created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 CONSTRAINT ck_bi_insight_provider CHECK (provider IN ('QWEN','DEEPSEEK'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
