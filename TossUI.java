package cric;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.sql.*;

public class TossUI extends JFrame {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/cricket_app?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "dinesh007";

    public TossUI() {

        setTitle("🏏 Toss Settings");
        setSize(550, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // ---------- PREMIUM BLUE ROYALE THEME ----------
        Color gradTop = new Color(12, 24, 60);
        Color gradBottom = new Color(26, 64, 160);

        Color glassBg = new Color(255, 255, 255, 26);
        Color glassBorder = new Color(255, 255, 255, 85);

        Color accent = new Color(52, 92, 255);
        Color hover = new Color(85, 133, 255);
        Color white = Color.WHITE;

        Color labelColor = new Color(185, 205, 255);

        JPanel root = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, gradTop, 0, getHeight(), gradBottom));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        root.setLayout(new GridBagLayout());
        add(root);

        // GLASS CARD
        JPanel card = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(glassBg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
                g2.setColor(glassBorder);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 25, 25);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(420, 650));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        root.add(card, gbc);

        // HEADER
        JLabel header = new JLabel("🏏 Toss Settings", SwingConstants.CENTER);
        header.setBounds(30, 30, 360, 50);
        header.setFont(new Font("Segoe UI", Font.BOLD, 27));
        header.setForeground(white);
        card.add(header);

        JLabel sub = new JLabel("Configure match toss details", SwingConstants.CENTER);
        sub.setBounds(30, 70, 360, 20);
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        sub.setForeground(labelColor);
        card.add(sub);

        JLabel team1Label = sectionLabel("Team 1 Name:", labelColor);
        team1Label.setBounds(50, 120, 200, 30);
        card.add(team1Label);

        JTextField team1Field = inputField("Team 1");
        team1Field.setBounds(50, 150, 320, 46);
        card.add(team1Field);

        JLabel team2Label = sectionLabel("Team 2 Name:", labelColor);
        team2Label.setBounds(50, 210, 200, 30);
        card.add(team2Label);

        JTextField team2Field = inputField("Team 2");
        team2Field.setBounds(50, 240, 320, 46);
        card.add(team2Field);

        JLabel tossLabel = sectionLabel("Toss won by:", labelColor);
        tossLabel.setBounds(50, 300, 200, 30);
        card.add(tossLabel);

        JRadioButton r1 = radio("Team 1");
        JRadioButton r2 = radio("Team 2");
        r1.setSelected(true);

        ButtonGroup g1 = new ButtonGroup();
        g1.add(r1); g1.add(r2);

        JPanel tossPanel = radioRow(r1, r2);
        tossPanel.setBounds(50, 335, 320, 40);
        card.add(tossPanel);

        // AUTO UPDATE TEAM NAMES
        DocumentListener updater = new DocumentListener() {
            public void changedUpdate(DocumentEvent e) { update(); }
            public void removeUpdate(DocumentEvent e) { update(); }
            public void insertUpdate(DocumentEvent e) { update(); }
            private void update() {
                r1.setText(team1Field.getText().isBlank() ? "Team 1" : team1Field.getText());
                r2.setText(team2Field.getText().isBlank() ? "Team 2" : team2Field.getText());
            }
        };
        team1Field.getDocument().addDocumentListener(updater);
        team2Field.getDocument().addDocumentListener(updater);

        JLabel optLabel = sectionLabel("Opted to:", labelColor);
        optLabel.setBounds(50, 390, 200, 30);
        card.add(optLabel);

        JRadioButton bat = radio("Bat");
        JRadioButton bowl = radio("Bowl");
        bat.setSelected(true);

        ButtonGroup g2 = new ButtonGroup();
        g2.add(bat); g2.add(bowl);

        JPanel optPanel = radioRow(bat, bowl);
        optPanel.setBounds(50, 425, 320, 40);
        card.add(optPanel);

        JLabel oversLabel = sectionLabel("Overs:", labelColor);
        oversLabel.setBounds(50, 480, 200, 30);
        card.add(oversLabel);

        JTextField oversField = inputField("Enter number of overs");
        oversField.setBounds(50, 510, 320, 46);
        card.add(oversField);

        JButton startBtn = mainButton("Start Match", accent, hover);
        startBtn.setBounds(50, 580, 320, 55);
        card.add(startBtn);

        // ✅ START MATCH ACTION (sessionId added)
        startBtn.addActionListener(e -> {
            String t1 = team1Field.getText().trim();
            String t2 = team2Field.getText().trim();
            String tossWinner = r1.isSelected() ? t1 : t2;
            String opted = bat.isSelected() ? "Bat" : "Bowl";
            String overs = oversField.getText().trim();

            if (t1.isBlank() || t2.isBlank() || overs.isBlank()) return;

            // ✅ NEW: Create match session ID
            MatchContext.sessionId = getNewSessionId();

            // ✅ Save toss (same as before)
            save(t1, t2, tossWinner, opted, overs);

            dispose();
            new OpeningPlayersUI().setVisible(true);
        });

        setVisible(true);
    }

    // ✅ NEW — Generate a new session ID based on ball_by_ball table
    private int getNewSessionId() {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(match_id),0) + 1 FROM ball_by_ball")) {

            if (rs.next()) return rs.getInt(1);

        } catch (Exception ignored) {}

        return (int) (System.currentTimeMillis() / 1000L);
    }

    private JLabel sectionLabel(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 17));
        l.setForeground(color);
        return l;
    }

    private JTextField inputField(String placeholder) {
        JTextField f = new JTextField(placeholder);
        f.setFont(new Font("Segoe UI", Font.PLAIN, 19));
        f.setForeground(Color.GRAY);
        f.setBackground(new Color(255,255,255,210));
        f.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        f.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent e) {
                if (f.getText().equals(placeholder)) {
                    f.setText("");
                    f.setForeground(Color.BLACK);
                }
            }
            public void focusLost(java.awt.event.FocusEvent e) {
                if (f.getText().isBlank()) {
                    f.setText(placeholder);
                    f.setForeground(Color.GRAY);
                }
            }
        });
        return f;
    }

    private JRadioButton radio(String text) {
        JRadioButton r = new JRadioButton(text);
        r.setOpaque(false);
        r.setForeground(Color.WHITE);
        r.setFont(new Font("Segoe UI", Font.PLAIN, 17));
        return r;
    }

    private JPanel radioRow(JRadioButton a, JRadioButton b) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        p.setOpaque(false);
        p.add(a); p.add(b);
        return p;
    }

    private JButton mainButton(String text, Color normal, Color hover) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setBackground(normal);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 18));
        btn.setBorder(BorderFactory.createLineBorder(new Color(255,255,255,120), 2, true));

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { btn.setBackground(hover); }
            public void mouseExited(java.awt.event.MouseEvent e) { btn.setBackground(normal); }
        });
        return btn;
    }

    private void save(String t1, String t2, String toss, String opted, String overs) {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO toss_details(team1,team2,toss_winner,opted,overs) VALUES(?,?,?,?,?)");
            ps.setString(1, t1);
            ps.setString(2, t2);
            ps.setString(3, toss);
            ps.setString(4, opted);
            ps.setString(5, overs);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) {
    	System.setProperty("apple.laf.useScreenMenuBar", "true");
    	System.setProperty("swing.defaultlaf", "javax.swing.plaf.nimbus.NimbusLookAndFeel");
    	try {
    	    UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
    	} catch (Exception e) {
    	    e.printStackTrace();
    	}

        SwingUtilities.invokeLater(TossUI::new);
    }
}