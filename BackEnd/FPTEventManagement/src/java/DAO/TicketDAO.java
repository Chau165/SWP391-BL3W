package DAO;

import DTO.Ticket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.sql.Types;
import mylib.DBUtils;

public class TicketDAO {

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

            // ✅ LƯU MÃ QR VÀO DB
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
}
