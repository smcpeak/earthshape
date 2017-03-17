// ManifoldObservations.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import util.Vector3d;
import util.Vector3f;
import util.Vector4f;

/** Observations for an arbitrary 2D manifold (surface) and set
  * of physical star locations (which can be infinitely far away). */
public abstract class ManifoldObservations implements WorldObservations {
    // ---- Abstract methods ----
    /** Map the 2D space of latitude and longitude to points on a 3D
      * surface.  The map is expected to be continuous and smooth
      * within the boundary of latitude in [-90,90] and longitude in
      * [-180,180]. */
    public abstract Vector3f mapLL(float latitude, float longitude);

    /** Return physical positions for all stars as 4D homogeneous
      * coordinates (so points at infinity can be represented). */
    public abstract Map<String, Vector4f> getStarMap();

    // ---- Methods ----
    @Override
    public TravelObservation getTravelObservation(
        float startLatitude, float startLongitude,
        float endLatitude, float endLongitude)
    {
        // Get squares for both endpoints.
        SurfaceSquare startSquare = this.getSquare(startLatitude, startLongitude);
        SurfaceSquare endSquare = this.getSquare(endLatitude, endLongitude);

        // Get global travel vectors in each direction.  This does
        // not take into account the possibility of a curved surface
        // in between, nor alternate shorter paths, but this should
        // suffice anyway for my demonstration.
        Vector3f startToEnd = endSquare.center.minus(startSquare.center);
        Vector3f endToStart = startSquare.center.minus(endSquare.center);

        // Translate them into local travel vectors by inverting the
        // rotation for each square.
        startToEnd = startToEnd.rotateAA(startSquare.rotationFromNominal.times(-1));
        endToStart = endToStart.rotateAA(endSquare.rotationFromNominal.times(-1));

        // Get the components that are orthogonal to local "up".
        Vector3f startToEndHorizontal =
            startToEnd.orthogonalComponentToUnitVector(startSquare.up);
        Vector3f endToStartHorizontal =
            endToStart.orthogonalComponentToUnitVector(endSquare.up);

        // Now get headings for both.
        double startToEndHeading = CloseStarObservations.azimuthOfLocalDirection(
            startToEndHorizontal.normalizeAsVector3d());
        double endToStartHeading = CloseStarObservations.azimuthOfLocalDirection(
            endToStartHorizontal.normalizeAsVector3d());

        // The distance is straightforward (again, ignoring intervening
        // ground).
        float distanceKm = (float)(startToEnd.length() * 1000.0);

        return new TravelObservation(
            startLatitude, startLongitude,
            endLatitude, endLongitude,
            distanceKm,
            startToEndHeading,
            endToStartHeading);
    }

    /** Get details of a square at a given location, in the mapLL
      * coordinate system.
      *
      * This square will not become part of the virtual 3D map, I am
      * just using it as a convenient package for some data. */
    public SurfaceSquare getSquare(float latitude, float longitude)
    {
        // Get the center of the square using mapLL directly.
        Vector3f center = this.mapLL(latitude, longitude);

        // Compute North by making a small change to latitude.
        Vector3f north;
        if (latitude >= 0) {
            Vector3f littleSouth = this.mapLL(latitude - 0.1f, longitude);
            north = center.minus(littleSouth).normalize();
        }
        else {
            Vector3f littleNorth = this.mapLL(latitude + 0.1f, longitude);
            north = littleNorth.minus(center).normalize();
        }

        // And similarly for East.
        Vector3f east;
        if (longitude >= 0) {
            Vector3f littleWest = this.mapLL(latitude, longitude - 0.1f);
            east = center.minus(littleWest).normalize();
        }
        else {
            Vector3f littleEast = this.mapLL(latitude, longitude + 0.1f);
            east = littleEast.minus(center).normalize();
        }

        // Compute up with cross product.
        Vector3f up = east.cross(north);

        // Now recompute east to make sure all three are orthogonal
        // even if the space has high orientation warp.
        east = north.cross(up);

        // Now we need a rotation from my "nominal" orientation.  Start
        // with a rotation that aligns North, which is -Z nominally.
        Vector3f rot1 = (new Vector3f(0, 0, -1)).rotationToBecome(north);

        // Apply that rotation to nominal East (+X).
        Vector3f rot1NominalEast = (new Vector3f(1, 0, 0)).rotateAA(rot1);

        // Now get a rotation that gets East into final position.  This
        // will be parallel to local North, hence preserving it, because
        // North is orthogonal to both vectors.
        Vector3f rot2 = rot1NominalEast.rotationToBecome(east);

        // The composition of these two is the rotation from base.
        Vector3f rot = Vector3f.composeRotations(rot1, rot2);

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
        // By default we will ignore the Sun in this model.
        return null;
    }

    @Override
    public List<String> getAllStars()
    {
        ArrayList<String> ret = new ArrayList<String>();
        for (Map.Entry<String, Vector4f> e : this.getStarMap().entrySet()) {
            ret.add(e.getKey());
        }
        return ret;
    }

    @Override
    public List<StarObservation> getStarObservations(
        double unixTime,
        float latitude,
        float longitude)
    {
        // Get the square at this location.
        SurfaceSquare square = this.getSquare(latitude, longitude);

        ArrayList<StarObservation> ret = new ArrayList<StarObservation>();
        for (Map.Entry<String, Vector4f> e : this.getStarMap().entrySet()) {
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
            double elevation = CloseStarObservations.elevationOfLocalDirection(dir);
            double azimuth = CloseStarObservations.azimuthOfLocalDirection(dir);

            // Package this up as an observation.
            ret.add(new StarObservation(
                latitude,
                longitude,
                starName,
                azimuth,
                elevation));
        }

        return ret;
    }

}

// EOf
