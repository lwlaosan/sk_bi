package com.ruoyi.bi.runtime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class InsightCredentialRepository {
    private final JdbcTemplate jdbc;
    public InsightCredentialRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    Optional<Record> find(String provider){
        return jdbc.query("SELECT provider,api_key_ciphertext,key_suffix,credential_version,updated_at FROM bi_insight_provider_credential WHERE provider=?",
            (rs,row)->new Record(rs.getString(1),rs.getString(2),rs.getString(3),rs.getInt(4),rs.getTimestamp(5).toLocalDateTime()),provider).stream().findFirst();
    }

    void save(String provider,String ciphertext,String suffix,long userId){
        jdbc.update("""
            INSERT INTO bi_insight_provider_credential(provider,api_key_ciphertext,key_suffix,created_by,updated_by)
            VALUES(?,?,?,?,?)
            ON DUPLICATE KEY UPDATE api_key_ciphertext=VALUES(api_key_ciphertext),key_suffix=VALUES(key_suffix),
              credential_version=credential_version+1,updated_by=VALUES(updated_by)
            """,provider,ciphertext,suffix,userId,userId);
    }

    void delete(String provider){jdbc.update("DELETE FROM bi_insight_provider_credential WHERE provider=?",provider);}
    record Record(String provider,String ciphertext,String suffix,int version,LocalDateTime updatedAt){}
}
