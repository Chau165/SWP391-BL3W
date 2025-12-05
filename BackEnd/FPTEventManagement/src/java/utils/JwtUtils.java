package utils;

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
                } catch (NumberFormatException ignored) {}
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
