// Vector3f.java
// See copyright.txt for license and terms of use.

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

    /** Return 'this' times scalar 's'. */
    public Vector3f timesd(double s)
    {
        return new Vector3f((float)(x()*s),
                            (float)(y()*s),
                            (float)(z()*s));
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
      * Rotation follows right-hand rule.  The axis vector is not
      * assumed to be normalized yet. */
    public Vector3f rotate(float degrees, Vector3f axis)
    {
        float radians = (float)(degrees / 180.0 * Math.PI);

        Matrix3f m = Matrix3f.rotate(radians, axis);
        return m.times(this);
    }

    /** Rotate about 'axisAndAngle', interpreting its length as
      * the rotation angle in degrees. */
    public Vector3f rotateAA(Vector3f axisAndAngle)
    {
        float degrees = (float)axisAndAngle.length();
        if (degrees == 0) {
            return this;
        }
        return this.rotate(degrees, axisAndAngle);
    }

    /** Return dot product of 'this' and 'v'. */
    public float dot(Vector3f v)
    {
        return x()*v.x() + y()*v.y() + z()*v.z();
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
        // The computation here is based on this question and answer:
        // http://math.stackexchange.com/questions/382760/composition-of-two-axis-angle-rotations

        // First rotation angle (in radians) and axis.
        double beta = FloatUtil.degreesToRadians(first.length());
        Vector3f m = first.normalize();

        // Second rotation angle and axis.
        double alpha = FloatUtil.degreesToRadians(second.length());
        Vector3f l = second.normalize();

        // Combined rotation angle, in radians.
        double gamma = Math.acos(Math.cos(alpha/2) * Math.cos(beta/2) -
                                 Math.sin(alpha/2) * Math.sin(beta/2) * l.dot(m)) * 2;

        // Combined rotation axis, which should be a unit vector.
        Vector3f n = ( l.timesd(Math.sin(alpha/2) * Math.cos(beta/2)).plus(
                       m.timesd(Math.cos(alpha/2) * Math.sin(beta/2)).plus(
                       (l.cross(m)).timesd(Math.sin(alpha/2) * Math.sin(beta/2)) ))).timesd(1/
                     // ----------------------------------------------------------
                                     Math.sin(gamma/2));

        // Put axis and angle back together.
        return n.timesd(FloatUtil.radiansToDegrees(gamma));
    }
}

// EOF
