package DAO;

import DTO.EventRequest;
import mylib.DBUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EventRequestDAO {

    // INSERT request mới từ STUDENT
    public Integer insertRequest(EventRequest r) {
        String sql = "INSERT INTO Event_Request ("
                + "requester_id, title, description, preferred_start_time, "
                + "preferred_end_time, expected_capacity, status"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?)";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, r.getRequesterId());
            ps.setNString(2, r.getTitle());
            if (r.getDescription() != null) {
                ps.setNString(3, r.getDescription());
            } else {
                ps.setNull(3, Types.NVARCHAR);
            }

            if (r.getPreferredStartTime() != null) {
                ps.setTimestamp(4, r.getPreferredStartTime());
            } else {
                ps.setNull(4, Types.TIMESTAMP);
            }

            if (r.getPreferredEndTime() != null) {
                ps.setTimestamp(5, r.getPreferredEndTime());
            } else {
                ps.setNull(5, Types.TIMESTAMP);
            }

            if (r.getExpectedCapacity() != null) {
                ps.setInt(6, r.getExpectedCapacity());
            } else {
                ps.setNull(6, Types.INTEGER);
            }

            ps.setNString(7, r.getStatus() != null ? r.getStatus() : "PENDING");

            int affected = ps.executeUpdate();
            if (affected == 0) {
                return null;
            }

            try ( ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] insertRequest: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public List<EventRequest> getRequestsByStudent(int requesterId) {
        List<EventRequest> list = new ArrayList<>();

        String sql
                = "SELECT er.request_id, er.requester_id, u.full_name AS requester_name, "
                + "       er.title, er.description, er.preferred_start_time, er.preferred_end_time, "
                + "       er.expected_capacity, er.status, er.created_at, er.processed_by, "
                + "       er.processed_at, er.organizer_note, er.created_event_id "
                + "FROM Event_Request er "
                + "JOIN Users u ON er.requester_id = u.user_id "
                + "WHERE er.requester_id = ? "
                + "ORDER BY er.created_at DESC";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, requesterId);
            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getRequestsByStudent: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    // Lấy tất cả request đang PENDING cho ORGANIZER xem
    public List<EventRequest> getPendingRequests() {
        List<EventRequest> list = new ArrayList<>();

        String sql = "SELECT er.request_id, er.requester_id, u.full_name AS requester_name, "
                + "er.title, er.description, er.preferred_start_time, er.preferred_end_time, "
                + "er.expected_capacity, er.status, er.created_at, er.processed_by, "
                + "er.processed_at, er.organizer_note, er.created_event_id "
                + "FROM Event_Request er "
                + "JOIN Users u ON er.requester_id = u.user_id "
                + "WHERE er.status = 'PENDING' "
                + "ORDER BY er.created_at ASC";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql);  ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getPendingRequests: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    // ======================= GET BY ID =======================
    public EventRequest getById(int requestId) {
        String sql
                = "SELECT er.request_id, er.requester_id, u.full_name AS requester_name, "
                + "       er.title, er.description, er.preferred_start_time, er.preferred_end_time, "
                + "       er.expected_capacity, er.status, er.created_at, er.processed_by, "
                + "       er.processed_at, er.organizer_note, er.created_event_id "
                + "FROM Event_Request er "
                + "JOIN Users u ON er.requester_id = u.user_id "
                + "WHERE er.request_id = ?";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, requestId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getById: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // ======================= CHECK TRÙNG LỊCH AREA (CÓ BUFFER 1H) =======================
    /**
     * Rule: - Giữa 2 event cùng 1 Area phải cách nhau ít nhất 1h (cả trước &
     * sau). - Khi check khoảng [startTime, endTime] của request mới:
     * newStartBuffer = startTime - 1h newEndBuffer = endTime + 1h => Nếu tồn
     * tại event cũ thỏa: existing.start_time < newEndBuffer
     *       AND existing.end_time > newStartBuffer => COI LÀ TRÙNG (conflict = true)
     */
    public boolean hasAreaConflict(int areaId, Timestamp startTime, Timestamp endTime) {
        if (startTime == null || endTime == null) {
            return false;
        }

        long ONE_HOUR_MS = 60L * 60L * 1000L;

        Timestamp startBuffer = new Timestamp(startTime.getTime() - ONE_HOUR_MS);
        Timestamp endBuffer = new Timestamp(endTime.getTime() + ONE_HOUR_MS);

        String sql = "SELECT COUNT(*) AS cnt "
                + "FROM Event "
                + "WHERE area_id = ? "
                + "  AND status IN ('OPEN','CLOSED','DRAFT') "
                + "  AND start_time < ? " // existing.start < newEndBuffer
                + "  AND end_time   > ?";   // existing.end > newStartBuffer

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, areaId);
            ps.setTimestamp(2, endBuffer);
            ps.setTimestamp(3, startBuffer);

            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int cnt = rs.getInt("cnt");
                    return (cnt > 0);
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] hasAreaConflict: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    // ======================= LẤY CAPACITY CỦA AREA =======================
    public Integer getAreaCapacity(int areaId) {
        String sql = "SELECT capacity FROM Venue_Area WHERE area_id = ?";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, areaId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("capacity");
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getAreaCapacity: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // ======================= APPROVE + TẠO EVENT (TRONG TRANSACTION) =======================
    public Integer approveRequestAndCreateEvent(EventRequest req,
            int organizerId,
            int areaId,
            String organizerNote) {
        Connection conn = null;
        PreparedStatement psInsertEvent = null;
        PreparedStatement psUpdateRequest = null;
        ResultSet rsKeys = null;

        try {
            conn = DBUtils.getConnection();
            conn.setAutoCommit(false); // bắt đầu transaction

            // 1) Lấy capacity khu vực để gợi ý số ghế
            Integer areaCapacity = getAreaCapacityInsideTx(conn, areaId);
            int maxSeatsFromReq = (req.getExpectedCapacity() != null ? req.getExpectedCapacity() : 0);
            int maxSeats;
            if (areaCapacity != null && areaCapacity > 0) {
                maxSeats = Math.min(maxSeatsFromReq, areaCapacity);
                if (maxSeats <= 0) {
                    maxSeats = areaCapacity; // nếu expectedCapacity = 0 thì cho = capacity
                }
            } else {
                maxSeats = maxSeatsFromReq > 0 ? maxSeatsFromReq : 50; // fallback
            }

            // 2) Insert Event
            String sqlInsertEvent = "INSERT INTO Event ("
                    + "title, description, start_time, end_time, "
                    + "speaker_id, max_seats, status, created_by, area_id"
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

            psInsertEvent = conn.prepareStatement(sqlInsertEvent, Statement.RETURN_GENERATED_KEYS);
            psInsertEvent.setNString(1, req.getTitle());
            if (req.getDescription() != null) {
                psInsertEvent.setNString(2, req.getDescription());
            } else {
                psInsertEvent.setNull(2, Types.NVARCHAR);
            }
            psInsertEvent.setTimestamp(3, req.getPreferredStartTime());
            psInsertEvent.setTimestamp(4, req.getPreferredEndTime());
            psInsertEvent.setNull(5, Types.INTEGER); // speaker_id = NULL, setup sau
            psInsertEvent.setInt(6, maxSeats);
            psInsertEvent.setNString(7, "CLOSED"); // như yêu cầu
            psInsertEvent.setInt(8, organizerId);
            psInsertEvent.setInt(9, areaId);

            int affected = psInsertEvent.executeUpdate();
            if (affected == 0) {
                conn.rollback();
                return null;
            }

            int newEventId;
            rsKeys = psInsertEvent.getGeneratedKeys();
            if (rsKeys.next()) {
                newEventId = rsKeys.getInt(1);
            } else {
                conn.rollback();
                return null;
            }

            // 3) Update Event_Request
            String sqlUpdateReq = "UPDATE Event_Request "
                    + "SET status = 'APPROVED', "
                    + "    processed_by = ?, "
                    + "    processed_at = SYSDATETIME(), "
                    + "    organizer_note = ?, "
                    + "    created_event_id = ? "
                    + "WHERE request_id = ? AND status = 'PENDING'";

            psUpdateRequest = conn.prepareStatement(sqlUpdateReq);
            psUpdateRequest.setInt(1, organizerId);
            if (organizerNote != null) {
                psUpdateRequest.setNString(2, organizerNote);
            } else {
                psUpdateRequest.setNull(2, Types.NVARCHAR);
            }
            psUpdateRequest.setInt(3, newEventId);
            psUpdateRequest.setInt(4, req.getRequestId());

            int affectedReq = psUpdateRequest.executeUpdate();
            if (affectedReq == 0) {
                conn.rollback();
                return null;
            }

            conn.commit();
            return newEventId;

        } catch (Exception e) {
            System.err.println("[ERROR] approveRequestAndCreateEvent: " + e.getMessage());
            e.printStackTrace();
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (Exception rollEx) {
                rollEx.printStackTrace();
            }
        } finally {
            try {
                if (rsKeys != null) {
                    rsKeys.close();
                }
            } catch (Exception ignore) {
            }
            try {
                if (psInsertEvent != null) {
                    psInsertEvent.close();
                }
            } catch (Exception ignore) {
            }
            try {
                if (psUpdateRequest != null) {
                    psUpdateRequest.close();
                }
            } catch (Exception ignore) {
            }
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private Integer getAreaCapacityInsideTx(Connection conn, int areaId) throws SQLException {
        String sql = "SELECT capacity FROM Venue_Area WHERE area_id = ?";
        try ( PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, areaId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("capacity");
                }
            }
        }
        return null;
    }

    // ======================= REJECT REQUEST =======================
    public boolean rejectRequest(int requestId, int organizerId, String organizerNote) {
        String sql = "UPDATE Event_Request "
                + "SET status = 'REJECTED', "
                + "    processed_by = ?, "
                + "    processed_at = SYSDATETIME(), "
                + "    organizer_note = ? "
                + "WHERE request_id = ? AND status = 'PENDING'";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, organizerId);
            if (organizerNote != null) {
                ps.setNString(2, organizerNote);
            } else {
                ps.setNull(2, Types.NVARCHAR);
            }
            ps.setInt(3, requestId);

            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (Exception e) {
            System.err.println("[ERROR] rejectRequest: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    // ======================= THÊM MỚI: LẤY REQUEST THEO created_event_id =======================
public EventRequest getByCreatedEventId(int eventId) {
    String sql = "SELECT " +
            "er.request_id, " +
            "er.requester_id, " +
            "er.title, " +
            "er.description, " +
            "er.preferred_start_time, " +
            "er.preferred_end_time, " +
            "er.expected_capacity, " +
            "er.status, " +
            "er.created_at, " +
            "er.processed_by, " +
            "er.processed_at, " +
            "er.organizer_note, " +
            "er.created_event_id, " +
            "u.full_name AS requester_name " +   // ✅ lấy tên người gửi
            "FROM Event_Request er " +
            "JOIN Users u ON er.requester_id = u.user_id " + // ✅ đúng tên bảng: Users
            "WHERE er.created_event_id = ?";

    try (Connection conn = DBUtils.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, eventId);

        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return mapRow(rs);
            }
        }
    } catch (Exception e) {
        System.err.println("[ERROR] getByCreatedEventId: " + e.getMessage());
        e.printStackTrace();
    }
    return null;
}

    // ======================= MAP ROW =======================
    private EventRequest mapRow(ResultSet rs) throws SQLException {
        EventRequest er = new EventRequest();
        er.setRequestId(rs.getInt("request_id"));
        er.setRequesterId(rs.getInt("requester_id"));

        // ✅ set thêm tên người gửi
        er.setRequesterName(rs.getString("requester_name"));

        er.setTitle(rs.getString("title"));
        er.setDescription(rs.getString("description"));
        er.setPreferredStartTime(rs.getTimestamp("preferred_start_time"));
        er.setPreferredEndTime(rs.getTimestamp("preferred_end_time"));
        er.setExpectedCapacity((Integer) rs.getObject("expected_capacity"));
        er.setStatus(rs.getString("status"));
        er.setCreatedAt(rs.getTimestamp("created_at"));
        er.setProcessedBy((Integer) rs.getObject("processed_by"));
        er.setProcessedAt(rs.getTimestamp("processed_at"));
        er.setOrganizerNote(rs.getString("organizer_note"));
        er.setCreatedEventId((Integer) rs.getObject("created_event_id"));
        return er;
    }

}
