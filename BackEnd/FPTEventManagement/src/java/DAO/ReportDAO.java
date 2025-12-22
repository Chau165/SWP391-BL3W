package DAO;

import DTO.Report;
import DTO.ReportDetailStaffDTO;
import DTO.ReportListStaffDTO;
import java.math.BigDecimal;
import mylib.DBUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReportDAO {

    // ✅ Validate: ticket này có thuộc student không?
    public boolean isTicketOwnedByUser(int ticketId, int userId) {
        String sql = "SELECT 1 FROM Ticket WHERE ticket_id = ? AND user_id = ?";
        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            ps.setInt(2, userId);
            try ( ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            System.err.println("[ERROR] isTicketOwnedByUser: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    // ✅ Optional: tránh spam report trùng ticket khi đang PENDING
    public boolean hasPendingReportForTicket(int ticketId) {
        String sql = "SELECT 1 FROM Report WHERE ticket_id = ? AND status = N'PENDING'";
        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            try ( ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            System.err.println("[ERROR] hasPendingReportForTicket: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public int insertReport(Report r) {
        String sql = "INSERT INTO Report(user_id, ticket_id, title, description, image_url, status) "
                + "VALUES (?, ?, ?, ?, ?, N'PENDING')";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, r.getUserId());
            ps.setInt(2, r.getTicketId());
            ps.setNString(3, r.getTitle());
            ps.setNString(4, r.getDescription());
            ps.setNString(5, r.getImageUrl());

            ps.executeUpdate();

            try ( ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1); // report_id
                }
            }

        } catch (Exception e) {
            System.err.println("[ERROR] insertReport: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    public ReportDetailStaffDTO getReportDetailForStaff(int reportId) {
        String sql
                = "SELECT "
                + "  r.report_id, r.ticket_id, r.title, r.description, r.image_url, r.created_at, r.status AS report_status, "
                + "  u.user_id AS student_id, u.full_name AS student_name, "
                + "  t.status AS ticket_status, t.category_ticket_id, "
                + "  ct.name AS category_ticket_name, ct.price "
                + "FROM Report r "
                + "JOIN Users u ON u.user_id = r.user_id "
                + "JOIN Ticket t ON t.ticket_id = r.ticket_id "
                + "JOIN Category_Ticket ct ON ct.category_ticket_id = t.category_ticket_id "
                + "WHERE r.report_id = ?";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, reportId);

            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ReportDetailStaffDTO dto = new ReportDetailStaffDTO();
                    dto.setReportId(rs.getInt("report_id"));
                    dto.setTicketId(rs.getInt("ticket_id"));

                    dto.setTitle(rs.getNString("title"));
                    dto.setDescription(rs.getNString("description"));
                    dto.setImageUrl(rs.getNString("image_url"));

                    dto.setCreatedAt(rs.getTimestamp("created_at"));
                    dto.setReportStatus(rs.getNString("report_status"));

                    dto.setStudentId(rs.getInt("student_id"));
                    dto.setStudentName(rs.getNString("student_name"));

                    dto.setTicketStatus(rs.getNString("ticket_status"));

                    dto.setCategoryTicketId(rs.getInt("category_ticket_id"));
                    dto.setCategoryTicketName(rs.getNString("category_ticket_name"));
                    dto.setPrice(rs.getBigDecimal("price"));

                    return dto;
                }
            }

        } catch (Exception e) {
            System.err.println("[ERROR] getReportDetailForStaff: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public List<ReportListStaffDTO> listReportsForStaff(String status, int page, int pageSize) {

        // safety
        if (page <= 0) {
            page = 1;
        }
        if (pageSize <= 0) {
            pageSize = 10;
        }
        if (pageSize > 100) {
            pageSize = 100;
        }

        int offset = (page - 1) * pageSize;

        // Nếu có filter status thì add WHERE, không thì lấy all
        boolean hasStatus = status != null && !status.trim().isEmpty();

        String sql
                = "SELECT "
                + "  r.report_id, r.ticket_id, r.title, r.description, r.image_url, r.created_at, r.status AS report_status, "
                + "  u.full_name AS student_name, "
                + "  t.status AS ticket_status, "
                + "  ct.name AS category_ticket_name, ct.price "
                + "FROM Report r "
                + "JOIN Users u ON u.user_id = r.user_id "
                + "JOIN Ticket t ON t.ticket_id = r.ticket_id "
                + "JOIN Category_Ticket ct ON ct.category_ticket_id = t.category_ticket_id "
                + (hasStatus ? "WHERE r.status = ? " : "")
                + "ORDER BY r.created_at DESC "
                + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        List<ReportListStaffDTO> list = new ArrayList<>();

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;

            if (hasStatus) {
                ps.setNString(idx++, status.trim().toUpperCase());
            }

            ps.setInt(idx++, offset);
            ps.setInt(idx++, pageSize);

            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ReportListStaffDTO dto = new ReportListStaffDTO();
                    dto.setReportId(rs.getInt("report_id"));
                    dto.setTicketId(rs.getInt("ticket_id"));

                    dto.setTitle(rs.getNString("title"));
                    dto.setDescription(rs.getNString("description"));
                    dto.setImageUrl(rs.getNString("image_url"));

                    dto.setCreatedAt(rs.getTimestamp("created_at"));
                    dto.setReportStatus(rs.getNString("report_status"));

                    dto.setStudentName(rs.getNString("student_name"));

                    dto.setTicketStatus(rs.getNString("ticket_status"));

                    dto.setCategoryTicketName(rs.getNString("category_ticket_name"));
                    dto.setPrice(rs.getBigDecimal("price"));

                    list.add(dto);
                }
            }

        } catch (Exception e) {
            System.err.println("[ERROR] listReportsForStaff: " + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }

    public List<Integer> getPendingTicketIdsByStudent(int studentId) {
        String sql = "SELECT DISTINCT ticket_id FROM Report WHERE user_id = ? AND status = N'PENDING'";
        List<Integer> ids = new ArrayList<>();

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, studentId);

            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("ticket_id"));
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getPendingTicketIdsByStudent: " + e.getMessage());
            e.printStackTrace();
        }

        return ids;
    }

    public static class ProcessResult {

        public boolean success;
        public String message;
        public BigDecimal refundAmount; // chỉ có khi approve
    }

    /**
     * STAFF xử lý report: - approve=true => APPROVED + refund +
     * Ticket.status=REFUND - approve=false => REJECTED (no refund)
     * @param reportId
     * @param staffId
     * @param approve
     * @param staffNote
     * @return 
     */
    public ProcessResult processReport(int reportId, int staffId, boolean approve, String staffNote) {
        ProcessResult result = new ProcessResult();
        result.success = false;
        result.message = "Unknown error";

        Connection conn = null;
        try {
            conn = DBUtils.getConnection();
            conn.setAutoCommit(false);

            // 1) Lock report row để chống race-condition (2 staff bấm cùng lúc)
            String sqlGet = "SELECT report_id, user_id, ticket_id, status "
                    + "FROM Report WITH (UPDLOCK, ROWLOCK) "
                    + "WHERE report_id = ?";
            int studentId;
            int ticketId;
            String reportStatus;

            try ( PreparedStatement ps = conn.prepareStatement(sqlGet)) {
                ps.setInt(1, reportId);
                try ( ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        result.message = "Không tìm thấy report";
                        return result;
                    }
                    studentId = rs.getInt("user_id");
                    ticketId = rs.getInt("ticket_id");
                    reportStatus = rs.getNString("status");
                }
            }

            if (reportStatus == null || !"PENDING".equalsIgnoreCase(reportStatus.trim())) {
                conn.rollback();
                result.message = "Report này đã được xử lý rồi";
                return result;
            }

            // Nếu reject -> chỉ update report
            if (!approve) {
                String sqlReject
                        = "UPDATE Report "
                        + "SET status = N'REJECTED', processed_by = ?, processed_at = SYSUTCDATETIME(), staff_note = ? "
                        + "WHERE report_id = ? AND status = N'PENDING'";

                try ( PreparedStatement ps = conn.prepareStatement(sqlReject)) {
                    ps.setInt(1, staffId);
                    ps.setNString(2, staffNote);
                    ps.setInt(3, reportId);

                    int rows = ps.executeUpdate();
                    if (rows <= 0) {
                        conn.rollback();
                        result.message = "Không thể từ chối (report không còn PENDING)";
                        return result;
                    }
                }

                conn.commit();
                result.success = true;
                result.message = "Đã từ chối report";
                return result;
            }

            // 2) APPROVE: tính refund amount theo Category_Ticket.price
            BigDecimal refund = BigDecimal.ZERO;

            String sqlRefundAmount
                    = "SELECT ct.price "
                    + "FROM Ticket t "
                    + "JOIN Category_Ticket ct ON ct.category_ticket_id = t.category_ticket_id "
                    + "WHERE t.ticket_id = ?";

            try ( PreparedStatement ps = conn.prepareStatement(sqlRefundAmount)) {
                ps.setInt(1, ticketId);
                try ( ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        result.message = "Không tìm thấy ticket/category_ticket để tính tiền hoàn";
                        return result;
                    }
                    refund = rs.getBigDecimal("price");
                    if (refund == null) {
                        refund = BigDecimal.ZERO;
                    }
                }
            }

            // 3) Update Users.Wallet += refund
            String sqlUpdateWallet = "UPDATE Users SET Wallet = Wallet + ? WHERE user_id = ?";
            try ( PreparedStatement ps = conn.prepareStatement(sqlUpdateWallet)) {
                ps.setBigDecimal(1, refund);
                ps.setInt(2, studentId);
                int rows = ps.executeUpdate();
                if (rows <= 0) {
                    conn.rollback();
                    result.message = "Không cập nhật được Wallet";
                    return result;
                }
            }

            // 4) Update Ticket.status = REFUND
            // (Bạn nhớ đảm bảo Ticket.status cho phép giá trị 'REFUNDED' nếu có CHECK constraint)
            String sqlUpdateTicket = "UPDATE Ticket SET status = N'REFUNDED' WHERE ticket_id = ?";
            try ( PreparedStatement ps = conn.prepareStatement(sqlUpdateTicket)) {
                ps.setInt(1, ticketId);
                int rows = ps.executeUpdate();
                if (rows <= 0) {
                    conn.rollback();
                    result.message = "Không cập nhật được trạng thái ticket";
                    return result;
                }
            }

            // 5) Update Report status APPROVED + processed info + refund_amount + staff_note
            String sqlApprove
                    = "UPDATE Report "
                    + "SET status = N'APPROVED', processed_by = ?, processed_at = SYSUTCDATETIME(), refund_amount = ?, staff_note = ? "
                    + "WHERE report_id = ? AND status = N'PENDING'";

            try ( PreparedStatement ps = conn.prepareStatement(sqlApprove)) {
                ps.setInt(1, staffId);
                ps.setBigDecimal(2, refund);
                ps.setNString(3, staffNote);
                ps.setInt(4, reportId);

                int rows = ps.executeUpdate();
                if (rows <= 0) {
                    conn.rollback();
                    result.message = "Không thể approve (report không còn PENDING)";
                    return result;
                }
            }

            conn.commit();
            result.success = true;
            result.refundAmount = refund;
            result.message = "Đã duyệt và hoàn tiền thành công";
            return result;

        } catch (Exception e) {
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (Exception ignored) {
            }
            e.printStackTrace();
            result.message = "Lỗi server khi xử lý report";
            return result;
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception ignored) {
            }
        }
    }
}

