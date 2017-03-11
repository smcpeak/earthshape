// SurfaceSquare.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.util.ArrayList;

import util.Vector3f;

/** Represent a single square on a surface in space. */
public class SurfaceSquare {
    /** Location of the center point of the square. */
    public Vector3f center;

    /** Unit vector indicating the direction of local North. */
    public Vector3f north;

    /** Unit vector indicating direction of "up", that is, the
      * opposite direction from the pull of gravity. */
    public Vector3f up;

    /** The size of a side of the square in kilometers. */
    public float sizeKm;

    /** Latitude of the center point in degrees North of the
      * equator.  Note: The latitude is simply used as an
      * arbitrary label.  The surface shape is not dependent
      * on this labeling system. */
    public float latitude;

    /** Longitude of center point in degrees East of the
      * prime meridian.  Again, this is just an arbitrary
      * coordinate system with which to tie the location to
      * the real world. */
    public float longitude;

    /** Amount by which this square has been rotated away from
      * the nominal orientation where North is -Z and East is +X.
      * I should not have to store this; this is a band-aid until
      * I fix the underlying problem properly.
      *
      * The vector gives an axis, and its magnitude is the rotation
      * angle in degrees. */
    public Vector3f rotationFromNominal;

    /** Star observations taken from this point at some
      * point in time. */
    public ArrayList<StarObservation> starObs = new ArrayList<StarObservation>();

    /** If true, draw a special marker to indicate the square is
      * "active", meaning it is the square upon which we will
      * build more squares. */
    public boolean showAsActive = false;

    /** If true, draw rays from this surface to its star observations. */
    public boolean drawStarRays = false;

    public SurfaceSquare(
        Vector3f center,
        Vector3f north,
        Vector3f up,
        float sizeKm,
        float latitude,
        float longitude,
        Vector3f rotationFromNominal_)
    {
        this.center = center;
        this.north = north;
        this.up = up;
        this.sizeKm = sizeKm;
        this.latitude = latitude;
        this.longitude = longitude;
        this.rotationFromNominal = rotationFromNominal_;
    }

    public String toString()
    {
        Vector3f east = this.north.cross(this.up);
        Vector3f celestialNorth = this.north.rotate(latitude, east);

        return "Sq(c="+this.center+
            ", n="+this.north+
            ", u="+this.up+
            ", s="+this.sizeKm+
            ", lat="+this.latitude+
            ", lng="+this.longitude+
            ", rfn="+this.rotationFromNominal+
            ", cn="+celestialNorth+
            ", ac="+this.showAsActive+
            ", ds="+this.drawStarRays+
            ")";
    }
}

// EOF
