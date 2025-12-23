package controller;

import DAO.UsersDAO;
import DTO.AdminCreateAccountRequest;
import mylib.ValidationUtil; // File bạn cung cấp
import utils.PasswordUtils; // File bạn cung cấp
import utils.JwtUtils;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet("/api/admin/create-account")
public class AdminCreateAccountController extends HttpServlet {

    private final UsersDAO usersDAO = new UsersDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        // 1. Kiểm tra quyền Admin (JWT)
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            resp.setStatus(401);
            resp.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }
        String role = JwtUtils.getRoleFromToken(authHeader.substring(7));
        if (!"ADMIN".equalsIgnoreCase(role)) {
            resp.setStatus(403);
            resp.getWriter().write("{\"error\":\"Forbidden: Admin only\"}");
            return;
        }

        // 2. Đọc dữ liệu
        AdminCreateAccountRequest data = gson.fromJson(req.getReader(), AdminCreateAccountRequest.class);

        // 3. Sử dụng ValidationUtil
        if (!ValidationUtil.isValidRoleForCreation(data.getRole())) {
            sendError(resp, "Role không hợp lệ. Chỉ được chọn STAFF, ORGANIZER hoặc ADMIN.");
            return;
        }
        if (!ValidationUtil.isValidFullName(data.getFullName())) {
            sendError(resp, "Họ tên không hợp lệ (2-100 ký tự)");
            return;
        }
        if (!ValidationUtil.isValidEmail(data.getEmail())) {
            sendError(resp, "Email không đúng định dạng FPT");
            return;
        }
        if (!ValidationUtil.isValidVNPhone(data.getPhone())) {
            sendError(resp, "Số điện thoại Việt Nam không hợp lệ");
            return;
        }
        if (!ValidationUtil.isValidPassword(data.getPassword())) {
            sendError(resp, "Mật khẩu tối thiểu 6 ký tự, gồm cả chữ và số");
            return;
        }

        // 4. Validate trùng lặp (Check Database)
        if (usersDAO.isEmailExists(data.getEmail())) {
            sendError(resp, "Email này đã được sử dụng");
            return;
        }
        if (usersDAO.isPhoneExists(data.getPhone())) {
            sendError(resp, "Số điện thoại này đã được sử dụng");
            return;
        }

        // 5. Lưu vào Database
        String hash = PasswordUtils.hashPassword(data.getPassword()); // SHA-256
        boolean success = usersDAO.adminCreateAccount(data, hash);

        if (success) {
            resp.setStatus(201);
            resp.getWriter().write("{\"message\":\"Tạo tài khoản thành công\"}");
        } else {
            resp.setStatus(500);
            sendError(resp, "Lỗi hệ thống khi tạo tài khoản");
        }
    }

    private void sendError(HttpServletResponse resp, String msg) throws IOException {
        resp.setStatus(400);
        resp.getWriter().write("{\"error\":\"" + msg + "\"}");
    }
}
