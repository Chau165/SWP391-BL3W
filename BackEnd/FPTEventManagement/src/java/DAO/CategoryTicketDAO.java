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
}
