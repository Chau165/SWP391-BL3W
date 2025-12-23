package controller;

import DAO.UsersDAO;
import DTO.StaffOrganizerResponse;
import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import utils.JwtUtils;

@WebServlet(name = "StaffOrganizerController", urlPatterns = {"/api/users/staff-organizer"})
public class StaffOrganizerController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {

            // 1) Check Authorization header
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                response.setStatus(401);
                out.print("{\"error\": \"Unauthorized\"}");
                return;
            }

            // 2) Parse token + get role/userId
            String token = authHeader.substring(7);
            String role = JwtUtils.getRoleFromToken(token); // ADMIN/STAFF/ORGANIZER/STUDENT...
            int userId = JwtUtils.getIdFromToken(token);    // nếu bạn cần log/audit thì dùng

            if (role == null || role.trim().isEmpty()) {
                response.setStatus(401);
                out.print("{\"error\": \"Unauthorized\"}");
                return;
            }

            // 3) Authorization (phân quyền)
            // CHỈ ADMIN mới được gọi API này
            if (!"ADMIN".equalsIgnoreCase(role)) {
                response.setStatus(403);
                out.print("{\"error\": \"Forbidden\"}");
                return;
            }

            // 4) Call DAO
            UsersDAO dao = new UsersDAO();
            StaffOrganizerResponse data = dao.getStaffAndOrganizer(); // đã bỏ createdAt theo yêu cầu trước

            out.print(new Gson().toJson(data));
            response.setStatus(200);

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            // optional: trả message lỗi cho FE
            // response.getWriter().print("{\"error\":\"Internal Server Error\"}");
        }
    }
}
