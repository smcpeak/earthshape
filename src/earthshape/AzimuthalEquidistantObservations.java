// AzimuthalEquidistantObservations.java
// See copyright.txt for license and terms of use.

package earthshape;

import util.FloatUtil;
import util.Vector3f;

/** Observations for a hypothetical world that a physical realization
  * of an azimuthal equidistant projection of the Earth.  Also, some of
  * the stars are nearby. */
public class AzimuthalEquidistantObservations extends CloseStarObservations {
    @Override
    public String getDescription()
    {
        return "azimuthal equidistant projection flat Earth data";
    }

    @Override
    public TravelObservation getTravelObservation(
        float startLatitude, float startLongitude,
        float endLatitude, float endLongitude)
    {
        // Get squares for both endpoints.
        SurfaceSquare startSquare = this.getSquare(startLatitude, startLongitude);
        SurfaceSquare endSquare = this.getSquare(endLatitude, endLongitude);

        // Get global travel vectors in each direction.
        Vector3f startToEnd = endSquare.center.minus(startSquare.center);
        Vector3f endToStart = startSquare.center.minus(endSquare.center);

        // Translate them into local travel vectors by inverting the
        // rotation for each square.
        startToEnd = startToEnd.rotateAA(startSquare.rotationFromNominal.times(-1));
        endToStart = endToStart.rotateAA(endSquare.rotationFromNominal.times(-1));

        // We know these are in the flat plane, so there is no need to
        // remove an "up" component before computing the headings.
        double startToEndHeading = CloseStarObservations.azimuthOfLocalDirection(
            startToEnd.normalizeAsVector3d());
        double endToStartHeading = CloseStarObservations.azimuthOfLocalDirection(
            endToStart.normalizeAsVector3d());

        // The distance is straightforward.
        float distanceKm = (float)(startToEnd.length() * 1000.0);

        return new TravelObservation(
            startLatitude, startLongitude,
            endLatitude, endLongitude,
            distanceKm,
            startToEndHeading,
            endToStartHeading);
    }

    // We only have to change how squares are placed in space to get
    // this new hypothetical world.  The existing code for
    // CloseStarObservations to make star observations will then
    // work unchanged.
    @Override
    public SurfaceSquare getSquare(float latitude, float longitude)
    {
        // The coordinate system here has the North pole at the center,
        // with North pole up as +Y, and the prime meridian intersecting
        // the +Z axis.  The diagram here helps to visualize:
        //
        // https://en.wikipedia.org/wiki/Azimuthal_equidistant_projection
        //
        // The units of this coordinate system are 1000 kilometers.

        // To compute the center of this square, start with a unit vector
        // going from the North pole toward the prime meridian.
        Vector3f center = new Vector3f(0, 0, 1);

        // Now stretch it proportionally to the latitude, preserving
        // distances along meridians.
        center = center.times(FloatUtil.degreesToRadiansf(90.0f - latitude)
            * RealWorldObservations.EARTH_RADIUS_KM / 1000.0f);

        // Then rotate it around "up" to match the longitude.
        Vector3f rot = new Vector3f(0, longitude, 0);
        center = center.rotateAA(rot);

        // Also define unit vectors that will be geographic North and up.
        Vector3f north = new Vector3f(0, 0, -1);
        Vector3f up = new Vector3f(0, 1, 0);

        // They are rotated the same way as our center vector.
        north = north.rotateAA(rot);
        up = up.rotateAA(rot);        // Up will not change.

        // Package these up.
        return new SurfaceSquare(
            center,
            north,
            up,
            1,              // sizeKm, irrelevant here
            latitude,
            longitude,
            null,           // baseSquare
            null,           // baseMidpoint
            rot);           // rotationFromBase
    }

    @Override
    public StarObservation getSunObservation(
        double unixTime,
        float latitude,
        float longitude)
    {
        // This model cannot handle the Sun at all, so drop it.
        return null;
    }

}

// EOf
