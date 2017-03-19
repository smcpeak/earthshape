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

    /** Right-multiply matrix 'm' by vector 'v'. */
    public static Vector3f multiply(Vector3f v, Matrix3f m)
    {
        return new Vector3f(Matrixf.multiply(v.getUnder(), m.mat));
    }

    /** Left-multiply this matrix by matrix 'm' and yield result. */
    public Matrix3f times(Matrix3f m)
    {
        return new Matrix3f(this.mat.times(m.mat));
    }

    /** Return the 3x3 identity matrix. */
    public static Matrix3f identity()
    {
        return new Matrix3f(Matrixf.identity(3));
    }

    /** Yield a matrix that, when multiplied by a vector, rotates that
      * vector by 'radians' around 'axis'. */
    public static Matrix3f rotateRad(double radians, Vector3f axis)
    {
        // Normalize the rotation axis.
        if (axis.isZero()) {
            return Matrix3f.identity();
        }
        double axisLength = axis.length();
        double x = axis.x() / axisLength;
        double y = axis.y() / axisLength;
        double z = axis.z() / axisLength;

        // https://www.khronos.org/registry/OpenGL-Refpages/gl2.1/xhtml/glRotate.xml
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

    public static void p(String s)
    {
        System.out.println(s);
    }

    public static void main(String[] args)
    {
        Vector3f eastCA = new Vector3f(1, 0, 0);
        Vector3f upCA = new Vector3f(0, 1, 0);
        Vector3f northCA = new Vector3f(0, 0, -1);

        float dubhe_ca_az = 36.9f;
        float dubhe_ca_el = 44.8f;

        Vector3f v1 = northCA.rotateDeg(dubhe_ca_el, eastCA);
        p("v1: "+v1);

        Vector3f dubhe_ca =
            multiply(northCA, rotateRad(dubhe_ca_el, eastCA).times(rotateRad(-dubhe_ca_az, upCA)));
        p("dubhe_ca: "+dubhe_ca);
    }
}

// EOF
