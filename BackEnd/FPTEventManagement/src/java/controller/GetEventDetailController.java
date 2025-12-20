package controller;

// ===== DAO dùng để truy vấn dữ liệu Event từ database =====
import DAO.EventDAO;

// ===== DTO chứa đầy đủ thông tin chi tiết của Event (event + venue + ticket...) =====
import DTO.EventDetailDto;

// ===== Gson dùng để convert Java Object -> JSON =====
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

// ===== Servlet API =====
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

// ===== Java core =====
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

/**
 * API: GET /api/events/detail?id={eventId}
 *
 * MỤC ĐÍCH:
 * - Trả về chi tiết 1 sự kiện theo eventId
 * - Dùng cho màn hình Event Detail bên Frontend
 * - API PUBLIC (Guest cũng xem được) → KHÔNG check JWT / role
 *
 * RESPONSE:
 * - 200: Thành công, trả JSON chi tiết event
 * - 400: Thiếu id hoặc id không hợp lệ
 * - 404: Event không tồn tại
 * - 500: Lỗi hệ thống / database
 */
@WebServlet("/api/events/detail")
public class GetEventDetailController extends HttpServlet {

    // DAO xử lý truy vấn dữ liệu Event từ DB
    private final EventDAO eventDAO = new EventDAO();

    /**
     * Gson:
     * - serializeNulls(): các field null (vd: bannerUrl) vẫn xuất hiện trong JSON
     *   → giúp FE dễ xử lý UI, không cần check field có tồn tại hay không
     */
    private final Gson gson = new GsonBuilder()
            .serializeNulls()
            .create();

    /**
     * doGet:
     * - API chỉ dùng GET vì chỉ đọc dữ liệu
     * - Không thay đổi trạng thái hệ thống
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        // ===== Set CORS để FE (React/Vite) gọi được API =====
        setCorsHeaders(response, request);

        // ===== Trả dữ liệu dạng JSON UTF-8 =====
        response.setContentType("application/json;charset=UTF-8");

        // ===== Lấy eventId từ query param (?id=...) =====
        String idParam = request.getParameter("id");

        // ===== VALIDATE: thiếu id =====
        if (idParam == null || idParam.trim().isEmpty()) {
            // 400 Bad Request: client gửi request sai
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter out = response.getWriter()) {
                out.write("{\"message\":\"Missing event id\"}");
            }
            return;
        }

        try {
            // ===== Parse id sang số =====
            // Nếu id không phải số → NumberFormatException
            int eventId = Integer.parseInt(idParam);

            // ===== Lấy chi tiết event từ DB =====
            EventDetailDto detail = eventDAO.getEventDetail(eventId);

            // ===== Nếu không tìm thấy event =====
            if (detail == null) {
                // 404 Not Found: resource không tồn tại
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                try (PrintWriter out = response.getWriter()) {
                    out.write("{\"message\":\"Event not found\"}");
                }
                return;
            }

            /**
             * CHÚ Ý:
             * - EventDetailDto phải có getter cho tickets (List<CategoryTicket>)
             * - CategoryTicket phải có getter cho description
             * → nếu không Gson sẽ không serialize được field
             */

            // ===== Convert DTO -> JSON =====
            String json = gson.toJson(detail);

            // ===== Trả JSON cho FE =====
            try (PrintWriter out = response.getWriter()) {
                out.write(json);
            }

        } catch (NumberFormatException ex) {
            // ===== id không phải số =====
            // 400 Bad Request: lỗi dữ liệu đầu vào từ client
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter out = response.getWriter()) {
                out.write("{\"message\":\"Invalid event id\"}");
            }

        } catch (SQLException | ClassNotFoundException ex) {
            // ===== Lỗi hệ thống / database =====
            ex.printStackTrace();

            // 500 Internal Server Error
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.write("{\"message\":\"Error loading event detail\"}");
            }
        }
    }

    /**
     * ==================== CORS ====================
     *
     * Cho phép FE gọi API từ:
     * - localhost (dev)
     * - ngrok (demo)
     *
     * Nếu không set CORS:
     * → FE sẽ bị lỗi "Blocked by CORS policy"
     */
    private void setCorsHeaders(HttpServletResponse res, HttpServletRequest req) {
        String origin = req.getHeader("Origin");

        boolean allowed = origin != null && (
                origin.equals("http://localhost:5173")
                        || origin.equals("http://127.0.0.1:5173")
                        || origin.equals("http://localhost:3000")
                        || origin.equals("http://127.0.0.1:3000")
                        || origin.contains("ngrok-free.app")
                        || origin.contains("ngrok.app")
        );

        // Nếu origin hợp lệ → cho phép truy cập
        if (allowed) {
            res.setHeader("Access-Control-Allow-Origin", origin);
            res.setHeader("Access-Control-Allow-Credentials", "true");
        } else {
            // Origin không hợp lệ → chặn
            res.setHeader("Access-Control-Allow-Origin", "null");
        }

        // Cho browser cache theo Origin
        res.setHeader("Vary", "Origin");

        // Cho phép method
        res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");

        // Header FE được phép gửi
        res.setHeader("Access-Control-Allow-Headers",
                "Content-Type, Authorization, ngrok-skip-browser-warning");

        // Cache preflight request 24h
        res.setHeader("Access-Control-Max-Age", "86400");
    }
}
