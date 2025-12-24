package DAO;

/**
 * ========================================================================================================
 * DAO: UsersDAO - DATA ACCESS OBJECT CHO BẢNG USERS
 * ========================================================================================================
 *
 * CHỨC NĂNG CHÍNH: - Thực hiện các thao tác CRUD với bảng Users - Authenticate:
 * checkLogin() - xác thực email + password - Query: findById(),
 * getUserByEmail(), existsByEmail() - Create: insertUser() - tạo tài khoản mới
 * - Update: updatePasswordByEmail() - đổi mật khẩu
 *
 * DATABASE CONNECTION: - Kết nối SQL Server qua mylib.DBUtils.getConnection() -
 * Sử dụng PreparedStatement để tránh SQL Injection - Auto-close resources với
 * try-with-resources
 *
 * PASSWORD HANDLING: - KHÔNG BAO GIỜ lưu plain password vào database - Hash
 * password bằng PasswordUtils.hashPassword() (SHA-256) - Verify password bằng
 * PasswordUtils.verifyPassword()
 *
 * METHODS: 1. checkLogin(email, password): Xác thực login, trả về Users nếu
 * đúng 2. findById(id): Tìm user theo user_id 3. existsByEmail(email): Kiểm tra
 * email đã tồn tại chưa 4. insertUser(user): Tạo user mới, trả về user_id 5.
 * updatePasswordByEmail(email, password): Đổi mật khẩu 6.
 * getUserByEmail(email): Lấy thông tin user theo email 7. mapRowToUser(rs):
 * Helper map ResultSet -> Users object
 *
 * SECURITY: - Password luôn được hash SHA-256 trước khi lưu - checkLogin()
 * verify hash thay vì so sánh plain text - Nên thêm thêm salt để tăng bảo mật
 * (hiện tại chưa có)
 *
 * SỬ DỤNG: - Controller: LoginController, RegisterVerifyOtpController,
 * ProfileController - Utils: PasswordUtils (hash/verify password) - Config:
 * mylib/DBUtils (database connection)
 */
