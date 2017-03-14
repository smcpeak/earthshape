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

    /** Pixels between left edge of panel and left edge of main plot area. */
    private static final int LEFT_MARGIN = 50;

    /** Pixels between right edge of panel and right edge of main plot area. */
    private static final int RIGHT_MARGIN = 20;

    /** Pixels between top edge of panel and top edge of main plot area. */
    private static final int TOP_MARGIN = 10;

    /** Pixels between bottom edge of panel and bottom edge of main plot area. */
    private static final int BOTTOM_MARGIN = 25;

    /** Pixel length of major tick marks. */
    private static final int MAJOR_TICK_LENGTH = 5;

    /** Pixel length of minor tick marks. */
    private static final int MINOR_TICK_LENGTH = 2;

    /** Pixels between top edge of an X axis label and its
      * associated tick mark. */
    private static final int X_AXIS_LABEL_PADDING = 3;

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

    /** X pixel coordinate of the left of the main plot area. */
    private int plotLeft()
    {
        return LEFT_MARGIN;
    }

    /** X pixel coordinate of the right of the main plot area. */
    private int plotRight()
    {
        return this.getWidth()-1 - RIGHT_MARGIN;
    }

    /** Main plot area width. */
    private int plotWidth()
    {
        return plotRight() - plotLeft();
    }

    /** Y pixel coordinate of the top of the main plot area. */
    private int plotTop()
    {
        return TOP_MARGIN;
    }

    /** Y pixel coordinate of the bottom of the main plot area. */
    private int plotBottom()
    {
        return this.getHeight()-1 - BOTTOM_MARGIN;
    }

    /** Main plot area height. */
    private int plotHeight()
    {
        return plotBottom() - plotTop();
    }

    /** The X coordinate, in pixels from the top of the panel,
      * at which to draw data value 'x'. */
    private int plotX(double x)
    {
        // Distance from left of plot area as a fraction of
        // the total plot width.
        double xFraction = (x - this.plotData.xMin) /
                           (this.plotData.xMax - this.plotData.xMin);

        return this.plotLeft() + (int)(this.plotWidth() * xFraction);
    }

    /** The Y coordinate, in pixels from the top of the panel,
      * at which to draw data value 'y'. */
    private int plotY(double y)
    {
        // Distance from bottom of plot area as a fraction of
        // the total plot height.
        double yFraction = (y - this.plotData.yMin) /
                           (this.plotData.yMax - this.plotData.yMin);

        return this.plotTop() + (int)(this.plotHeight() * (1.0 - yFraction));
    }

    /** Draw tick marks and labels along the X axis every 'tickSpace'
      * units.  Only draw labels if 'labels' is true. */
    private void drawXAxisLabels(Graphics g, double tickSpace, int tickLength, boolean labels)
    {
        if (tickSpace > 0) {
            double x = Math.floor(this.plotData.xMax / tickSpace) * tickSpace;
            while (x >= this.plotData.xMin) {
                int px = plotX(x);
                g.drawLine(px, this.plotBottom(),
                           px, this.plotBottom() + tickLength);
                if (labels) {
                    String label = ""+x;
                    Rectangle2D rect = g.getFontMetrics().getStringBounds(label, g);

                    int dx = (int)(px - rect.getWidth()/2 - rect.getX());
                    int dy = (int)(this.plotBottom() + tickLength + X_AXIS_LABEL_PADDING
                                     - rect.getY());

                    g.drawString(label, dx, dy);
                }
                x -= tickSpace;
            }
        }
    }

    /** Draw tick marks and labels along the Y axis every 'tickSpace'
      * units.  Only draw labels if 'labels' is true. */
    private void drawYAxisLabels(Graphics g, double tickSpace, int tickLength, boolean labels)
    {
        if (tickSpace > 0) {
            double y = Math.floor(this.plotData.yMax / tickSpace) * tickSpace;
            while (y >= this.plotData.yMin) {
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

        if (this.plotData.yValues.length < 1) {
            g.drawString("Insufficient data", 20, 20);
            return;
        }

        if (this.plotData.yMax <= this.plotData.yMin) {
            g.drawString("Y axis range is degenerate.", 20, 20);
            return;
        }

        // Draw axis ticks and labels.
        this.drawXAxisLabels(g, this.plotData.xMajorTickSpace, MAJOR_TICK_LENGTH, true /*labels*/);
        this.drawXAxisLabels(g, this.plotData.xMinorTickSpace, MINOR_TICK_LENGTH, false /*labels*/);
        this.drawYAxisLabels(g, this.plotData.yMajorTickSpace, MAJOR_TICK_LENGTH, true /*labels*/);
        this.drawYAxisLabels(g, this.plotData.yMinorTickSpace, MINOR_TICK_LENGTH, false /*labels*/);

        // Border around main plot area.
        g.drawRect(this.plotLeft(), this.plotTop(), this.plotWidth(), this.plotHeight());

        // Line segments connecting adjacent pairs of points.
        int prevX = 0;
        int prevY = 0;
        for (int i=0; i < this.plotData.yValues.length; i++) {
            float x = this.plotData.xValueForIndex(i);
            float y = this.plotData.yValues[i];

            int px = plotX(x);
            int py = plotY(y);

            if (i > 0) {
                g.drawLine(prevX, prevY, px, py);
            }
            prevX = px;
            prevY = py;
        }
    }
}

// EOF
