// PlotData3D.java
// See copyright.txt for license and terms of use.

package earthshape;

import util.FloatUtil;

/** Hold data for a plot that has three real-valued independent
  * variables, X, Y, and Z, and a real-valued dependent variable, W.
  *
  * Note: Although this class extends PlotData2D, it does not
  * use the inherited members 'yValues' or 'zValues'. */
public class PlotData3D extends PlotData2D {
    // ---------- Instance data ----------
    // ---- Core plot data ----
    /** Z value for the first plane of 'wData'. */
    public float zFirst;

    /** Z value for the last plane of 'wData'. */
    public float zLast;

    /** Number of Y values per column of 'wData'. */
    public int yValuesPerColumn;

    /** Dependent variable data.  The value for X index 'xi',
      * Y index 'yi', and Z index 'zi' is located at
      * "xi + xValuesPerRow * yi + (xValuesPerRow * yValuesPerColumn) * zi".
      * This array's size must be an integer multiple of
      * "xValuesPerRow * yValuesPerColumn".
      *
      * I call the dimension along with X varies a "row",
      * the dimension along which Y varies a "column",
      * and the dimension along which Z varies a "tower".
      * A slice containing a single Z value is a "plane";
      * planes are perpendicular to towers. */
    public float[] wValues;

    // ---- Axis labels ----
    /** Spacing between major tick marks on the Z axis.  Major tick
      * marks are longer and have numeric labels. */
    public double zMajorTickSpace;

    /** Spacing between minor tick marks on the Z axis.  Minor tick
      * marks are shorter and are not labeled. */
    public double zMinorTickSpace;

    /** Spacing between major tick marks on the W axis.  Major tick
      * marks are longer and have numeric labels. */
    public double wMajorTickSpace;

    /** Spacing between minor tick marks on the W axis.  Minor tick
      * marks are shorter and are not labeled. */
    public double wMinorTickSpace;

    // ---- Logical data hypercube to plot ----
    /** W value that will be mapped to the maximum color brightness,
      * along with all larger W values. */
    public float wMax;

    /** W value that will be mapped to the minimum color brightness,
      * along with all smaller W values. */
    public float wMin;

    // ---------- Methods ----------
    public PlotData3D(
        float xFirst_, float xLast_,
        float yFirst_, float yLast_,
        float zFirst_, float zLast_,
        int xValuesPerRow_,
        int yValuesPerColumn_,
        float[] wValues_)
    {
        this.xFirst = xFirst_;
        this.xLast = xLast_;
        this.yFirst = yFirst_;
        this.yLast = yLast_;
        this.zFirst = zFirst_;
        this.zLast = zLast_;

        this.xValuesPerRow = xValuesPerRow_;
        this.yValuesPerColumn = yValuesPerColumn_;

        this.wValues = wValues_;

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
        this.computeWLimits();
    }

    // computeXLimits and computeYLimits are inherited unchanged.

    @Override
    public void computeZLimits()
    {
        this.zMin = this.zFirst;
        this.zMax = this.zLast;
    }

    public void computeWLimits()
    {
        this.wMin = FloatUtil.minimumOfArray(this.wValues);
        this.wMax = FloatUtil.maximumOfArray(this.wValues);

        if (this.wMin == this.wMax) {
            this.wMin -= 0.5;
            this.wMax += 0.5;
        }
    }

    @Override
    public void computeTickSpacing()
    {
        this.computeXTickSpacing();
        this.computeYTickSpacing();
        this.computeZTickSpacing();
        this.computeWTickSpacing();
    }

    public void computeZTickSpacing()
    {
        this.zMajorTickSpace = PlotData1D.majorTickSpace(this.zMax, this.zMin);
        this.zMinorTickSpace = PlotData1D.minorTickSpace(this.zMax, this.zMin,
            this.zMajorTickSpace);
    }

    public void computeWTickSpacing()
    {
        this.wMajorTickSpace = PlotData1D.majorTickSpace(this.wMax, this.wMin);
        this.wMinorTickSpace = PlotData1D.minorTickSpace(this.wMax, this.wMin,
            this.wMajorTickSpace);
    }

    /** Compute the Y value for row 'i' of 'zValues'. */
    @Override
    public float yValueForIndex(int i)
    {
        if (this.yValuesPerColumn < 2) {
            return i==0? this.yFirst : this.yLast;
        }
        return this.yFirst + (this.yLast - this.yFirst) / (this.yValuesPerColumn - 1) * i;
    }

    /** Total number of values in a single plane. */
    public int xyValuesPerPlane()
    {
        return this.xValuesPerRow * this.yValuesPerColumn;
    }

