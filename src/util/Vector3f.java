// Vector3f.java
// See copyright.txt for license and terms of use.

package util;

/** A immutable 3D vector of float.  This class exists, as opposed to
  * directly using Vectorf, to provide some degree of dimensional safety
  * for vector operations. */
public class Vector3f {
    // ---- Instance data ----
    /** Underlying vector components: x, y, z. */
    private Vectorf under;

    // ---- Methods ----
    public Vector3f(float x, float y, float z)
    {
        this.under = new Vectorf(new float[] {x,y,z});
    }

    public Vector3f(Vectorf v3)
    {
        assert(v3.dim() == 3);
        this.under = v3;
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
    public Vector3f plus(Vector3f v)
    {
        return new Vector3f(this.under.plus(v.under));
    }

    /** Return 'this' minus 'v'. */
    public Vector3f minus(Vector3f v)
    {
        return new Vector3f(this.under.minus(v.under));
    }

    /** Return 'this' times scalar 's'. */
    public Vector3f times(float s)
    {
        return new Vector3f(this.under.times(s));
    }

    /** Return 'this' times scalar 's'. */
    public Vector3f timesd(double s)
    {
        return new Vector3f(this.under.timesd(s));
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
    public Vector3f normalize()
    {
        return new Vector3f(this.under.normalize());
    }

    /** Return a normalized version as Vector3d. */
    public Vector3d normalizeAsVector3d()
    {
        return new Vector3d(this.under.normalizeAsVectord());
    }

    /** Return this vector after rotating by 'degrees' about 'axis'.
      * Rotation follows right-hand rule.  The axis vector is not
      * assumed to be normalized yet. */
    public Vector3f rotate(double degrees, Vector3f axis)
    {
        Matrix3f m = Matrix3f.rotate(FloatUtil.degreesToRadians(degrees), axis);
        return m.times(this);
    }

    /** Rotate about 'axisAndAngle', interpreting its length as
      * the rotation angle in degrees. */
    public Vector3f rotateAA(Vector3f axisAndAngle)
    {
        double degrees = axisAndAngle.length();
        if (degrees == 0) {
            return this;
        }
        return this.rotate(degrees, axisAndAngle);
    }

    /** Return dot product of 'this' and 'v'. */
    public double dot(Vector3f v)
    {
        return this.under.dot(v.under);
    }

    /** Return the separation angle between 'this' and 'v' in degrees. */
    public double separationAngleDegrees(Vector3f v)
    {
        return this.under.separationAngleDegrees(v.under);
    }

    /** Return 'this' projected onto 'u', which is assumed to be a
      * unit vector. */
    public Vector3f projectOntoUnitVector(Vector3f u)
    {
        return u.timesd(this.dot(u));
    }

    /** Return 'this' cross 'v'. */
    public Vector3f cross(Vector3f v)
    {
        return new Vector3f(y()*v.z() - z()*v.y(),
                            z()*v.x() - x()*v.z(),
                            x()*v.y() - y()*v.x());
    }

    /** Return a rotation vector that transforms 'this' to 'dest', ignoring
      * their lengths.  The vector is the rotation axis and its length
      * is the rotation angle in degrees. */
    public Vector3f rotationToBecome(Vector3f dest)
    {
        // First ensure the vectors are normalized.
        dest = dest.normalize();
        Vector3f src = this.normalize();

        // Now their cross product gives the axis and the
        // sine of the desired length.
        Vector3f v = src.cross(dest);

        // Fix the length.
        float degrees = FloatUtil.radiansToDegreesf(
            (float)Math.asin(v.length()));
        return v.normalize().times(degrees);
    }

    /** Compose two rotation vectors, where each has magnitude equal to
      * the rotation angle in degrees. */
    public static Vector3f composeRotations(Vector3f first, Vector3f second)
    {
        // This calculation is sensitive to small errors, so carry it
        // out entirely using double.
        return Vector3d.composeRotations(new Vector3d(first), new Vector3d(second)).toVector3f();
    }
}

// EOF
