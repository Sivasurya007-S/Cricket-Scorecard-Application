// File: ScorecardUI.java
package cric;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.sql.*;
import java.util.*;
import java.util.List;

/**
 * ScorecardUI
 * - Highlights best batter (most runs) and best bowler (most wickets)
 * - Two-line batter renderer (name + dismissal)
 * - Cricbuzz-like batting column widths (compact/mobile style)
 * - No horizontal scrollbar for batting table (AUTO_RESIZE_SUBSEQUENT_COLUMNS + enforced small numeric widths)
 *
 * NOTE: This class depends on DB.get() and MatchContext.sessionId available elsewhere in the project.
 */
public class ScorecardUI extends JFrame {

    // ======= THEME =======
    private static final Color BG = new Color(28, 37, 51);
    private static final Color CARD = new Color(33, 44, 61);
    private static final Color CARD_SOFT = new Color(39, 52, 72);
    private static final Color TEXT = Color.WHITE;
    private static final Color SUBTLE = new Color(190, 200, 215);
    private static final Color ACCENT = new Color(52, 92, 255);
    private static final Color HIGHLIGHT_BAT = new Color(60, 120, 60);
    private static final Color HIGHLIGHT_BOWL = new Color(120, 60, 60);

    private final JTabbedPane tabs = new JTabbedPane();

    // caches for highlighting
    private final Set<String> bestBatters = new HashSet<>();
    private final Set<String> bestBowlers = new HashSet<>();

    // Layout sizes for "mobile/compact" style
    private static final int FRAME_WIDTH = 900;
    private static final int FRAME_HEIGHT = 720;
    private static final int CARD_WIDTH = 840;   // preferred width for scroll panes/cards

    public ScorecardUI() {
        setTitle("📒 Full Scorecard");
        setSize(FRAME_WIDTH, FRAME_HEIGHT);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);

