import java.awt.*;
import java.awt.event.*;

public class DrawPadBuggy extends Frame {
    int count = 0;
    Label status = new Label("Clicks: 0");

    public DrawPadBuggy() {
        add(status, BorderLayout.SOUTH);
        addMouseListener(new MouseAdapter() {
            public void mouseclicked(MouseEvent e) {     // BUG: lower-case 'c', never called
                count++;
                Graphics g = getGraphics();
                g.fillOval(e.getX(), e.getY(), 6, 6);
            }
        });
        setSize(400, 300);
        setVisible(true);
    }

    public static void main(String[] args) { new DrawPadBuggy(); }
}
