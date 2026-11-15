import java.awt.*;
import java.awt.event.*;

public class DrawPadOverrideError extends Frame {
    public DrawPadOverrideError() {
        addMouseListener(new MouseAdapter() {
            @Override public void mouseclicked(MouseEvent e) { }   // misspelled on purpose
        });
    }
}
