package controller;

import DAO.SeatDAO;
import DAO.EventSeatLayoutDAO;
import DTO.Seat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * API: GET /api/seats
 *
 * MỤC ĐÍCH: - Trả danh sách ghế để FE vẽ sơ đồ ghế. - Có 2 mode: (1) Mode theo
 * EVENT (khi student mua vé): dùng eventId -> lấy ghế từ Event_Seat_Layout join
 * Seat (2) Mode ghế vật lý theo AREA (khi cấu hình/ quản lý venue): không có
 * eventId -> lấy ghế theo areaId từ Seat
 *
 * THIẾT KẾ DB: - Seat: ghế vật lý của địa điểm (venue/area). Không phụ thuộc
 * event. - Event_Seat_Layout: cấu hình ghế cho từng event (event_id + seat_id +
 * seat_type + status). => Layout chỉ là cấu hình: event mở bán ghế nào, loại
 * ghế nào (VIP/Standard). => Trạng thái “đã đặt/đã bán” không nên lưu ở layout,
 * mà nằm ở Ticket (PENDING/BOOKED) vì đó là giao dịch.
 *
 *
 * /**
 * API: GET /api/seats
 *
 * STATUS CODE TỔNG QUAN: - 200 OK: trả danh sách ghế thành công - 400 Bad
 * Request: sai/thiếu tham số (eventId/areaId không hợp lệ) - 500 Internal
 * Server Error: lỗi server (exception không mong muốn)
 */
@WebServlet("/api/seats")
public class GetAllSeatsController extends HttpServlet {

    // DAO thao tác bảng Seat (ghế vật lý theo area)
    private final SeatDAO seatDAO = new SeatDAO();

    // DAO thao tác bảng Event_Seat_Layout (layout ghế theo event) + join Seat để lấy seat_code/row/col...
    private final EventSeatLayoutDAO eventSeatLayoutDAO = new EventSeatLayoutDAO();

    // Gson trả JSON đẹp để debug dễ hơn
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * doGet: FE gọi API này để lấy danh sách ghế.
     *
     * INPUT (query params): - eventId (optional): nếu có -> ưu tiên lấy layout
     * theo event - seatType (optional): lọc loại ghế (VIP/Standard...) khi lấy
     * theo event - areaId (optional/required): nếu không có eventId thì bắt
     * buộc có areaId để lấy ghế vật lý
     *
     * OUTPUT: - JSON wrapper: { eventId, areaId, seatType, total, seats: [...]
     * }
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setContentType("application/json;charset=UTF-8");

        try {
            // ===== 1) Lấy params =====
            String areaIdStr = req.getParameter("areaId");
            String seatType = req.getParameter("seatType"); // optional, chỉ ý nghĩa khi lấy theo event
            String eventIdStr = req.getParameter("eventId");  // có => mode theo event

            List<Seat> seats;
            Integer eventId = null;
            Integer areaId = null;

            // =====================================================
            // CASE 1: CÓ eventId -> lấy ghế THEO EVENT
            // Dùng khi: student vào trang chọn ghế để mua vé cho event cụ thể
            // =====================================================
            if (eventIdStr != null && !eventIdStr.trim().isEmpty()) {

                // 1.1) Validate eventId (tránh user truyền bậy)
                try {
                    eventId = Integer.parseInt(eventIdStr.trim());
                } catch (NumberFormatException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"error\":\"Invalid eventId\"}");
                    return;
                }

                /**
                 * 1.2) Lấy danh sách ghế theo event: - Query thường là:
                 * Event_Seat_Layout JOIN Seat - Vì Seat chứa
                 * seat_code/row_no/col_no/area_id... - Event_Seat_Layout chứa
                 * seat_type, status theo event (mở/khóa ghế trong event) -
                 * seatType nếu có thì lọc ghế VIP/Standard...
                 */
                seats = eventSeatLayoutDAO.getSeatsForEvent(eventId, seatType);

                /**
                 * 1.3) Xác định areaId: - Nếu có ghế thì lấy areaId từ ghế đầu
                 * tiên (vì ghế đó đã join từ Seat) - Nếu danh sách rỗng: event
                 * chưa cấu hình layout -> trả empty list
                 */
                if (seats != null && !seats.isEmpty()) {
                    areaId = seats.get(0).getAreaId();
                } else {
                    // Nếu FE có gửi areaId thì dùng tạm (không bắt buộc)
                    areaId = (areaIdStr != null && !areaIdStr.isEmpty())
                            ? Integer.parseInt(areaIdStr)
                            : null;
                }

                // =====================================================
                // CASE 2: KHÔNG có eventId -> chỉ trả ghế VẬT LÝ theo area
                // Dùng khi: quản lý / cấu hình ghế theo khu vực venue
                // =====================================================
            } else {

                // 2.1) Nếu không có eventId thì areaId bắt buộc
                if (areaIdStr == null || areaIdStr.trim().isEmpty()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"error\":\"Missing areaId or eventId\"}");
                    return;
                }

