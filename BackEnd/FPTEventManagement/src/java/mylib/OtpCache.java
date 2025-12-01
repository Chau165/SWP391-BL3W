package mylib;

import DTO.Users;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OtpCache {

    public static class PendingUser {
        public String fullName;
        public String phone;
        public String email;
        public String password;
        public String otp;
        public long   expiresAt;   // epoch millis
        public int    attempts;

        // === resend control ===
        public long   lastSentAt;  // epoch millis (lần gửi gần nhất)
        public int    resendCount; // số lần resend đã gửi

        public Users toUsersEntity() {
            Users u = new Users();
            u.setFullName(fullName);
            u.setPhone(phone);
            u.setEmail(email);
            // passwordHash sẽ được set ở DAO khi insert
            u.setRole("Student"); // Default role for event registration
            u.setStatus("ACTIVE");
            return u;
        }
        
        // Helper để lấy raw password cho DAO
        public String getRawPassword() {
            return password;
        }
    }

    private static final Map<String, PendingUser> CACHE = new ConcurrentHashMap<>();
    private static final long OTP_TTL_MS = 5 * 60 * 1000; // 5 phút
    private static final int  MAX_ATTEMPTS = 5;

    // === giới hạn resend ===
    private static final long RESEND_COOLDOWN_MS = 60 * 1000; // 60s
    private static final int  MAX_RESEND = 5;

    private OtpCache() {}

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

        CACHE.put(email.toLowerCase(), p);
    }

    public static PendingUser get(String email) {
        cleanup();
        return CACHE.get(email.toLowerCase());
    }

    public static void remove(String email) {
        CACHE.remove(email.toLowerCase());
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

    // === resend helpers ===
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
        p.expiresAt = now + OTP_TTL_MS; // gia hạn lại 5 phút
        p.lastSentAt = now;
        p.resendCount++;
        // Không reset attempts: người dùng nhập sai nhiều lần vẫn bị giới hạn đúng
    }

    private static void cleanup() {
        long now = Instant.now().toEpochMilli();
        CACHE.values().removeIf(p -> p.expiresAt < now);
    }
}
