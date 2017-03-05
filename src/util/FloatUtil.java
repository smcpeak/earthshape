// FloatUtil.java

package util;

/** Miscellaneous floating-point utilities. */
public class FloatUtil {
    /** Return a value 'x' in [0,limit) such that 'v-x'
      * is an integer multiple of 'limit'.  */
    public static float modulus(float v, float limit)
    {
        if (v < 0) {
            v += Math.floor(-v / limit) * limit;
        }
        else if (v >= limit) {
            v -= Math.ceil(v / limit) * limit;
        }
        return v;
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
}
