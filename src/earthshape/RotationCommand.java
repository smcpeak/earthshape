// RotationCommand.java
// See copyright.txt for license and terms of use.

package earthshape;

import util.Vector3f;

/** Encapsulate a keybinding and an axis about which that key
  * causes a rotation of the active square. */
public enum RotationCommand {
    RC_ROLL_RIGHT("Roll active square right", 'o', new Vector3f(0, 0, -1)),
    RC_ROLL_LEFT("Roll active square left", 'u', new Vector3f(0, 0, +1)),
    RC_PITCH_FORWARD("Pitch active square forward", 'i', new Vector3f(-1, 0, 0)),
    RC_PITCH_BACKWARD("Pitch active square backward", 'k', new Vector3f(+1, 0, 0)),
    RC_YAW_RIGHT("Yaw active square right", 'l', new Vector3f(0, -1, 0)),
    RC_YAW_LEFT("Yaw active square left", 'j', new Vector3f(0, +1, 0));

    /** Description of the effect of the command. */
    public final String description;

    /** The letter of the key to press to cause it.*/
    public final char key;

    /** The axis around which rotation occurs, where -Z is forward,
      * +Y is up, and +X is right.  This is a unit vector expressing
      * rotation with the right hand rule. */
    public final Vector3f axis;

    RotationCommand(String description_, char key_, Vector3f axis_)
    {
        this.description = description_;
        this.key = key_;
        this.axis = axis_;
    }
}

// EOF
