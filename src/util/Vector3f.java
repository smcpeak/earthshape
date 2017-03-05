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
}
