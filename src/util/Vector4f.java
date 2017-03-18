// Vector4f.java
// See copyright.txt for license and terms of use.

package util;

/** A immutable 4D vector of float.  This class exists, as opposed to
  * directly using Vectorf, to provide some degree of dimensional safety
  * for vector operations. */
public class Vector4f {
    // ---- Instance data ----
    /** Underlying vector components: x, y, z, w. */
    private Vectorf under;

    // ---- Methods ----
    public Vector4f(float x, float y, float z, float w)
    {
        this.under = new Vectorf(new float[] {x,y,z,w});
    }

    /** Add a 'w' coordinate of 1. */
    public Vector4f(Vector3f v3)
    {
        this(v3.x(), v3.y(), v3.z(), 1);
    }

    /** Add a 'w' coordinate as specified. */
    public Vector4f(Vector3f v3, float w)
    {
        this(v3.x(), v3.y(), v3.z(), w);
    }

    public Vector4f(Vectorf v4)
    {
        assert(v4.dim() == 4);
        this.under = v4;
    }

    public float x()
    {
        return this.under.get(0);
    }

    public float y()
    {
         return this.under.get(1);
    }

    public float z()
    {
        return this.under.get(2);
    }

    public float w()
    {
        return this.under.get(3);
    }

    /** Get x,y,z as a 3D vector, dropping w entirely. */
    public Vector3f slice3()
    {
        return new Vector3f(x(), y(), z());
    }

    /** Get the underlying Vectorf, which is useful to pass to routines
      * that accept dynamically sized vectors. */
    public Vectorf getUnder()
    {
        return this.under;
    }

    public String toString()
    {
        return this.under.toString();
    }

    /** Get the values as an array.  For speed, this returns the
      * internal array, but the caller must promise not to
      * modify it! */
    public float[] getArray()
    {
        return this.under.getArray();
    }

    /** Return sum of 'this' and 'v'. */
    public Vector4f plus(Vector4f v)
    {
        return new Vector4f(this.under.plus(v.under));
    }

    /** Return 'this' minus 'v'. */
    public Vector4f minus(Vector4f v)
    {
        return new Vector4f(this.under.minus(v.under));
    }

    /** Return 'this' times scalar 's'. */
    public Vector4f times(float s)
    {
        return new Vector4f(this.under.times(s));
    }

    /** Return 'this' times scalar 's'. */
    public Vector4f timesd(double s)
    {
        return new Vector4f(this.under.timesd(s));
    }

    /** Return the square of the length of this vector. */
    public double lengthSquared()
    {
        return this.under.lengthSquared();
    }

    /** Return the length of this vector. */
    public double length()
    {
        return this.under.length();
    }

    /** True if this vector's length is zero. */
    public boolean isZero()
    {
        return this.under.isZero();
    }

    /** Return a normalized version of this vector.  The zero
      * vector is returned unchanged. */
    public Vector4f normalize()
    {
        return new Vector4f(this.under.normalize());
    }

    /** Return this vector after rotating by 'degrees' about 'axis'.
      * Rotation follows right-hand rule.  The axis vector is not
      * assumed to be normalized yet. */
    public Vector4f rotate(double degrees, Vector3f axis)
    {
        Matrix4f m = Matrix4f.rotate(FloatUtil.degreesToRadians(degrees), axis);
        return m.times(this);
    }

    /** Return dot product of 'this' and 'v'. */
    public double dot(Vector4f v)
    {
        return this.under.dot(v.under);
    }

    /** Return the separation angle between 'this' and 'v' in degrees. */
    public double separationAngleDegrees(Vector4f v)
    {
        return this.under.separationAngleDegrees(v.under);
    }
}

// EOF
