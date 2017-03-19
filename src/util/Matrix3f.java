// Matrix3f.java
// See copyright.txt for license and terms of use.

package util;

/** Represent an immutable 3x3 matrix of float. */
public class Matrix3f {
    // ---- Instance data. ----
    /** Underlying matrix data. */
    private Matrixf mat;

    // ---- Methods ----
    public Matrix3f(float a11, float a12, float a13,
                    float a21, float a22, float a23,
                    float a31, float a32, float a33)
    {
        this.mat = new Matrixf(3, 3,
            new float[] { a11, a12, a13,
                          a21, a22, a23,
                          a31, a32, a33 });
    }

    public Matrix3f(Matrixf m)
    {
        assert(m.C() == 3 && m.R() == 3);
        this.mat = m;
    }

    public float a11() { return this.mat.get(0,0); }
    public float a12() { return this.mat.get(0,1); }
    public float a13() { return this.mat.get(0,2); }
    public float a21() { return this.mat.get(1,0); }
    public float a22() { return this.mat.get(1,1); }
    public float a23() { return this.mat.get(1,2); }
    public float a31() { return this.mat.get(2,0); }
    public float a32() { return this.mat.get(2,1); }
    public float a33() { return this.mat.get(2,2); }

    public Matrixf getUnder()
    {
        return this.mat;
    }

    @Override
    public String toString()
    {
        return this.mat.toString();
    }

    /** Left-multiply this matrix by 'v' and yield the result. */
    public Vector3f times(Vector3f v)
    {
        return new Vector3f(this.mat.times(v.getUnder()));
    }

    /** Right-multiply matrix 'm' by vector 'v'. */
    public static Vector3f multiply(Vector3f v, Matrix3f m)
    {
        return new Vector3f(Matrixf.multiply(v.getUnder(), m.mat));
    }

    /** Left-multiply this matrix by matrix 'm' and yield result. */
    public Matrix3f times(Matrix3f m)
    {
        return new Matrix3f(this.mat.times(m.mat));
    }

    /** Return the 3x3 identity matrix. */
    public static Matrix3f identity()
    {
        return new Matrix3f(Matrixf.identity(3));
    }

    /** Yield a matrix that, when multiplied by a vector, rotates that
      * vector by 'radians' around 'axis'. */
    public static Matrix3f rotateRad(double radians, Vector3f axis)
    {
        // Normalize the rotation axis.
        if (axis.isZero()) {
            return Matrix3f.identity();
        }
        double axisLength = axis.length();
        double x = axis.x() / axisLength;
        double y = axis.y() / axisLength;
        double z = axis.z() / axisLength;

        // https://www.khronos.org/registry/OpenGL-Refpages/gl2.1/xhtml/glRotate.xml
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        return new Matrix3f(
            (float)(x*x*(1-c)+c),
            (float)(x*y*(1-c)-z*s),
            (float)(x*z*(1-c)+y*s),

            (float)(y*x*(1-c)+z*s),
            (float)(y*y*(1-c)+c),
            (float)(y*z*(1-c)-x*s),

            (float)(z*x*(1-c)-y*s),
            (float)(z*y*(1-c)+x*s),
            (float)(z*z*(1-c)+c));
    }

    /** Yield a matrix that, when multiplied by a vector, rotates that
      * vector by 'degrees' around 'axis'. */
    public static Matrix3f rotateDeg(double degrees, Vector3f axis)
    {
        return rotateRad(FloatUtil.degreesToRadians(degrees), axis);
    }

    public static void p(String s)
    {
        System.out.println(s);
    }

    public static double diffLen(Vector3f a, Vector3f b)
    {
        return a.minus(b).length();
    }

    /** This uses the "inverse iteration" method to iteratively
      * find an eigenvector of 'this'.  It is not an
      * efficient algorithm but it is very simple. */
    public Vector3f dominantEigenvectorByInverseIteration(
        Vector3f initEigenvector, float initEigenvalue)
    {
        // Do this with doubles due to frequent renormalization.
        Matrix3d A = new Matrix3d(this);
        Vector3d prev = new Vector3d(initEigenvector);
        Vector3d b = prev;

        Matrix3d A_minus_mu_I = A.plus(Matrix3d.identity().times(-initEigenvalue));
        Matrix3d inverse = A_minus_mu_I.inverse();

        for (int iters=0; iters < 1000; iters++) {
            Vector3d product = inverse.times(prev);
            double length = product.length();
            if (length == 0) {
                p("dominantEigenvector: hit a 0 length intermediate vector!");
                return prev.toVector3f();     // Not right.
            }
            b = product.times(1/length);
            double diff = b.minus(prev).lengthSquared();
            if (diff < 1e-7) {
                // We'll call that close enough.
                p("dominantEigenvector: iters="+iters+", diff="+diff+", found="+b);
                return b.toVector3f();
            }
            prev = b;
        }
        p("dominantEigenvector: hit iteration limit!");
        return b.toVector3f();
    }

