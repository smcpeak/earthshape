// InfoPanel.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.awt.Dimension;

import javax.swing.JPanel;
import javax.swing.JTextArea;

import util.swing.HBox;
import util.swing.VBox;

/** Shows information about the selected square. */
public class InfoPanel extends JPanel {
    /** AWT boilerplate generated ID. */
    private static final long serialVersionUID = -2754637681249934440L;

    /** For now, all info is in a single multi-line text area that
      * cannot be edited. */
    public JTextArea text;

    public InfoPanel()
    {
        super();
        this.setName("InfoPanel");

        this.setPreferredSize(new Dimension(350, 500));

        HBox outer = new HBox();
        this.add(outer);

        outer.strut();

        VBox vb = new VBox();
        outer.add(vb);

        this.text = new JTextArea("No info set");
        this.text.setFocusable(false);
        this.text.setFont(this.text.getFont().deriveFont(20.0f));
        vb.add(this.text);

        outer.strut();
    }
}
