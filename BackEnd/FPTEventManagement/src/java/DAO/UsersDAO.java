package DAO;

import DTO.Users;
import mylib.DBUtils;
import utils.PasswordUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

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
}
