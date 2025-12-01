package DAO;

import DTO.Users;
import mylib.DBUtils;
import utils.PasswordUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class UsersDAO {

    public Users checkLogin(String email, String rawPassword) {
        if (email == null || rawPassword == null) {
            return null;
        }

        String sql = "SELECT user_id, full_name, email, phone, password_hash, role, status, created_at " +
                     "FROM Users WHERE email = ?";

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String dbHash = rs.getString("password_hash");

                    // ✅ So sánh mật khẩu nhập vào với hash trong DB
                    boolean matched = PasswordUtils.verifyPassword(rawPassword, dbHash);
                    if (!matched) {
                        return null; // sai mật khẩu
                    }

                    // ✅ Tạo đối tượng Users nếu login đúng
                    Users user = new Users();
                    user.setId(rs.getInt("user_id"));
                    user.setFullName(rs.getString("full_name"));
                    user.setEmail(rs.getString("email"));
                    user.setPhone(rs.getString("phone"));
                    user.setPasswordHash(dbHash); // nếu DTO có field này
                    user.setRole(rs.getString("role"));
                    user.setStatus(rs.getString("status"));
                    user.setCreatedAt(rs.getTimestamp("created_at"));
                    return user;
                } else {
                    // Không tìm thấy email
                    return null;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ========== THÊM CÁC METHOD CHO FORGOT PASSWORD & REGISTER ==========

    /**
     * Tìm user theo email (cho forgot password)
     */
    public Users getUserByEmail(String email) {
        if (email == null) return null;

        String sql = "SELECT user_id, full_name, email, phone, password_hash, role, status, created_at " +
                     "FROM Users WHERE email = ?";

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Users user = new Users();
                    user.setId(rs.getInt("user_id"));
                    user.setFullName(rs.getString("full_name"));
                    user.setEmail(rs.getString("email"));
                    user.setPhone(rs.getString("phone"));
                    user.setPasswordHash(rs.getString("password_hash"));
                    user.setRole(rs.getString("role"));
                    user.setStatus(rs.getString("status"));
                    user.setCreatedAt(rs.getTimestamp("created_at"));
                    return user;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Kiểm tra email đã tồn tại chưa (cho register)
     */
    public boolean existsByEmail(String email) throws Exception {
        if (email == null) return false;

        String sql = "SELECT COUNT(*) FROM Users WHERE email = ?";
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Insert user mới (cho register), trả về user_id
     * @param user Users entity with basic info
     * @param rawPassword Raw password (unhashed) to be hashed before insert
     */
    public int insertUser(Users user, String rawPassword) {
        if (user == null || user.getEmail() == null || rawPassword == null) return -1;

        String sql = "INSERT INTO Users (full_name, email, phone, password_hash, role, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // Hash password trước khi lưu
            String hashedPassword = PasswordUtils.hashPassword(rawPassword);

            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setString(4, hashedPassword);
            ps.setString(5, user.getRole() != null ? user.getRole() : "Student");
            ps.setString(6, user.getStatus() != null ? user.getStatus() : "ACTIVE");

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Tìm user theo ID (sau khi insert)
     */
    public Users findById(int userId) {
        String sql = "SELECT user_id, full_name, email, phone, password_hash, role, status, created_at " +
                     "FROM Users WHERE user_id = ?";

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Users user = new Users();
                    user.setId(rs.getInt("user_id"));
                    user.setFullName(rs.getString("full_name"));
                    user.setEmail(rs.getString("email"));
                    user.setPhone(rs.getString("phone"));
                    user.setPasswordHash(rs.getString("password_hash"));
                    user.setRole(rs.getString("role"));
                    user.setStatus(rs.getString("status"));
                    user.setCreatedAt(rs.getTimestamp("created_at"));
                    return user;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Cập nhật password theo email (cho reset password)
     */
    public boolean updatePasswordByEmail(String email, String newPassword) throws Exception {
        if (email == null || newPassword == null) return false;

        String sql = "UPDATE Users SET password_hash = ? WHERE email = ?";

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            String hashedPassword = PasswordUtils.hashPassword(newPassword);
            ps.setString(1, hashedPassword);
            ps.setString(2, email);

            int affected = ps.executeUpdate();
            return affected > 0;
        }
    }
}
