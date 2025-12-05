package DAO;

import DTO.Ticket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import mylib.DBUtils;
import DTO.MyTicketResponse;
import java.util.ArrayList;
import java.util.List;

public class TicketDAO {

    // ✅ HÀM CŨ - vẫn giữ, nếu chỗ khác còn dùng
    public boolean insertTicket(Ticket t) {
        String sql = "INSERT INTO Ticket "
                + " (event_id, user_id, category_ticket_id, bill_id, seat_id, "
                + "  qr_code_value, qr_issued_at, status, checkin_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

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

            ps.setString(6, t.getQrCodeValue());

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
            return affected > 0;

        } catch (SQLIntegrityConstraintViolationException ex) {
            System.err.println("[WARN] insertTicket - constraint violation: " + ex.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("[ERROR] insertTicket: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    // ✅ MỚI: Insert ticket và trả về ticket_id (chưa có QR)
    public int insertTicketAndReturnId(Ticket t) {
        String sql = "INSERT INTO Ticket "
                + " (event_id, user_id, category_ticket_id, bill_id, seat_id, "
                + "  qr_code_value, qr_issued_at, status, checkin_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

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
                return -1;
            }

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
            return rows > 0;
        } catch (Exception e) {
            System.err.println("[ERROR] updateTicketQr: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Lấy ticket theo ID
    public Ticket getTicketById(int ticketId) {
        String sql = "SELECT ticket_id, event_id, user_id, category_ticket_id, "
                + "       bill_id, seat_id, qr_code_value, qr_issued_at, "
                + "       status, checkin_time "
                + "FROM Ticket WHERE ticket_id = ?";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, ticketId);

            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Ticket t = new Ticket();
                    t.setTicketId(rs.getInt("ticket_id"));
                    t.setEventId(rs.getInt("event_id"));
                    t.setUserId(rs.getInt("user_id"));
                    t.setCategoryTicketId(rs.getInt("category_ticket_id"));
                    t.setBillId((Integer) rs.getObject("bill_id"));
                    t.setSeatId((Integer) rs.getObject("seat_id"));
                    t.setQrCodeValue(rs.getString("qr_code_value"));
                    t.setQrIssuedAt(rs.getTimestamp("qr_issued_at"));
                    t.setStatus(rs.getString("status"));
                    t.setCheckinTime(rs.getTimestamp("checkin_time"));
                    return t;
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getTicketById: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

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

    // New: Lấy danh sách vé (kèm thông tin Event + Venue) theo user_id
    public List<MyTicketResponse> getTicketsByUserId(int userId) {
        String sql = "SELECT t.ticket_id, t.qr_code_value, t.status, t.checkin_time, "
                + " e.title AS event_name, e.start_time AS start_time, v.venue_name AS venue_name "
                + "FROM Ticket t "
                + "JOIN Event e ON t.event_id = e.event_id "
                + "LEFT JOIN Venue_Area va ON e.area_id = va.area_id "
                + "LEFT JOIN Venue v ON va.venue_id = v.venue_id "
                + "WHERE t.user_id = ? "
                + "ORDER BY t.qr_issued_at DESC";

        List<MyTicketResponse> result = new ArrayList<>();

        try (Connection conn = DBUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MyTicketResponse m = new MyTicketResponse();
                    m.setTicketId(rs.getInt("ticket_id"));
                    m.setTicketCode(rs.getString("qr_code_value"));
                    m.setStatus(rs.getString("status"));
                    m.setCheckInTime(rs.getTimestamp("checkin_time"));
                    m.setEventName(rs.getString("event_name"));
                    m.setStartTime(rs.getTimestamp("start_time"));
                    String venue = rs.getString("venue_name");
                    m.setVenueName(venue); // can be null
                    result.add(m);
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getTicketsByUserId: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }
}


