// StarListDialog.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import util.swing.HBox;
import util.swing.ModalDialog;
import util.swing.VBox;

/** Let the user choose which stars to use. */
public class StarListDialog extends ModalDialog {
    /** AWT boilerplate generated ID. */
    private static final long serialVersionUID = -1013374817853839733L;

    // Final set of choices.
    public LinkedHashMap<String, Boolean> stars = new LinkedHashMap<String, Boolean>();

    // Widgets we need to query at the end.
    public LinkedHashMap<String, JCheckBox> checkboxes = new LinkedHashMap<String, JCheckBox>();

    public StarListDialog(JFrame parent, LinkedHashMap<String, Boolean> stars_)
    {
        super(parent, "Choose stars");

        VBox vb = new VBox();
        vb.strut();

        this.makeLabel(vb, "Choose which stars to use:");

        for (Map.Entry<String, Boolean> e : stars_.entrySet())  {
            String k = e.getKey();
            Boolean v = e.getValue();
            JCheckBox cb = this.makeCheckBox(vb, k, v);
            this.checkboxes.put(k, cb);
        }

        vb.strut();
        vb.strut();

        HBox hb3 = new HBox();
        {
            hb3.glue();
            hb3.add(this.makeCancelButton());
            hb3.strut();
            hb3.add(this.makeOKButton());
            hb3.glue();
        }
        vb.add(hb3);
        vb.strut();

        this.finishDialogWithVBox(vb);
    }

    public void okPressed()
    {
        for (Map.Entry<String, JCheckBox> e : this.checkboxes.entrySet())  {
            String k = e.getKey();
            JCheckBox cb = e.getValue();
            this.stars.put(k, cb.isSelected());
        }

        super.okPressed();
    }

    private static void testDialog()
    {
        LinkedHashMap<String, Boolean> stars = new LinkedHashMap<String, Boolean>();
        stars.put("A", true);
        stars.put("B", false);
        stars.put("C", true);

        StarListDialog d = new StarListDialog(null /*parent*/, stars);
        if (d.exec()) {
            for (Map.Entry<String, Boolean> e : d.stars.entrySet()) {
                System.out.println(e.getKey()+": "+e.getValue());
            }
        }
        else {
            System.out.println("Canceled.");
        }
    }

    /** Test this one dialog. */
    public static void main(String[] args)
    {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                testDialog();
            }
        });
    }
}

// EOF
