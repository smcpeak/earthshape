// SwingUtil.java
// See copyright.txt for license and terms of use.

package util.swing;

/** Collection of Swing-related utility routines. */
public class SwingUtil {
    /** The thread that last issued a 'log' command.  This is used to
      * only log thread names when there is interleaving. */
    private static Thread lastLoggedThread = null;

    /** Print a message to the console with a timestamp. */
    public static synchronized void log(String msg)
    {
        Thread t = Thread.currentThread();
        if (t != SwingUtil.lastLoggedThread) {
            System.out.println(""+System.currentTimeMillis()+
                               " ["+t.getName()+"]"+
                               ": "+msg);
            SwingUtil.lastLoggedThread = t;
        }
        else {
            System.out.println(""+System.currentTimeMillis()+
                               ": "+msg);
        }
    }
}

// EOF
