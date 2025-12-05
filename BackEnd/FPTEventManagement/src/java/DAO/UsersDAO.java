package DAO;

import DTO.Users;
import mylib.DBUtils;
import utils.PasswordUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class UsersDAO {

    public Users checkLogin(String email, String rawPassword) {
        if (email == null || rawPassword == null) {
            return null;
        }

        String sql = "SELECT user_id, full_name, email, phone, password_hash, role, status, created_at "
                + "FROM Users WHERE email = ?";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            try ( ResultSet rs = ps.executeQuery()) {
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
                    user.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
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

    // =========================
    // TÌM USER THEO ID
    // =========================
    public Users findById(int id) {
        String sql = "SELECT user_id, full_name, email, phone, password_hash, role, status, created_at "
                + "FROM Users WHERE user_id = ?";

        try ( Connection con = DBUtils.getConnection();  PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, id);

            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] findById: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // =========================
    // KIỂM TRA EMAIL TỒN TẠI
    // =========================
    public boolean existsByEmail(String email) {
        String sql = "SELECT user_id FROM Users WHERE email = ?";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setNString(1, email); // email là NVARCHAR
            try ( ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            System.err.println("[ERROR] existsByEmail: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    // =========================
    // TẠO USER MỚI
    // =========================
    /**
     * insertUser: nhận vào Users đã có passwordHash (đã hash trước khi
     * gọi).Role & Status nếu null sẽ set default: - role : STUDENT - status:
     * ACTIVE
     *
     * @param u
     * @return
     */
    public int insertUser(Users u) {
        String sql = "INSERT INTO Users(full_name, email, phone, password_hash, role, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setNString(1, u.getFullName());
            ps.setNString(2, u.getEmail());
            ps.setNString(3, u.getPhone());

            // password_hash: phải là hash SHA-256
            ps.setString(4, u.getPasswordHash());

            String role = isBlank(u.getRole()) ? "STUDENT" : u.getRole();
            String status = isBlank(u.getStatus()) ? "ACTIVE" : u.getStatus();

            ps.setNString(5, role);
            ps.setNString(6, status);

            ps.executeUpdate();

            try ( ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1); // user_id mới
                }
            }

        } catch (Exception e) {
            System.err.println("[ERROR] insertUser: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Cập nhật mật khẩu (hash) theo email.
     *
     * @param email email user
     * @param rawPassword mật khẩu mới dạng plain-text
     * @return 
     */
    public boolean updatePasswordByEmail(String email, String rawPassword) {
        if (email == null || rawPassword == null) {
            return false;
        }

        String sql = "UPDATE Users SET password_hash = ? WHERE email = ?";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            String hash = PasswordUtils.hashPassword(rawPassword);
            ps.setString(1, hash);
            ps.setNString(2, email);

            int rows = ps.executeUpdate();
            return rows > 0;

        } catch (Exception e) {
            System.err.println("[ERROR] updatePasswordByEmail: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    
    public Users getUserByEmail(String email) {
    Users user = null;

    String sql = "SELECT user_id, full_name, email, phone, password_hash, role, status, created_at "
               + "FROM Users WHERE email = ?";

    try (Connection conn = DBUtils.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setNString(1, email);  // Email là NVARCHAR

        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                user = new Users();
                user.setId(rs.getInt("user_id"));
                user.setFullName(rs.getNString("full_name"));
                user.setEmail(rs.getNString("email"));
                user.setPhone(rs.getString("phone"));
                user.setPasswordHash(rs.getString("password_hash"));
                user.setRole(rs.getString("role"));
                user.setStatus(rs.getString("status"));
                user.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            }
        }

    } catch (Exception e) {
        System.err.println("[ERROR] getUserByEmail: " + e.getMessage());
        e.printStackTrace();
    }

    return user;
}

    // =========================
    // HELPER: MAP 1 ROW -> Users DTO
    // =========================
    private Users mapRowToUser(ResultSet rs) throws SQLException {
        Users u = new Users();
        u.setId(rs.getInt("user_id"));
        u.setFullName(rs.getNString("full_name"));
        u.setEmail(rs.getNString("email"));
        u.setPhone(rs.getNString("phone"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(rs.getNString("role"));
        u.setStatus(rs.getNString("status"));
        u.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return u;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

}
