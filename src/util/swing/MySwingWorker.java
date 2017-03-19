// MySwingWorker.java
// See copyright.txt for license and terms of use.

package util.swing;

import javax.swing.SwingWorker;

/** This is similar SwingWorker, but meant for use with ProgressDialog.
  *
  * It adds support for a couple additional properties that the dialog
  * understands.
  *
  * More importantly, it keeps track of whether the
  * worker task code is still running, whereas SwingWorker declares it
  * "done" as soon as a cancellation is requested.  This is important
  * because we need to wait for the code to stop in order to ensure
  * safe access to mutable, shared state. */
public abstract class MySwingWorker<V,T> extends SwingWorker<V,T> {
    // ---- Constants ----
    /** A property change event with this name, and Boolean value, is
      * sent when the code in 'doTask' starts or stops running. */
    public static final String TASK_CODE_RUNNING_PROPERTY = "taskCodeRunning";

    /** When the task wants to change the status label, it sends a
      * property change event with this name and String value. */
    public static final String STATUS_PROPERTY = "status";

    // ---- Instance data ----
    /** The last fired "status" property value.  It is guarded by
      * 'this' lock. */
    protected String currentStatus = null;

    /** True while code in 'doTask' is still running.  It is guarded
      * by 'this' lock. */
    protected boolean taskCodeRunning = false;

    // ---- Methods ----
    /** This method should be overridden to perform the long-running
      * computation.  It must periodically poll 'isCancelled()'.  The
      * progress dialog will *not* go away until this method returns
      * or throws an exception. */
    protected abstract V doTask() throws Exception;

    /** My version of 'doInBackground' that keeps track of whether
      * the task code has truly stopped running. */
    @Override
    protected V doInBackground() throws Exception
    {
        synchronized (this) {
            this.taskCodeRunning = true;
        }
        this.firePropertyChange(TASK_CODE_RUNNING_PROPERTY,
            Boolean.FALSE /*old*/, Boolean.TRUE /*new*/);

        try {
            return doTask();
        }
        finally {
            synchronized (this) {
                this.taskCodeRunning = false;
            }
            this.firePropertyChange(TASK_CODE_RUNNING_PROPERTY,
                Boolean.TRUE /*old*/, Boolean.FALSE /*new*/);
        }
    }

    /** True if code in 'doTask' is running. */
    public synchronized boolean isTaskCodeRunning()
    {
        return this.taskCodeRunning;
    }

    /** Update the status by firing a property change event. */
    public void setStatus(String newStatus)
    {
        String oldStatus;
        synchronized (this) {
            oldStatus = this.currentStatus;
            this.currentStatus = newStatus;
        }

        this.firePropertyChange(STATUS_PROPERTY, oldStatus, newStatus);
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
