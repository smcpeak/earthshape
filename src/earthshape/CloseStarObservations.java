// CloseStarObservations.java
// See copyright.txt for license and terms of use.

package earthshape;

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
    private CloseStarGenerator starGenerator;

    /** Observations this class is based on. */
    private RealWorldObservations rwo = new RealWorldObservations();

    // ---- Methods ----
    public CloseStarObservations()
    {
        // Use real world observations for the reference square.
        this.starGenerator = CloseStarObservations.buildStarGenerator(
            rwo.getStarObservations(StarObservation.unixTimeOfManualData,
                REFERENCE_LATITUDE, REFERENCE_LONGITUDE),
            this.getModelSquare(REFERENCE_LATITUDE, REFERENCE_LONGITUDE));
    }

    public static CloseStarGenerator buildStarGenerator(
        List<StarObservation> referenceObservations, SurfaceSquare referenceSquare)
    {
        // Store the observations in the square.
        for (StarObservation so : referenceObservations) {
            referenceSquare.starObs.put(so.name, so);
        }

        // Build the star generator from that data.
        return new CloseStarGenerator(referenceSquare);
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
        // The coordinate system here has the Earth's center at the
        // origin, its spin axis along the Z axis with celestial North
        // at -Z, and the prime meridian intersecting the +Y axis.
        // This orientation means that the square at (latitude 0,
        // longitude 0) is oriented the same way as the output of
        // Vector3f.azimuthElevationToVector.
        //
        // The units of this coordinate system are 1000 kilometers.

        // Start with a vector going from the center of the Earth to
        // the intersection of the equator and prime meridian.  This
        // will become the center of the resulting square.
        Vector3f pt =
            new Vector3f(0, RealWorldObservations.EARTH_RADIUS_KM / 1000.0f, 0);

        // Then rotate about the X axis (toward one of the
        // poles) for latitude.
        pt = pt.rotate(-latitude, new Vector3f(1, 0, 0));

        // Then rotate about the Earth's spin axis to account
        // for longitude.
        pt = pt.rotate(-longitude, new Vector3f(0, 0, 1));

        return pt;
    }

    @Override
    public Map<String, Vector4f> getModelStarMap()
    {
        return this.starGenerator.starLocations;
    }
}

// EOf
