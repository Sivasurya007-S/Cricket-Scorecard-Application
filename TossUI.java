package cric;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.sql.*;

public class TossUI extends JFrame {

    private String username;

    public TossUI(String username) {
        this.username = username;
        initUI();
    }

    public TossUI() {
        this.username = "Guest";
        initUI();
    }

    private void initUI() {

        setTitle("🏏 Toss Settings");
        setSize(550, 850);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

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
        card.setPreferredSize(new Dimension(420, 720));
        root.add(card);

        JLabel header = new JLabel("🏏 Toss Settings", SwingConstants.CENTER);
        header.setBounds(30, 30, 360, 50);
        header.setFont(new Font("Segoe UI", Font.BOLD, 27));
        header.setForeground(white);
        card.add(header);

        JLabel userLabel = new JLabel("Welcome, " + username + "!", SwingConstants.CENTER);
        userLabel.setBounds(30, 80, 360, 25);
        userLabel.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        userLabel.setForeground(labelColor);
        card.add(userLabel);

        JLabel sub = new JLabel("Configure match toss details", SwingConstants.CENTER);
        sub.setBounds(30, 110, 360, 20);
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        sub.setForeground(labelColor);
        card.add(sub);

        JLabel team1Label = sectionLabel("Team 1 Name:", labelColor);
        team1Label.setBounds(50, 150, 200, 30);
        card.add(team1Label);

        JTextField team1Field = inputField("Team 1");
        team1Field.setBounds(50, 180, 320, 46);
        card.add(team1Field);

        JLabel team2Label = sectionLabel("Team 2 Name:", labelColor);
        team2Label.setBounds(50, 240, 200, 30);
        card.add(team2Label);

        JTextField team2Field = inputField("Team 2");
        team2Field.setBounds(50, 270, 320, 46);
        card.add(team2Field);

        // 🔥 REAL TOSS SYSTEM

        JLabel tossLabel = sectionLabel("Choose Heads or Tails:", labelColor);
        tossLabel.setBounds(50, 330, 250, 30);
        card.add(tossLabel);

        JRadioButton heads = radio("Heads");
        JRadioButton tails = radio("Tails");
        heads.setSelected(true);

        ButtonGroup tossChoiceGroup = new ButtonGroup();
        tossChoiceGroup.add(heads);
        tossChoiceGroup.add(tails);

        JPanel tossChoicePanel = radioRow(heads, tails);
        tossChoicePanel.setBounds(50, 365, 320, 40);
        card.add(tossChoicePanel);

        JLabel tossResultLabel = new JLabel("Click Toss to play!", SwingConstants.CENTER);
        tossResultLabel.setBounds(50, 405, 320, 30);
        tossResultLabel.setForeground(Color.WHITE);
        tossResultLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        card.add(tossResultLabel);

        JButton tossBtn = mainButton("Flip Coin 🪙", accent, hover);
        tossBtn.setBounds(50, 440, 320, 45);
        card.add(tossBtn);

        final String[] tossWinnerHolder = new String[1];

        tossBtn.addActionListener(e -> {
            String t1 = team1Field.getText().trim();
            String t2 = team2Field.getText().trim();

            if (t1.isBlank() || t2.isBlank()) {
                tossResultLabel.setText("Enter team names first!");
                return;
            }

            String result = Math.random() < 0.5 ? "Heads" : "Tails";
            String userChoice = heads.isSelected() ? "Heads" : "Tails";

            if (result.equals(userChoice)) {
                tossWinnerHolder[0] = t1;
            } else {
                tossWinnerHolder[0] = t2;
            }

            tossResultLabel.setText("Result: " + result + " → " + tossWinnerHolder[0] + " won!");
        });

        JLabel optLabel = sectionLabel("Opted to:", labelColor);
        optLabel.setBounds(50, 500, 200, 30);
        card.add(optLabel);

        JRadioButton bat = radio("Bat");
        JRadioButton bowl = radio("Bowl");
        bat.setSelected(true);

        ButtonGroup g2 = new ButtonGroup();
        g2.add(bat); g2.add(bowl);

        JPanel optPanel = radioRow(bat, bowl);
        optPanel.setBounds(50, 535, 320, 40);
        card.add(optPanel);

        JLabel oversLabel = sectionLabel("Overs:", labelColor);
        oversLabel.setBounds(50, 580, 200, 30);
        card.add(oversLabel);

        JTextField oversField = inputField("Enter number of overs");
        oversField.setBounds(50, 610, 320, 46);
        card.add(oversField);

        JButton startBtn = mainButton("Start Match", accent, hover);
        startBtn.setBounds(50, 670, 320, 55);
        card.add(startBtn);

        startBtn.addActionListener(e -> {
            String t1 = team1Field.getText().trim();
            String t2 = team2Field.getText().trim();
            String tossWinner = tossWinnerHolder[0];
            String opted = bat.isSelected() ? "Bat" : "Bowl";
            String overs = oversField.getText().trim();

            if (t1.isBlank() || t2.isBlank() || overs.isBlank() || tossWinner == null) return;

            MatchContext.sessionId = getNewSessionId();
            save(t1, t2, tossWinner, opted, overs);

            dispose();
            new OpeningPlayersUI().setVisible(true);
        });

        setVisible(true);
    }

    private int getNewSessionId() {
        try (Connection c = DB.get();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(match_id),0) + 1 FROM ball_by_ball")) {
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return (int) (System.currentTimeMillis() / 1000L);
    }

    private void save(String t1, String t2, String toss, String opted, String overs) {
        try (Connection c = DB.get()) {
            PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO toss_details(team1,team2,toss_winner,opted,overs) VALUES(?,?,?,?,?)");
            ps.setString(1, t1);
            ps.setString(2, t2);
            ps.setString(3, toss);
            ps.setString(4, opted);
            ps.setString(5, overs);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
}
