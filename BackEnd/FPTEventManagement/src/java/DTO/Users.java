package DTO;

/**
 * ========================================================================================================
 * DTO: Users - ENTITY ĐẠI DIỆN CHO BẢNG USERS TRONG DATABASE
 * ========================================================================================================
 * 
 * MỤC ĐÍCH:
 * - Class này ánh xạ (mapping) với bảng Users trong SQL Server
 * - Lưu trữ thông tin người dùng: sinh viên, admin, organizer
 * - Sử dụng trong authentication, authorization, và quản lý user
 * 
 * CẤU TRÚC BẢNG DATABASE (Users):
 * - user_id: INT IDENTITY PRIMARY KEY
 * - full_name: NVARCHAR(100) NOT NULL
 * - email: NVARCHAR(255) NOT NULL UNIQUE
 * - phone: NVARCHAR(20) NOT NULL
 * - password_hash: VARCHAR(64) NOT NULL (SHA-256 hash)
 * - role: NVARCHAR(20) NOT NULL CHECK (role IN ('STUDENT', 'ADMIN', 'ORGANIZER'))
 * - status: NVARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'BANNED'))
 * - created_at: DATETIME2 DEFAULT GETDATE()
 * 
 * ROLES:
 * - STUDENT: Sinh viên - đăng ký sự kiện, xem vé
 * - ADMIN: Quản trị viên - quản lý toàn bộ hệ thống
 * - ORGANIZER: Người tổ chức - tạo và quản lý sự kiện
 * 
 * STATUS:
 * - ACTIVE: Tài khoản hoạt động bình thường
 * - INACTIVE: Tài khoản bị vô hiệu hóa tạm thời
 * - BANNED: Tài khoản bị cấm vĩnh viễn
 * 
 * PASSWORD:
 * - KHÔNG BAO GIỜ lưu plain password
 * - Luôn hash bằng SHA-256 trước khi lưu DB
 * - Field passwordHash chứa hash 64 ký tự hex
 * - Verify: hash(input) == passwordHash
 * 
 * LUỒNG DỮ LIỆU:
 * 1. User đăng ký: RegisterVerifyOtpController tạo Users object
 * 2. Hash password: PasswordUtils.hashPassword(plainPassword)
 * 3. Set default: role="STUDENT", status="ACTIVE"
 * 4. Insert DB: UsersDAO.insertUser(users)
 * 5. Sinh JWT: JwtUtils.generateToken(email, role, id)
 * 6. Login: UsersDAO.checkLogin(email, password)
 * 
 * SỬ DỤNG:
 * - DAO: DAO/UsersDAO.java (CRUD operations)
 * - Controller: LoginController, RegisterController, ProfileController
 * - Auth: JWT authentication, role-based access control
 */

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

public class Users {

    private int id;
    private String fullName;
    private String email;
    private String phone;
    private String passwordHash;
    private String role;
    private String status;
    private LocalDateTime createdAt;

    // ==================== Constructor ====================

    public Users() {
    }

    public Users(int id, String fullName, String email, String phone, String passwordHash,
            String role, String status, LocalDateTime createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
    }

    // ==================== Getters & Setters ====================

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // ==================== toString ====================

    @Override
    public String toString() {
        return "Users{" +
                "id=" + id +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", role='" + role + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}