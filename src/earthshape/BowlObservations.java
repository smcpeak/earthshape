// BowlObservations.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.util.HashMap;
import java.util.Map;

import util.FloatUtil;
import util.Vector3f;
import util.Vector4f;

/** World model where the surface is shaped like a bowl and stars are
  * placed arbitrarily. */
public class BowlObservations extends ManifoldObservations {
    @Override
    public String getDescription()
    {
        return "bowl";
    }

    @Override
    public Vector3f mapLL(float latitude, float longitude)
    {
        // This is based on the Azimuthal Equidistant projection,
        // but I added a quadratic curve in the +Y direction.
        float distDegrees = 90.0f - latitude;
        float r = FloatUtil.degreesToRadiansf(distDegrees)
            * RealWorldObservations.EARTH_RADIUS_KM / 1000.0f;
        float x = r * FloatUtil.sinDegf(longitude);
        float z = r * FloatUtil.cosDegf(longitude);
        float y = 5 * (1 - FloatUtil.cosDegf(distDegrees / 2));

        return new Vector3f(x, y, z);
    }

    @Override
    public Map<String, Vector4f> getStarMap()
    {
        HashMap<String, Vector4f> ret = new HashMap<String, Vector4f>();

        // Some close stars.
        ret.put("A", new Vector4f(1, 6, 2, 1));
        ret.put("B", new Vector4f(-3, 7, 4, 1));
        ret.put("C", new Vector4f(5, 18, -6, 1));
        ret.put("D", new Vector4f(-17, 19, -8, 1));

        // Some far away, but finitely so.
        ret.put("E", new Vector4f(200, 380, 300, 1));
        ret.put("F", new Vector4f(-300, 390, 150, 1));

        // Some infinitely far away.
        ret.put("G", new Vector4f(15, 28, -6, 0));
        ret.put("H", new Vector4f(-7, 29, -18, 0));

        return ret;
    }
}

// EOf
