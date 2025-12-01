package utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;

public class ResetJwtUtil {

    // ❗ Secret phải là BASE64-encoded >= 256-bit (32 byte). Đưa vào ENV trong thực tế.
    private static final String BASE64_SECRET =
            "m5b0u7V6Zy0pZr5j3z2mJ8jJj2cZbYxJw0l0pWlCk8hM6m8cJz7JbZc+oQd8hQ1f";

    private static final Key SIGNING_KEY =
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(BASE64_SECRET));

    private static final long EXPIRE_MS = 10 * 60 * 1000L; // 10 phút

    /** Tạo token reset mật khẩu: subject = email, claim uid = int
     * @param userId
     * @param email
     * @return  */
    public static String generateResetToken(int userId, String email) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(email)
                .claim("uid", Integer.valueOf(userId)) // đảm bảo là int
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + EXPIRE_MS))
                .signWith(SIGNING_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Lấy email (subject). Null nếu token invalid/expired. */
    public static String getEmail(String token) {
        try {
            return parse(token).getSubject();
        } catch (JwtException e) {
            return null;
        }
    }

    /** *  Lấy userId từ claim "uid".Hỗ trợ int/long/double/float/String "6.0"/"6".
     * @param token
     * @return  */
    public static Integer getUserId(String token) {
        try {
            Object uid = parse(token).get("uid");
            return coerceToInt(uid);
        } catch (JwtException e) {
            return null;
        }
    }

    // ===== Helpers =====

    private static Claims parse(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(SIGNING_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /** Chuyển nhiều kiểu số phổ biến hoặc string số sang int an toàn. */
    private static Integer coerceToInt(Object v) {
        if (v == null) return null;

        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Long)    return ((Long) v).intValue();
        if (v instanceof Short)   return ((Short) v).intValue();
        if (v instanceof Double)  return ((Double) v).intValue();
        if (v instanceof Float)   return ((Float) v).intValue();

        if (v instanceof String) {
            String s = ((String) v).trim();
            // "6.0" / "6.000" -> "6"
            if (s.matches("^\\d+\\.0+$")) s = s.substring(0, s.indexOf('.'));
            if (!s.matches("^\\d+$")) return null;
            return Integer.parseInt(s);
        }
        return null;
    }
}
