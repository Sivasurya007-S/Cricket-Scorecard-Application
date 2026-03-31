// File: DB.java
package cric;

import java.sql.*;
import java.util.Objects;

public class DB {

    // ⚙️ Adjust if your MySQL user/pass differ
    private static final String URL  = "jdbc:mysql://localhost:3306/cricket_app?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASS = "dinesh007";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            // Create any missing tables we rely on (idempotent)
            initSchema();
            System.out.println("✅ MySQL driver loaded & schema ensured.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    /** Ensures auxiliary tables & constraints we reference exist. */
    private static void initSchema() {
        try (Connection c = get(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS match_session (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  overs INT NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS batting_scorecard (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    innings_no INT NOT NULL,
                    player VARCHAR(100) NOT NULL,
                    runs INT NOT NULL DEFAULT 0,
                    balls INT NOT NULL DEFAULT 0,
                    fours INT NOT NULL DEFAULT 0,
                    sixes INT NOT NULL DEFAULT 0,
                    out_desc VARCHAR(100) NOT NULL DEFAULT 'not out',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY unique_batter (innings_no, player)
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS bowling_scorecard (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    innings_no INT NOT NULL,
                    bowler VARCHAR(100) NOT NULL,
                    balls INT NOT NULL DEFAULT 0,
                    maidens INT NOT NULL DEFAULT 0,
                    runs INT NOT NULL DEFAULT 0,
                    wickets INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY unique_bowler (innings_no, bowler)
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ball_by_ball (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  match_id INT,
                  innings_number INT,
                  over_number INT,
                  ball_number INT,
                  batsman VARCHAR(100),
                  bowler VARCHAR(100),
                  runs INT DEFAULT 0,
                  extra_type VARCHAR(20),
                  wicket VARCHAR(10),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 🆕 Create a new match session and return its id (no UI change in callers)
    public static int createMatchSession(int overs) {
        String sql = "INSERT INTO match_session (overs) VALUES (?)";
        try (Connection c = get();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, overs);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("❌ createMatchSession failed: " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }

    // 🧾 Insert or update batting (by innings_no + player)
    public static void upsertBatting(int inningsNo, String player, int runs, int balls, int fours, int sixes, String outDesc) {
        Objects.requireNonNull(player, "player");
        String sql = """
            INSERT INTO batting_scorecard (innings_no, player, runs, balls, fours, sixes, out_desc)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              runs = VALUES(runs),
              balls = VALUES(balls),
              fours = VALUES(fours),
              sixes = VALUES(sixes),
              out_desc = VALUES(out_desc)
        """;
        try (Connection c = get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, inningsNo);
            ps.setString(2, player);
            ps.setInt(3, runs);
            ps.setInt(4, balls);
            ps.setInt(5, fours);
            ps.setInt(6, sixes);
            ps.setString(7, (outDesc == null || outDesc.isBlank()) ? "not out" : outDesc);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ upsertBatting failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 🎯 Insert or update bowling (by innings_no + bowler)
    public static void upsertBowling(int inningsNo, String bowler, int balls, int maidens, int runs, int wkts) {
        Objects.requireNonNull(bowler, "bowler");
        String sql = """
            INSERT INTO bowling_scorecard (innings_no, bowler, balls, maidens, runs, wickets)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              balls = VALUES(balls),
              maidens = VALUES(maidens),
              runs = VALUES(runs),
              wickets = VALUES(wickets)
        """;
        try (Connection c = get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, inningsNo);
            ps.setString(2, bowler);
            ps.setInt(3, balls);
            ps.setInt(4, maidens);
            ps.setInt(5, runs);
            ps.setInt(6, wkts);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ upsertBowling failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 🟢 Ball-by-ball logging
    public static void insertBall(int matchId, int inningsNumber, int overNumber, int ballNumber,
                                  String batsman, String bowler, int runs, String extraType, String wicketFlag) {
        String sql = """
            INSERT INTO ball_by_ball
            (match_id, innings_number, over_number, ball_number, batsman, bowler, runs, extra_type, wicket)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection c = get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, matchId);
            ps.setInt(2, inningsNumber);
            ps.setInt(3, overNumber);
            ps.setInt(4, ballNumber);
            ps.setString(5, batsman);
            ps.setString(6, bowler);
            ps.setInt(7, runs);
            ps.setString(8, extraType);
            ps.setString(9, wicketFlag);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ insertBall failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 🔙 Delete the last ball logged for this match & innings
    public static void deleteLastBall(int matchId, int inningsNumber) {
        String sql = """
            DELETE FROM ball_by_ball
            WHERE id = (
              SELECT id FROM (
                SELECT id FROM ball_by_ball
                WHERE match_id=? AND innings_number=?
                ORDER BY id DESC LIMIT 1
              ) t
            )
        """;
        try (Connection c = get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, matchId);
            ps.setInt(2, inningsNumber);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ deleteLastBall failed: " + e.getMessage());
        }
    }
}