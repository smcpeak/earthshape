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
      * from start to end, as recorded at the start location. */
    public float startTravelHeading;

    /** Degrees East of North of the shortest travel direction
      * from start to end, as recorded at the end location. */
    public float endTravelHeading;

    /** Distance of shortest path from start to end, in km. */
    public float distanceKm;

    // ---------- Outputs ----------
    /** After aligning both stars, how far is the end B observation
      * from start B?  This will be 0 if both pairs of observations
      * have the same angular separation. */
    public double deviationBDegrees;

    /** Surface curvature in direction of travel, in radians
      * per km. */
    public double normalCurvature;

    /** Change in direction of travel within the tangent plane,
      * in degrees per km. */
    public double geodesicCurvature;

    /** Twist rate to the right in the direction of travel,
      * in degrees per km. */
    public double geodesicTorsion;

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
        Vector3f cp1 = end_A.cross(start_A);
        this.steps.add("cp1: "+cp1);
        double angle1 = FloatUtil.asinDeg(cp1.length());
        Vector3f axis1 = cp1.normalize();
        Matrix3f rot1 = Matrix3f.rotateDeg(angle1, axis1);

        this.steps.add("axis1: "+axis1);
        this.steps.add("angle1 (deg): "+angle1);
        this.steps.add("rot1: "+rot1);

        // Apply that rotation to end B and starting surface normal.
        Vector3f end_B_rot1 = rot1.times(end_B);
        Vector3f up = new Vector3f(0,1,0);
        Vector3f up_rot1 = rot1.times(up);

        this.steps.add("end_B_rot1: "+end_B_rot1);
        this.steps.add("up_rot1: "+up_rot1);

        // Project the B observations into the plane perpendicular
        // to the now-aligned A observations, then normalize those
        // to unit vectors.
        Vector3f start_B_proj = start_B.orthogonalComponentToUnitVector(start_A).normalize();
        Vector3f end_B_rot1_proj = end_B_rot1.orthogonalComponentToUnitVector(start_A).normalize();

        this.steps.add("start_B_proj: "+start_B_proj);
        this.steps.add("end_B_rot1_proj: "+end_B_rot1_proj);

        // Calculate rotation about start A axis that aligns the
        // projected B observations.
        Vector3f cp2 = end_B_rot1_proj.cross(start_B_proj);
        this.steps.add("cp2: "+cp2);
        double angle2 = FloatUtil.asinDeg(cp2.length());
        cp2 = cp2.normalize();
        Matrix3f rot2 = Matrix3f.rotateDeg(angle2, cp2);

        this.steps.add("cp2: "+cp2);
        this.steps.add("angle2 (deg): "+angle2);
        this.steps.add("rot2: "+rot2);

        // Apply that rotation to rotated end B and start up
        // (surface normal).
        Vector3f end_B_rot12 = rot2.times(end_B_rot1);
        Vector3f up_rot12 = rot2.times(up_rot1);

        this.steps.add("end_B_rot12: "+end_B_rot12);
        this.steps.add("up_rot12: "+up_rot12);

        // Check alignment of B.
        this.deviationBDegrees = FloatUtil.acosDeg(end_B_rot12.dot(start_B));
        if (this.deviationBDegrees > 1) {
            this.warnings.add("Warning: deviation exceeds one degree, star separation angles are not the same.");
        }

        // Unit travel vector in start coordinate system.
        Vector3f start_forward = Vector3f.headingToVector(this.startTravelHeading);
        this.steps.add("start_forward: "+start_forward);

        // Travel vector at end square.
        Vector3f end_forward =
            rot2.times(rot1.times(Vector3f.headingToVector(this.endTravelHeading)));
        this.steps.add("end_forward: "+end_forward);

        this.computeFromNormals(up, up_rot12, start_forward, end_forward);
    }

    /** Jump in to the middle of the curvature calculation with known
      * original and rotated normals, plus the start and end travel unit
      * vectors.  'distanceKm' must be set first to use this. */
    public void computeFromNormals(
        Vector3f up, Vector3f up_rot12, Vector3f start_forward, Vector3f end_forward)
    {
        // Background:
        // https://en.wikipedia.org/wiki/Curvature#Curvature_of_curves_on_surfaces

        // Perpendicular to travel direction.  If the normal rotation
        // cross product is aligned with this, curvature is positive.
        Vector3f travel_left = up.cross(start_forward);

        this.steps.add("travel_left: "+travel_left);

        // Cross the old normal with the new normal to get a
        // vector along the axis with magnitude sin(angle).
        Vector3f normal_cp = up.cross(up_rot12);

        // Get the curvature of normal rotation in the plane
        // containing both 'normal' and 'forward'.
        double normal_rot_angle_radians =
            Math.asin(normal_cp.dot(travel_left));
        this.normalCurvature = normal_rot_angle_radians / this.distanceKm;

        this.steps.add("normal_rot_angle_radians: "+normal_rot_angle_radians);
        this.steps.add("normal curvature: "+this.normalCurvature);

        // Geodesic curvature: how the travel direction changes
        // in the tangent plane.
        this.geodesicCurvature =
            FloatUtil.asinDeg(start_forward.cross(end_forward).dot(up)) / this.distanceKm;
        this.steps.add("geodesic curvature: "+this.geodesicCurvature);

        if (Math.abs(this.geodesicCurvature) > 0.001) {
            this.warnings.add("Warning: Geodesic curvature magnitude exceeds one degree per 1000 km.  That never happens on the real Earth.  Check the travel headings.");
        }

        // Geodesic torsion curvature.
        this.geodesicTorsion =
            FloatUtil.asinDeg(normal_cp.dot(start_forward)) / this.distanceKm;
        this.steps.add("geodesic torsion: "+this.geodesicTorsion);

        if (Math.abs(this.geodesicTorsion) > 0.001) {
            this.warnings.add("Warning: Geodesic torsion magnitude exceeds one degree per 1000 km.  That never happens on the real Earth.  Check the start travel heading.");
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
        this.startTravelHeading = (float)FloatUtil.getLatLongPairHeading(
            startLatitude, startLongitude, endLatitude, endLongitude);
        float endToStartHeading = (float)FloatUtil.getLatLongPairHeading(
            endLatitude, endLongitude, startLatitude, startLongitude);

        // This is the start->end heading at the end location.
        this.endTravelHeading = FloatUtil.modulus2f(endToStartHeading + 180, 0, 360);

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
        float startTravelHeading,
        float endTravelHeading,
        float distanceKm,
        double deviationBDegrees,
        double normalCurvature,
        double geodesicCurvature,
        double geodesicTorsion,
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
        c.startTravelHeading = startTravelHeading;
        c.endTravelHeading = endTravelHeading;
        c.distanceKm = distanceKm;

        testOneC(c, deviationBDegrees, normalCurvature, geodesicCurvature,
                 geodesicTorsion, numWarnings);
    }

    /** Test using the inputs in 'c'. */
    private static void testOneC(CurvatureCalculator c,
        double deviationBDegrees,
        double normalCurvature,
        double geodesicCurvature,
        double geodesicTorsion,
        int numWarnings)
    {
        c.calculate();

        System.out.println("start_A_az: "+c.start_A_az);
        System.out.println("start_A_el: "+c.start_A_el);
        System.out.println("start_B_az: "+c.start_B_az);
        System.out.println("start_B_el: "+c.start_B_el);
        System.out.println("end_A_az: "+c.end_A_az);
        System.out.println("end_A_el: "+c.end_A_el);
        System.out.println("end_B_az: "+c.end_B_az);
        System.out.println("end_B_el: "+c.end_B_el);
        System.out.println("startTravelHeading: "+c.startTravelHeading);
        System.out.println("endTravelHeading: "+c.endTravelHeading);
        System.out.println("distanceKm: "+c.distanceKm);
        System.out.println("deviationBDegrees expect: "+deviationBDegrees);
        System.out.println("deviationBDegrees actual: "+c.deviationBDegrees);
        System.out.println("normal curvature expect: "+normalCurvature);
        System.out.println("normal curvature actual: "+c.normalCurvature);
        System.out.println("geodesic curvature expect: "+geodesicCurvature);
        System.out.println("geodesic curvature actual: "+c.geodesicCurvature);
        System.out.println("geodesic torsion expect: "+geodesicTorsion);
        System.out.println("geodesic torsion actual: "+c.geodesicTorsion);
        System.out.println("numWarnings expect: "+numWarnings);
        System.out.println("numWarnings actual: "+c.warnings.size());

        if (Math.abs(deviationBDegrees - c.deviationBDegrees) > 0.2) {
            throw new RuntimeException("deviation is wrong");
        }
        if (Math.abs(normalCurvature - c.normalCurvature) > 1e-7) {
            throw new RuntimeException("normal curvature is wrong");
        }
        if (Math.abs(geodesicCurvature - c.geodesicCurvature) > 1e-7) {
            throw new RuntimeException("geodesic curvature is wrong");
        }
        if (Math.abs(geodesicTorsion - c.geodesicTorsion) > 1e-7) {
            throw new RuntimeException("geodesic torsion is wrong");
        }
        if (numWarnings != c.warnings.size()) {
            throw new RuntimeException("number of warnings is wrong");
        }
    }

    /** Unit tests. */
    public static void main(String[] args)
    {
        // Travel North across 90 degrees.
        testOne(
            0, 90,   // A: zenith
            0, 0,    // B: fwd
            180, 0,  // A: backwd
            0, 90,   // B: zenith
            0,       // start heading
            0,       // end heading
            10000,   // dist
            0,       // devB
            2 * Math.PI / 40000,    // normal curvature
            0,       // geodesic curvature
            0,       // geodesic torsion
            1);      // warnings

        // Travel East along torsion helix for 90 degrees.
        testOne(
            0, 90,   // A: zenith
            0, 0,    // B: fwd
            180, 0,  // A: backwd
            0, 90,   // B: zenith
            90,      // start heading
            90,      // end heading
            10000,   // dist
            0,       // devB
            0,       // normal curvature
            0,       // geodesic curvature
            -.009,   // geodesic torsion
            2);      // warnings

        // Travel East along canyon bottom turning left.
        testOne(
            0, 90,   // A: zenith
            0, 0,    // B: fwd
            0, 90,   // A: zenith
            90, 0,   // B: right
            90,      // start heading
            90,      // end heading
            10000,   // dist
            0,       // devB
            0,       // normal curvature
            0.009,   // geodesic curvature
            0,       // geodesic torsion
            2);      // warnings

        testOneC(
            CurvatureCalculator.getDubheSirius(),
            0.06391,          // deviation
            1/6382.0,         // normal curvature
            -2.108664e-5,     // geodesic curvature
            -1.0303577e-4,    // geodesic torsion
            0);               // warnings

        System.out.println("CurvatureCalculator tests passed.");
    }
}

// EOF
