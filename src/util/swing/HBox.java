// HBox.java
// See copyright.txt for license and terms of use.

package util.swing;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

/** This is a container with horizontal layout manager.  It is
  * meant to be used with VBox to build a box hierarchy. */
public class HBox extends JPanel {
    /** AWT boilerplate generated ID. */
    private static final long serialVersionUID = 3429174811485731401L;

    public HBox()
    {
        super();
        this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    }

    /** Fixed-size separator. */
    public void strut()
    {
        this.add(Box.createHorizontalStrut(10));
    }

    /** Invisible component that expands to fill unneeded space. */
    public void glue()
    {
        this.add(Box.createHorizontalGlue());
    }
}

// EOF
