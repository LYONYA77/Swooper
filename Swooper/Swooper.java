import java.awt.*;
import java.awt.event.*;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import javax.swing.*;

public class Swooper extends JPanel implements KeyListener, ActionListener {

    private final int ROWS, COLS;
    private final double MINE_CHANCE;
    private final int TILE_SIZE = 40;
    private Tile[][] board;
    private int playerX = 0, playerY = 0;
    private final JFrame parentFrame;
    private boolean firstClick = true;
    private int visionRadius = -1; //Cavemode
    private final Timer renderTimer;
    private final Timer gameTimer;
    private double flagPulse = 0;
    private int secondsElapsed = 0;

    private boolean hunterMode = false;
    private boolean cavehunterMode = false;
    private int hunterX, hunterY;
    private Timer hunterTimer;

    private final Color[] numberColors = {
            Color.BLUE, Color.GREEN.darker(), Color.RED, Color.MAGENTA,
            Color.ORANGE, Color.CYAN.darker(), Color.BLACK, Color.GRAY
    };

    public Swooper(int rows, int cols, double mineChance, JFrame frame, int visionRadius, boolean hunterMode, boolean cavehunterMode) {
        this.ROWS = rows;
        this.COLS = cols;
        this.MINE_CHANCE = mineChance;
        this.parentFrame = frame;
        this.visionRadius = visionRadius;
        this.hunterMode = hunterMode;
        this.cavehunterMode = cavehunterMode;

        setPreferredSize(new Dimension(cols * TILE_SIZE, rows * TILE_SIZE));
        setBackground(new Color(50, 50, 50));
        setFocusable(true);
        addKeyListener(this);

        initBoard();
        requestFocusInWindow();

        renderTimer = new Timer(100, this);
        renderTimer.start();

        gameTimer = new Timer(1000, e -> {
            secondsElapsed++;
            repaint();
        });

        if (hunterMode || cavehunterMode) startHunter();
    }

