package cric;

import java.sql.*;

public class DB {

    // 🔹 Get connection
    
    public static Connection get() {
    try {
        Class.forName("org.sqlite.JDBC");
        return DriverManager.getConnection("jdbc:sqlite:cricket.db");
    } catch (Exception e) {
        e.printStackTrace();
        return null;
    }
}

    // 🔹 Create tables
    public static void init() {
        try (Connection conn = get(); Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS match_session (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, overs INTEGER)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS batting_scorecard (" +
                    "innings INTEGER, player TEXT, runs INTEGER, balls INTEGER, fours INTEGER, sixes INTEGER, out TEXT)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS bowling_scorecard (" +
                    "innings INTEGER, player TEXT, balls INTEGER, maidens INTEGER, runs INTEGER, wickets INTEGER)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ball_by_ball (" +
                    "session_id INTEGER, innings INTEGER, over_no INTEGER, ball_no INTEGER," +
                    "batsman TEXT, bowler TEXT, runs INTEGER, extra TEXT, wicket TEXT)");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 🔹 Create match session
    public static int createMatchSession(int overs) {
        try (Connection conn = get();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO match_session(overs) VALUES(?)",
                     Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, overs);
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    // 🔹 Insert ball
    public static void insertBall(int sessionId, int innings, int over, int ball,
                                  String batsman, String bowler, int runs,
                                  String extra, String wicket) {

        try (Connection conn = get();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO ball_by_ball VALUES(?,?,?,?,?,?,?,?,?)")) {

            ps.setInt(1, sessionId);
            ps.setInt(2, innings);
            ps.setInt(3, over);
            ps.setInt(4, ball);
            ps.setString(5, batsman);
            ps.setString(6, bowler);
            ps.setInt(7, runs);
            ps.setString(8, extra);
            ps.setString(9, wicket);

            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 🔹 Insert/Update Batting
    public static void upsertBatting(int innings, String player, int runs,
                                    int balls, int fours, int sixes, String out) {

        try (Connection conn = get()) {

            PreparedStatement check = conn.prepareStatement(
                    "SELECT * FROM batting_scorecard WHERE innings=? AND player=?");
            check.setInt(1, innings);
            check.setString(2, player);

            ResultSet rs = check.executeQuery();

            if (rs.next()) {
                PreparedStatement update = conn.prepareStatement(
                        "UPDATE batting_scorecard SET runs=?, balls=?, fours=?, sixes=?, out=? WHERE innings=? AND player=?");

                update.setInt(1, runs);
                update.setInt(2, balls);
                update.setInt(3, fours);
                update.setInt(4, sixes);
                update.setString(5, out);
                update.setInt(6, innings);
                update.setString(7, player);

                update.executeUpdate();

            } else {
                PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO batting_scorecard VALUES(?,?,?,?,?,?,?)");

                insert.setInt(1, innings);
                insert.setString(2, player);
                insert.setInt(3, runs);
                insert.setInt(4, balls);
                insert.setInt(5, fours);
                insert.setInt(6, sixes);
                insert.setString(7, out);

                insert.executeUpdate();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 🔹 Insert/Update Bowling
    public static void upsertBowling(int innings, String player, int balls,
                                    int maidens, int runs, int wickets) {

        try (Connection conn = get()) {

            PreparedStatement check = conn.prepareStatement(
                    "SELECT * FROM bowling_scorecard WHERE innings=? AND player=?");
            check.setInt(1, innings);
            check.setString(2, player);

            ResultSet rs = check.executeQuery();

            if (rs.next()) {
                PreparedStatement update = conn.prepareStatement(
                        "UPDATE bowling_scorecard SET balls=?, maidens=?, runs=?, wickets=? WHERE innings=? AND player=?");

                update.setInt(1, balls);
                update.setInt(2, maidens);
                update.setInt(3, runs);
                update.setInt(4, wickets);
                update.setInt(5, innings);
                update.setString(6, player);

                update.executeUpdate();

            } else {
                PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO bowling_scorecard VALUES(?,?,?,?,?,?)");

                insert.setInt(1, innings);
                insert.setString(2, player);
                insert.setInt(3, balls);
                insert.setInt(4, maidens);
                insert.setInt(5, runs);
                insert.setInt(6, wickets);

                insert.executeUpdate();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
