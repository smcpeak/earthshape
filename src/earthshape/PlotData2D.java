// PlotData2D.java
// See copyright.txt for license and terms of use.

package earthshape;

import util.FloatUtil;

/** Hold data for a plot that has two real-valued independent
  * variables, X and Y, and a real-valued dependent variable, Z.
  *
  * Note: Although this class extends PlotData1D, it does not
  * use the inherited member 'yValues'.  Instead, it adds first
  * and last values for Y, and 'zValues' has the dependent data. */
public class PlotData2D extends PlotData1D {
    // ---------- Instance data ----------
    // ---- Core plot data ----
    /** Y value for the first row of 'zData'. */
    public float yFirst;

    /** Y value for the last row of 'zData'. */
    public float yLast;

    /** Number of X values per row of 'zData'. */
    public int xValuesPerRow;

    /** Dependent variable data.  The value for X index 'xi' and
      * Y index 'yi' is located at "xi + xValuesPerRow * yi".  This
      * array's size must be an integer multiple of xValuesPerRow. */
    public float[] zValues;

    // ---- Logical data cube to plot ----
    /** Z value that will be mapped to the maximum color brightness,
      * along with all larger Z values. */
    public float zMax;

    /** Z value that will be mapped to the minimum color brightness,
      * along with all smaller Z values. */
    public float zMin;

    // ---------- Methods ----------
    /** This is for use by PlotData3D. */
    protected PlotData2D()
    {}

    public PlotData2D(
        float xFirst_, float xLast_,
        float yFirst_, float yLast_,
        int xValuesPerRow_,
        float[] zValues_)
    {
        this.xFirst = xFirst_;
        this.xLast = xLast_;
        this.yFirst = yFirst_;
        this.yLast = yLast_;
        this.xValuesPerRow = xValuesPerRow_;
        this.zValues = zValues_;

        this.checkInvariants();

        this.computeLimits();
        this.computeTickSpacing();
    }

    @Override
    public void computeLimits()
    {
        this.computeXLimits();
        this.computeYLimits();
        this.computeZLimits();
    }

    // computeXLimits is inherited from PlotData1D unchanged.

    @Override
    public void computeYLimits()
    {
        this.yMin = this.yFirst;
        this.yMax = this.yLast;
    }

    public void computeZLimits()
    {
        this.zMin = FloatUtil.minimumOfArray(this.zValues);
        this.zMax = FloatUtil.maximumOfArray(this.zValues);

        if (this.zMin == this.zMax) {
            this.zMin -= 0.5;
            this.zMax += 0.5;
        }
    }

    public int yValuesPerColumn()
    {
        return this.zValues.length / this.xValuesPerRow;
    }

    /** Compute the X value for index 'i' of 'zValues'. */
    public float xValueForIndex(int i)
    {
        if (this.xValuesPerRow < 2) {
            return i==0? this.xFirst : this.xLast;
        }
        return this.xFirst + (this.xLast - this.xFirst) / (this.xValuesPerRow - 1) * i;
    }

    /** Compute the Y value for row 'i' of 'zValues'. */
    public float yValueForIndex(int i)
    {
        if (this.yValuesPerColumn() < 2) {
            return i==0? this.yFirst : this.yLast;
        }
        return this.yFirst + (this.yLast - this.yFirst) / (this.yValuesPerColumn() - 1) * i;
    }

    public float zValueForXYIndex(int xIndex, int yIndex)
    {
        return this.zValues[xIndex + this.xValuesPerRow * yIndex];
    }

    /** Scale and clamp the given Z value to [1,0], to be interpreted
      * as a fraction of the total Z range. */
    public float zScaledClamped(float z)
    {
        float clamped = FloatUtil.clamp(z, this.zMin, this.zMax);
        return (clamped - this.zMin) / (this.zMax - this.zMin);
    }

    /** Assert that data integrity invariants hold. */
    public void checkInvariants()
    {
        assert(this.zValues != null);
        assert(this.xValuesPerRow > 0);
        assert(this.zValues.length % this.xValuesPerRow == 0);
    }
}

// EOF
