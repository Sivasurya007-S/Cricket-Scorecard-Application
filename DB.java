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
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, match_id INTEGER, innings_no INTEGER, player TEXT, runs INTEGER, balls INTEGER, fours INTEGER, sixes INTEGER, out_desc TEXT DEFAULT 'not out', "
                    +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS bowling_scorecard (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, match_id INTEGER, innings_no INTEGER, bowler TEXT, balls INTEGER, maidens INTEGER, runs INTEGER, wickets INTEGER, "
                    +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");

            try {
                stmt.executeUpdate("ALTER TABLE batting_scorecard ADD COLUMN match_id INTEGER DEFAULT 0");
            } catch (Exception e) {}
            try {
                stmt.executeUpdate("ALTER TABLE bowling_scorecard ADD COLUMN match_id INTEGER DEFAULT 0");
            } catch (Exception e) {}

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ball_by_ball (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, match_id INTEGER, innings_number INTEGER, over_number INTEGER, ball_number INTEGER,"
                    +
                    "batsman TEXT, bowler TEXT, runs INTEGER DEFAULT 0, extra_type TEXT, wicket TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS toss_details (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, team1 TEXT, team2 TEXT," +
                    "toss_winner TEXT, opted TEXT, overs TEXT)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS opening_players (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, striker TEXT, non_striker TEXT, bowler TEXT)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS teams_stats (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, team_name TEXT, matches_played INTEGER, matches_won INTEGER, matches_lost INTEGER)");

            // Seed Teams
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM teams_stats");
            if (rs.next() && rs.getInt(1) == 0) {
                String[] seedSql = {
                        "INSERT INTO teams_stats(team_name, matches_played, matches_won, matches_lost) VALUES('India', 1050, 580, 420)",
                        "INSERT INTO teams_stats(team_name, matches_played, matches_won, matches_lost) VALUES('Australia', 980, 594, 340)",
                        "INSERT INTO teams_stats(team_name, matches_played, matches_won, matches_lost) VALUES('England', 780, 390, 350)",
                        "INSERT INTO teams_stats(team_name, matches_played, matches_won, matches_lost) VALUES('New Zealand', 800, 370, 380)",
                        "INSERT INTO teams_stats(team_name, matches_played, matches_won, matches_lost) VALUES('South Africa', 650, 400, 220)",
                        "INSERT INTO teams_stats(team_name, matches_played, matches_won, matches_lost) VALUES('Pakistan', 960, 500, 430)",
                        "INSERT INTO teams_stats(team_name, matches_played, matches_won, matches_lost) VALUES('Sri Lanka', 890, 410, 440)",
                        "INSERT INTO teams_stats(team_name, matches_played, matches_won, matches_lost) VALUES('West Indies', 860, 415, 410)",
                        "INSERT INTO teams_stats(team_name, matches_played, matches_won, matches_lost) VALUES('Bangladesh', 420, 150, 260)",
                        "INSERT INTO teams_stats(team_name, matches_played, matches_won, matches_lost) VALUES('Afghanistan', 160, 80, 75)"
                };
                for (String q : seedSql) {
                    stmt.executeUpdate(q);
                }
            }

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
            if (rs.next())
                return rs.getInt(1);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    private static final java.util.concurrent.ExecutorService dbExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    // 🔹 Insert ball
    public static void insertBall(int sessionId, int innings, int over, int ball,
            String batsman, String bowler, int runs,
            String extra, String wicket) {
        dbExecutor.execute(() -> {
            try (Connection conn = get();
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO ball_by_ball(match_id, innings_number, over_number, ball_number, batsman, bowler, runs, extra_type, wicket) VALUES(?,?,?,?,?,?,?,?,?)")) {

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
        });
    }

    // 🔹 Insert/Update Batting
    public static void upsertBatting(int matchId, int innings, String player, int runs,
            int balls, int fours, int sixes, String out) {
        dbExecutor.execute(() -> {
            try (Connection conn = get()) {

                PreparedStatement check = conn.prepareStatement(
                        "SELECT * FROM batting_scorecard WHERE match_id=? AND innings_no=? AND player=?");
                check.setInt(1, matchId);
                check.setInt(2, innings);
                check.setString(3, player);

                ResultSet rs = check.executeQuery();

                if (rs.next()) {
                    PreparedStatement update = conn.prepareStatement(
                            "UPDATE batting_scorecard SET runs=?, balls=?, fours=?, sixes=?, out_desc=? WHERE match_id=? AND innings_no=? AND player=?");

                    update.setInt(1, runs);
                    update.setInt(2, balls);
                    update.setInt(3, fours);
                    update.setInt(4, sixes);
                    update.setString(5, out);
                    update.setInt(6, matchId);
                    update.setInt(7, innings);
                    update.setString(8, player);

                    update.executeUpdate();

                } else {
                    PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO batting_scorecard(match_id, innings_no, player, runs, balls, fours, sixes, out_desc) VALUES(?,?,?,?,?,?,?,?)");

                    insert.setInt(1, matchId);
                    insert.setInt(2, innings);
                    insert.setString(3, player);
                    insert.setInt(4, runs);
                    insert.setInt(5, balls);
                    insert.setInt(6, fours);
                    insert.setInt(7, sixes);
                    insert.setString(8, out);

                    insert.executeUpdate();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // 🔹 Insert/Update Bowling
    public static void upsertBowling(int matchId, int innings, String bowler, int balls,
            int maidens, int runs, int wickets) {
        dbExecutor.execute(() -> {
            try (Connection conn = get()) {

                PreparedStatement check = conn.prepareStatement(
                        "SELECT * FROM bowling_scorecard WHERE match_id=? AND innings_no=? AND bowler=?");
                check.setInt(1, matchId);
                check.setInt(2, innings);
                check.setString(3, bowler);

                ResultSet rs = check.executeQuery();

                if (rs.next()) {
                    PreparedStatement update = conn.prepareStatement(
                            "UPDATE bowling_scorecard SET balls=?, maidens=?, runs=?, wickets=? WHERE match_id=? AND innings_no=? AND bowler=?");

                    update.setInt(1, balls);
                    update.setInt(2, maidens);
                    update.setInt(3, runs);
                    update.setInt(4, wickets);
                    update.setInt(5, matchId);
                    update.setInt(6, innings);
                    update.setString(7, bowler);

                    update.executeUpdate();

                } else {
                    PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO bowling_scorecard(match_id, innings_no, bowler, balls, maidens, runs, wickets) VALUES(?,?,?,?,?,?,?)");

                    insert.setInt(1, matchId);
                    insert.setInt(2, innings);
                    insert.setString(3, bowler);
                    insert.setInt(4, balls);
                    insert.setInt(5, maidens);
                    insert.setInt(6, runs);
                    insert.setInt(7, wickets);

                    insert.executeUpdate();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // 🔹 Get Team Stats
    public static java.util.List<java.util.Map<String, Object>> getTeamStats() {
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        try (Connection conn = get();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM teams_stats")) {
            while (rs.next()) {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("team_name", rs.getString("team_name"));
                map.put("matches_played", rs.getInt("matches_played"));
                map.put("matches_won", rs.getInt("matches_won"));
                map.put("matches_lost", rs.getInt("matches_lost"));
                list.add(map);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
