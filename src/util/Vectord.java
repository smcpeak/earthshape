// Vectord.java
// See copyright.txt for license and terms of use.

package util;

/** Arbitrary-length immutable vector of double. */
public class Vectord {
    // ---- Instance data ----
    /** Array of vector components, in order. */
    private double[] vals;

    // ---- Methods ----
    /** This takes ownership of the passed values.  The caller should
      * not retain a reference to the 'vals_' array. */
    public Vectord(double[] vals_)
    {
        this.vals = vals_;
    }

    /** Make a vector of doubles out of a vector of floats. */
    public Vectord(Vectorf vf)
    {
        this.vals = new double[vf.dim()];
        for (int i=0; i < this.vals.length; i++) {
            this.vals[i] = vf.get(i);
        }
    }

    /** Return the number of components (dimensions) of this vector. */
    public int dim()
    {
        return vals.length;
    }

    /** Get one component of the vector.  'd' must be in
      * [0,dim()-1]. */
    public double get(int d)
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
    public double[] getArray()
    {
        return vals;
    }

    /** Return sum of 'this' and 'v'. */
    public Vectord plus(Vectord v)
    {
        assert(this.dim() == v.dim());
        double[] ret = new double[this.dim()];
        for (int i=0; i < this.dim(); i++) {
            ret[i] = this.get(i) + v.get(i);
        }
        return new Vectord(ret);
    }

    /** Return 'this' minus 'v'. */
    public Vectord minus(Vectord v)
    {
        assert(this.dim() == v.dim());
        double[] ret = new double[this.dim()];
        for (int i=0; i < this.dim(); i++) {
            ret[i] = this.get(i) - v.get(i);
        }
        return new Vectord(ret);
    }

    /** Return 'this' times scalar 's'. */
    public Vectord times(double s)
    {
        double[] ret = new double[this.dim()];
        for (int i=0; i < this.dim(); i++) {
            ret[i] = this.get(i) * s;
        }
        return new Vectord(ret);
    }

    /** Return the square of the length of this vector. */
    public double lengthSquared()
    {
        double ret = 0;
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
    public Vectord normalize()
    {
        if (isZero()) {
            return this;
        }
        double normFactor = 1.0 / this.length();
        return this.times(normFactor);
    }

    /** Return dot product of 'this' and 'v'. */
    public double dot(Vectord v)
    {
        assert(this.dim() == v.dim());
        double ret = 0;
        for (int i=0; i < this.dim(); i++) {
            ret += this.get(i) * v.get(i);
        }
        return ret;
    }

    /** Calculate the separation angle between 'this' and 'v' by
      * first normalizing both to high precision and then using
      * the dot product and inverse cosine. */
    public double separationAngleDegrees(Vectord v)
    {
        assert(this.dim() == v.dim());

        double thisLength = this.length();
        double vLength = v.length();

        double dot = 0;
        for (int i=0; i < this.dim(); i++) {
            dot += (this.get(i) / thisLength) * (v.get(i) / vLength);
        }

        return FloatUtil.acosDeg(dot);
    }

    /** Return 'this' cross 'v', assuming both are 3 dimensional.
      * (If I had a Vector3d, I would put it there, but for the
      * moment I am being lazy and do not have that class.) */
    public Vectord cross(Vectord v)
    {
        double[] ret = new double[3];
        ret[0] = this.get(1)*v.get(2) - this.get(2)*v.get(1);
        ret[1] = this.get(2)*v.get(0) - this.get(0)*v.get(2);
        ret[2] = this.get(0)*v.get(1) - this.get(1)*v.get(0);
        return new Vectord(ret);
    }

}

// EOF
