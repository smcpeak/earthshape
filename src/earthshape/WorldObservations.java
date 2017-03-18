// WorldObservations.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.util.List;
import java.util.Map;

import util.Vector3f;
import util.Vector4f;

/** Interface to observations about the world.  The observations can
  * come from either direct measurement or synthesized from a model. */
public interface WorldObservations {
    /** Get a description for this set of observations so we can
      * tell the user what they are seeing. */
    String getDescription();

    // ---- Pure observations ----
    /** Find headings and distance from one point to another. */
    TravelObservation getTravelObservation(
        float startLatitude, float startLongitude,
        float endLatitude, float endLongitude);

    /** Get all the stars for which we potentially have observation
      * data.  This is used to allow the user to turn them on and off
      * in the UI.  The Sun is *not* meant to be included in this
      * list. */
    List<String> getAllStars();

    /** Get the set of stars visible from a certain location at a
      * certain time. */
    List<StarObservation> getStarObservations(
        double unixTime,
        float latitude,
        float longitude);

    /** Get an observed position of the Sun, which can be used to
      * disqualify a set of star observations as being invisible
      * due to the glare of the Sun.  This can return null. */
    StarObservation getSunObservation(
        double unixTime,
        float latitude,
        float longitude);

    // ---- Theoretical model ----
    // The following methods allow an observation set to include
    // information about what the reconstruction should look like
    // for easy comparison.

    /** If true, then clients can call 'getModelPt' and
      * 'getModelSquare'.  Otherwise, those method will just return
      * null for all points. */
    boolean hasModelPoints();

    /** Map the 2D space of latitude and longitude to points to a 3D
      * surface.  The map is expected to be continuous and smooth
      * within the boundary of latitude in [-90,90] and longitude in
      * [-180,180].  This is used to draw the wireframe of the
      * expected shape. */
    Vector3f getModelPt(float latitude, float longitude);

    /** Get details of a square at a given location, in the model
      * coordinate system.
      *
      * This square will not become part of the virtual 3D map, I am
      * just using it as a convenient package for some data about
      * the theoretical model that goes beyond just the location of
      * the point. */
    public SurfaceSquare getModelSquare(float latitude, float longitude);

    /** Return physical positions for all stars as 4D homogeneous
      * coordinates (so points at infinity can be represented).
      * This can be empty if there isn't an a-priori map. */
    Map<String, Vector4f> getModelStarMap();
}

// EOF
