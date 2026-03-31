package cric;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.*;

/**
 * OpeningPlayersUI - Blue Royale (royal-blue gradient + glass card)
 * - Header auto: "🏏 {Team} – 1st/2nd Innings"
 * - Reads latest toss_details (team1, team2, toss_winner, opted, overs)
 * - Determines batting team for both innings
 * - Size matches Login/Toss UI: 550 x 800
 */
public class OpeningPlayersUI extends JFrame {

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/cricket_app?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "dinesh007";

    private TossRow toss;           // latest toss row
    private boolean firstInnings;   // true if first innings
    private String battingTeam;     // team displayed in header

    public OpeningPlayersUI() {
        setTitle("🏏 Opening Players");
        setSize(550, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Load toss row (team names + overs + toss result)
        toss = fetchLatestToss();
        firstInnings = (SecondInningsData.target == 0);
        battingTeam = computeBattingTeam(toss, firstInnings);

        // --------- THEME (Blue Royale) ----------
        Color gradientTop    = new Color(12, 24, 60);   // deep navy
        Color gradientBottom = new Color(26, 64, 160);  // royal blue
        Color glassBg        = new Color(255, 255, 255, 28);  // translucent glass
        Color glassStroke    = new Color(255, 255, 255, 70);
        Color accent         = new Color(52, 92, 255);
        Color accentHover    = new Color(85, 133, 255);
        Color textOnDark     = Color.WHITE;
        Color labelAccent    = new Color(173, 197, 255);

        // Gradient root panel
        JPanel root = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setPaint(new GradientPaint(0, 0, gradientTop, 0, getHeight(), gradientBottom));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        root.setLayout(new GridBagLayout());
        add(root);

        // Glass card
        JPanel card = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // glass fill
                g2.setColor(glassBg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                // subtle inner stroke
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(glassStroke);
                g2.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 22, 22);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(420, 650));
        card.setBorder(new EmptyBorder(16,16,16,16));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        root.add(card, gbc);

        // --------- HEADER ---------
        String inningText = firstInnings ? "1st Innings" : "2nd Innings";
        JLabel head = new JLabel("🏏 " + (battingTeam == null ? "Team" : battingTeam) + " – " + inningText, SwingConstants.CENTER);
        head.setBounds(30, 24, 360, 38);
        head.setFont(new Font("Segoe UI", Font.BOLD, 22));
        head.setForeground(textOnDark);
        card.add(head);

        JLabel sub = new JLabel("🎯 Opening Players", SwingConstants.CENTER);
        sub.setBounds(30, 64, 360, 26);
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        sub.setForeground(labelAccent);
        card.add(sub);

        // --------- INPUTS ---------
        JLabel l1 = label("Striker", labelAccent);
        l1.setBounds(40, 120, 340, 22);
        card.add(l1);

        JTextField tfStriker = field();
        tfStriker.setBounds(40, 148, 340, 46);
        card.add(tfStriker);

        JLabel l2 = label("Non-Striker", labelAccent);
        l2.setBounds(40, 214, 340, 22);
        card.add(l2);

        JTextField tfNonStriker = field();
        tfNonStriker.setBounds(40, 242, 340, 46);
        card.add(tfNonStriker);

        JLabel l3 = label("Bowler", labelAccent);
        l3.setBounds(40, 308, 340, 22);
        card.add(l3);

        JTextField tfBowler = field();
        tfBowler.setBounds(40, 336, 340, 46);
        card.add(tfBowler);

        JButton startBtn = button("Start Innings", accent, accentHover);
        startBtn.setBounds(40, 420, 340, 52);
        card.add(startBtn);

        // Action: validate, save opening players, ensure match_session, launch LiveScoreUI
        startBtn.addActionListener(e -> {
            String s  = tfStriker.getText().trim();
            String ns = tfNonStriker.getText().trim();
            String bw = tfBowler.getText().trim();

            if (s.isEmpty() || ns.isEmpty() || bw.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please fill all fields!", "Missing info", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Save players row (optional; you were already doing this)
            savePlayers(s, ns, bw);

            // Ensure we have a match_session (id in MatchContext.sessionId)
            int totalOvers = (firstInnings ? toss.overs : SecondInningsData.totalOvers);
            ensureMatchSession(totalOvers);

            // Figure target + overs for innings
            int targetRuns = firstInnings ? 0 : SecondInningsData.target;
            int ov = firstInnings ? toss.overs : SecondInningsData.totalOvers;

            // Move to LiveScoreUI
            dispose();
            new LiveScoreUI(s, ns, bw, ov, firstInnings, targetRuns).setVisible(true);
        });

        setVisible(true);
    }

    // ---------- Helpers (UI) ----------
    private JLabel label(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 16));
        l.setForeground(color);
        return l;
    }

    private JTextField field() {
        JTextField f = new JTextField();
        f.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        f.setForeground(new Color(15, 23, 42));
        f.setBackground(new Color(255, 255, 255, 210)); // milky glass input
        f.setCaretColor(Color.BLACK);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 200, 255), 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
        return f;
    }

    private JButton button(String text, Color base, Color hover) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setForeground(Color.WHITE);
        b.setBackground(base);
        b.setFont(new Font("Segoe UI", Font.BOLD, 18));
        b.setBorder(BorderFactory.createLineBorder(new Color(255,255,255,120), 2, true));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { b.setBackground(hover); }
            public void mouseExited (java.awt.event.MouseEvent e) { b.setBackground(base ); }
        });
        return b;
    }

    // ---------- Helpers (DB + logic) ----------

    private void ensureMatchSession(int overs) {
        try (Connection c = DB.get()) {
            if (MatchContext.sessionId == 0) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO match_session(overs) VALUES (?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, overs);
                    ps.executeUpdate();
                    ResultSet k = ps.getGeneratedKeys();
                    if (k.next()) MatchContext.sessionId = k.getInt(1);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void savePlayers(String striker, String nonStriker, String bowler) {
        try (Connection conn = DB.get()) {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO opening_players (striker, non_striker, bowler) VALUES (?, ?, ?)");
            ps.setString(1, striker);
            ps.setString(2, nonStriker);
            ps.setString(3, bowler);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static class TossRow {
        String team1, team2, tossWinner, opted; // opted: "Bat" or "Bowl"
        int overs;
    }

    private TossRow fetchLatestToss() {
        TossRow row = new TossRow();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "SELECT team1, team2, toss_winner, opted, overs FROM toss_details ORDER BY id DESC LIMIT 1";
            ResultSet rs = conn.createStatement().executeQuery(sql);
            if (rs.next()) {
                row.team1 = rs.getString("team1");
                row.team2 = rs.getString("team2");
                row.tossWinner = rs.getString("toss_winner");
                row.opted = rs.getString("opted");
                row.overs = rs.getInt("overs");
            } else {
                // safe defaults if row missing
                row.team1 = "Team 1";
                row.team2 = "Team 2";
                row.tossWinner = row.team1;
                row.opted = "Bat";
                row.overs = 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            // fallback defaults
            row.team1 = "Team 1";
            row.team2 = "Team 2";
            row.tossWinner = row.team1;
            row.opted = "Bat";
            row.overs = 0;
        }
        return row;
    }

    /**
     * Compute which team is batting.
     * 1st Innings:
     *   - If toss winner opted "Bat" -> toss winner bats.
     *   - If opted "Bowl" -> the other team bats.
     * 2nd Innings:
     *   - The other team (not batting in 1st innings).
     */
    private String computeBattingTeam(TossRow t, boolean firstInnings) {
        if (t == null) return "Team";
        String firstBat;
        if ("Bat".equalsIgnoreCase(t.opted)) {
            firstBat = t.tossWinner;
        } else {
            // other team bats first
            firstBat = t.tossWinner != null && t.tossWinner.equals(t.team1) ? t.team2 : t.team1;
        }
        if (firstInnings) return firstBat;
        // second innings = other team
        return firstBat != null && firstBat.equals(t.team1) ? t.team2 : t.team1;
    }

    public static void main(String[] args) {
    	System.setProperty("apple.laf.useScreenMenuBar", "true");
    	System.setProperty("swing.defaultlaf", "javax.swing.plaf.nimbus.NimbusLookAndFeel");
    	try {
    	    UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
    	} catch (Exception e) {
    	    e.printStackTrace();
    	}
        SwingUtilities.invokeLater(OpeningPlayersUI::new);
    }
}