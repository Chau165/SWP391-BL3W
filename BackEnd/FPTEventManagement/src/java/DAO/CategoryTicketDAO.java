package DAO;
/**
 * ========================================================================================================
 * DAO: CategoryTicketDAO - QUẢN LÝ LOẠI VÉ (CATEGORY_TICKET)
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - CRUD operations cho bảng Category_Ticket
 * - Quản lý các loại vé của sự kiện (VIP, Standard, Free...)
 * - Lấy danh sách loại vé theo eventId
 * - Kiểm tra số lượng vé còn lại
 * - Xóa loại vé khi xóa sự kiện (cascade delete)
 * 
 * DATABASE TABLE: Category_Ticket
 * Columns:
 * - categoryTicketId (INT, PK, IDENTITY): ID tự tăng
 * - eventId (INT, FK -> Events, NOT NULL): Sự kiện liên kết
 * - categoryName (NVARCHAR(100), NOT NULL): Tên loại vé (VIP, Standard, Free...)
 * - price (DECIMAL(18,2)): Giá vé (0 cho vé miễn phí)
 * - quantity (INT): Tổng số lượng vé
 * - quantityAvailable (INT): Số vé còn lại (quantity - sold)
 * - description (NVARCHAR(MAX)): Mô tả quyền lợi
 * - status (VARCHAR(50)): ACTIVE, INACTIVE, SOLD_OUT
 * 
 * RELATIONSHIPS:
 * - Category_Ticket N:1 Events (mỗi loại vé thuộc 1 sự kiện)
 * - Category_Ticket 1:N Registrations (mỗi loại vé có nhiều đăng ký)
 * 
 * KEY METHODS:
 * 1. getActiveCategoryTicketById(categoryTicketId): Lấy loại vé ACTIVE theo ID
 *    - Chỉ lấy nếu status = "ACTIVE"
 *    - Trả về null nếu không tìm thấy hoặc INACTIVE
 *    - Dùng khi user đăng ký vé
 * 
 * 2. deleteByEventId(eventId): Xóa tất cả loại vé của sự kiện
 *    - Cascade delete khi xóa event
 *    - Hoặc khi ORGANIZER hủy sự kiện
 *    - Trả về true nếu thành công
 * 
 * 3. insertCategoryTicket(categoryTicket): Tạo loại vé mới
 *    - Validate eventId NOT NULL
 *    - Kiểm tra categoryName, price, quantity
 *    - quantityAvailable = quantity ban đầu
 *    - Trả về true nếu insert thành công
 * 
 * 4. getCategoriesByEventId(eventId): Lấy tất cả loại vé của event
 * 5. updateQuantityAvailable(categoryTicketId, newQuantity): Cập nhật số vé còn lại
 * 6. checkAvailability(categoryTicketId): Kiểm tra còn vé không
 * 
 * TICKET CATEGORIES:
 * - VIP: Vé cao cấp, giá cao, quyền lợi đặc biệt
 * - Standard: Vé thường, giá vừa phải
 * - Free: Vé miễn phí (price = 0)
 * - Early Bird: Vé ưu đãi đăng ký sớm
 * - Student: Vé sinh viên (giảm giá)
 * 
 * BUSINESS RULES:
 * - quantityAvailable <= quantity
 * - quantityAvailable >= 0
 * - price >= 0 (0 cho vé miễn phí)
 * - Không cho phép xóa category đã có registrations (FK constraint)
 * - Auto set status = SOLD_OUT khi quantityAvailable = 0
 * 
 * QUANTITY MANAGEMENT:
 * - quantity: Tổng số vé ban đầu (đặt bởi ORGANIZER)
 * - quantityAvailable: Số vé còn lại (giảm khi có registration)
 * - sold = quantity - quantityAvailable
 * - Update quantityAvailable sau mỗi registration success
 * 
 * SQL NULL HANDLING:
 * - eventId: Validate NOT NULL trước khi insert
 * - price: Default = 0 nếu null
 * - quantity: Default = 0 nếu null
 * - description: Có thể null
 * 
 * USE CASES:
 * - Tạo sự kiện: ORGANIZER tạo các loại vé (VIP, Standard...)
 * - Đăng ký: User chọn loại vé, kiểm tra còn slot
 * - Thanh toán: Lấy giá vé để tính tiền
 * - Dashboard: Hiển thị số vé bán/còn lại
 * 
 * KẾT NỐI FILE:
 * - DTO: DTO/Category_Ticket.java (entity class)
 * - DAO: DAO/EventDAO.java (parent entity)
 * - DAO: DAO/TicketDAO.java (registrations)
 * - Controller: controller/CategoryTicketController.java
 * - Controller: controller/RegistrationController.java (check availability)
 */

