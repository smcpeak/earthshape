// MySwingWorker.java
// See copyright.txt for license and terms of use.

package util.swing;

import javax.swing.SwingWorker;

/** This is just like SwingWorker, except it knows how to fire a
  * String-valued "status" property, which is useful with
  * ProgressDialog. */
public abstract class MySwingWorker<V,T> extends SwingWorker<V,T> {
    /** The last fired "status" property value. */
    protected String currentStatus = null;

    /** Update the status by firing a property change event. */
    public void setStatus(String newStatus)
    {
        this.firePropertyChange("status", currentStatus, newStatus);
        currentStatus = newStatus;
    }
}

// EOF
