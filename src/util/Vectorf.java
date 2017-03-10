// Vectorf.java
// See copyright.txt for license and terms of use.

package util;

/** Arbitrary-length immutable vector of float. */
public class Vectorf {
    // ---- Instance data ----
    /** Array of vector components, in order. */
    private float[] vals;

    // ---- Methods ----
    /** This takes ownership of the passed values.  The caller should
      * not retain a reference to the 'vals_' array. */
    public Vectorf(float[] vals_)
    {
        this.vals = vals_;
    }

    /** Return the number of components (dimensions) of this vector. */
    public int dim()
    {
        return vals.length;
    }

    /** Get one component of the vector.  'd' must be in
      * [0,dim()-1]. */
    public float get(int d)
    {
        return vals[d];
    }

    /** Return the vector as a comma-separated, parentheses-enclosed list. */
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (int i=0; i < this.dim(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(""+this.get(i));
        }
        sb.append(")");
        return sb.toString();
    }

    /** Get the values as an array.  For speed, this returns the
      * internal array, but the caller must promise not to
      * modify it! */
    public float[] getArray()
    {
        return vals;
    }

    /** Return sum of 'this' and 'v'. */
    public Vectorf plus(Vectorf v)
    {
        assert(this.dim() == v.dim());
        float[] ret = new float[this.dim()];
        for (int i=0; i < this.dim(); i++) {
            ret[i] = this.get(i) + v.get(i);
        }
        return new Vectorf(ret);
    }

    /** Return 'this' minus 'v'. */
    public Vectorf minus(Vectorf v)
    {
        assert(this.dim() == v.dim());
        float[] ret = new float[this.dim()];
        for (int i=0; i < this.dim(); i++) {
            ret[i] = this.get(i) - v.get(i);
        }
        return new Vectorf(ret);
    }

    /** Return 'this' times scalar 's'. */
    public Vectorf times(float s)
    {
        float[] ret = new float[this.dim()];
        for (int i=0; i < this.dim(); i++) {
            ret[i] = this.get(i) * s;
        }
        return new Vectorf(ret);
    }

    /** Return 'this' times scalar 's'. */
    public Vectorf timesd(double s)
    {
        float[] ret = new float[this.dim()];
        for (int i=0; i < this.dim(); i++) {
            ret[i] = (float)(this.get(i) * s);
        }
        return new Vectorf(ret);
    }

    /** Return the square of the length of this vector. */
    public double lengthSquared()
    {
        float ret = 0;
        for (int i=0; i < this.dim(); i++) {
            ret += this.get(i) * this.get(i);
        }
        return ret;
    }

    /** Return the length of this vector. */
    public double length()
    {
        return Math.sqrt(lengthSquared());
    }

    /** True if this vector's length is zero. */
    public boolean isZero()
    {
        boolean ret = true;
        for (int i=0; i < this.dim(); i++) {
            if (this.get(i) != 0) {
                ret = false;
            }
        }
        return ret;
    }

    /** Return a normalized version of this vector.  The zero
      * vector is returned unchanged. */
    public Vectorf normalize()
    {
        if (isZero()) {
            return this;
        }
        float normFactor = (float)(1.0 / this.length());
        return this.times(normFactor);
    }

    /** Return dot product of 'this' and 'v'. */
    public float dot(Vectorf v)
    {
        assert(this.dim() == v.dim());
        float ret = 0;
        for (int i=0; i < this.dim(); i++) {
            ret += this.get(i) * v.get(i);
        }
        return ret;
    }
}

// EOF
