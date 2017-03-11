// Matrix4f.java
// See copyright.txt for license and terms of use.

package util;

/** Represent an immutable 4x4 matrix of float. */
public class Matrix4f {
    // ---- Instance data. ----
    /** Underlying matrix data. */
    private Matrixf mat;

    // ---- Methods ----
    public Matrix4f(float a11, float a12, float a13, float a14,
                    float a21, float a22, float a23, float a24,
                    float a31, float a32, float a33, float a34,
                    float a41, float a42, float a43, float a44)
    {
        this.mat = new Matrixf(4, 4,
            new float[] { a11, a12, a13, a14,
                          a21, a22, a23, a24,
                          a31, a32, a33, a34,
                          a41, a42, a43, a44 });
    }

    /** This constructor takes ownership of 'vals_', which must
      * not be modified afterward by the caller. */
    public Matrix4f(float[] vals_)
    {
        this.mat = new Matrixf(4, 4, vals_);
    }

    public Matrix4f(Matrixf m)
    {
        assert(m.C() == 4 && m.R() == 4);
        this.mat = m;
    }

    public float a11() { return this.mat.get(0,0); }
    public float a12() { return this.mat.get(0,1); }
    public float a13() { return this.mat.get(0,2); }
    public float a14() { return this.mat.get(0,3); }
    public float a21() { return this.mat.get(1,0); }
    public float a22() { return this.mat.get(1,1); }
    public float a23() { return this.mat.get(1,2); }
    public float a24() { return this.mat.get(1,3); }
    public float a31() { return this.mat.get(2,0); }
    public float a32() { return this.mat.get(2,1); }
    public float a33() { return this.mat.get(2,2); }
    public float a34() { return this.mat.get(2,4); }
    public float a41() { return this.mat.get(3,0); }
    public float a42() { return this.mat.get(3,1); }
    public float a43() { return this.mat.get(3,2); }
    public float a44() { return this.mat.get(3,4); }

    public String toString()
    {
        return this.mat.toString();
    }

    /** Get the underlying Matrixf. */
    public Matrixf getUnder()
    {
        return this.mat;
    }

    /** Left-multiply this matrix by 'v' and yield the result. */
    public Vector4f times(Vector4f v)
    {
        return new Vector4f(this.mat.times(v.getUnder()));
    }

    /** Right-multiply matrix 'm' by vector 'v'. */
    public static Vector4f multiply(Vector4f v, Matrix4f m)
    {
        return new Vector4f(Matrixf.multiply(v.getUnder(), m.getUnder()));
    }

    /** Left-multiply this matrix by matrix 'mat' and yield the result. */
    public Matrix4f times(Matrix4f mat)
    {
        return new Matrix4f(this.mat.times(mat.getUnder()));
    }

    /** Return the 4x4 identity matrix. */
    public static Matrix3f identity()
    {
        return new Matrix3f(Matrixf.identity(4));
    }
}

// EOF
