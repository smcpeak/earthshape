// LatLongDialog.java
// See copyright.txt for license and terms of use.

package earthshape;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

import util.swing.HBox;
import util.swing.ModalDialog;
import util.swing.VBox;

/** Prompt for a latitude and longitude. */
public class LatLongDialog extends ModalDialog {
    /** AWT boilerplate generated ID. */
    private static final long serialVersionUID = -7849633617913528235L;

    // Chosen values if Ok pressed.
    public float finalLatitude;
    public float finalLongitude;

    // Widgets we need to query at the end.
    JTextField latitudeTextField;
    JTextField longitudeTextField;

    public LatLongDialog(JFrame parent, float initLatitude, float initLongitude)
    {
        super(parent, "Start new surface");

        VBox vb = new VBox();
        vb.strut();

        vb.add(new JLabel("Choose latitude and longitude for start square"));
        vb.strut();

        HBox hb1 = new HBox();
        {
            hb1.add(new JLabel("Latitude (degrees North)"));
            hb1.strut();
            hb1.add(this.latitudeTextField = new JTextField(""+initLatitude));
        }
        vb.add(hb1);
        vb.strut();

        HBox hb2 = new HBox();
        {
            hb2.add(new JLabel("Longitude (degrees East)"));
            hb2.strut();
            hb2.add(this.longitudeTextField = new JTextField(""+initLongitude));
        }
        vb.add(hb2);
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
        try {
            this.finalLatitude = Float.valueOf(this.latitudeTextField.getText());
        }
        catch (NumberFormatException e) {
            errorBox(this, "Invalid latitude value: "+e.getMessage());
            return;
        }

        try {
            this.finalLongitude = Float.valueOf(this.longitudeTextField.getText());
        }
        catch (NumberFormatException e) {
            errorBox(this, "Invalid longitude value: "+e.getMessage());
            return;
        }

        super.okPressed();
    }
}

// EOF