    private void initBoard() {
        board = new Tile[ROWS][COLS];
        Random rand = new Random();

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                boolean mine = rand.nextDouble() < MINE_CHANCE;
                board[r][c] = new Tile(mine);
            }
        }

        recalcAdjacentMines();

        playerX = 0;
        playerY = 0;
        firstClick = true;
        secondsElapsed = 0;
        if (gameTimer != null) gameTimer.stop();
        if (hunterMode || cavehunterMode) startHunter();
    }

    private void recalcAdjacentMines() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (!board[r][c].isMine) {
                    int count = 0;
                    for (int dr = -1; dr <= 1; dr++)
                        for (int dc = -1; dc <= 1; dc++)
                            if (dr != 0 || dc != 0) {
                                int nr = r + dr, nc = c + dc;
                                if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS && board[nr][nc].isMine)
                                    count++;
                            }
                    board[r][c].adjacentMines = count;
                }
            }
        }
    }

    private void startHunter() {
        Random rand = new Random();
        do {
            hunterX = rand.nextInt(COLS);
            hunterY = rand.nextInt(ROWS);
        } while (Math.abs(hunterX - playerX) < 3 && Math.abs(hunterY - playerY) < 3);

        if (hunterTimer != null) hunterTimer.stop();
        hunterTimer = new Timer(1000, e -> moveHunter()); // Hunter Geschwindigkeit
        hunterTimer.start();
    }

    private void moveHunter() {
        if (playerX == hunterX && playerY == hunterY) {
            gameOver("Der Hunter hat dich erwischt!");
            return;
        }

        int dx = playerX - hunterX;
        int dy = playerY - hunterY;

        if (Math.abs(dx) > Math.abs(dy)) hunterX += Integer.signum(dx);
        else if (Math.abs(dy) > 0) hunterY += Integer.signum(dy);
        else hunterX += Integer.signum(dx);

        if (playerX == hunterX && playerY == hunterY) gameOver("Der Hunter hat dich erwischt!");
        repaint();
    }

    private void gameOver(String message) {
        if (gameTimer != null) gameTimer.stop();
        if (hunterTimer != null) hunterTimer.stop();
        int result = JOptionPane.showOptionDialog(this, message + " Möchtest du neu starten?",
                "Game Over", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null, null, null);
        if (result == JOptionPane.YES_OPTION) {
            parentFrame.getContentPane().removeAll();
            parentFrame.getContentPane().add(new MainMenu(parentFrame));
            parentFrame.pack();
        } else System.exit(0);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int drawX = c * TILE_SIZE;
                int drawY = r * TILE_SIZE;

                double dist = Math.sqrt((r - playerY)*(r - playerY) + (c - playerX)*(c - playerX));
                if ((visionRadius >= 0) && (dist > visionRadius)) {
                    g2.setColor(new Color(30,30,30));
                    g2.fillRect(drawX, drawY, TILE_SIZE, TILE_SIZE);
                    continue;
                }

                drawBlock(g2, drawX, drawY, board[r][c]);
            }
        }

        // Spieler
        g2.setColor(new Color(255,165,0,180));
        g2.fillOval(playerX*TILE_SIZE+10, playerY*TILE_SIZE+10, TILE_SIZE-20, TILE_SIZE-20);

        // Cavehunter Fog of War
        if(hunterMode || cavehunterMode){
            boolean visible = true;
            if(cavehunterMode && visionRadius >= 0){
                double dist = Math.sqrt((hunterY - playerY)*(hunterY - playerY) + (hunterX - playerX)*(hunterX - playerX));
                visible = dist <= visionRadius;
            }
            if(visible){
                g2.setColor(Color.RED);
                g2.fillOval(hunterX*TILE_SIZE+12, hunterY*TILE_SIZE+12, TILE_SIZE-24, TILE_SIZE-24);
            }
        }

        // Timer
        int minutes = secondsElapsed/60;
        int seconds = secondsElapsed%60;
        String timeStr = String.format("%02d:%02d", minutes, seconds);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 20));
        g2.drawString(timeStr, 10, 25);
    }

    private void drawBlock(Graphics2D g2, int x, int y, Tile t) {
        Color color = t.revealed ? new Color(200,200,255) : new Color(100,100,150);
        g2.setColor(color);
        g2.fillRect(x,y,TILE_SIZE,TILE_SIZE);
        g2.setColor(Color.BLACK);
        g2.drawRect(x,y,TILE_SIZE,TILE_SIZE);

        if(t.revealed){
            if(t.isMine){
                g2.setColor(Color.RED);
                g2.fillOval(x+10, y+10, TILE_SIZE-20, TILE_SIZE-20);
            } else if(t.adjacentMines>0){
                g2.setColor(numberColors[Math.min(t.adjacentMines-1, numberColors.length-1)]);
                g2.setFont(new Font("Arial",Font.BOLD,18));
                g2.drawString(""+t.adjacentMines, x+TILE_SIZE/2-5, y+TILE_SIZE/2+6);
            }
        }

        if(t.flagged){
            g2.setColor(new Color(255,0,0,200));
            g2.fillRect(x+TILE_SIZE/2-3, y+5, 6, 20);
            g2.fillPolygon(new int[]{x+TILE_SIZE/2, x+TILE_SIZE/2, x+TILE_SIZE/2+12},
                    new int[]{y+5, y+15, y+10}, 3);
        }
    }

    private void revealTile(int r, int c){
        if(r<0||r>=ROWS||c<0||c>=COLS) return;
        Tile t = board[r][c];
        if(t.revealed || t.flagged) return;

        if(firstClick){
            firstClick = false;
            gameTimer.start();
            if(t.isMine) t.isMine = false;
            recalcAdjacentMines();
            floodRevealFirstClick(r, c);
        } else {
            if(t.isMine){
                gameOver("Du bist auf eine Mine gestoßen!");
            } else {
                floodReveal(r,c);
            }
        }
    }

    private void floodRevealFirstClick(int r, int c){
        for(int dr=-1; dr<=1; dr++){
            for(int dc=-1; dc<=1; dc++){
                int nr = r+dr, nc = c+dc;
                if(nr>=0 && nr<ROWS && nc>=0 && nc<COLS){
                    floodReveal(nr,nc);
                }
            }
        }
    }

    private void floodReveal(int r, int c){
        Queue<Point> queue = new LinkedList<>();
        queue.add(new Point(r,c));
        boolean[][] visited = new boolean[ROWS][COLS];

        while(!queue.isEmpty()){
            Point p = queue.poll();
            int pr = p.x, pc = p.y;
            if(pr<0||pr>=ROWS||pc<0||pc>=COLS) continue;
            Tile t = board[pr][pc];
            if(visited[pr][pc] || t.revealed || t.isMine) continue;
            t.revealed = true;
            visited[pr][pc] = true;

            if(t.adjacentMines==0){
                for(int dr=-1;dr<=1;dr++)
                    for(int dc=-1;dc<=1;dc++)
                        if(dr!=0||dc!=0) queue.add(new Point(pr+dr,pc+dc));
            }
        }
    }

    private boolean checkWin(){
        for(int r=0;r<ROWS;r++)
            for(int c=0;c<COLS;c++){
                Tile t = board[r][c];
                if(!t.isMine && !t.revealed) return false;
            }
        return true;
    }

    @Override
    public void keyPressed(KeyEvent e){
        switch(e.getKeyCode()){
            case KeyEvent.VK_LEFT -> playerX=Math.max(0,playerX-1);
            case KeyEvent.VK_RIGHT -> playerX=Math.min(COLS-1,playerX+1);
            case KeyEvent.VK_UP -> playerY=Math.max(0,playerY-1);
            case KeyEvent.VK_DOWN -> playerY=Math.min(ROWS-1,playerY+1);
            case KeyEvent.VK_SPACE -> {
                revealTile(playerY,playerX);
                if(checkWin()){
                    if(gameTimer != null) gameTimer.stop();
                    if(hunterTimer != null) hunterTimer.stop();
                    int result = JOptionPane.showOptionDialog(this,"Gewonnen! Möchtest du neu starten?","Gewonnen",
                            JOptionPane.YES_NO_OPTION,JOptionPane.INFORMATION_MESSAGE,null,null,null);
                    if(result==JOptionPane.YES_OPTION){
                        parentFrame.getContentPane().removeAll();
                        parentFrame.getContentPane().add(new MainMenu(parentFrame));
                        parentFrame.pack();
                    } else System.exit(0);
                }
            }
            case KeyEvent.VK_F -> {
                Tile t = board[playerY][playerX];
                if(!t.revealed) t.flagged=!t.flagged;
            }
            case KeyEvent.VK_ESCAPE -> {
            // Stoppe Timer
            if(gameTimer != null) gameTimer.stop();
            if(hunterTimer != null) hunterTimer.stop();
            // Zurück zum Menü
            parentFrame.getContentPane().removeAll();
            parentFrame.getContentPane().add(new MainMenu(parentFrame));
            parentFrame.pack();
        }
        }
        repaint();
    }

    @Override public void keyReleased(KeyEvent e){}
    @Override public void keyTyped(KeyEvent e){}
    @Override public void actionPerformed(ActionEvent e){
        flagPulse += 0.2;
        repaint();
    }

    private static class Tile{
        boolean isMine;
        boolean revealed=false;
        boolean flagged=false;
        int adjacentMines=0;
        Tile(boolean mine){ this.isMine = mine; }
    }

    public static class MainMenu extends JPanel{
        public MainMenu(JFrame frame){
            setLayout(new GridBagLayout());
            setBackground(new Color(50,50,50));
            setPreferredSize(new Dimension(400, 500));
            GridBagConstraints gbc=new GridBagConstraints();
            gbc.insets=new Insets(10,10,10,10);

            JLabel title=new JLabel("Swooper");
            title.setFont(new Font("Arial",Font.BOLD,36));
            title.setForeground(Color.WHITE);
            gbc.gridx=0; gbc.gridy=0;
            add(title,gbc);

            JButton easy=new JButton("Einfach");
            easy.setPreferredSize(new Dimension(200,50));
            easy.addActionListener(e->startGame(frame,8,8,0.10,-1,false,false));
            gbc.gridy=1; add(easy,gbc);

            JButton medium=new JButton("Mittel");
            medium.setPreferredSize(new Dimension(200,50));
            medium.addActionListener(e->startGame(frame,12,12,0.15,-1,false,false));
            gbc.gridy=2; add(medium,gbc);

            JButton hard=new JButton("Schwer");
            hard.setPreferredSize(new Dimension(200,50));
            hard.addActionListener(e->startGame(frame,16,16,0.20,-1,false,false));
            gbc.gridy=3; add(hard,gbc);

            JButton cavemode=new JButton("Cave Modus");
            cavemode.setPreferredSize(new Dimension(200,50));
            cavemode.addActionListener(e->startGame(frame,24,24,0.25,3,false,false));
            gbc.gridy=4; add(cavemode,gbc);

            JButton hunter=new JButton("Hunter Modus");
            hunter.setPreferredSize(new Dimension(200,50));
            hunter.addActionListener(e->startGame(frame,16,16,0.20,-1,true,false));
            gbc.gridy=5; add(hunter,gbc);

            JButton cavehunter=new JButton("Cavehunter Modus");
            cavehunter.setPreferredSize(new Dimension(200,50));
            cavehunter.addActionListener(e->startGame(frame,24,24,0.25,3,true,true));
            gbc.gridy=6; add(cavehunter,gbc);
        }

        private void startGame(JFrame frame,int rows,int cols,double mineChance,int visionRadius, boolean hunter, boolean cavehunter){
            Swooper gamePanel=new Swooper(rows,cols,mineChance,frame,visionRadius,hunter,cavehunter);
            frame.getContentPane().removeAll();
            frame.getContentPane().add(gamePanel);
            frame.pack();
            gamePanel.requestFocusInWindow();
        }
    }

    public static void main(String[] args){
        JFrame frame=new JFrame("Swooper");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new MainMenu(frame));
        frame.pack();
        frame.setVisible(true);
    }

    
}
