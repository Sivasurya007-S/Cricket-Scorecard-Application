package cric;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;

public class LoginScreen extends JFrame {

    private JTextField emailField;
    private JPasswordField passwordField;
    private Connection connection;

    public LoginScreen() {

        // Init DB
        DB.init(); // ensure tables exist
        createUsersTable(); // create users table if missing

        setTitle("🏏 Cricket App Login");
        setSize(550, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // --------- THEME COLORS (from old page) ----------
        Color gradientTop    = new Color(12, 24, 60);
        Color gradientBottom = new Color(26, 64, 160);
        Color glassBg        = new Color(255, 255, 255,26);
        Color glassStroke    = new Color(255, 255, 255, 80);
        Color accent         = new Color(52, 92, 255);
        Color hover          = new Color(85, 133, 255);
        Color textMain       = Color.WHITE;
        Color subText        = new Color(185, 205 , 225);

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

                g2.setColor(glassBg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 26, 26);
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(glassStroke);
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 22, 22);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(420, 620));
        card.setBorder(new EmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        root.add(card, gbc);

        // ----------- HEADER -----------
        JLabel title = new JLabel("🏏 Cricket Score App", SwingConstants.CENTER);
        title.setBounds(30, 30, 360, 50);
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(textMain);
        card.add(title);

        JLabel subtitle = new JLabel("User Login", SwingConstants.CENTER);
        subtitle.setBounds(30, 75, 360, 25);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 17));
        subtitle.setForeground(subText);
        card.add(subtitle);

        // ----------- EMAIL ----------
        JLabel emailLabel = new JLabel("Email");
        emailLabel.setBounds(40, 130, 300, 22);
        emailLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        emailLabel.setForeground(subText);
        card.add(emailLabel);

        emailField = new JTextField();
        emailField.setBounds(40, 155, 340, 46);
        emailField.setFont(new Font("Segoe UI", Font.PLAIN, 19));
        emailField.setBackground(new Color(255, 255, 255, 210));
        emailField.setForeground(Color.BLACK);
        emailField.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        card.add(emailField);

        // ----------- PASSWORD ----------
        JLabel passLabel = new JLabel("Password");
        passLabel.setBounds(40, 225, 300, 22);
        passLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        passLabel.setForeground(subText);
        card.add(passLabel);

        passwordField = new JPasswordField();
        passwordField.setBounds(40, 250, 340, 46);
        passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 19));
        passwordField.setBackground(new Color(255, 255, 255, 210));
        passwordField.setForeground(Color.BLACK);
        passwordField.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        card.add(passwordField);

        // ------------ BUTTONS ----------
        JButton loginBtn = button("Login", accent, hover);
        loginBtn.setBounds(40, 330, 340, 50);
        card.add(loginBtn);

        JButton guestBtn = button("Guest Login", hover, accent);
        guestBtn.setBounds(40, 395, 340, 50);
        card.add(guestBtn);

        JButton registerBtn = button("Create Account", new Color(34,139,34), new Color(45,180,45));
        registerBtn.setBounds(40, 460, 340, 50);
        card.add(registerBtn);

        connectDatabase();

        // Actions
        loginBtn.addActionListener(e -> handleLogin());
        guestBtn.addActionListener(e -> openMainApp("Guest"));
        registerBtn.addActionListener(e -> showRegisterDialog());

        setVisible(true);
    }

    // ---------- BUTTON STYLE ----------
    private JButton button(String text, Color base, Color hover) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setBackground(base);
        b.setForeground(Color.WHITE);
        b.setFont(new Font("Segoe UI", Font.BOLD, 18));
        b.setBorder(BorderFactory.createLineBorder(new Color(255,255,255,120), 2, true));

        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(hover); }
            public void mouseExited(MouseEvent e) { b.setBackground(base); }
        });

        return b;
    }

    private void createUsersTable() {
        try (Connection conn = DB.get();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "email TEXT PRIMARY KEY, password TEXT)");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void connectDatabase() {
        try {
            connection = DB.get();
            System.out.println("✅ DB Connected");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "DB Error: " + e.getMessage());
        }
    }

    private void handleLogin() {
        String email = emailField.getText().trim();
        String pass  = new String(passwordField.getPassword());

        if (email.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter Email & Password");
            return;
        }

        try {
            String sql = "SELECT * FROM users WHERE email=? AND password=?";
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setString(1, email);
            ps.setString(2, pass);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) openMainApp(email);
            else JOptionPane.showMessageDialog(this, "Invalid login");

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "DB Error: " + e.getMessage());
        }
    }

    private void showRegisterDialog() {
        JDialog dialog = new JDialog(this, "Create Account", true);
        dialog.setSize(480, 450);
        dialog.setLocationRelativeTo(this);

        // ---------- PANEL WITH OLD COLORS ----------
        Color gradientTop    = new Color(12, 24, 60);
        Color gradientBottom = new Color(26, 64, 160);
        Color glassBg        = new Color(255, 255, 255,26);
        Color glassStroke    = new Color(255, 255, 255, 80);
        Color subText        = new Color(185, 205 , 225);
        Color accent         = new Color(52, 92, 255);
        Color hover          = new Color(85, 133, 255);

        JPanel root = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, gradientTop, 0, getHeight(), gradientBottom));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        root.setLayout(new GridBagLayout());
        dialog.add(root);

        JPanel card = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(glassBg);
                g2.fillRoundRect(0,0,getWidth(),getHeight(),26,26);
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(glassStroke);
                g2.drawRoundRect(1,1,getWidth()-2,getHeight()-2,22,22);
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(400,350));
        root.add(card, new GridBagConstraints());

        // HEADER
        JLabel title = new JLabel("🆕 Create Account", SwingConstants.CENTER);
        title.setBounds(20, 20, 360, 40);
        title.setFont(new Font("Segoe UI", Font.BOLD, 25));
        title.setForeground(Color.WHITE);
        card.add(title);

        JLabel subtitle = new JLabel("Register to continue", SwingConstants.CENTER);
        subtitle.setBounds(20, 55, 360, 25);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        subtitle.setForeground(subText);
        card.add(subtitle);

        JLabel emailLabel = new JLabel("Email");
        emailLabel.setBounds(40, 105, 300, 20);
        emailLabel.setForeground(subText);
        card.add(emailLabel);

        JTextField email = new JTextField();
        email.setBounds(40, 130, 320, 45);
        email.setBackground(new Color(255, 255, 255, 220));
        email.setForeground(Color.BLACK);
        card.add(email);

        JLabel passLabel = new JLabel("Password");
        passLabel.setBounds(40, 185, 300, 20);
        passLabel.setForeground(subText);
        card.add(passLabel);

        JPasswordField pass = new JPasswordField();
        pass.setBounds(40, 210, 320, 45);
        pass.setBackground(new Color(255,255,255,220));
        pass.setForeground(Color.BLACK);
        card.add(pass);

        JButton createBtn = new JButton("Create Account");
        createBtn.setBounds(40, 275, 320, 50);
        createBtn.setBackground(accent);
        createBtn.setForeground(Color.WHITE);
        card.add(createBtn);

        createBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e){ createBtn.setBackground(hover); }
            public void mouseExited(MouseEvent e){ createBtn.setBackground(accent); }
        });

        createBtn.addActionListener(e -> {
            String em = email.getText().trim();
            String pw = new String(pass.getPassword()).trim();

            if(em.isEmpty() || pw.isEmpty()){
                JOptionPane.showMessageDialog(dialog, "Fields cannot be empty!");
                return;
            }

            try {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO users(email,password) VALUES(?,?)");
                ps.setString(1, em);
                ps.setString(2, pw);
                ps.executeUpdate();
                JOptionPane.showMessageDialog(dialog, "✅ Account Created Successfully!");
                dialog.dispose();
            } catch (SQLException ex){
                JOptionPane.showMessageDialog(dialog, "⚠️ Email already registered!");
            }
        });

        dialog.setVisible(true);
    }

    private void openMainApp(String username) {
        dispose();
        new TossUI(username).setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LoginScreen::new);
    }
}
