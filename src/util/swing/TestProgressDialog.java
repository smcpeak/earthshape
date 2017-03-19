// TestProgressDialog.java
// See copyright.txt for license and terms of use.

package util.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.ExecutionException;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import static util.swing.SwingUtil.log;

/** Little program to test ProgressDialog. */
public class TestProgressDialog extends MyJFrame {
    /** AWT boilerplate. */
    private static final long serialVersionUID = 1770692424967304611L;

    /** Text field where user can specify how many milliseconds each
      * worker tick should require. */
    private JTextField msPerTickTextField;

    /** Let user request that worker throw an exception. */
    private JCheckBox throwExceptionCB;

    public TestProgressDialog()
    {
        super("Test Progress Dialog");

        log("in TestProgressDialog ctor");

        HBox outerHB = new HBox();
        outerHB.strut();

        {
            VBox vb = new VBox();
            vb.strut();

            vb.add(Box.createHorizontalStrut(400));

            {
                HBox hb = new HBox();

                hb.add(new JLabel("Milliseconds per tick: "));
                hb.strut();
                hb.add(this.msPerTickTextField = new JTextField("10"));
                hb.glue();

                vb.add(hb);
            }

            vb.strut();

            {
                HBox hb = new HBox();

                hb.add(this.throwExceptionCB =
                    new JCheckBox("Throw an exception inside worker", false));
                hb.glue();

                vb.add(hb);
            }

            vb.strut();

            {
                HBox hb = new HBox();

                JButton button = new JButton("Start");
                hb.add(button);
                button.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent ev) {
                        TestProgressDialog.this.startTask();
                    }
                });

                hb.glue();
                vb.add(hb);
            }

            vb.strut();

            {
                HBox hb = new HBox();

                JButton button = new JButton("Quit");
                hb.add(button);
                button.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent ev) {
                        log("closing due to Quit button press");
                        TestProgressDialog.this.dispose();
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

        this.pack();
        this.setLocationByPlatform(true);
    }

    private static class TestTask extends SwingWorker<Integer, Void> {
        private int msPerTick;
        private boolean throwException;

        public TestTask(int msPerTick_, boolean throwException_)
        {
            this.msPerTick = msPerTick_;
            this.throwException = throwException_;
        }

        protected Integer doInBackground() throws Exception
        {
            String oldStatus = null;

            log("TestTask: starting");
            this.setProgress(0);
            for (int i=0; i < 100 && !this.isCancelled(); i++) {
                Thread.sleep(this.msPerTick);

                if (i == 50 && this.throwException) {
                    log("TestTask: throwing exception");
                    throw new RuntimeException("Exception thrown from worker");
                }

                if (i % 10 == 0) {
                    log("TestTask: progress is "+i);

                    if (i != 0) {
                        String newStatus = "Passed "+i+"%";
                        this.firePropertyChange("status", oldStatus, newStatus);
                        oldStatus = newStatus;
                    }
                }
                this.setProgress(i);
            }

            if (this.isCancelled()) {
                log("TestTask: canceled externally");
                return null;
            }
            else {
                log("TestTask: finished with complete task");
                return 5;
            }
        }
    }

    private void startTask()
    {
        int msPerTick;
        try {
            String s = this.msPerTickTextField.getText();
            msPerTick = Integer.valueOf(s);
        }
        catch (NumberFormatException e) {
            ModalDialog.errorBox(this, "Invalid integer: "+e.getMessage());
            return;
        }

        boolean throwException = this.throwExceptionCB.isSelected();

        log("startTask: starting new task");

        TestTask task = new TestTask(msPerTick, throwException);
        ProgressDialog<Integer, Void> pd =
            new ProgressDialog<Integer, Void>(this, "Test Progress", task);

        boolean res = pd.exec();
        if (res) {
            log("startTask: task finished normally");

            try {
                Integer i = task.get();
                log("startTask: computation returned: "+i);
            }
            catch (ExecutionException e) {
                log("startTask: computation threw exception: "+e.getMessage());
            }
            catch (InterruptedException e) {
                log("startTask: computation was interrupted: "+e.getMessage());
            }
        }
        else {
            log("startTask: task was canceled");
        }
    }

    public static void main(String[] args)
    {
        log("start of main");
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                log("start of main.run");
                (new TestProgressDialog()).setVisible(true);
            }
        });
    }
}
