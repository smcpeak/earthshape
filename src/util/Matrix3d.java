// Matrix3d.java
// See copyright.txt for license and terms of use.

package util;

/** Represent an immutable 3x3 matrix of double. */
public class Matrix3d {
    // ---- Instance data. ----
    /** Underlying matrix data. */
    private Matrixd mat;

    // ---- Methods ----
    public Matrix3d(double a11, double a12, double a13,
                    double a21, double a22, double a23,
                    double a31, double a32, double a33)
    {
        this.mat = new Matrixd(3, 3,
            new double[] { a11, a12, a13,
                           a21, a22, a23,
                           a31, a32, a33 });
    }

    public Matrix3d(Matrixd m)
    {
        assert(m.C() == 3 && m.R() == 3);
        this.mat = m;
    }

    public Matrix3d(Matrix3f m)
    {
        this.mat = new Matrixd(m.getUnder());
    }

    public double a11() { return this.mat.get(0,0); }
    public double a12() { return this.mat.get(0,1); }
    public double a13() { return this.mat.get(0,2); }
    public double a21() { return this.mat.get(1,0); }
    public double a22() { return this.mat.get(1,1); }
    public double a23() { return this.mat.get(1,2); }
    public double a31() { return this.mat.get(2,0); }
    public double a32() { return this.mat.get(2,1); }
    public double a33() { return this.mat.get(2,2); }

    public String toString()
    {
        return this.mat.toString();
    }

    /** Left-multiply this matrix by 'v' and yield the result. */
    public Vector3d times(Vector3d v)
    {
        return new Vector3d(this.mat.times(v.getUnder()));
    }

    /** Left-multiply this matrix by matrix 'm'. */
    public Matrix3d times(Matrix3d m)
    {
        return new Matrix3d(this.mat.times(m.mat));
    }

    /** Multiply this matrix by a scalar. */
    public Matrix3d times(double x)
    {
        return new Matrix3d(this.mat.times(x));
    }

    /** Add this matrix to matrix 'm'. */
    public Matrix3d plus(Matrix3d m)
    {
        return new Matrix3d(this.mat.plus(m.mat));
    }

    /** Return the 3x3 identity matrix. */
    public static Matrix3d identity()
    {
        return new Matrix3d(Matrixd.identity(3));
    }

    /** Return the inverse of this matrix, or null if it is not invertible. */
    public Matrix3d inverse()
    {
        Matrixd i = this.mat.inverse();
        if (i == null) {
            return null;
        }
        else {
            return new Matrix3d(i);
        }
    }

    /** Yield a matrix that, when multiplied by a vector, rotates that
      * vector by 'radians' around 'axis'. */
    public static Matrix3d rotateRad(double radians, Vector3d axis)
    {
        // Normalize the rotation axis.
        if (axis.isZero()) {
            return Matrix3d.identity();
        }
        double axisLength = axis.length();
        double x = axis.x() / axisLength;
        double y = axis.y() / axisLength;
        double z = axis.z() / axisLength;

        // Compute the upper 3x3 of this matrix:
        // https://www.khronos.org/registry/OpenGL-Refpages/gl2.1/
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        return new Matrix3d(
            x*x*(1-c)+c,
            x*y*(1-c)-z*s,
            x*z*(1-c)+y*s,

            y*x*(1-c)+z*s,
            y*y*(1-c)+c,
            y*z*(1-c)-x*s,

            z*x*(1-c)-y*s,
            z*y*(1-c)+x*s,
            z*z*(1-c)+c);
    }
}

// EOF
