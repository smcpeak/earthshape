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
public abstract class ManifoldObservations extends WorldObservations {
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
        startToEnd = startToEnd.rotateAADeg(startSquare.rotationFromNominal.times(-1));
        endToStart = endToStart.rotateAADeg(endSquare.rotationFromNominal.times(-1));

        // Get the components that are orthogonal to local "up".  Since we
        // already rotated them into their local spaces, up is +Y.
        Vector3f localUp = new Vector3f(0, 1, 0);
        Vector3f startToEndHorizontal =
            startToEnd.orthogonalComponentToUnitVector(localUp);
        Vector3f endToStartHorizontal =
            endToStart.orthogonalComponentToUnitVector(localUp);

        // Now get headings for both.
        double startToEndHeading = StarGenerator.azimuthOfLocalDirection(
            startToEndHorizontal.normalizeAsVector3d());
        double endToStartHeading = StarGenerator.azimuthOfLocalDirection(
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
        return StarGenerator.getStarObservations(square, this.getModelStarMap());
    }
}

// EOf
