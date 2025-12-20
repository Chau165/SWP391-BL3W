package DAO;

import DTO.CategoryTicket;
import DTO.Event;
import DTO.EventDetailDto;
import mylib.DBUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class EventDAO {

    // ================== GET ALL EVENTS (OPEN) ==================
    // ================== GET ALL EVENTS (OPEN + CLOSED) ==================
    public List<Event> getAllEvents() throws SQLException, ClassNotFoundException {
        List<Event> list = new ArrayList<>();

        String sql
                = "SELECT e.event_id, e.title, e.description, e.start_time, e.end_time, "
                + "       e.max_seats, e.status, e.created_by, e.created_at, e.banner_url, "
                + "       e.area_id, "
                + // ✅ lấy area_id từ Event
                "       va.area_name, va.floor, "
                + // ✅ thông tin khu vực
                "       v.venue_id, v.venue_name, v.location "
                + // ✅ thông tin venue
                "FROM   [FPTEventManagement].[dbo].[Event] e "
                + "LEFT JOIN [FPTEventManagement].[dbo].[Venue_Area] va ON e.area_id = va.area_id "
                + "LEFT JOIN [FPTEventManagement].[dbo].[Venue]      v  ON va.venue_id = v.venue_id "
                + "WHERE  e.status IN ('OPEN', 'CLOSED') "
                + "ORDER BY e.start_time ASC";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql);  ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapRowWithLocation(rs)); // ✅ map đầy đủ
            }
        }
        return list;
    }

    // ================== EVENT DETAIL (JOIN Venue_Area + Venue) ==================
    public EventDetailDto getEventDetail(int eventId) throws SQLException, ClassNotFoundException {
        EventDetailDto detail = null;

        String sqlEvent
                = "SELECT e.event_id, e.title, e.description, e.start_time, e.end_time, "
                + "       e.max_seats, e.status, e.banner_url, "
                + "       v.venue_name, "
                + "       va.area_id, va.area_name, va.floor, va.capacity, "
                + "       s.full_name   AS speaker_name, "
                + "       s.bio         AS speaker_bio, "
                + "       s.avatar_url  AS speaker_avatar_url "
                + "FROM   [FPTEventManagement].[dbo].[Event]       e "
                + "JOIN   [FPTEventManagement].[dbo].[Venue_Area]  va ON e.area_id   = va.area_id "
                + "JOIN   [FPTEventManagement].[dbo].[Venue]       v  ON va.venue_id = v.venue_id "
                + "LEFT JOIN [FPTEventManagement].[dbo].[Speaker]  s  ON e.speaker_id = s.speaker_id "
                + "WHERE  e.event_id = ? "
                + "  AND  (e.status = 'OPEN' OR e.status = 'CLOSED')";

        // ✅ thêm cột description vào truy vấn vé
        String sqlTickets
                = "SELECT category_ticket_id, name, description, price, max_quantity, status "
                + "FROM   [FPTEventManagement].[dbo].[Category_Ticket] "
                + "WHERE  event_id = ? AND status = 'ACTIVE'";

        try ( Connection conn = DBUtils.getConnection()) {

            // 1) Lấy thông tin event + venue + area + speaker
            try ( PreparedStatement ps = conn.prepareStatement(sqlEvent)) {
                ps.setInt(1, eventId);
                try ( ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        detail = new EventDetailDto();
                        detail.setEventId(rs.getInt("event_id"));
                        detail.setTitle(rs.getString("title"));
                        detail.setDescription(rs.getString("description"));
                        detail.setStartTime(rs.getTimestamp("start_time"));
                        detail.setEndTime(rs.getTimestamp("end_time"));
                        detail.setMaxSeats(rs.getInt("max_seats"));
                        detail.setStatus(rs.getString("status"));

                        // banner
                        detail.setBannerUrl(rs.getString("banner_url"));

                        // Venue
                        detail.setVenueName(rs.getString("venue_name"));

                        // Area (Venue_Area)
                        detail.setAreaId((Integer) rs.getObject("area_id"));
                        detail.setAreaName(rs.getString("area_name"));
                        detail.setFloor(rs.getString("floor"));
                        detail.setAreaCapacity((Integer) rs.getObject("capacity"));

                        // Speaker
                        detail.setSpeakerName(rs.getString("speaker_name"));
                        detail.setSpeakerBio(rs.getString("speaker_bio"));
                        detail.setSpeakerAvatarUrl(rs.getString("speaker_avatar_url"));
                    } else {
                        return null;
                    }
                }

                // 2) Lấy danh sách loại vé (với description)
                List<CategoryTicket> tickets = new ArrayList<>();
                try ( PreparedStatement ps2 = conn.prepareStatement(sqlTickets)) {
                    ps2.setInt(1, eventId);
                    try ( ResultSet rs2 = ps2.executeQuery()) {
                        while (rs2.next()) {
                            CategoryTicket t = new CategoryTicket();
                            t.setCategoryTicketId(rs2.getInt("category_ticket_id"));
                            t.setEventId(eventId);
                            t.setName(rs2.getString("name"));
                            t.setDescription(rs2.getString("description")); // ✅ gán description
                            t.setPrice(rs2.getBigDecimal("price"));
                            t.setMaxQuantity(rs2.getInt("max_quantity"));
                            t.setStatus(rs2.getString("status"));
                            tickets.add(t);
                        }
                    }
                }

                detail.setTickets(tickets);
            }

            return detail;
        }
    }
    // ================== GET EVENT BY ID ==================

    public Event getEventById(int eventId) {
        String sql
                = "SELECT event_id, title, description, start_time, end_time, "
                + "       area_id, speaker_id, max_seats, status, created_by, created_at, "
                + "       banner_url "
                + "FROM   Event "
                + "WHERE  event_id = ?";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, eventId);

            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Event e = new Event();
                    e.setEventId(rs.getInt("event_id"));
                    e.setTitle(rs.getString("title"));
                    e.setDescription(rs.getString("description"));
                    e.setStartTime(rs.getTimestamp("start_time"));
                    e.setEndTime(rs.getTimestamp("end_time"));
                    e.setAreaId((Integer) rs.getObject("area_id"));
                    e.setSpeakerId((Integer) rs.getObject("speaker_id"));
                    e.setMaxSeats(rs.getInt("max_seats"));
                    e.setStatus(rs.getString("status"));
                    e.setCreatedBy((Integer) rs.getObject("created_by"));
                    e.setCreatedAt(rs.getTimestamp("created_at"));

                    // ✅ banner
                    e.setBannerUrl(rs.getString("banner_url"));

                    return e;
                }
            }
        } catch (Exception ex) {
            System.err.println("[ERROR] getEventById: " + ex.getMessage());
            ex.printStackTrace();
        }
        return null;
    }

    // ================== THÊM MỚI: UPDATE SPEAKER_ID CHO EVENT ==================
    public void updateSpeakerForEvent(Connection conn, int eventId, int speakerId) throws SQLException {
        String sql = "UPDATE Event SET speaker_id = ? WHERE event_id = ?";
        try ( PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, speakerId);
            ps.setInt(2, eventId);
            ps.executeUpdate();
        }
    }

    // Trong EventDAO
    public boolean updateEventStatus(Connection conn, int eventId, String newStatus) throws SQLException {
        String sql = "UPDATE Event SET status = ? WHERE event_id = ?";

        try ( PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setNString(1, newStatus);
            ps.setInt(2, eventId);

            int affected = ps.executeUpdate();
            return affected > 0;
        }
    }

    // ================== THÊM MỚI: UPDATE BANNER_URL CHO EVENT ==================
    public void updateBannerUrlForEvent(Connection conn, int eventId, String bannerUrl) throws SQLException {
        String sql = "UPDATE Event SET banner_url = ? WHERE event_id = ?";
        try ( PreparedStatement ps = conn.prepareStatement(sql)) {
            if (bannerUrl != null && !bannerUrl.trim().isEmpty()) {
                ps.setNString(1, bannerUrl.trim());
            } else {
                // Cho phép xóa banner (set NULL) nếu FE gửi rỗng
                ps.setNull(1, java.sql.Types.NVARCHAR);
            }
            ps.setInt(2, eventId);
            ps.executeUpdate();
        }
    }

    private Event mapRowWithLocation(ResultSet rs) throws SQLException {
        Event e = new Event();

        e.setEventId(rs.getInt("event_id"));
        e.setTitle(rs.getString("title"));
        e.setDescription(rs.getString("description"));
        e.setStartTime(rs.getTimestamp("start_time"));
        e.setEndTime(rs.getTimestamp("end_time"));

        e.setMaxSeats(rs.getInt("max_seats"));
        e.setStatus(rs.getString("status"));
        e.setCreatedBy((Integer) rs.getObject("created_by"));
        e.setCreatedAt(rs.getTimestamp("created_at"));
        e.setBannerUrl(rs.getString("banner_url"));

        // ===== Area =====
        e.setAreaId((Integer) rs.getObject("area_id"));    // từ e.area_id
        e.setAreaName(rs.getString("area_name"));
        e.setFloor(rs.getString("floor"));

        // ===== Venue =====
        e.setVenueId((Integer) rs.getObject("venue_id"));
        e.setVenueName(rs.getString("venue_name"));
        e.setVenueLocation(rs.getString("location"));

        return e;
    }

    /**
     * "Xóa mềm" = vô hiệu hóa Event và các bảng liên quan: - Event.status =
     * 'CLOSED' - Category_Ticket.status = 'INACTIVE' - Event_Seat_Layout.status
     * = 'INAVAILABLE'
     *
     * Điều kiện: Event chưa có vé mua (Ticket tồn tại và status != 'CANCELLED'
     * => KHÔNG cho disable)
     *
     * @param eventId
     * @return true nếu disable thành công, false nếu event không tồn tại
     * @throws java.sql.SQLException
     * @throws java.lang.ClassNotFoundException
     */
    public boolean disableEventIfNoTickets(int eventId) throws SQLException, ClassNotFoundException {
        Connection conn = null;

        // (A) Lock event để tránh race-condition
        String sqlCheckEvent
                = "SELECT 1 "
                + "FROM [FPTEventManagement].[dbo].[Event] WITH (UPDLOCK, HOLDLOCK) "
                + "WHERE event_id = ?";

        // (B) Check đã có vé mua chưa
        String sqlCheckTickets
                = "SELECT TOP 1 1 "
                + "FROM [FPTEventManagement].[dbo].[Ticket] "
                + "WHERE event_id = ? "
                + "  AND status NOT IN ('CANCELLED')";

        // (C) Disable Event
        String sqlCloseEvent
                = "UPDATE [FPTEventManagement].[dbo].[Event] "
                + "SET status = 'CLOSED' "
                + "WHERE event_id = ?";

        // (D) Disable Category_Ticket
        String sqlInactiveCategoryTicket
                = "UPDATE [FPTEventManagement].[dbo].[Category_Ticket] "
                + "SET status = 'INACTIVE' "
                + "WHERE event_id = ? AND status <> 'INACTIVE'";

        // (E) Disable Seat layout
        String sqlInavailableSeats
                = "UPDATE [FPTEventManagement].[dbo].[Event_Seat_Layout] "
                + "SET status = 'INAVAILABLE' "
                + "WHERE event_id = ? AND status <> 'INAVAILABLE'";

        try {
            conn = DBUtils.getConnection();
            conn.setAutoCommit(false);

            // 1) Check event tồn tại
            try ( PreparedStatement ps = conn.prepareStatement(sqlCheckEvent)) {
                ps.setInt(1, eventId);
                try ( ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return false; // không có event
                    }
                }
            }

            // 2) Check đã có vé mua chưa
            try ( PreparedStatement ps = conn.prepareStatement(sqlCheckTickets)) {
                ps.setInt(1, eventId);
                try ( ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        conn.rollback();
                        throw new SQLException("Không thể vô hiệu hóa: Event đã có vé được mua.");
                    }
                }
            }

            // 3) Update Event -> CLOSED
            try ( PreparedStatement ps = conn.prepareStatement(sqlCloseEvent)) {
                ps.setInt(1, eventId);
                ps.executeUpdate();
            }

            // 4) Category_Ticket -> INACTIVE
            try ( PreparedStatement ps = conn.prepareStatement(sqlInactiveCategoryTicket)) {
                ps.setInt(1, eventId);
                ps.executeUpdate();
            }

            // 5) Event_Seat_Layout -> INAVAILABLE
            try ( PreparedStatement ps = conn.prepareStatement(sqlInavailableSeats)) {
                ps.setInt(1, eventId);
                ps.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (SQLException ex) {
            if (conn != null) {
                conn.rollback();
            }
            throw ex;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignore) {
                }
                try {
                    conn.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }
}