    public static void main(String[] args)
    {
        Vector3f eastCA = new Vector3f(1, 0, 0);
        Vector3f upCA = new Vector3f(0, 1, 0);
        Vector3f northCA = new Vector3f(0, 0, -1);

        float dubhe_ca_az = 36.9f;
        float dubhe_ca_el = 44.8f;

        float sirius_ca_az = 181.1f;
        float sirius_ca_el = 35.2f;

        float dubhe_ut_az = 36.4f;
        float dubhe_ut_el = 49.1f;

        float sirius_ut_az = 191.5f;
        float sirius_ut_el = 34.4f;

        Vector3f v1 = northCA.rotateDeg(dubhe_ca_el, eastCA);
        p("v1: "+v1);

        Vector3f v2 = v1.rotateDeg(-dubhe_ca_az, upCA);
        p("v2: "+v2);

        // Unit vector pointing from California to Dubhe in local
        // California coordinates.
        Vector3f dubhe_ca =
            rotateDeg(-dubhe_ca_az, upCA).times(rotateDeg(dubhe_ca_el, eastCA)).times(northCA);
        p("dubhe_ca: "+dubhe_ca);

        assert(v2.minus(dubhe_ca).length() < 0.00001);

        // CA to Sirius.
        Vector3f sirius_ca =
            rotateDeg(-sirius_ca_az, upCA).times(rotateDeg(sirius_ca_el, eastCA)).times(northCA);
        p("sirius_ca: "+sirius_ca);

        // Utah to Dubhe in Utah coordinates.
        Vector3f dubhe_ut =
            rotateDeg(-dubhe_ut_az, upCA).times(rotateDeg(dubhe_ut_el, eastCA)).times(northCA);
        p("dubhe_ut: "+dubhe_ut);

        // Utah to Sirius.
        Vector3f sirius_ut =
            rotateDeg(-sirius_ut_az, upCA).times(rotateDeg(sirius_ut_el, eastCA)).times(northCA);
        p("sirius_ut: "+sirius_ut);

        // Utah "up" vector, which will be transformed with the others
        // so we can see where it ends up.
        Vector3f utah_up = new Vector3f(0, 1, 0);
        p("utah_up: "+utah_up);

        // Cross the UT and CA Dubhe vectors to get an angle and axis
        // that can be applied to both Utah observations to align the
        // Dubhe observations.
        Vector3f dubhe_ut_cross_ca = dubhe_ut.cross(dubhe_ca);
        p("dubhe_ut_cross_ca: "+dubhe_ut_cross_ca);
        double dubhe_adjust_angle = FloatUtil.asinDeg(dubhe_ut_cross_ca.length());
        p("dubhe_adjust_angle: "+dubhe_adjust_angle);
        Vector3f dubhe_adjust_axis = dubhe_ut_cross_ca.normalize();
        p("dubhe_adjust_axis: "+dubhe_adjust_axis);

        // Calculate a rotation matrix for that angle and axis.
        Matrix3f rot1 = Matrix3f.rotateDeg(dubhe_adjust_angle, dubhe_adjust_axis);
        p("rot1: "+rot1);

        // Aside: check the inverse.
        {
            Matrix3d rot1d = new Matrix3d(rot1);
            Matrix3d rot1di = rot1d.inverse();
            p("rot1di: "+rot1di);
            if (rot1di != null) {
                Matrix3d prod1 = rot1d.times(rot1di);
                p("prod1: "+prod1);
                Matrix3d prod2 = rot1di.times(rot1d);
                p("prod2: "+prod2);
            }
        }

        // Apply it to the UT vectors.
        Vector3f dubhe_ut_rot1 = rot1.times(dubhe_ut);
        p("dubhe_ut_rot1: "+dubhe_ut_rot1+
          ", diff="+diffLen(dubhe_ut_rot1, dubhe_ca));
        Vector3f sirius_ut_rot1 = rot1.times(sirius_ut);
        p("sirius_ut_rot1: "+sirius_ut_rot1);
        Vector3f utah_up_rot1 = rot1.times(utah_up);
        p("utah_up_rot1: "+utah_up_rot1);

        // Project the Sirius observations onto the plane perpendicular
        // to the now-aligned Dubhe vector.
        Vector3f sirius_ut_rot1_proj = sirius_ut_rot1.orthogonalComponentToUnitVector(dubhe_ut_rot1);
        p("sirius_ut_rot1_proj: "+sirius_ut_rot1_proj);
        Vector3f sirius_ca_proj = sirius_ca.orthogonalComponentToUnitVector(dubhe_ut_rot1);
        p("sirius_ca_proj: "+sirius_ca_proj);

        // Normalize them in preparation to calculate the rotation
        // between them.
        Vector3f sirius_ut_rot1_proj_norm = sirius_ut_rot1_proj.normalize();
        p("sirius_ut_rot1_proj_norm: "+sirius_ut_rot1_proj_norm);
        Vector3f sirius_ca_proj_norm = sirius_ca_proj.normalize();
        p("sirius_ca_proj_norm: "+sirius_ca_proj_norm);

        // Calculate the rotation to best align Sirius.
        Vector3f sirius_ut_rot1_proj_cross_ca = sirius_ut_rot1_proj_norm.cross(sirius_ca_proj_norm);
        p("sirius_ut_rot1_proj_cross_ca: "+sirius_ut_rot1_proj_cross_ca);
        double sirius_adjust_angle = FloatUtil.asinDeg(sirius_ut_rot1_proj_cross_ca.length());
        p("sirius_adjust_angle: "+sirius_adjust_angle);
        Vector3f sirius_adjust_axis = sirius_ut_rot1_proj_cross_ca.normalize();
        p("sirius_adjust_axis: "+sirius_adjust_axis);

        // Calculate that as a rotation matrix.
        Matrix3f rot2 = Matrix3f.rotateDeg(sirius_adjust_angle, sirius_adjust_axis);
        p("rot2: "+rot2);

        // Apply it as well to the Utah observations.
        Vector3f dubhe_ut_rot2 = rot2.times(dubhe_ut_rot1);
        p("dubhe_ut_rot2: "+dubhe_ut_rot2+
          ", sep="+dubhe_ut_rot2.separationAngleDegrees(dubhe_ca));
        Vector3f sirius_ut_rot2 = rot2.times(sirius_ut_rot1);
        p("sirius_ut_rot2: "+sirius_ut_rot2+
          ", sep="+sirius_ut_rot2.separationAngleDegrees(sirius_ca));
        Vector3f utah_up_rot2 = rot2.times(utah_up_rot1);
        p("utah_up_rot2: "+utah_up_rot2);

        // Combine the rotations into one matrix, 1 then 2.
        Matrix3f rot12 = rot2.times(rot1);
        p("rot12: "+rot12);

        // Confirm that it has the proper cumulative effect.
        Vector3f dubhe_ut_rot12 = rot12.times(dubhe_ut);
        p("dubhe_ut_rot12: "+dubhe_ut_rot12+
          ", diff="+diffLen(dubhe_ut_rot12, dubhe_ut_rot2));
        Vector3f sirius_ut_rot12 = rot12.times(sirius_ut);
        p("sirius_ut_rot12: "+sirius_ut_rot12+
          ", diff="+diffLen(sirius_ut_rot12, sirius_ut_rot2));

        // How does the combined rotation affect celestial North?
        Vector3f celestial_north_ca = northCA.rotateDeg(38, eastCA);
        p("celestial_north_ca: "+celestial_north_ca);
        Vector3f celestial_north_ca_rot12 = rot12.times(celestial_north_ca);
        p("celestial_north_ca_rot12: "+celestial_north_ca_rot12+
          ", diff="+diffLen(celestial_north_ca, celestial_north_ca_rot12)+
          ", sep="+celestial_north_ca.separationAngleDegrees(celestial_north_ca_rot12));

        // From the above, we find that in fact celestial North is
        // nearly unchanged, so let's use it as a seed to find the
        // true dominant eigenvector.
        Vector3f eigenvector = rot12.dominantEigenvectorByInverseIteration(celestial_north_ca, 1);
        p("eigenvector: "+eigenvector);
        double eigenvalue = rot12.times(eigenvector).length() / eigenvector.length();
        p("eigenvalue: "+eigenvalue);

        // Then use that to compute the rotation angle by
        // doing a trial rotation of an orthogonal vector.
        Vector3f orthogonal = eigenvector.cross(eastCA);
        p("orthogonal: "+orthogonal);
        Vector3f orthogonal_rot12 = rot12.times(orthogonal);
        p("orthogonal_rot12: "+orthogonal_rot12);
        double rot12_angle = orthogonal_rot12.separationAngleDegrees(orthogonal);
        p("rot12_angle: "+rot12_angle);

        // OBSERVATION: rot12_angle is very close to 9 degrees, which
        // is the different in longitude, meaning that if we repeat
        // this process then after 360 degrees of longitude we will be
        // back to the same surface orientation.  That is, we have a
        // slice of the surface that looks like a truncated cone.  (And
        // of course if we go North or South, the cones will start to
        // look like parts of a a sphere.)

        // Examine its effect on "up".  The resulting angle tells us
        // how the direction to the center changes, i.e., how much of
        // a spherical surface is subtended by the travel.
        Vector3f up_rot12 = rot12.times(upCA);
        p("up_rot12: "+up_rot12);
        double up_rot12_angle = up_rot12.separationAngleDegrees(upCA);
        p("up_rot12_angle: "+up_rot12_angle);

        // Distance between (38N,122W) and (38N,113W).  Here I calculate
        // it, but this could be measured directly.
        double travel_distance = 9.0 / 360.0 * 40000 * FloatUtil.cosDeg(38);
        p("travel_distance: "+travel_distance);

        // Inferred earth circumference.
        double circumference = 360.0 / up_rot12_angle * travel_distance;
        p("circumference: "+circumference);

        // OBSERVATION: Inferred circumference agrees with prediction.
    }
}

// EOF
