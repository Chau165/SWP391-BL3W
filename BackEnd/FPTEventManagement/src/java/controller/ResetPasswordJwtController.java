package controller;

/**
 * ========================================================================================================
 * CONTROLLER: ResetPasswordJwtController - XÁC THỰC OTP VÀ ĐỔI MẬT KHẨU
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - Nhận email, OTP và mật khẩu mới từ Frontend
 * - Verify OTP từ PasswordResetManager
 * - Kiểm tra OTP hết hạn, số lần nhập sai
 * - Cập nhật mật khẩu mới vào database (hash SHA-256)
 * - Vô hiệu hóa OTP sau khi dùng
 * - Trả về kết quả cho FE
 * 
 * ENDPOINT: POST /api/reset-password
 * 
 * REQUEST BODY:
 * {
 *   "email": "a@fpt.edu.vn",
 *   "otp": "123456",
 *   "newPassword": "NewPass123"
 * }
 * 
 * RESPONSE SUCCESS (200):
 * {
 *   "status": "success",
 *   "message": "Đổi mật khẩu thành công"
 * }
 * 
 * RESPONSE ERROR:
 * - 400 Bad Request: Thiếu field, mật khẩu quá ngắn (<6 ký tự)
 * - 401 Unauthorized: OTP không đúng hoặc hết hạn
 * - 404 Not Found: Email không tồn tại
 * - 500 Internal Server Error: Không thể cập nhật mật khẩu
 * 
 * LUỒNG XỬ LÝ:
 * 1. FE gửi POST request với email, OTP, newPassword
 * 2. Parse JSON request body
 * 3. Validate các field không rỗng
 * 4. Validate mật khẩu mới (tối thiểu 6 ký tự)
 * 5. Kiểm tra email tồn tại (UsersDAO.getUserByEmail)
 * 6. Verify OTP (PasswordResetManager.verifyOtp):
 *    - Kiểm tra OTP khớp
 *    - Kiểm tra chưa hết hạn (5 phút)
 *    - Kiểm tra số lần nhập sai < 5
 *    - Kiểm tra chưa dùng (one-time use)
 * 7. Nếu OTP hợp lệ: Cập nhật mật khẩu (UsersDAO.updatePasswordByEmail)
 * 8. DAO tự động hash mật khẩu bằng SHA-256
 * 9. Vô hiệu hóa OTP (PasswordResetManager.invalidate)
 * 10. Trả về success response
 * 11. FE chuyển user về trang login
 * 
 * PASSWORD VALIDATION:
 * - Hiện tại: Tối thiểu 6 ký tự
 * - Nên thêm: Validation mạnh hơn (chữ hoa, chữ thường, số, ký tự đặc biệt)
 * - Sử dụng ValidationUtil.isValidPassword() cho nhất quán
 * 
 * SECURITY:
 * - OTP one-time use: Chỉ dùng được 1 lần, tự động vô hiệu hóa sau khi verify thành công
 * - Max attempts: Tối đa 5 lần nhập sai
 * - TTL: OTP hết hạn sau 5 phút
 * - Password hash: Tự động hash SHA-256 trong DAO
 * - Rate limiting: Nên thêm để tránh brute-force
 * 
 * OTP FLOW:
 * 1. User quên mật khẩu
 * 2. ForgotPasswordJwtController: Gửi OTP qua email
 * 3. User nhập OTP + mật khẩu mới
 * 4. ResetPasswordJwtController: Verify OTP và đổi mật khẩu
 * 5. User login với mật khẩu mới
 * 
 * KẾT NỐI FILE:
 * - DAO: DAO/UsersDAO.java (cập nhật mật khẩu)
 * - Manager: utils/PasswordResetManager.java (verify và invalidate OTP)
 * - Utils: utils/PasswordUtils.java (hash password trong DAO)
 * - Previous step: controller/ForgotPasswordJwtController.java
 */

import DAO.UsersDAO;
import DTO.Users;
import com.google.gson.Gson;
import utils.PasswordResetManager; // dùng OTP

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;

/**
 * POST /api/reset-password
 * Body: { "email": "xxx@fpt.edu.vn", "otp": "123456", "newPassword": "Abc@123"
 * }
 *
 * Flow:
 * 1. Kiểm tra email, otp, newPassword không rỗng
 * 2. Kiểm tra mật khẩu đủ mạnh (tối thiểu 6 ký tự – bạn có thể tăng thêm rule)
 * 3. Kiểm tra email có tồn tại trong hệ thống
 * 4. Verify OTP bằng PasswordResetManager (theo email)
 * 5. Nếu OK -> cập nhật mật khẩu mới cho user (hash trong DAO)
 * 6. Vô hiệu hóa OTP sau khi dùng
 */
