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
  public List<Event> getAllEvents() throws SQLException, ClassNotFoundException {
    List<Event> list = new ArrayList<>();

    String sql
            = "SELECT event_id, title, description, start_time, end_time, "
            + "       area_id, speaker_id, max_seats, status, created_by, created_at, "
            + "       banner_url "
            + "FROM [FPTEventManagement].[dbo].[Event] "
            + "WHERE status IN ('OPEN', 'CLOSED')"; // ✅ lấy cả OPEN + CLOSED

    try (Connection conn = DBUtils.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {
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
            e.setBannerUrl(rs.getString("banner_url")); // ✅ map banner_url

            list.add(e);
        }
    }

    return list;
}
    // ================== EVENT DETAIL (JOIN Venue_Area + Venue) ==================
    public EventDetailDto getEventDetail(int eventId) throws SQLException, ClassNotFoundException {
        EventDetailDto detail = null;

        String sqlEvent
                = "SELECT e.event_id, e.title, e.description, e.start_time, e.end_time, "
                + "       e.max_seats, e.status, e.banner_url, " // ✅ thêm e.banner_url
                + "       v.venue_name, "
                + "       va.area_id, va.area_name, va.floor, va.capacity, "
                + "       s.full_name AS speaker_name "
                + "FROM   [FPTEventManagement].[dbo].[Event]       e "
                + "JOIN   [FPTEventManagement].[dbo].[Venue_Area]  va ON e.area_id   = va.area_id "
                + "JOIN   [FPTEventManagement].[dbo].[Venue]       v  ON va.venue_id = v.venue_id "
                + "LEFT JOIN [FPTEventManagement].[dbo].[Speaker]  s  ON e.speaker_id = s.speaker_id "
                + "WHERE  e.event_id = ? "
                + "  AND  e.status = 'OPEN'";

        String sqlTickets
                = "SELECT category_ticket_id, name, price, max_quantity, status "
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

                        // ✅ banner
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
                    } else {
                        // Không tìm thấy event
                        return null;
                    }
                }
            }

            // 2) Lấy danh sách loại vé
            List<CategoryTicket> tickets = new ArrayList<>();
            try ( PreparedStatement ps2 = conn.prepareStatement(sqlTickets)) {
                ps2.setInt(1, eventId);
                try ( ResultSet rs2 = ps2.executeQuery()) {
                    while (rs2.next()) {
                        CategoryTicket t = new CategoryTicket();
                        t.setCategoryTicketId(rs2.getInt("category_ticket_id"));
                        t.setEventId(eventId);
                        t.setName(rs2.getString("name"));
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
}
