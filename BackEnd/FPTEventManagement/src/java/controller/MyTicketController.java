package controller;

/**
 * ========================================================================================================
 * CONTROLLER: MyTicketController - LỊCH SỬ VÉ CỦA SINH VIÊN (Ticket Trading History / Student Ticket History)
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - Hiển thị danh sách tất cả các vé (tickets) mà sinh viên đã mua/đăng ký
 * - Bao gồm thông tin chi tiết: tên sự kiện, loại vé, ghế ngồi, trạng thái vé, mã QR
 * - Được bảo vệ bởi JWT Authentication - chỉ user đã đăng nhập mới xem được
 * 
 * ENDPOINT: GET /api/registrations/my-tickets
 * 
 * LUỒNG XỬ LÝ:
 * 1. JWT Filter (JwtAuthFilter.java) kiểm tra token trong header Authorization
 * 2. JWT Filter giải mã token và đặt userId vào request.setAttribute("userId", ...)
 * 3. Controller lấy userId từ request attribute
 * 4. Gọi TicketDAO.getTicketsByUserId(userId) để truy vấn database
 * 5. TicketDAO thực hiện SQL JOIN nhiều bảng:
 *    - Ticket: thông tin vé
 *    - Event: thông tin sự kiện  
 *    - CategoryTicket: loại vé (VIP, Regular, Standard)
 *    - Seat: mã ghế ngồi
 *    - VenueArea: khu vực (Hall A, Hall B)
 *    - Venue: địa điểm tổ chức
 * 6. Trả về danh sách MyTicketResponse dạng JSON
 * 
 * DATABASE MAPPING:
 * - Bảng chính: Ticket (ticket_id, event_id, user_id, category_ticket_id, seat_id, status, qr_code_value)
 * - Bảng liên kết: Event, CategoryTicket, Seat, VenueArea, Venue
 * - Quan hệ: Ticket -> Event (1-n), Ticket -> Seat (1-1), Ticket -> CategoryTicket (n-1)
 * 
 * KẾT NỐI FILE:
 * - Filter: filter/JwtAuthFilter.java (xác thực JWT trước khi vào controller)
 * - DAO: DAO/TicketDAO.java (truy vấn database)
 * - DTO: DTO/MyTicketResponse.java (cấu trúc dữ liệu trả về)
 * - Utils: utils/JwtUtils.java (xử lý JWT token)
 * - Config: mylib/DBUtils.java (kết nối SQL Server)
 */

import DAO.TicketDAO;
import DTO.MyTicketResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@WebServlet("/api/registrations/my-tickets")
public class MyTicketController extends HttpServlet {

    // DAO để truy vấn bảng Ticket trong SQL Server
    // Kết nối qua: TicketDAO -> DBUtils.getConnection() -> SQL Server
    private final TicketDAO ticketDAO = new TicketDAO();
    
