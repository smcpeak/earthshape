// CurvatureCalculator.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.util.ArrayList;

import util.FloatUtil;
import util.Matrix3f;
import util.Vector3f;

/** Calculate the average surface curvature between two points,
  * given the sky positions of two stars at each location. */
public class CurvatureCalculator {
    // ---------- Inputs ----------
    // For each of two places, and each of two stars, the
    // azimuth (degrees East of North) and elevation (degrees
    // above horizon) of their observed positions at some
    // point in time.
    public float start_A_az;
    public float start_A_el;
    public float start_B_az;
    public float start_B_el;
    public float end_A_az;
    public float end_A_el;
    public float end_B_az;
    public float end_B_el;

    /** Degrees East of North of the shortest travel direction
      * from start to end. */
    public float heading;

    /** Distance of shortest path from start to end, in km. */
    public float distanceKm;

    // ---------- Outputs ----------
    /** After aligning both stars, how far is the end B observation
      * from start B?  This will be 0 if both pairs of observations
      * have the same angular separation. */
    public double deviationBDegrees;

    /** Surface curvature in direction of travel, in 1/km. */
    public double curvature;

    /** Twist rate to the right in the direction of travel,
      * in degrees per km. */
    public double twistRate;

    /** A list of explanations of each step of the calculations. */
    public ArrayList<String> steps = new ArrayList<String>();

    /** List of warnings generated during calculation that may
      * affect reliability of the results.  Each warning is a
      * string without any newlines. */
    public ArrayList<String> warnings = new ArrayList<String>();

    // ---------- Methods ----------
    public void calculate()
    {
        if (this.start_A_el < 20 || this.start_B_el < 20 || this.end_A_el < 20 || this.end_B_el < 20) {
            this.warnings.add(
                "Warning: At least on elevation is below 20 degrees, which "+
                "makes the measurement unreliable due to atmospheric refraction.");
        }

        if (this.distanceKm < 0) {
            this.warnings.add("Distance should be positive.  Substituting 1 km.");
            this.distanceKm = 1;
        }

        // Compute star direction unit vectors.
        Vector3f start_A = Vector3f.azimuthElevationToVector(this.start_A_az, this.start_A_el);
        Vector3f start_B = Vector3f.azimuthElevationToVector(this.start_B_az, this.start_B_el);
        Vector3f end_A = Vector3f.azimuthElevationToVector(this.end_A_az, this.end_A_el);
        Vector3f end_B = Vector3f.azimuthElevationToVector(this.end_B_az, this.end_B_el);

        this.steps.add("start_A: "+start_A);
        this.steps.add("start_B: "+start_B);
        this.steps.add("end_A: "+end_A);
        this.steps.add("end_B: "+end_B);

        // Calculate rotation that aligns A.
        Vector3f u = end_A.cross(start_A);
        this.steps.add("end_A cross start_A: "+u);
        double theta1 = FloatUtil.asinDeg(u.length());
        u = u.normalize();
        Matrix3f rot1 = Matrix3f.rotateDeg(theta1, u);

        this.steps.add("u: "+u);
        this.steps.add("theta1: "+theta1);
        this.steps.add("rot1: "+rot1);

        // Apply that rotation to end B and starting surface normal.
        Vector3f end_B_rot1 = rot1.times(end_B);
        Vector3f normal = new Vector3f(0,1,0);
        Vector3f normal_rot1 = rot1.times(normal);

        this.steps.add("end_B_rot1: "+end_B_rot1);
        this.steps.add("normal_rot1: "+normal_rot1);

        // Project the B observations into the plane perpendicular
        // to the now-aligned A observations, then normalize those
        // to unit vectors.
        Vector3f start_B_proj = start_B.orthogonalComponentToUnitVector(start_A).normalize();
        Vector3f end_B_rot1_proj = end_B_rot1.orthogonalComponentToUnitVector(start_A).normalize();

        this.steps.add("start_B_proj: "+start_B_proj);
        this.steps.add("end_B_rot1_proj: "+end_B_rot1_proj);

        // Calculate rotation about start A axis that aligns the
        // projected B observations.
        Vector3f v = end_B_rot1_proj.cross(start_B_proj);
        this.steps.add("end_B_rot1_proj cross start_B_proj: "+v);
        double theta2 = FloatUtil.asinDeg(v.length());
        v = v.normalize();
        Matrix3f rot2 = Matrix3f.rotateDeg(theta2, v);

        this.steps.add("v: "+v);
        this.steps.add("theta2: "+theta2);
        this.steps.add("rot2: "+rot2);

        // Apply that rotation to rotated end B and start up
        // (surface normal).
        Vector3f end_B_rot12 = rot2.times(end_B_rot1);
        Vector3f normal_rot12 = rot2.times(normal_rot1);

        this.steps.add("end_B_rot12: "+end_B_rot12);
        this.steps.add("normal_rot12: "+normal_rot12);

        // Check alignment of B.
        this.deviationBDegrees = FloatUtil.acosDeg(end_B_rot12.dot(start_B));
        if (this.deviationBDegrees > 1) {
            this.warnings.add("Warning: deviation exceeds one degree, star separation angles are not the same.");
        }

        // Unit travel vector in start coordinate system.
        Vector3f start_north = new Vector3f(0,0,-1);
        Vector3f travel_forward = start_north.rotateDeg(-this.heading, normal);

        this.steps.add("travel_forward: "+travel_forward);

        this.computeFromNormals(normal, normal_rot12, travel_forward);
    }

