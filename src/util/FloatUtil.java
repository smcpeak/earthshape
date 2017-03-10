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

    /** Cosine of degrees, as 'float'. */
    public static float cosDegf(float degrees)
    {
        return (float)Math.cos(degreesToRadiansf(degrees));
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

        // Sum; when the inputs are the same, this can be slightly
        // greater than 1 or less than -1 due to rounding.
        float sum = cosines + sines;
        if (-1 <= sum && sum <= 1) {
            // Inverse cosine of the sum.
            return FloatUtil.radiansToDegreesf(
                (float)Math.acos(sum));
        }
        else {
            return 0;
        }

    }

}
