// MoveDirection.java
// See copyright.txt for license and terms of use.

package earthshape;

import util.Vector3f;

/** Enumeration of the six directions the user might want to be
  * moving the camera in.  This is used by the user interface for
  * camera translational movement via the keyboard. */
public enum MoveDirection {
    MD_LEFT(new Vector3f(-1, 0, 0)),
    MD_RIGHT(new Vector3f(+1, 0, 0)),
    MD_UP(new Vector3f(0, +1, 0)),
    MD_DOWN(new Vector3f(0, -1, 0)),
    MD_FORWARD(new Vector3f(0, 0, -1)),
    MD_BACKWARD(new Vector3f(0, 0, +1));

    /** Direction of movement in space, relative to a camera that
      * is looking down the -Z axis with +X to the right and +Y up. */
    public Vector3f direction;

    MoveDirection(Vector3f d)
    {
        this.direction = d;
    }
}

// EOF
