package com.ruoyi.bi.report;

import com.ruoyi.bi.datasource.SubjectType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AclDirectoryService {
    private final JdbcTemplate jdbc;

    public AclDirectoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Option> search(SubjectType type, String keyword) {
        String query = keyword == null ? "" : keyword.trim();
        String like = "%" + query + "%";
        if (type == SubjectType.ROLE) {
            return jdbc.query("""
                SELECT role_id, role_name, role_key FROM sys_role
                WHERE status='0' AND del_flag='0'
                  AND (?='' OR role_name LIKE ? OR role_key LIKE ? OR CAST(role_id AS CHAR) LIKE ?)
                ORDER BY role_sort, role_id LIMIT 100
                """, (rs, rowNum) -> new Option(String.valueOf(rs.getLong(1)), rs.getString(2), rs.getString(3)),
                query, like, like, like);
        }
        return jdbc.query("""
            SELECT user_id, nick_name, user_name FROM sys_user
            WHERE status='0' AND del_flag='0'
              AND (?='' OR nick_name LIKE ? OR user_name LIKE ? OR CAST(user_id AS CHAR) LIKE ?)
            ORDER BY user_id LIMIT 100
            """, (rs, rowNum) -> new Option(String.valueOf(rs.getLong(1)), rs.getString(2), rs.getString(3)),
            query, like, like, like);
    }

    public record Option(String id, String label, String code) {}
}
