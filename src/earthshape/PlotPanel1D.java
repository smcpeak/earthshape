// PlotPanel1D.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.geom.Rectangle2D;

import javax.swing.JPanel;

import util.FloatUtil;

/** Visualize a function from int to real (one dimension) as a
  * line plot. */
public class PlotPanel1D extends JPanel {
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

    /** Data points.  The independent variable is the array index. */
    private float[] data;

    /** Minimum value in 'data'. */
    private float dataMin;

    /** Maximum value in 'data'. */
    private float dataMax;

    public PlotPanel1D(float[] data_)
    {
        this.setPreferredSize(new Dimension(400, 200));
        this.setBackground(Color.WHITE);

        this.data = data_;
        this.dataMin = FloatUtil.minimumOfArray(this.data);
        this.dataMax = FloatUtil.maximumOfArray(this.data);

        // Expand the ranges to avoid hitting the plot edges.
        double yRange = this.dataMax - this.dataMin;
        if (yRange == 0) {
            yRange = 1;    // Just use something non-zero.
        }
        this.dataMax += yRange * 0.05f;
        this.dataMin -= yRange * 0.05f;
    }

    /** The X coordinate, in pixels from the top of the panel,
      * at which to draw data value 'x'. */
    private int plotX(double x)
    {
        // Distance from left of plot area as a fraction of
        // the total plot width.
        double xFraction = (double)x / (double)(data.length - 1);

        return LEFT_MARGIN + (int)((this.getWidth() - LEFT_MARGIN) * xFraction);
    }

    /** The Y coordinate, in pixels from the top of the panel,
      * at which to draw data value 'y'. */
    private int plotY(double y)
    {
        // Distance from bottom of plot area as a fraction of
        // the total plot height.
        double yFraction = (y - this.dataMin) / (this.dataMax - this.dataMin);

        return (int)((this.getHeight() - 1 - BOTTOM_MARGIN) * (1.0 - yFraction));
    }

    /** Draw tick marks and labels along the Y axis every 'tickSpace'
      * units.  Only draw labels if 'labels' is true. */
    private void drawYAxisLabels(Graphics g, double tickSpace, int tickLength, boolean labels)
    {
        if (tickSpace > 0) {
            double y = Math.floor(this.dataMax / tickSpace) * tickSpace;
            while (y > this.dataMin) {
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

        if (data.length < 1) {
            g.drawString("Insufficient data", 20, 20);
            return;
        }

        if (this.dataMax == this.dataMin) {
            g.drawString("All values are: "+this.dataMax, 20, 20);
            return;
        }

        // Core plot area.
        int plotTop = 0;
        int plotBottom = this.getHeight()-1 - BOTTOM_MARGIN;
        int plotLeft = LEFT_MARGIN;
        int plotRight = this.getWidth()-1;

        // Decide on a good label spacing.
        double yRange = (this.dataMax - this.dataMin);
        double majorTickSpace = Math.pow(10, Math.floor(Math.log10(yRange)));
        double minorTickSpace = majorTickSpace / 10.0f;

        // Draw Y axis ticks and labels.
        this.drawYAxisLabels(g, majorTickSpace, MAJOR_TICK_LENGTH, true /*labels*/);
        this.drawYAxisLabels(g, minorTickSpace, MINOR_TICK_LENGTH, false /*labels*/);

        // Border.
        g.drawRect(plotLeft, plotTop, plotRight - plotLeft, plotBottom - plotTop);

        // Line segments connecting adjacent pairs of points.
        int prevX = 0;
        int prevY = 0;
        for (int x=0; x < data.length; x++) {
            float y = this.data[x];

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
