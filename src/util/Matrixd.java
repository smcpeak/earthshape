// Matrixd.java
// See copyright.txt for license and terms of use.

package util;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;

/** Arbitrary-size immutable matrix of double. */
public class Matrixd {
    // ---- Instance data. ----
    /** Number of rows. */
    private int rows;

    /** Number of columns. */
    private int cols;

    /** Matrix entries, in row-major order.  Its size is rows * cols,
      * and entry (r,c) is at index r*cols + c. */
    private double[] vals;

    // ---- Methods ----
    /** This object takes ownership of 'vals_', so the caller must
      * promise not to modify it after passing it in. */
    public Matrixd(int rows_, int cols_, double[] vals_)
    {
        assert(vals_.length >= rows_ * cols_);
        this.rows = rows_;
        this.cols = cols_;
        this.vals = vals_;
    }

    /** Create from a float-typed matrix. */
    public Matrixd(Matrixf m)
    {
        this.rows = m.R();
        this.cols = m.C();
        this.vals = new double[R() * C()];
        for (int r = 0; r < this.R(); r++) {
            for (int c = 0; c < this.C(); c++) {
                this.vals[r * this.cols + c] = m.get(r, c);
            }
        }
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
    public double get(int r, int c)
    {
        return this.vals[r * this.cols + c];
    }

    @Override
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

    /** True if 'm' has the same dimensions, and all entries have
      * an absolute value difference of 'threshold' or less. */
    public boolean equalsWithin(Matrixd m, double threshold)
    {
        if (this.C() == m.C() && this.R() == m.R()) {
            for (int r=0; r < m.R(); r++) {
                for (int c=0; c < m.C(); c++) {
                    if (Math.abs(this.get(r,c) - m.get(r,c)) > threshold) {
                        return false;
                    }
                }
            }
            return true;
        }
        else {
            return false;      // differing dimensions
        }
    }

    /** Left-multiply this matrix by vector 'v' and yield the result. */
    public Vectord times(Vectord v)
    {
        assert(this.C() == v.dim());
        double[] ret = new double[this.R()];

        for (int r = 0; r < this.R(); r++) {
            ret[r] = 0;
            for (int c = 0; c < this.C(); c++) {
                ret[r] += this.get(r,c) * v.get(c);
            }
        }

        return new Vectord(ret);
    }

    /** Right-multiply matrix 'm' by vector 'v'. */
    public static Vectord multiply(Vectord v, Matrixd m)
    {
        assert(v.dim() == m.R());
        double[] ret = new double[m.C()];

        for (int c = 0; c < m.C(); c++) {
            ret[c] = 0;
            for (int i = 0; i < v.dim(); i++) {
                ret[c] += v.get(i) * m.get(i,c);
            }
        }

        return new Vectord(ret);
    }

    /** Left-multiply this matrix by matrix 'm'. */
    public Matrixd times(Matrixd m)
    {
        assert(this.C() == m.R());

        // The result has the number of rows of the first operand
        // and number of columns of the second operand.
        int retRows = this.R();
        int retCols = m.C();
        double[] ret = new double[retRows * retCols];

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

        return new Matrixd(retRows, retCols, ret);
    }

    /** Multiply this matrix by a scalar. */
    public Matrixd times(double x)
    {
        double[] ret = new double[R() * C()];

        for (int r = 0; r < R(); r++) {
            for (int c = 0; c < C(); c++) {
                ret[r * C() + c] = this.get(r, c) * x;
            }
        }

        return new Matrixd(R(), C(), ret);
    }

    /** Add this matrix to matrix 'm'. */
    public Matrixd plus(Matrixd m)
    {
        assert(this.R() == m.R());
        assert(this.C() == m.C());
        double[] ret = new double[R() * C()];

        for (int r = 0; r < R(); r++) {
            for (int c = 0; c < C(); c++) {
                ret[r * C() + c] = this.get(r, c) + m.get(r, c);
            }
        }

        return new Matrixd(R(), C(), ret);
    }

    /** Return the NxN identity matrix. */
    public static Matrixd identity(int n)
    {
        double[] mat = new double[n*n];
        for (int r=0; r<n; r++) {
            for (int c=0; c<n; c++) {
                mat[r*n + c] = (r==c? 1.0f : 0.0f);
            }
        }
        return new Matrixd(n, n, mat);
    }

    public double determinant()
    {
        assert(R() == C());

        if (R() == 1) {
            return this.get(0,0);
        }

        double sum = 0;
        double plus_or_minus = 1;

        for (int c=0; c < C(); c++) {
            sum += plus_or_minus * this.get(0, c) * getMinorMatrix(0, c).determinant();
            plus_or_minus *= -1;
        }

        return sum;
    }

    /** Get the matrix obtained by dropping row 'r' and column 'c'. */
    public Matrixd getMinorMatrix(int r, int c)
    {
        assert(R() > 1 && C() > 1);

        // Rows and columns of the minor.
        int MR = R() - 1;
        int MC = C() - 1;

        double[] ret = new double[MR * MC];
        for (int mr=0; mr < MR; mr++) {
            for (int mc=0; mc < MC; mc++) {
                ret[mr * MC + mc] =
                    this.get(mr < r? mr : mr+1,
                             mc < c? mc : mc+1);
            }
        }

        return new Matrixd(MR, MC, ret);
    }

    public Matrixd cofactorMatrix()
    {
        assert(R() == C());
        double[] ret = new double[R() * C()];

        double plus_or_minus = 1;

        for (int r = 0; r < R(); r++) {
            for (int c = 0; c < C(); c++) {
                ret[r * C() + c] = plus_or_minus * this.getMinorMatrix(r, c).determinant();
                plus_or_minus *= -1;
            }
        }

        return new Matrixd(R(), C(), ret);
    }

    /** Matrix with rows and columns swapped. */
    public Matrixd transpose()
    {
        double[] ret = new double[R() * C()];

        for (int r = 0; r < R(); r++) {
            for (int c = 0; c < C(); c++) {
                ret[c * R() + r] = this.get(r, c);
            }
        }

        return new Matrixd(C(), R(), ret);
    }

    public Matrixd adjugate()
    {
        return this.cofactorMatrix().transpose();
    }

    /** Convert 'this' to a Jama Matrix. */
    private Matrix toJamaMatrix()
    {
        Matrix m = new Matrix(R(), C());
        for (int r = 0; r < R(); r++) {
            for (int c = 0; c < C(); c++) {
                m.set(r, c, this.get(r, c));
            }
        }
        return m;
    }

    /** Convert from a Jama Matrix. */
    private Matrixd(Matrix m)
    {
        this.rows = m.getRowDimension();
        this.cols = m.getColumnDimension();
        this.vals = new double[R() * C()];
        for (int r = 0; r < R(); r++) {
            for (int c = 0; c < C(); c++) {
                this.vals[r * C() + c] = m.get(r, c);
            }
        }
    }

    /** Return the inverse of this matrix, or null if it is not invertible. */
    public Matrixd inverse()
    {
        return new Matrixd(this.toJamaMatrix().inverse());
    }

    /** This is the textbook algorithm, but suffers from numerical
      * instability. */
    public Matrixd inverseViaTextbookAlgorithm()
    {
        double det = this.determinant();
        if (det == 0) {
            return null;
        }
        return adjugate().times(1/det);
    }

    /** Return the eigenvector with real eigenvalue and largest
      * eigenvalue.  This might be zero if there are no such
      * eigenvectors. */
    public Vectord largestRealEigenvector()
    {
        EigenvalueDecomposition ed = this.toJamaMatrix().eig();
        Matrix V = ed.getV();
        double[] e = ed.getImagEigenvalues();

        Vectord ret = Vectord.zero(R());
        double retLen = 0;

        for (int c=0; c < C(); c++) {
            if (e[c] != 0) {
                continue;      // Complex eigenvalue
            }

            double[] vals = new double[R()];
            for (int r=0; r < R(); r++) {
                vals[r] = V.get(r, c);
            }
            Vectord vec = new Vectord(vals);
            double vecLen = vec.length();
            if (vecLen > retLen) {
                ret = vec;
                retLen = vecLen;
            }
        }

        return ret;
    }
}

// EOF
