// Matrix3d.java
// See copyright.txt for license and terms of use.

package util;

/** Represent an immutable 3x3 matrix of double. */
public class Matrix3d {
    // ---- Constants ----
    /** How close the result must be for a test to pass. */
    private static final double TEST_THRESHOLD = 0.0001;

    // ---- Instance data ----
    /** Underlying matrix data. */
    private Matrixd mat;

    // ---- Methods ----
    public Matrix3d(double a11, double a12, double a13,
                    double a21, double a22, double a23,
                    double a31, double a32, double a33)
    {
        this.mat = new Matrixd(3, 3,
            new double[] { a11, a12, a13,
                           a21, a22, a23,
                           a31, a32, a33 });
    }

    public Matrix3d(Matrixd m)
    {
        assert(m.C() == 3 && m.R() == 3);
        this.mat = m;
    }

    public Matrix3d(Matrix3f m)
    {
        this.mat = new Matrixd(m.getUnder());
    }

    public double a11() { return this.mat.get(0,0); }
    public double a12() { return this.mat.get(0,1); }
    public double a13() { return this.mat.get(0,2); }
    public double a21() { return this.mat.get(1,0); }
    public double a22() { return this.mat.get(1,1); }
    public double a23() { return this.mat.get(1,2); }
    public double a31() { return this.mat.get(2,0); }
    public double a32() { return this.mat.get(2,1); }
    public double a33() { return this.mat.get(2,2); }

    public String toString()
    {
        return this.mat.toString();
    }

    /** True if 'm' has the same dimensions, and all entries have
      * an absolute value difference of 'threshold' or less. */
    public boolean equalsWithin(Matrix3d m, double threshold)
    {
        return this.mat.equalsWithin(m.mat, threshold);
    }

    /** Left-multiply this matrix by 'v' and yield the result. */
    public Vector3d times(Vector3d v)
    {
        return new Vector3d(this.mat.times(v.getUnder()));
    }

    /** Left-multiply this matrix by matrix 'm'. */
    public Matrix3d times(Matrix3d m)
    {
        return new Matrix3d(this.mat.times(m.mat));
    }

    /** Multiply this matrix by a scalar. */
    public Matrix3d times(double x)
    {
        return new Matrix3d(this.mat.times(x));
    }

    /** Add this matrix to matrix 'm'. */
    public Matrix3d plus(Matrix3d m)
    {
        return new Matrix3d(this.mat.plus(m.mat));
    }

    /** Return the 3x3 identity matrix. */
    public static Matrix3d identity()
    {
        return new Matrix3d(Matrixd.identity(3));
    }

    /** Return the inverse of this matrix, or null if it is not invertible. */
    public Matrix3d inverse()
    {
        Matrixd i = this.mat.inverse();
        if (i == null) {
            return null;
        }
        else {
            return new Matrix3d(i);
        }
    }

    /** Yield a matrix that, when multiplied by a vector, rotates that
      * vector by 'radians' around 'axis'. */
    public static Matrix3d rotateRad(double radians, Vector3d axis)
    {
        // Normalize the rotation axis.
        if (axis.isZero()) {
            return Matrix3d.identity();
        }
        double axisLength = axis.length();
        double x = axis.x() / axisLength;
        double y = axis.y() / axisLength;
        double z = axis.z() / axisLength;

        // Compute the upper 3x3 of this matrix:
        // https://www.khronos.org/registry/OpenGL-Refpages/gl2.1/
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        return new Matrix3d(
            x*x*(1-c)+c,
            x*y*(1-c)-z*s,
            x*z*(1-c)+y*s,

            y*x*(1-c)+z*s,
            y*y*(1-c)+c,
            y*z*(1-c)-x*s,

            z*x*(1-c)-y*s,
            z*y*(1-c)+x*s,
            z*z*(1-c)+c);
    }

    /** Return the eigenvector with real eigenvalue and largest
      * eigenvalue.  This might be zero if there are no such
      * eigenvectors. */
    public Vector3d largestRealEigenvector()
    {
        return new Vector3d(this.mat.largestRealEigenvector());
    }

