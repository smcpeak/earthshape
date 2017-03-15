// Vector3d.java
// See copyright.txt for license and terms of use.

package util;

/** A immutable 3D vector of double.  This class exists, as opposed to
  * directly using Vectord, to provide some degree of dimensional safety
  * for vector operations. */
public class Vector3d {
    // ---- Instance data ----
    /** Underlying vector components: x, y, z. */
    private Vectord under;

    // ---- Methods ----
    public Vector3d(double x, double y, double z)
    {
        this.under = new Vectord(new double[] {x,y,z});
    }

    public Vector3d(Vectord v3)
    {
        assert(v3.dim() == 3);
        this.under = v3;
    }

    /** Make a Vector3d out of a Vector3f. */
    public Vector3d(Vector3f vf)
    {
        this.under = new Vectord(vf.getUnder());
    }

    /** Make a Vector3f out of a Vector3d. */
    public Vector3f toVector3f()
    {
        return new Vector3f(under.toVectorf());
    }

    public double x()
    {
        return this.under.get(0);
    }

    public double y()
    {
         return this.under.get(1);
    }

    public double z()
    {
        return this.under.get(2);
    }

    /** Get the underlying Vectord, which is useful to pass to routines
      * that accept dynamically sized vectors. */
    public Vectord getUnder()
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
    public double[] getArray()
    {
        return this.under.getArray();
    }

    /** Return sum of 'this' and 'v'. */
    public Vector3d plus(Vector3d v)
    {
        return new Vector3d(this.under.plus(v.under));
    }

    /** Return 'this' minus 'v'. */
    public Vector3d minus(Vector3d v)
    {
        return new Vector3d(this.under.minus(v.under));
    }

