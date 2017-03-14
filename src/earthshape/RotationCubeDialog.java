// RotationCubeDialog.java
// See copyright.txt for license and terms of use.

package earthshape;

import javax.swing.JFrame;
import javax.swing.JLabel;

import util.swing.HBox;
import util.swing.ModalDialog;
import util.swing.VBox;

/** This dialog shows a 3D cube of results representing the variance
  * of the active square after various amounts of roll, pitch, and
  * yaw rotation are applied.  That is, this visualizes a function
  * from 3 real inputs to one real output. */
public class RotationCubeDialog extends ModalDialog {
    /** AWT boilerplate generated ID. */
    private static final long serialVersionUID = 4473449078815404630L;

    public RotationCubeDialog(JFrame parent,
        float currentVariance,
        PlotData1D rollPlotData,
        PlotData1D pitchPlotData,
        PlotData1D yawPlotData)
    {
        super(parent, "Rotation Cube");

        VBox vb = new VBox();
        vb.strut();

        {
            HBox hb = new HBox();
            hb.add(new JLabel("Current variance: "+currentVariance));
            hb.glue();
            vb.add(hb);
        }
        vb.strut();

        this.addPlot1D(vb, "roll", rollPlotData);
        this.addPlot1D(vb, "pitch", pitchPlotData);
        this.addPlot1D(vb, "yaw", yawPlotData);

        HBox hb3 = new HBox();
        {
            hb3.glue();
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

        this.pack();
        this.setLocationByPlatform(true);
    }

    /** Construct widgets to show 'plotData'. */
    private void addPlot1D(VBox vb, String axisDescription, PlotData1D plotData)
    {
        {
            HBox hb = new HBox();
            hb.add(new JLabel("Variance versus "+axisDescription+" angle degrees:"));
            hb.glue();
            vb.add(hb);
        }

        vb.add(new PlotPanel1D(plotData));
        vb.strut();
    }
}

// EOF
