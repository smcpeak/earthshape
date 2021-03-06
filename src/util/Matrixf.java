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
        assert(vals_.length >= rows_ * cols_);
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

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int r = 0; r < this.R(); r++) {
            if (r > 0) {
                sb.append(", ");
            }
            sb.append("(");
            for (int c = 0; c < this.C(); c++) {
                if (c > 0) {
                    sb.append(", ");
                }
                sb.append(""+this.get(r,c));
            }
            sb.append(")");
        }
        sb.append("]");
        return sb.toString();
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

    /** Right-multiply matrix 'm' by vector 'v'. */
    public static Vectorf multiply(Vectorf v, Matrixf m)
    {
        assert(v.dim() == m.R());
        float[] ret = new float[m.C()];

        for (int c = 0; c < m.C(); c++) {
            ret[c] = 0;
            for (int i = 0; i < v.dim(); i++) {
                ret[c] += v.get(i) * m.get(i,c);
            }
        }

        return new Vectorf(ret);
    }

    /** Left-multiply this matrix by matrix 'm' and yield result. */
    public Matrixf times(Matrixf m)
    {
        assert(this.C() == m.R());

        // The result has the number of rows of the first operand
        // and number of columns of the second operand.
        int retRows = this.R();
        int retCols = m.C();
        float[] ret = new float[retRows * retCols];

        // This is *not* an efficient implementation, it is just the
        // naive, straightforward algorithm.

        // Iterate through the entries of the result matrix.
        for (int r = 0; r < retRows; r++) {
            for (int c = 0; c < retCols; c++) {
                ret[r * retCols + c] = 0;

                // Iterate through the elements of the operand
                // matrices that contribute to the result at (r,c).
                for (int i=0; i < this.C(); i++) {
                    ret[r * retCols + c] += this.get(r,i) * m.get(i,c);
                }
            }
        }

        return new Matrixf(retRows, retCols, ret);
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
