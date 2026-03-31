// File: LiveScoreUI.java
package cric;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;

/**
 * LiveScoreUI (Blue Royale theme, NO glass)
 * - WIDE/NOBALL are MODES. Wicket can be toggled as a MODE too.
 * - In Wicket mode: press a run (0–6) -> runs are added AND wicket recorded on the SAME delivery.
 *   Then the popup opens to pick who is out and helper/catcher. One DB row is written for the ball.
 * - Run-out flow lets you choose which batter is out + "helped by" free text.
 */
public class LiveScoreUI extends JFrame {

    // ======= THEME: Blue Royale (no glass) =======
    private static final Color GRAD_TOP = new Color(12, 24, 60);
    private static final Color GRAD_BOTTOM = new Color(26, 64, 160);
    private static final Color TEXT_MAIN = Color.WHITE;
    private static final Color TEXT_SUB = new Color(185, 205, 255);
    private static final Color ACCENT = new Color(52, 92, 255);
    private static final Color ACCENT_HOVER = new Color(85, 133, 255);

    private static final Font TITLE_FONT = new Font("Consolas", Font.BOLD, 36);
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.BOLD, 18);
    private static final Font BODY_FONT = new Font("Segoe UI", Font.PLAIN, 16);
    private static final Font BTN_FONT = new Font("Segoe UI", Font.BOLD, 18);

    private static final Color PANEL_DARK = new Color(15, 25, 45, 180);
    private static final Color GRID = new Color(200, 210, 240, 80);

    // ======= Match state =======
    private static final int MAX_WICKETS = 10;
    private static final int BALLS_PER_OVER = 6;

    private enum Mode { NORMAL, LB, BYE, WIDE, NOBALL }
    private Mode mode = Mode.NORMAL;
    private boolean wicketMode = false; // ✅ NEW: wicket-as-mode toggle

    private final int inningsNo;
    private int totalOvers;
    private int runs = 0;
    private int wickets = 0;
    private int balls = 0;

    private int target = 0;
    private boolean firstInnings = true;

    private String striker, nonStriker, bowler;

    private int strikerRuns = 0, strikerBalls = 0, strikerFours = 0, strikerSixes = 0;
    private int nonStrikerRuns = 0, nonStrikerBalls = 0, nonStrikerFours = 0, nonStrikerSixes = 0;

    private int bowlerBalls = 0, bowlerRuns = 0, bowlerWickets = 0, bowlerMaidens = 0;
    private int runsThisOver = 0;

    private static class BatStat { int r,b,f,s; String out = "not out"; }
    private static class BowlStat { int balls, maidens, runs, wkts; int runsInCurrentOver = 0; }

    private final Map<String,BatStat> batting = new LinkedHashMap<>();
    private final Map<String,BowlStat> bowling = new LinkedHashMap<>();

    // ======= UI =======
    private JTable batsmanTable, bowlerTable;
    private DefaultTableModel batModel, bowlModel;

    private JLabel lblScore, lblCRR, lblRRR, lblChaseLeft, lblStatus;
    private JLabel thisOverLabel;
    private int uiBallsInOver = 0;
    private final Deque<String> thisOverEntries = new ArrayDeque<>();

    private JButton btnLB, btnBYE, btnWide, btnNoBall, btnWicket; // ✅ keep ref to toggle border

    // ======= Undo support =======
    private static class Action {
        String type;          // run, wide, noball, lb, bye, wicket, swap, run+wicket
        int run;              // runs (for wide/noball: this is X; total added is 1+X)
        boolean legalBall;    // counted ball
        boolean swapped;      // swap occurred
        String prevStriker, prevNonStriker, prevBowler;
        int prevRuns, prevWkts, prevBalls;
        int prevStrikerRuns, prevStrikerBalls, prevStrikerFours, prevStrikerSixes;
        int prevNonStrikerRuns, prevNonStrikerBalls, prevNonStrikerFours, prevNonStrikerSixes;
        int prevBowlerBalls, prevBowlerRuns, prevBowlerWkts, prevBowlerMaidens, prevRunsThisOver, prevUiBallsInOver;
        String overIcon;
    }
    private final Deque<Action> history = new ArrayDeque<>();

    public LiveScoreUI(String striker, String nonStriker, String bowler, int totalOvers, boolean firstInnings, int target) {
        this.firstInnings = firstInnings;
        this.inningsNo = firstInnings ? 1 : 2;

        this.striker = striker;
        this.nonStriker = nonStriker;
        this.bowler = bowler;
        this.totalOvers = totalOvers;
        this.target = target;

        // Ensure match session id
        if (this.firstInnings && (MatchContext.sessionId <= 0)) {
            MatchContext.sessionId = DB.createMatchSession(totalOvers);
        }

        batting.putIfAbsent(striker, new BatStat());
        batting.putIfAbsent(nonStriker, new BatStat());
        bowling.putIfAbsent(bowler, new BowlStat());

        setTitle(firstInnings ? "🏏 1st Innings Live Score" : "🏏 2nd Innings Live Score");
        setSize(560, 860);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
        setVisible(true);

        // F9 -> Scorecard
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F9"), "scorecard");
        getRootPane().getActionMap().put("scorecard", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                new ScorecardUI();
            }
        });

        if (!firstInnings) {
            JOptionPane.showMessageDialog(this, "Target: " + target + "  (Overs: " + totalOvers + ")");
        }
    }

    // ========= UI =========
    private void initUI() {
        JPanel root = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setPaint(new GradientPaint(0, 0, GRAD_TOP, 0, getHeight(), GRAD_BOTTOM));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        root.setLayout(new BorderLayout());
        setContentPane(root);

        JPanel mainPanel = new JPanel();
        mainPanel.setOpaque(false);
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        lblScore = new JLabel("0 - 0 (0.0)", SwingConstants.CENTER);
        lblScore.setFont(TITLE_FONT);
        lblScore.setForeground(TEXT_MAIN);
        Dimension scoreBox = new Dimension(500, 48);
        lblScore.setPreferredSize(scoreBox);
        lblScore.setMinimumSize(scoreBox);
        lblScore.setMaximumSize(scoreBox);
        lblScore.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblCRR = label("CRR: 0.00", TEXT_SUB, BODY_FONT);
        lblRRR = label(firstInnings ? "RRR: --" : "RRR: 0.00", new Color(255, 210, 120), BODY_FONT);
        lblChaseLeft = label("", new Color(200, 230, 255), new Font("Consolas", Font.PLAIN, 18));

        JPanel overPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        overPanel.setOpaque(false);
        thisOverLabel = new JLabel("<html><span style='font-size:16px;color:white;'>This over:</span></html>");
        thisOverLabel.setOpaque(false);
        overPanel.add(thisOverLabel);

        JScrollPane batScroll = buildBattingTable();
        JScrollPane bowlScroll = buildBowlingTable();

        mainPanel.add(Box.createVerticalStrut(5));
        mainPanel.add(lblScore);
        mainPanel.add(Box.createVerticalStrut(8));
        mainPanel.add(lblCRR);
        mainPanel.add(lblRRR);
        if (!firstInnings) {
            mainPanel.add(Box.createVerticalStrut(6));
            mainPanel.add(lblChaseLeft);
        }
        mainPanel.add(Box.createVerticalStrut(14));
        mainPanel.add(overPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(batScroll);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(bowlScroll);
        mainPanel.add(Box.createVerticalStrut(18));

        // Changed to 2 rows x 4 cols so we can add a Scorecard button after the '6' button
        JPanel runPanel = new JPanel(new GridLayout(2, 4, 14, 14));
        runPanel.setOpaque(false);
        for (int i = 0; i <= 6; i++) {
            int run = i;
            JButton btn = primaryButton(String.valueOf(i));
            btn.addActionListener(e -> onRunPressed(run));
            runPanel.add(btn);
        }

        // New Scorecard button added immediately after the '6' button
        JButton btnScorecard = neutralButton("Scorecard");
        btnScorecard.setToolTipText("Open full scorecard (F9)");
        btnScorecard.addActionListener(e -> new ScorecardUI());
        runPanel.add(btnScorecard);

        JPanel extrasPanel = new JPanel(new GridLayout(2, 2, 14, 14));
        extrasPanel.setOpaque(false);

        btnWide   = primaryButton("Wide");
        btnNoBall = primaryButton("No Ball");
        btnWicket = dangerButton("Wicket");
        JButton btnEnd    = neutralButton("End Innings");

        btnWide.addActionListener(e -> setMode(Mode.WIDE));
        btnNoBall.addActionListener(e -> setMode(Mode.NOBALL));
        btnWicket.addActionListener(e -> toggleWicketMode());
        btnEnd.addActionListener(e -> forceEndIfAllowed());

        extrasPanel.add(btnWide);
        extrasPanel.add(btnNoBall);
        extrasPanel.add(btnWicket);
        extrasPanel.add(btnEnd);

        JPanel thinRow = new JPanel(new GridLayout(1, 4, 12, 0));
        thinRow.setOpaque(false);

        btnLB = toggleButton("Leg Bye");
        btnLB.addActionListener(e -> setMode(Mode.LB));

        btnBYE = toggleButton("Bye");
        btnBYE.addActionListener(e -> setMode(Mode.BYE));

        JButton btnSwap = neutralButton("Swap Strike");
        btnSwap.addActionListener(e -> manualSwap());

        JButton btnUndo = warnButton("Undo");
        btnUndo.addActionListener(e -> undoLast());

        thinRow.add(btnLB);
        thinRow.add(btnBYE);
        thinRow.add(btnSwap);
        thinRow.add(btnUndo);

        lblStatus = label("", Color.YELLOW, new Font("Segoe UI", Font.BOLD, 16));
        mainPanel.add(runPanel);
        mainPanel.add(Box.createVerticalStrut(18));
        mainPanel.add(extrasPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(thinRow);
        mainPanel.add(Box.createVerticalStrut(16));
        mainPanel.add(lblStatus);

        root.add(mainPanel, BorderLayout.CENTER);
    }

    private JLabel label(String txt, Color col, Font f) {
        JLabel l = new JLabel(txt, SwingConstants.CENTER);
        l.setForeground(col);
        l.setFont(f);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private JScrollPane buildBattingTable() {
        String[] batCols = {"Batsman", "R", "B", "4s", "6s", "SR"};
        Object[][] batData = {
                {striker + " *", 0, 0, 0, 0, "0.00"},
                {nonStriker, 0, 0, 0, 0, "0.00"}
        };
        batModel = new DefaultTableModel(batData, batCols) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        batsmanTable = new JTable(batModel);
        styleTable(batsmanTable);
        JScrollPane sp = new JScrollPane(batsmanTable);
        styleScroll(sp);
        sp.setPreferredSize(new Dimension(500, 110));
        return sp;
    }

    private JScrollPane buildBowlingTable() {
        String[] bowlCols = {"Bowler", "O", "M", "R", "W", "ER"};
        bowlModel = new DefaultTableModel(bowlCols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        bowlModel.addRow(new Object[]{bowler, "0.0", 0, 0, 0, "0.00"});
        bowlerTable = new JTable(bowlModel);
        styleTable(bowlerTable);
        JScrollPane sp = new JScrollPane(bowlerTable);
        styleScroll(sp);
        sp.setPreferredSize(new Dimension(500, 80));
        return sp;
    }

    private void styleTable(JTable t) {
        t.setOpaque(false);
        t.setBackground(PANEL_DARK);
        t.setForeground(TEXT_MAIN);
        t.setFont(BODY_FONT);
        t.setRowHeight(28);
        t.setGridColor(GRID);
        t.setShowGrid(true);
        t.setSelectionBackground(new Color(255,255,255,40));
        t.setSelectionForeground(Color.WHITE);

        ((DefaultTableCellRenderer) t.getDefaultRenderer(Object.class)).setOpaque(false);

        JTableHeader h = t.getTableHeader();
        h.setOpaque(false);
        h.setBackground(new Color(220, 220, 230)); // light grey
        h.setForeground(Color.BLACK);
        h.setFont(new Font("Segoe UI", Font.BOLD, 15));
    }

    private void styleScroll(JScrollPane sp) {
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setBorder(BorderFactory.createLineBorder(new Color(255,255,255,90), 1, true));
    }

    // ======= Buttons =======
    private JButton baseButton(String text, Color base, Color hover) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setBackground(base);
        b.setForeground(Color.WHITE);
        b.setFont(BTN_FONT);
        b.setBorder(BorderFactory.createLineBorder(new Color(255,255,255,120), 2, true));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e){ b.setBackground(hover); }
            public void mouseExited(java.awt.event.MouseEvent e){ b.setBackground(base); }
        });
        return b;
    }
    private JButton primaryButton(String txt) { return baseButton(txt, ACCENT, ACCENT_HOVER); }
    private JButton neutralButton(String txt) { return baseButton(txt, new Color(90, 100, 125), new Color(110, 130, 150)); }
    private JButton dangerButton(String txt)  { return baseButton(txt, new Color(220, 66, 66), new Color(235, 85, 85)); }
    private JButton warnButton(String txt)    { return baseButton(txt, new Color(160, 82, 45), new Color(185, 100, 60)); }

    private JButton toggleButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(new Color(40, 60, 110));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 16));
        b.setBorder(BorderFactory.createLineBorder(new Color(180, 200, 255), 1, true));
        return b;
    }

    private void setMode(Mode m) {
        this.mode = (this.mode == m) ? Mode.NORMAL : m;

        Color on  = Color.GREEN;
        Color off = new Color(180,200,255);

        btnLB.setBorder(BorderFactory.createLineBorder(mode == Mode.LB ? on : off, 2, true));
        btnBYE.setBorder(BorderFactory.createLineBorder(mode == Mode.BYE ? on : off, 2, true));
        btnWide.setBorder(BorderFactory.createLineBorder(mode == Mode.WIDE ? on : new Color(255,255,255,120), 2, true));
        btnNoBall.setBorder(BorderFactory.createLineBorder(mode == Mode.NOBALL ? on : new Color(255,255,255,120), 2, true));

        // Turning on any other mode disables wicket mode
        if (mode != Mode.NORMAL && wicketMode) toggleWicketModeOff();

        lblStatus.setText(
                mode == Mode.LB ? "Leg Bye mode: tap runs" :
                mode == Mode.BYE ? "Bye mode: tap runs" :
                mode == Mode.WIDE ? "Wide mode: tap 0–6 to add Wd+X (no ball counted)" :
                mode == Mode.NOBALL ? "No Ball mode: tap 0–6 to add NB+X (no ball counted)" :
                (wicketMode ? "Wicket mode: tap runs (0–6) for same delivery" : "")
        );
    }

    private void toggleWicketMode() {
        wicketMode = !wicketMode;
        // turn off other modes when wicket mode is on
        if (wicketMode && mode != Mode.NORMAL) mode = Mode.NORMAL;
        // Visual cue on button border
        btnWicket.setBorder(BorderFactory.createLineBorder(wicketMode ? Color.GREEN : new Color(255,255,255,120), 2, true));
        lblStatus.setText(wicketMode ? "Wicket mode: tap runs (0–6) for same delivery" : "");
    }

    private void toggleWicketModeOff() {
        wicketMode = false;
        btnWicket.setBorder(BorderFactory.createLineBorder(new Color(255,255,255,120), 2, true));
    }

    // ======================== SCORING ===============================

    private void onRunPressed(int run) {
        if (isInningsComplete()) return;

        if (wicketMode) {
            addRunThenWicket(run); // ✅ runs + wicket on same ball
            toggleWicketModeOff();
            return;
        }

        switch (mode) {
            case NORMAL -> addRun(run);
            case LB     -> addLegBye(run);
            case BYE    -> addBye(run);
            case WIDE   -> addWideWith(run);
            case NOBALL -> addNoBallWith(run);
        }
        if (mode != Mode.NORMAL) setMode(mode);
    }

    private void addRun(int run) {
        Action a = snapshot("run");
        a.run = run;
        a.legalBall = true;

        runs += run;
        balls++;

        strikerBalls++; strikerRuns += run;
        if (run == 4) strikerFours++;
        if (run == 6) strikerSixes++;

        BatStat bs = batting.get(striker);
        if (bs == null) { bs = new BatStat(); batting.put(striker, bs); }
        bs.b++; bs.r += run;
        if (run == 4) bs.f++;
        if (run == 6) bs.s++;

        BowlStat bw = bowling.get(bowler);
        if (bw == null) { bw = new BowlStat(); bowling.put(bowler, bw); }
        bw.balls++; bw.runs += run; bw.runsInCurrentOver += run;

        bowlerBalls++; bowlerRuns += run;
        runsThisOver += run;

        DB.insertBall(MatchContext.sessionId, inningsNo,
                balls / 6 + 1,
                balls % 6 == 0 ? 6 : balls % 6,
                striker, bowler, run, null, null);

        // --- NEW: upsert batting & bowling after ball to make scorecard live ---
        DB.upsertBatting(inningsNo, striker, bs.r, bs.b, bs.f, bs.s, bs.out);
        // Also ensure non-striker present in batting_scorecard (keep their values)
        BatStat ns = batting.get(nonStriker);
        if (ns != null) DB.upsertBatting(inningsNo, nonStriker, ns.r, ns.b, ns.f, ns.s, ns.out);

        DB.upsertBowling(inningsNo, bowler, bw.balls, bw.maidens, bw.runs, bw.wkts);
        // ----------------------------------------------------------------------

        if (run % 2 != 0) { swapBatsmen(); a.swapped = true; }

        String icon = iconForRuns(run);
        pushOverIcon(a, icon);
        updateScoreboard(run + " run(s)");
        refreshTables();

        checkOverCompletion();
        checkInningsCompletion();
        checkChaseFinished();

        history.push(a);
    }

    // ✅ NEW: Add runs and a wicket on the SAME legal ball (used in wicket mode)
    private void addRunThenWicket(int run) {
        Action a = snapshot("run+wicket");
        a.run = run;
        a.legalBall = true;

        // 1) Add the runs as a normal ball (BUT delay DB write until after popup so we can write single row)
        runs += run;
        balls++;

        strikerBalls++; strikerRuns += run;
        if (run == 4) strikerFours++;
        if (run == 6) strikerSixes++;

        BatStat bs = batting.get(striker);
        if (bs == null) { bs = new BatStat(); batting.put(striker, bs); }
        bs.b++; bs.r += run;
        if (run == 4) bs.f++;
        if (run == 6) bs.s++;

        BowlStat bw = bowling.get(bowler);
        if (bw == null) { bw = new BowlStat(); bowling.put(bowler, bw); }
        bw.balls++; bw.runs += run; bw.runsInCurrentOver += run;

        bowlerBalls++; bowlerRuns += run;
        runsThisOver += run;

        // Swap for odd completed runs BEFORE the wicket replacement
        if (run % 2 != 0) { swapBatsmen(); a.swapped = true; }

        // Save current batting positions (after potential swap) — these are the PRE-REPLACEMENT names
        String prevStriker = striker;
        String prevNonStriker = nonStriker;

        // 2) Popup for wicket details
        WicketPopup popup = new WicketPopup(this, striker, nonStriker);
        popup.setVisible(true);

        String dismissal = popup.getDismissalText();
        String newBat = popup.getNewBatsman();
        String whoOut = popup.getWhoOutName();

        if (dismissal == null || newBat == null || whoOut == null) {
            // User cancelled -> rollback to snapshot
            restoreFromAction(a);
            updateScoreboard("Wicket cancelled");
            refreshTables();
            return;
        }

        // 3) Apply wicket on SAME ball
        wickets++;
        bowlerWickets++;
        BowlStat bow = bowling.get(bowler);
        if (bow == null) { bow = new BowlStat(); bowling.put(bowler, bow); }
        bow.wkts++;

        BatStat outBat = batting.get(whoOut);
        if (outBat == null) { outBat = new BatStat(); batting.put(whoOut, outBat); }
        outBat.out = dismissal;

        // Determine replacement — use prevStriker/prevNonStriker (pre-replacement)
        boolean outWasPrevStriker = whoOut.equals(prevStriker);

        // Ensure new batsman exists in map
        batting.putIfAbsent(newBat, new BatStat());
        BatStat newBs = batting.get(newBat);

        if (outWasPrevStriker) {
            // prevStriker out -> striker becomes newBat
            striker = newBat;
            // update striker stat variables from batting map
            strikerRuns = newBs.r; strikerBalls = newBs.b; strikerFours = newBs.f; strikerSixes = newBs.s;

            // other remains prevNonStriker; ensure its stat variables reflect batting map
            BatStat otherBs = batting.get(prevNonStriker);
            if (otherBs != null) {
                nonStriker = prevNonStriker;
                nonStrikerRuns = otherBs.r; nonStrikerBalls = otherBs.b; nonStrikerFours = otherBs.f; nonStrikerSixes = otherBs.s;
            }
        } else {
            // prevNonStriker out -> nonStriker becomes newBat
            nonStriker = newBat;
            nonStrikerRuns = newBs.r; nonStrikerBalls = newBs.b; nonStrikerFours = newBs.f; nonStrikerSixes = newBs.s;

            // other remains prevStriker; ensure its stat variables reflect batting map
            BatStat otherBs = batting.get(prevStriker);
            if (otherBs != null) {
                striker = prevStriker;
                strikerRuns = otherBs.r; strikerBalls = otherBs.b; strikerFours = otherBs.f; strikerSixes = otherBs.s;
            }
        }

        // 4) Single DB row for this delivery with runs + wicket
        DB.insertBall(
                MatchContext.sessionId,
                inningsNo,
                balls / 6 + 1,
                balls % 6 == 0 ? 6 : balls % 6,
                whoOut, // store who got out as the batsman on this ball
                bowler,
                run,
                null,
                "W"
        );

        // --- NEW: upsert batting & bowling after combined ball ---
        // Upsert out batsman (with updated out description)
        DB.upsertBatting(inningsNo, whoOut, outBat.r, outBat.b, outBat.f, outBat.s, outBat.out);
        // Upsert new batsman (ensure present with values)
        DB.upsertBatting(inningsNo, newBat, newBs.r, newBs.b, newBs.f, newBs.s, newBs.out);

        // Determine the 'other' (pre-replacement) to upsert its latest values
        String otherPre = outWasPrevStriker ? prevNonStriker : prevStriker;
        BatStat otherPreBs = batting.get(otherPre);
        if (otherPreBs != null) {
            DB.upsertBatting(inningsNo, otherPre, otherPreBs.r, otherPreBs.b, otherPreBs.f, otherPreBs.s, otherPreBs.out);
        }

        DB.upsertBowling(inningsNo, bowler, bow.balls, bow.maidens, bow.runs, bow.wkts);
        // ------------------------------------------------------------------

        // 5) One combined icon, no comma inside: e.g., "🟢2 🔴W"
        String combinedIcon = iconForRuns(run) + " ⚪W";
        pushOverIcon(a, combinedIcon);

        updateScoreboard((run == 0 ? "Wicket!" : (run + "+W wicket")) + " (" + dismissal + ")");
        refreshTables();

        checkOverCompletion();
        checkInningsCompletion();
        checkChaseFinished();

        history.push(a);
    }
    private String iconForRuns(int run) {
        if (run == 0) return "⚪";
        if (run == 4) return "🔵4";
        if (run == 6) return "🟠6";
        return "🟢" + run;
    }

    private void addLegBye(int run) {
        Action a = snapshot("lb");
        a.run = run;
        a.legalBall = true;

        runs += run;
        balls++;

        strikerBalls++;
        BatStat bs = batting.get(striker);
        if (bs == null) { bs = new BatStat(); batting.put(striker, bs); }
        bs.b++;

        BowlStat bw = bowling.get(bowler);
        if (bw == null) { bw = new BowlStat(); bowling.put(bowler, bw); }
        bw.balls++; bw.runsInCurrentOver += run;
        bowlerBalls++;

        runsThisOver += run;

        DB.insertBall(MatchContext.sessionId, inningsNo,
                balls / 6 + 1,
                balls % 6 == 0 ? 6 : balls % 6,
                striker, bowler, run, "LB", null);

        // --- NEW: update DB batting (ball) and bowling ---
        DB.upsertBatting(inningsNo, striker, bs.r, bs.b, bs.f, bs.s, bs.out);
        BatStat ns = batting.get(nonStriker);
        if (ns != null) DB.upsertBatting(inningsNo, nonStriker, ns.r, ns.b, ns.f, ns.s, ns.out);
        DB.upsertBowling(inningsNo, bowler, bw.balls, bw.maidens, bw.runs, bw.wkts);
        // ---------------------------------------------------------

        if (run % 2 != 0) { swapBatsmen(); a.swapped = true; }

        pushOverIcon(a, "🟤LB" + run);
        updateScoreboard("Leg bye " + run);
        refreshTables();

        checkOverCompletion();
        checkInningsCompletion();
        checkChaseFinished();

        history.push(a);
    }

    private void addBye(int run) {
        Action a = snapshot("bye");
        a.run = run;
        a.legalBall = true;

        runs += run;
        balls++;

        strikerBalls++;
        BatStat bs = batting.get(striker);
        if (bs == null) { bs = new BatStat(); batting.put(striker, bs); }
        bs.b++;

        BowlStat bw = bowling.get(bowler);
        if (bw == null) { bw = new BowlStat(); bowling.put(bowler, bw); }
        bw.balls++; bw.runsInCurrentOver += run;
        bowlerBalls++;

        runsThisOver += run;

        DB.insertBall(MatchContext.sessionId, inningsNo,
                balls / 6 + 1,
                balls % 6 == 0 ? 6 : balls % 6,
                striker, bowler, run, "BYE", null);

        // --- NEW: upsert batting (ball) and bowling ---
        DB.upsertBatting(inningsNo, striker, bs.r, bs.b, bs.f, bs.s, bs.out);
        BatStat ns = batting.get(nonStriker);
        if (ns != null) DB.upsertBatting(inningsNo, nonStriker, ns.r, ns.b, ns.f, ns.s, ns.out);
        DB.upsertBowling(inningsNo, bowler, bw.balls, bw.maidens, bw.runs, bw.wkts);
        // ---------------------------------------------------

        if (run % 2 != 0) { swapBatsmen(); a.swapped = true; }

        pushOverIcon(a, "🟤B" + run);
        updateScoreboard("Bye " + run);
        refreshTables();

        checkOverCompletion();
        checkInningsCompletion();
        checkChaseFinished();

        history.push(a);
    }

    private void addWideWith(int x) {
        Action a = snapshot("wide");
        a.run = x;
        a.legalBall = false; // ✅ extra ball (does not count) remains in effect

        int add = 1 + x;
        runs += add;
        bowlerRuns += add;
        runsThisOver += add;

        BowlStat bw = bowling.get(bowler);
        if (bw == null) { bw = new BowlStat(); bowling.put(bowler, bw); }
        bw.runs += add;
        bw.runsInCurrentOver += add;

        int overNo = balls / 6 + 1;
        int ballNo = balls % 6; if (ballNo == 0) ballNo = 1;
        DB.insertBall(MatchContext.sessionId, inningsNo, overNo, ballNo, striker, bowler, add, "Wide", null);

        // --- NEW: upsert bowling (wide affects bowler runs) and ensure batsmen exist in batting_scorecard
        DB.upsertBowling(inningsNo, bowler, bw.balls, bw.maidens, bw.runs, bw.wkts);
        BatStat bs = batting.get(striker);
        if (bs != null) DB.upsertBatting(inningsNo, striker, bs.r, bs.b, bs.f, bs.s, bs.out);
        BatStat ns = batting.get(nonStriker);
        if (ns != null) DB.upsertBatting(inningsNo, nonStriker, ns.r, ns.b, ns.f, ns.s, ns.out);
        // ----------------------------------------------------------

        if (x % 2 != 0) { swapBatsmen(); a.swapped = true; }

        pushOverIcon(a, "🟣Wd+" + x);
        updateScoreboard("Wide +" + x);
        refreshTables();

        checkChaseFinished();
        checkInningsCompletion();

        history.push(a);
    }

    private void addNoBallWith(int x) {
        Action a = snapshot("noball");
        a.run = x;
        a.legalBall = false; // ✅ extra ball (does not count)

        int add = 1 + x;
        runs += add;

        if (x > 0) {
            strikerRuns += x;
            if (x == 4) strikerFours++;
            if (x == 6) strikerSixes++;

            BatStat bs = batting.get(striker);
            if (bs == null) { bs = new BatStat(); batting.put(striker, bs); }
            bs.r += x;
            if (x == 4) bs.f++;
            if (x == 6) bs.s++;
        }

        BowlStat bw = bowling.get(bowler);
        if (bw == null) { bw = new BowlStat(); bowling.put(bowler, bw); }
        bw.runs += add;
        bw.runsInCurrentOver += add;

        bowlerRuns += add;
        runsThisOver += add;

        int overNo = balls / 6 + 1;
        int ballNo = balls % 6; if (ballNo == 0) ballNo = 1;
        DB.insertBall(MatchContext.sessionId, inningsNo, overNo, ballNo, striker, bowler, add, "No Ball", null);

        // --- NEW: upsert bowling and potentially batting if batsman got runs from no-ball extras
        DB.upsertBowling(inningsNo, bowler, bw.balls, bw.maidens, bw.runs, bw.wkts);
        BatStat bs = batting.get(striker);
        if (bs != null) DB.upsertBatting(inningsNo, striker, bs.r, bs.b, bs.f, bs.s, bs.out);
        BatStat ns = batting.get(nonStriker);
        if (ns != null) DB.upsertBatting(inningsNo, nonStriker, ns.r, ns.b, ns.f, ns.s, ns.out);
        // ----------------------------------------------------------------------

        if (x % 2 != 0) { swapBatsmen(); a.swapped = true; }

        pushOverIcon(a, "🟣NB+" + x);
        updateScoreboard("No ball +" + x);
        refreshTables();

        checkChaseFinished();
        checkInningsCompletion();

        history.push(a);
    }

    // ======================== WICKET (legacy direct press disabled) ===============================
    private void wicket() {
        // Deprecated direct popup pathway; kept for compatibility if needed
        toggleWicketMode();
    }

    private String makeOutText(String type, String helpedBy) {
        String clean = type;
        if ("Run out".equals(type)) clean = "run out";
        if ("Catch out".equals(type)) clean = "c";
        if ("Bowled".equals(type)) clean = "b";
        if ("Stumping".equals(type)) clean = "st";
        if ("LBW".equals(type)) clean = "lbw";
        if ("Hit wicket".equals(type)) clean = "hit wicket";
        if (helpedBy != null && !helpedBy.isBlank()
                && ("run out".equals(clean) || "c".equals(clean) || "st".equals(clean))) {
            return clean + " (" + helpedBy + ")";
        }
        return clean;
    }

    private void manualSwap() {
        Action a = snapshot("swap");
        swapBatsmen();
        a.swapped = true;
        updateScoreboard("Strike swapped");
        refreshTables();
        history.push(a);
    }

    private void swapBatsmen() {
        // save stat snapshot for current striker before changing names
        int tr = strikerRuns, tb = strikerBalls, t4 = strikerFours, t6 = strikerSixes;
        int nr = nonStrikerRuns, nb = nonStrikerBalls, n4 = nonStrikerFours, n6 = nonStrikerSixes;

        // swap names
        String t = striker;
        striker = nonStriker;
        nonStriker = t;

        // swap stats
        strikerRuns = nr; strikerBalls = nb; strikerFours = n4; strikerSixes = n6;
        nonStrikerRuns = tr; nonStrikerBalls = tb; nonStrikerFours = t4; nonStrikerSixes = t6;
    }

    // ======================== OVER END ===============================

    /**
     * Modified over-completion behavior:
     *  - when an over completes, we insert/update bowling stats,
     *  - swap strike,
     *  - show an over summary dialog (so the 'This over' UI remains visible),
     *  - ONLY IF innings still ongoing prompt for new bowler and FORCE typing (Option B).
     *  - After that, clear the this-over UI entries and reset per-over counters.
     */
    private void checkOverCompletion() {
        if (balls % BALLS_PER_OVER != 0) return;

        // 1) mark maiden if applicable
        if (runsThisOver == 0) {
            bowlerMaidens++;
            BowlStat bws = bowling.get(bowler);
            if (bws != null) bws.maidens++;
        }

        // 2) persist bowler summary for the finished over
        BowlStat bw = bowling.get(bowler);
        if (bw != null) DB.upsertBowling(inningsNo, bowler, bw.balls, bw.maidens, bw.runs, bw.wkts);

        // 3) swap batsmen (standard at over end)
        swapBatsmen();

        // 4) Show over summary (keep this-over UI intact so user sees the six entries)
        String overNum = String.valueOf((balls / BALLS_PER_OVER));
        String overSummary = "Over " + overNum + " summary:\n" +
                "Runs this over: " + runsThisOver + "\n" +
                "Match score: " + runs + "/" + wickets + " (" + getOvers() + ")";
        JOptionPane.showMessageDialog(this, overSummary);

        // 5) If innings is still ongoing, prompt for next bowler and FORCE input (Option B)
        if (!isInningsComplete()) {
            String newBowler = null;
            // Force until non-empty name provided
            do {
                newBowler = JOptionPane.showInputDialog(this, "Enter next bowler:");
                if (newBowler == null) {
                    // User clicked Cancel — per Option B we force again (loop continues)
                    // But allow a 'final' cancel if they choose to close program: show confirm
                    int c = JOptionPane.showConfirmDialog(this,
                            "You must enter bowler name to continue.\nDo you want to cancel and end match?",
                            "Bowler required", JOptionPane.YES_NO_OPTION);
                    if (c == JOptionPane.YES_OPTION) {
                        // treat as forced end of innings
                        forceEndIfAllowed();
                        return;
                    }
                    // otherwise loop again
                } else {
                    newBowler = newBowler.trim();
                }
            } while (newBowler == null || newBowler.isEmpty());

            // Set new bowler
            bowler = newBowler;
            bowling.putIfAbsent(bowler, new BowlStat());
            // reset per-bowler counters
            bowlerBalls = 0;
            bowlerRuns = 0;
            bowlerWickets = 0;
            runsThisOver = 0;

            // Update bowler row in the table (first row reserved for current bowler)
            bowlModel.setValueAt(bowler, 0, 0);
            bowlModel.setValueAt("0.0", 0, 1);
            bowlModel.setValueAt(0, 0, 2);
            bowlModel.setValueAt(0, 0, 3);
            bowlModel.setValueAt(0, 0, 4);
            bowlModel.setValueAt("0.00", 0, 5);
        } else {
            // innings completed at exactly over end: do not prompt for bowler
            // proceed to innings completion processing (caller handles it)
        }

        // 6) clear this-over UI and counters (after summary and prompt)
        thisOverEntries.clear();
        uiBallsInOver = 0;
        runsThisOver = 0;
        renderThisOver();

        // 7) refresh tables to reflect the new bowler / swap / reset
        refreshTables();
    }

    // ======================== DISPLAY ===============================

    private void refreshTables() {
        batModel.setValueAt(striker + " *", 0, 0);
        batModel.setValueAt(strikerRuns, 0, 1);
        batModel.setValueAt(strikerBalls, 0, 2);
        batModel.setValueAt(strikerFours, 0, 3);
        batModel.setValueAt(strikerSixes, 0, 4);
        batModel.setValueAt(sr(strikerRuns, strikerBalls), 0, 5);

        batModel.setValueAt(nonStriker, 1, 0);
        batModel.setValueAt(nonStrikerRuns, 1, 1);
        batModel.setValueAt(nonStrikerBalls, 1, 2);
        batModel.setValueAt(nonStrikerFours, 1, 3);
        batModel.setValueAt(nonStrikerSixes, 1, 4);
        batModel.setValueAt(sr(nonStrikerRuns, nonStrikerBalls), 1, 5);

        bowlModel.setValueAt(toOvers(bowlerBalls), 0, 1);
        bowlModel.setValueAt(bowlerMaidens, 0, 2);
        bowlModel.setValueAt(bowlerRuns, 0, 3);
        bowlModel.setValueAt(bowlerWickets, 0, 4);
        bowlModel.setValueAt(er(bowlerRuns, bowlerBalls), 0, 5);
    }

    private void updateScoreboard(String msg) {
        lblScore.setText(runs + " - " + wickets + " (" + getOvers() + ")");

        double crr = balls == 0 ? 0.0 : (runs * 6.0 / balls);
        lblCRR.setText(String.format("CRR: %.2f", crr));

        if (!firstInnings) {
            int remainingRuns = Math.max(0, target - runs);
            int remainingBalls = Math.max(0, (totalOvers * 6) - balls);

            if (remainingRuns <= 0) {
                lblRRR.setText("RRR: --");
                lblChaseLeft.setText("Target achieved");
            } else {
                double rrr = remainingBalls == 0 ? 0.0 : (remainingRuns * 6.0 / remainingBalls);
                lblRRR.setText(String.format("RRR: %.2f", rrr));
                lblChaseLeft.setText(String.format("Need %d off %d", remainingRuns, remainingBalls));
            }
        } else {
            lblRRR.setText("RRR: --");
            lblChaseLeft.setText("");
        }

        lblStatus.setText(msg);
    }

    private void pushOverIcon(Action a, String icon) {
        a.overIcon = icon;
        // wides/noballs don't increment legal ball
        if (!Objects.equals(a.type, "wide") && !Objects.equals(a.type, "noball")) {
            uiBallsInOver++;
        }
        thisOverEntries.add(icon); // combined icon for run+wicket keeps no comma inside
        renderThisOver();
    }

    private void popOverIcon(Action a) {
        if (a.overIcon != null) {
            if (!thisOverEntries.isEmpty()) thisOverEntries.removeLast();
            if (!Objects.equals(a.type, "wide") && !Objects.equals(a.type, "noball")) {
                if (uiBallsInOver > 0) uiBallsInOver--;
            }
            renderThisOver();
        }
    }

    private void renderThisOver() {
        if (thisOverEntries.isEmpty()) {
            thisOverLabel.setText("<html><span style='font-size:16px;color:white;'>This over:</span></html>");
            return;
        }
        StringBuilder sb = new StringBuilder("<html><span style='font-size:16px;color:white;'>This over: ");
        int count = 0;
        for (String s : thisOverEntries) {
            if (count > 0) sb.append(", "); // commas only BETWEEN separate deliveries
            sb.append(s);
            count++;
            if (count % 10 == 0) sb.append("<br>");
        }
        sb.append("</span></html>");
        thisOverLabel.setText(sb.toString());
    }

    // ======================== INNINGS END ===============================

    private boolean isInningsComplete() {
        return wickets == MAX_WICKETS || balls == totalOvers * BALLS_PER_OVER;
    }

    private void checkChaseFinished() {
        if (!firstInnings && runs >= target) {
            flushOpenPlayersAsNotOut();
            flushCurrentBowler();

            JOptionPane.showMessageDialog(this,
                    "🏆 Batting team won!\nChased " + target +
                            " with " + ((totalOvers * 6) - balls) + " balls to spare.");

            dispose();
            new ScorecardUI();
        }
    }

    private void forceEndIfAllowed() {
        balls = totalOvers * BALLS_PER_OVER;
        checkInningsCompletion();
    }

    private void checkInningsCompletion() {
        if (!isInningsComplete()) return;

        flushOpenPlayersAsNotOut();
        flushCurrentBowler();

        if (firstInnings) {
            String oversTxt = getOvers();
            double crr = balls == 0 ? 0.0 : (runs * 6.0 / balls);

            JOptionPane.showMessageDialog(this,
                    "1st Innings Summary\n\nScore: " + runs + "/" + wickets +
                            " in " + oversTxt + " overs\nCRR: " + String.format("%.2f", crr));

            int targetRuns = runs + 1;

            SecondInningsData.target = targetRuns;
            SecondInningsData.totalOvers = totalOvers;
            SecondInningsData.firstInningsScore = runs + "/" + wickets;
            SecondInningsData.firstInningsOvers = oversTxt;

            JOptionPane.showMessageDialog(this, "Target for 2nd Innings: " + targetRuns);

            dispose();
            new OpeningPlayersUI();

        } else {
            String result = (runs >= target) ? "Batting team won!" : "Bowling team won!";
            JOptionPane.showMessageDialog(this,
                    "🏆 " + result + "\nFinal: " + runs + "/" + wickets + " (" + getOvers() + ")");
            dispose();
            new ScorecardUI();
            
        }
    }
    
    private void flushOpenPlayersAsNotOut() {
        BatStat s = batting.get(striker);
        if (s != null)
            DB.upsertBatting(inningsNo, striker, s.r, s.b, s.f, s.s, s.out);

        BatStat n = batting.get(nonStriker);
        if (n != null)
            DB.upsertBatting(inningsNo, nonStriker, n.r, n.b, n.f, n.s, n.out);

        for (Map.Entry<String,BatStat> e : batting.entrySet()) {
            DB.upsertBatting(inningsNo, e.getKey(), e.getValue().r, e.getValue().b,
                    e.getValue().f, e.getValue().s, e.getValue().out);
        }
    }

    private void flushCurrentBowler() {
        for (Map.Entry<String,BowlStat> e : bowling.entrySet()) {
            DB.upsertBowling(inningsNo, e.getKey(), e.getValue().balls,
                    e.getValue().maidens, e.getValue().runs, e.getValue().wkts);
        }
    }

    // ======================== UNDO ===============================

    private Action snapshot(String type) {
        Action a = new Action();
        a.type = type;
        a.prevStriker = striker;
        a.prevNonStriker = nonStriker;
        a.prevBowler = bowler;

        a.prevRuns = runs; a.prevWkts = wickets; a.prevBalls = balls;

        a.prevStrikerRuns = strikerRuns; a.prevStrikerBalls = strikerBalls; a.prevStrikerFours = strikerFours; a.prevStrikerSixes = strikerSixes;
        a.prevNonStrikerRuns = nonStrikerRuns; a.prevNonStrikerBalls = nonStrikerBalls; a.prevNonStrikerFours = nonStrikerFours; a.prevNonStrikerSixes = nonStrikerSixes;

        a.prevBowlerBalls = bowlerBalls; a.prevBowlerRuns = bowlerRuns; a.prevBowlerWkts = bowlerWickets; a.prevBowlerMaidens = bowlerMaidens;
        a.prevRunsThisOver = runsThisOver; a.prevUiBallsInOver = uiBallsInOver;
        return a;
    }

    private void restoreFromAction(Action a) {
        runs = a.prevRuns; wickets = a.prevWkts; balls = a.prevBalls;
        striker = a.prevStriker; nonStriker = a.prevNonStriker; bowler = a.prevBowler;

        strikerRuns = a.prevStrikerRuns; strikerBalls = a.prevStrikerBalls; strikerFours = a.prevStrikerFours; strikerSixes = a.prevStrikerSixes;
        nonStrikerRuns = a.prevNonStrikerRuns; nonStrikerBalls = a.prevNonStrikerBalls; nonStrikerFours = a.prevNonStrikerFours; nonStrikerSixes = a.prevNonStrikerSixes;

        bowlerBalls = a.prevBowlerBalls; bowlerRuns = a.prevBowlerRuns; bowlerWickets = a.prevBowlerWkts; bowlerMaidens = a.prevBowlerMaidens;
        runsThisOver = a.prevRunsThisOver; uiBallsInOver = a.prevUiBallsInOver;
        popOverIcon(a);
    }

    private void undoLast() {
        if (history.isEmpty()) return;
        Action a = history.pop();

        if (!Objects.equals(a.type, "swap")) {
            deleteLastBallRowForMatch(MatchContext.sessionId);
        }

        restoreFromAction(a);

        updateScoreboard("Undone");
        refreshTables();
    }

    private void deleteLastBallRowForMatch(int matchId) {
        String sql = "DELETE FROM ball_by_ball WHERE match_id = ? ORDER BY id DESC LIMIT 1";
        try (Connection c = DB.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, matchId);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    // ======================== UTILS ===============================
    private String sr(int r, int b) { return b == 0 ? "0.00" : String.format("%.2f", r * 100.0 / b); }
    private String er(int r, int b) { return b == 0 ? "0.00" : String.format("%.2f", r * 6.0 / b); }
    private String toOvers(int b) { return (b / 6) + "." + (b % 6); }
    private String getOvers() { return (balls / 6) + "." + (balls % 6); }

    public static void main(String[] args) {
    	System.setProperty("apple.laf.useScreenMenuBar", "true");
    	System.setProperty("swing.defaultlaf", "javax.swing.plaf.nimbus.NimbusLookAndFeel");
    	try {
    	    UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
    	} catch (Exception e) {
    	    e.printStackTrace();
    	}
        SwingUtilities.invokeLater(() -> new LiveScoreUI("A","B","C",2,true,0));
    }

    // =====================================================================
    // Themed Wicket Popup (Create Account theme) with Run-out logic fields
    // =====================================================================
    private static class WicketPopup extends JDialog {

        private String dismissalText = null;
        private String newBatsman = null;
        private String whoOutName = null;
        private String helperName = null;
        private String catcherName = null;

        private JComboBox<String> whoOutDrop;
        private JLabel lblWhoOut;

        private JTextField helperField;
        private JLabel lblHelper;

        private JTextField catcherField;
        private JLabel lblCatcher;

        private final Color GRAD_TOP = new Color(10, 20, 60);
        private final Color GRAD_BOTTOM = new Color(25, 45, 110);

        public WicketPopup(JFrame parent, String striker, String nonStriker) {
            super(parent, "Fall of Wicket", true);
            setSize(470, 560);   // sized so New batsman + Done sit nicely
            setLocationRelativeTo(parent);
            setUndecorated(true);

            // BG gradient
            JPanel bg = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setPaint(new GradientPaint(0,0,GRAD_TOP, 0,getHeight(),GRAD_BOTTOM));
                    g2.fillRect(0,0,getWidth(),getHeight());
                }
            };
            bg.setLayout(new GridBagLayout());
            setContentPane(bg);

            // Glass card
            JPanel card = new JPanel(null) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    g2.setColor(new Color(255,255,255,40));
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),28,28);

                    g2.setColor(new Color(25,35,70,210));
                    g2.fillRoundRect(4,4,getWidth()-8,getHeight()-8,20,20);

                    g2.setColor(new Color(150,170,255,120));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRoundRect(4,4,getWidth()-8,getHeight()-8,20,20);
                }
            };

            card.setPreferredSize(new Dimension(390, 520));
            bg.add(card);

            // Title
            JLabel title = new JLabel("Fall of wicket", SwingConstants.CENTER);
            title.setBounds(0, 20, 390, 40);
            title.setFont(new Font("Segoe UI", Font.BOLD, 28));
            title.setForeground(Color.WHITE);
            card.add(title);

            JLabel sub = new JLabel("Enter wicket details", SwingConstants.CENTER);
            sub.setBounds(0, 55, 390, 25);
            sub.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            sub.setForeground(new Color(200,210,255));
            card.add(sub);

            // Wicket type
            JLabel lblHow = new JLabel("How wicket fall?");
            lblHow.setBounds(35, 90, 280, 20);
            lblHow.setFont(new Font("Segoe UI", Font.BOLD, 16));
            lblHow.setForeground(new Color(210,225,255));
            card.add(lblHow);

            JComboBox<String> typeDrop = new JComboBox<>(new String[]{
                    "Bowled",
                    "Catch out",
                    "Run out",
                    "Stumping",
                    "LBW",
                    "Hit wicket"
            });
            typeDrop.setBounds(35, 115, 320, 42);
            typeDrop.setFont(new Font("Segoe UI", Font.PLAIN, 17));
            typeDrop.setBackground(new Color(240,245,255));
            typeDrop.setBorder(BorderFactory.createLineBorder(new Color(120,150,230),2));
            card.add(typeDrop);

            // Who out?
            lblWhoOut = new JLabel("Who got out?");
            lblWhoOut.setBounds(35, 165, 250, 20);
            lblWhoOut.setFont(new Font("Segoe UI", Font.BOLD, 16));
            lblWhoOut.setForeground(new Color(210,225,255));
            lblWhoOut.setVisible(false);
            card.add(lblWhoOut);

            whoOutDrop = new JComboBox<>();
            whoOutDrop.addItem(striker);
            whoOutDrop.addItem(nonStriker);
            whoOutDrop.setBounds(35, 190, 320, 42);
            whoOutDrop.setFont(new Font("Segoe UI", Font.PLAIN, 17));
            whoOutDrop.setBackground(new Color(240,245,255));
            whoOutDrop.setBorder(BorderFactory.createLineBorder(new Color(120,150,230),2));
            whoOutDrop.setVisible(false);
            card.add(whoOutDrop);

            // Run out helper
            lblHelper = new JLabel("Who helped?");
            lblHelper.setBounds(35, 240, 250, 20);
            lblHelper.setFont(new Font("Segoe UI", Font.BOLD, 16));
            lblHelper.setForeground(new Color(210,225,255));
            lblHelper.setVisible(false);
            card.add(lblHelper);

            helperField = new JTextField();
            helperField.setBounds(35, 265, 320, 42);
            helperField.setFont(new Font("Segoe UI", Font.PLAIN, 18));
            helperField.setBackground(new Color(240,245,255));
            helperField.setBorder(BorderFactory.createMatteBorder(0,0,3,0,new Color(60,150,90)));
            helperField.setVisible(false);
            card.add(helperField);

            // Catch helper
            lblCatcher = new JLabel("Who caught?");
            lblCatcher.setBounds(35, 240, 250, 20);
            lblCatcher.setFont(new Font("Segoe UI", Font.BOLD, 16));
            lblCatcher.setForeground(new Color(210,225,255));
            lblCatcher.setVisible(false);
            card.add(lblCatcher);

            catcherField = new JTextField();
            catcherField.setBounds(35, 265, 320, 42);
            catcherField.setFont(new Font("Segoe UI", Font.PLAIN, 18));
            catcherField.setBackground(new Color(240,245,255));
            catcherField.setBorder(BorderFactory.createMatteBorder(0,0,3,0,new Color(60,150,90)));
            catcherField.setVisible(false);
            card.add(catcherField);

            // New batsman
            JLabel lblNew = new JLabel("New batsman");
            lblNew.setBounds(35, 325, 250, 20); // slightly lower
            lblNew.setFont(new Font("Segoe UI", Font.BOLD, 16));
            lblNew.setForeground(new Color(210,225,255));
            card.add(lblNew);

            JTextField newBatField = new JTextField();
            newBatField.setBounds(35, 350, 320, 42); // spaced nicely above Done
            newBatField.setFont(new Font("Segoe UI", Font.PLAIN, 18));
            newBatField.setBackground(new Color(240,245,255));
            newBatField.setBorder(BorderFactory.createMatteBorder(0,0,3,0,new Color(60,150,90)));
            card.add(newBatField);

            // Done
            JButton btn = new JButton("Done") {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setColor(new Color(70,100,210));
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),16,16);
                    super.paintComponent(g);
                }
            };
            btn.setBounds(35, 410, 320, 48);
            btn.setForeground(Color.WHITE);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 20));
            btn.setFocusPainted(false);
            btn.setContentAreaFilled(false);
            btn.setBorder(BorderFactory.createLineBorder(new Color(255,255,255,120),2));
            card.add(btn);

            // Close
            JButton close = new JButton("✖");
            close.setBounds(345, 15, 40, 30);
            close.setForeground(Color.WHITE);
            close.setFont(new Font("Segoe UI", Font.BOLD, 14));
            close.setContentAreaFilled(false);
            close.setBorder(BorderFactory.createEmptyBorder());
            close.addActionListener(e -> dispose());
            card.add(close);

            // Show logic
            typeDrop.addActionListener(e -> {
                String type = (String) typeDrop.getSelectedItem();

                boolean isRunOut = type.equals("Run out");
                boolean isCatch = type.equals("Catch out");

                lblWhoOut.setVisible(isRunOut);
                whoOutDrop.setVisible(isRunOut);

                lblHelper.setVisible(isRunOut);
                helperField.setVisible(isRunOut);

                lblCatcher.setVisible(isCatch);
                catcherField.setVisible(isCatch);
            });

            // Done
            btn.addActionListener(e -> {
                String type = (String) typeDrop.getSelectedItem();

                if (type.equals("Run out")) {
                    whoOutName = (String) whoOutDrop.getSelectedItem();
                    helperName = helperField.getText().trim();
                    if (helperName.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "Enter who helped");
                        return;
                    }
                    dismissalText = "Run out (" + whoOutName + " assisted by " + helperName + ")";

                } else if (type.equals("Catch out")) {
                    catcherName = catcherField.getText().trim();
                    if (catcherName.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "Enter who caught");
                        return;
                    }
                    dismissalText = "Catch out (caught by " + catcherName + ")";
                    whoOutName = striker; // default

                } else {
                    dismissalText = type;
                    whoOutName = striker;
                }

                newBatsman = newBatField.getText().trim();
                if (newBatsman.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Enter new batsman");
                    return;
                }

                dispose();
            });
        }

        public String getDismissalText() { return dismissalText; }
        public String getNewBatsman() { return newBatsman; }
        public String getWhoOutName() { return whoOutName; }
        public String getHelperName() { return helperName; }
        public String getCatcherName() { return catcherName; }
    }

}