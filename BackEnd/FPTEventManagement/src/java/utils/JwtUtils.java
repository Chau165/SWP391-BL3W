package utils;

/**
 * ========================================================================================================
 * UTILS: JwtUtils - GENERATE VÀ PARSE JWT TOKEN
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - Tạo JWT token cho user sau khi login/register thành công
 * - Parse và validate JWT token từ request header
 * - Trích xuất thông tin user từ token (userId, email, role)
 * 
 * JWT (JSON Web Token):
 * - Token format: header.payload.signature
 * - Header: Algorithm (HS256) + Token type (JWT)
 * - Payload: Claims (userId, email, role, issued time, expiration time)
 * - Signature: HMAC-SHA256(header + payload, SECRET_KEY)
 * 
 * SECRET KEY:
 * - Lưu trong JwtConfig.SECRET_KEY (Key object)
 * - Dùng để ký và verify token
 * - PHẢI GIỮ BÍ MẬT, không commit lên Git
 * 
 * EXPIRATION:
 * - JwtConfig.EXPIRATION_TIME = 7 ngày (hoặc tùy config)
 * - Sau khi hết hạn: token invalid, user phải login lại
 * 
 * METHODS:
 * 1. generateToken(email, role, id): Tạo JWT token mới
 * 2. parseToken(token): Parse token ra JwtUser (userId, email, role)
 * 3. validateToken(token): Kiểm tra token hợp lệ (chưa hết hạn, chữ ký đúng)
 * 4. getEmailFromToken(token): Lấy email từ token
 * 5. getRoleFromToken(token): Lấy role từ token
 * 6. getIdFromToken(token): Lấy userId từ token
 * 
 * JWT CLAIMS:
 * - subject: email
 * - role: STUDENT / ADMIN / ORGANIZER
 * - id: userId (primary key)
 * - issuedAt: Thời điểm tạo token
 * - expiration: Thời điểm token hết hạn
 * 
 * LUỒNG SỬ DỤNG:
 * 1. User login/register thành công
 * 2. Backend gọi generateToken(email, role, id)
 * 3. Trả token cho FE trong response
 * 4. FE lưu token (localStorage / sessionStorage)
 * 5. FE gửi token trong header: Authorization: Bearer <token>
 * 6. JwtAuthFilter gọi parseToken() để xác thực
 * 7. Nếu valid: set userId vào request.setAttribute("userId")
 * 8. Controller lấy userId từ attribute
 * 
 * SECURITY:
 * - Token được ký bằng SECRET_KEY, không thể giả mạo
 * - Hết hạn tự động sau EXPIRATION_TIME
 * - HTTPS: Token phải truyền qua HTTPS để tránh bị đánh cắp
 * - Refresh token: Nên thêm refresh token cho UX tốt hơn
 * 
 * SỬ DỤNG:
 * - Controller: LoginController, RegisterVerifyOtpController (generate token)
 * - Filter: filter/JwtAuthFilter.java (parse và validate token)
 * - Config: utils/JwtConfig.java (SECRET_KEY, EXPIRATION_TIME)
 */

import io.jsonwebtoken.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtils {

    // ===== DTO nhỏ để dùng trong code =====
    public static class JwtUser {
        private final int userId;
        private final String email;
        private final String role;

        public JwtUser(int userId, String email, String role) {
            this.userId = userId;
            this.email = email;
            this.role = role;
        }

        public int getUserId() {
            return userId;
        }

        public String getEmail() {
            return email;
        }

        public String getRole() {
            return role;
        }
    }

    // ✅ Tạo token cho user
    public static String generateToken(String email, String role, int id) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("id", id);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + JwtConfig.EXPIRATION_TIME))
                .signWith(JwtConfig.SECRET_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    // ✅ Hàm mới: parse token ra JwtUser (dùng cho controller)
    public static JwtUser parseToken(String token) {
        try {
            Jws<Claims> jws = Jwts.parserBuilder()
                    .setSigningKey(JwtConfig.SECRET_KEY)
                    .build()
                    .parseClaimsJws(token);

            Claims claims = jws.getBody();

            String email = claims.getSubject();
            String role = null;
            Object roleObj = claims.get("role");
            if (roleObj != null) {
                role = roleObj.toString();
            }

            Object idObj = claims.get("id");
            Integer userId = null;
            if (idObj instanceof Number) {
                userId = ((Number) idObj).intValue();
            } else if (idObj != null) {
                try {
                    userId = Integer.parseInt(idObj.toString());
                } catch (NumberFormatException ignored) {
                }
            }

            if (email == null || role == null || userId == null) {
                System.out.println("❌ JWT missing required claims (email/role/id)");
                return null;
            }

            return new JwtUser(userId, email, role);

        } catch (JwtException e) {
            System.out.println("❌ JWT parse error: " + e.getMessage());
            return null;
        }
    }

    // ✅ Kiểm tra token hợp lệ (nếu chỗ khác còn dùng)
    public static boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(JwtConfig.SECRET_KEY)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            System.out.println("❌ JWT validation error: " + e.getMessage());
            return false;
        }
    }

    // ✅ Lấy email từ token (optional)
    public static String getEmailFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(JwtConfig.SECRET_KEY)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (Exception e) {
            System.out.println("❌ Error getting email: " + e.getMessage());
            return null;
        }
    }

    // ✅ Lấy role từ token (optional)
    public static String getRoleFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(JwtConfig.SECRET_KEY)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Object roleObj = claims.get("role");
            return roleObj != null ? roleObj.toString() : null;
        } catch (Exception e) {
            System.out.println("❌ Error getting role: " + e.getMessage());
            return null;
        }
    }

    // ✅ Lấy ID user từ token (optional)
    public static Integer getIdFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(JwtConfig.SECRET_KEY)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Object idObj = claims.get("id");
            if (idObj instanceof Number) {
                return ((Number) idObj).intValue();
            }
            if (idObj != null) {
                return Integer.parseInt(idObj.toString());
            }
            return null;
        } catch (Exception e) {
            System.out.println("❌ Error getting id: " + e.getMessage());
            return null;
        }
    }
}