// RealWorldObservations.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import util.FloatUtil;
import util.Vector3f;
import util.Vector4f;

/** Observations for the actual Earth. */
public class RealWorldObservations extends WorldObservations {
    // ---- Constants ----
    /** The average radius of the Earth in kilometers, from Wikipedia.
      *
      * Within this class, this value is used to calculate the distance
      * between different surface locations, given their latitude and
      * longitude, which is something that is fairly easy to measure
      * directly, at least for points on land. */
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

    /** Star physical directions, as inferred from the observations
      * at 38N, 122W.  This is used to plot their theoretical positions
      * in the 3D map, but *not* used in the surface reconstruction
      * algorithm. */
    private StarGenerator starGenerator;

    // ---- Methods ----
    public RealWorldObservations()
    {
        this.starGenerator = CloseStarObservations.buildStarGenerator(
            this.getStarObservations(StarObservation.unixTimeOfManualData,
                CloseStarObservations.REFERENCE_LATITUDE,
                CloseStarObservations.REFERENCE_LONGITUDE),
            this.getModelSquare(
                CloseStarObservations.REFERENCE_LATITUDE,
                CloseStarObservations.REFERENCE_LONGITUDE),
            new HashMap<String, Float>() /*all at infinity*/);
    }

    @Override
    public String getDescription()
    {
        return "real world star data";
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

    @Override
    public boolean hasModelPoints()
    {
        return true;
    }

    // This is the theoretical sphere that our observations should be
    // able to reconstruct.
    @Override
    public Vector3f getModelPt(float latitude, float longitude)
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
        Vector3f pt =
            new Vector3f(0, RealWorldObservations.EARTH_RADIUS_KM / 1000.0f, 0);

        // Then rotate about the X axis (toward one of the
        // poles) for latitude.
        pt = pt.rotateDeg(-latitude, new Vector3f(1, 0, 0));

        // Then rotate about the Earth's spin axis to account
        // for longitude.
        pt = pt.rotateDeg(-longitude, new Vector3f(0, 0, 1));

        return pt;
    }

    @Override
    public Map<String, Vector4f> getModelStarMap()
    {
        return this.starGenerator.starLocations;
    }
}

// EOf
