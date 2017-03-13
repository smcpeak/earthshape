// FloatUtil.java
// See copyright.txt for license and terms of use.

package util;

/** Miscellaneous floating-point utilities. */
public class FloatUtil {
    /** Return a value 'x' in [0,limit) such that 'v-x'
      * is an integer multiple of 'limit'.  */
    public static float modulus(float v, float limit)
    {
        if (v < 0) {
            v += Math.ceil(-v / limit) * limit;
        }
        else if (v >= limit) {
            v -= Math.floor(v / limit) * limit;
        }
        return v;
    }

    /** Return a value 'x' in [lo, hi) such that 'v-x'
     * is an integer multiple of 'limit'.  */
   public static float modulus2(float v, float lo, float hi)
   {
       return lo + modulus(v - lo, hi - lo);
   }

    /** Clamp v to [lo,hi]. */
    public static float clamp(float v, float lo, float hi)
    {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
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

    /** Inverse cosine, in degrees in [0,180].  Also, this handles
      * inputs outside [-1,1] by effectively clamping them to that
      * range, whereas standard acos yields NaN. */
    public static double acosDeg(double x)
    {
        if (x > 1) {
            return 0;
        }
        else if (x < -1) {
            return 180;
        }
        else {
            return radiansToDegrees(Math.acos(x));
        }
    }

    public static float acosDegf(float x)
    {
        return (float)acosDeg(x);
    }

    /** Calculate the angle between two angles expressed as azimuth
      * and elevation, in degrees.  This also works for longitude
      * (as azimuth) and latitude (as elevation).  It is derived
      * from the "rule of cosines" for spherical trigonometry. */
    public static float sphericalSeparationAngle(
        float az1, float el1,
        float az2, float el2)
    {
        // Product of cosines.
        float cosines = FloatUtil.cosDegf(el1) *
                        FloatUtil.cosDegf(el2) *
                        FloatUtil.cosDegf(az2 - az1);

        // Product of sines.
        float sines = FloatUtil.sinDegf(el1) *
                      FloatUtil.sinDegf(el2);

        // Inverse cosine of the sum.
        return acosDegf(cosines + sines);
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
    public static float getLatLongPairHeading(
        float lat1, float long1,
        float lat2, float long2)
    {
        // First, get the spherical angle between the points.
        float sep = sphericalSeparationAngle(long1, lat1, long2, lat2);

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

        return (float)heading;
    }

    /** Given a longitude that might not be in [-180,180], add or
      * subtract a multiple of 360 so that it is in that range. */
    public static float normalizeLongitude(float longitude)
    {
        return modulus2(longitude, -180, 180);
    }

    /** Given a latitude that might be outside [-90,90], clamp
      * it to that range. */
    public static float clampLatitude(float latitude)
    {
        return clamp(latitude, -90, 90);
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
        float actual1 = getLatLongPairHeading(lat1, long1, lat2, long2);
        checkFloat(actual1, h1);

        float actual2 = getLatLongPairHeading(lat2, long2, lat1, long1);
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
