// RealWorldObservations.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import util.FloatUtil;

/** Observations for the actual Earth. */
public class RealWorldObservations implements WorldObservations {
    // ---- Constants ----
    /** The average radius of the Earth in kilometers, from Wikipedia.
      *
      * Within this class, this value is used to calculate the distance
      * between different surface locations, given their latitude and
      * longitude, which is something that can also be easily measured
      * by anyone. */
    public static final float EARTH_RADIUS_KM = 6371.0f;

    // ---- Instance data ----
    /** Some star observations I gathered manually from an
      * online planetarium. */
    private StarObservation[] manualObservations = StarObservation.getManualObservations();

    /** A small catalog of celestial coordinates from that
      * same planetarium.  These are used the synthesize
      * observations from times and places that I did not
      * manually measure.  I have confirmed that the
      * synthetic observations agree with the manual ones. */
    private StarCatalog[] starCatalog = StarCatalog.makeCatalog();

    /** Position of the sun on StarObservation.unixTimeOfManualData. */
    private StarCatalog sunPosition = StarCatalog.sunPosition();

    // ---- Methods ----
    @Override
    public String getDescription()
    {
        return "real world data";
    }

    @Override
    public TravelObservation getTravelObservation(
        float startLatitude, float startLongitude,
        float endLatitude, float endLongitude)
    {
        // Normalize latitude and longitude.
        startLatitude = FloatUtil.clampLatitudef(startLatitude);
        startLongitude = FloatUtil.normalizeLongitudef(startLongitude);
        endLatitude = FloatUtil.clampLatitudef(endLatitude);
        endLongitude = FloatUtil.normalizeLongitudef(endLongitude);

        // Calculate the angle along the spherical Earth subtended
        // by the arc from 'old' to the new coordinates.
        double arcAngleDegrees = FloatUtil.sphericalSeparationAngle(
            startLongitude, startLatitude,
            endLongitude, endLatitude);

        // Calculate the distance along the surface that separates
        // these points.  Obviously, this statement and the one above
        // encode the size and shape of the Earth.  But this is
        // empirically the same value one would obtain by walking
        // or driving the path in question; the accuracy of the formula
        // it is readily verifiable, regardless of the model used to
        // compute it.
        float distanceKm =
            (float)(arcAngleDegrees / 180.0 * Math.PI * EARTH_RADIUS_KM);

        // Calculate headings, in degrees East of North, from
        // the old square to the new and vice-versa.
        double startToEndHeading = FloatUtil.getLatLongPairHeading(
            startLatitude, startLongitude, endLatitude, endLongitude);
        double endToStartHeading = FloatUtil.getLatLongPairHeading(
            endLatitude, endLongitude, startLatitude, startLongitude);

        // Package the result.
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
        for (StarCatalog sc : this.starCatalog) {
            ret.add(sc.name);
        }
        return ret;
    }

    @Override
    public List<StarObservation> getStarObservations(
        double unixTime,
        float latitude,
        float longitude)
    {
        ArrayList<StarObservation> ret = new ArrayList<StarObservation>();

        // For which stars do I have manual data?
        HashSet<String> manualStars = new HashSet<String>();
        if (unixTime == StarObservation.unixTimeOfManualData) {
            for (StarObservation so : this.manualObservations) {
                if (latitude == so.latitude && longitude == so.longitude) {
                    manualStars.add(so.name);
                    ret.add(so);
                }
            }
        }

        // Synthesize observations for others.
        for (StarCatalog sc : this.starCatalog) {
            if (!manualStars.contains(sc.name)) {
                StarObservation so = sc.makeObservation(unixTime, latitude, longitude);
                ret.add(so);
            }
        }

        return ret;
    }

    @Override
    public StarObservation getSunObservation(
        double unixTime,
        float latitude,
        float longitude)
    {
        if (unixTime == StarObservation.unixTimeOfManualData) {
            return this.sunPosition.makeObservation(unixTime, latitude, longitude);
        }
        else {
            // I do not have data on the Sun for other times.
            return null;
        }
    }
}

// EOf
