package DAO;

import DTO.Speaker;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

public class SpeakerDAO {

    /**
     * Insert speaker mới, dùng connection đang mở (trong transaction).
     * Trả về speaker_id vừa tạo, hoặc null nếu lỗi.
     */
    public Integer insertSpeaker(Connection conn, Speaker sp) throws SQLException {
        String sql = "INSERT INTO Speaker (full_name, bio, email, phone, avatar_url) "
                   + "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // full_name thường là NOT NULL
            ps.setNString(1, sp.getFullName());

            if (sp.getBio() != null) {
                ps.setNString(2, sp.getBio());
            } else {
                ps.setNull(2, Types.NVARCHAR);
            }

            if (sp.getEmail() != null) {
                ps.setNString(3, sp.getEmail());
            } else {
                ps.setNull(3, Types.NVARCHAR);
            }

            if (sp.getPhone() != null) {
                ps.setNString(4, sp.getPhone());
            } else {
                ps.setNull(4, Types.NVARCHAR);
            }

            if (sp.getAvatarUrl() != null) {
                ps.setNString(5, sp.getAvatarUrl());
            } else {
                ps.setNull(5, Types.NVARCHAR);
            }

            // ❗ QUAN TRỌNG: DÙNG executeUpdate(), KHÔNG PHẢI executeQuery()
            int affected = ps.executeUpdate();
            if (affected == 0) {
                return null;
            }

            // Lấy speaker_id vừa insert
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        return null;
    }
}
