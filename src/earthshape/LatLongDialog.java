// LatLongDialog.java
// See copyright.txt for license and terms of use.

package earthshape;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

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
        super(parent, "Create new square");

        VBox vb = new VBox();
        vb.strut();

        vb.add(new JLabel("Choose latitude and longitude for start square"));
        vb.strut();

        this.latitudeTextField =
            this.makeTextField(vb, "Latitude (degrees North)", ""+initLatitude);

        this.longitudeTextField =
            this.makeTextField(vb, "Longitude (degrees East)", ""+initLongitude);

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

    /** Test this one dialog. */
    public static void main(String[] args)
    {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                LatLongDialog d = new LatLongDialog(null /*parent*/, 3, -4);
                if (d.exec()) {
                    System.out.println("Ok: "+d.finalLatitude+", "+d.finalLongitude);
                }
                else {
                    System.out.println("Canceled.");
                }
            }
        });
    }
}

// EOF
