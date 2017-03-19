// ProgressDialog.java
// See copyright.txt for license and terms of use.

package util.swing;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

/** Modal dialog to show progress of some long-running task
  * while pumping the event queue to keep the UI responsive.
  *
  * Conceptually this is similar to JProgressDialog.  The main
  * advantage of this class is it is modal, whereas JProgressDialog
  * is not; modality ensures the user can't do other things while
  * the task is running.
  *
  * Another apparent difference is that JProgressDialog responds
  * to the window close button by simply closing itself without
  * delivering a cancelation message.  (I have found a StackOverflow
  * discussion claiming the opposite, but it contradicts my
  * experiments.  It might depend on the platform; I am running
  * on Windows.)
  *
  * Finally, this class takes care of some of the state management
  * that is the same for all of these kinds of tasks. */
public class ProgressDialog<T,V> extends ModalDialog implements PropertyChangeListener {
    // ---- Constants ----
    /** AWT boilerplate. */
    private static final long serialVersionUID = 2203629471285652409L;

    // ---- Instance data ----
    /** The object that will perform the work whose progress we monitor. */
    private SwingWorker<T,V> swingWorker;

    /** Text status label above the bar. */
    private JLabel statusLabel;

    /** Bar showing progress. */
    private JProgressBar progressBar;

    // ---- Methods ----
    public ProgressDialog(JFrame parent, String title, SwingWorker<T,V> swingWorker_)
    {
        super(parent, title);
        this.swingWorker = swingWorker_;

        this.swingWorker.addPropertyChangeListener(this);

        HBox outerHB = new HBox();
        outerHB.strut();

        {
            VBox vb = new VBox();
            vb.strut();

            {
                HBox hb = new HBox();
                hb.add(this.statusLabel = new JLabel("Working..."));
                hb.glue();
                vb.add(hb);
            }

            vb.strut();

            vb.add(this.progressBar = new JProgressBar(0, 100));

            vb.strut();

            {
                HBox hb = new HBox();
                hb.glue();
                hb.add(this.makeCancelButton());
                hb.glue();
                vb.add(hb);
            }

            vb.strut();
            outerHB.add(vb);
        }

        outerHB.strut();
        this.add(outerHB);

        this.getContentPane().add(outerHB);

        this.finishBuildingDialog();
    }

    /** Respond to property changes from the worker task. */
    @Override
    public void propertyChange(PropertyChangeEvent ev)
    {
        Object newValue = ev.getNewValue();

        if ("state".equals(ev.getPropertyName())) {
            if (SwingWorker.StateValue.DONE == newValue) {
                ProgressDialog.this.taskStateIsDone();
            }
        }
        else if ("progress".equals(ev.getPropertyName())) {
            if (newValue instanceof Integer) {
                ProgressDialog.this.setProgress((Integer)newValue);
            }
        }
        else if ("status".equals(ev.getPropertyName())) {
            if (newValue instanceof String) {
                ProgressDialog.this.setStatus((String)newValue);
            }
        }
    }

    /** Change the status label text.  This can also be done by having
      * the worker fire a "status" property change event. */
    public void setStatus(String text)
    {
        this.statusLabel.setText(text);
    }

    /** Change the amount of progress shown, in range [0,100]. */
    public void setProgress(int progress)
    {
        this.progressBar.setValue(progress);
    }

    /** Show the progress dialog and start the task, blocking until
      * the task completes or the user cancels it.  Returns true
      * if the task completed normally, false if it was canceled. */
    @Override
    public boolean exec()
    {
        // Start running the task.
        this.swingWorker.execute();

        // This blocks until the dialog is closed, which itself is
        // triggered when the task completes or is canceled.
        this.setVisible(true);

        // Clean up.
        this.dispose();

        if (!this.okWasPressed) {
            // Notify the thread to stop running.  The thread must
            // poll for this information if it wants to not waste
            // CPU cycles, since asynchronous interruption is
            // dangerous.
            this.swingWorker.cancel(false /*mayInterrupt*/);
        }

        return this.okWasPressed;
    }

    /** Called when the task state is "done". */
    private void taskStateIsDone()
    {
        if (!this.swingWorker.isCancelled()) {
            // We abuse this flag to mean "completed normally".
            // But note that normal completion could still mean
            // the worker threw an exception.
            this.okWasPressed = true;
        }

        // Close the dialog.  This will end the event loop, allowing
        // 'exec' to resume after its own call to 'setVisible'.
        this.setVisible(false);
    }
}

// EOF
