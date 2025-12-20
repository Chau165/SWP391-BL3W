package controller;

/**
 * ========================================================================================================
 * CONTROLLER: MyBillsController - LỊCH SỬ HÓA ĐƠN CỦA SINH VIÊN (Student Bill History)
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - Hiển thị danh sách tất cả các hóa đơn (bills) của sinh viên đã thanh toán
 * - Bao gồm thông tin: mã hóa đơn, tổng tiền, phương thức thanh toán, trạng thái, thời gian
 * - Được bảo vệ bởi JWT Authentication
 * 
 * ENDPOINT: GET /api/payment/my-bills
 * 
 * LUỒNG XỬ LÝ:
 * 1. JwtAuthFilter kiểm tra JWT token trong header Authorization
 * 2. JwtAuthFilter giải mã userId và đặt vào request.setAttribute("userId", ...)
 * 3. Controller lấy userId từ request attribute
 * 4. Gọi BillDAO.getBillsByUserId(userId) để query database
 * 5. BillDAO thực hiện SQL JOIN:
 *    - Bill: thông tin hóa đơn
 *    - Users: thông tin người dùng (full_name)
 * 6. Trả về danh sách BillResponse dạng JSON
 * 
 * DATABASE MAPPING:
 * - Bảng chính: Bill (bill_id, user_id, total_amount, currency, payment_method, payment_status, created_at)
 * - Bảng liên kết: Users (user_id, full_name)
 * - Quan hệ: Bill -> Users (n-1), mỗi Bill thuộc về 1 User
 * - Bill KHÔNG lưu event_id (1 bill có thể chứa nhiều vé từ nhiều event khác nhau)
 * - Mối quan hệ Bill -> Ticket (1-n): 1 hóa đơn có thể có nhiều vé
 * 
 * KẾT NỐI FILE:
 * - Filter: filter/JwtAuthFilter.java (xác thực JWT)
 * - DAO: DAO/BillDAO.java (truy vấn database)
 * - DTO: DTO/BillResponse.java (cấu trúc dữ liệu trả về)
 * - Utils: utils/JwtUtils.java (xử lý JWT)
 * - Config: mylib/DBUtils.java (kết nối SQL Server)
 */

import DAO.BillDAO;
import DTO.BillResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet("/api/payment/my-bills")
public class MyBillsController extends HttpServlet {

    // DAO để truy vấn bảng Bill trong SQL Server
    // Kết nối: BillDAO -> DBUtils.getConnection() -> SQL Server
    private final BillDAO billDAO = new BillDAO();
    
    // Gson để chuyển đổi List<BillResponse> -> JSON
    private final Gson gson = new Gson();

    /**
     * ====================================================================================================
     * METHOD: setCorsHeaders - THIẾT LẬP CORS HEADERS
     * ====================================================================================================
     * (Giống MyTicketController - cho phép FE cross-origin gọi API)
     */
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
        res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, ngrok-skip-browser-warning");
        res.setHeader("Access-Control-Expose-Headers", "Authorization");
        res.setHeader("Access-Control-Max-Age", "86400");
    }

    /**
     * ====================================================================================================
     * METHOD: doOptions - XỬ LÝ PREFLIGHT REQUEST (CORS)
     * ====================================================================================================
     * Xử lý OPTIONS request từ browser trước khi gửi GET request thực sự
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * ====================================================================================================
     * METHOD: doGet - LẤY DANH SÁCH HÓA ĐƠN CỦA USER
     * ====================================================================================================
     * 
     * ENDPOINT: GET /api/payment/my-bills
     * AUTHENTICATION: Bắt buộc JWT Token
     * 
     * RESPONSE SUCCESS (200):
     * [
     *   {
     *     "billId": 456,
     *     "totalAmount": 500000,
     *     "currency": "VND",
     *     "paymentMethod": "VNPAY",
     *     "paymentStatus": "PAID",
     *     "createdAt": "2025-01-10T10:30:00",
     *     "userName": "Nguyễn Văn A"
     *   },
     *   ...
     * ]
     * 
     * LUỒNG XỬ LÝ:
     * 1. JwtAuthFilter đã validate token và set userId vào request attribute
     * 2. Controller lấy userId từ request.getAttribute("userId")
     * 3. Gọi BillDAO.getBillsByUserId(userId)
     * 4. BillDAO query SQL:
     *    SELECT b.bill_id, b.total_amount, b.currency, b.payment_method, 
     *           b.payment_status, b.created_at, u.full_name
     *    FROM Bill b
     *    JOIN Users u ON b.user_id = u.user_id
     *    WHERE b.user_id = ?
     *    ORDER BY b.created_at DESC
     * 5. Chuyển ResultSet -> List<BillResponse>
     * 6. Trả về JSON
     * 
     * DATABASE:
     * - Bill: Lưu thông tin hóa đơn (payment_method: VNPAY, payment_status: PAID/PENDING)
     * - Users: Lấy full_name để hiển thị
     * - Ticket: Link với Bill qua bill_id (1 Bill -> nhiều Tickets)
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req); // Thiết lập CORS headers
        resp.setContentType("application/json;charset=UTF-8"); // Response dạng JSON UTF-8

        // Lấy userId từ request attribute (đã được JwtAuthFilter set sẵn)
        Object uidObj = req.getAttribute("userId");
        if (uidObj == null) {
            // Không có userId = JWT không hợp lệ hoặc chưa login
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            resp.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }

        // Parse userId từ Object sang int
        // Xử lý cả trường hợp Integer và String (phòng trường hợp attribute bị convert)
        int userId;
        try {
            if (uidObj instanceof Integer) {
                userId = (Integer) uidObj;
            } else {
                userId = Integer.parseInt(uidObj.toString());
            }
        } catch (Exception e) {
            // Parse lỗi = token bị corrupt
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\":\"Invalid userId in token\"}");
            return;
        }

        // Gọi DAO để lấy danh sách hóa đơn từ database
        // BillDAO.getBillsByUserId() JOIN với Users để lấy full_name
        // Query: SELECT b.*, u.full_name FROM Bill b JOIN Users u ON b.user_id = u.user_id WHERE b.user_id = ?
        List<BillResponse> bills = billDAO.getBillsByUserId(userId);
        
        // Trả về danh sách bills dạng JSON
        resp.setStatus(HttpServletResponse.SC_OK); // 200
        resp.getWriter().write(gson.toJson(bills)); // Convert List -> JSON array
    }
}