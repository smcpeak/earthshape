// CurvatureCalculatorDialog.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import util.FloatUtil;
import util.swing.HBox;
import util.swing.ModalDialog;
import util.swing.VBox;

/** Show a dialog that allows one to interactively calculate the
  * average surface curvature between two points, given the sky
  * positions of two stars at each location. */
public class CurvatureCalculatorDialog extends ModalDialog {
    /** Generated ID. */
    private static final long serialVersionUID = 1776587184367621482L;

    // Text fields for all of the inputs.
    private JTextField start_A_az_tf;
    private JTextField start_A_el_tf;
    private JTextField start_B_az_tf;
    private JTextField start_B_el_tf;
    private JTextField end_A_az_tf;
    private JTextField end_A_el_tf;
    private JTextField end_B_az_tf;
    private JTextField end_B_el_tf;
    private JTextField heading_tf;
    private JTextField distance_tf;

    private JCheckBox showStepsCB;

    /** Uneditable output text. */
    private JTextArea text;

    /** Scroller for the text. */
    private JScrollPane scrollPane;

    public CurvatureCalculatorDialog(JFrame parent)
    {
        super(parent, "Curvature Calculator");

        VBox vb = new VBox();
        vb.add(Box.createHorizontalStrut(600));
        vb.strut();

        // The initial values are for observations of Dubhe and Sirius
        // from 38N,122W and 38N,113W at 2017-03-05 20:00 -08:00.  The
        // data is accurate to about 0.2 degrees.
        this.start_A_az_tf = this.makeTextField(vb, "Start square star A azimuth (degrees East of North)",
            "36.9");
        this.start_A_el_tf = this.makeTextField(vb, "Start square star A elevation (degrees above horizon)",
            "44.8");
        this.start_B_az_tf = this.makeTextField(vb, "Start square star B azimuth (degrees East of North)",
            "181.1");
        this.start_B_el_tf = this.makeTextField(vb, "Start square star B elevation (degrees above horizon)",
            "35.2");
        this.end_A_az_tf = this.makeTextField(vb, "End square star A azimuth (degrees East of North)",
            "36.4");
        this.end_A_el_tf = this.makeTextField(vb, "End square star A elevation (degrees above horizon)",
            "49.1");
        this.end_B_az_tf = this.makeTextField(vb, "End square star B azimuth (degrees East of North)",
            "191.5");
        this.end_B_el_tf = this.makeTextField(vb, "End square star B elevation (degrees above horizon)",
            "34.4");

        float startLatitude = 38;
        float startLongitude = -122;
        float endLatitude = 38;
        float endLongitude = -113;
        double startToEndHeading = FloatUtil.getLatLongPairHeading(
            startLatitude, startLongitude, endLatitude, endLongitude);
        this.heading_tf = this.makeTextField(vb, "Start to end travel heading (degrees East of North)",
            String.format("%.3g", startToEndHeading));

        double arcAngleDegrees = FloatUtil.sphericalSeparationAngle(
            startLongitude, startLatitude,
            endLongitude, endLatitude);
        float distanceKm =
            (float)(arcAngleDegrees / 180.0 * Math.PI * RealWorldObservations.EARTH_RADIUS_KM);
        this.distance_tf = this.makeTextField(vb, "Start to end distance (km)",
            String.format("%.3g", distanceKm));

        this.showStepsCB = this.makeCheckBox(vb, "Show calculation steps", false);
        this.showStepsCB.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                CurvatureCalculatorDialog.this.calculate();
            }
        });
        vb.strut();

        // Output area.
        this.text = new JTextArea("No info set");
        this.text.setFocusable(false);
        this.text.setEditable(false);
        this.text.setFont(this.text.getFont().deriveFont(20.0f));

        // Make the text scrollable.
        this.scrollPane = new JScrollPane(this.text);
        this.scrollPane.setPreferredSize(new Dimension(600, 300));
        vb.add(this.scrollPane);

        vb.strut();
        {
            HBox hb = new HBox();
            hb.glue();

            JButton calculateButton = this.makeButton("Calculate", new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    CurvatureCalculatorDialog.this.calculate();
                }
            });
            hb.add(calculateButton);
            this.getRootPane().setDefaultButton(calculateButton);

            hb.strut();

            hb.add(this.makeButton("Close", new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    CurvatureCalculatorDialog.this.okPressed();
                }
            }));

            hb.glue();
            vb.add(hb);
        }
        vb.strut();

        this.calculate();

        this.finishDialogWithVBox(vb);
    }

    private static float parseFloat(String s)
    {
        try {
            return Float.valueOf(s);
        }
        catch (NumberFormatException e) {
            throw new RuntimeException("Cannot parse value \""+s+"\" as a floating-point value.");
        }
    }

    /** Run the curvature calculation and show the results. */
    private void calculate()
    {
        try {
            StringBuilder sb = this.innerCalculate();
            this.text.setText(sb.toString());
        }
        catch (Exception e) {
            //e.printStackTrace();
            this.text.setText("Exception: "+e.getMessage());
        }
    }

    private StringBuilder innerCalculate()
    {
        StringBuilder sb = new StringBuilder();

        CurvatureCalculator c = new CurvatureCalculator();

        // Parse all the inputs.
        c.start_A_az = parseFloat(this.start_A_az_tf.getText());
        c.start_A_el = parseFloat(this.start_A_el_tf.getText());
        c.start_B_az = parseFloat(this.start_B_az_tf.getText());
        c.start_B_el = parseFloat(this.start_B_el_tf.getText());
        c.end_A_az = parseFloat(this.end_A_az_tf.getText());
        c.end_A_el = parseFloat(this.end_A_el_tf.getText());
        c.end_B_az = parseFloat(this.end_B_az_tf.getText());
        c.end_B_el = parseFloat(this.end_B_el_tf.getText());
        c.heading = parseFloat(this.heading_tf.getText());
        c.distanceKm = parseFloat(this.distance_tf.getText());

        c.calculate();

        if (this.showStepsCB.isSelected()) {
            for (String s : c.steps) {
                sb.append(s+"\n");
            }
        }

        for (String w : c.warnings) {
            sb.append(w+"\n");
        }
        sb.append("Deviation of rotated B (deg): "+c.deviationBDegrees+"\n");
        sb.append("Curvature: "+c.curvature+" km^-1\n");
        if (c.curvature == 0) {
            sb.append("Radius: Infinite\n");
        }
        else {
            sb.append("Radius of curvature: "+(1/c.curvature)+" km\n");
        }
        sb.append("Twist rate: "+(c.twistRate*1000)+" deg per 1000 km\n");

        return sb;
    }

    /** Test this one dialog. */
    public static void main(String[] args)
    {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                (new CurvatureCalculatorDialog(null /*parent*/)).exec();
            }
        });
    }
}