        tabs.setBackground(BG);
        tabs.setForeground(TEXT);
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 14));
        tabs.setBorder(new EmptyBorder(8, 8, 8, 8));

        // compute bests first
        computeBestBatterAndBowler();

        // build tabs for the current match only (uses MatchContext.sessionId)
        tabs.add(getTeamName(1) + " - 1st Innings", buildInningsPanel(1));
        tabs.add(getTeamName(2) + " - 2nd Innings", buildInningsPanel(2));

        add(tabs);
        setVisible(true);
    }

    // Build a panel for an innings
    private JPanel buildInningsPanel(int inningsNo) {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Batting section
        root.add(sectionHeader("Batting"));
        JTable batTable = themedTableBatting(new String[]{"Batter", "R", "B", "4s", "6s", "S/R"});
        fillBattingTable(batTable, inningsNo);
        root.add(wrapCard(batTable, 300));

        // Extras + Total
        Map<String, Integer> extras = getExtras(inningsNo);
        int extrasTotal = extras.values().stream().mapToInt(i -> i).sum();

        int totalRuns = getTotalRuns(inningsNo);
        int wkts = getWickets(inningsNo);
        String oversTxt = legalOversText(inningsNo);

        JPanel info = new JPanel(new GridLayout(2, 1));
        info.setBackground(CARD);
        info.setBorder(new EmptyBorder(14, 18, 14, 18));

        JLabel extrasLbl = new JLabel("Extras  " + extrasLine(extras, extrasTotal));
        extrasLbl.setForeground(SUBTLE);
        extrasLbl.setFont(new Font("Segoe UI", Font.PLAIN, 15));

        JLabel totalLbl = boldLabel(
                "Total  " + totalRuns + "   (" + wkts + " wkts, " + oversTxt + " ov)");
        totalLbl.setForeground(TEXT);

        info.add(extrasLbl);
        info.add(totalLbl);
        root.add(gap(10));
        root.add(cardWrap(info));

        // FOW
        root.add(gap(10));
        root.add(sectionHeader("Fall of wickets"));
        JLabel fowLbl = new JLabel(buildFOW(inningsNo));
        fowLbl.setForeground(SUBTLE);
        fowLbl.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        fowLbl.setBorder(new EmptyBorder(12, 18, 12, 18));
        root.add(cardWrap(fowLbl));

        // Bowling section
        root.add(gap(12));
        root.add(sectionHeader("Bowling"));
        JTable bowlTable = themedTableBowling(new String[]{"Bowler", "O", "M", "R", "W", "Econ"});
        fillBowlingTable(bowlTable, inningsNo);
        root.add(wrapCard(bowlTable, 230));

        root.add(Box.createVerticalGlue());
        return root;
    }

    private Component cardWrap(Component c) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD);
        card.setBorder(new EmptyBorder(8, 8, 8, 8));
        card.add(c, BorderLayout.CENTER);
        return card;
    }

    private JScrollPane wrapCard(JTable table, int prefHeight) {
        // vertical scrollbar as-needed, horizontal scrollbar NEVER
        JScrollPane sp = new JScrollPane(table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setPreferredSize(new Dimension(CARD_WIDTH, prefHeight));
        sp.setBorder(BorderFactory.createEmptyBorder());

        // Ensure the viewport width equals CARD_WIDTH so AUTO_RESIZE_SUBSEQUENT_COLUMNS can
        // distribute extra space instead of creating a scrollbar.
        table.setPreferredScrollableViewportSize(new Dimension(CARD_WIDTH, prefHeight));
        return sp;
    }

    private JLabel sectionHeader(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT);
        l.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 16));
        l.setBorder(new EmptyBorder(6, 2, 6, 2));
        return l;
    }

    private JLabel boldLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("Segoe UI", Font.BOLD, 16));
        return l;
    }

    private Component gap(int h) {
        return Box.createVerticalStrut(h);
    }

    // -------------------------
    // Batting table - taller rows
    // -------------------------
    private JTable themedTableBatting(String[] cols) {
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable t = new JTable(model) {
            @Override
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component comp = super.prepareRenderer(renderer, row, column);
                if (comp instanceof JComponent jc) jc.setBorder(new EmptyBorder(6, 10, 6, 10));

                // safe guard: if no data in model yet, return defaults
                if (getModel().getRowCount() == 0) {
                	comp.setBackground(new Color(70, 110, 200));  // soft bright blue
                	comp.setForeground(Color.WHITE);
                    return comp;
                }

                String nameCell = Objects.toString(getModel().getValueAt(row, 0), "");
                String name = nameCell.split("\\n")[0].trim();
                if (bestBatters.contains(name)) {
                    comp.setBackground(HIGHLIGHT_BAT);
                    comp.setForeground(Color.WHITE);
                } else {
                    comp.setBackground(CARD_SOFT);
                    comp.setForeground(TEXT);
                }
                return comp;
            }
        };

        t.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        t.setForeground(TEXT);
        t.setBackground(CARD_SOFT);
        t.setRowHeight(58);                    // taller rows so second line is visible
        t.setIntercellSpacing(new Dimension(0, 8)); // slight spacing
        t.setShowGrid(false);
        t.setFillsViewportHeight(true);

        JTableHeader hd = t.getTableHeader();
        hd.setBackground(new Color(220, 220, 230)); // light grey
        hd.setForeground(Color.BLACK);
        hd.setFont(new Font("Segoe UI", Font.BOLD, 13));
        ((DefaultTableCellRenderer) hd.getDefaultRenderer()).setHorizontalAlignment(SwingConstants.LEFT);

        // right align numeric columns
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        right.setForeground(TEXT);

        // attach numeric renderer safely
        if (t.getColumnModel().getColumnCount() >= cols.length) {
            for (int i = 1; i < cols.length; i++) {
                t.getColumnModel().getColumn(i).setCellRenderer(right);
            }
        }

        // --- KEY PART: make the table auto-resize to fill the CARD width
        // and ensure numeric columns remain narrow while S/R is slightly larger.
        // This combination avoids horizontal scrollbar (we keep JScrollPane.HORIZONTAL_SCROLLBAR_NEVER).
     // Make batting table expand like bowling table
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        // Two-line renderer for Batter column
        t.getColumnModel().getColumn(0).setCellRenderer(new TwoLineRenderer(true));

        int colCount = t.getColumnModel().getColumnCount();
        if (colCount >= cols.length) {

            // Batter column expands freely
            t.getColumnModel().getColumn(0).setMinWidth(180);
            t.getColumnModel().getColumn(0).setPreferredWidth(180);
            t.getColumnModel().getColumn(0).setMaxWidth(Integer.MAX_VALUE);

            // Numeric columns stay compact but flexible
            for (int i = 1; i <= 4; i++) {
                t.getColumnModel().getColumn(i).setMinWidth(40);
                t.getColumnModel().getColumn(i).setPreferredWidth(80);
                t.getColumnModel().getColumn(i).setMaxWidth(160);
            }

            // Strike rate column slightly wider
            t.getColumnModel().getColumn(5).setMinWidth(55);
            t.getColumnModel().getColumn(5).setPreferredWidth(100);
            t.getColumnModel().getColumn(5).setMaxWidth(200);
        
            // Set table's preferredScrollableViewportSize to CARD_WIDTH so it fills the viewport
            t.setPreferredScrollableViewportSize(new Dimension(CARD_WIDTH, 300));
        } else {
            // fallback: just set two-line renderer
            t.getColumnModel().getColumn(0).setCellRenderer(new TwoLineRenderer(true));
        }

        return t;
    }

    // -------------------------
    // Bowling table - shorter rows
    // -------------------------
    private JTable themedTableBowling(String[] cols) {
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable t = new JTable(model) {
            @Override
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component comp = super.prepareRenderer(renderer, row, column);

                String bowlerName = Objects.toString(getModel().getValueAt(row, 0), "").trim();

                if (bestBowlers.contains(bowlerName)) {
                    comp.setBackground(new Color(120, 60, 60)); // highlight
                    comp.setForeground(Color.WHITE);
                } else {
                    comp.setBackground(new Color(39, 52, 72));  // SAME as batting table
                    comp.setForeground(Color.WHITE);
                }

                return comp;
            }
        };

        // ===== APPLY SAME COLORS AS BATTING TABLE =====
        t.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        t.setForeground(Color.WHITE);
        t.setBackground(new Color(39, 52, 72));
        t.setRowHeight(40);
        t.setIntercellSpacing(new Dimension(0, 4));
        t.setShowGrid(false);
        t.setFillsViewportHeight(true);

        // ===== FIX HEADER COLORS ON MAC =====
        JTableHeader hd = t.getTableHeader();
        hd.setOpaque(true);
        hd.setBackground(new Color(220, 220, 230)); // light grey
        hd.setForeground(Color.BLACK);
        hd.setFont(new Font("Segoe UI", Font.BOLD, 13));

        DefaultTableCellRenderer left = new DefaultTableCellRenderer();
        left.setHorizontalAlignment(SwingConstants.LEFT);
        left.setForeground(Color.WHITE);
        left.setBackground(new Color(39, 52, 72));

        t.getColumnModel().getColumn(0).setCellRenderer(left);

        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        right.setForeground(Color.WHITE);
        right.setBackground(new Color(39, 52, 72));

        for (int i = 1; i < cols.length; i++)
            t.getColumnModel().getColumn(i).setCellRenderer(right);

        return t;
    }
    // ============================
    //        DATA FILLERS
    // ============================
    private void fillBattingTable(JTable table, int inningsNo) {
        DefaultTableModel m = (DefaultTableModel) table.getModel();
        m.setRowCount(0);

        String sql = """
            SELECT b.player, b.runs, b.balls, b.fours, b.sixes, b.out_desc
            FROM batting_scorecard b
            WHERE b.innings_no = ?
              AND b.player IN (
                    SELECT DISTINCT batsman
                    FROM ball_by_ball
                    WHERE match_id = ? AND innings_number = ?
              )
            ORDER BY b.player ASC
        """;

        try (Connection c = DB.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, inningsNo);
            ps.setInt(2, MatchContext.sessionId);
            ps.setInt(3, inningsNo);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String name = rs.getString(1);
                int r = rs.getInt(2), b = rs.getInt(3), f = rs.getInt(4), s = rs.getInt(5);
                String out = rs.getString(6);

                String formattedOut = formatOutDescription(name, out, inningsNo);
                String subtitle = (formattedOut == null || formattedOut.isBlank()) ? "not out" : formattedOut;
                String sr = b == 0 ? "0.00" : String.format("%.2f", r * 100.0 / b);

                m.addRow(new Object[]{name + "\n" + subtitle, r, b, f, s, sr});
            }
        } catch (SQLException e) {
            toast("Load batting failed: " + e.getMessage());
        }
    }

    /**
     * Format out description to style similar to cricbuzz:
     * - Use batting_scorecard.out_desc if present
     * - If it mentions "Caught" or "caught by X" produce: "c X b Bowler"
     * - If "lbw" produce: "lbw b Bowler"
     * - If "Bowled" produce: "b Bowler"
     * - For run outs keep detailed "Run out (A assisted by B)" if present
     * - If we cannot parse, return out_desc as-is.
     */
    private String formatOutDescription(String batsmanName, String outDesc, int inningsNo) {
        if (outDesc == null || outDesc.isBlank()) return "";

        String desc = outDesc.trim();
        String lower = desc.toLowerCase();

        if (lower.contains("caught") || lower.contains("caught by") || lower.startsWith("catch")) {
            String fielder = extractFielderFromOut(desc);
            String bowler = findBowlerForDismissal(batsmanName, inningsNo);
            if (fielder == null) fielder = "?";
            if (bowler == null) bowler = "?";
            return "c " + fielder + " b " + bowler;
        }

        if (lower.contains("run out")) {
            return desc;
        }

        if (lower.contains("lbw")) {
            String bowler = findBowlerForDismissal(batsmanName, inningsNo);
            return "lbw b " + (bowler == null ? "?" : bowler);
        }

        if (lower.contains("bowled")) {
            String bowler = findBowlerForDismissal(batsmanName, inningsNo);
            return "b " + (bowler == null ? "?" : bowler);
        }

        if (lower.contains("stump") || lower.startsWith("st")) {
            String bowler = findBowlerForDismissal(batsmanName, inningsNo);
            return "st b " + (bowler == null ? "?" : bowler);
        }

        return desc;
    }

    private String extractFielderFromOut(String outDesc) {
        String lower = outDesc.toLowerCase();
        int idx = lower.indexOf("caught by");
        if (idx >= 0) {
            String after = outDesc.substring(idx + "caught by".length()).trim();
            after = after.replaceAll("[()]", "").trim();
            return after.split("\\)")[0].trim();
        }
        int open = outDesc.indexOf('('), close = outDesc.indexOf(')');
        if (open >= 0 && close > open) {
            String inside = outDesc.substring(open + 1, close).trim();
            if (inside.toLowerCase().contains("caught by")) {
                return inside.replaceAll("(?i)caught by", "").trim();
            }
            return inside;
        }
        return null;
    }

    // Find the bowler for the dismissal of the given batsman (last wicket event for that batsman in this innings)
    private String findBowlerForDismissal(String batsmanName, int inningsNo) {
        String sql = """
            SELECT bowler
            FROM ball_by_ball
            WHERE match_id = ? AND innings_number = ? AND batsman = ? AND wicket IS NOT NULL
            ORDER BY id DESC
            LIMIT 1
        """;
        try (Connection c = DB.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, MatchContext.sessionId);
            ps.setInt(2, inningsNo);
            ps.setString(3, batsmanName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1);
        } catch (SQLException ignored) {}
        return null;
    }

    private void fillBowlingTable(JTable table, int inningsNo) {
        DefaultTableModel m = (DefaultTableModel) table.getModel();
        m.setRowCount(0);

        String sql = """
            SELECT bw.bowler, bw.balls, bw.maidens, bw.runs, bw.wickets
            FROM bowling_scorecard bw
            WHERE bw.innings_no = ?
              AND bw.bowler IN (
                    SELECT DISTINCT bowler
                    FROM ball_by_ball
                    WHERE match_id = ? AND innings_number = ?
              )
            ORDER BY bw.wickets DESC, bw.runs ASC, bw.balls ASC, bw.bowler ASC
        """;

        try (Connection c = DB.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, inningsNo);
            ps.setInt(2, MatchContext.sessionId);
            ps.setInt(3, inningsNo);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String bowler = rs.getString(1);
                int balls = rs.getInt(2);
                int maid = rs.getInt(3);
                int runs = rs.getInt(4);
                int wkts = rs.getInt(5);

                String overs = toOvers(balls);
                String econ = balls == 0 ? "0.00" : String.format("%.2f", runs * 6.0 / balls);

                m.addRow(new Object[]{bowler, overs, maid, runs, wkts, econ});
            }
        } catch (SQLException e) {
            toast("Load bowling failed: " + e.getMessage());
        }
    }

    private Map<String, Integer> getExtras(int inningsNo) {
        Map<String, Integer> map = new LinkedHashMap<>();
        String sql = """
            SELECT extra_type, COALESCE(SUM(runs),0)
            FROM ball_by_ball
            WHERE match_id=? AND innings_number=? AND extra_type IS NOT NULL
            GROUP BY extra_type
        """;
        try (Connection c = DB.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, MatchContext.sessionId);
            ps.setInt(2, inningsNo);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                map.put(rs.getString(1), rs.getInt(2));
            }
        } catch (SQLException ignored) {}
        return map;
    }

    private String extrasLine(Map<String, Integer> extras, int total) {
        if (extras.isEmpty()) return "0";
        List<String> parts = new ArrayList<>();
        extras.forEach((k, v) -> parts.add(shortKey(k) + " " + v));
        return total + "   (" + String.join(", ", parts) + ")";
    }

    private String shortKey(String k) {
        if (k == null) return "";
        String s = k.trim().toLowerCase();
        return switch (s) {
            case "wide" -> "W";
            case "no ball", "noball" -> "NB";
            case "bye" -> "B";
            case "leg bye", "leg_bye", "legbye" -> "LB";
            default -> k;
        };
    }

    private int getTotalRuns(int inningsNo) {
        String sql = """
            SELECT COALESCE(SUM(runs),0)
            FROM ball_by_ball
            WHERE match_id=? AND innings_number=?
        """;
        try (Connection c = DB.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, MatchContext.sessionId);
            ps.setInt(2, inningsNo);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            toast("Total runs failed: " + e.getMessage());
        }
        return 0;
    }

    private int getWickets(int inningsNo) {
        String sql = """
            SELECT COUNT(*)
            FROM ball_by_ball
            WHERE match_id=? AND innings_number=? AND wicket IS NOT NULL
        """;
        try (Connection c = DB.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, MatchContext.sessionId);
            ps.setInt(2, inningsNo);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            toast("Wickets failed: " + e.getMessage());
        }
        return 0;
    }

    private String legalOversText(int inningsNo) {
        String sql = """
            SELECT COUNT(*)
            FROM ball_by_ball
            WHERE match_id=? AND innings_number=? AND extra_type IS NULL
        """;
        int balls = 0;
        try (Connection c = DB.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, MatchContext.sessionId);
            ps.setInt(2, inningsNo);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) balls = rs.getInt(1);
        } catch (SQLException e) {
            toast("Overs failed: " + e.getMessage());
        }
        return toOvers(balls);
    }

    private String buildFOW(int inningsNo) {
        String sql = """
            SELECT over_number, ball_number, runs, extra_type, wicket
            FROM ball_by_ball
            WHERE match_id=? AND innings_number=?
            ORDER BY id ASC
        """;
        int score = 0, wkts = 0;
        List<String> items = new ArrayList<>();

        try (Connection c = DB.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, MatchContext.sessionId);
            ps.setInt(2, inningsNo);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int over = rs.getInt(1);
                int ball = rs.getInt(2);
                int r = rs.getInt(3);
                String w = rs.getString(5);

                score += r;
                if (w != null && !w.isEmpty()) {
                    wkts++;
                    items.add(score + "/" + wkts + " (" + over + "." + ball + " ov)");
                }
            }
        } catch (SQLException e) {
            toast("FOW failed: " + e.getMessage());
        }
        if (items.isEmpty()) return "—";
        return String.join(" · ", items);
    }

    private String toOvers(int balls) {
        return (balls / 6) + "." + (balls % 6);
    }

    private void toast(String s) {
        JOptionPane.showMessageDialog(this, s);
    }

    // ======= Renderer with two-line batter + highlight support =======
    private class TwoLineRenderer extends DefaultTableCellRenderer {
        private final boolean considerBestBatter;

        TwoLineRenderer(boolean considerBestBatter) {
            this.considerBestBatter = considerBestBatter;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {

            String txt = Objects.toString(value, "");
            String[] parts = txt.split("\\n", 2);

            String name = parts.length > 0 ? parts[0] : "";
            String sub = parts.length > 1 ? parts[1] : "";

            JPanel cell = new JPanel();
            cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
            cell.setOpaque(true);

            // default background
            Color bg = isSelected ? new Color(60, 80, 120) : CARD_SOFT;
            Color fg = TEXT;

            // if this row is the best batter, override colors
            if (considerBestBatter) {
                String nm = name.trim();
                if (bestBatters.contains(nm)) {
                    bg = HIGHLIGHT_BAT;
                    fg = Color.WHITE;
                }
            }

            cell.setBackground(bg);
            cell.setBorder(new EmptyBorder(6, 12, 8, 12)); // top/bottom padding gives clear space

            JLabel l1 = new JLabel(name);
            l1.setForeground(fg);
            l1.setFont(new Font("Segoe UI", Font.PLAIN, 14));

            JLabel l2 = new JLabel(sub);
            l2.setForeground(SUBTLE);
            l2.setFont(new Font("Segoe UI", Font.PLAIN, 12));

            cell.add(l1);
            if (!sub.isEmpty()) {
                cell.add(Box.createVerticalStrut(2));
                cell.add(l2);
            }
            return cell;
        }
    }

    // ==============================
    // Determine best batter & bowler
    // ==============================
    private void computeBestBatterAndBowler() {
        bestBatters.clear();
        bestBowlers.clear();

        // Best batter: highest runs in batting_scorecard for this match
        String batterSql = """
            SELECT player, runs
            FROM batting_scorecard
            WHERE match_id = ?
            ORDER BY runs DESC LIMIT 1
        """;
        try (Connection c = DB.get(); PreparedStatement ps = c.prepareStatement(batterSql)) {
            ps.setInt(1, MatchContext.sessionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String p = rs.getString(1);
                bestBatters.add(p);
            }
        } catch (SQLException ignored) {
        }

        // Best bowler: most wickets in bowling_scorecard for this match
        String bowlSql = """
            SELECT bowler, wickets
            FROM bowling_scorecard
            WHERE match_id = ?
            ORDER BY wickets DESC, runs ASC LIMIT 1
        """;
        try (Connection c = DB.get(); PreparedStatement ps = c.prepareStatement(bowlSql)) {
            ps.setInt(1, MatchContext.sessionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String p = rs.getString(1);
                bestBowlers.add(p);
            }
        } catch (SQLException ignored) {
        }
    }

    // ==============================
    // Utility: get team names safely
    // ==============================
    private String getTeamName(int inningsNo) {
        String sql = "SELECT team1, team2 FROM toss_details ORDER BY id DESC LIMIT 1";
        try (Connection c = DB.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String t1 = rs.getString("team1");
                String t2 = rs.getString("team2");
                if (t1 == null || t1.isBlank()) t1 = "Team 1";
                if (t2 == null || t2.isBlank()) t2 = "Team 2";
                return inningsNo == 1 ? t1 : t2;
            }
        } catch (SQLException ignored) {
        }
        // safe default
        return inningsNo == 1 ? "1st Innings" : "2nd Innings";
    }

    // main
    public static void main(String[] args) {
    	System.setProperty("apple.laf.useScreenMenuBar", "true");
    	System.setProperty("swing.defaultlaf", "javax.swing.plaf.nimbus.NimbusLookAndFeel");
    	try {
    	    UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
    	} catch (Exception e) {
    	    e.printStackTrace();
    	}
        SwingUtilities.invokeLater(ScorecardUI::new);
    }
}