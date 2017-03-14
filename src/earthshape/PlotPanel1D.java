// PlotPanel1D.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.geom.Rectangle2D;

import javax.swing.JPanel;

/** Draw the data in a PlotData1D object as a JPanel. */
public class PlotPanel1D extends JPanel {
    // ---- Constants ----
    /** AWT boilerplate. */
    private static final long serialVersionUID = -6657389271092560523L;

    /** Pixels to the left of the main plot area where the Y axis
      * labels will go. */
    private static final int LEFT_MARGIN = 50;

    /** Pixels to the bottom of the main plot area where the X axis
      * labels will go. */
    private static final int BOTTOM_MARGIN = 50;

    /** Pixel length of major tick marks. */
    private static final int MAJOR_TICK_LENGTH = 5;

    /** Pixel length of minor tick marks. */
    private static final int MINOR_TICK_LENGTH = 2;

    /** Pixels between right edge of a Y axis label and its
      * associated tick mark. */
    private static final int Y_AXIS_LABEL_PADDING = 3;

    // ---- Instance data ----
    /** Data and plot options. */
    private PlotData1D plotData;

    // ---- Methods ----
    public PlotPanel1D(PlotData1D plotData_)
    {
        this.setPreferredSize(new Dimension(400, 200));
        this.setBackground(Color.WHITE);

        this.plotData = plotData_;
    }

    /** The X coordinate, in pixels from the top of the panel,
      * at which to draw data value 'x'. */
    private int plotX(double x)
    {
        // Distance from left of plot area as a fraction of
        // the total plot width.
        double xFraction = (double)x / (double)(this.plotData.data.length - 1);

        return LEFT_MARGIN + (int)((this.getWidth() - LEFT_MARGIN) * xFraction);
    }

    /** The Y coordinate, in pixels from the top of the panel,
      * at which to draw data value 'y'. */
    private int plotY(double y)
    {
        // Distance from bottom of plot area as a fraction of
        // the total plot height.
        double yFraction = (y - this.plotData.dataMin) /
                           (this.plotData.dataMax - this.plotData.dataMin);

        return (int)((this.getHeight() - 1 - BOTTOM_MARGIN) * (1.0 - yFraction));
    }

    /** Draw tick marks and labels along the Y axis every 'tickSpace'
      * units.  Only draw labels if 'labels' is true. */
    private void drawYAxisLabels(Graphics g, double tickSpace, int tickLength, boolean labels)
    {
        if (tickSpace > 0) {
            double y = Math.floor(this.plotData.dataMax / tickSpace) * tickSpace;
            while (y > this.plotData.dataMin) {
                int py = plotY(y);
                g.drawLine(LEFT_MARGIN - tickLength, py,
                           LEFT_MARGIN, py);
                if (labels) {
                    String label = ""+y;
                    Rectangle2D rect = g.getFontMetrics().getStringBounds(label, g);

                    int dx = (int)(LEFT_MARGIN - tickLength - Y_AXIS_LABEL_PADDING
                                     - rect.getWidth() - rect.getX());
                    int dy = (int)(py - rect.getHeight()/2 - rect.getY());

                    g.drawString(label, dx, dy);
                }
                y -= tickSpace;
            }
        }
    }

    @Override
    public void paint(Graphics g)
    {
        super.paint(g);

        if (this.plotData.data.length < 1) {
            g.drawString("Insufficient data", 20, 20);
            return;
        }

        if (this.plotData.dataMax <= this.plotData.dataMin) {
            g.drawString("Y axis range is degenerate.", 20, 20);
            return;
        }

        // Core plot area.
        int plotTop = 0;
        int plotBottom = this.getHeight()-1 - BOTTOM_MARGIN;
        int plotLeft = LEFT_MARGIN;
        int plotRight = this.getWidth()-1;

        // Draw Y axis ticks and labels.
        this.drawYAxisLabels(g, this.plotData.majorYTickSpace, MAJOR_TICK_LENGTH, true /*labels*/);
        this.drawYAxisLabels(g, this.plotData.minorYTickSpace, MINOR_TICK_LENGTH, false /*labels*/);

        // Border.
        g.drawRect(plotLeft, plotTop, plotRight - plotLeft, plotBottom - plotTop);

        // Line segments connecting adjacent pairs of points.
        int prevX = 0;
        int prevY = 0;
        for (int x=0; x < this.plotData.data.length; x++) {
            float y = this.plotData.data[x];

            int px = plotX(x);
            int py = plotY(y);

            if (x > 0) {
                g.drawLine(prevX, prevY, px, py);
            }
            prevX = px;
            prevY = py;
        }
    }
}

// EOF
