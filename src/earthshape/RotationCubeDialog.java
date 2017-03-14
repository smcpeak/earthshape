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

    /** Original 3D cube of data to visualize. */
    private PlotData3D rollPitchYawPlotData;

    /** The current X index, from which the slices are derived. */
    private int currentXIndex;

    /** Derived 1D slice of roll data. */
    private PlotData1D rollPlotData;

    /** Derived 1D slice of pitch data. */
    private PlotData1D pitchPlotData;

    /** Derived 1D slice of yaw data. */
    private PlotData1D yawPlotData;

    /** Derived 2D slice of pitch+yaw data. */
    private PlotData2D pitchYawPlotData;

    public RotationCubeDialog(JFrame parent,
        float currentVariance,
        PlotData3D rollPitchYawPlotData_)
    {
        super(parent, "Rotation Cube");

        this.rollPitchYawPlotData = rollPitchYawPlotData_;

        // The middle index should correspond to 0 rotation, and that is
        // the slice I want to start with.
        this.currentXIndex = this.rollPitchYawPlotData.xValuesPerRow / 2;

        this.computeSlices();

        VBox vb = new VBox();
        vb.strut();

        {
            HBox hb = new HBox();
            hb.add(new JLabel("Current variance: "+currentVariance));
            hb.glue();
            vb.add(hb);
        }
        vb.strut();

        {
            HBox innerHB = new HBox();

            {
                VBox col = new VBox();
                this.addPlot1D(col, "roll", this.rollPlotData);
                this.addPlot1D(col, "pitch", this.pitchPlotData);
                this.addPlot1D(col, "yaw", this.yawPlotData);
                innerHB.add(col);
            }

            innerHB.strut();

            {
                VBox col = new VBox();
                this.addPlot2D(col, "pitch and yaw", this.pitchYawPlotData);
                innerHB.add(col);
            }

            vb.add(innerHB);
        }

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

    /** Construct widgets to show 1D 'plotData'. */
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

    /** Construct widgets to show 2D 'plotData'. */
    private void addPlot2D(VBox vb, String axesDescription, PlotData2D plotData)
    {
        {
            HBox hb = new HBox();
            hb.add(new JLabel("Variance versus "+axesDescription+" angle degrees:"));
            hb.glue();
            vb.add(hb);
        }

        vb.add(new PlotPanel2D(plotData));
        vb.strut();
    }

    /** Given the 3D data and the current X index, compute the
      * various slices of data that the widgets can display. */
    private void computeSlices()
    {
        int midY = this.rollPitchYawPlotData.yValuesPerColumn / 2;
        int midZ = this.rollPitchYawPlotData.zValuesPerTower() / 2;

        this.rollPlotData = this.rollPitchYawPlotData.getXSlice(midY, midZ);
        this.pitchPlotData = this.rollPitchYawPlotData.getYSlice(this.currentXIndex, midZ);
        this.yawPlotData = this.rollPitchYawPlotData.getZSlice(this.currentXIndex, midY);

        this.pitchYawPlotData = this.rollPitchYawPlotData.getYZSlice(this.currentXIndex);
    }
}

// EOF
