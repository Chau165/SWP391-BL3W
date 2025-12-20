package controller;

// DAO dùng để lấy dữ liệu Event từ database
import DAO.EventDAO;

// Entity Event (mapping 1–1 với bảng event trong DB)
import DTO.Event;

// DTO dùng để trả dữ liệu gọn nhẹ cho FE (list event)
import DTO.EventListDto;

// Gson để convert Java object -> JSON
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API lấy danh sách sự kiện
 * Endpoint: GET /api/events
 *
 * Đặc điểm:
 * - KHÔNG cần đăng nhập (guest xem được)
 * - Trả về 2 danh sách:
 *   + openEvents  : sự kiện đang mở bán / đang diễn ra
 *   + closedEvents: sự kiện đã kết thúc / đóng
 *
 * Luồng tổng quát:
 * 1) FE gọi GET /api/events
 * 2) Backend lấy danh sách event từ DB
 * 3) Map Event -> EventListDto
 * 4) Phân loại OPEN / CLOSED
 * 5) Trả JSON cho FE render
 */
@WebServlet("/api/events")
public class GetAllEventsController extends HttpServlet {

    // DAO dùng để query DB
    private final EventDAO eventDAO = new EventDAO();

    // GsonBuilder.serializeNulls():
    // => field nào null vẫn được đưa ra JSON (FE dễ xử lý)
    private final Gson gson = new GsonBuilder()
            .serializeNulls()
            .create();

    /**
     * doOptions: xử lý CORS preflight
     * Browser sẽ gọi OPTIONS trước GET nếu có CORS
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * doGet: API chính để lấy danh sách sự kiện
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        // Set CORS để FE gọi được từ browser
        setCorsHeaders(response, request);

        // Response trả JSON UTF-8
        response.setContentType("application/json;charset=UTF-8");

        // ❌ Không check JWT / role
        // => Guest (chưa login) vẫn xem được danh sách event
        // => Phù hợp nghiệp vụ: quảng bá sự kiện công khai

        try {
            // =================================================
            // (1) Lấy toàn bộ Event từ database
            // =================================================
            // eventDAO.getAllEvents():
            // - Thường là query JOIN (event + area + venue)
            // - Trả về cả OPEN và CLOSED
            List<Event> events = eventDAO.getAllEvents();

            // Danh sách event mở
            List<EventListDto> openEvents = new ArrayList<>();

            // Danh sách event đóng
            List<EventListDto> closedEvents = new ArrayList<>();

            // =================================================
            // (2) Duyệt từng Event và map sang DTO
            // =================================================
            for (Event e : events) {

                // Tạo DTO chứa các field cần cho FE
                EventListDto dto = new EventListDto(
                        e.getEventId(),
                        e.getTitle(),
                        e.getDescription(),
                        e.getStartTime(),
                        e.getEndTime(),
                        e.getMaxSeats(),
                        e.getStatus(),
                        e.getBannerUrl()
                );

                // ===== Map thêm thông tin khu vực =====
                dto.setAreaId(e.getAreaId());
                dto.setAreaName(e.getAreaName());
                dto.setFloor(e.getFloor());

                // ===== Map thêm thông tin địa điểm =====
                dto.setVenueName(e.getVenueName());
                dto.setVenueLocation(e.getVenueLocation());

                // =================================================
                // (3) Phân loại theo trạng thái
                // =================================================
                if ("OPEN".equalsIgnoreCase(e.getStatus())) {
                    openEvents.add(dto);
                } else if ("CLOSED".equalsIgnoreCase(e.getStatus())) {
                    closedEvents.add(dto);
                }
                // Nếu có status khác (VD: DRAFT) thì không add
            }

            // =================================================
            // (4) Đóng gói JSON trả về FE
            // =================================================
            // Cấu trúc:
            // {
            //   "openEvents":   [ ... ],
            //   "closedEvents": [ ... ]
            // }
            Map<String, Object> result = new HashMap<>();
            result.put("openEvents", openEvents);
            result.put("closedEvents", closedEvents);

            // Convert Java object -> JSON string
            String json = gson.toJson(result);

            // Ghi JSON ra response
            try (PrintWriter out = response.getWriter()) {
                out.write(json);
            }

        } catch (SQLException ex) {
            // =================================================
            // (5) Lỗi DB
            // =================================================
            ex.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            try (PrintWriter out = response.getWriter()) {
                out.write("{\"message\":\"Internal server error when loading events\"}");
            }

        } catch (ClassNotFoundException ex) {
            // =================================================
            // (6) Lỗi load JDBC driver (hiếm khi xảy ra)
            // =================================================
            Logger.getLogger(GetAllEventsController.class.getName())
                  .log(Level.SEVERE, null, ex);
        }
    }

    /**
     * setCorsHeaders:
     * - Cho phép FE gọi API từ browser
     * - Whitelist domain FE (localhost + ngrok)
     */
    private void setCorsHeaders(HttpServletResponse res, HttpServletRequest req) {

        // Lấy origin từ request (domain FE)
        String origin = req.getHeader("Origin");

        boolean allowed = origin != null && (
                   origin.equals("http://localhost:5173")
                || origin.equals("http://127.0.0.1:5173")
                || origin.equals("http://localhost:3000")
                || origin.equals("http://127.0.0.1:3000")
                || origin.contains("ngrok-free.app")
                || origin.contains("ngrok.app")
        );

        if (allowed) {
            // Cho phép đúng origin gọi API
            res.setHeader("Access-Control-Allow-Origin", origin);
            res.setHeader("Access-Control-Allow-Credentials", "true");
        } else {
            // Origin không hợp lệ -> browser sẽ chặn
            res.setHeader("Access-Control-Allow-Origin", "null");
        }

        // Bắt buộc để tránh cache CORS sai origin
        res.setHeader("Vary", "Origin");

        // Cho phép các HTTP method
        res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");

        // Cho phép các header cần thiết
        res.setHeader("Access-Control-Allow-Headers",
                "Content-Type, Authorization, ngrok-skip-browser-warning");

        // Header nào FE được đọc
        res.setHeader("Access-Control-Expose-Headers", "Authorization");

        // Cache preflight 24h
        res.setHeader("Access-Control-Max-Age", "86400");
    }
}
