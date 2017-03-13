// SurfaceSquare.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.util.HashMap;

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

    /** The square, if any, that this one was constructed from
      * by moving and rotating. */
    public SurfaceSquare baseSquare;

    /** If 'baseSquare' is not null, then this is not null, and
      * indicates the midpoint between the two that was followed
      * to arrive at this square's center.  It is not necessarily
      * or even usually the point midway between the centers
      * because it more closely follows the intervening curve. */
    public Vector3f baseMidpoint;

    /** Amount by which this square has been rotated away from
      * the nominal orientation where North is -Z and East is +X.
      * I should not have to store this; this is a band-aid until
      * I fix the underlying problem properly.
      *
      * The vector gives an axis, and its magnitude is the rotation
      * angle in degrees. */
    public Vector3f rotationFromNominal;

    /** Another band-aid: this is the rotation that was applied to
      * the base square's orientation to get here.  It is the same
      * as 'rotationFromNominal' if 'base' is null. */
    public Vector3f rotationFromBase;

    /** Star observations taken from this point at some
      * point in time. */
    public HashMap<String, StarObservation> starObs = new HashMap<String, StarObservation>();

    /** If true, draw a special marker to indicate the square is
      * "active", meaning it is the square upon which we will
      * build more squares. */
    public boolean showAsActive = false;

    /** If true, draw rays from this surface to its star observations. */
    public boolean drawStarRays = false;

    public SurfaceSquare(
        Vector3f center_,
        Vector3f north_,
        Vector3f up_,
        float sizeKm_,
        float latitude_,
        float longitude_,
        SurfaceSquare baseSquare_,
        Vector3f baseMidpoint_,
        Vector3f rotationFromBase_)
    {
        this.center = center_;
        this.north = north_;
        this.up = up_;
        this.sizeKm = sizeKm_;
        this.latitude = latitude_;
        this.longitude = longitude_;
        this.baseSquare = baseSquare_;
        this.baseMidpoint = baseMidpoint_;
        this.rotationFromBase = rotationFromBase_;

        if (this.baseSquare == null) {
            this.rotationFromNominal = rotationFromBase_;
        }
        else {
            this.rotationFromNominal =
                Vector3f.composeRotations(this.baseSquare.rotationFromNominal, rotationFromBase_);
        }
    }

    /** Add 'so' to this star's set of observations. */
    public void addObservation(StarObservation so)
    {
        this.starObs.put(so.name, so);
    }

    /** If this star has an observation for 'starName', return it. */
    public StarObservation findObservation(String starName)
    {
        return this.starObs.get(starName);
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
