// Matrix3f.java

package util;

/** Represent an immutable 3x3 matrix of float. */
public class Matrix3f {
    // ---- Instance data. ----
    // Matrix entries, in row-major order.
    private float vals[];

    // ---- Methods ----
    public Matrix3f(float a11, float a12, float a13,
                    float a21, float a22, float a23,
                    float a31, float a32, float a33)
    {
        this.vals = new float[] { a11, a12, a13,
                                  a21, a22, a23,
                                  a31, a32, a33 };
    }

    public float a11() { return vals[0]; }
    public float a12() { return vals[1]; }
    public float a13() { return vals[2]; }
    public float a21() { return vals[3]; }
    public float a22() { return vals[4]; }
    public float a23() { return vals[5]; }
    public float a31() { return vals[6]; }
    public float a32() { return vals[7]; }
    public float a33() { return vals[8]; }

    /** Left-multiply this matrix by 'v' and yield the result. */
    public Vector3f times(Vector3f v)
    {
        return new Vector3f(a11()*v.x() + a12()*v.y() + a13()*v.z(),
                            a21()*v.x() + a22()*v.y() + a23()*v.z(),
                            a31()*v.x() + a32()*v.y() + a33()*v.z());
    }

    /** Return the 3x3 identity matrix. */
    public static Matrix3f identity()
    {
        return new Matrix3f(1,0,0,
                            0,1,0,
                            0,0,1);
    }

    /** Yield a matrix that, when multiplied by a vector, rotates that
      * vector by 'radians' around 'axis'. */
    public static Matrix3f rotate(float radians, Vector3f axis)
    {
        // Normalize the rotation axis.
        if (axis.isZero()) {
            return Matrix3f.identity();
        }
        Vector3f na = axis.normalize();
        float x = na.x();
        float y = na.y();
        float z = na.z();

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
