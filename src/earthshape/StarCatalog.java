// StarCatalog.java
// See copyright.txt

package earthshape;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.FloatUtil;

/** A small, hardcoded catalog of star locations on the celestial
  * sphere. This is used to synthesize observational data that
  * should match the online planetarium I am using.  Each object
  * represents one star. */
public class StarCatalog {
    // ---- Class data ----
    // Regex patterns used by 'parse'.
    private static Pattern raPattern = Pattern.compile("(\\d+)h(\\d+)m(\\d+)s");
    private static Pattern decPattern = Pattern.compile("([-+0-9]+)°(\\d+)'(\\d+)\"");

    // ---- Instance data ----
    // Name of the star.
    String name;

    // Degrees east of the Sun's position at vernal equinox
    // along the celestial equator to the star's hour circle.
    float rightAscensionDegrees;

    // Degrees North of the celestial equator.
    float declinationDegrees;

    // ---- Methods ----
    public StarCatalog(
        String name_,
        float rightAscensionDegrees_,
        float declinationDegrees_)
    {
        this.name = name_;
        this.rightAscensionDegrees = rightAscensionDegrees_;
        this.declinationDegrees = declinationDegrees_;
    }

    /** Construct a catalog entry from strings representing right
      * ascension and declination as they appear in the in-the-sky.org
      * catalog. */
    public static StarCatalog parse(String name, String rightAscensionTime, String declinationDMS)
    {
        float rightAscensionDegrees;
        {
            Matcher m = raPattern.matcher(rightAscensionTime);
            if (m.matches()) {
                int hour = Integer.valueOf(m.group(1));
                int minute = Integer.valueOf(m.group(2));
                int second = Integer.valueOf(m.group(3));
                rightAscensionDegrees = (float)(hour*15.0 + minute*(15.0/60) + second*(15.0/3600));
            }
            else {
                throw new RuntimeException(
                    "StarCatalog.parse: could not parse RA: "+rightAscensionTime);
            }
        }

        float declinationDegrees;
        {
            Matcher m = decPattern.matcher(declinationDMS);
            if (m.matches()) {
                int degree = Integer.valueOf(m.group(1));
                boolean negative = degree < 0;
                if (negative) {
                    // Flip the sign on degree; we must negate the
                    // entire result, not just the degree portion.
                    degree = -degree;
                }
                int minute = Integer.valueOf(m.group(2));
                int second = Integer.valueOf(m.group(3));
                declinationDegrees = (float)(degree + minute/60.0 + second/3600.0);
                if (negative) {
                    declinationDegrees = -declinationDegrees;
                }
            }
            else {
                throw new RuntimeException(
                    "StarCatalog.parse: could not parse declination: "+declinationDMS);
            }
        }

        return new StarCatalog(name, rightAscensionDegrees, declinationDegrees);
    }

    /** Return a hardcoded array of star catalog entries.  The source
      * of this data is in-the-sky.org. */
    public static StarCatalog[] makeCatalog()
    {
        return new StarCatalog[] {
            parse("Capella", "05h16m41s", "+45°59'56\""),
            parse("Betelgeuse", "05h55m10s", "+07°24'25\""),
            parse("Rigel", "05h14m32s", "-08°12'05\""),
            parse("Aldebaran", "04h35m55s", "+16°30'35\""),
            parse("Sirius", "06h45m09s", "-16°42'47\""),
            parse("Procyon", "07h39m18s", "+05°13'39\""),
            parse("Polaris", "02h31m47s", "+89°15'50\""),
            parse("Dubhe", "11h03m43s", "+61°45'03\"")
        };
    }

