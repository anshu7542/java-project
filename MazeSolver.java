import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class MazeSolver extends JPanel {

    private static final int WIDTH = 600;
    private static final int HEIGHT = 600;
    private static final int ROWS = 30;
    private final int cellSize = WIDTH / ROWS;

    // Colors
    private static final Color BACKGROUND = new Color(46, 2, 73);
    private static final Color PATH_CLOSE = new Color(87, 9, 135);
    private static final Color PATH_OPEN = new Color(127, 17, 194);
    private static final Color GRID_COLOR = new Color(87, 10, 87);
    private static final Color WALL_COLOR = new Color(248, 6, 204);
    private static final Color START_COLOR = new Color(0, 255, 171);
    private static final Color END_COLOR = new Color(47, 255, 0);
    private static final Color PATH_COLOR = new Color(255, 210, 76);
    private static final Color NO_PATH_COLOR = new Color(140, 140, 140);

    private final Node[][] grid = new Node[ROWS][ROWS];
    private Node startNode = null, endNode = null;
    private JFrame frame;

    private double wallProbability = 0.35;

    public MazeSolver(JFrame frame) {
        this.frame = frame;
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(BACKGROUND);

        initGrid();
        addMouseListeners();
        addKeyListener(new KeyHandler());
        setFocusable(true);

        generateRandomWalls(wallProbability);
    }

    private void initGrid() {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < ROWS; c++)
                grid[r][c] = new Node(r, c);

        for (int i = 0; i < ROWS; i++) {
            grid[0][i].makeWall();
            grid[ROWS - 1][i].makeWall();
            grid[i][0].makeWall();
            grid[i][ROWS - 1].makeWall();
        }
    }

    private void generateRandomWalls(double prob) {
        Random rnd = new Random();

        for (int r = 1; r < ROWS - 1; r++) {
            for (int c = 1; c < ROWS - 1; c++) {
                grid[r][c].reset();
                grid[r][c].setWall(rnd.nextDouble() < prob);
                if (grid[r][c].isWall()) grid[r][c].makeWall();
            }
        }

        repaint();
    }

    private void clearAll() {
        startNode = endNode = null;

        for (Node[] row : grid)
            for (Node n : row) {
                n.reset();
                n.setWall(false);
            }

        for (int i = 0; i < ROWS; i++) {
            grid[0][i].makeWall();
            grid[ROWS - 1][i].makeWall();
            grid[i][0].makeWall();
            grid[i][ROWS - 1].makeWall();
        }

        generateRandomWalls(wallProbability);
        repaint();
    }

    private void addMouseListeners() {
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();

                int row = e.getY() / cellSize;
                int col = e.getX() / cellSize;

                if (row < 0 || row >= ROWS || col < 0 || col >= ROWS) return;

                Node clicked = grid[row][col];

                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (clicked.isWall()) return;

                    if (startNode == null) {
                        startNode = clicked;
                        clicked.makeStart();
                    } else if (endNode == null && clicked != startNode) {
                        endNode = clicked;
                        clicked.makeEnd();
                    }
                }

                if (SwingUtilities.isRightMouseButton(e)) {
                    if (clicked == startNode) startNode = null;
                    if (clicked == endNode) endNode = null;

                    clicked.reset();
                }

                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                // MANUAL WALL DISABLED
            }
        };

        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        for (Node[] row : grid)
            for (Node n : row) {
                g.setColor(n.color);
                g.fillRect(n.col * cellSize, n.row * cellSize, cellSize, cellSize);
            }

        g.setColor(GRID_COLOR);
        for (int i = 0; i <= ROWS; i++) {
            g.drawLine(0, i * cellSize, WIDTH, i * cellSize);
            g.drawLine(i * cellSize, 0, i * cellSize, HEIGHT);
        }
    }

    private void repaintPause(int ms) {
        try {
            repaint();
            Thread.sleep(ms);
        } catch (Exception ignored) {}
    }

    // -------------------------------
    //   PATHFINDING ALGORITHMS
    // -------------------------------

    private void runAlgorithm(String type, ResultRecorder recorder) {
        if (startNode == null || endNode == null) {
            frame.setTitle("Set Start and End first!");
            return;
        }

        for (Node[] row : grid)
            for (Node n : row)
                n.updateNeighbors(grid);

        long start = System.currentTimeMillis();
        int length = Integer.MAX_VALUE;

        if (type.equals("A*")) length = runAStar();
        if (type.equals("BFS")) length = runBFS();
        if (type.equals("DFS")) length = runDFS();

        long time = System.currentTimeMillis() - start;

        recorder.addResult(type, length, time);
    }

    private int runAStar() {
        resetForSearch();

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(n -> n.f));
        Map<Node, Node> came = new HashMap<>();
        Map<Node, Integer> g = new HashMap<>();

        for (Node[] row : grid)
            for (Node n : row)
                g.put(n, Integer.MAX_VALUE);

        g.put(startNode, 0);
        startNode.f = h(startNode, endNode);
        open.add(startNode);

        while (!open.isEmpty()) {
            Node cur = open.poll();

            if (cur == endNode)
                return reconstructPath(came, cur);

            for (Node nb : cur.neighbors) {
                if (nb.isWall()) continue;

                int temp = g.get(cur) + 1;
                if (temp < g.get(nb)) {
                    came.put(nb, cur);
                    g.put(nb, temp);
                    nb.f = temp + h(nb, endNode);

                    if (!open.contains(nb)) {
                        open.add(nb);
                        nb.makeOpen();
                    }
                }
            }

            if (cur != startNode) cur.makeClosed();
            repaintPause(10);
        }

        return Integer.MAX_VALUE;
    }

    private int runBFS() {
        resetForSearch();

        Queue<Node> q = new LinkedList<>();
        Map<Node, Node> parent = new HashMap<>();
        Set<Node> visited = new HashSet<>();

        q.add(startNode);
        visited.add(startNode);

        while (!q.isEmpty()) {
            Node cur = q.poll();

            if (cur == endNode)
                return reconstructPath(parent, cur);

            for (Node nb : cur.neighbors) {
                if (!visited.contains(nb) && !nb.isWall()) {
                    visited.add(nb);
                    parent.put(nb, cur);
                    q.add(nb);
                    nb.makeOpen();
                }
            }

            if (cur != startNode) cur.makeClosed();
            repaintPause(10);
        }

        return Integer.MAX_VALUE;
    }

    private int runDFS() {
        resetForSearch();

        Stack<Node> st = new Stack<>();
        Map<Node, Node> parent = new HashMap<>();
        Set<Node> visited = new HashSet<>();

        st.push(startNode);

        while (!st.isEmpty()) {
            Node cur = st.pop();

            if (visited.contains(cur)) continue;
            visited.add(cur);

            if (cur == endNode)
                return reconstructPath(parent, cur);

            for (Node nb : cur.neighbors) {
                if (!visited.contains(nb) && !nb.isWall()) {
                    parent.put(nb, cur);
                    st.push(nb);
                    nb.makeOpen();
                }
            }

            if (cur != startNode) cur.makeClosed();
            repaintPause(10);
        }

        return Integer.MAX_VALUE;
    }

    private int reconstructPath(Map<Node, Node> parent, Node cur) {
        int len = 0;

        while (parent.containsKey(cur)) {
            cur = parent.get(cur);
            if (cur != startNode) cur.makePath();
            len++;
            repaintPause(20);
        }

        return len;
    }

    private void resetForSearch() {
        for (Node[] row : grid)
            for (Node n : row) {
                if (n.isWall()) continue;
                if (n == startNode) n.makeStart();
                else if (n == endNode) n.makeEnd();
                else n.reset();
            }

        repaint();
    }

    private int h(Node a, Node b) {
        return Math.abs(a.row - b.row) + Math.abs(a.col - b.col);
    }

    private void runAllSequential() {
        if (startNode == null || endNode == null) {
            frame.setTitle("Set Start & End First!");
            return;
        }

        ResultRecorder r = new ResultRecorder();

        runAlgorithm("A*", r);
        sleep(300);
        runAlgorithm("BFS", r);
        sleep(300);
        runAlgorithm("DFS", r);

        SwingUtilities.invokeLater(() -> showResults(r));
    }

    private void sleep(int ms) {
        try { Thread.sleep(ms); } catch (Exception ignored) {}
    }

    private void showResults(ResultRecorder rec) {
        JFrame f = new JFrame("Algorithm Comparison");
        f.setSize(350, 200);
        f.setLocationRelativeTo(null);

        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(10, 10, 10, 10));

        String[] col = {"Algo", "Path", "Time (ms)"};
        Object[][] data = rec.table();

        JTable table = new JTable(data, col);
        p.add(new JScrollPane(table), BorderLayout.CENTER);

        JLabel best = new JLabel("BEST: " + rec.best().name);
        best.setFont(new Font("", Font.BOLD, 16));
        best.setForeground(new Color(0, 200, 90));

        p.add(best, BorderLayout.SOUTH);

        f.add(p);
        f.setVisible(true);
    }

    private class Result {
        String name;
        int length;
        long time;

        Result(String n, int l, long t) {
            name = n;
            length = l;
            time = t;
        }
    }

    private class ResultRecorder {
        ArrayList<Result> list = new ArrayList<>();

        void addResult(String name, int len, long time) {
            list.add(new Result(name, len, time));
        }

        Object[][] table() {
            Object[][] d = new Object[list.size()][3];

            for (int i = 0; i < list.size(); i++) {
                Result r = list.get(i);
                d[i][0] = r.name;
                d[i][1] = r.length == Integer.MAX_VALUE ? "No Path" : r.length;
                d[i][2] = r.time;
            }

            return d;
        }

        Result best() {
            Result b = null;

            for (Result r : list) {
                if (r.length == Integer.MAX_VALUE) continue;
                if (b == null) b = r;
                else if (r.length < b.length ||
                        (r.length == b.length && r.time < b.time))
                    b = r;
            }

            return b;
        }
    }

    private class Node {
        int row, col;
        Color color = BACKGROUND;
        boolean wall = false;
        List<Node> neighbors = new ArrayList<>();
        int f;

        Node(int r, int c) {
            row = r;
            col = c;
        }

        void reset() {
            color = BACKGROUND;
        }

        void makeWall() {
            wall = true;
            color = WALL_COLOR;
        }

        void makeStart() {
            color = START_COLOR;
        }

        void makeEnd() {
            color = END_COLOR;
        }

        void makeOpen() {
            color = PATH_OPEN;
        }

        void makeClosed() {
            color = PATH_CLOSE;
        }

        void makePath() {
            color = PATH_COLOR;
        }

        void setWall(boolean w) {
            wall = w;
        }

        boolean isWall() {
            return wall;
        }

        void updateNeighbors(Node[][] g) {
            neighbors.clear();

            if (row > 0 && !g[row - 1][col].isWall()) neighbors.add(g[row - 1][col]);
            if (row < ROWS - 1 && !g[row + 1][col].isWall()) neighbors.add(g[row + 1][col]);
            if (col > 0 && !g[row][col - 1].isWall()) neighbors.add(g[row][col - 1]);
            if (col < ROWS - 1 && !g[row][col + 1].isWall()) neighbors.add(g[row][col + 1]);
        }
    }

    // ------------------------------
    //        KEY HANDLER FIX
    // ------------------------------
    private class KeyHandler extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            int k = e.getKeyCode();

            if (k == KeyEvent.VK_R) new Thread(() -> runAllSequential()).start();

            if (k == KeyEvent.VK_C) clearAll();

            if (k == KeyEvent.VK_G) generateRandomWalls(wallProbability);

            if (k == KeyEvent.VK_UP) {
                wallProbability = Math.min(0.9, wallProbability + 0.05);
                frame.setTitle("Wall Density: " + wallProbability);
            }

            if (k == KeyEvent.VK_DOWN) {
                wallProbability = Math.max(0.0, wallProbability - 0.05);
                frame.setTitle("Wall Density: " + wallProbability);
            }
        }
    }

    // -------------------------------------------------
    //                MAIN FUNCTION
    // -------------------------------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {

            JFrame f = new JFrame("Maze Solver - Auto Walls");
            MazeSolver p = new MazeSolver(f);

            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.add(p);

            JPanel info = new JPanel();
            info.setBackground(Color.DARK_GRAY);

            JLabel l = new JLabel("<html><font color='white'>Left-click: Start/End | G: New Walls | R: Run | C: Clear</font></html>");
            info.add(l);

            f.add(info, BorderLayout.NORTH);

            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
