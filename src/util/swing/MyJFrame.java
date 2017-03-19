// MyJFrame.java
// See copyright.txt for license and terms of use.

package util.swing;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;

/** Like JFrame, but closes in response to window close button. */
public class MyJFrame extends JFrame {
    /** Generated ID. */
    private static final long serialVersionUID = 7425587687068601846L;

    public MyJFrame(String title)
    {
        super(title);

        // Respond to window close button.
        this.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                MyJFrame.this.dispose();
            }
        });
    }
}

// EOF
