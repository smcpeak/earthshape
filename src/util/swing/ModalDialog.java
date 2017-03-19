// ModalDialog.java
// See copyright.txt for license and terms of use.

// This is a cut-down version of ModalDialog.java from my 'ded' project.

package util.swing;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
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

    /** Create a Cancel button and set its action to close the dialog. */
    public JButton makeCancelButton()
    {
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ModalDialog.this.dispose();
            }
        });
        return cancelButton;
    }

    /** Create an OK button and set its action to close the dialog,
      * indicating that changes should be preserved. */
    public JButton makeOKButton()
    {
        JButton okButton = new JButton("OK");
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
