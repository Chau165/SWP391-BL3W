package utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtils {

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

    // ✅ Kiểm tra token hợp lệ
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

    // ✅ Lấy email từ token
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

    // ✅ Lấy role từ token
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

    // ✅ Lấy ID user từ token
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
            return null;
        } catch (Exception e) {
            System.out.println("❌ Error getting id: " + e.getMessage());
            return null;
        }
    }
}