    /** Jump in to the middle of the curvature calculation with known
      * original and rotated normals, plus the forward travel unit
      * vector.  'distanceKm' must be set first to use this. */
    public void computeFromNormals(Vector3f normal, Vector3f normal_rot12, Vector3f travel_forward)
    {
        // Perpendicular to travel direction.  If the normal rotation
        // cross product is aligned with this, curvature is positive.
        Vector3f travel_left = normal.cross(travel_forward);

        this.steps.add("travel_left: "+travel_left);

        // Express the normal rotation as an angle/axis vector.
        Vector3f normal_rot_axis = normal.cross(normal_rot12).normalize();
        double normal_rot_angle = FloatUtil.acosDeg(normal.dot(normal_rot12));
        Vector3f normal_rot_AA = normal_rot_axis.timesd(normal_rot_angle);

        this.steps.add("normal_rot_axis: "+normal_rot_axis);
        this.steps.add("normal_rot_angle: "+normal_rot_angle);
        this.steps.add("normal_rot_AA: "+normal_rot_AA);

        // Component of normal rotation that is not twisted.
        Vector3f normal_rot_AA_straight =
            normal_rot_AA.projectOntoUnitVector(travel_left);

        this.steps.add("normal_rot_AA_straight: "+normal_rot_AA_straight);

        // Calculate the angle through which the normal was rotated
        // forward without twisting.
        //
        // I don't think this is really right.  I'm just projecting
        // the AA and assuming the resulting length is the angle of
        // a rotation component.  What I want is a proper rotation
        // decomposition.  But this should suffice so long as the
        // twist rate is small.
        double curvatureAngle = normal_rot_AA_straight.length();
        if (normal_rot_AA_straight.dot(travel_left) < 0) {
            curvatureAngle = -curvatureAngle;
        }

        this.steps.add("curvatureAngle: "+curvatureAngle);

        // Calculate resulting curvature in km^-1.
        this.curvature = (2 * Math.PI * curvatureAngle) / (this.distanceKm * 360.0);

        // Normal rotation that is twist.
        Vector3f normal_rot_AA_twist =
            normal_rot_AA.projectOntoUnitVector(travel_forward);

        this.steps.add("normal_rot_AA_twist: "+normal_rot_AA_twist);

        // Calculate twist angle to the right, so it follows the
        // right hand rule w.r.t. the forward travel direction.
        double twistAngle = normal_rot_AA_twist.length();
        if (normal_rot_AA_twist.dot(travel_forward) < 0) {
            twistAngle = -twistAngle;
        }

        this.steps.add("twistAngle: "+twistAngle);

        // Twist per distance.
        this.twistRate = twistAngle / this.distanceKm;

        if (Math.abs(this.twistRate) > 0.001) {
            this.warnings.add("Warning: Twist rate magnitude exceeds one degree per 1000 km.  That never happens on the real Earth.  Check the travel heading.");
        }
    }

    /** Set the heading and distanceKm using latitude and longitude
      * coordinates. */
    public void setTravelByLatLong(
        float startLatitude,
        float startLongitude,
        float endLatitude,
        float endLongitude)
    {
        this.heading = (float)FloatUtil.getLatLongPairHeading(
            startLatitude, startLongitude, endLatitude, endLongitude);

        double arcAngleDegrees = FloatUtil.sphericalSeparationAngle(
            startLongitude, startLatitude,
            endLongitude, endLatitude);
        this.distanceKm =
            (float)(arcAngleDegrees / 180.0 * Math.PI * RealWorldObservations.EARTH_RADIUS_KM);
    }

