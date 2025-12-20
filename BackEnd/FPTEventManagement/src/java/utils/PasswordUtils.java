package utils;

/**
 * ========================================================================================================
 * UTILS: PasswordUtils - HASH VÀ VERIFY MẬT KHẨU
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - Hash mật khẩu bằng thuật toán SHA-256
 * - Verify mật khẩu người dùng nhập với hash trong database
 * - Chuyển byte array thành hex string để lưu DB
 * 
 * ALGORITHM: SHA-256
 * - SHA-256: Secure Hash Algorithm 256-bit
 * - Output: 64 ký tự hex (256 bits = 32 bytes = 64 hex chars)
 * - One-way function: Không thể decode ngược lại plain password
 * 
 * METHODS:
 * 1. hashPassword(plainPassword): Hash mật khẩu plain text thành hex string
 * 2. verifyPassword(plainPassword, storedHash): So sánh mật khẩu với hash
 * 3. bytesToHex(hash): Chuyển byte[] thành hex string (private helper)
 * 
 * LƯU Ý BẢO MẬT:
 * - SHA-256 là hash đơn giản, KHÔNG có salt
 * - Nên nâng cấp lên bcrypt, Argon2, hoặc PBKDF2 với salt
 * - Salt giúp chống rainbow table attack
 * - Hiện tại: 2 user có cùng password sẽ có cùng hash (yếu)
 * 
 * VÍ DỤ:
 * String plain = "Pass123";
 * String hash = PasswordUtils.hashPassword(plain);
 * // hash = "a1b2c3d4e5f6... (64 chars)"
 * 
 * boolean isValid = PasswordUtils.verifyPassword("Pass123", hash);
 * // isValid = true
 * 
 * SỬ DỤNG:
 * - DAO: UsersDAO.insertUser() - hash password trước khi insert
 * - DAO: UsersDAO.checkLogin() - verify password khi login
 * - DAO: UsersDAO.updatePasswordByEmail() - hash password mới
 * - Cache: OtpCache.PendingUser.toUsersEntity() - hash trước khi tạo user
 * 
 * NÂNG CẤP ĐỀ XUẤT:
 * - Thêm salt ngẫu nhiên cho mỗi user
 * - Sử dụng BCrypt hoặc Argon2 thay vì SHA-256
 * - Lưu salt cùng với hash trong DB
 */

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PasswordUtils {

    // Chuyển mảng byte thành chuỗi hex để lưu vào DB
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    // Hash mật khẩu bằng SHA-256
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null)
            return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(plainPassword.getBytes());
            return bytesToHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    // So sánh mật khẩu user nhập với hash trong DB
    public static boolean verifyPassword(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null)
            return false;
        String hashedInput = hashPassword(plainPassword);
        return storedHash.equalsIgnoreCase(hashedInput);
    }
}