// InfoPanel.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

/** Shows information about the selected square. */
public class InfoPanel extends JPanel {
    /** AWT boilerplate generated ID. */
    private static final long serialVersionUID = -2754637681249934440L;

    /** For now, all info is in a single multi-line text area that
      * cannot be edited. */
    private JTextArea text;

    /** Scroller for the text. */
    private JScrollPane scrollPane;

    public InfoPanel()
    {
        super();
        this.setName("InfoPanel");

        this.setLayout(new BorderLayout());

        // Make the text area, but do not let it get focus since
        // I want the canvas key bindings to keep working.
        this.text = new JTextArea("No info set");
        this.text.setFocusable(false);
        this.text.setEditable(false);
        this.text.setFont(this.text.getFont().deriveFont(20.0f));

        // Make the text scrollable.
        this.scrollPane = new JScrollPane(this.text);
        this.scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        this.scrollPane.setPreferredSize(new Dimension(350, 500));
        this.add(this.scrollPane, BorderLayout.CENTER);
    }

    /** Update the main info panel text. */
    public void setText(String text)
    {
        this.text.setText(text);
        this.text.setCaretPosition(0);
    }
}