    /** Get initial values for observations of Dubhe and Sirius
      * from 38N,122W and 38N,113W at 2017-03-05 20:00 -08:00.  The
      * data is accurate to about 0.2 degrees. */
    public static CurvatureCalculator getDubheSirius()
    {
        CurvatureCalculator c = new CurvatureCalculator();
        c.setTravelByLatLong(38, -122, 38, -113);

        c.start_A_az = 36.9f;
        c.start_A_el = 44.8f;
        c.start_B_az = 181.1f;
        c.start_B_el = 35.2f;
        c.end_A_az = 36.4f;
        c.end_A_el = 49.1f;
        c.end_B_az = 191.5f;
        c.end_B_el = 34.4f;

        return c;
    }

    /** Test a single call to calculate. */
    private static void testOne(
        float start_A_az,
        float start_A_el,
        float start_B_az,
        float start_B_el,
        float end_A_az,
        float end_A_el,
        float end_B_az,
        float end_B_el,
        float heading,
        float distanceKm,
        double deviationBDegrees,
        double curvature,
        double twistRate,
        int numWarnings)
    {
        CurvatureCalculator c = new CurvatureCalculator();
        c.start_A_az = start_A_az;
        c.start_A_el = start_A_el;
        c.start_B_az = start_B_az;
        c.start_B_el = start_B_el;
        c.end_A_az = end_A_az;
        c.end_A_el = end_A_el;
        c.end_B_az = end_B_az;
        c.end_B_el = end_B_el;
        c.heading = heading;
        c.distanceKm = distanceKm;

        testOneC(c, deviationBDegrees, curvature, twistRate, numWarnings);
    }

    /** Test using the inputs in 'c'. */
    private static void testOneC(CurvatureCalculator c,
        double deviationBDegrees,
        double curvature,
        double twistRate,
        int numWarnings)
    {
        c.calculate();

        if (Math.abs(deviationBDegrees - c.deviationBDegrees) < 0.2 &&
            Math.abs(curvature - c.curvature) < 1e-7 &&
            Math.abs(twistRate - c.twistRate) < 1e-7 &&
            numWarnings == c.warnings.size())
        {
            // Ok.
        }
        else {
            System.err.println("start_A_az: "+c.start_A_az);
            System.err.println("start_A_el: "+c.start_A_el);
            System.err.println("start_B_az: "+c.start_B_az);
            System.err.println("start_B_el: "+c.start_B_el);
            System.err.println("end_A_az: "+c.end_A_az);
            System.err.println("end_A_el: "+c.end_A_el);
            System.err.println("end_B_az: "+c.end_B_az);
            System.err.println("end_B_el: "+c.end_B_el);
            System.err.println("heading: "+c.heading);
            System.err.println("distanceKm: "+c.distanceKm);
            System.err.println("deviationBDegrees expect: "+deviationBDegrees);
            System.err.println("deviationBDegrees actual: "+c.deviationBDegrees);
            System.err.println("curvature expect: "+curvature);
            System.err.println("curvature actual: "+c.curvature);
            System.err.println("twistRate expect: "+twistRate);
            System.err.println("twistRate actual: "+c.twistRate);
            System.err.println("numWarnings expect: "+numWarnings);
            System.err.println("numWarnings actual: "+c.warnings.size());
            throw new RuntimeException("testOne failed");
        }
    }

    /** Unit tests. */
    public static void main(String[] args)
    {
        testOne(
            0, 90,   // A: zenith
            0, 0,    // B: fwd
            180, 0,  // A: backwd
            0, 90,   // B: zenith
            0,       // hdg
            10000,   // dist
            0,       // devB
            2 * Math.PI / 40000,    // curvature
            0,       // twist
            1);      // warnings

        testOne(
            0, 90,   // A: zenith
            0, 0,    // B: fwd
            180, 0,  // A: backwd
            0, 90,   // B: zenith
            90,      // hdg
            10000,   // dist
            0,       // devB
            0,       // curvature
            -.009,       // twist
            2);      // warnings

        testOneC(
            CurvatureCalculator.getDubheSirius(),
            0.06391,
            1/6382.0,
            -1.0329799e-4,
            0);

        System.out.println("CurvatureCalculator tests passed.");
    }
}

// EOF