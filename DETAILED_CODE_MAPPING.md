# 📚 HƯỚNG DẪN CHI TIẾT - CẤU TRÚC CODE VÀ MAPPING DATABASE

> **Tài liệu này giải thích chi tiết luồng xử lý, kết nối file và mapping database cho tất cả các chức năng trong hệ thống FPT Event Management**

---

## 📋 MỤC LỤC

1. [Ticket Trading History (Lịch sử vé)](#1-ticket-trading-history)
2. [Register (Đăng ký tài khoản)](#2-register)
3. [Verify OTP (Xác thực OTP)](#3-verify-otp)
4. [Reset Password API (Đặt lại mật khẩu)](#4-reset-password-api)
5. [reCAPTCHA Login (Đăng nhập)](#5-recaptcha-login)
6. [Event Statistics (Thống kê sự kiện)](#6-event-statistics)
7. [Ticket List (Danh sách vé)](#7-ticket-list)
8. [Student Bill History (Lịch sử hóa đơn)](#8-student-bill-history)
9. [CRUD Venue (Quản lý địa điểm)](#9-crud-venue)
10. [Venue-Area (Quản lý khu vực)](#10-venue-area)
11. [Admin CRUD Account (Quản lý tài khoản)](#11-admin-crud-account)

---

## 1. TICKET TRADING HISTORY

### 📝 Mô tả
Chức năng hiển thị lịch sử tất cả các vé mà sinh viên đã mua/đăng ký tham gia sự kiện.

### 🔗 Luồng xử lý từ FE → BE → Database

```
1. Frontend (React/Vue)
   ↓
   📤 GET /api/registrations/my-tickets
   📤 Header: Authorization: Bearer <JWT_TOKEN>
   ↓

2. Jakarta Servlet Container
   ↓
   🔒 filter/JwtAuthFilter.java (doFilter)
      - Lấy token từ header: Authorization
      - Validate token: JwtUtils.validateToken(token)
      - Giải mã token: JwtUtils.parseToken(token)
      - Lấy userId từ claims: token.getClaim("userId")
      - Đặt vào request: request.setAttribute("userId", userId)
   ↓

3. controller/MyTicketController.java (doGet)
      - Lấy userId: request.getAttribute("userId")
      - Kiểm tra userId != null (không cho vào nếu null)
      - Gọi DAO: ticketDAO.getTicketsByUserId(userId)
   ↓

4. DAO/TicketDAO.java (getTicketsByUserId)
      - Kết nối DB: DBUtils.getConnection()
      - SQL Query với nhiều JOIN:
   
      SELECT 
          t.ticket_id,           -- ID vé
          e.title,               -- Tên sự kiện
          ct.name,               -- Loại vé (VIP, Regular...)
          s.seat_code,           -- Mã ghế (A-01, B-12...)
          t.status,              -- Trạng thái (BOOKED, CHECKED_IN...)
          t.qr_code_value,       -- Mã QR (base64 image)
          e.start_time,          -- Thời gian bắt đầu
          v.venue_name,          -- Tên địa điểm
          va.area_name           -- Tên khu vực
      FROM Ticket t
      LEFT JOIN Event e ON t.event_id = e.event_id
      LEFT JOIN CategoryTicket ct ON t.category_ticket_id = ct.category_ticket_id
      LEFT JOIN Seat s ON t.seat_id = s.seat_id
      LEFT JOIN VenueArea va ON s.area_id = va.area_id
      LEFT JOIN Venue v ON va.venue_id = v.venue_id
      WHERE t.user_id = ?
      ORDER BY e.start_time DESC
   ↓

5. Database - SQL Server
      📊 Bảng Ticket (chính):
         - ticket_id (PK) - ID vé
         - user_id (FK → Users) - Người sở hữu vé
         - event_id (FK → Event) - Sự kiện
         - category_ticket_id (FK → CategoryTicket) - Loại vé
         - seat_id (FK → Seat) - Ghế ngồi
         - status (VARCHAR) - Trạng thái vé
         - qr_code_value (VARCHAR) - Mã QR code
         - created_at (DATETIME) - Thời gian mua
   
      📊 Bảng Event:
         - event_id (PK)
         - title (VARCHAR) - Tên sự kiện
         - start_time (DATETIME) - Thời gian bắt đầu
   
      📊 Bảng CategoryTicket:
         - category_ticket_id (PK)
         - name (VARCHAR) - VIP, Standard, Free
         - price (DECIMAL) - Giá vé
   
      📊 Bảng Seat:
         - seat_id (PK)
         - seat_code (VARCHAR) - A-01, B-12...
         - area_id (FK → VenueArea)
   
      📊 Bảng VenueArea:
         - area_id (PK)
         - area_name (VARCHAR) - Hall A, Room 101
         - venue_id (FK → Venue)
   
      📊 Bảng Venue:
         - venue_id (PK)
         - venue_name (VARCHAR) - FPT Hòa Lạc
   ↓

6. DAO Convert ResultSet → DTO
      - Tạo List<MyTicketResponse>
      - Mỗi row -> 1 MyTicketResponse object
      - Set các field từ ResultSet
   ↓

7. Controller Serialize Response
      - Gson convert List → JSON string
      - Trả về JSON qua PrintWriter
   ↓

8. Frontend nhận JSON
      - Parse JSON → Array of objects
      - Hiển thị danh sách vé trong UI
      - Render QR code từ base64 string
```

### 🗂️ Mapping File

```
controller/MyTicketController.java         // Controller xử lý request
    ↓ uses
DAO/TicketDAO.java                         // Truy vấn database
    ↓ uses
mylib/DBUtils.java                         // Kết nối SQL Server
    ↓ connects to
SQL Server Database                        // Lưu trữ dữ liệu
    
filter/JwtAuthFilter.java                  // Authentication middleware
    ↓ uses
utils/JwtUtils.java                        // Xử lý JWT token

DTO/MyTicketResponse.java                  // Cấu trúc response
```

### 🔐 Security

- **Authentication**: JWT token bắt buộc
- **Authorization**: User chỉ xem được vé của chính mình
- **SQL Injection**: PreparedStatement với parameterized query
- **CORS**: Whitelist origins được config

---

## 2. REGISTER

### 📝 Mô tả
Chức năng đăng ký tài khoản mới cho user (role STUDENT mặc định).

### 🔗 Luồng xử lý từ FE → BE → Database

```
1. Frontend
   ↓
   📤 POST /api/register
   📤 Body (JSON):
      {
        "fullName": "Nguyễn Văn A",
        "email": "a@fpt.edu.vn",
        "phone": "0901234567",
        "password": "Pass123",
        "recaptchaToken": "03AGdBq27..."
      }
   ↓

2. controller/registerController.java (doPost)
   
   ✅ Bước 1: Parse JSON body
      - Gson.fromJson(reader, RegisterRequest.class)
      - Kiểm tra input != null
   
   ✅ Bước 2: Verify reCAPTCHA
      - RecaptchaUtils.verify(recaptchaToken)
      - Gọi Google reCAPTCHA API:
        POST https://www.google.com/recaptcha/api/siteverify
        Body: secret=<SECRET_KEY>&response=<recaptchaToken>
      - Google trả về: { "success": true/false }
   
   ✅ Bước 3: Validate các field
      - ValidationUtil.isValidFullName(fullName)
        Regex: ^[a-zA-ZÀ-ỹ\s]{2,100}$
      
      - ValidationUtil.isValidVNPhone(phone)
        Regex: ^0\d{9,10}$
      
      - ValidationUtil.isValidEmail(email)
        Regex: ^[a-zA-Z0-9._%+-]+@fpt\.edu\.vn$
      
      - ValidationUtil.isValidPassword(password)
        Check: length >= 6 && hasLetters && hasDigits
   
   ✅ Bước 4: Kiểm tra email trùng
      - UsersDAO.existsByEmail(email)
      
      SQL Query:
      SELECT COUNT(*) FROM Users WHERE email = ?
      
      - Return true nếu COUNT > 0
      - Trả lỗi 409 Conflict nếu email đã tồn tại
   
   ✅ Bước 5: Tạo Users object
      - new Users()
      - Set: fullName, email, phone
      - Set: role = "STUDENT" (mặc định)
      - Set: status = "ACTIVE" (mặc định)
   
   ✅ Bước 6: Hash password
      - PasswordUtils.hashPassword(password)
      - Dùng MessageDigest SHA-256:
        1. MessageDigest.getInstance("SHA-256")
        2. digest(password.getBytes())
        3. Convert byte[] → hex string (64 ký tự)
      
      Ví dụ:
      Input:  "Pass123"
      Output: "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92"
   
   ✅ Bước 7: Insert vào database
      - UsersDAO.insertUser(user)
      
      SQL Query:
      INSERT INTO Users (
          full_name, 
          email, 
          phone, 
          password_hash, 
          role, 
          status, 
          created_at
      ) VALUES (?, ?, ?, ?, ?, ?, GETDATE())
      
      - Return: user_id (auto-increment)
   
   ✅ Bước 8: Lấy thông tin user vừa tạo
      - UsersDAO.findById(userId)
      
      SQL Query:
      SELECT user_id, full_name, email, phone, role, 
             status, avatar, created_at
      FROM Users
      WHERE user_id = ?
   
   ✅ Bước 9: Generate JWT token
      - JwtUtils.generateToken(email, role, userId)
      
      JWT Structure:
      Header: {
        "alg": "HS256",
        "typ": "JWT"
      }
      
      Payload (Claims): {
        "userId": 123,
        "email": "a@fpt.edu.vn",
        "role": "STUDENT",
        "iat": 1704067200,      // Issued at
        "exp": 1704672000       // Expiration (7 ngày)
      }
      
      Signature:
      HMACSHA256(
        base64UrlEncode(header) + "." + base64UrlEncode(payload),
        SECRET_KEY
      )
   
   ✅ Bước 10: Trả response
      - Status: 200 OK
      - Body: {
          "status": "success",
          "message": "Registered and logged in successfully",
          "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
          "user": { ... }
        }
   ↓

3. Frontend nhận response
   - Parse JSON
   - Lưu token vào localStorage: localStorage.setItem('token', token)
   - Lưu user info vào state/context
   - Redirect về trang chủ (user đã login tự động)
```

### 🗂️ Database Schema

```sql
-- Bảng Users (lưu thông tin user)
CREATE TABLE Users (
    user_id INT PRIMARY KEY IDENTITY(1,1),  -- Auto-increment
    full_name NVARCHAR(100) NOT NULL,       -- Họ tên
    email VARCHAR(100) UNIQUE NOT NULL,     -- Email (unique)
    phone VARCHAR(15) UNIQUE,               -- SĐT (unique)
    password_hash VARCHAR(255) NOT NULL,    -- Password đã hash SHA-256
    role VARCHAR(20) NOT NULL,              -- STUDENT, ORGANIZER, STAFF, ADMIN
    status VARCHAR(20) NOT NULL,            -- ACTIVE, INACTIVE, BLOCKED
    avatar VARCHAR(500),                    -- URL avatar
    created_at DATETIME DEFAULT GETDATE(),  -- Thời gian tạo
    updated_at DATETIME                     -- Thời gian cập nhật
);

-- Index để tăng tốc query
CREATE INDEX idx_users_email ON Users(email);
CREATE INDEX idx_users_role ON Users(role);
CREATE INDEX idx_users_status ON Users(status);
```

### 🗂️ Mapping File

```
controller/registerController.java         // Controller xử lý register
    ↓ uses
utils/RecaptchaUtils.java                 // Verify reCAPTCHA với Google
    ↓ calls
Google reCAPTCHA API                      // siteverify endpoint

controller/registerController.java
    ↓ uses
mylib/ValidationUtil.java                 // Validate input (email, phone, password)
    
controller/registerController.java
    ↓ uses
DAO/UsersDAO.java                         // Database operations
    ↓ uses
mylib/DBUtils.java                        // Connection pool
    ↓ connects to
SQL Server Database                       // Lưu Users

controller/registerController.java
    ↓ uses
utils/PasswordUtils.java                  // Hash password SHA-256

controller/registerController.java
    ↓ uses
utils/JwtUtils.java                       // Generate JWT token
    ↓ uses
utils/JwtConfig.java                      // JWT config (SECRET_KEY, expiration)

DTO/RegisterRequest.java                  // Request body structure
DTO/Users.java                            // User entity
```

### 🔐 Security Measures

1. **reCAPTCHA v2/v3**: Chống bot đăng ký spam
2. **Password Hashing**: SHA-256 (nên nâng cấp lên BCrypt/Argon2)
3. **Email Validation**: Chỉ cho phép @fpt.edu.vn
4. **Unique Constraints**: Email và phone phải unique trong DB
5. **JWT Token**: 7 ngày expiration, signed với SECRET_KEY
6. **CORS**: Whitelist origins để tránh CSRF

---

## 3. VERIFY OTP

### 📝 Mô tả
Xác thực mã OTP khi đăng ký tài khoản mới (phương pháp 2-step verification).

### 🔗 Luồng xử lý chi tiết

```
1. Workflow tổng quan:
   
   [RegisterSendOtpController]
       ↓ (gửi OTP qua email)
   [User nhập OTP]
       ↓ (submit form)
   [RegisterVerifyOtpController] ← ĐÂY LÀ FILE NÀY
       ↓ (verify + tạo user)
   [User login tự động]

2. Frontend
   ↓
   📤 POST /api/register/verify-otp
   📤 Body: {
        "email": "a@fpt.edu.vn",
        "otp": "123456"
      }
   ↓

3. controller/RegisterVerifyOtpController.java (doPost)
   
   ✅ Bước 1: Parse request
      - Gson.fromJson(reader, VerifyRequest.class)
      - Validate email và otp không null
   
   ✅ Bước 2: Lấy PendingUser từ cache
      - OtpCache.get(email)
      
      OtpCache structure (In-memory HashMap):
      Map<String, PendingUser> cache = {
        "a@fpt.edu.vn": {
          fullName: "Nguyễn Văn A",
          email: "a@fpt.edu.vn",
          phone: "0901234567",
          password: "hashed_password",
          otp: "123456",
          createdAt: 1704067200000,  // timestamp
          attempts: 0                 // số lần nhập sai
        }
      }
      
      - Return null nếu không tìm thấy email
      - Lưu ý: Cache này chỉ tồn tại trong memory (mất khi restart server)
   
   ✅ Bước 3: Kiểm tra OTP hết hạn
      - OtpCache.isExpired(pendingUser)
      
      Logic:
      long now = System.currentTimeMillis();
      long created = pendingUser.createdAt;
      long TTL = 5 * 60 * 1000; // 5 phút
      
      return (now - created) > TTL;
      
      - Nếu expired: Xóa khỏi cache, trả lỗi 400
   
   ✅ Bước 4: Kiểm tra số lần nhập sai
      - OtpCache.canAttempt(pendingUser)
      
      Logic:
      int MAX_ATTEMPTS = 5;
      return pendingUser.attempts < MAX_ATTEMPTS;
      
      - Nếu >= 5 lần: Xóa khỏi cache, trả lỗi 429 Too Many Requests
   
   ✅ Bước 5: Verify OTP
      - So sánh: pendingUser.otp.equals(inputOtp)
      
      - Nếu ĐÚNG:
        → Tiếp tục bước 6
      
      - Nếu SAI:
        → OtpCache.incAttempt(pendingUser)
        → pendingUser.attempts++
        → Trả lỗi 400 "OTP is incorrect"
        → User có thể nhập lại (còn attempts)
   
   ✅ Bước 6: Double-check email tồn tại
      - UsersDAO.existsByEmail(email)
      
      - Trường hợp race condition:
        User A nhập OTP → tạo user thành công
        User A (tab khác) nhập OTP lại → bị lỗi 409
      
      - Xóa OTP khỏi cache nếu email đã tồn tại
   
   ✅ Bước 7: Tạo Users entity từ PendingUser
      - pendingUser.toUsersEntity()
      
      Logic:
      Users u = new Users();
      u.setFullName(this.fullName);
      u.setEmail(this.email);
      u.setPhone(this.phone);
      u.setPasswordHash(this.password); // Đã hash từ trước
      u.setRole("STUDENT");
      u.setStatus("ACTIVE");
      return u;
   
   ✅ Bước 8: Insert vào database
      - UsersDAO.insertUser(user)
      
      SQL:
      INSERT INTO Users (full_name, email, phone, password_hash, 
                        role, status, created_at)
      VALUES (?, ?, ?, ?, ?, ?, GETDATE())
      
      - Return: newUserId
   
   ✅ Bước 9: Lấy user vừa tạo
      - UsersDAO.findById(newUserId)
   
   ✅ Bước 10: Xóa OTP khỏi cache
      - OtpCache.remove(email)
      
      Lý do: OTP chỉ dùng 1 lần (one-time password)
   
   ✅ Bước 11: Generate JWT token
      - JwtUtils.generateToken(email, role, userId)
   
   ✅ Bước 12: Trả response
      - Status: 200 OK
      - Body: {
          "status": "success",
          "message": "Registered and logged in successfully",
          "token": "...",
          "user": { ... }
        }
```

### 🗂️ OTP Cache Structure

```java
// mylib/OtpCache.java

public class OtpCache {
    // In-memory cache (mất khi restart server)
    private static final Map<String, PendingUser> cache = 
        new ConcurrentHashMap<>();
    
    // TTL cho OTP (5 phút)
    private static final long OTP_TTL = 5 * 60 * 1000;
    
    // Số lần nhập sai tối đa
    private static final int MAX_ATTEMPTS = 5;
    
    // Inner class: Lưu thông tin user tạm thời
    public static class PendingUser {
        String fullName;
        String email;
        String phone;
        String password;     // Đã hash SHA-256
        String otp;          // Mã OTP 6 số
        long createdAt;      // Timestamp tạo OTP
        int attempts;        // Số lần nhập sai
        
        // Convert sang Users entity để insert DB
        public Users toUsersEntity() {
            Users u = new Users();
            u.setFullName(this.fullName);
            u.setEmail(this.email);
            u.setPhone(this.phone);
            u.setPasswordHash(this.password);
            u.setRole("STUDENT");
            u.setStatus("ACTIVE");
            return u;
        }
    }
    
    // Lưu PendingUser vào cache
    public static void put(String email, PendingUser user) {
        cache.put(email, user);
    }
    
    // Lấy PendingUser từ cache
    public static PendingUser get(String email) {
        return cache.get(email);
    }
    
    // Xóa PendingUser khỏi cache
    public static void remove(String email) {
        cache.remove(email);
    }
    
    // Kiểm tra OTP hết hạn
    public static boolean isExpired(PendingUser user) {
        long now = System.currentTimeMillis();
        return (now - user.createdAt) > OTP_TTL;
    }
    
    // Kiểm tra còn được nhập OTP không
    public static boolean canAttempt(PendingUser user) {
        return user.attempts < MAX_ATTEMPTS;
    }
    
    // Tăng số lần nhập sai
    public static void incAttempt(PendingUser user) {
        user.attempts++;
    }
}
```

### 🗂️ Mapping File

```
Previous step:
controller/RegisterSendOtpController.java  // Gửi OTP qua email
    ↓ saves to
mylib/OtpCache.java                       // In-memory cache
    
Current step:
controller/RegisterVerifyOtpController.java // Verify OTP
    ↓ reads from
mylib/OtpCache.java                       // Lấy PendingUser
    ↓ uses
DAO/UsersDAO.java                         // Tạo user mới
    ↓ inserts to
SQL Server Database (Users table)         // Lưu user

utils/JwtUtils.java                       // Generate token
DTO/Users.java                            // User entity
```

### 🔐 Security Features

1. **OTP TTL**: 5 phút hết hạn
2. **Max Attempts**: 5 lần nhập sai
3. **One-time Use**: OTP bị xóa sau khi dùng
4. **Race Condition**: Double-check email tồn tại
5. **In-memory**: OTP không lưu database (bảo mật cao hơn)

### ⚠️ Limitations

- **In-memory cache**: Mất OTP khi restart server
  → Nên nâng cấp lên Redis cache cho production
  
- **No clustering support**: Không hoạt động với nhiều server instance
  → Redis giải quyết được vấn đề này

---

## 4. RESET PASSWORD API

### 📝 Mô tả
Đặt lại mật khẩu khi user quên mật khẩu (2-step: gửi OTP → verify + đổi mật khẩu).

### 🔗 Luồng xử lý chi tiết

```
1. Workflow tổng quan:
   
   [ForgotPasswordJwtController]
       ↓ (gửi OTP qua email)
   [User nhận email, lấy OTP]
       ↓ (nhập OTP + mật khẩu mới)
   [ResetPasswordJwtController] ← ĐÂY LÀ FILE NÀY
       ↓ (verify OTP + update password)
   [User login với mật khẩu mới]

2. Frontend
   ↓
   📤 POST /api/reset-password
   📤 Body: {
        "email": "a@fpt.edu.vn",
        "otp": "123456",
        "newPassword": "NewPass123"
      }
   ↓

3. controller/ResetPasswordJwtController.java (doPost)
   
   ✅ Bước 1: Parse request body
      - Gson.fromJson(sb.toString(), Req.class)
      - Validate: email, otp, newPassword không rỗng
   
   ✅ Bước 2: Validate mật khẩu mới
      - Check: newPassword.length() >= 6
      
      Nên nâng cấp validation:
      - ValidationUtil.isValidPassword(newPassword)
      - Check: chữ hoa, chữ thường, số, ký tự đặc biệt
   
   ✅ Bước 3: Kiểm tra email tồn tại
      - UsersDAO.getUserByEmail(email)
      
      SQL:
      SELECT * FROM Users WHERE email = ?
      
      - Return null → 404 Not Found
      - Return Users object → Tiếp tục
   
   ✅ Bước 4: Verify OTP
      - PasswordResetManager.verifyOtp(email, otp)
      
      PasswordResetManager structure (In-memory HashMap):
      Map<String, ResetRequest> resetRequests = {
        "a@fpt.edu.vn": {
          otp: "123456",
          createdAt: 1704067200000,
          attempts: 0,
          used: false
        }
      }
      
      Logic verify:
      
      a) Kiểm tra email có trong manager không
         - Không có → return false (OTP không tồn tại)
      
      b) Kiểm tra OTP hết hạn (5 phút)
         long TTL = 5 * 60 * 1000;
         if (now - createdAt > TTL) return false;
      
      c) Kiểm tra OTP đã dùng chưa
         if (used) return false;
      
      d) Kiểm tra số lần nhập sai (< 5)
         if (attempts >= 5) return false;
      
      e) So sánh OTP
         if (!storedOtp.equals(inputOtp)) {
           attempts++;
           return false;
         }
      
      f) OTP đúng → Đánh dấu đã dùng
         used = true;
         return true;
      
      - Return false → 401 Unauthorized
   
   ✅ Bước 5: Cập nhật mật khẩu
      - UsersDAO.updatePasswordByEmail(email, newPassword)
      
      SQL:
      UPDATE Users
      SET password_hash = ?,      -- Mật khẩu mới đã hash
          updated_at = GETDATE()
      WHERE email = ?
      
      DAO tự động hash password:
      String hashedPassword = PasswordUtils.hashPassword(newPassword);
      
      - Return boolean: true = success, false = failed
   
   ✅ Bước 6: Vô hiệu hóa OTP
      - PasswordResetManager.invalidate(email)
      
      Logic:
      resetRequests.remove(email);
      
      Lý do: OTP one-time use, không cho dùng lại
   
   ✅ Bước 7: Trả response
      - Status: 200 OK
      - Body: {
          "status": "success",
          "message": "Đổi mật khẩu thành công"
        }
   ↓

4. Frontend
   - Hiển thị thông báo thành công
   - Redirect về trang login
   - User login với mật khẩu mới
```

### 🗂️ PasswordResetManager Structure

```java
// utils/PasswordResetManager.java

public class PasswordResetManager {
    // In-memory storage cho reset requests
    private static final Map<String, ResetRequest> resetRequests = 
        new ConcurrentHashMap<>();
    
    // TTL cho OTP (5 phút)
    private static final long OTP_TTL = 5 * 60 * 1000;
    
    // Max attempts
    private static final int MAX_ATTEMPTS = 5;
    
    // Inner class: Thông tin reset password
    private static class ResetRequest {
        String otp;          // Mã OTP 6 số
        long createdAt;      // Timestamp tạo OTP
        int attempts;        // Số lần nhập sai
        boolean used;        // Đã dùng chưa
    }
    
    // Tạo OTP mới cho email
    public static String generateOtp(String email) {
        // Generate OTP 6 số ngẫu nhiên
        String otp = String.format("%06d", 
            new Random().nextInt(1000000));
        
        // Tạo reset request
        ResetRequest req = new ResetRequest();
        req.otp = otp;
        req.createdAt = System.currentTimeMillis();
        req.attempts = 0;
        req.used = false;
        
        // Lưu vào map
        resetRequests.put(email, req);
        
        return otp;
    }
    
    // Verify OTP
    public static boolean verifyOtp(String email, String otp) {
        ResetRequest req = resetRequests.get(email);
        
        // Không tìm thấy email
        if (req == null) return false;
        
        // Kiểm tra hết hạn
        long now = System.currentTimeMillis();
        if (now - req.createdAt > OTP_TTL) {
            resetRequests.remove(email);
            return false;
        }
        
        // Kiểm tra đã dùng
        if (req.used) return false;
        
        // Kiểm tra max attempts
        if (req.attempts >= MAX_ATTEMPTS) {
            resetRequests.remove(email);
            return false;
        }
        
        // Verify OTP
        if (!req.otp.equals(otp)) {
            req.attempts++;
            return false;
        }
        
        // OTP đúng → đánh dấu đã dùng
        req.used = true;
        return true;
    }
    
    // Vô hiệu hóa OTP
    public static void invalidate(String email) {
        resetRequests.remove(email);
    }
}
```

### 🗂️ Database Update

```sql
-- Cập nhật mật khẩu trong bảng Users
UPDATE Users
SET password_hash = ?,           -- Mật khẩu mới (đã hash SHA-256)
    updated_at = GETDATE()       -- Timestamp cập nhật
WHERE email = ?

-- Password hash format
-- Input:  "NewPass123"
-- Hash:   SHA-256
-- Output: "8d969eef6ecad3c29a3a629280e686cf..."
--         (64 ký tự hex string)
```

### 🗂️ Mapping File

```
Step 1 (Previous):
controller/ForgotPasswordJwtController.java  // Gửi OTP
    ↓ uses
utils/PasswordResetManager.java            // Generate OTP
    ↓ saves to
In-memory HashMap                           // Lưu OTP tạm
    ↓ uses
mylib/EmailService.java                     // Gửi email

Step 2 (Current):
controller/ResetPasswordJwtController.java  // Verify OTP + Update password
    ↓ uses
utils/PasswordResetManager.java            // Verify OTP
    ↓ uses
DAO/UsersDAO.java                          // Update password
    ↓ uses
utils/PasswordUtils.java                   // Hash password mới
    ↓ updates
SQL Server Database (Users table)          // Lưu password mới
```

### 🔐 Security Features

1. **OTP One-time Use**: Sau khi verify thành công, OTP bị vô hiệu hóa
2. **OTP TTL**: 5 phút hết hạn
3. **Max Attempts**: 5 lần nhập sai
4. **Password Hashing**: SHA-256 (nên nâng cấp BCrypt)
5. **No Link Reset**: Không gửi link trong email (tránh phishing)

### ⚠️ Lưu ý

- **Race Condition**: Nếu user submit nhiều lần cùng lúc, chỉ lần đầu thành công
- **In-memory Storage**: Mất OTP khi restart server → Nên dùng Redis
- **Rate Limiting**: Nên thêm để tránh brute-force OTP

---

## 5. reCAPTCHA LOGIN

### 📝 Mô tả
Đăng nhập với xác thực reCAPTCHA để chống bot và brute-force attack.

### 🔗 Luồng xử lý chi tiết

```
1. Frontend
   ↓
   📤 Tích hợp reCAPTCHA widget (Google)
   
   HTML:
   <script src="https://www.google.com/recaptcha/api.js"></script>
   <div class="g-recaptcha" data-sitekey="YOUR_SITE_KEY"></div>
   
   hoặc (reCAPTCHA v3 - invisible):
   grecaptcha.ready(() => {
     grecaptcha.execute('SITE_KEY', {action: 'login'})
       .then((token) => {
         // token là recaptchaToken
       });
   });
   ↓

2. User nhập email + password + solve reCAPTCHA
   ↓

3. Frontend gửi request
   📤 POST /api/login
   📤 Body: {
        "email": "a@fpt.edu.vn",
        "password": "Pass123",
        "recaptchaToken": "03AGdBq27..."
      }
   ↓

4. controller/loginController.java (doPost)
   
   ✅ Bước 1: Parse request body
      - Gson.fromJson(reader, LoginRequest.class)
      - Validate: email và password không rỗng
   
   ✅ Bước 2: Verify reCAPTCHA
      - RecaptchaUtils.verify(recaptchaToken)
      
      Flow trong RecaptchaUtils:
      
      a) Gọi Google reCAPTCHA API:
         POST https://www.google.com/recaptcha/api/siteverify
         
         Body (form-urlencoded):
         secret=YOUR_SECRET_KEY&
         response=recaptchaToken
      
      b) Google trả về JSON:
         {
           "success": true/false,
           "challenge_ts": "2025-01-01T12:00:00Z",
           "hostname": "localhost",
           "score": 0.9,        // reCAPTCHA v3 only
           "action": "login"    // reCAPTCHA v3 only
         }
      
      c) Parse response và kiểm tra:
         - success == true
         - score >= 0.5 (nếu dùng v3)
      
      d) Return: boolean (true = verified, false = failed)
      
      - Nếu return false → 403 Forbidden
   
   ✅ Bước 3: Kiểm tra login credentials
      - UsersDAO.checkLogin(email, password)
      
      Logic trong DAO:
      
      a) Query user từ database:
         SELECT user_id, full_name, email, phone, role, 
                status, avatar, password_hash
         FROM Users
         WHERE email = ?
      
      b) Kiểm tra user tồn tại
         if (user == null) return null;
      
      c) Verify password:
         String inputHash = PasswordUtils.hashPassword(inputPassword);
         if (!user.getPasswordHash().equals(inputHash)) {
           return null;  // Password sai
         }
      
      d) Return: Users object (hoặc null nếu login failed)
      
      - Nếu return null → 401 Unauthorized
   
   ✅ Bước 4: Kiểm tra user status
      - user.getStatus().equalsIgnoreCase("INACTIVE")
      
      Status trong DB:
      - ACTIVE: User bình thường, cho phép login
      - INACTIVE: User bị khóa (admin khóa)
      - BLOCKED: User vi phạm (tương tự INACTIVE)
      
      - Nếu INACTIVE/BLOCKED → 403 Forbidden
   
   ✅ Bước 5: Generate JWT token
      - JwtUtils.generateToken(email, role, userId)
      
      JWT Structure:
      Header: {
        "alg": "HS256",
        "typ": "JWT"
      }
      
      Payload: {
        "userId": 123,
        "email": "a@fpt.edu.vn",
        "role": "STUDENT",
        "iat": 1704067200,
        "exp": 1704672000    // 7 ngày sau
      }
      
      Signature:
      HMACSHA256(
        base64UrlEncode(header) + "." + base64UrlEncode(payload),
        SECRET_KEY
      )
      
      Output: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
   
   ✅ Bước 6: Trả response
      - Status: 200 OK
      - Body: {
          "status": "success",
          "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
          "user": {
            "userId": 123,
            "email": "a@fpt.edu.vn",
            "fullName": "Nguyễn Văn A",
            "role": "ORGANIZER",
            "phone": "0901234567",
            "status": "ACTIVE",
            "avatar": "https://..."
          }
        }
   ↓

5. Frontend
   - Lưu token: localStorage.setItem('token', response.token)
   - Lưu user info vào state/context
   - Redirect về trang chủ hoặc dashboard
   - Gọi API khác với header: Authorization: Bearer <token>
```

### 🗂️ reCAPTCHA Integration

```javascript
// Frontend - reCAPTCHA v2 (Checkbox)
<script src="https://www.google.com/recaptcha/api.js"></script>

<form onSubmit={handleLogin}>
  <input type="email" name="email" />
  <input type="password" name="password" />
  
  <!-- reCAPTCHA widget -->
  <div class="g-recaptcha" 
       data-sitekey="6LcXXXXXXXXXXXXXXXXXXXXXXXXXXXXX">
  </div>
  
  <button type="submit">Login</button>
</form>

<script>
function handleLogin(e) {
  e.preventDefault();
  
  const email = form.email.value;
  const password = form.password.value;
  const recaptchaToken = grecaptcha.getResponse();
  
  if (!recaptchaToken) {
    alert('Please complete the reCAPTCHA');
    return;
  }
  
  // Gửi request
  fetch('/api/login', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({email, password, recaptchaToken})
  })
  .then(res => res.json())
  .then(data => {
    if (data.token) {
      localStorage.setItem('token', data.token);
      window.location.href = '/dashboard';
    }
  });
}
</script>
```

```javascript
// Frontend - reCAPTCHA v3 (Invisible)
<script src="https://www.google.com/recaptcha/api.js?render=YOUR_SITE_KEY"></script>

<script>
async function handleLogin(email, password) {
  // Execute reCAPTCHA v3
  const recaptchaToken = await grecaptcha.execute(
    'YOUR_SITE_KEY', 
    {action: 'login'}
  );
  
  // Gửi request
  const response = await fetch('/api/login', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({
      email,
      password,
      recaptchaToken
    })
  });
  
  const data = await response.json();
  
  if (data.token) {
    localStorage.setItem('token', data.token);
    window.location.href = '/dashboard';
  }
}
</script>
```

### 🗂️ Mapping File

```
Frontend
    ↓ integrates
Google reCAPTCHA Widget (JavaScript)
    ↓ returns
recaptchaToken (string)
    ↓ sends to
controller/loginController.java
    ↓ verifies with
utils/RecaptchaUtils.java
    ↓ calls
Google reCAPTCHA API (siteverify)
    ↓ returns
{success: true/false, score: 0.9}

controller/loginController.java
    ↓ validates credentials
DAO/UsersDAO.java (checkLogin)
    ↓ queries
SQL Server Database (Users table)
    ↓ verifies password
utils/PasswordUtils.java (hashPassword + compare)

controller/loginController.java
    ↓ generates token
utils/JwtUtils.java
    ↓ uses config
utils/JwtConfig.java (SECRET_KEY)

DTO/LoginRequest.java    // Request body
DTO/Users.java           // User entity
```

### 🔐 Security Features

1. **reCAPTCHA v2**: Checkbox challenge, dễ implement
2. **reCAPTCHA v3**: Invisible, score-based (0.0-1.0)
3. **Password Hashing**: SHA-256 compare
4. **Status Check**: Không cho INACTIVE/BLOCKED user login
5. **JWT Token**: 7 ngày expiration, signed
6. **CORS**: Whitelist origins

### 📊 reCAPTCHA Score (v3)

```
Score   |  Meaning              |  Action
--------|----------------------|---------------------------
0.9-1.0 |  Very likely human   |  Allow login
0.7-0.8 |  Probably human      |  Allow login
0.5-0.6 |  Neutral/Suspicious  |  Challenge or allow
0.3-0.4 |  Likely bot          |  Block or challenge
0.0-0.2 |  Very likely bot     |  Block login
```

---

(Tiếp tục phần 6-11...)

- Event Statistics
- Ticket List  
- Student Bill History
- CRUD Venue
- Venue-Area
- Admin CRUD Account