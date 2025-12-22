// DAO/WalletDAO.java
package DAO;

import java.math.BigDecimal;
import java.sql.*;

public class WalletDAO {

    public BigDecimal getWalletForUpdate(Connection conn, int userId) throws SQLException {
        String sql = "SELECT Wallet FROM Users WITH (UPDLOCK, ROWLOCK) WHERE user_id = ?";
        try ( PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal w = rs.getBigDecimal("Wallet");
                    return w != null ? w : BigDecimal.ZERO;
                }
                return null;
            }
        }
    }

    public boolean deductWallet(Connection conn, int userId, BigDecimal amount) throws SQLException {
        String sql = "UPDATE Users SET Wallet = Wallet - ? WHERE user_id = ? AND Wallet >= ?";
        try ( PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, amount);
            ps.setInt(2, userId);
            ps.setBigDecimal(3, amount);
            return ps.executeUpdate() == 1;
        }
    }

    public BigDecimal getWalletByUserId(Connection conn, int userId) throws SQLException {
        String sql = "SELECT wallet FROM users WHERE user_id = ?";
        try ( PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("wallet");
                }
            }
        }
        return null;
    }
}