import DTO.CategoryTicket;
import java.math.BigDecimal;
import java.sql.*;
import mylib.DBUtils;

public class CategoryTicketDAO {

    public CategoryTicket getActiveCategoryTicketById(int id) {
        String sql = "SELECT category_ticket_id, event_id, name, description, price, "
                + "       max_quantity, status "
                + "FROM Category_Ticket "
                + "WHERE category_ticket_id = ? AND status = 'ACTIVE'";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try ( ResultSet rs = ps.executeQuery()) {
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
        try ( PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.executeUpdate();
        }
    }

    // ================== THÊM MỚI: INSERT CATEGORY_TICKET CHO 1 EVENT ==================
    public void insertCategoryTicket(Connection conn, CategoryTicket ct) throws SQLException {
        String sql = "INSERT INTO Category_Ticket (event_id, name, description, price, max_quantity, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try ( PreparedStatement ps = conn.prepareStatement(sql)) {
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

    public CategoryTicket getActiveCategoryTicketByEventIdAndName(int eventId, String name) {
        String sql
                = "SELECT TOP 1 category_ticket_id, event_id, name, description, price, "
                + "       max_quantity, status "
                + "FROM Category_Ticket "
                + "WHERE event_id = ? "
                + "  AND LOWER(name) = LOWER(?) "
                + "  AND status = 'ACTIVE'";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, eventId);
            ps.setString(2, name); // seatType: VIP / STANDARD / ...

            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    CategoryTicket ct = new CategoryTicket();
                    ct.setCategoryTicketId(rs.getInt("category_ticket_id"));
                    ct.setEventId(rs.getInt("event_id"));
                    ct.setName(rs.getString("name"));
                    ct.setDescription(rs.getString("description"));
                    ct.setPrice(rs.getBigDecimal("price"));
                    ct.setMaxQuantity((Integer) rs.getObject("max_quantity")); // có thể null
                    ct.setStatus(rs.getString("status"));
                    return ct;
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getActiveCategoryTicketByEventIdAndName: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public CategoryTicket getByEventIdAndName(Connection conn, int eventId, String name) throws SQLException {
        String sql = "SELECT category_ticket_id, event_id, name, description, price, max_quantity, status "
                + "FROM Category_Ticket WHERE event_id = ? AND name = ?";
        try ( PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.setString(2, name);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    CategoryTicket ct = new CategoryTicket();
                    ct.setCategoryTicketId(rs.getInt("category_ticket_id"));
                    ct.setEventId(rs.getInt("event_id"));
                    ct.setName(rs.getString("name"));
                    ct.setDescription(rs.getString("description"));
                    ct.setPrice(rs.getBigDecimal("price"));
                    ct.setMaxQuantity(rs.getInt("max_quantity"));
                    ct.setStatus(rs.getString("status"));
                    return ct;
                }
                return null;
            }
        }
    }

    public void updateTicketInfoExceptQuantity(Connection conn, int categoryTicketId,
            String description, BigDecimal price, String status) throws SQLException {
        String sql = "UPDATE Category_Ticket SET description = ?, price = ?, status = ? WHERE category_ticket_id = ?";
        try ( PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, description);
            ps.setBigDecimal(2, price);
            ps.setString(3, status);
            ps.setInt(4, categoryTicketId);
            ps.executeUpdate();
        }
    }

    public void updateMaxQuantity(Connection conn, int categoryTicketId, int maxQuantity) throws SQLException {
        String sql = "UPDATE Category_Ticket SET max_quantity = ? WHERE category_ticket_id = ?";
        try ( PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, maxQuantity);
            ps.setInt(2, categoryTicketId);
            ps.executeUpdate();
        }
    }

}
