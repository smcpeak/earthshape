// StarListDialog.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;

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

        // The layout of this dialog is screwy and I do not know why.

        vb.add(new JLabel("Choose which stars to use"));
        vb.strut();

        for (Map.Entry<String, Boolean> e : stars_.entrySet())  {
            String k = e.getKey();
            Boolean v = e.getValue();
            JCheckBox cb = new JCheckBox(k, v);
            vb.add(cb);
            this.checkboxes.put(k, cb);
        }

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

        HBox outer = new HBox();
        outer.strut();
        outer.add(vb);
        outer.strut();

        this.getContentPane().add(outer);

        this.finishBuildingDialog();
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
}

// EOF
