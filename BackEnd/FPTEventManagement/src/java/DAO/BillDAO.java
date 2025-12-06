package DAO;

import DTO.Bill;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import mylib.DBUtils;

public class BillDAO {
    
    /**
     * Insert Bill và trả về bill_id vừa tạo
     * ✅ ĐÃ BỎ event_id - Bill chỉ liên kết với User
     * @param bill
     * @return 
     */
    public int insertBillAndReturnId(Bill bill) {
        String sql = "INSERT INTO Bill (user_id, total_amount, currency, " +
                     "payment_method, payment_status, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setInt(1, bill.getUserId());
            ps.setBigDecimal(2, bill.getTotalAmount());
            ps.setString(3, bill.getCurrency());
            ps.setString(4, bill.getPaymentMethod());
            ps.setString(5, bill.getPaymentStatus());
            ps.setTimestamp(6, bill.getCreatedAt());
            
            int affected = ps.executeUpdate();
            if (affected == 0) {
                return -1;
            }
            
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1); // bill_id
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] insertBillAndReturnId: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }
    
    /**
     * Lấy Bill theo ID
     * @param billId
     * @return 
     */
    public Bill getBillById(int billId) {
        String sql = "SELECT * FROM Bill WHERE bill_id = ?";
        
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, billId);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToBill(rs);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getBillById: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Lấy tất cả Bill của 1 User
     * @param userId
     * @return 
     */
    public List<DTO.BillResponse> getBillsByUserId(int userId) {
        String sql = "SELECT b.bill_id, b.total_amount, b.currency, b.payment_method, b.payment_status, b.created_at, u.full_name "
                + "FROM Bill b JOIN Users u ON b.user_id = u.user_id "
                + "WHERE b.user_id = ? ORDER BY b.created_at DESC";

        List<DTO.BillResponse> bills = new ArrayList<>();

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                DTO.BillResponse br = new DTO.BillResponse();
                br.setBillId(rs.getInt("bill_id"));
                br.setTotalAmount(rs.getBigDecimal("total_amount"));
                br.setCurrency(rs.getString("currency"));
                br.setPaymentMethod(rs.getString("payment_method"));
                br.setPaymentStatus(rs.getString("payment_status"));
                br.setCreatedAt(rs.getTimestamp("created_at"));
                br.setUserName(rs.getString("full_name"));
                bills.add(br);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getBillsByUserId: " + e.getMessage());
            e.printStackTrace();
        }
        return bills;
    }
    
    /**
     * Update payment status của Bill
     * @param billId
     * @param newStatus
     * @return 
     */
    public boolean updatePaymentStatus(int billId, String newStatus) {
        String sql = "UPDATE Bill SET payment_status = ? WHERE bill_id = ?";
        
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, newStatus);
            ps.setInt(2, billId);
            
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[ERROR] updatePaymentStatus: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    // Helper method
    private Bill mapResultSetToBill(ResultSet rs) throws SQLException {
        Bill bill = new Bill();
        bill.setBillId(rs.getInt("bill_id"));
        bill.setUserId(rs.getInt("user_id"));
        bill.setTotalAmount(rs.getBigDecimal("total_amount"));
        bill.setCurrency(rs.getString("currency"));
        bill.setPaymentMethod(rs.getString("payment_method"));
        bill.setPaymentStatus(rs.getString("payment_status"));
        bill.setCreatedAt(rs.getTimestamp("created_at"));
        return bill;
    }
}