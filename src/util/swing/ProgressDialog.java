// ProgressDialog.java
// See copyright.txt for license and terms of use.

package util.swing;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

import util.FloatUtil;

import static util.swing.SwingUtil.log;

/** Modal dialog to show progress of some activity. */
public class ProgressDialog<T,V> extends ModalDialog {
    /** AWT boilerplate. */
    private static final long serialVersionUID = 2203629471285652409L;

    /** The object that will perform the work whose progress we monitor. */
    private SwingWorker<T,V> swingWorker;

    /** Text status label above the bar. */
    private JLabel statusLabel;

    /** Bar showing progress. */
    private JProgressBar progressBar;

    public ProgressDialog(JFrame parent, String title, SwingWorker<T,V> swingWorker_)
    {
        super(parent, title);
        this.swingWorker = swingWorker_;

        // Monitor for completion of the task.
        this.swingWorker.addPropertyChangeListener(
            new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent ev) {
                    if ("state".equals(ev.getPropertyName())) {
                        if (SwingWorker.StateValue.DONE == ev.getNewValue()) {
                            ProgressDialog.this.taskCompleted();
                        }
                    }
                    else if ("progress".equals(ev.getPropertyName())) {
                        Integer i = (Integer)ev.getNewValue();
                        if (i % 10 == 0) {
                            log("progress dialog received progress event: "+i);
                        }
                        ProgressDialog.this.setProgress(i / 100.0f);
                    }
                    else if ("status".equals(ev.getPropertyName())) {
                        String s = (String)ev.getNewValue();
                        ProgressDialog.this.setStatus(s);
                    }
                }
            });

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

            vb.add(this.progressBar = new JProgressBar(0, 1000));

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

    /** Change the status label text. */
    public void setStatus(String text)
    {
        this.statusLabel.setText(text);
    }

    /** Change the amount of progress shown. */
    public void setProgress(float fraction)
    {
        this.progressBar.setValue((int)(FloatUtil.clamp(fraction, 0, 1) * 1000));
    }

    /** Show the progress dialog and start the task, blocking until
      * the task completes or the user cancels it.  Returns true
      * if the task completed normally, false if it was canceled. */
    public boolean exec()
    {
        // Start running the task.
        this.swingWorker.execute();

        log("okWasPressed at start of exec: "+this.okWasPressed);
        log("ProgressDialog: calling setVisible(true)");

        // This blocks until the dialog is closed.
        this.setVisible(true);

        log("ProgressDialog: setVisible(true) returned; okWasPressed="+this.okWasPressed);

        // Clean up.
        this.dispose();
        log("okWasPressed after dispose: "+this.okWasPressed);

        if (!this.okWasPressed) {
            // Notify the thread to stop running.  The thread must
            // poll for this information.
            this.swingWorker.cancel(false /*mayInterrupt*/);
        }

        log("okWasPressed at end of exec: "+this.okWasPressed);
        return this.okWasPressed;
    }

    /** Called when the task completes normally. */
    private void taskCompleted()
    {
        log("ProgressDialog: taskCompleted called; isCancelled is "+
            this.swingWorker.isCancelled());

        // We abuse this flag to mean "completed normally".
        if (!this.swingWorker.isCancelled()) {
            this.okWasPressed = true;
        }

        // Close the dialog.  This will end the event loop, allowing
        // 'exec' to resume after its own call to 'setVisible'.
        this.setVisible(false);
    }
}

// EOF
