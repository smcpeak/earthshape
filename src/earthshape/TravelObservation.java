// TravelObservation.java
// See copyright.txt for license and terms of use.

package earthshape;

/** Observed headings and distance between two points on the Earth's
  * surface, identified by latitude and longitude, when traveling
  * from one to the other along the shortest route.  An instance of
  * this class is returned by WorldObservations.getTravelObservation. */
public class TravelObservation {
    // Start and end coordinates, in degrees.  These should be stored
    // normalized to latitude in [-90,90] degrees North of the equator
    // and longitude in (-180,180] degrees East of the prime meridian.
    public float startLatitude;
    public float startLongitude;
    public float endLatitude;
    public float endLongitude;

    /** Distance along the surface between the points, in kilometers. */
    public float distanceKm;

    /** Direction that one must travel leaving the start location, in
      * [0,360) degrees East of North, according to the local geographic
      * orientation at the start location. */
    public double startToEndHeading;

    /** Direction one must travel to go from the end location back to the
      * start location, according to the local geographic orientation
      * at the end location. */
    public double endToStartHeading;

    public TravelObservation()
    {}

    public TravelObservation(
        float startLatitude_,
        float startLongitude_,
        float endLatitude_,
        float endLongitude_,
        float distanceKm_,
        double startToEndHeading_,
        double endToStartHeading_)
    {
        this.startLatitude = startLatitude_;
        this.startLongitude = startLongitude_;
        this.endLatitude = endLatitude_;
        this.endLongitude = endLongitude_;
        this.distanceKm = distanceKm_;
        this.startToEndHeading = startToEndHeading_;
        this.endToStartHeading = endToStartHeading_;
    }
}

// EOF
