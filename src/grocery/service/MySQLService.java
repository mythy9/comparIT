package grocery.service;

import grocery.model.Product;

import java.sql.*;
import java.util.*;

/**
 * Service managing connection and persistence with MySQL.
 * Database: "comparit_db"
 * Tables:
 *   - "products"        : grocery catalogue
 *   - "cart"            : active user cart items with quantity and product details
 *   - "favourites"      : saved user favourite items
 *   - "recent_searches" : recent user search queries
 */
public class MySQLService {

    private static final String DB_HOST = "localhost";
    private static final int    DB_PORT = 3306;
    private static final String DB_NAME = "comparit_db";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "mithileshbobbili";   // change if your root has a password

    private Connection conn = null;
    private boolean connected = false;

    public MySQLService() {}

    // ------------------------------------------------------------------ init

    public void init(List<Product> catalog) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT
                    + "/?serverTimezone=UTC&connectTimeout=3000&socketTimeout=3000";
            conn = DriverManager.getConnection(url, DB_USER, DB_PASS);

            // Create database if absent
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + DB_NAME + "`");
                st.executeUpdate("USE `" + DB_NAME + "`");
            }

            createTables();
            connected = true;

            System.out.println("==================================================");
            System.out.println("  [MySQL] Connected to database: " + DB_NAME);
            System.out.println("  [MySQL] Host: " + DB_HOST + ":" + DB_PORT);

            // Seed products table if empty
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM products")) {
                rs.next();
                int count = rs.getInt(1);
                if (count == 0 && catalog != null && !catalog.isEmpty()) {
                    String sql = "INSERT INTO products (name, category, emoji, unit, basePrice, imageUrl) VALUES (?,?,?,?,?,?)";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (Product p : catalog) {
                            ps.setString(1, p.getName());
                            ps.setString(2, p.getCategory());
                            ps.setString(3, p.getEmoji());
                            ps.setString(4, p.getUnit());
                            ps.setDouble(5, p.getBasePrice());
                            ps.setString(6, p.getImageUrl());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                    System.out.println("  [MySQL] Populated 'products' table with " + catalog.size() + " items.");
                } else {
                    System.out.println("  [MySQL] 'products' table has " + count + " items.");
                }
            }

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM cart")) {
                rs.next();
                System.out.println("  [MySQL] 'cart' table has " + rs.getInt(1) + " items.");
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM favourites")) {
                rs.next();
                System.out.println("  [MySQL] 'favourites' table has " + rs.getInt(1) + " items.");
            }
            System.out.println("==================================================");

        } catch (Exception e) {
            connected = false;
            System.err.println("  [MySQL] Connection failed: " + e.getMessage());
            System.err.println("  [MySQL] (Application will continue running in in-memory mode)");
        }
    }

    private void createTables() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("USE `" + DB_NAME + "`");

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS products (" +
                "  id        INT AUTO_INCREMENT PRIMARY KEY," +
                "  name      VARCHAR(255) NOT NULL," +
                "  category  VARCHAR(255)," +
                "  emoji     VARCHAR(16)," +
                "  unit      VARCHAR(64)," +
                "  basePrice DOUBLE," +
                "  imageUrl  TEXT," +
                "  UNIQUE KEY uq_name (name)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS cart (" +
                "  productName VARCHAR(255) NOT NULL PRIMARY KEY," +
                "  qty         INT          NOT NULL DEFAULT 0," +
                "  unit        VARCHAR(64)," +
                "  category    VARCHAR(255)," +
                "  emoji       VARCHAR(16)," +
                "  basePrice   DOUBLE," +
                "  updatedAt   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS favourites (" +
                "  productName VARCHAR(255) NOT NULL PRIMARY KEY," +
                "  addedAt     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS recent_searches (" +
                "  query     VARCHAR(512) NOT NULL PRIMARY KEY," +
                "  timestamp DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
        }
    }

    public boolean isConnected() {
        return connected;
    }

    // ------------------------------------------------------------------ Cart

    public Map<String, Integer> getCart() {
        Map<String, Integer> cart = new LinkedHashMap<>();
        if (!connected) return cart;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT productName, qty FROM cart WHERE qty > 0")) {
            while (rs.next()) {
                cart.put(rs.getString("productName"), rs.getInt("qty"));
            }
        } catch (Exception e) {
            System.err.println("Error reading cart from MySQL: " + e.getMessage());
        }
        return cart;
    }

    public void updateCartItem(String productName, int qty, Product product) {
        if (!connected || productName == null || productName.isBlank()) return;
        try {
            if (qty <= 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM cart WHERE productName = ?")) {
                    ps.setString(1, productName);
                    ps.executeUpdate();
                }
            } else {
                String sql =
                    "INSERT INTO cart (productName, qty, unit, category, emoji, basePrice) " +
                    "VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "  qty = VALUES(qty), unit = VALUES(unit), category = VALUES(category)," +
                    "  emoji = VALUES(emoji), basePrice = VALUES(basePrice)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, productName);
                    ps.setInt(2, qty);
                    ps.setString(3, product != null ? product.getUnit()     : null);
                    ps.setString(4, product != null ? product.getCategory() : null);
                    ps.setString(5, product != null ? product.getEmoji()    : null);
                    if (product != null) ps.setDouble(6, product.getBasePrice());
                    else                 ps.setNull(6, Types.DOUBLE);
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) {
            System.err.println("Error updating cart in MySQL: " + e.getMessage());
        }
    }

    public void clearCart() {
        if (!connected) return;
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM cart");
        } catch (Exception e) {
            System.err.println("Error clearing cart in MySQL: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ Favourites

    public Set<String> getFavourites() {
        Set<String> favs = new LinkedHashSet<>();
        if (!connected) return favs;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT productName FROM favourites")) {
            while (rs.next()) favs.add(rs.getString("productName"));
        } catch (Exception e) {
            System.err.println("Error reading favourites from MySQL: " + e.getMessage());
        }
        return favs;
    }

    public boolean toggleFavourite(String productName) {
        if (!connected || productName == null || productName.isBlank()) return false;
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM favourites WHERE productName = ?")) {
                ps.setString(1, productName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        // Already a favourite — remove it
                        try (PreparedStatement del = conn.prepareStatement(
                                "DELETE FROM favourites WHERE productName = ?")) {
                            del.setString(1, productName);
                            del.executeUpdate();
                        }
                        return false;
                    } else {
                        // Add as favourite
                        try (PreparedStatement ins = conn.prepareStatement(
                                "INSERT INTO favourites (productName) VALUES (?)")) {
                            ins.setString(1, productName);
                            ins.executeUpdate();
                        }
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error toggling favourite in MySQL: " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------ Recent Searches

    public List<String> getRecentSearches() {
        List<String> list = new ArrayList<>();
        if (!connected) return list;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT query FROM recent_searches ORDER BY timestamp DESC LIMIT 10")) {
            while (rs.next()) list.add(rs.getString("query"));
        } catch (Exception e) {
            System.err.println("Error reading recent searches from MySQL: " + e.getMessage());
        }
        return list;
    }

    public void addRecentSearch(String query) {
        if (!connected || query == null || query.isBlank()) return;
        try {
            String sql =
                "INSERT INTO recent_searches (query) VALUES (?) " +
                "ON DUPLICATE KEY UPDATE timestamp = CURRENT_TIMESTAMP";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, query.trim());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error saving recent search in MySQL: " + e.getMessage());
        }
    }
}
