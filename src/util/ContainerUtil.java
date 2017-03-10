// ContainerUtil.java
// See copyright.txt for license and terms of use.

package util;

import java.util.HashSet;

/** Miscellaneous container utilities. */
public class ContainerUtil {
    /** Return a new set that contains the intersection of the two
      * given sets. */
    public static <T> HashSet<T> intersect(HashSet<T> a, HashSet<T> b)
    {
        HashSet<T> ret = new HashSet<T>();

        for (T t : a) {
            if (b.contains(t)) {
                ret.add(t);
            }
        }

        return ret;
    }
}

// EOF
