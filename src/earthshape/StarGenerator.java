// StarGenerator.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import util.FloatUtil;
import util.Vector3d;
import util.Vector3f;
import util.Vector4f;

/** This class generates star observations, for use by an implementor
  * of 'WorldObservations', such that
  * their direction matches some other given observations (e.g.,
  * real world observations) from a chosen location, possibly
  * also giving finite distances to them.. */
public class StarGenerator {
    // ---- Instance data ----
    /** For some stars, the distance from the reference location to
      * that star, in thousands of kilometers. */
    private HashMap<String, Float> distanceToStar = new HashMap<String, Float>();

    /** Physical positions of stars such that they have the proper
      * distances and sky positions w.r.t. the reference location.
      * This member is the "output" of the class; clients call the
      * constructor and then read this field.  The units of the
      * vector are nominally thousands of kilometers, although the
      * client can scale them arbitrarily of course.
      *
      * Homogeneous coordinates are used to allow expressing stars
      * at infinity, but this class only generates finite distance
      * stars currently. */
    public HashMap<String, Vector4f> starLocations = new HashMap<String, Vector4f>();

    // ---- Methods ----
    public StarGenerator(SurfaceSquare referenceSquare)
    {
        this.setStarDistances();
        this.setStarLocations(referenceSquare);
    }

    /** Populate 'this.distanceToStar'. */
    private void setStarDistances()
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

    /** Populate 'this.starLocations'. */
    private void setStarLocations(SurfaceSquare refSquare)
    {
        // Iterate over the observations in the reference square.
        for (Map.Entry<String, StarObservation> e : refSquare.starObs.entrySet()) {
            StarObservation refObs = e.getValue();

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

            // Store this position.
            this.starLocations.put(refObs.name, new Vector4f(starAbsolute));
        }
    }

    /** Given the physical star locations in 'starLocations', synthesize
      * observations for 'square'. */
    public static List<StarObservation> getStarObservations(
        SurfaceSquare square, Map<String, Vector4f> starLocations)
    {
        ArrayList<StarObservation> ret = new ArrayList<StarObservation>();
        for (Map.Entry<String, Vector4f> e : starLocations.entrySet()) {
            String starName = e.getKey();

            // Absolute location of the star in both 4D and 3D.
            Vector4f starLoc4D = e.getValue();
            Vector3f starLoc3D = new Vector3f(starLoc4D.x(), starLoc4D.y(), starLoc4D.z());

            // Get a vector to the star in global coordinates.
            Vector3f targetToStarGlobal;
            if (starLoc4D.w() == 0) {
                // Point at infinity; the position of the square is
                // irrelevant.
                targetToStarGlobal = starLoc3D;
            }
            else {
                // Subtract off the square's coordinates.
                targetToStarGlobal = starLoc3D.minus(square.center);
            }

            // Convert it to the target square's local coordinates by
            // reversing that square's rotation.
            Vector3f targetToStarLocal =
                targetToStarGlobal.rotateAA(square.rotationFromNominal.times(-1));

            // Normalize to treat it as a direction in local coordinates.
            // Use 'double' due to the sensitivity of normalization.
            Vector3d dir = targetToStarLocal.normalizeAsVector3d();

            // Extract azimuth and elevation from the components of 'dir'.
            double elevation = StarGenerator.elevationOfLocalDirection(dir);
            double azimuth = StarGenerator.azimuthOfLocalDirection(dir);

            // Package this up as an observation.
            ret.add(new StarObservation(
                square.latitude,
                square.longitude,
                starName,
                azimuth,
                elevation));
        }

        return ret;
    }

    /** Given the physical star locations in 'this.starLocations', synthesize
      * observations for 'square'. */
    public List<StarObservation> getMyStarObservations(SurfaceSquare square)
    {
        return StarGenerator.getStarObservations(square, this.starLocations);
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
