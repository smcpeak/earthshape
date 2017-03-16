// FloatUtil.java
// See copyright.txt for license and terms of use.

package util;

/** Miscellaneous floating-point utilities. */
public class FloatUtil {
    /** Return a value 'x' in [0,limit) such that 'v-x'
      * is an integer multiple of 'limit'.  */
    public static double modulus(double v, double limit)
    {
        if (v < 0) {
            v += Math.ceil(-v / limit) * limit;
        }
        else if (v >= limit) {
            v -= Math.floor(v / limit) * limit;
        }
        return v;
    }

    /** Like 'modulus' but for float. */
    public static float modulusf(float v, float limit)
    {
        return (float)modulus(v, limit);
    }

    /** Return a value 'x' in [lo, hi) such that 'v-x'
      * is an integer multiple of 'limit'.  */
    public static double modulus2(double v, double lo, double hi)
    {
        return lo + modulus(v - lo, hi - lo);
    }

    /** Like 'modulus2' but for float. */
    public static float modulus2f(float v, float lo, float hi)
    {
        return (float)modulus2(v, lo, hi);
    }

    /** Clamp v to [lo,hi]. */
    public static double clamp(double v, double lo, double hi)
    {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    /** Like 'clamp' but for float. */
    public static float clampf(float v, float lo, float hi)
    {
        return (float)clamp(v, lo, hi);
    }

    /** Convert degrees to radians using 'float'. */
    public static float degreesToRadiansf(float degrees)
    {
        return (float)(degrees / 180.0 * Math.PI);
    }

    /** Convert degrees to radians using 'double'. */
    public static double degreesToRadians(double degrees)
    {
        return degrees / 180.0 * Math.PI;
    }

    /** Convert radians to degrees using 'float'. */
    public static float radiansToDegreesf(float radians)
    {
        return (float)(radians / Math.PI * 180.0);
    }

    /** Convert radians to degrees using 'double'. */
    public static double radiansToDegrees(double radians)
    {
        return radians / Math.PI * 180.0;
    }

    /** Sine of degrees, as 'float'. */
    public static float sinDegf(float degrees)
    {
        return (float)Math.sin(degreesToRadiansf(degrees));
    }

    /** Sine of degrees, as 'double'. */
    public static double sinDeg(double degrees)
    {
        return Math.sin(degreesToRadians(degrees));
    }

    /** Cosine of degrees, as 'float'. */
    public static float cosDegf(float degrees)
    {
        return (float)Math.cos(degreesToRadiansf(degrees));
    }

    /** Cosine of degrees, as 'double'. */
    public static double cosDeg(double degrees)
    {
        return Math.cos(degreesToRadians(degrees));
    }

    /** Inverse sine, in degrees in [-90,90].  Also, this handles
      * inputs outside [-1,1] by effectively clamping them to that
      * range, whereas standard asin yields NaN. */
    public static double asinDeg(double x)
    {
        return radiansToDegrees(asinRad(x));
    }

    public static float asinDegf(float x)
    {
        return (float)asinDeg(x);
    }

    /** Inverse sine, in radians in [-pi/2,pi/2].  Also, this handles
      * inputs outside [-1,1] by effectively clamping them to that
      * range, whereas standard acos yields NaN. */
    public static double asinRad(double x)
    {
        if (x > 1) {
            return Math.PI / 2.0;
        }
        else if (x < -1) {
            return - Math.PI / 2.0;
        }
        else {
            return Math.asin(x);
        }
    }

    /** Inverse cosine, in degrees in [0,180].  Also, this handles
      * inputs outside [-1,1] by effectively clamping them to that
      * range, whereas standard acos yields NaN. */
    public static double acosDeg(double x)
    {
        return radiansToDegrees(acosRad(x));
    }

    public static float acosDegf(float x)
    {
        return (float)acosDeg(x);
    }

    /** Inverse cosine, in radians in [0,pi].  Also, this handles
      * inputs outside [-1,1] by effectively clamping them to that
      * range, whereas standard acos yields NaN. */
    public static double acosRad(double x)
    {
        if (x > 1) {
            return 0;
        }
        else if (x < -1) {
            return Math.PI;
        }
        else {
            return Math.acos(x);
        }
    }

    /** Compute the two-argument inverse tangent of (y,x),
      * in degrees in [-180,180]. */
    public static double atan2Deg(double y, double x)
    {
        return radiansToDegrees(Math.atan2(y, x));
    }

    /** Calculate the angle between two angles expressed as azimuth
      * and elevation, in degrees.  This also works for longitude
      * (as azimuth) and latitude (as elevation).  It is derived
      * from the "rule of cosines" for spherical trigonometry. */
    public static double sphericalSeparationAngle(
        double az1, double el1,
        double az2, double el2)
    {
        // Product of cosines.
        double cosines = FloatUtil.cosDeg(el1) *
                         FloatUtil.cosDeg(el2) *
                         FloatUtil.cosDeg(az2 - az1);

        // Product of sines.
        double sines = FloatUtil.sinDeg(el1) *
                       FloatUtil.sinDeg(el2);

        // Inverse cosine of the sum.
        return acosDeg(cosines + sines);
    }

    /** Given a pair of (latitude,longitude) points, in degrees, where
      * latitude is degrees North of the equator in [-90,90] and
      * longitude is degrees East of the prime meridian, return the
      * heading as degrees East of North from the first to the second
      * along a great circle (i.e., path of shortest distance).
      *
      * Summary diagram:
      * <pre>
      *                        North pole
      *                            / \
      *                           /h  \
      *                        P1*--   \
      *                         /   --  \
      *                        /       --*P2
      *                       /           \
      *                      /             \
      *          equator  ----------------------
      * </pre>
      *
      * This method returns the angle 'h' in [-180,180]. */
    public static double getLatLongPairHeading(
        double lat1, double long1,
        double lat2, double long2)
    {
        // First, get the spherical angle between the points.
        double sep = sphericalSeparationAngle(long1, lat1, long2, lat2);

        // The spherical angle opposite h is 90-lat2, and the
        // spherical angles adjacent to h are 90-lat1 and 'sep'.
        // Consequently, the rule of cosines says:
        //
        //   cos(90-lat2) = cos(90-lat1)*cos(sep) + sin(90-lat1)*sin(sep)*cos(h)
        //
        // Substituting cos(90-x)=sin(x) and vice-versa:
        //
        //   sin(lat2) = sin(lat1)*cos(sep) + cos(lat1)*sin(sep)*cos(h)
        //
        // Solving for cos(h):
        //
        //           sin(lat2) - sin(lat1) * cos(sep)
        //   cos h = --------------------------------
        //                 cos(lat1) * sin(sep)

        double denom = cosDeg(lat1) * sinDeg(sep);
        if (denom == 0) {
            // This can happen for three reasons:
            //
            // * The points are the same.
            // * The points are antipodes.
            // * The source is one of the poles.
            //
            // For my present purpose, in the first case, the heading
            // does not matter; in the second, all headings work; and
            // in the third, the correct direction cannot be expressed
            // as a heading, but North or South will do.  So, assume
            // I'm in the third case and answer accordingly.
            if (lat1 >= 0) {
                return 180;     // Go South.
            }
            else {
                return 0;       // Go North.
            }
        }

        double numer = sinDeg(lat2) - sinDeg(lat1) * cosDeg(sep);
        double ratio = numer / denom;
        double heading = acosDeg(ratio);

        // The result so far assumed we had to go East, yielding
        // a heading in [0,180].  Check if the longitudes actually
        // require going West.
        if (normalizeLongitude(long2 - long1) < 0) {
            heading = -heading;
        }

        return heading;
    }

    /** Given a longitude that might not be in [-180,180], add or
      * subtract a multiple of 360 so that it is in that range. */
    public static double normalizeLongitude(double longitude)
    {
        return modulus2(longitude, -180, 180);
    }

    /** Given a longitude that might not be in [-180,180], add or
      * subtract a multiple of 360 so that it is in that range. */
    public static float normalizeLongitudef(float longitude)
    {
        return modulus2f(longitude, -180, 180);
    }

    /** Given a latitude that might be outside [-90,90], clamp
      * it to that range. */
    public static double clampLatitude(double latitude)
    {
        return clamp(latitude, -90, 90);
    }

    /** Given a latitude that might be outside [-90,90], clamp
      * it to that range. */
    public static float clampLatitudef(float latitude)
    {
        return clampf(latitude, -90, 90);
    }

    /** Compare an actual result to an expected result, throwing
      * an exception if they differ. */
    public static void checkFloat(float actual, float expect)
    {
        if (Math.abs(actual - expect) < 0.001) {
            // ok
        }
        else {
            System.err.println("actual: "+actual);
            System.err.println("expect: "+expect);
            throw new RuntimeException("checkFloat failed");
        }
    }

    /** Test that going from (lat1,long1) to (lat2,long2) requires
      * heading 'h1', and that the reverse requires 'h2'. */
    private static void testOneGetLatLongPairHeading(
        float lat1, float long1,
        float lat2, float long2,
        float h1, float h2)
    {
        float actual1 = (float)getLatLongPairHeading(lat1, long1, lat2, long2);
        checkFloat(actual1, h1);

        float actual2 = (float)getLatLongPairHeading(lat2, long2, lat1, long1);
        checkFloat(actual2, h2);
    }

    private static void testGetLatLongPairHeadings()
    {
        // Same points: 180.
        testOneGetLatLongPairHeading(0, 0,  0, 0,  180, 180);

        // Two points on the equator: +/-90.
        testOneGetLatLongPairHeading(0, 0,  0, 1.0f,  90.0f, -90.0f);
        testOneGetLatLongPairHeading(0, 0,  0, 90.0f,  90.0f, -90.0f);
        testOneGetLatLongPairHeading(0, 0,  0, 179.0f,  90.0f, -90.0f);

        // Same longitude.
        testOneGetLatLongPairHeading(0, 0,  45f, 0,  0, 180);
        testOneGetLatLongPairHeading(-45f, 0,  45f, 0,  0, 180);

        // Two points at 45N.
        testOneGetLatLongPairHeading(45f, 0,  45f, 10,  86.45996f, -86.45996f);
        testOneGetLatLongPairHeading(45f, 0,  45f, 20,  82.892914f, -82.892914f);

        // SFO to FRA (approx).
        testOneGetLatLongPairHeading(37f, -122f,  50f, 8f,  29.781033f, -38.105503f);

        // SFO to SYD (approx).
        testOneGetLatLongPairHeading(37f, -122f,  -33.5f, 151f,  -119.28679f, 56.648434f);
    }

    /** Return the smallest value in 'arr', or 0 if it is empty. */
    public static float minimumOfArray(float[] arr)
    {
        float ret = 0;
        if (arr.length > 0) {
            ret = arr[0];
            for (int i=1; i < arr.length; i++) {
                if (arr[i] < ret) {
                    ret = arr[i];
                }
            }
        }
        return ret;
    }

    /** Return the largest value in 'arr', or 0 if it is empty. */
    public static float maximumOfArray(float[] arr)
    {
        float ret = 0;
        if (arr.length > 0) {
            ret = arr[0];
            for (int i=1; i < arr.length; i++) {
                if (arr[i] > ret) {
                    ret = arr[i];
                }
            }
        }
        return ret;
    }

    public static void main(String args[])
    {
        testGetLatLongPairHeadings();
        System.out.println("FloatUtil tests passed");
    }
}

// EOF
