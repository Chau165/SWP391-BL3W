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

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        // 1) Check Admin JWT
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            resp.setStatus(401);
            resp.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }
        String roleFromToken = JwtUtils.getRoleFromToken(authHeader.substring(7));
        if (!"ADMIN".equalsIgnoreCase(roleFromToken)) {
            resp.setStatus(403);
            resp.getWriter().write("{\"error\":\"Forbidden: Admin only\"}");
            return;
        }

        // 2) Read body
        DTO.AdminUpdateUserRequest data = gson.fromJson(req.getReader(), DTO.AdminUpdateUserRequest.class);
        if (data == null || data.getId() <= 0) {
            sendError(resp, "Thiếu user_id (id)");
            return;
        }

        // 3) Validate input (chỉ validate nếu field được gửi lên)
        if (data.getRole() != null && !ValidationUtil.isValidRoleForCreation(data.getRole())) {
            sendError(resp, "Role không hợp lệ. Chỉ STAFF, ORGANIZER hoặc ADMIN.");
            return;
        }
        if (data.getFullName() != null && !ValidationUtil.isValidFullName(data.getFullName())) {
            sendError(resp, "Họ tên không hợp lệ (2-100 ký tự)");
            return;
        }
        if (data.getPhone() != null && !ValidationUtil.isValidVNPhone(data.getPhone())) {
            sendError(resp, "Số điện thoại Việt Nam không hợp lệ");
            return;
        }
        // status chỉ cho ACTIVE/INACTIVE
        if (data.getStatus() != null) {
            String st = data.getStatus().trim().toUpperCase();
            if (!("ACTIVE".equals(st) || "INACTIVE".equals(st))) {
                sendError(resp, "Status không hợp lệ (chỉ ACTIVE/INACTIVE)");
                return;
            }
            data.setStatus(st);
        }
        // optional đổi password
        String passwordHash = null;
        if (data.getPassword() != null && !data.getPassword().trim().isEmpty()) {
            if (!ValidationUtil.isValidPassword(data.getPassword())) {
                sendError(resp, "Mật khẩu tối thiểu 6 ký tự, gồm cả chữ và số");
                return;
            }
            passwordHash = PasswordUtils.hashPassword(data.getPassword());
        }

        // 4) Update DB
        boolean ok = usersDAO.adminUpdateUserById(
                data.getId(),
                data.getFullName(),
                data.getPhone(),
                data.getRole() == null ? null : data.getRole().toUpperCase(),
                data.getStatus(),
                passwordHash
        );

        if (ok) {
            resp.setStatus(200);
            resp.getWriter().write("{\"message\":\"Cập nhật tài khoản thành công\"}");
        } else {
            resp.setStatus(500);
            sendError(resp, "Lỗi hệ thống khi cập nhật tài khoản");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        // 1) Check Admin JWT
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            resp.setStatus(401);
            resp.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }
        String roleFromToken = JwtUtils.getRoleFromToken(authHeader.substring(7));
        if (!"ADMIN".equalsIgnoreCase(roleFromToken)) {
            resp.setStatus(403);
            resp.getWriter().write("{\"error\":\"Forbidden: Admin only\"}");
            return;
        }

        // 2) Read id from query param: /api/admin/create-account?id=123
        String idParam = req.getParameter("id");
        if (idParam == null || idParam.trim().isEmpty()) {
            sendError(resp, "Thiếu id trên query (?id=...)");
            return;
        }

        int userId;
        try {
            userId = Integer.parseInt(idParam);
        } catch (NumberFormatException e) {
            sendError(resp, "id không hợp lệ");
            return;
        }

        // 3) Soft delete -> status = INACTIVE
        boolean ok = usersDAO.softDeleteUser(userId);

        if (ok) {
            resp.setStatus(200);
            resp.getWriter().write("{\"message\":\"Xóa mềm thành công (status=INACTIVE)\"}");
        } else {
            resp.setStatus(500);
            sendError(resp, "Lỗi hệ thống khi xóa mềm");
        }
    }

    private void sendError(HttpServletResponse resp, String msg) throws IOException {
        resp.setStatus(400);
        resp.getWriter().write("{\"error\":\"" + msg + "\"}");
    }
}
