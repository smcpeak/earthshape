// WorldObservations.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import util.Vector3f;
import util.Vector4f;

/** Interface to observations about the world.  The observations can
  * come from either direct measurement or synthesized from a model. */
public abstract class WorldObservations {
    /** Get a description for this set of observations so we can
      * tell the user what they are seeing. */
    public abstract String getDescription();

    // ---- Pure observations ----
    /** Find headings and distance from one point to another. */
    public abstract TravelObservation getTravelObservation(
        float startLatitude, float startLongitude,
        float endLatitude, float endLongitude);

    /** Get all the stars for which we potentially have observation
      * data.  This is used to allow the user to turn them on and off
      * in the UI.  The Sun is *not* meant to be included in this
      * list. */
    public abstract List<String> getAllStars();

    /** Get the set of stars visible from a certain location at a
      * certain time. */
    public abstract List<StarObservation> getStarObservations(
        double unixTime,
        float latitude,
        float longitude);

    /** Get an observed position of the Sun, which can be used to
      * disqualify a set of star observations as being invisible
      * due to the glare of the Sun.  This can return null. */
    public StarObservation getSunObservation(
        double unixTime,
        float latitude,
        float longitude)
    {
        return null;
    }

    // ---- Theoretical model ----
    // The following methods allow an observation set to include
    // information about what the reconstruction should look like
    // for easy comparison.

    /** If true, then clients can call 'getModelPt' and
      * 'getModelSquare'.  Otherwise, those method will just return
      * null for all points. */
    public boolean hasModelPoints()
    {
        return false;
    }

    /** Map the 2D space of latitude and longitude to points to a 3D
      * surface.  The map is expected to be continuous and smooth
      * within the boundary of latitude in [-90,90] and longitude in
      * [-180,180].  This is used to draw the wireframe of the
      * expected shape. */
    public Vector3f getModelPt(float latitude, float longitude)
    {
        return null;
    }

    /** Return physical positions for all stars as 4D homogeneous
      * coordinates (so points at infinity can be represented).
      * This can be empty if there isn't an a-priori map. */
    public Map<String, Vector4f> getModelStarMap()
    {
        return new HashMap<String, Vector4f>();
    }

    /** Get details of a square at a given location, in the model
      * coordinate system.
      *
      * This square will not become part of the virtual 3D map, I am
      * just using it as a convenient package for some data about
      * the theoretical model that goes beyond just the location of
      * the point. */
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
        Vector3f rot1NominalEast = (new Vector3f(1, 0, 0)).rotateAADeg(rot1);

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
}

// EOF