import DTO.AdminCreateAccountRequest;
import DTO.StaffOrganizerResponse;
import DTO.Users;
import mylib.DBUtils;
import utils.PasswordUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class UsersDAO {

    public Users checkLogin(String email, String rawPassword) {
        if (email == null || rawPassword == null) {
            return null;
        }
        String sql = "SELECT user_id, full_name, email, phone, password_hash, role, status, Wallet, created_at "
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

                    Users user = new Users();
                    user.setId(rs.getInt("user_id"));
                    user.setFullName(rs.getString("full_name"));
                    user.setEmail(rs.getString("email"));
                    user.setPhone(rs.getString("phone"));
                    user.setPasswordHash(dbHash);
                    user.setRole(rs.getString("role"));
                    user.setStatus(rs.getString("status"));
                    user.setWallet(rs.getBigDecimal("Wallet"));
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
        String sql = "SELECT user_id, full_name, email, phone, password_hash, role, status, Wallet, created_at "
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
        String sql = "INSERT INTO Users(full_name, email, phone, password_hash, role, status, Wallet) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setNString(1, u.getFullName());
            ps.setNString(2, u.getEmail());
            ps.setNString(3, u.getPhone());
            ps.setString(4, u.getPasswordHash());

            String role = isBlank(u.getRole()) ? "STUDENT" : u.getRole();
            String status = isBlank(u.getStatus()) ? "ACTIVE" : u.getStatus();

            ps.setNString(5, role);
            ps.setNString(6, status);

            BigDecimal wallet = (u.getWallet() == null) ? BigDecimal.ZERO : u.getWallet();
            ps.setBigDecimal(7, wallet);

            ps.executeUpdate();

            try ( ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
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

        String sql = "SELECT user_id, full_name, email, phone, password_hash, role, status, Wallet, created_at "
                + "FROM Users WHERE email = ?";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setNString(1, email); // Email là NVARCHAR

            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    user = new Users();
                    user.setId(rs.getInt("user_id"));
                    user.setFullName(rs.getNString("full_name"));
                    user.setEmail(rs.getNString("email"));
                    user.setPhone(rs.getString("phone"));
                    user.setPasswordHash(rs.getString("password_hash"));
                    user.setRole(rs.getString("role"));
                    user.setStatus(rs.getString("status"));
                    user.setWallet(rs.getBigDecimal("Wallet"));
                    user.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                }
            }

        } catch (Exception e) {
            System.err.println("[ERROR] getUserByEmail: " + e.getMessage());
            e.printStackTrace();
        }

        return user;
    }

    // 1. Kiểm tra Email đã tồn tại chưa
    public boolean isEmailExists(String email) {
        String sql = "SELECT user_id FROM Users WHERE email = ?";
        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try ( ResultSet rs = ps.executeQuery()) {
                return rs.next(); // Trả về true nếu tìm thấy email
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    // 2. Kiểm tra Số điện thoại đã tồn tại chưa
    public boolean isPhoneExists(String phone) {
        String sql = "SELECT user_id FROM Users WHERE phone = ?";
        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            try ( ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    // 3. Hàm tạo tài khoản chính thức
    public boolean adminCreateAccount(AdminCreateAccountRequest req, String passwordHash) {
        // Wallet mặc định 0.00 và status mặc định ACTIVE theo yêu cầu
        String sql = "INSERT INTO Users (full_name, email, phone, password_hash, role, status, created_at, Wallet) "
                + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', GETDATE(), 0.00)";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, req.getFullName());
            ps.setString(2, req.getEmail());
            ps.setString(3, req.getPhone());
            ps.setString(4, passwordHash);
            ps.setString(5, req.getRole().toUpperCase());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean adminUpdateUserById(int id, String fullName, String phone, String role, String status, String passwordHash) {
        // update động: field nào null thì giữ nguyên
        String sql = "  UPDATE Users\n"
                + "        SET\n"
                + "          full_name = COALESCE(?, full_name),\n"
                + "          phone = COALESCE(?, phone),\n"
                + "          role = COALESCE(?, role),\n"
                + "          status = COALESCE(?, status),\n"
                + "          password_hash = COALESCE(?, password_hash)\n"
                + "        WHERE user_id = ?";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setNString(1, fullName);
            ps.setNString(2, phone);
            ps.setString(3, role);
            ps.setString(4, status);
            ps.setString(5, passwordHash);
            ps.setInt(6, id);

            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean softDeleteUser(int userId) {
        String sql = "UPDATE Users SET status = 'INACTIVE' WHERE user_id = ?";
        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
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
        u.setWallet(rs.getBigDecimal("Wallet"));
        u.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return u;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public StaffOrganizerResponse getStaffAndOrganizer() {
        String sql = "SELECT user_id, full_name, email, phone, role, status, Wallet "
                + "FROM Users "
                + "WHERE status IN (?, ?) AND role IN (?, ?)";

        List<Users> staffList = new ArrayList<>();
        List<Users> organizerList = new ArrayList<>();

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "ACTIVE");
            ps.setString(2, "INACTIVE");
            ps.setString(3, "STAFF");
            ps.setString(4, "ORGANIZER");

            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Users u = new Users();
                    u.setId(rs.getInt("user_id"));
                    u.setFullName(rs.getNString("full_name"));
                    u.setEmail(rs.getNString("email"));
                    u.setPhone(rs.getString("phone"));
                    u.setRole(rs.getString("role"));
                    u.setStatus(rs.getString("status"));
                    u.setWallet(rs.getBigDecimal("Wallet"));

                    if ("STAFF".equalsIgnoreCase(u.getRole())) {
                        staffList.add(u);
                    } else {
                        organizerList.add(u);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return new StaffOrganizerResponse(staffList, organizerList);
    }

}
