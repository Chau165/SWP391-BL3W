package mylib;

import DTO.Users;
import utils.PasswordUtils;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OtpCache {

    public static class PendingUser {
        public String fullName;
        public String phone;
        public String email;
        public String password;  // raw password (sẽ hash khi tạo Users)
        public String otp;
        public long   expiresAt;   // epoch millis
        public int    attempts;

        // resend control
        public long   lastSentAt;
        public int    resendCount;

        // Chuyển sang entity Users (theo DB mới)
        public Users toUsersEntity() {
            Users u = new Users();
            u.setFullName(fullName);
            u.setPhone(phone);
            u.setEmail(email);
            // hash password
            String hashed = PasswordUtils.hashPassword(password);
            u.setPasswordHash(hashed);
            u.setRole("STUDENT");     // mặc định sinh viên
            u.setStatus("ACTIVE");    // khớp CHECK constraint
            return u;
        }
    }

    private static final Map<String, PendingUser> CACHE = new ConcurrentHashMap<>();
    private static final long OTP_TTL_MS = 5 * 60 * 1000; // 5 phút
    private static final int  MAX_ATTEMPTS = 5;

    // resend limit
    private static final long RESEND_COOLDOWN_MS = 60 * 1000; // 60s
    private static final int  MAX_RESEND = 5;

    private OtpCache() {}

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    public static void put(String email, String fullName, String phone, String password, String otp) {
        long now = Instant.now().toEpochMilli();
        PendingUser p = new PendingUser();
        p.email = email;
        p.fullName = fullName;
        p.phone = phone;
        p.password = password;
        p.otp = otp;
        p.expiresAt = now + OTP_TTL_MS;
        p.attempts = 0;
        p.lastSentAt = now;
        p.resendCount = 0;
        CACHE.put(key(email), p);
    }

    public static PendingUser get(String email) {
        cleanup();
        return CACHE.get(key(email));
    }

    public static void remove(String email) {
        CACHE.remove(key(email));
    }

    public static boolean isExpired(PendingUser p) {
        return p == null || Instant.now().toEpochMilli() > p.expiresAt;
    }

    public static boolean canAttempt(PendingUser p) {
        return p != null && p.attempts < MAX_ATTEMPTS;
    }

    public static void incAttempt(PendingUser p) {
        if (p != null) p.attempts++;
    }

    // resend helpers
    public static boolean canResend(PendingUser p) {
        if (p == null) return false;
        long now = Instant.now().toEpochMilli();
        boolean cooldownOk = (now - p.lastSentAt) >= RESEND_COOLDOWN_MS;
        boolean underLimit = p.resendCount < MAX_RESEND;
        return cooldownOk && underLimit;
    }

    public static void applyResend(String email, String newOtp) {
        PendingUser p = get(email);
        if (p == null) return;
        long now = Instant.now().toEpochMilli();
        p.otp = newOtp;
        p.expiresAt = now + OTP_TTL_MS;
        p.lastSentAt = now;
        p.resendCount++;
    }

    private static void cleanup() {
        long now = Instant.now().toEpochMilli();
        CACHE.values().removeIf(p -> p.expiresAt < now);
    }

    /**
     * Kiểm tra OTP:
     *  - Hết hạn -> xoá và false
     *  - Vượt MAX_ATTEMPTS -> xoá và false
     *  - Sai -> tăng attempts, nếu quá ngưỡng thì xoá; false
     *  - Đúng -> true (controller tự remove sau khi tạo user)
     */
    public static boolean verify(String email, String otpInput) {
        cleanup();
        String k = key(email);
        PendingUser p = CACHE.get(k);
        if (p == null) return false;

        if (isExpired(p)) {
            CACHE.remove(k);
            return false;
        }
        if (!canAttempt(p)) {
            CACHE.remove(k);
            return false;
        }

        String want = p.otp == null ? "" : p.otp.trim();
        String got  = otpInput == null ? "" : otpInput.trim();

        if (!want.equals(got)) {
            incAttempt(p);
            if (!canAttempt(p)) {
                CACHE.remove(k);
            }
            return false;
        }
        return true;
    }
}
