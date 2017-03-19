// ProgressDialog.java
// See copyright.txt for license and terms of use.

package util.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

/** Modal dialog to show progress of some long-running task
  * while pumping the event queue to keep the UI responsive.
  *
  * Conceptually this is similar to javax.swing.ProgressMonitor.  The main
  * advantage of this class is it is modal, whereas ProgressMonitor
  * is not; modality ensures the user can't do other things while
  * the task is running.
  *
  * Another apparent difference is that ProgressMonitor responds
  * to the window close button by simply closing itself without
  * delivering a cancelation message.  (I have found a StackOverflow
  * discussion claiming the opposite, but it contradicts my
  * experiments.  It might depend on the platform; I am running
  * on Windows.)
  *
  * Finally, this class takes care of some of the state management
  * that is the same for all of these kinds of tasks. */
public class ProgressDialog<T,V> extends ModalDialog
    implements PropertyChangeListener, WindowListener
{
    // ---- Constants ----
    /** AWT boilerplate. */
    private static final long serialVersionUID = 2203629471285652409L;

    // ---- Instance data ----
    /** The object that will perform the work whose progress we monitor. */
    private MySwingWorker<T,V> swingWorker;

    /** Text status label above the bar. */
    private JLabel statusLabel;

    /** Bar showing progress. */
    private JProgressBar progressBar;

    /** Button the user can press to Cancel the task. */
    private JButton cancelButton;

    // ---- Methods ----
    public ProgressDialog(JFrame parent, String title, MySwingWorker<T,V> swingWorker_)
    {
        super(parent, title);
        this.swingWorker = swingWorker_;

        this.swingWorker.addPropertyChangeListener(this);

        // Listen for window close events myself.
        this.addWindowListener(this);
        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        HBox outerHB = new HBox();
        outerHB.strut();

        {
            VBox vb = new VBox();
            vb.add(Box.createHorizontalStrut(400));
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

                hb.add(this.cancelButton = new JButton("Cancel"));
                this.cancelButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent ev) {
                        ProgressDialog.this.cancelRequested();
                    }
                });

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

    /** Debugging message.  This should not do anything unless I am
      * actively testing or debugging this class. */
    public void log(String msg)
    {
        //SwingUtil.log("ProgressDialog: "+msg);
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
        else if (MySwingWorker.STATUS_PROPERTY.equals(ev.getPropertyName())) {
            if (newValue instanceof String) {
                ProgressDialog.this.setStatus((String)newValue);
            }
        }
        else if (MySwingWorker.TASK_CODE_RUNNING_PROPERTY.equals(ev.getPropertyName())) {
            if (newValue instanceof Boolean) {
                ProgressDialog.this.taskCodeRunningChanged((Boolean)newValue);
            }
        }
    }

    /** Change the status label text.  This can also be done by having
      * the worker fire a "status" property change event. */
    public void setStatus(String text)
    {
        log("setStatus(\""+text+"\")");
        this.statusLabel.setText(text);
    }

    /** Change the amount of progress shown, in range [0,100]. */
    public void setProgress(int progress)
    {
        log("setProgress("+progress+")");
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
        log("calling setVisible(true)");
        this.setVisible(true);
        log("setVisible(true) returned");

        // Clean up.
        this.dispose();

        log("exec returning "+this.okWasPressed);
        return this.okWasPressed;
    }

    /** Called when the task state is "done". */
    private void taskStateIsDone()
    {
        log("taskStateIsDone; worker canceled is "+this.swingWorker.isCancelled());
        log("  worker done is "+this.swingWorker.isDone());
        log("  task code running is "+this.swingWorker.isTaskCodeRunning());

        // This fires as soon as a cancellation is requested,
        // without waiting for the code to stop running.  That
        // makes it risky to use when there is shared state
        // between task and client code.  So, ignore this
        // event, and instead wait for 'taskCodeHasStopped'.
    }

    /** Called when code in the task changes whether it is running. */
    private void taskCodeRunningChanged(boolean running)
    {
        log("taskCodeRunningChanged: running="+running);

        if (!running) {
            log("  worker canceled is "+this.swingWorker.isCancelled());
            log("  worker done is "+this.swingWorker.isDone());

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

    /** Respond to a user's request to cancel the task. */
    public void cancelRequested()
    {
        log("cancelRequested; task running is "+this.swingWorker.isTaskCodeRunning());
        if (this.swingWorker.isTaskCodeRunning()) {
            // Show the user we are in the process of canceling.
            this.setStatus("Canceling...");

            // Disable the Cancel button to reinforce that.
            this.cancelButton.setEnabled(false);

            // Tell the task code to stop.  But we will not close the
            // dialog until it actually does.
            this.swingWorker.cancel(false /*mayInterrupt*/);
        }
        else {
            // The task is not running, so it is safe to close
            // the dialog.
            this.setVisible(false);
        }
    }

    @Override
    public void windowClosed(WindowEvent ev)
    {
        log("windowClosed");

        // Treat a click on the "X" the same as pressing the
        // Cancel button.
        this.cancelRequested();
    }

    // WindowListener events I do not care about.
    @Override public void windowActivated(WindowEvent ev) {}
    @Override public void windowClosing(WindowEvent ev) {}
    @Override public void windowDeactivated(WindowEvent ev) {}
    @Override public void windowDeiconified(WindowEvent ev) {}
    @Override public void windowIconified(WindowEvent ev) {}
    @Override public void windowOpened(WindowEvent ev) {}
}

// EOF
