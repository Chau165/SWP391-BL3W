// util/PasswordResetManager.java
package utils;

/**
 * ========================================================================================================
 * MANAGER: PasswordResetManager - QUẢN LÝ OTP ĐẶT LẠI MẬT KHẨU
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - Quản lý OTP cho chức năng quên mật khẩu
 * - Sinh OTP 6 chữ số ngẫu nhiên
 * - Lưu trữ OTP tạm thời trong in-memory cache
 * - Verify OTP: kiểm tra khớp, hết hạn, attempts
 * - Vô hiệu hóa OTP sau khi dùng
 * 
 * TẠI SAO CẦN PASSWORDRESETMANAGER?
 * - Quên mật khẩu là flow tạm thời, không cần lưu DB
 * - OTP chỉ sống 5 phút, sau đó tự động hết hạn
 * - In-memory cache nhanh hơn, đơn giản hơn database
 * - Tách biệt với OtpCache (đăng ký tài khoản)
 * 
 * INNER CLASS: Entry
 * - Chứa thông tin OTP tạm thời cho mỗi email
 * - Fields:
 *   + email: Email của user quên mật khẩu
 *   + otp: Mã OTP 6 chữ số (có thể hash trong production)
 *   + expiresAt: Thời điểm OTP hết hạn (Instant)
 *   + attempts: Số lần nhập OTP sai
 *   + used: Đánh dấu OTP đã dùng chưa (one-time use)
 * 
 * CONFIGURATION:
 * - OTP_TTL_SEC: 5 phút (300 giây) - thời gian sống của OTP
 * - MAX_ATTEMPTS: 5 lần - số lần nhập sai tối đa
 * 
 * DATA STRUCTURE:
 * - STORE: ConcurrentHashMap<String, Entry> (thread-safe)
 * - Key: email.toLowerCase() (đảm bảo case-insensitive)
 * - Value: Entry (chứa OTP + metadata)
 * 
 * METHODS:
 * 1. generateOtp(email): Sinh OTP mới và lưu vào cache
 *    - Sinh số ngẫu nhiên 6 chữ số (000000 - 999999)
 *    - Tạo Entry mới với TTL 5 phút
 *    - Lưu vào STORE theo key = email
 *    - Return: OTP string
 * 
 * 2. verifyOtp(email, otp): Xác thực OTP
 *    - Lấy Entry từ STORE theo email
 *    - Kiểm tra Entry tồn tại
 *    - Kiểm tra OTP chưa dùng (used = false)
 *    - Kiểm tra chưa hết hạn (now <= expiresAt)
 *    - Kiểm tra attempts < MAX_ATTEMPTS
 *    - Tăng attempts++
 *    - So sánh OTP
 *    - Nếu khớp: đánh dấu used = true
 *    - Return: true/false
 * 
 * 3. invalidate(email): Vô hiệu hóa OTP
 *    - Xóa Entry khỏi STORE
 *    - Gọi sau khi đổi mật khẩu thành công
 *    - Hoặc khi muốn hủy OTP
 * 
 * SECURITY:
 * - SecureRandom: Số ngẫu nhiên an toàn hơn Math.random()
 * - One-time use: OTP chỉ dùng được 1 lần
 * - TTL: Tự động hết hạn sau 5 phút
 * - Max attempts: Chống brute-force OTP
 * - Case-insensitive email: Tránh lỗi do viết hoa/thường
 * 
 * NÂNG CẤP ĐỀ XUẤT:
 * - Hash OTP trong Entry (BCrypt) thay vì lưu plain text
 * - Dùng Redis thay vì ConcurrentHashMap (scale cho multi-server)
 * - Thêm cleanup job để xóa expired entries
 * - Log audit trail: ai request OTP, khi nào, IP nào
 * 
 * HẠN CHẾb:
 * - In-memory: Mất dữ liệu khi restart server
 * - Không scale: Không dùng được cho multi-server setup
 * - Nên chuyển sang Redis trong production
 * 
 * SỬ DỤNG:
 * - Controller: ForgotPasswordJwtController.java (generateOtp)
 * - Controller: ResetPasswordJwtController.java (verifyOtp, invalidate)
 */

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PasswordResetManager {
    private static final SecureRandom RNG = new SecureRandom();
    private static final long OTP_TTL_SEC = 5 * 60; // 5 phút
    private static final int MAX_ATTEMPTS = 5;

    /**
     * INNER CLASS: Entry - Chứa thông tin OTP tạm thời
     * 
     * CấU TRÚC DỮC LIỆU:
     * - Lưu trữ thông tin OTP cho mỗi email
     * - Immutable với các fields final (trừ used)
     * - Thread-safe khi dùng trong ConcurrentHashMap
     * 
     * FIELDS:
     * - email: Email của user (key) - toLowerCase() để case-insensitive
     * - otp: Mã OTP 6 chữ số (hiện tại plain text, nên hash trong production)
     * - expiresAt: Thời điểm hết hạn (Instant) - so sánh với Instant.now()
     * - attempts: Số lần nhập OTP sai - tăng mỗi lần verify fail
     * - used: Đánh dấu OTP đã dùng chưa (one-time use) - set true sau verify
     * success
     */
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
        if (e == null)
            return false;
        if (e.used)
            return false;
        if (Instant.now().isAfter(e.expiresAt))
            return false;
        if (e.attempts >= MAX_ATTEMPTS)
            return false;
        e.attempts++;
        boolean ok = Objects.equals(e.otp, otp);
        if (ok)
            e.used = true; // một lần dùng
        return ok;
    }

    public static void invalidate(String email) {
        STORE.remove(email.toLowerCase());
    }
}