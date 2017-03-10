// VBox.java
// See copyright.txt for license and terms of use.

package util.swing;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

/** Component with vertical layout.  Meant to be used as part
  * of a hierarchy with HBox. */
public class VBox extends JPanel {
    /** AWT boilerplate generated ID. */
    private static final long serialVersionUID = 9161659269198125061L;

    public VBox()
    {
        super();
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    /** Fixed-size separator. */
    public void strut()
    {
        this.add(Box.createVerticalStrut(10));
    }

    /** Invisible component that expands to fill unneeded space. */
    public void glue()
    {
        this.add(Box.createHorizontalGlue());
    }
}

// EOF
