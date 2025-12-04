package utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
// import io.jsonwebtoken.SignatureException; // Bỏ import này nếu không dùng, hoặc giữ lại cũng không sao nhưng không catch nó

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT helper utilities.
 * Uses a fixed hard-coded secret key so tokens remain valid across server restarts.
 */
public class JwtUtils {

    // ✅ FIXED SECRET KEY (Không đổi khi restart server)
    // Chuỗi này phải đủ dài (ít nhất 32 ký tự)
    private static final String SECRET = "MySuperSecretKeyForEventManagement2025_FixedKey";
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    // Thời gian hết hạn (ví dụ: 24 giờ)
    private static final long EXPIRATION = 86400000L; 

    // 1. Tạo Token
    public static String generateToken(String email, String role, int id) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("id", id);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    // 2. Validate Token (Trả về True/False và in log lỗi)
    public static boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(SECRET_KEY)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            System.out.println("❌ JWT expired: " + e.getMessage());
        } catch (UnsupportedJwtException e) {
            System.out.println("❌ Unsupported JWT: " + e.getMessage());
        } catch (MalformedJwtException e) {
            System.out.println("❌ Malformed JWT: " + e.getMessage());
        } catch (SecurityException e) { 
            // Đã xóa SignatureException ở đây vì SecurityException bao trùm nó
            System.out.println("❌ Invalid JWT signature: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("❌ Illegal argument: " + e.getMessage());
        } catch (JwtException e) {
            System.out.println("❌ General JWT error: " + e.getMessage());
        }
        return false;
    }

    // 3. Lấy chi tiết lỗi (Dùng cho Filter)
    public static String getValidationError(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(SECRET_KEY).build().parseClaimsJws(token);
            return null; // Không lỗi
        } catch (ExpiredJwtException e) {
            return "ExpiredJwtException: " + e.getMessage();
        } catch (UnsupportedJwtException e) {
            return "UnsupportedJwtException: " + e.getMessage();
        } catch (MalformedJwtException e) {
            return "MalformedJwtException: " + e.getMessage();
        } catch (SecurityException e) {
            // Đã xóa SignatureException ở đây
            return "SignatureException: " + e.getMessage();
        } catch (IllegalArgumentException e) {
            return "IllegalArgumentException: " + e.getMessage();
        } catch (JwtException e) {
            return "JwtException: " + e.getMessage();
        }
    }

    // 4. Lấy Email từ Token
    public static String getEmailFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(SECRET_KEY)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    // 5. Lấy Role từ Token
    public static String getRoleFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(SECRET_KEY)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            Object role = claims.get("role");
            return role != null ? role.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // 6. Lấy ID từ Token
    public static Integer getIdFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(SECRET_KEY)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            Object idObj = claims.get("id");
            if (idObj instanceof Number) {
                return ((Number) idObj).intValue();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}