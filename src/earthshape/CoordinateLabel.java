// CoordinateLabel.java
// See copyright.txt for license and terms of use.

package earthshape;

import util.Vector4f;

/** A 3D coordinate and a text label, meant to be used by
  * EarthMapCanvas to represent labels to draw on the GL canvas. */
public class CoordinateLabel {
    /** Location in homogeneous world coordinates that we want to label. */
    public Vector4f coordinate;

    /** The label string. */
    public String label;

    public CoordinateLabel(Vector4f coordinate_, String label_)
    {
        this.coordinate = coordinate_;
        this.label = label_;
    }
}

// EOF
