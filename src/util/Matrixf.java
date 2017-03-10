// Matrixf.java
// See copyright.txt for license and terms of use.

package util;

/** Arbitrary-size immutable matrix of float. */
public class Matrixf {
    // ---- Instance data. ----
    /** Number of rows. */
    private int rows;

    /** Number of columns. */
    private int cols;

    /** Matrix entries, in row-major order.  Its size is rows * cols,
      * and entry (r,c) is at index r*cols + c. */
    private float[] vals;

    // ---- Methods ----
    /** This object takes ownership of 'vals_', so the caller must
      * promise not to modify it after passing it in. */
    public Matrixf(int rows_, int cols_, float[] vals_)
    {
        this.rows = rows_;
        this.cols = cols_;
        this.vals = vals_;
    }

    /** Number of rows. */
    public int R()
    {
        return this.rows;
    }

    /** Number of columns. */
    public int C()
    {
        return this.cols;
    }

    /** Get element at row 'r' and column 'c'.  Both are 0-based. */
    public float get(int r, int c)
    {
        return this.vals[r * this.cols + c];
    }

    /** Left-multiply this matrix by vector 'v' and yield the result. */
    public Vectorf times(Vectorf v)
    {
        assert(this.C() == v.dim());
        float[] ret = new float[this.R()];

        for (int r = 0; r < this.R(); r++) {
            ret[r] = 0;
            for (int c = 0; c < this.C(); c++) {
                ret[r] += this.get(r,c) * v.get(c);
            }
        }

        return new Vectorf(ret);
    }

    /** Return the NxN identity matrix. */
    public static Matrixf identity(int n)
    {
        float[] mat = new float[n*n];
        for (int r=0; r<n; r++) {
            for (int c=0; c<n; c++) {
                mat[r*n + c] = (r==c? 1.0f : 0.0f);
            }
        }
        return new Matrixf(n, n, mat);
    }
}

// EOF
