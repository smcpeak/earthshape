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
    // ---- Instance data ----
    /** Data points.  The independent variable is the array index. */
    public float[] data;

    /** Minimum Y value to plot.  By default, this is a little smaller
      * than the smallest value in 'data'. */
    public float dataMin;

    /** Maximum Y value to plot. */
    public float dataMax;

    // ---- Methods ----
    public PlotData1D(float[] data_)
    {
        this.data = data_;
        this.computeLimits();
    }

    /** Compute the plot limit values based on the data present.  If
      * the data is changed after construction, the client may want to
      * invoke this method as well to recompute reasonable limits. */
    public void computeLimits()
    {
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
}

// EOF