    public int zValuesPerTower()
    {
        return this.wValues.length / xyValuesPerPlane();
    }

    /** Assert that data integrity invariants hold. */
    public void checkInvariants()
    {
        assert(this.wValues != null);
        assert(this.xValuesPerRow > 0);
        assert(this.yValuesPerColumn > 0);
        assert(this.wValues.length % this.xyValuesPerPlane() == 0);
    }

    public float wValueForXYZIndex(int xIndex, int yIndex, int zIndex)
    {
        return this.wValues[xIndex + this.xValuesPerRow * yIndex +
                            this.xValuesPerRow * this.yValuesPerColumn * zIndex];
    }

    public PlotData1D getXSlice(int yIndex, int zIndex)
    {
        float[] slice = new float[this.xValuesPerRow];
        for (int xIndex=0; xIndex < this.xValuesPerRow; xIndex++) {
            slice[xIndex] = wValueForXYZIndex(xIndex, yIndex, zIndex);
        }

        // My X is ret's X, my W is ret's Y.
        PlotData1D ret = new PlotData1D(this.xFirst, this.xLast, slice);
        ret.xMin = this.xMin;
        ret.xMax = this.xMax;
        ret.xMajorTickSpace = this.xMajorTickSpace;
        ret.xMinorTickSpace = this.xMinorTickSpace;
        ret.yMin = this.wMin;
        ret.yMax = this.wMax;
        ret.yMajorTickSpace = this.wMajorTickSpace;
        ret.yMinorTickSpace = this.wMinorTickSpace;
        return ret;
    }

    public PlotData1D getYSlice(int xIndex, int zIndex)
    {
        float[] slice = new float[this.yValuesPerColumn];
        for (int yIndex=0; yIndex < this.yValuesPerColumn; yIndex++) {
            slice[yIndex] = wValueForXYZIndex(xIndex, yIndex, zIndex);
        }

        // My Y is ret's X, my W is ret's Y.
        PlotData1D ret = new PlotData1D(this.yFirst, this.yLast, slice);
        ret.xMin = this.yMin;
        ret.xMax = this.yMax;
        ret.xMajorTickSpace = this.yMajorTickSpace;
        ret.xMinorTickSpace = this.yMinorTickSpace;
        ret.yMin = this.wMin;
        ret.yMax = this.wMax;
        ret.yMajorTickSpace = this.wMajorTickSpace;
        ret.yMinorTickSpace = this.wMinorTickSpace;
        return ret;
    }

    public PlotData1D getZSlice(int xIndex, int yIndex)
    {
        float[] slice = new float[this.zValuesPerTower()];
        for (int zIndex=0; zIndex < this.zValuesPerTower(); zIndex++) {
            slice[zIndex] = wValueForXYZIndex(xIndex, yIndex, zIndex);
        }

        // My Z is ret's X, my W is ret's Y..
        PlotData1D ret = new PlotData1D(this.zFirst, this.zLast, slice);
        ret.xMin = this.zMin;
        ret.xMax = this.zMax;
        ret.xMajorTickSpace = this.zMajorTickSpace;
        ret.xMinorTickSpace = this.zMinorTickSpace;
        ret.yMin = this.wMin;
        ret.yMax = this.wMax;
        ret.yMajorTickSpace = this.wMajorTickSpace;
        ret.yMinorTickSpace = this.wMinorTickSpace;
        return ret;
    }

    public PlotData2D getYZSlice(int xIndex)
    {
        float[] slice = new float[this.yValuesPerColumn * this.zValuesPerTower()];
        for (int yIndex=0; yIndex < this.yValuesPerColumn; yIndex++) {
            for (int zIndex=0; zIndex < this.zValuesPerTower(); zIndex++) {
                slice[yIndex + this.yValuesPerColumn * zIndex] =
                    wValueForXYZIndex(xIndex, yIndex, zIndex);
            }
        }

        // My Y is ret's X, my Z is ret's Y, my W is ret's Z.
        PlotData2D ret = new PlotData2D(
            this.yFirst, this.yLast,
            this.zFirst, this.zLast,
            this.yValuesPerColumn,
            slice);
        ret.xMin = this.yMin;
        ret.xMax = this.yMax;
        ret.xMajorTickSpace = this.yMajorTickSpace;
        ret.xMinorTickSpace = this.yMinorTickSpace;
        ret.yMin = this.zMin;
        ret.yMax = this.zMax;
        ret.yMajorTickSpace = this.zMajorTickSpace;
        ret.yMinorTickSpace = this.zMinorTickSpace;
        ret.zMin = this.wMin;
        ret.zMax = this.wMax;
        // Z does not have ticks in PlotData2D since it uses colors.
        return ret;
    }
}

// EOF
