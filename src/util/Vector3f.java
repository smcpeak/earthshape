// Vector3f.java

package util;

/** A immutable 3D vector of float. */
public class Vector3f {
    // ---- Instance data ----
    /** Array of three vector components, in order x, y, z. */
    private float[] vals;

    // ---- Methods ----
    public Vector3f(float x, float y, float z)
    {
        this.vals = new float[] {x,y,z};
    }

    public float x()
    {
        return vals[0];
    }

    public float y()
    {
         return vals[1];
    }

    public float z()
    {
        return vals[2];
    }

    public String toString()
    {
        return "("+x()+","+y()+","+z()+")";
    }

    /** Get the values as an array.  For speed, this returns the
      * internal array, but the caller must promise not to
      * modify it! */
    public float[] getArray()
    {
        return vals;
    }

    /** Return sum of 'this' and 'v'. */
    public Vector3f plus(Vector3f v)
    {
        return new Vector3f(x()+v.x(), y()+v.y(), z()+v.z());
    }

    /** Return 'this' minus 'v'. */
    public Vector3f minus(Vector3f v)
    {
        return new Vector3f(x()-v.x(), y()-v.y(), z()-v.z());
    }

    /** Return 'this' times scalar 's'. */
    public Vector3f times(float s)
    {
        return new Vector3f(x()*s, y()*s, z()*s);
    }

    /** Return the square of the length of this vector. */
    public double lengthSquared()
    {
        return x()*x() + y()*y() + z()*z();
    }

    /** Return the length of this vector. */
    public double length()
    {
        return Math.sqrt(lengthSquared());
    }

    /** True if this vector's length is zero. */
    public boolean isZero()
    {
        return x()==0 && y()==0 && z()==0;
    }

    /** Return a normalized version of this vector.  The zero
      * vector is returned unchanged. */
    public Vector3f normalize()
    {
        if (isZero()) {
            return this;
        }
        float normFactor = (float)(1.0 / this.length());
        return this.times(normFactor);
    }

    /** Return this vector after rotating by 'degrees' about 'axis'.
      * Rotation follows right-hand rule. */
    public Vector3f rotate(float degrees, Vector3f axis)
    {
        float radians = (float)(degrees / 180.0 * Math.PI);

        Matrix3f m = Matrix3f.rotate(radians, axis);
        return m.times(this);
    }

    /** Return 'this' cross 'v'. */
    public Vector3f cross(Vector3f v)
    {
        return new Vector3f(y()*v.z() - z()*v.y(),
                            z()*v.x() - x()*v.z(),
                            x()*v.y() - y()*v.x());
    }
}
