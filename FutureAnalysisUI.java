package cric;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class FutureAnalysisUI extends JFrame {

    public FutureAnalysisUI() {
        setTitle("📊 Future Analysis");
        setSize(800, 600);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        // --------- THEME COLORS ----------
        Color gradientTop = new Color(12, 24, 60);
        Color gradientBottom = new Color(26, 64, 160);
        Color glassBg = new Color(255, 255, 255, 30);
        Color textMain = Color.WHITE;

        // Gradient Background
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, gradientTop, 0, getHeight(), gradientBottom));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        add(root);

        // Header Title
        JLabel titleLabel = new JLabel("Future Dominance Analysis", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(textMain);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        root.add(titleLabel, BorderLayout.NORTH);

        // Table Panel
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setOpaque(false);
        tablePanel.setBorder(BorderFactory.createEmptyBorder(10, 40, 10, 40));

        String[] columns = { "Team Name", "Matches Played", "Matches Won", "Matches Lost", "Win (%)" };
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.BOLD, 15));
        table.setRowHeight(35);
        table.setForeground(new Color(15, 30, 80));
        table.setBackground(new Color(245, 250, 255));
        table.setSelectionBackground(new Color(173, 216, 230));
        table.setShowGrid(true);
        table.setGridColor(new Color(200, 200, 200));

        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 16));
        header.setBackground(new Color(20, 45, 120));
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(header.getWidth(), 40));

        // Center align table contents
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(gradientBottom);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 100), 2));
        tablePanel.add(scrollPane, BorderLayout.CENTER);
        root.add(tablePanel, BorderLayout.CENTER);

        // Fetch Data and compute dominant team
        List<Map<String, Object>> stats = DB.getTeamStats();
        String dominantTeam = "N/A";
        double maxWinPercentage = -1.0;

        for (Map<String, Object> stat : stats) {
            String team = (String) stat.get("team_name");
            int played = (int) stat.get("matches_played");
            int won = (int) stat.get("matches_won");
            int lost = (int) stat.get("matches_lost");
            double winPercent = played > 0 ? ((double) won / played) * 100 : 0.0;

            if (winPercent > maxWinPercentage) {
                maxWinPercentage = winPercent;
                dominantTeam = team;
            }

            model.addRow(new Object[] { team, played, won, lost, String.format("%.2f %%", winPercent) });
        }

        // Prediction Panel
        JPanel predictionPanel = new JPanel();
        predictionPanel.setOpaque(false);
        predictionPanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 30, 0));

        JLabel predictionLabel = new JLabel("🏆 Predicted Future Dominant Team: " + dominantTeam + " 🏆");
        predictionLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        predictionLabel.setForeground(new Color(255, 223, 0)); // Bright Gold Color
        predictionLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Add a soft glass effect behind the label
        JPanel glassBox = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(glassBg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(new Color(255, 255, 255, 80));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 20, 20);
                g2.dispose();
            }
        };
        glassBox.setOpaque(false);
        glassBox.setBorder(BorderFactory.createEmptyBorder(15, 30, 15, 30));
        glassBox.add(predictionLabel, BorderLayout.CENTER);

        predictionPanel.add(glassBox);
        root.add(predictionPanel, BorderLayout.SOUTH);
    }
}
