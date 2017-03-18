// CloseStarObservations.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import util.Vector3f;
import util.Vector4f;

/** Observations for a hypothetical world that has the same size and
  * shape as the real Earth, but the stars are nearby. */
public class CloseStarObservations extends ManifoldObservations {
    // ---- Constants ----
    // The star observation data for this one location will be
    // the same as in the real world.
    public static final float REFERENCE_LATITUDE = 38;
    public static final float REFERENCE_LONGITUDE = -122;

    // ---- Instance data ----
    /** Star physical locations, in thousands of kilometers. */
    private StarGenerator starGenerator;

    /** Observations this class is based on. */
    private RealWorldObservations rwo = new RealWorldObservations();

    // ---- Methods ----
    public CloseStarObservations()
    {
        // Use real world observations for the reference square.
        this.starGenerator = CloseStarObservations.buildStarGenerator(
            rwo.getStarObservations(StarObservation.unixTimeOfManualData,
                REFERENCE_LATITUDE, REFERENCE_LONGITUDE),
            this.getModelSquare(REFERENCE_LATITUDE, REFERENCE_LONGITUDE),
            CloseStarObservations.getStarDistances());
    }

    public static HashMap<String, Float> getStarDistances()
    {
        HashMap<String, Float> distanceToStar = new HashMap<String, Float>();

        // These will be about one Earth radius (6300km) away.
        distanceToStar.put("Procyon", 6.0f);
        distanceToStar.put("Betelgeuse", 7.0f);
        distanceToStar.put("Rigel", 8.0f);
        distanceToStar.put("Aldebaran", 9.0f);

        // The next four will be about the Earth-Moon distance (380000km) away
        distanceToStar.put("Sirius", 380.0f);
        distanceToStar.put("Capella", 390.0f);
        distanceToStar.put("Polaris", 400.0f);
        distanceToStar.put("Dubhe", 410.0f);

        return distanceToStar;
    }

    public static StarGenerator buildStarGenerator(
        List<StarObservation> referenceObservations,
        SurfaceSquare referenceSquare,
        Map<String, Float> distanceToStar)
    {
        // Store the observations in the square.
        for (StarObservation so : referenceObservations) {
            referenceSquare.starObs.put(so.name, so);
        }

        // Build the star generator from that data.
        return new StarGenerator(referenceSquare, distanceToStar);
    }

    @Override
    public String getDescription()
    {
        return "spherical Earth with close stars";
    }

    @Override
    public List<StarObservation> getStarObservations(
        double unixTime,
        float latitude,
        float longitude)
    {
        // Get the square whose observations we want to compute,
        // using the spherical Earth model.
        SurfaceSquare targetSquare = this.getModelSquare(latitude, longitude);

        // Synthesize observations for it.
        return this.starGenerator.getMyStarObservations(targetSquare);
    }

    @Override
    public StarObservation getSunObservation(
        double unixTime,
        float latitude,
        float longitude)
    {
        return rwo.getSunObservation(unixTime, latitude, longitude);
    }

    @Override
    public Vector3f getModelPt(float latitude, float longitude)
    {
        return rwo.getModelPt(latitude, longitude);
    }

    @Override
    public Map<String, Vector4f> getModelStarMap()
    {
        return this.starGenerator.starLocations;
    }
}

// EOf
