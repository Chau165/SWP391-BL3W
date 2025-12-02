// util/PasswordResetManager.java
package utils;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PasswordResetManager {
    private static final SecureRandom RNG = new SecureRandom();
    private static final long OTP_TTL_SEC = 5 * 60; // 5 phút
    private static final int MAX_ATTEMPTS = 5;

    private static class Entry {
        String email;
        String otp; // có thể hash (BCrypt) trong thực tế
        Instant expiresAt;
        int attempts;
        boolean used;
    }

    // key: email (hoặc email+nonce)
    private static final Map<String, Entry> STORE = new ConcurrentHashMap<>();

    public static String generateOtp(String email) {
        String otp = String.format("%06d", RNG.nextInt(1_000_000));
        Entry e = new Entry();
        e.email = email;
        e.otp = otp;
        e.expiresAt = Instant.now().plusSeconds(OTP_TTL_SEC);
        e.attempts = 0;
        e.used = false;
        STORE.put(email.toLowerCase(), e);
        return otp;
    }

    public static boolean verifyOtp(String email, String otp) {
        Entry e = STORE.get(email.toLowerCase());
        if (e == null) return false;
        if (e.used) return false;
        if (Instant.now().isAfter(e.expiresAt)) return false;
        if (e.attempts >= MAX_ATTEMPTS) return false;
        e.attempts++;
        boolean ok = Objects.equals(e.otp, otp);
        if (ok) e.used = true; // một lần dùng
        return ok;
    }

    /**
     * Find and consume an OTP without requiring the email.
     * Returns the associated email if OTP is valid and not expired/used, otherwise null.
     */
    public static String consumeOtp(String otp) {
        if (otp == null) return null;
        String o = otp.trim();
        for (Map.Entry<String, Entry> kv : STORE.entrySet()) {
            Entry e = kv.getValue();
            if (e == null) continue;
            if (e.used) continue;
            if (Instant.now().isAfter(e.expiresAt)) continue;
            if (e.attempts >= MAX_ATTEMPTS) continue;
            // increment attempts for this try
            e.attempts++;
            if (Objects.equals(e.otp, o)) {
                e.used = true;
                return e.email;
            }
        }
        return null;
    }

    public static void invalidate(String email) {
        STORE.remove(email.toLowerCase());
    }
}
