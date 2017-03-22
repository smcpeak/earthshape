// ModalDialog.java
// See copyright.txt for license and terms of use.

// This is a cut-down version of ModalDialog.java from my 'ded' project.

package util.swing;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

/** Base class for modal dialogs. */
public class ModalDialog extends JDialog {
    /** AWT boilerplate generated ID. */
    private static final long serialVersionUID = 8309396165967113773L;

    // -------------- public data --------------
    /** Initially false, this is set to true if the dialog is closed
      * by pressing the OK button. */
    public boolean okWasPressed;

    // ---------------- methods ----------------
    /** Create a new dialog.  'documentParent' is a Component that
      * originated the request; the top-level window that contains
      * it will be blocked from interaction until this dialog closes. */
    public ModalDialog(JFrame parent, String title)
    {
        super(parent, title, true /*modal*/);

        this.okWasPressed = false;

        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        installEscapeCloseOperation(this);
    }

    /** Run the dialog, blocking until it is dismissed.  Returns true
      * if the user pressed OK, false if Cancel. */
    public boolean exec()
    {
        // This blocks until the dialog is dismissed.
        this.setVisible(true);

        // This does not happen automatically, or at least not right
        // away, based on watching Window.getOwnedWindows().  Anyway,
        // I am done with the dialog, and this does not appear to cause
        // problems, so it seems like a good thing to do.
        this.dispose();

        return this.okWasPressed;
    }

    /** Make a new JButton and attach a listener. */
    public JButton makeButton(String label, ActionListener listener)
    {
        JButton b = new JButton(label);
        b.addActionListener(listener);
        return b;
    }

    /** Create a Cancel button and set its action to close the dialog. */
    public JButton makeCancelButton()
    {
        return this.makeButton("Cancel", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ModalDialog.this.dispose();
            }
        });
    }

    /** Create an OK button and set its action to close the dialog,
      * indicating that changes should be preserved. */
    public JButton makeOKButton()
    {
        return this.makeOKButtonCalled("OK");
    }

    /** Create an OK button and specify its label. */
    public JButton makeOKButtonCalled(String label)
    {
        JButton okButton = new JButton(label);
        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ModalDialog.this.okPressed();
            }
        });
        this.getRootPane().setDefaultButton(okButton);
        return okButton;
    }

    /** React to the OK button being pressed.  The base class
      * implementation remembers that it was pressed and closes the dialog.
      * Derived classes should copy data from controls into the object that
      * the dialog is meant to edit, then call super.okPressed().
      *
      * If some inputs need to be validated, do so before calling
      * super.okPressed(); and if validation fails, do not call it at
      * all, so the dialog will remain open. */
    public void okPressed()
    {
        this.okWasPressed = true;
        this.dispose();
    }

    /** Add a labeled text field to 'vb'.  Also add a strut at the end. */
    public JTextField makeTextField(VBox vb, String labelText, String initValue)
    {
        HBox hb = new HBox();

        JLabel label = new JLabel(labelText);
        hb.add(label);

        hb.strut();

        JTextField textField = new JTextField(initValue);
        hb.add(textField);

        // This won't do anything since I haven't set the mnemonic...
        label.setLabelFor(textField);

        vb.add(hb);
        vb.strut();

        return textField;
    }

    /** Create a checkbox control and add it to 'vb'. */
    public JCheckBox makeCheckBox(VBox vb, String label, boolean initValue)
    {
        HBox hb = new HBox();
        JCheckBox cb = new JCheckBox(label, initValue);
        hb.add(cb);
        hb.glue();
        vb.add(hb);
        return cb;
    }

    /** Create a label control on its own line, add it to 'vb'. */
    public JLabel makeLabel(VBox vb, String text)
    {
        HBox hb = new HBox();
        JLabel label = new JLabel(text);
        hb.add(label);
        hb.glue();
        vb.add(hb);
        vb.strut();
        return label;
    }

    /** Arrange to close a dialog when Escape is pressed.
      *
      * Based on code from:
      *  http://stackoverflow.com/questions/642925/swing-how-do-i-close-a-dialog-when-the-esc-key-is-pressed
      */
    public static void installEscapeCloseOperation(final JDialog dialog)
    {
        JRootPane rootPane = dialog.getRootPane();
        rootPane.registerKeyboardAction(
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    dialog.dispose();
                }
            },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Given a vbox containing the controls, finish building the
      * dialog by wrapping it in an hbox with horizontal margins,
      * then pack and center the dialog. */
    public void finishDialogWithVBox(VBox vb)
    {
        HBox outer = new HBox();
        outer.strut();
        outer.add(vb);
        outer.strut();

        this.getContentPane().add(outer);

        this.finishBuildingDialog();
    }

    /** Pack the controls and center the dialog w.r.t. parent. */
    public void finishBuildingDialog()
    {
        this.pack();
        this.setLocationRelativeTo(this.getParent());
    }

    /** Show an error message. */
    public static void errorBox(Component parent, String message)
    {
        JOptionPane pane = new JOptionPane();
        pane.setMessage(message);
        pane.setMessageType(JOptionPane.ERROR_MESSAGE);

        JDialog dialog = pane.createDialog(parent, "Error");
        dialog.setVisible(true);
    }
}

// EOF
