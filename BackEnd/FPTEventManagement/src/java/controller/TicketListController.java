package controller;

import DAO.TicketDAO;
import DTO.MyTicketResponse;
import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import utils.JwtUtils;

@WebServlet(name = "TicketListController", urlPatterns = {"/api/tickets/list"})
public class TicketListController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("application/json;charset=UTF-8");
        
        try (PrintWriter out = response.getWriter()) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                response.setStatus(401);
                out.print("{\"error\": \"Unauthorized\"}");
                return;
            }

            String token = authHeader.substring(7);
            String role = JwtUtils.getRoleFromToken(token); // Lấy role: ADMIN/STAFF/ORGANIZER
            int userId = JwtUtils.getIdFromToken(token);    // Lấy ID người dùng hiện tại

            String eventIdStr = request.getParameter("eventId");
            Integer eventId = (eventIdStr != null && !eventIdStr.isEmpty()) ? Integer.parseInt(eventIdStr) : null;

            TicketDAO dao = new TicketDAO();
            // Truyền cả userId, role và eventId vào DAO để xử lý phân quyền động
            List<MyTicketResponse> list = dao.getTicketsByRole(role, userId, eventId);
            out.print(new Gson().toJson(list));
            response.setStatus(200);

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
        }
    }
}