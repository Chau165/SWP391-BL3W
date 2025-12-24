package DAO;

/**
 * ========================================================================================================
 * DAO: TicketDAO - DATA ACCESS OBJECT CHO BẢNG TICKET
 * ========================================================================================================
 *
 * CHỨC NĂNG CHÍNH: - Thực hiện tất cả các thao tác CRUD (Create, Read, Update,
 * Delete) với bảng Ticket - Xử lý các query phức tạp: JOIN nhiều bảng để lấy
 * thông tin đầy đủ về vé - Quản lý QR code: insert ticket với placeholder, sau
 * đó update QR code value - Xử lý checkin/checkout: cập nhật trạng thái vé và
 * thời gian - Thống kê: đếm số vé, tỷ lệ checkin/checkout theo sự kiện
 *
 * DATABASE CONNECTION: - Kết nối SQL Server qua mylib.DBUtils.getConnection() -
 * Sử dụng PreparedStatement để tránh SQL Injection - Auto-close resources với
 * try-with-resources
 *
 * METHODS CHÍNH: 1. insertTicket() - Tạo vé mới (legacy, giữ để tương thích) 2.
 * insertTicketAndReturnId() - Tạo vé và trả về ticket_id để sinh QR 3.
 * updateTicketQr() - Cập nhật QR code sau khi sinh 4. getTicketById() - Lấy
 * thông tin chi tiết 1 vé 5. getTicketsByUserId() - Lấy danh sách vé của user
 * (JOIN Event, Venue) 6. checkinTicket() - Check-in vé (quét QR) 7.
 * checkoutTicket() - Check-out vé 8. getEventStats() - Thống kê vé theo sự kiện
 * 9. findTicketsByIds() - Lấy nhiều vé theo danh sách ID (batch) 10.
 * updateTicketAfterPayment() - Cập nhật vé sau thanh toán 11.
 * deleteTicketsByIds() - Xóa nhiều vé (batch)
 *
 * SỬ DỤNG: - Controller: MyTicketController, RegistrationController,
 * CheckinController - Service: QR generation, payment processing, statistics
 */
import DTO.EventStatsResponse;
import DTO.MyTicketResponse;
import DTO.Ticket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import mylib.DBUtils;

public class TicketDAO {

