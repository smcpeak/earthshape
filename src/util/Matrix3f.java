// Matrix3f.java
// See copyright.txt for license and terms of use.

package util;

/** Represent an immutable 3x3 matrix of float. */
public class Matrix3f {
    // ---- Instance data. ----
    /** Underlying matrix data. */
    private Matrixf mat;

    // ---- Methods ----
    public Matrix3f(float a11, float a12, float a13,
                    float a21, float a22, float a23,
                    float a31, float a32, float a33)
    {
        this.mat = new Matrixf(3, 3,
            new float[] { a11, a12, a13,
                          a21, a22, a23,
                          a31, a32, a33 });
    }

    public Matrix3f(Matrixf m)
    {
        assert(m.C() == 3 && m.R() == 3);
        this.mat = m;
    }

    public float a11() { return this.mat.get(0,0); }
    public float a12() { return this.mat.get(0,1); }
    public float a13() { return this.mat.get(0,2); }
    public float a21() { return this.mat.get(1,0); }
    public float a22() { return this.mat.get(1,1); }
    public float a23() { return this.mat.get(1,2); }
    public float a31() { return this.mat.get(2,0); }
    public float a32() { return this.mat.get(2,1); }
    public float a33() { return this.mat.get(2,2); }

    public String toString()
    {
        return this.mat.toString();
    }

    /** Left-multiply this matrix by 'v' and yield the result. */
    public Vector3f times(Vector3f v)
    {
        return new Vector3f(this.mat.times(v.getUnder()));
    }

    /** Return the 3x3 identity matrix. */
    public static Matrix3f identity()
    {
        return new Matrix3f(Matrixf.identity(3));
    }

    /** Yield a matrix that, when multiplied by a vector, rotates that
      * vector by 'radians' around 'axis'. */
    public static Matrix3f rotate(double radians, Vector3f axis)
    {
        // Normalize the rotation axis.
        if (axis.isZero()) {
            return Matrix3f.identity();
        }
        double axisLength = axis.length();
        double x = axis.x() / axisLength;
        double y = axis.y() / axisLength;
        double z = axis.z() / axisLength;

        // Compute the upper 3x3 of this matrix:
        // https://www.khronos.org/registry/OpenGL-Refpages/gl2.1/
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        return new Matrix3f(
            (float)(x*x*(1-c)+c),
            (float)(x*y*(1-c)-z*s),
            (float)(x*z*(1-c)+y*s),

            (float)(y*x*(1-c)+z*s),
            (float)(y*y*(1-c)+c),
            (float)(y*z*(1-c)-x*s),

            (float)(z*x*(1-c)-y*s),
            (float)(z*y*(1-c)+x*s),
            (float)(z*z*(1-c)+c));
    }
}

// EOF