                // 2.2) Validate areaId
                try {
                    areaId = Integer.parseInt(areaIdStr.trim());
                } catch (NumberFormatException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"error\":\"Invalid areaId\"}");
                    return;
                }

                /**
                 * 2.3) Lấy ghế vật lý theo areaId: -
                 * SeatDAO.getSeatsByVenue(areaId) - Lúc này không quan tâm
                 * seatType vì seatType thuộc cấu hình event
                 */
                seats = seatDAO.getSeatsByVenue(areaId);
            }

            // =====================================================
            // 3) Sắp xếp ghế theo row/col để FE render đúng thứ tự
            // =====================================================
            /**
             * Vì row_no là ký tự (A,B,C...) còn col_no là số (1,2,3...) -> sort
             * theo row trước, sau đó sort theo col tăng dần.
             *
             * Lý do thầy hay hỏi: - Sort backend giúp FE chỉ việc render, đỡ xử
             * lý logic sort - Đảm bảo mọi FE/Client đều nhận thứ tự ghế thống
             * nhất
             */
            if (seats != null) {
                seats.sort(new Comparator<Seat>() {
                    @Override
                    public int compare(Seat s1, Seat s2) {
                        String r1 = s1.getRowNo() != null ? s1.getRowNo() : "";
                        String r2 = s2.getRowNo() != null ? s2.getRowNo() : "";

                        // So sánh row trước (A < B < C)
                        int cmpRow = r1.compareToIgnoreCase(r2);
                        if (cmpRow != 0) {
                            return cmpRow;
                        }

                        // Nếu cùng row thì so sánh col (1 < 2 < 3)
                        int c1 = parseColNumber(s1.getColNo());
                        int c2 = parseColNumber(s2.getColNo());
                        return Integer.compare(c1, c2);
                    }
                });
            }

            // =====================================================
            // 4) Build response JSON
            // =====================================================
            /**
             * Response wrapper giúp FE dễ xử lý: - eventId: event nào (nếu có)
             * - areaId: khu vực nào - seatType: filter đang dùng - total: tổng
             * số ghế trả về - seats: danh sách ghế chi tiết (Seat DTO)
             */
            SeatResponse seatResponse = new SeatResponse();
            seatResponse.setEventId(eventId);
            seatResponse.setAreaId(areaId != null ? areaId : 0);
            seatResponse.setSeatType(seatType);
            seatResponse.setTotal(seats != null ? seats.size() : 0);
            seatResponse.setSeats(seats);

            String json = gson.toJson(seatResponse);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(json);

        } catch (Exception e) {
            // Nếu lỗi bất kỳ -> trả 500 để FE biết backend lỗi
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Server error while loading seats\"}");
        }
    }

    /**
     * parseColNumber: - colNo trong DB có thể là NVARCHAR ("1","2","03") -
     * convert sang int để sort đúng - nếu lỗi parse -> trả 0 để tránh crash
     * comparator
     */
    private static int parseColNumber(String colNo) {
        if (colNo == null) {
            return 0;
        }
        try {
            return Integer.parseInt(colNo.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * SeatResponse: class wrapper nhỏ để đóng gói dữ liệu trả về FE FE nhận
     * đúng format, dễ dùng.
     */
    private static class SeatResponse {

        private Integer eventId;
        private int areaId;
        private String seatType;
        private int total;
        private List<Seat> seats;

        public Integer getEventId() {
            return eventId;
        }

        public void setEventId(Integer eventId) {
            this.eventId = eventId;
        }

        public int getAreaId() {
            return areaId;
        }

        public void setAreaId(int areaId) {
            this.areaId = areaId;
        }

        public String getSeatType() {
            return seatType;
        }

        public void setSeatType(String seatType) {
            this.seatType = seatType;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public List<Seat> getSeats() {
            return seats;
        }

        public void setSeats(List<Seat> seats) {
            this.seats = seats;
        }
    }

    /**
     * setCorsHeaders: - Cho phép FE từ localhost / ngrok gọi API (tránh lỗi
     * CORS) - Nếu origin không nằm trong whitelist -> trả "null" origin
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
        res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers",
                "Content-Type, Authorization, ngrok-skip-browser-warning");
        res.setHeader("Access-Control-Expose-Headers", "Authorization");
        res.setHeader("Access-Control-Max-Age", "86400");
    }
}
