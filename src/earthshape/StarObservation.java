// StarObservation.java
// See copyright.txt for license and terms of use.

package earthshape;

/** Contains an observation of one star from one location on
  * Earth at a particular point in time.  For now, the point in
  * time is not stored as data, as it is assumed that all
  * observations are from the same point in time. */
public class StarObservation {
    // Latitude of observer in degrees North of equator.
    // Should be in [-90,90].
    public float latitude;

    // Longitude of observer in degrees East of the prime
    // meridian.  Should be in [-180,180].
    public float longitude;

    // Name of the star that is observed.
    public String name;

    // Degrees East of geographic North of the point on the
    // horizon nearest to the star's location in the sky.
    // Should be in [0,360].
    public float azimuth;

    // Degrees above the horizon that the star appears.
    // Should be in [-90,90], although I intend to only
    // use observations where elevation is at least 20.
    public float elevation;

    public StarObservation(
        float latitude_,
        float longitude_,
        String name_,
        double azimuth_,
        double elevation_)
    {
        this.latitude = latitude_;
        this.longitude = longitude_;
        this.name = name_;
        this.azimuth = (float)azimuth_;
        this.elevation = (float)elevation_;
    }

    public String toString()
    {
        return "lat="+latitude+
               ", lng="+longitude+
               ", name=\""+name+
               "\", az="+azimuth+
               ", el="+elevation;
    }

    /** Get some manually gathered data I have hardcoded. */
    public static StarObservation[] getManualObservations()
    {
        // This data comes from the online planetarium at
        // "https://in-the-sky.org/skymap.php".  I manually
        // set the date and time to "2017-03-05 20:00 -08:00"
        // for all observations, and reset the time between
        // each star measurement (since otherwise the app
        // slowly adjusts star locations as time passes).
        //
        // The set of stars chosen were simply the ones that
        // the app chooses to label when set to only show
        // bright stars, plus Polaris and Dubhe (Alpha Ursae
        // Majoris) because they/ are well-known and helped
        // ensure better coverage of the full sky.
        //
        // For each measurement, I manually placed my mouse
        // point on top of the star, centering it as best I
        // could, and then reading off the azimuth and
        // elevation from the app's status display.  The
        // values have a resolution of a tenth of a degree,
        // but I estimate their true accuracy to be closer
        // to plus or minus two tenths of a degree due to
        // the crudeness of my collection technique.
        //
        // Obviously, there are more accurate ways of knowing
        // star positions.  I did this manually in order to
        // replicate what someone with only a hand sextant and
        // a dark sky might do.

        return new StarObservation[] {
            new StarObservation(38,-122,"Capella",302.8,71.2),
            new StarObservation(38,-122,"Betelgeuse",205.0,57.2),
            new StarObservation(38,-122,"Rigel",210.5,38.8),
            new StarObservation(38,-122,"Aldebaran",242.9,53.9),
            new StarObservation(38,-122,"Sirius",181.1,35.2),
            new StarObservation(38,-122,"Procyon",157.5,55.2),
            new StarObservation(38,-122,"Polaris",359.3,38.2),
            new StarObservation(38,-122,"Dubhe",36.9,44.8),

            new StarObservation(38,-113,"Capella",299.1,65.1),
            new StarObservation(38,-113,"Betelgeuse",219.1,53.3),
            new StarObservation(38,-113,"Rigel",220.3,34.6),
            new StarObservation(38,-113,"Aldebaran",251.6,47.2),
            new StarObservation(38,-113,"Sirius",191.5,34.4),
            new StarObservation(38,-113,"Procyon",173.3,57),
            new StarObservation(38,-113,"Polaris",359.2,38.1),
            new StarObservation(38,-113,"Dubhe",36.4,49.1),

            new StarObservation(38,-104,"Capella",298.2,58.9),
            new StarObservation(38,-104,"Betelgeuse",230.9,48.3),
            new StarObservation(38,-104,"Rigel",229.1,29.7),
            new StarObservation(38,-104,"Aldebaran",258.9,40.4),
            new StarObservation(38,-104,"Sirius",201.5,32.5),
            new StarObservation(38,-104,"Procyon",189.8,56.9),
            new StarObservation(38,-104,"Polaris",359.1,38.1),
            new StarObservation(38,-104,"Dubhe",34.7,53.2),

            new StarObservation(38,-95,"Capella",298.7,52.5),
            new StarObservation(38,-95,"Betelgeuse",240.5,42.3),
            new StarObservation(38,-95,"Rigel",236.8,24.0),
            new StarObservation(38,-95,"Aldebaran",265.2,33.4),
            new StarObservation(38,-95,"Sirius",210.9,29.3),
            new StarObservation(38,-95,"Procyon",205.3,54.6),
            new StarObservation(38,-95,"Polaris",359.1,37.9),
            new StarObservation(38,-95,"Dubhe",31.7,57.1),

            new StarObservation(38,-86,"Capella",300.2,46.3),
            new StarObservation(38,-86,"Betelgeuse",248.6,35.9),
            new StarObservation(38,-86,"Rigel",243.7,17.8),
            new StarObservation(38,-86,"Aldebaran",271.0,26.2),
            new StarObservation(38,-86,"Sirius",219.5,25.2),
            new StarObservation(38,-86,"Procyon",218.8,50.9),
            new StarObservation(38,-86,"Polaris",359.2,37.8),
            new StarObservation(38,-86,"Dubhe",26.9,60.6),
        };
    }
}

// EOF