    /** Construct a star observation for a given observer time and location.
      *
      * @param unixTime is seconds since 1970-01-01 00:00 GMT.
      * @param latitude is degrees North of the equator.
      * @param longitude is degrees East of the prime meridian. */
    public StarData makeObservation(
        double unixTime,
        float latitudeDegrees,
        float longitudeDegrees)
    {
        // First, we need to calculate Greenwich Mean Sidereal Time
        // corresponding to the given unixTime, in degrees.
        float gmstDegrees = unixTimeToGMST(unixTime) * 15.0f;

        // Next, calculate the "hour angle", which basically offsets
        // the RA due to time and longitude.  The equation I use
        // comes from:
        //
        //   https://en.wikipedia.org/wiki/Celestial_coordinate_system
        //
        // except their longitude has a flipped sign.
        float hourAngleDegrees = gmstDegrees + longitudeDegrees - this.rightAscensionDegrees;

        // I need some things in radians now.
        float latitudeRadians = FloatUtil.degreesToRadiansf(latitudeDegrees);
        float declRadians = FloatUtil.degreesToRadiansf(this.declinationDegrees);
        float hourAngleRadians = FloatUtil.degreesToRadiansf(hourAngleDegrees);

        // Now we can calculate azimuth and elevation in degrees.  The
        // formulas are derived from the same wikipedia page.
        float azimuthRadians = (float)Math.atan2(
            Math.sin(hourAngleRadians),
            Math.cos(hourAngleRadians) * Math.sin(latitudeRadians) -
                Math.tan(declRadians) * Math.cos(latitudeRadians));
        float elevationRadians = (float)Math.asin(
            Math.sin(latitudeRadians) * Math.sin(declRadians) +
            Math.cos(latitudeRadians) * Math.cos(declRadians) * Math.cos(hourAngleRadians));

        // The above formula yields an azimuth in [-pi,pi] where
        // 0 is South.  I want 0 to be North and a range of [0,2pi],
        // so add pi now.
        azimuthRadians += Math.PI;

        // Finally, combine everything into an observation.
        return new StarData(
            latitudeDegrees,
            longitudeDegrees,
            this.name,
            FloatUtil.radiansToDegreesf(azimuthRadians),
            FloatUtil.radiansToDegreesf(elevationRadians));
    }

    /** Convert a given unix time to Greenwich Mean Sidereal Time,
      * in hours, modulo 24 hours */
    public float unixTimeToGMST(double unixTime)
    {
        // Wikipedia gives the following equation at
        // https://en.wikipedia.org/wiki/Sidereal_time:
        //
        //    GMST = 18.697374558 + 24.06570982441908 × D
        //
        // where D is the number of elapsed UT1 days since
        // 2000-01-01 12:00 UT.  That reference time is 946728000
        // in unix time.
        double elapsedSeconds = unixTime - 946728000.0;
        double elapsedDays = elapsedSeconds / (24*60*60);
        double gmstRaw = 18.697374558 + 24.06570982441908 * elapsedDays;

        // Mod by 24 hours.  After that, 'float' has
        // adequate precision.
        return (float)(gmstRaw - Math.floor(gmstRaw / 24.0) * 24.0);
    }

    /** Calculate and print one observation, and compare it to what is
      * in the manually gathered observation data. */
    private static void printObs(StarData[] manualObs, StarCatalog sc,
        double unixTime, float latitude, float longitude)
    {
        StarData obs = sc.makeObservation(unixTime, latitude, longitude);
        System.out.println(obs);

        // Find the corresponding manual entry.
        boolean found = false;
        for (StarData m : manualObs) {
            if (m.name.equals(obs.name) &&
                m.latitude == latitude &&
                m.longitude == longitude)
            {
                float diffAzimuth = obs.azimuth - m.azimuth;
                float diffElevation = obs.elevation - m.elevation;
                System.out.println("diffAz="+diffAzimuth+", diffEl="+diffElevation);

                if (Math.abs(diffAzimuth) > 0.3 || Math.abs(diffElevation) > 0.3) {
                    System.out.println("OBSERVATION DIFFERS FROM CALCULATION");
                }

                found = true;
                break;
            }
        }
        if (!found) {
            System.out.println("NOT FOUND IN MANUAL OBSERVATIONS");
        }
    }

    public static void main(String args[])
    {
        // Get the manual data.
        StarData[] manualObs = StarData.getHardcodedData();

        // Calculate and print out observation data corresponding
        // to the points I gathered manually.
        double obsUnixTime = 1488772800.0;
        StarCatalog catalog[] = makeCatalog();
        for (StarCatalog sc : catalog) {
            printObs(manualObs, sc, obsUnixTime, 38, -122);
            printObs(manualObs, sc, obsUnixTime, 38, -113);
            printObs(manualObs, sc, obsUnixTime, 38, -104);
            printObs(manualObs, sc, obsUnixTime, 38, -95);
            printObs(manualObs, sc, obsUnixTime, 38, -86);
        }
    }
}

// EOF
