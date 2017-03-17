// CloseStarObservations.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import util.FloatUtil;
import util.Vector3d;
import util.Vector3f;

/** Observations for a hypothetical world that has the same size and
  * shape as the real Earth, but the stars are nearby. */
public class CloseStarObservations extends RealWorldObservations {
    // ---- Constants ----
    // The star observation data for this one location will be
    // the same as in the real world.
    public static final float REFERENCE_LATITUDE = 38;
    public static final float REFERENCE_LONGITUDE = -122;

    // ---- Instance data ----
    /** For some stars, the distance from the reference location to
      * that star, in thousands of kilometers. */
    private HashMap<String, Float> distanceToStar = new HashMap<String, Float>();

    // ---- Methods ----
    public CloseStarObservations()
    {
        // These will be about one Earth radius (6300km) away.
        this.distanceToStar.put("Procyon", 6.0f);
        this.distanceToStar.put("Betelgeuse", 7.0f);
        this.distanceToStar.put("Rigel", 8.0f);
        this.distanceToStar.put("Aldebaran", 9.0f);

        // The next four will be about the Earth-Moon distance (380000km) away
        this.distanceToStar.put("Sirius", 380.0f);
        this.distanceToStar.put("Capella", 390.0f);
        this.distanceToStar.put("Polaris", 400.0f);
        this.distanceToStar.put("Dubhe", 410.0f);
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

    @Override
    public List<StarObservation> getStarObservations(
        double unixTime,
        float latitude,
        float longitude)
    {
        // Get the observations that we will match.
        List<StarObservation> referenceObservations =
            super.getStarObservations(unixTime, REFERENCE_LATITUDE, REFERENCE_LONGITUDE);

        // Get the reference square and the square whose observations
        // we want to compute, using the spherical Earth model.
        SurfaceSquare refSquare = getSquare(REFERENCE_LATITUDE, REFERENCE_LONGITUDE);
        SurfaceSquare targetSquare = getSquare(latitude, longitude);

        // Start building a modified set of observations for the target.
        ArrayList<StarObservation> modified = new ArrayList<StarObservation>();
        for (StarObservation refObs : referenceObservations) {
            // How far away is the star from the reference location?
            Float distanceFromRef = this.distanceToStar.get(refObs.name);
            if (distanceFromRef == null) {
                continue;      // Skip this star.
            }

            // Vector from reference location to the star, in the reference
            // location's local geographic coordinate system.
            Vector3f refToStarLocal =
                Vector3f.azimuthElevationToVector(refObs.azimuth, refObs.elevation)
                    .times(distanceFromRef);

            // Convert it to an absolute position vector by first rotating
            // it the same way the square's orientation was, then adding
            // the square's location.
            Vector3f starAbsolute =
                refToStarLocal.rotateAA(refSquare.rotationFromNominal)
                    .plus(refSquare.center);

            // Now get a vector from the target square to the star, in
            // global coordinates.
            Vector3f targetToStarGlobal = starAbsolute.minus(targetSquare.center);

            // Convert it to the target square's local coordinates by
            // reversing that square's rotation.
            Vector3f targetToStarLocal =
                targetToStarGlobal.rotateAA(targetSquare.rotationFromNominal.times(-1));

            // Normalize to treat it as a direction in local coordinates.
            // Use 'double' due to the sensitivity of normalization.
            Vector3d dir = targetToStarLocal.normalizeAsVector3d();

            // Extract azimuth and elevation from the components of 'dir'.
            double elevation = elevationOfLocalDirection(dir);
            double azimuth = azimuthOfLocalDirection(dir);

            // Package this up as an observation.
            modified.add(new StarObservation(
                latitude,
                longitude,
                refObs.name,
                azimuth,
                elevation));
        }

        return modified;
    }

    /** Given a unit vector in my "nominal" coordinate system, where
      * -Z is north and +Y is up, return its elevation in [-90,90]
      * degrees. */
    public static double elevationOfLocalDirection(Vector3d dir)
    {
        // Elevation is straightforward since +Y is up in the local
        // coordinate system.
        return FloatUtil.asinDeg(dir.y());
    }

    /** Given a unit vector in my "nominal" coordinate system, where
      * -Z is north and +Y is up, return its azimuth in [0,360]
      * degrees. */
    public static double azimuthOfLocalDirection(Vector3d dir)
    {
        // Azimuth is a little tricky since my axes and angles are
        // oriented differently from the standard 2D coordinate
        // system used by atan2.
        //
        // The first argument to atan2 is the standard Y coordinate,
        // which starts at 0 and increases as standard degrees
        // increase (they go counter-clockwise from the standard X axis).
        // Here, 'dir.x()' starts at 0 and increases as azimuth
        // degrees increase (clockwise from North, which is my -Z).
        //
        // The second argument to atan2 is the standard X coordinate,
        // which starts at 1 and decreases as standard degrees
        // increase.  Here, '-dir.z()' does that as azimuth degrees
        // increase.
        double azimuth = FloatUtil.atan2Deg(dir.x(), -dir.z());

        // atan2 return is in [-180,180] but I want [0,360].
        return FloatUtil.modulus2(azimuth, 0, 360);
    }
}

// EOf
