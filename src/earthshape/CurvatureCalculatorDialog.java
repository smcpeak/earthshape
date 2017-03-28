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
    private JTextField start_travel_heading_tf;
    private JTextField end_travel_heading_tf;
    private JTextField distance_tf;

    private JCheckBox showStepsCB;

    /** Uneditable output text. */
    private JTextArea text;

    /** Scroller for the text. */
    private JScrollPane scrollPane;

    public CurvatureCalculatorDialog(JFrame parent, CurvatureCalculator initValues)
    {
        super(parent, "Curvature Calculator");

        if (initValues == null) {
            initValues = CurvatureCalculator.getDubheSirius();
        }

        VBox vb = new VBox();
        vb.add(Box.createHorizontalStrut(600));
        vb.strut();

        this.start_A_az_tf = this.makeTextField(vb, "Start square star A azimuth (degrees East of North)",
            ""+initValues.start_A_az);
        this.start_A_el_tf = this.makeTextField(vb, "Start square star A elevation (degrees above horizon)",
            ""+initValues.start_A_el);
        this.start_B_az_tf = this.makeTextField(vb, "Start square star B azimuth (degrees East of North)",
            ""+initValues.start_B_az);
        this.start_B_el_tf = this.makeTextField(vb, "Start square star B elevation (degrees above horizon)",
            ""+initValues.start_B_el);
        this.end_A_az_tf = this.makeTextField(vb, "End square star A azimuth (degrees East of North)",
            ""+initValues.end_A_az);
        this.end_A_el_tf = this.makeTextField(vb, "End square star A elevation (degrees above horizon)",
            ""+initValues.end_A_el);
        this.end_B_az_tf = this.makeTextField(vb, "End square star B azimuth (degrees East of North)",
            ""+initValues.end_B_az);
        this.end_B_el_tf = this.makeTextField(vb, "End square star B elevation (degrees above horizon)",
            ""+initValues.end_B_el);

        this.start_travel_heading_tf = this.makeTextField(vb, "Travel heading at start (degrees East of North)",
            ""+initValues.startTravelHeading);
        this.end_travel_heading_tf = this.makeTextField(vb, "Travel heading at end (degrees East of North)",
            ""+initValues.endTravelHeading);

        this.distance_tf = this.makeTextField(vb, "Start to end distance (km)",
            ""+initValues.distanceKm);

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
        c.startTravelHeading = parseFloat(this.start_travel_heading_tf.getText());
        c.endTravelHeading = parseFloat(this.end_travel_heading_tf.getText());
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
        double normalCurvatureDegPer1000km = FloatUtil.radiansToDegrees(c.normalCurvature*1000);
        sb.append("Normal curvature: "+normalCurvatureDegPer1000km+" deg per 1000 km\n");
        if (c.normalCurvature == 0) {
            sb.append("Radius of normal curvature: Infinite\n");
        }
        else {
            sb.append("Radius of normal curvature: "+(1/c.normalCurvature)+" km\n");
        }
        sb.append("Geodesic curvature: "+(c.geodesicCurvature*1000)+" deg per 1000 km\n");
        sb.append("Geodesic torsion: "+(c.geodesicTorsion*1000)+" deg per 1000 km\n");

        return sb;
    }

    /** Test this one dialog. */
    public static void main(String[] args)
    {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                (new CurvatureCalculatorDialog(null /*parent*/, null /*initValues*/)).exec();
            }
        });
    }
}
