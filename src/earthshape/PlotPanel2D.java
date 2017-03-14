// PlotPanel2D.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;

/** Visualize the data in a PlotData2D object by using color
  * brightness to represent the dependent data value. */
public class PlotPanel2D extends PlotPanel1D {
    // ---- Constants ----
    /** AWT boilerplate. */
    private static final long serialVersionUID = 9028282071818183607L;

    /** Extra space at the bottom to print the range of Z values. */
    private static final int EXTRA_BOTTOM_MARGIN = 20;

    // ---- Instance data ----
    /** Data and plot options. */
    private PlotData2D plotData;

    // ---- Methods ----
    public PlotPanel2D(PlotData2D plotData_)
    {
        super(plotData_);

        this.setPreferredSize(new Dimension(600, 600));
        this.setBackground(Color.WHITE);

        this.plotData = plotData_;
    }

    /** The pixel rectangle corresponding to (x,y). */
    private Rectangle plotXY(double x, double y)
    {
        // Width of one rectangle as a fraction of plot width.
        double xWidthFraction = 1.0 / this.plotData.xValuesPerRow;

        // Left edge of (x,y) box as fraction of plot width.
        double xLeftFraction = (x - this.plotData.xMin) /
                               (this.plotData.xMax - this.plotData.xMin) *
                               this.plotData.xValuesPerRow /
                               (this.plotData.xValuesPerRow + 1.0);

        // Height of one rectangle as a fraction of plot height.
        double yHeightFraction = 1.0 / this.plotData.yValuesPerColumn();

        // Top edge of (x,y) box as fraction of plot height.
        double yTopFraction = (y - this.plotData.yMin) /
                               (this.plotData.yMax - this.plotData.yMin) *
                               this.plotData.yValuesPerColumn() /
                               (this.plotData.yValuesPerColumn() + 1.0);

        return new Rectangle(
            this.plotLeft() + (int)(this.plotWidth() * xLeftFraction),
            this.plotTop() + (int)(this.plotHeight() * yTopFraction),
            (int)(this.plotWidth() * xWidthFraction + 1),
            (int)(this.plotHeight() * yHeightFraction + 1));
    }

    @Override
    protected int plotX(double x)
    {
        return (int)plotXY(x, 0).getCenterX();
    }

    @Override
    protected int plotY(double y)
    {
        return (int)plotXY(0, y).getCenterY();
    }

    @Override
    protected int plotBottom()
    {
        return super.plotBottom() - EXTRA_BOTTOM_MARGIN;
    }

    @Override
    public void paint(Graphics g)
    {
        g.clearRect(0, 0, this.getWidth(), this.getHeight());

        g.setColor(Color.BLACK);
        if (this.plotData.zValues.length < 1) {
            g.drawString("Insufficient data", 20, 20);
            return;
        }

        if (!this.checkDataRanges(g)) {
            return;
        }

        // Draw a grid of gray boxes of varying intensity.
        for (int yIndex=0; yIndex < this.plotData.yValuesPerColumn(); yIndex++) {
            float y = this.plotData.yValueForIndex(yIndex);
            for (int xIndex=0; xIndex < this.plotData.xValuesPerRow; xIndex++) {
                float x = this.plotData.xValueForIndex(xIndex);

                // Location of the grid square.
                Rectangle r = this.plotXY(x, y);

                // Brightness of its color.
                float z = this.plotData.zValueForXYIndex(xIndex, yIndex);
                float brightness = this.plotData.zScaledClamped(z);

                // Draw it.
                g.setColor(new Color(brightness, brightness, brightness));;
                g.fillRect(r.x, r.y, r.width, r.height);
            }
        }

        // Draw the border after the grid so it covers the edge pixels.
        g.setColor(Color.BLACK);
        this.drawBorderAndAxes(g);

        // Explain the range of Z values.
        g.drawString("White is "+this.plotData.zMax+", black is "+this.plotData.zMin+".",
            5, this.getHeight() - 10);
    }
}

// EOF