    /**
     * ================================================================================================
     * METHOD: insertTicket - TẠO VÉ MỚI (LEGACY VERSION)
     * ================================================================================================
     *
     * MỤC ĐÍCH: - Insert 1 ticket mới vào database với đầy đủ thông tin - Phiên
     * bản cũ, vẫn giữ để các module khác còn dùng
     *
     * THAM SỐ: - t: Ticket object chứa đầy đủ thông tin (eventId, userId,
     * categoryTicketId, qrCodeValue...)
     *
     * TRẢ VỀ: - true: Insert thành công - false: Insert thất bại (constraint
     * violation, connection error...)
     *
     * BUSINESS LOGIC: - Các field nullable (billId, seatId, checkinTime) được
     * xử lý bằng setNull() - qr_issued_at mặc định = current timestamp nếu
     * không truyền - Bắt SQLIntegrityConstraintViolationException riêng để log
     * constraint errors
     *
     * NOTE: - Method này insert luôn qrCodeValue, phù hợp khi đã có QR sẵn -
     * Nếu muốn sinh QR sau, dùng insertTicketAndReturnId()
     */
    // ✅ HÀM CŨ - vẫn giữ, nếu chỗ khác còn dùng
    public boolean insertTicket(Ticket t) {
        String sql = "INSERT INTO Ticket "
                + " (event_id, user_id, category_ticket_id, bill_id, seat_id, "
                + "  qr_code_value, qr_issued_at, status, checkin_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            // Set các field bắt buộc (NOT NULL trong database)
            ps.setInt(1, t.getEventId());
            ps.setInt(2, t.getUserId());
            ps.setInt(3, t.getCategoryTicketId());

            // billId: nullable - null nếu vé miễn phí
            if (t.getBillId() != null) {
                ps.setInt(4, t.getBillId());
            } else {
                ps.setNull(4, Types.INTEGER);
            }

            // seatId: nullable - null nếu không chọn chỗ ngồi
            if (t.getSeatId() != null) {
                ps.setInt(5, t.getSeatId());
            } else {
                ps.setNull(5, Types.INTEGER);
            }

            // qr_code_value: NOT NULL - phải có giá trị
            ps.setString(6, t.getQrCodeValue());

            // qr_issued_at: Mặc định là thời gian hiện tại nếu không truyền
            Timestamp issuedAt = t.getQrIssuedAt();
            if (issuedAt != null) {
                ps.setTimestamp(7, issuedAt);
            } else {
                ps.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
            }

            // status: NOT NULL - trạng thái vé (BOOKED, CHECKED_IN...)
            ps.setString(8, t.getStatus());

            // checkin_time: nullable - null khi chưa checkin
            if (t.getCheckinTime() != null) {
                ps.setTimestamp(9, t.getCheckinTime());
            } else {
                ps.setNull(9, Types.TIMESTAMP);
            }

            int affected = ps.executeUpdate();
            return affected > 0; // Trả về true nếu insert thành công

        } catch (SQLIntegrityConstraintViolationException ex) {
            // Lỗi ràng buộc: FK không hợp lệ, duplicate key, CHECK constraint...
            System.err.println("[WARN] insertTicket - constraint violation: " + ex.getMessage());
            return false;
        } catch (Exception e) {
            // Lỗi khác: connection, SQL syntax...
            System.err.println("[ERROR] insertTicket: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * ================================================================================================
     * METHOD: insertTicketAndReturnId - TẠO VÉ VÀ TRẢ VỀ TICKET_ID (MỚI)
     * ================================================================================================
     *
     * MỤC ĐÍCH: - Insert ticket mới và trả về ticket_id được database tự động
     * sinh (IDENTITY) - Cho phép backend sinh QR code SAU khi có ticket_id
     *
     * WORKFLOW: 1. Insert ticket với qr_code_value = "PENDING_QR" (placeholder)
     * 2. Database sinh ticket_id tự động (IDENTITY) 3. Trả về ticket_id cho
     * caller 4. Caller dùng ticket_id để sinh QR code 5. Gọi updateTicketQr()
     * để cập nhật QR code thực
     *
     * TRẢ VỀ: - ticket_id (>0): Insert thành công - -1: Insert thất bại
     *
     * WHY PENDING_QR? - Cột qr_code_value là NOT NULL trong database - Không
     * thể insert NULL, phải có giá trị placeholder - "PENDING_QR" sẽ bị ghi đè
     * sau bằng Base64 QR image
     *
     * VÍ DỤ SỬ DỤNG: int ticketId = dao.insertTicketAndReturnId(ticket); String
     * qrBase64 = QRGenerator.generate(ticketId); dao.updateTicketQr(ticketId,
     * qrBase64);
     */
    // ✅ MỚI: Insert ticket và trả về ticket_id (chưa có QR)
    public int insertTicketAndReturnId(Ticket t) {
        String sql = "INSERT INTO Ticket "
                + " (event_id, user_id, category_ticket_id, bill_id, seat_id, "
                + "  qr_code_value, qr_issued_at, status, checkin_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // Set các tham số giống insertTicket()
            ps.setInt(1, t.getEventId());
            ps.setInt(2, t.getUserId());
            ps.setInt(3, t.getCategoryTicketId());

            if (t.getBillId() != null) {
                ps.setInt(4, t.getBillId());
            } else {
                ps.setNull(4, Types.INTEGER);
            }

            if (t.getSeatId() != null) {
                ps.setInt(5, t.getSeatId());
            } else {
                ps.setNull(5, Types.INTEGER);
            }

            // ❌ KHÔNG ĐƯỢC ĐỂ NULL VÌ CỘT qr_code_value NOT NULL
            // ps.setNull(6, Types.NVARCHAR);
            // ✅ DÙNG GIÁ TRỊ TẠM, SẼ BỊ GHI ĐÈ SAU BẰNG updateTicketQr(...)
            ps.setString(6, "PENDING_QR");

            Timestamp issuedAt = t.getQrIssuedAt();
            if (issuedAt != null) {
                ps.setTimestamp(7, issuedAt);
            } else {
                ps.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
            }

            ps.setString(8, t.getStatus());

            if (t.getCheckinTime() != null) {
                ps.setTimestamp(9, t.getCheckinTime());
            } else {
                ps.setNull(9, Types.TIMESTAMP);
            }

            int affected = ps.executeUpdate();
            if (affected == 0) {
                return -1; // Insert thất bại
            }

            // Lấy ticket_id vừa được sinh bởi IDENTITY
            try ( ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1); // ticket_id
                }
            }
            return -1;

        } catch (SQLIntegrityConstraintViolationException ex) {
            System.err.println("[WARN] insertTicketAndReturnId - constraint violation: " + ex.getMessage());
            return -1;
        } catch (Exception e) {
            System.err.println("[ERROR] insertTicketAndReturnId: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * ================================================================================================
     * METHOD: updateTicketQr - CẬP NHẬT QR CODE CHO VÉ
     * ================================================================================================
     *
     * MỤC ĐÍCH: - Cập nhật giá trị QR code (Base64) vào database sau khi sinh
     * QR - Cập nhật luôn qr_issued_at thành thời điểm hiện tại
     *
     * WORKFLOW: 1. insertTicketAndReturnId() tạo vé với qr_code_value =
     * "PENDING_QR" 2. Backend sinh QR code từ ticket_id (thư viện ZXing,
     * QRCode.js...) 3. Encode QR image thành Base64 string 4. Gọi
     * updateTicketQr() để lưu Base64 vào database
     *
     * THAM SỐ: - ticketId: ID của vé cần update - qrBase64: QR code dạng Base64
     * (data:image/png;base64,iVBORw0KG...)
     *
     * TRẢ VỀ: - true: Update thành công - false: Update thất bại (ticket không
     * tồn tại, connection error...)
     */
    // ✅ MỚI: cập nhật QR code (Base64) cho ticket
    public boolean updateTicketQr(int ticketId, String qrBase64) {
        String sql = "UPDATE Ticket "
                + "SET qr_code_value = ?, qr_issued_at = ? "
                + "WHERE ticket_id = ?";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, qrBase64);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setInt(3, ticketId);

            int rows = ps.executeUpdate();
            return rows > 0; // Trả về true nếu có ít nhất 1 row được update
        } catch (Exception e) {
            System.err.println("[ERROR] updateTicketQr: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * ================================================================================================
     * METHOD: getTicketById - LẤY THÔNG TIN CHI TIẾT 1 VÉ
     * ================================================================================================
     *
     * MỤC ĐÍCH: - Truy vấn và trả về toàn bộ thông tin của 1 vé theo ticket_id
     * - Dùng để kiểm tra vé khi checkin, xem chi tiết vé, validate QR code
     *
     * THAM SỐ: - ticketId: ID của vé cần lấy
     *
     * TRẢ VỀ: - Ticket object: Nếu tìm thấy - null: Nếu không tìm thấy hoặc có
     * lỗi
     *
     * SỬ DỤNG: - CheckinController: Validate vé trước khi checkin -
     * MyTicketController: Xem chi tiết 1 vé - QR Scanner: Verify QR code có hợp
     * lệ không
     */
    // Lấy ticket theo ID
    public Ticket getTicketById(int ticketId) {
        String sql = "SELECT ticket_id, event_id, user_id, category_ticket_id, "
                + "       bill_id, seat_id, qr_code_value, qr_issued_at, "
                + "       status, checkin_time, check_out_time "
                + "FROM Ticket WHERE ticket_id = ?";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, ticketId);

            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // Map ResultSet sang Ticket object
                    Ticket t = new Ticket();
                    t.setTicketId(rs.getInt("ticket_id"));
                    t.setEventId(rs.getInt("event_id"));
                    t.setUserId(rs.getInt("user_id"));
                    t.setCategoryTicketId(rs.getInt("category_ticket_id"));
                    t.setBillId((Integer) rs.getObject("bill_id")); // nullable
                    t.setSeatId((Integer) rs.getObject("seat_id")); // nullable
                    t.setQrCodeValue(rs.getString("qr_code_value"));
                    t.setQrIssuedAt(rs.getTimestamp("qr_issued_at"));
                    t.setStatus(rs.getString("status"));
                    t.setCheckinTime(rs.getTimestamp("checkin_time")); // nullable
                    t.setCheckoutTime(rs.getTimestamp("check_out_time")); // nullable
                    return t;
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getTicketById: " + e.getMessage());
            e.printStackTrace();
        }
        return null; // Không tìm thấy hoặc có lỗi
    }

    /**
     * ================================================================================================
     * METHOD: getTicketId - LẤY TICKET_ID THEO EVENT + USER + CATEGORY
     * ================================================================================================
     *
     * MỤC ĐÍCH: - Kiểm tra xem user đã đăng ký loại vé này cho sự kiện này chưa
     * - Tránh đăng ký trùng: 1 user không thể đăng ký 2 vé cùng loại cho 1 sự
     * kiện
     *
     * THAM SỐ: - eventId: ID sự kiện - userId: ID người dùng - categoryId: ID
     * loại vé (VIP, Regular, Free...)
     *
     * TRẢ VỀ: - ticket_id (>0): Đã tồn tại vé - 0: Chưa có vé (cho phép đăng ký
     * mới)
     *
     * SỬ DỤNG: - RegistrationController: Check duplicate trước khi tạo vé mới
     */
    /**
     * Get ticket id by event + user + category. Return 0 if not found.
     */
    public int getTicketId(int eventId, int userId, int categoryId) {
        String sql = "SELECT ticket_id FROM Ticket WHERE event_id = ? AND user_id = ? AND category_ticket_id = ?";
        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.setInt(2, userId);
            ps.setInt(3, categoryId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ticket_id");
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getTicketId: " + e.getMessage());
            e.printStackTrace();
        }
        return 0; // Không tìm thấy
    }

    /**
     * ================================================================================================
     * METHOD: checkinTicket - CHECK-IN VÉ (QUÉT QR TẠI CỔNG VÀO)
     * ================================================================================================
     *
     * MỤC ĐÍCH: - Cập nhật trạng thái vé từ BOOKED -> CHECKED_IN - Lưu thời
     * gian checkin thực tế - CHỈ cho phép checkin nếu vé đang ở trạng thái
     * BOOKED
     *
     * BUSINESS RULES: - Chỉ vé có status = 'BOOKED' mới được checkin - Vé đã
     * CHECKED_IN, CANCELLED, EXPIRED không thể checkin lại - WHERE clause đảm
     * bảo concurrency safety
     *
     * THAM SỐ: - ticketId: ID vé cần checkin - checkinTime: Thời gian checkin
     * thực tế (từ server hoặc mobile device)
     *
     * TRẢ VỀ: - true: Checkin thành công (cập nhật 1 row) - false: Checkin thất
     * bại (vé không hợp lệ hoặc đã checkin rồi)
     *
     * SỬ DỤNG: - CheckinController: API quét QR code để checkin - Mobile App:
     * Scan QR -> gọi API checkin
     */
    // Check-in ticket: chỉ cho update nếu đang BOOKED
    public boolean checkinTicket(int ticketId, Timestamp checkinTime) {
        String sql = "UPDATE Ticket "
                + "SET status = 'CHECKED_IN', checkin_time = ? "
                + "WHERE ticket_id = ? AND status = 'BOOKED'";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, checkinTime);
            ps.setInt(2, ticketId);

            int rows = ps.executeUpdate();
            return rows > 0; // true = checkin thành công
        } catch (Exception e) {
            System.err.println("[ERROR] checkinTicket: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * ================================================================================================
     * METHOD: getTicketsByUserId - LẤY DANH SÁCH VÉ CỦA USER (TICKET HISTORY)
     * ================================================================================================
     *
     * MỤC ĐÍCH: - Trả về danh sách tất cả các vé mà user đã đăng ký (My Tickets
     * / Ticket Trading History) - JOIN nhiều bảng để lấy thông tin đầy đủ:
     * Event, Venue, Status, QR code - Sắp xếp theo thời gian tạo QR (mới nhất
     * trước)
     *
     * SQL JOIN FLOW: 1. Ticket (bảng chính): thông tin vé 2. JOIN Event: lấy
     * tên sự kiện, thời gian bắt đầu 3. LEFT JOIN Venue_Area: lấy khu vực (có
     * thể null nếu online event) 4. LEFT JOIN Venue: lấy tên địa điểm (FPT Hòa
     * Lạc, FPT Arena...)
     *
     * THAM SỐ: - userId: ID của user cần lấy danh sách vé
     *
     * TRẢ VỀ: - List<MyTicketResponse>: Danh sách vé (có thể rỗng nếu user chưa
     * đăng ký vé nào)
     *
     * OUTPUT FIELDS: - ticketId, ticketCode (QR Base64), status, checkInTime,
     * checkOutTime - eventName, startTime, venueName
     *
     * SỬ DỤNG: - MyTicketController: API GET /api/registrations/my-tickets -
     * Frontend: Màn hình "My Tickets" / "Lịch sử vé"
     *
     * ORDER BY: - qr_issued_at DESC: Vé mới nhất hiển thị trước (recently
     * registered first)
     */
    // New: Lấy danh sách vé (kèm thông tin Event + Venue) theo user_id
    public List<MyTicketResponse> getTicketsByUserId(int userId) {
        String sql = "SELECT t.ticket_id, t.qr_code_value, t.status, t.checkin_time, t.check_out_time, "
                + " e.title AS event_name, e.start_time AS start_time, v.venue_name AS venue_name "
                + "FROM Ticket t "
                + "JOIN Event e ON t.event_id = e.event_id "
                + "LEFT JOIN Venue_Area va ON e.area_id = va.area_id "
                + "LEFT JOIN Venue v ON va.venue_id = v.venue_id "
                + "WHERE t.user_id = ? "
                + "ORDER BY t.qr_issued_at DESC";

        List<MyTicketResponse> result = new ArrayList<>();

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // Map từng row trong ResultSet sang MyTicketResponse object
                    MyTicketResponse m = new MyTicketResponse();
                    m.setTicketId(rs.getInt("ticket_id"));
                    m.setTicketCode(rs.getString("qr_code_value")); // Base64 QR image
                    m.setStatus(rs.getString("status"));
                    m.setCheckInTime(rs.getTimestamp("checkin_time"));

                    // ✅ thêm check_out_time (feature mới)
                    m.setCheckOutTime(rs.getTimestamp("check_out_time"));

                    m.setEventName(rs.getString("event_name"));
                    m.setStartTime(rs.getTimestamp("start_time"));

                    String venue = rs.getString("venue_name");
                    m.setVenueName(venue); // can be null (online events)

                    result.add(m);
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getTicketsByUserId: " + e.getMessage());
            e.printStackTrace();
        }

        return result; // Trả về danh sách (có thể rỗng)
    }

    /**
     * ================================================================================================
     * METHOD: getEventStats - THỐNG KÊ VÉ THEO SỰ KIỆN
     * ================================================================================================
     *
     * MỤC ĐÍCH: - Lấy số liệu thống kê về vé của 1 sự kiện - Tính tỷ lệ checkin
     * và checkout - Dùng cho dashboard quản lý sự kiện
     *
     * THỐNG KÊ: - total: Tổng số vé đã đăng ký (không tính CANCELLED) -
     * checked_in: Số vé đã checkin - checked_out: Số vé đã checkout -
     * checkInRate: Tỷ lệ checkin (%) - checkOutRate: Tỷ lệ checkout (%)
     *
     * BUSINESS LOGIC: - Chỉ đếm vé có status != 'CANCELLED' - Rate = (count /
     * total) * 100% - Format: "75.50%"
     *
     * SỬ DỤNG: - Dashboard: Hiển thị thống kê realtime - EventController: API
     * lấy stats của sự kiện - Report: Xuất báo cáo sau sự kiện
     *
     * @param eventId
     * @return
     */
    // New: get event statistics (total tickets, checked-in count, check-in rate)
    public EventStatsResponse getEventStats(int eventId) {
        EventStatsResponse stats = new EventStatsResponse();
        stats.setEventId(eventId);

        // SQL đếm số lượng theo từng status
        String sql = "SELECT status, COUNT(*) as total FROM Ticket WHERE event_id = ? "
                + "AND status IN ('BOOKED', 'CHECKED_IN', 'CHECKED_OUT', 'REFUNDED') GROUP BY status";

        int booked = 0, checkedIn = 0, checkedOut = 0, refunded = 0;
        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String s = rs.getString("status");
                    int count = rs.getInt("total");
                    if ("BOOKED".equalsIgnoreCase(s)) {
                        booked = count;
                    } else if ("CHECKED_IN".equalsIgnoreCase(s)) {
                        checkedIn = count;
                    } else if ("CHECKED_OUT".equalsIgnoreCase(s)) {
                        checkedOut = count;
                    } else if ("REFUNDED".equalsIgnoreCase(s)) {
                        refunded = count;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        int total = booked + checkedIn + checkedOut + refunded;
        stats.setTotalRegistered(total);
        stats.setTotalBooking(booked);
        stats.setTotalCheckedIn(checkedIn);
        stats.setTotalCheckedOut(checkedOut);
        stats.setTotalRefunded(refunded);

        // Tính tỉ lệ phần trăm (Rate)
        stats.setBookingRate(total > 0 ? String.format("%.1f%%", (booked * 100.0) / total) : "0.0%");
        stats.setCheckInRate(total > 0 ? String.format("%.1f%%", (checkedIn * 100.0) / total) : "0.0%");
        stats.setCheckOutRate(total > 0 ? String.format("%.1f%%", (checkedOut * 100.0) / total) : "0.0%");
        stats.setRefundedRate(total > 0 ? String.format("%.1f%%", (refunded * 100.0) / total) : "0.0%");

        return stats;
    }

    public List<MyTicketResponse> getTicketsByRole(String role, int userId, Integer eventId) {
        List<MyTicketResponse> list = new ArrayList<>();

        String sql = "SELECT t.ticket_id, t.qr_code_value, er.title, er.preferred_start_time, "
                + "t.status, t.checkin_time, t.check_out_time, t.qr_issued_at, u.full_name, "
                + "s.seat_code, esl.seat_type "
                + "FROM [dbo].[Ticket] t "
                + "JOIN [dbo].[Event_Request] er ON t.event_id = er.created_event_id "
                + "JOIN [dbo].[Users] u ON t.user_id = u.user_id "
                + "LEFT JOIN [dbo].[Event_Seat_Layout] esl "
                + "   ON esl.event_id = t.event_id AND esl.seat_id = t.seat_id "
                + "LEFT JOIN [dbo].[Seat] s "
                + "   ON s.seat_id = t.seat_id "
                + "WHERE t.status IN ('BOOKED', 'CHECKED_IN', 'CHECKED_OUT', 'REFUNDED') "
                + "AND (? = 'ADMIN' OR (? = 'ORGANIZER' AND er.requester_id = ?)) "
                + "AND (? IS NULL OR t.event_id = ?) "
                + "ORDER BY t.qr_issued_at DESC";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, role);
            ps.setString(2, role);
            ps.setInt(3, userId);

            if (eventId != null) {
                ps.setInt(4, eventId);
                ps.setInt(5, eventId);
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
                ps.setNull(5, java.sql.Types.INTEGER);
            }

            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MyTicketResponse item = new MyTicketResponse();
                    item.setTicketId(rs.getInt("ticket_id"));
                    item.setTicketCode(rs.getString("qr_code_value"));
                    item.setEventName(rs.getString("title"));
                    item.setStartTime(rs.getTimestamp("preferred_start_time"));
                    item.setStatus(rs.getString("status"));
                    item.setCheckInTime(rs.getTimestamp("checkin_time"));
                    item.setCheckOutTime(rs.getTimestamp("check_out_time"));
                    item.setPurchaseDate(rs.getTimestamp("qr_issued_at"));
                    item.setBuyerName(rs.getString("full_name"));

                    // UPDATED:
                    item.setSeatCode(rs.getString("seat_code"));   // từ Seat
                    item.setCategory(rs.getString("seat_type"));   // từ Event_Seat_Layout

                    list.add(item);
                }
            }

        } catch (Exception e) {
            System.out.println("Lỗi tại TicketDAO.getTicketsByRole: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    public List<Ticket> findTicketsByIds(List<Integer> ids) throws SQLException, ClassNotFoundException {
        List<Ticket> list = new ArrayList<>();

        if (ids == null || ids.isEmpty()) {
            return list;
        }

        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT ticket_id, event_id, user_id, category_ticket_id, bill_id, seat_id, "
                + "qr_code_value, qr_issued_at, status, checkin_time "
                + "FROM Ticket WHERE ticket_id IN (" + placeholders + ")";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;
            for (Integer id : ids) {
                ps.setInt(idx++, id);
            }

            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Ticket t = new Ticket();
                    t.setTicketId(rs.getInt("ticket_id"));
                    t.setEventId(rs.getInt("event_id"));
                    t.setUserId(rs.getInt("user_id"));
                    t.setCategoryTicketId(rs.getInt("category_ticket_id"));
                    t.setBillId((Integer) rs.getObject("bill_id"));
                    t.setSeatId(rs.getInt("seat_id"));
                    t.setQrCodeValue(rs.getString("qr_code_value"));
                    t.setQrIssuedAt(rs.getTimestamp("qr_issued_at"));
                    t.setStatus(rs.getString("status"));
                    t.setCheckinTime(rs.getTimestamp("checkin_time"));

                    list.add(t);
                }
            }
        }

        return list;
    }

    public void updateTicketAfterPayment(Ticket t) throws SQLException, ClassNotFoundException {
        String sql = "UPDATE Ticket SET "
                + "bill_id = ?, "
                + "status = ?, "
                + "qr_issued_at = ? "
                + "WHERE ticket_id = ?";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            if (t.getBillId() == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, t.getBillId());
            }

            ps.setString(2, t.getStatus());
            ps.setTimestamp(3, t.getQrIssuedAt());
            ps.setInt(4, t.getTicketId());

            ps.executeUpdate();
        }
    }

    public void deleteTicketsByIds(List<Integer> ids) throws SQLException, ClassNotFoundException {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "DELETE FROM Ticket WHERE ticket_id IN (" + placeholders + ")";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;
            for (Integer id : ids) {
                ps.setInt(idx++, id);
            }

            ps.executeUpdate();
        }
    }

    public boolean checkoutTicket(int ticketId) {
        String sql = "UPDATE dbo.Ticket "
                + "SET status = 'CHECKED_OUT', check_out_time = GETDATE() "
                + "WHERE ticket_id = ? AND status = 'CHECKED_IN'";

        try ( Connection con = DBUtils.getConnection();  PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, ticketId);
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

// DAO/TicketDAO.java (thêm overload dùng Connection)
    public int insertTicketAndReturnId(Connection conn, Ticket t) throws SQLException {
        String sql = "INSERT INTO Ticket "
                + "(event_id, user_id, category_ticket_id, bill_id, seat_id, "
                + " qr_code_value, qr_issued_at, status, checkin_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try ( PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, t.getEventId());
            ps.setInt(2, t.getUserId());
            ps.setInt(3, t.getCategoryTicketId());

            if (t.getBillId() != null) {
                ps.setInt(4, t.getBillId());
            } else {
                ps.setNull(4, Types.INTEGER);
            }

            if (t.getSeatId() != null) {
                ps.setInt(5, t.getSeatId());
            } else {
                ps.setNull(5, Types.INTEGER);
            }

            // NOT NULL
            String qrVal = t.getQrCodeValue();
            if (qrVal == null || qrVal.trim().isEmpty()) {
                qrVal = "PENDING_QR";
            }
            ps.setString(6, qrVal);

            Timestamp issuedAt = t.getQrIssuedAt();
            if (issuedAt == null) {
                issuedAt = new Timestamp(System.currentTimeMillis());
            }
            ps.setTimestamp(7, issuedAt);

            ps.setString(8, t.getStatus());

            if (t.getCheckinTime() != null) {
                ps.setTimestamp(9, t.getCheckinTime());
            } else {
                ps.setNull(9, Types.TIMESTAMP);
            }

            int affected = ps.executeUpdate();   // ✅ KHÔNG dùng executeQuery()
            if (affected == 0) {
                return -1;
            }

            try ( ResultSet rs = ps.getGeneratedKeys()) { // ✅ Lấy id sinh ra
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            return -1;
        }
    }

    public void updateTicketAfterPayment(Connection conn, Ticket t) throws SQLException {
        String sql = "UPDATE Ticket SET bill_id=?, status=?, qr_issued_at=? WHERE ticket_id=?";
        try ( PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, t.getBillId());
            ps.setString(2, t.getStatus());
            ps.setTimestamp(3, t.getQrIssuedAt());
            ps.setInt(4, t.getTicketId());
            ps.executeUpdate();
        }
    }

    public void deleteTicketsByIds(Connection conn, List<Integer> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String in = ids.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = "DELETE FROM Ticket WHERE ticket_id IN (" + in + ")";
        try ( PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setInt(i + 1, ids.get(i));
            }
            ps.executeUpdate();
        }
    }

}