    // Gson để chuyển đổi List<MyTicketResponse> -> JSON
    // serializeNulls(): cho phép trả về field null trong JSON (tránh FE bị lỗi)
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    /**
     * ====================================================================================================
     * METHOD: doOptions - XỬ LÝ PREFLIGHT REQUEST (CORS)
     * ====================================================================================================
     * 
     * MỤC ĐÍCH:
     * - Xử lý preflight request từ browser khi FE gọi API từ domain khác (localhost:3000 -> backend)
     * - Browser tự động gửi OPTIONS request trước khi gửi GET/POST thực sự
     * 
     * CORS (Cross-Origin Resource Sharing):
     * - Cho phép FE (React) chạy trên http://localhost:3000 gọi API backend
     * - Thiết lập header Access-Control-Allow-* để browser cho phép request
     * 
     * FLOW:
     * 1. Browser phát hiện cross-origin request
     * 2. Browser tự động gửi OPTIONS request
     * 3. Server trả về 200 OK + CORS headers
     * 4. Browser cho phép request thực sự (GET) được gửi đi
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req); // Thiết lập CORS headers
        resp.setStatus(HttpServletResponse.SC_OK); // Trả về 200 OK
    }

    /**
     * ====================================================================================================
     * METHOD: doGet - LẤY DANH SÁCH VÉ CỦA USER
     * ====================================================================================================
     * 
     * ENDPOINT: GET /api/registrations/my-tickets
     * AUTHENTICATION: Bắt buộc JWT Token trong header Authorization
     * 
     * REQUEST:
     * - Header: Authorization: Bearer <JWT_TOKEN>
     * - JWT Token được JwtAuthFilter kiểm tra trước khi vào đây
     * 
     * RESPONSE SUCCESS (200):
     * [
     *   {
     *     "ticketId": 123,
     *     "eventTitle": "Workshop AI 2025",
     *     "categoryName": "VIP",
     *     "seatCode": "A-01",
     *     "status": "BOOKED",
     *     "qrCodeValue": "base64_encoded_qr_image...",
     *     "startTime": "2025-01-15T14:00:00",
     *     "venueName": "FPT Hòa Lạc",
     *     "areaName": "Hall A"
     *   },
     *   ...
     * ]
     * 
     * RESPONSE ERROR:
     * - 401 Unauthorized: Thiếu userId (JWT không hợp lệ hoặc không có trong request)
     * - 500 Internal Server Error: Lỗi database
     * 
     * LUỒNG XỬ LÝ:
     * 1. JwtAuthFilter.doFilter() đã chạy trước:
     *    - Kiểm tra header "Authorization: Bearer <token>"
     *    - Validate token bằng JwtUtils.validateToken()
     *    - Giải mã userId từ token
     *    - Đặt userId vào request: request.setAttribute("userId", userIdFromToken)
     * 
     * 2. Controller lấy userId từ request attribute
     * 3. Gọi TicketDAO.getTicketsByUserId(userId)
     * 4. TicketDAO thực hiện SQL query:
     *    SELECT t.ticket_id, e.title, ct.name, s.seat_code, t.status, t.qr_code_value, ...
     *    FROM Ticket t
     *    LEFT JOIN Event e ON t.event_id = e.event_id
     *    LEFT JOIN CategoryTicket ct ON t.category_ticket_id = ct.category_ticket_id
     *    LEFT JOIN Seat s ON t.seat_id = s.seat_id
     *    LEFT JOIN VenueArea va ON s.area_id = va.area_id
     *    LEFT JOIN Venue v ON va.venue_id = v.venue_id
     *    WHERE t.user_id = ?
     * 5. Chuyển ResultSet -> List<MyTicketResponse>
     * 6. Gson chuyển List -> JSON string
     * 7. Trả về JSON cho FE
     * 
     * DATABASE TABLES INVOLVED:
     * - Ticket: Lưu thông tin vé đã mua
     * - Event: Thông tin sự kiện
     * - CategoryTicket: Loại vé (VIP, Standard, Free)
     * - Seat: Ghế ngồi (seat_code: A-01, B-12, ...)
     * - VenueArea: Khu vực trong địa điểm (Hall A, Hall B, ...)
     * - Venue: Địa điểm tổ chức (FPT Hòa Lạc, FPT TP.HCM, ...)
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setCorsHeaders(response, request); // Thiết lập CORS để FE có thể đọc response
        response.setContentType("application/json;charset=UTF-8"); // Trả về JSON với encoding UTF-8

        // Lấy userId từ request attribute (đã được JwtAuthFilter set sẵn)
        // JwtAuthFilter đã validate token và extract userId từ JWT claims
        Integer userId = (Integer) request.getAttribute("userId");
        
        // Kiểm tra userId có tồn tại không
        // Nếu null = JWT không hợp lệ hoặc JwtAuthFilter bị bypass
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 Unauthorized
            try (PrintWriter out = response.getWriter()) {
                out.write("{\"message\":\"Unauthorized: missing userId\"}");
            }
            return; // Dừng xử lý, không cho phép truy cập
        }

        try {
            // Gọi DAO để lấy danh sách vé từ database
            // TicketDAO kết nối tới SQL Server qua DBUtils.getConnection()
            // Query JOIN nhiều bảng để lấy đầy đủ thông tin: event, category, seat, venue
            List<MyTicketResponse> tickets = ticketDAO.getTicketsByUserId(userId);
            
            // Chuyển đổi List<MyTicketResponse> thành JSON string
            // Gson tự động map các field của MyTicketResponse thành JSON properties
            String json = gson.toJson(tickets);
            
            // Ghi JSON vào response body và trả về cho FE
            try (PrintWriter out = response.getWriter()) {
                out.write(json); // Trả về danh sách vé dạng JSON array
            }
        } catch (Exception e) {
            // Log lỗi ra console để debug
            e.printStackTrace();
            
            // Trả về lỗi 500 nếu có vấn đề với database hoặc logic xử lý
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.write("{\"message\":\"Internal server error when loading tickets\"}");
            }
        }
    }

    /**
     * ====================================================================================================
     * METHOD: setCorsHeaders - THIẾT LẬP CORS HEADERS
     * ====================================================================================================
     * 
     * MỤC ĐÍCH:
     * - Cho phép FE (React/Vue/Angular) chạy trên domain khác gọi API
     * - Whitelist các domain được phép: localhost:3000, localhost:5173, ngrok
     * 
     * CORS HEADERS:
     * - Access-Control-Allow-Origin: Domain được phép gọi API
     * - Access-Control-Allow-Credentials: Cho phép gửi cookies/credentials
     * - Access-Control-Allow-Methods: Các HTTP methods được phép (GET, POST, OPTIONS)
     * - Access-Control-Allow-Headers: Các headers FE được phép gửi (Authorization, Content-Type)
     * - Access-Control-Expose-Headers: Các headers FE được phép đọc từ response
     * - Access-Control-Max-Age: Thời gian cache preflight request (86400s = 24h)
     * 
     * WHITELISTED ORIGINS:
     * - http://localhost:5173 (Vite dev server)
     * - http://localhost:3000 (Create React App, Next.js)
     * - *.ngrok-free.app / *.ngrok.app (Ngrok tunneling cho test mobile)
     * 
     * FLOW:
     * 1. Lấy Origin header từ request (domain mà FE đang chạy)
     * 2. Kiểm tra Origin có trong whitelist không
     * 3. Nếu có: Set Access-Control-Allow-Origin = Origin đó (cho phép)
     * 4. Nếu không: Set Access-Control-Allow-Origin = "null" (block)
     */
    private void setCorsHeaders(HttpServletResponse res, HttpServletRequest req) {
        // Lấy Origin header từ request (domain của FE)
        // VD: http://localhost:3000, http://localhost:5173
        String origin = req.getHeader("Origin");

        // Kiểm tra origin có trong danh sách whitelist không
        // Cho phép localhost (dev) và ngrok (test mobile/public demo)
        boolean allowed = origin != null && (origin.equals("http://localhost:5173")
                || origin.equals("http://127.0.0.1:5173")
                || origin.equals("http://localhost:3000")
                || origin.equals("http://127.0.0.1:3000")
                || origin.contains("ngrok-free.app")
                || origin.contains("ngrok.app"));

        if (allowed) {
            // Cho phép origin này gọi API
            res.setHeader("Access-Control-Allow-Origin", origin);
            // Cho phép gửi credentials (cookies, authorization headers)
            res.setHeader("Access-Control-Allow-Credentials", "true");
        } else {
            // Block origin không trong whitelist
            res.setHeader("Access-Control-Allow-Origin", "null");
        }

        // Header Vary: Origin -> báo cho browser/proxy biết response phụ thuộc vào Origin
        // Giúp browser cache đúng cho từng origin khác nhau
        res.setHeader("Vary", "Origin");
        
        // Cho phép các HTTP methods
        res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        
        // Cho phép FE gửi các headers này trong request
        // Authorization: JWT token
        // Content-Type: application/json
        // ngrok-skip-browser-warning: skip ngrok warning page
        res.setHeader("Access-Control-Allow-Headers",
                "Content-Type, Authorization, ngrok-skip-browser-warning");
        
        // Cho phép FE đọc Authorization header từ response
        // (Dùng khi backend trả về JWT token mới trong response header)
        res.setHeader("Access-Control-Expose-Headers", "Authorization");
        
        // Cache preflight request trong 24h (86400 giây)
        // Browser không cần gửi OPTIONS request lại trong 24h
        res.setHeader("Access-Control-Max-Age", "86400");
    }

}