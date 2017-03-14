// PlotData1D.java
// See copyright.txt for license and terms of use.

package earthshape;

import util.FloatUtil;

/** Hold the data required to make a 1D plot.  Logically, this is
  * a map from a single independent real variable (X) to a single
  * dependent real variable (Y).  It carries other information
  * about how to visualize the data, but does not have any drawing
  * routines of its own.  Instead, PlotPanel1D does that. */
public class PlotData1D {
    // ---------- Instance data ----------
    // ---- Core plot data ----
    /** X value of the first entry in 'yValues'. */
    public float xFirst;

    /** X value of the last entry in 'yValues'.  All other X values
      * are assumed to be equally spaced between first and last. */
    public float xLast;

    /** Data points.  The independent variable is the array index. */
    public float[] yValues;

    // ---- Logical data rectangle to plot ----
    /** Minimum X value to plot.  By default, equal to 'xFirst'. */
    public float xMin;

    /** Maximum X value to plot.  By default, equal to 'xLast'. */
    public float xMax;

    /** Minimum Y value to plot.  By default, this is a little smaller
      * than the smallest value in 'data'. */
    public float yMin;

    /** Maximum Y value to plot. */
    public float yMax;

    // ---- Axis labeling ----
    /** Spacing between major tick marks on the X axis.  Major tick
      * marks are longer and have numeric labels. */
    public double xMajorTickSpace;

    /** Spacing between minor tick marks on the X  axis.  Minor tick
      * marks are shorter and are not labeled. */
    public double xMinorTickSpace;

    /** Spacing between major tick marks on the Y axis.  Major tick
      * marks are longer and have numeric labels. */
    public double yMajorTickSpace;

    /** Spacing between minor tick marks on the Y axis.  Minor tick
      * marks are shorter and are not labeled. */
    public double yMinorTickSpace;

    // ---------- Methods ----------
    public PlotData1D(float xFirst_, float xLast_, float[] yValues_)
    {
        this.xFirst = xFirst_;
        this.xLast = xLast_;
        this.yValues = yValues_;
        this.computeLimits();
        this.computeTickSpacing();
    }

    /** Compute the X value for index 'i' of 'yValues'. */
    public float xValueForIndex(int i)
    {
        if (this.yValues.length < 2) {
            return i==0? this.xFirst : this.xLast;
        }
        return this.xFirst + (this.xLast - this.xFirst) / (this.yValues.length - 1) * i;
    }

    /** Compute the plot limit values based on the data present.  If
      * the data is changed after construction, the client may want to
      * invoke this method as well to recompute reasonable limits. */
    public void computeLimits()
    {
        this.computeXLimits();
        this.computeYLimits();
    }

    public void computeXLimits()
    {
        this.xMin = this.xFirst;
        this.xMax = this.xLast;
    }

    public void computeYLimits()
    {
        this.yMin = FloatUtil.minimumOfArray(this.yValues);
        this.yMax = FloatUtil.maximumOfArray(this.yValues);

        // Expand the ranges to avoid hitting the plot edges.
        double yRange = this.yMax - this.yMin;
        if (yRange == 0) {
            yRange = 1;    // Just use something non-zero.
        }
        this.yMax += yRange * 0.05f;
        this.yMin -= yRange * 0.05f;
    }

    /** Compute good tick spacing based on the current limits. */
    public void computeTickSpacing()
    {
        this.computeXTickSpacing();
        this.computeYTickSpacing();
    }

    public void computeXTickSpacing()
    {
        double xRange = (this.xMax - this.xMin);
        this.xMajorTickSpace = Math.pow(10, Math.floor(Math.log10(xRange)));
        this.xMinorTickSpace = this.xMajorTickSpace / 10.0f;
    }

    public void computeYTickSpacing()
    {
        double yRange = (this.yMax - this.yMin);
        this.yMajorTickSpace = Math.pow(10, Math.floor(Math.log10(yRange)));
        this.yMinorTickSpace = this.yMajorTickSpace / 10.0f;
    }
}

// EOF
