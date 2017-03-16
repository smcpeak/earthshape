// WorldObservations.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.util.List;

/** Interface to observations about the world.  The observations can
  * come from either direct measurement or synthesized from a model. */
public interface WorldObservations {
    /** Get a description for this set of observations so we can
      * tell the user what they are seeing. */
    String getDescription();

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
}

// EOF
