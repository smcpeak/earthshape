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

    /** Allow other code to set the progress value in [0,100].  This is
      * useful because I may put the core logic into a method that can
      * potentially run outside a task.
      *
      * I cannot call this method 'setProgress' because the one in
      * SwingWorker is final! */
    public void setProgressPercent(int progressPct)
    {
        this.setProgress(progressPct);
    }

    /** Set progress in [0,1]. */
    public void setProgressFraction(float progressFraction)
    {
        this.setProgress((int)(progressFraction * 100.0f));
    }
}

// EOF
