package utils;

import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

public class JwtConfig {
    // ✅ Khóa bí mật cố định (phải > 32 ký tự)
    private static final String SECRET = "BatterySwapSuperSecretKeyForJwtSystem2025_!@#";

    // ✅ Sinh SecretKey cố định từ chuỗi trên
    public static final SecretKey SECRET_KEY =
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    // ✅ Thời gian sống của token (1 giờ)
    public static final long EXPIRATION_TIME = 1800000; // 1 giờ
}