    // --------------------------- Test code ------------------------------
    private static void testOneMatrixInverse(Matrix3d m)
    {
        Matrix3d I = Matrix3d.identity();
        Matrix3d inv = m.inverse();

        Matrix3d prod = inv.times(m);
        if (!prod.equalsWithin(I, TEST_THRESHOLD)) {
            System.err.println("m: "+m);
            System.err.println("inv: "+inv);
            System.err.println("prod: "+prod);
            throw new RuntimeException("matrix inverse test 1 failed");
        }

        prod = m.times(inv);
        if (!prod.equalsWithin(I, TEST_THRESHOLD)) {
            System.err.println("m: "+m);
            System.err.println("inv: "+inv);
            System.err.println("prod: "+prod);
            throw new RuntimeException("matrix inverse test 2 failed");
        }

        Matrix3d invInv = inv.inverse();
        prod = inv.times(invInv);
        if (!prod.equalsWithin(I, TEST_THRESHOLD)) {
            System.err.println("m: "+m);
            System.err.println("inv: "+inv);
            System.err.println("invInv: "+invInv);
            System.err.println("prod: "+prod);
            throw new RuntimeException("matrix inverse test 3 failed");
        }

        prod = invInv.times(inv);
        if (!prod.equalsWithin(I, TEST_THRESHOLD)) {
            System.err.println("m: "+m);
            System.err.println("inv: "+inv);
            System.err.println("invInv: "+invInv);
            System.err.println("prod: "+prod);
            throw new RuntimeException("matrix inverse test 4 failed");
        }
    }

    private static void testMatrixInverse()
    {
        testOneMatrixInverse(Matrix3d.identity());
        testOneMatrixInverse(Matrix3d.identity().times(2));

        double angle = Math.PI/2;
        testOneMatrixInverse(Matrix3d.rotateRad(angle, new Vector3d(1, 0, 0)));
        testOneMatrixInverse(Matrix3d.rotateRad(angle, new Vector3d(0, 1, 0)));
        testOneMatrixInverse(Matrix3d.rotateRad(angle, new Vector3d(0, 0, 1)));

        angle = Math.PI/4;
        testOneMatrixInverse(Matrix3d.rotateRad(angle, new Vector3d(1, 0, 0)));
        testOneMatrixInverse(Matrix3d.rotateRad(angle, new Vector3d(0, 1, 0)));
        testOneMatrixInverse(Matrix3d.rotateRad(angle, new Vector3d(0, 0, 1)));

        // This is just some matrix that arose while doing stuff
        // with EarthShape.
        testOneMatrixInverse(new Matrix3d(
            -0.29275739192962646, 0.5579640865325928, 0.434147447347641,
            -0.5590898990631104, -0.18272608518600464, -0.1395774930715561,
            -0.4326966404914856, -0.14401228725910187, -0.11003702878952026));

        // This is ostensibly the inverse of a matrix close to the above
        // matrix that an online calculator came up with, but I did not
        // feed it all of the digits.
        testOneMatrixInverse(new Matrix3d(
            -0.5025125628140716, -3.517587939698681, 2.512562814070593,
            0.7537688442209383, -544.7236180904614, 696.2311557789062,
            1.0050251256283578, 707.0351758794089, -905.025125628156));
    }

    private static void testOneEigenvector(Matrix3d m)
    {
        System.out.println("m: "+m);

        Vector3d v = m.largestRealEigenvector();
        System.out.println("v: "+v);

        Vector3d v2 = m.times(v);
        System.out.println("v2: "+v2);

        if (v2.minus(v).length() > TEST_THRESHOLD) {
            throw new RuntimeException("testOneEigenvector failed");
        }
    }

    private static void testEigenvalues()
    {
        testOneEigenvector(Matrix3d.identity());

        double angle = Math.PI/2;
        testOneEigenvector(Matrix3d.rotateRad(angle, new Vector3d(1, 0, 0)));
        testOneEigenvector(Matrix3d.rotateRad(angle, new Vector3d(0, 1, 0)));
        testOneEigenvector(Matrix3d.rotateRad(angle, new Vector3d(0, 0, 1)));
    }

    public static void main(String[] args)
    {
        testMatrixInverse();
        testEigenvalues();
        System.out.println("Matrix3d tests passed");
    }
}

// EOF