    /** Return 'this' times scalar 's'. */
    public Vector3d times(double s)
    {
        return new Vector3d(this.under.times(s));
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
    public Vector3d normalize()
    {
        return new Vector3d(this.under.normalize());
    }

    /** Return this vector after rotating by 'degrees' about 'axis'.
      * Rotation follows right-hand rule.  The axis vector is not
      * assumed to be normalized yet. */
    public Vector3d rotate(double degrees, Vector3d axis)
    {
        Matrix3d m = Matrix3d.rotate(FloatUtil.degreesToRadians(degrees), axis);
        return m.times(this);
    }

    /** Rotate about 'axisAndAngle', interpreting its length as
      * the rotation angle in degrees. */
    public Vector3d rotateAA(Vector3d axisAndAngle)
    {
        double degrees = axisAndAngle.length();
        if (degrees == 0) {
            return this;
        }
        return this.rotate(degrees, axisAndAngle);
    }

    /** Return dot product of 'this' and 'v'. */
    public double dot(Vector3d v)
    {
        return this.under.dot(v.under);
    }

    /** Return the separation angle between 'this' and 'v' in degrees. */
    public double separationAngleDegrees(Vector3d v)
    {
        return this.under.separationAngleDegrees(v.under);
    }

    /** Return 'this' projected onto 'u', which is assumed to be a
      * unit vector. */
    public Vector3d projectOntoUnitVector(Vector3d u)
    {
        return u.times(this.dot(u));
    }

    /** Return 'this' projected onto 'other', which need not be a unit
      * vector, although it cannot be zero. */
    public Vector3d projectOnto(Vector3d other)
    {
        return this.projectOntoUnitVector(other.normalize());
    }

    /** Return 'this' cross 'v'. */
    public Vector3d cross(Vector3d v)
    {
        return new Vector3d(y()*v.z() - z()*v.y(),
                            z()*v.x() - x()*v.z(),
                            x()*v.y() - y()*v.x());
    }

    /** Return a rotation vector that transforms 'this' to 'dest', ignoring
      * their lengths.  The vector is the rotation axis and its length
      * is the rotation angle in degrees. */
    public Vector3d rotationToBecome(Vector3d dest)
    {
        // First ensure the vectors are normalized.
        dest = dest.normalize();
        Vector3d src = this.normalize();

        // Now their cross product gives the axis and the
        // sine of the desired length.
        Vector3d v = src.cross(dest);

        // Fix the length.
        double degrees = FloatUtil.radiansToDegrees(
            (double)Math.asin(v.length()));
        return v.normalize().times(degrees);
    }

    /** Compose two rotation vectors, where each has magnitude equal to
      * the rotation angle in degrees. */
    public static Vector3d composeRotations(Vector3d first, Vector3d second)
    {
        // The computation here is based on this question and answer:
        // http://math.stackexchange.com/questions/382760/composition-of-two-axis-angle-rotations

        // First rotation angle (in radians) and axis.
        double beta = FloatUtil.degreesToRadians(first.length());
        Vector3d m = first.normalize();

        // Second rotation angle and axis.
        double alpha = FloatUtil.degreesToRadians(second.length());
        Vector3d l = second.normalize();

        // Combined rotation angle, in radians.  This guards against
        // yielding NaN near boundaries by clamping the difference to [-1,1].
        double gamma = FloatUtil.acosRad(Math.cos(alpha/2) * Math.cos(beta/2) -
                                         Math.sin(alpha/2) * Math.sin(beta/2) * l.dot(m)) * 2;

        // Map very small angles to zero directly (rather than divide by zero).
        if (gamma < 1e-20) {
            return new Vector3d(0,0,0);
        }

        // Combined rotation axis, which should be a unit vector.
        Vector3d n = ( l.times(Math.sin(alpha/2) * Math.cos(beta/2)).plus(
                       m.times(Math.cos(alpha/2) * Math.sin(beta/2)).plus(
                       (l.cross(m)).times(Math.sin(alpha/2) * Math.sin(beta/2)) ))).times(1/
                     // ----------------------------------------------------------
                                      Math.sin(gamma/2));

        // Put axis and angle back together.
        return n.times(FloatUtil.radiansToDegrees(gamma));
    }


    /** Result of call to 'getClosestApproach'. */
    public static class ClosestApproach {
        /** Minimum distance between the lines. */
        public double minimumDistance;

        /** Location on line1 closest to line2, or null if the
          * lines are parallel. */
        public Vector3d line1Closest = null;

        /** Location on line2 closest to line1, or null if the
          * lines are parallel. */
        public Vector3d line2Closest = null;

        /** Set distance only, for when lines are parallel. */
        public ClosestApproach(double minimumDistance_)
        {
            this.minimumDistance = minimumDistance_;
        }

        /** Set all three components. */
        public ClosestApproach(
            double minimumDistance_,
            Vector3d line1Closest_,
            Vector3d line2Closest_)
        {
            this.minimumDistance = minimumDistance_;
            this.line1Closest = line1Closest_;
            this.line2Closest = line2Closest_;
        }

        /** Return true if 'this' approximately equals 'obj', to within
          * the specified 'threshold' for each element. */
        public boolean approxEquals(ClosestApproach obj, double threshold)
        {
            if (Math.abs(this.minimumDistance - obj.minimumDistance) > threshold) {
                return false;
            }

            if (! ((this.line1Closest==null) == (obj.line1Closest==null) &&
                   (this.line2Closest==null) == (obj.line2Closest==null)) ) {
                return false;
            }

            // Slight optimization: avoid sqrt calls to check lengths.
            double threshSquared = threshold * threshold;

            if (this.line1Closest != null &&
                this.line1Closest.minus(obj.line1Closest).lengthSquared() > threshSquared)
            {
                return false;
            }

            if (this.line2Closest != null &&
                this.line2Closest.minus(obj.line2Closest).lengthSquared() > threshSquared)
            {
                return false;
            }

            return true;
        }

        public String toString()
        {
            return "CA(min="+this.minimumDistance+
                   ", L1C="+this.line1Closest+
                   ", L2C="+this.line2Closest+
                   ")";
        }
    }

    /** Let line1 be the set of points "p1 + s*u1" for real numbers 's',
      * and line2 be "p2 + t*u2" for real numbers 't'.  This routine
      * determines the minimum distance between the lines and the
      * locations on each line where that minimum distance occurs.
      * 'u1' and 'u2' should be unit vectors. */
    public static ClosestApproach getClosestApproach(
        Vector3d p1, Vector3d u1,
        Vector3d p2, Vector3d u2)
    {
        Vector3d difference = p2.minus(p1);

        // Get the normal to the plane containing both unit vectors.
        Vector3d normal = u1.cross(u2);
        if (normal.isZero()) {
            // The lines are parallel.  Project the difference vector
            // onto u1 (u2 would also work).
            Vector3d parallelDifference = difference.projectOntoUnitVector(u1);

            // Now subtract to get the orthogonal difference.
            Vector3d orthogonalDifference = difference.minus(parallelDifference);

            // The length of that vector is their separation.
            return new ClosestApproach(orthogonalDifference.length());
        }

        // Project to get orthogonal difference.
        Vector3d orthogonalDifference = difference.projectOntoUnitVector(normal);

        // Subtract that from p2 to yield a line (with u2) coplanar with p1.
        Vector3d p2Coplanar = p2.minus(orthogonalDifference);

        // Get the component of u1 orthogonal to u2 (hence going toward line2).
        Vector3d u1TowardLine2 = u1.minus(u1.projectOntoUnitVector(u2));

        // Get the component of p2-p1 (coplanar) going toward line2.
        Vector3d p1ToP2Ortho = p2Coplanar.minus(p1).projectOnto(u1TowardLine2);

        // Compute 's', the number of times we must travel along 'u1',
        // starting at 'p1', to reach the variant of line2 that is coplanar.
        double s = p1ToP2Ortho.length() / u1TowardLine2.length();

        // Flip the sign if they're in opposite directions.  (I suspect this
        // can be done without such an inelegant step...)
        if (u1TowardLine2.dot(p1ToP2Ortho) < 0) {
            s = -s;
        }

        // Repeat the last four steps for u2 going toward line1.
        Vector3d u2TowardLine1 = u2.minus(u2.projectOntoUnitVector(u1));
        Vector3d p2ToP1Ortho = p1.minus(p2Coplanar).projectOnto(u2TowardLine1);
        double t = p2ToP1Ortho.length() / u2TowardLine1.length();
        if (u2TowardLine1.dot(p2ToP1Ortho) < 0) {
            t = -t;
        }

        // Package the results.
        return new ClosestApproach(
            orthogonalDifference.length(),
            p1.plus(u1.times(s)),
            p2.plus(u2.times(t)));
    }

    private static void testOneClosestApproach(
        Vector3d p1, Vector3d u1,
        Vector3d p2, Vector3d u2,
        ClosestApproach expect)
    {
        ClosestApproach actual = Vector3d.getClosestApproach(p1, u1, p2, u2);
        if (!expect.approxEquals(actual, 0.001)) {
            System.err.println("testOneClosestApproach failed:");
            System.err.println("  p1: "+p1);
            System.err.println("  u1: "+u1);
            System.err.println("  p2: "+p2);
            System.err.println("  u2: "+u2);
            System.err.println("  expect: "+expect);
            System.err.println("  actual: "+actual);
            throw new RuntimeException("Unit test failure");
        }
    }

    private static void testClosestApproach()
    {
        // Same point, same direction.
        testOneClosestApproach(
            new Vector3d(0,0,0), new Vector3d(1,0,0),
            new Vector3d(0,0,0), new Vector3d(1,0,0),
            new ClosestApproach(0));

        // Different points in line with same direction.
        testOneClosestApproach(
            new Vector3d(1,0,0), new Vector3d(1,0,0),
            new Vector3d(0,0,0), new Vector3d(1,0,0),
            new ClosestApproach(0));

        // Different points not in line with same direction.
        testOneClosestApproach(
            new Vector3d(0,1,0), new Vector3d(1,0,0),
            new Vector3d(0,0,0), new Vector3d(1,0,0),
            new ClosestApproach(1));

        // Same point, different directions.
        testOneClosestApproach(
            new Vector3d(0,0,0), new Vector3d(0,1,0),
            new Vector3d(0,0,0), new Vector3d(1,0,0),
            new ClosestApproach(0, new Vector3d(0,0,0), new Vector3d(0,0,0)));

        // Different points, different directions, coplanar lines.
        testOneClosestApproach(
            new Vector3d(0,1,0), new Vector3d(0,1,0),
            new Vector3d(1,0,0), new Vector3d(1,0,0),
            new ClosestApproach(0, new Vector3d(0,0,0), new Vector3d(0,0,0)));

        // Different points, different directions, coplanar lines,
        // solution parameters have opposite sign.
        testOneClosestApproach(
            new Vector3d(0,-1,0), new Vector3d(0,1,0),
            new Vector3d(1,0,0), new Vector3d(1,0,0),
            new ClosestApproach(0, new Vector3d(0,0,0), new Vector3d(0,0,0)));

        // Not coplanar.
        testOneClosestApproach(
            new Vector3d(0,-1,1), new Vector3d(0,1,0),
            new Vector3d(1,0,0), new Vector3d(1,0,0),
            new ClosestApproach(1, new Vector3d(0,0,1), new Vector3d(0,0,0)));

        // Another not copanar.
        testOneClosestApproach(
            new Vector3d(5,-1,1), new Vector3d(0,1,0),
            new Vector3d(1,7,0), new Vector3d(1,0,0),
            new ClosestApproach(1, new Vector3d(5,7,1), new Vector3d(5,7,0)));
    }

    public static void main(String args[])
    {
        Vector3d.testClosestApproach();
        System.out.println("Vector3d tests passed");
    }
}

// EOF
