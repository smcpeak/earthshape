// SurfaceSquare.java
// See copyright.txt for license and terms of use.

package earthshape;

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

    public SurfaceSquare(
        Vector3f center,
        Vector3f north,
        Vector3f up,
        float sizeKm,
        float latitude,
        float longitude)
    {
        this.center = center;
        this.north = north;
        this.up = up;
        this.sizeKm = sizeKm;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String toString()
    {
        return "Sq(c="+this.center+
            ", n="+this.north+
            ", u="+this.up+
            ", s="+this.sizeKm+
            ", lat="+this.latitude+
            ", lng="+this.longitude+
            ")";
    }
}

// EOF
