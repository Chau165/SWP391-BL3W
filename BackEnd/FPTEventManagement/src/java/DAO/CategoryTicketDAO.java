package DAO;

import DTO.CategoryTicket;
import java.sql.*;
import mylib.DBUtils;

public class CategoryTicketDAO {

    public CategoryTicket getActiveCategoryTicketById(int id) {
        String sql = "SELECT category_ticket_id, event_id, name, description, price, " +
                     "       max_quantity, status " +
                     "FROM Category_Ticket " +
                     "WHERE category_ticket_id = ? AND status = 'ACTIVE'";

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    CategoryTicket ct = new CategoryTicket();
                    ct.setCategoryTicketId(rs.getInt("category_ticket_id"));
                    ct.setEventId(rs.getInt("event_id"));
                    ct.setName(rs.getString("name"));
                    ct.setDescription(rs.getString("description"));
                    ct.setPrice(rs.getBigDecimal("price")); // hoặc double tuỳ DTO
                    ct.setMaxQuantity((Integer) rs.getObject("max_quantity")); // có thể null
                    ct.setStatus(rs.getString("status"));
                    return ct;
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getActiveCategoryTicketById: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
        // ================== THÊM MỚI: XÓA TOÀN BỘ CATEGORY_TICKET CỦA 1 EVENT ==================
    public void deleteByEventId(Connection conn, int eventId) throws SQLException {
        String sql = "DELETE FROM Category_Ticket WHERE event_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.executeUpdate();
        }
    }

    // ================== THÊM MỚI: INSERT CATEGORY_TICKET CHO 1 EVENT ==================
    public void insertCategoryTicket(Connection conn, CategoryTicket ct) throws SQLException {
        String sql = "INSERT INTO Category_Ticket (event_id, name, description, price, max_quantity, status) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ct.getEventId());
            ps.setNString(2, ct.getName());

            if (ct.getDescription() != null) {
                ps.setNString(3, ct.getDescription());
            } else {
                ps.setNull(3, Types.NVARCHAR);
            }

            if (ct.getPrice() != null) {
                ps.setBigDecimal(4, ct.getPrice());
            } else {
                ps.setNull(4, Types.DECIMAL);
            }

            if (ct.getMaxQuantity() != null) {
                ps.setInt(5, ct.getMaxQuantity());
            } else {
                ps.setNull(5, Types.INTEGER);
            }

            ps.setString(6, ct.getStatus() != null ? ct.getStatus() : "ACTIVE");

            ps.executeUpdate();
        }
    }
}
