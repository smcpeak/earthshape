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
            this.getSquare(REFERENCE_LATITUDE, REFERENCE_LONGITUDE));
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

    /** Get details of a square at a given location, in an arbitrary
      * coordinate system, using a spherical model for the Earth.
      * This square will not become part of the virtual 3D map, I am
      * just using it as a convenient package for some data. */
    @Override
    public SurfaceSquare getSquare(float latitude, float longitude)
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
        Vector3f center =
            new Vector3f(0, RealWorldObservations.EARTH_RADIUS_KM / 1000.0f, 0);

        // Also define unit vectors that will be geographic North and up.
        Vector3f north = new Vector3f(0, 0, -1);
        Vector3f up = new Vector3f(0, 1, 0);

        // Now, define a composed rotation to put each of these three
        // into the proper position for the square we want.

        // Start with a rotation about the X axis (toward one of the
        // poles) for latitude as an angle-axis vector.
        Vector3f rot = new Vector3f(-latitude, 0, 0);

        // Then add a rotation about the Earth's spin axis to account
        // for longitude.
        rot = Vector3f.composeRotations(rot, new Vector3f(0, 0, -longitude));

        // Now apply this rotation to all three spatial vectors.
        center = center.rotateAA(rot);
        north = north.rotateAA(rot);
        up = up.rotateAA(rot);

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

    // TODO: Why is this necessary?  I thought the implementation in
    // ManifoldObservations would suffice given how I defined mapLL.
    @Override
    public TravelObservation getTravelObservation(
        float startLatitude, float startLongitude,
        float endLatitude, float endLongitude)
    {
        return rwo.getTravelObservation(startLatitude, startLongitude, endLatitude, endLongitude);
    }

    @Override
    public List<StarObservation> getStarObservations(
        double unixTime,
        float latitude,
        float longitude)
    {
        // Get the square whose observations we want to compute,
        // using the spherical Earth model.
        SurfaceSquare targetSquare = this.getSquare(latitude, longitude);

        // Synthesize observations for it.
        return this.starGenerator.getMyStarObservations(targetSquare);
    }

    @Override
    public Vector3f mapLL(float latitude, float longitude)
    {
        // This is backwards from how ManifoldObservations usually works:
        // I derive the coordinate from the square here.
        SurfaceSquare s = this.getSquare(latitude, longitude);
        return s.center;
    }

    @Override
    public Map<String, Vector4f> getStarMap()
    {
        return this.starGenerator.starLocations;
    }

    @Override
    public StarObservation getSunObservation(
        double unixTime,
        float latitude,
        float longitude)
    {
        return rwo.getSunObservation(unixTime, latitude, longitude);
    }
}

// EOf
