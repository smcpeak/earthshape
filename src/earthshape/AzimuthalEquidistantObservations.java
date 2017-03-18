// AzimuthalEquidistantObservations.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.util.List;
import java.util.Map;

import util.FloatUtil;
import util.Vector3f;
import util.Vector4f;

/** Observations for a hypothetical world that a physical realization
  * of an azimuthal equidistant projection of the Earth.  Also, some of
  * the stars are nearby. */
public class AzimuthalEquidistantObservations extends ManifoldObservations {
    // ---- Instance data ----
    /** Star info borrowed from CloseStarObservations. */
    private StarGenerator starGenerator;

    // ---- Methods ----
    public AzimuthalEquidistantObservations()
    {
        float latitude = CloseStarObservations.REFERENCE_LATITUDE;
        float longitude = CloseStarObservations.REFERENCE_LONGITUDE;

        // I need the *observations* from CloseStarObservations,
        // but the *squares* from my own class.
        CloseStarObservations cso = new CloseStarObservations();
        List<StarObservation> referenceObservations =
            cso.getStarObservations(StarObservation.unixTimeOfManualData,
                latitude, longitude);
        this.starGenerator = CloseStarObservations.buildStarGenerator(
            referenceObservations, this.getModelSquare(latitude, longitude));
    }

    @Override
    public String getDescription()
    {
        return "azimuthal equidistant projection flat Earth";
    }

    @Override
    public Vector3f getModelPt(float latitude, float longitude)
    {
        float distDegrees = 90.0f - latitude;
        float r = FloatUtil.degreesToRadiansf(distDegrees)
            * RealWorldObservations.EARTH_RADIUS_KM / 1000.0f;
        float x = r * FloatUtil.sinDegf(longitude);
        float z = r * FloatUtil.cosDegf(longitude);

        return new Vector3f(x, 0, z);
    }

    @Override
    public Map<String, Vector4f> getModelStarMap()
    {
        return this.starGenerator.starLocations;
    }
}

// EOf
