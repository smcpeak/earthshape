// ManifoldObservations.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import util.Vector3f;
import util.Vector4f;

/** Observations for an arbitrary 2D manifold (surface) and set
  * of physical star locations (which can be infinitely far away). */
public abstract class ManifoldObservations implements WorldObservations {
    // ---- Methods ----
    /** In a sense, the defining characteristic of this class is that
      * derived classes always have an underlying model. */
    @Override
    public boolean hasModelPoints()
    {
        return true;
    }

    @Override
    public TravelObservation getTravelObservation(
        float startLatitude, float startLongitude,
        float endLatitude, float endLongitude)
    {
        // Get squares for both endpoints.
        SurfaceSquare startSquare = this.getModelSquare(startLatitude, startLongitude);
        SurfaceSquare endSquare = this.getModelSquare(endLatitude, endLongitude);

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
        double startToEndHeading = CloseStarGenerator.azimuthOfLocalDirection(
            startToEndHorizontal.normalizeAsVector3d());
        double endToStartHeading = CloseStarGenerator.azimuthOfLocalDirection(
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

    /** Get details of a square at a given location, in the model
      * coordinate system.
      *
      * This square will not become part of the virtual 3D map, I am
      * just using it as a convenient package for some data about
      * the theoretical model. */
    public SurfaceSquare getModelSquare(float latitude, float longitude)
    {
        // Get the center of the square using the model.
        Vector3f center = this.getModelPt(latitude, longitude);

        // Compute North by making a small change to latitude.
        Vector3f north;
        if (latitude >= 0) {
            Vector3f littleSouth = this.getModelPt(latitude - 0.1f, longitude);
            north = center.minus(littleSouth).normalize();
        }
        else {
            Vector3f littleNorth = this.getModelPt(latitude + 0.1f, longitude);
            north = littleNorth.minus(center).normalize();
        }

        // And similarly for East.
        Vector3f east;
        if (longitude >= 0) {
            Vector3f littleWest = this.getModelPt(latitude, longitude - 0.1f);
            east = center.minus(littleWest).normalize();
        }
        else {
            Vector3f littleEast = this.getModelPt(latitude, longitude + 0.1f);
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
        for (Map.Entry<String, Vector4f> e : this.getModelStarMap().entrySet()) {
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
        SurfaceSquare square = this.getModelSquare(latitude, longitude);

        // Synthesize observations for it.
        return CloseStarGenerator.getStarObservations(square, this.getModelStarMap());
    }
}

// EOf
