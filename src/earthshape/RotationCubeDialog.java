// RotationCubeDialog.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JFrame;
import javax.swing.JLabel;

import util.swing.HBox;
import util.swing.ModalDialog;
import util.swing.VBox;

/** This dialog shows a 3D cube of results representing the variance
  * of the active square after various amounts of roll, pitch, and
  * yaw rotation are applied.  That is, this visualizes a function
  * from 3 real inputs to one real output. */
public class RotationCubeDialog extends ModalDialog implements ItemListener {
    // ---- Constants ----
    /** AWT boilerplate generated ID. */
    private static final long serialVersionUID = 4473449078815404630L;

    // ---- Core plot data ----
    /** Original 3D cube of data to visualize. */
    private PlotData3D rollPitchYawPlotData;

    /** The current X index, from which the slices are derived. */
    private int currentXIndex;

    // ---- Derived slice data ----
    /** Derived 1D slice of roll data. */
    private PlotData1D rollPlotData;

    /** Derived 1D slice of pitch data. */
    private PlotData1D pitchPlotData;

    /** Derived 1D slice of yaw data. */
    private PlotData1D yawPlotData;

    /** Derived 2D slice of pitch+yaw data. */
    private PlotData2D pitchYawPlotData;

    // ---- Widgets ----
    /** Panel holding the plot of the roll data. */
    private PlotPanel1D rollPlotPanel;

    /** Panel holding the plot of the pitch data. */
    private PlotPanel1D pitchPlotPanel;

    /** Panel holding the plot of the yaw data. */
    private PlotPanel1D yawPlotPanel;

    /** Panel holding the plot of the pitch+yaw data. */
    private PlotPanel2D pitchYawPlotPanel;

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

                this.rollPlotPanel = this.addPlot1D(col, "roll", this.rollPlotData);
                this.rollPlotPanel.setSelectedXIndex(this.currentXIndex);
                this.rollPlotPanel.addItemListener(this);

                this.pitchPlotPanel = this.addPlot1D(col, "pitch", this.pitchPlotData);

                this.yawPlotPanel = this.addPlot1D(col, "yaw", this.yawPlotData);

                innerHB.add(col);
            }

            innerHB.strut();

            {
                VBox col = new VBox();
                this.pitchYawPlotPanel =
                    this.addPlot2D(col, "pitch (horizontal) and yaw (vertical)",
                        this.pitchYawPlotData);
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
    private PlotPanel1D addPlot1D(VBox vb, String axisDescription, PlotData1D plotData)
    {
        {
            HBox hb = new HBox();
            hb.add(new JLabel("Variance versus "+axisDescription+" angle degrees:"));
            hb.glue();
            vb.add(hb);
        }

        PlotPanel1D plotPanel = new PlotPanel1D(plotData);
        vb.add(plotPanel);
        vb.strut();

        return plotPanel;
    }

    /** Construct widgets to show 2D 'plotData'. */
    private PlotPanel2D addPlot2D(VBox vb, String axesDescription, PlotData2D plotData)
    {
        {
            HBox hb = new HBox();
            hb.add(new JLabel("Variance versus "+axesDescription+" angle degrees:"));
            hb.glue();
            vb.add(hb);
        }

        PlotPanel2D plotPanel = new PlotPanel2D(plotData);
        vb.add(plotPanel);
        vb.strut();

        return plotPanel;
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

    private void updatePanelData()
    {
        // The roll panel's data should never change, even though I
        // recompute it when I recompute the others.

        this.pitchPlotPanel.setPlotData(this.pitchPlotData);
        this.yawPlotPanel.setPlotData(this.yawPlotData);

        this.pitchYawPlotPanel.setPlotData(this.pitchYawPlotData);
    }

    @Override
    public void itemStateChanged(ItemEvent e)
    {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            this.currentXIndex = this.rollPlotPanel.selectedXIndex.intValue();
            this.computeSlices();
            this.updatePanelData();
            this.repaint();
        }
    }
}

// EOF