@WebServlet("/api/reset-password")
public class ResetPasswordJwtController extends HttpServlet {

    private final UsersDAO usersDAO = new UsersDAO();
    private final Gson gson = new Gson();

    // ===== DTO request =====
    private static class Req {
        String email;
        String otp;
        String newPassword;
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * XỬ LÝ XÁC THỰC OTP VÀ ĐỔI MẬT KHẨU
     * 
     * ENDPOINT: POST /api/reset-password
     * AUTHENTICATION: Không cần JWT (public endpoint)
     * CONTENT-TYPE: application/json
     * 
     * REQUEST FLOW:
     * 1. Parse email, OTP, newPassword từ request body
     * 2. Validate các field không rỗng và mật khẩu đủ mạnh
     * 3. Kiểm tra email tồn tại trong DB
     * 4. Verify OTP (khớp, chưa hết hạn, chưa dùng)
     * 5. Cập nhật mật khẩu mới (hash SHA-256)
     * 6. Vô hiệu hóa OTP
     * 7. Return success response
     * 
     * ERROR HANDLING:
     * - 400: Field rỗng, mật khẩu quá ngắn
     * - 401: OTP sai, hết hạn, đã dùng
     * - 404: Email không tồn tại
     * - 500: Lỗi database
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setCorsHeaders(response, request);
        response.setContentType("application/json;charset=UTF-8");
        request.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        // ===== 1. Đọc body JSON =====
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        Req body = gson.fromJson(sb.toString(), Req.class);
        if (body == null || isBlank(body.email) || isBlank(body.otp) || isBlank(body.newPassword)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu email/OTP/mật khẩu mới\"}");
            return;
        }

        String email = body.email.trim();
        String otp = body.otp.trim();
        String newPassword = body.newPassword;

        // ===== 2. Validate đơn giản mật khẩu =====
        if (newPassword.length() < 6) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Mật khẩu phải có ít nhất 6 ký tự\"}");
            return;
        }

        // ===== 3. Kiểm tra email tồn tại =====
        Users user = usersDAO.getUserByEmail(email);
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            out.print("{\"status\":\"fail\",\"message\":\"Email không tồn tại trong hệ thống\"}");
            return;
        }

        // ===== 4. Verify OTP theo email =====
        boolean otpOk = PasswordResetManager.verifyOtp(email, otp);
        if (!otpOk) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"status\":\"fail\",\"message\":\"OTP không đúng hoặc đã hết hạn\"}");
            return;
        }

        // ===== 5. Đổi mật khẩu (hash bên trong DAO) =====
        boolean ok;
        try {
            ok = usersDAO.updatePasswordByEmail(email, newPassword);
        } catch (Exception ex) {
            ex.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"error\",\"message\":\"Không thể cập nhật mật khẩu\"}");
            return;
        }

        if (!ok) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"error\",\"message\":\"Không thể cập nhật mật khẩu\"}");
            return;
        }

        // ===== 6. Vô hiệu OTP sau khi dùng =====
        PasswordResetManager.invalidate(email);

        response.setStatus(HttpServletResponse.SC_OK);
        out.print("{\"status\":\"success\",\"message\":\"Đổi mật khẩu thành công\"}");
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ==== CORS giống các controller khác ====
    private void setCorsHeaders(HttpServletResponse res, HttpServletRequest req) {
        String origin = req.getHeader("Origin");

        boolean allowed = origin != null && (origin.equals("http://localhost:5173")
                || origin.equals("http://127.0.0.1:5173")
                || origin.equals("http://localhost:3000")
                || origin.equals("http://127.0.0.1:3000")
                || origin.contains("ngrok-free.app")
                || origin.contains("ngrok.app"));

        if (allowed) {
            res.setHeader("Access-Control-Allow-Origin", origin);
            res.setHeader("Access-Control-Allow-Credentials", "true");
        } else {
            res.setHeader("Access-Control-Allow-Origin", "null");
        }

        res.setHeader("Vary", "Origin");
        res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers",
                "Content-Type, Authorization, ngrok-skip-browser-warning");
        res.setHeader("Access-Control-Expose-Headers", "Authorization");
        res.setHeader("Access-Control-Max-Age", "86400");
    }

}