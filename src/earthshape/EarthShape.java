// EarthShape.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.TextureIO;

/** This application demonstrates a procedure for inferring the shape
  * of a surface (such as the Earth) based on the observed locations of
  * stars from various locations at a fixed point in time. */
public class EarthShape extends Frame implements GLEventListener {
    // AWT boilerplate.
    private static final long serialVersionUID = 3903955302894393226L;

    /** Compass rose texture.  This is only valid between 'init' and
      * 'dispose'. */
    private Texture compassTexture;

    public EarthShape()
    {
        super("Earth Shape");

        this.setLayout(new BorderLayout());

        // Exit the app when window close button is pressed (AWT boilerplate).
        this.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                EarthShape.this.dispose();
            }
        });

        this.setSize(800, 800);
        this.setLocationByPlatform(true);

        this.setupJOGL();
    }

    /** Initialize the JOGL library, then create a GL canvas and
      * associate it with this window. */
    private void setupJOGL()
    {
        log("creating GLCapabilities");

        // This call takes about one second to complete, which is
        // pretty slow...
        GLCapabilities caps = new GLCapabilities(null /*profile*/);

        log("caps: "+caps);
        caps.setDoubleBuffered(true);
        caps.setHardwareAccelerated(true);

        // Make the GL canvas and specify that 'this' object will
        // handle its draw-related events.
        GLCanvas canvas = new GLCanvas(caps);
        canvas.addGLEventListener(this);

       // Associate the canvas with 'this' window.
       this.add(canvas, BorderLayout.CENTER);
    }

    /** Print a message to the console with a timestamp. */
    private static void log(String msg)
    {
        System.out.println(""+System.currentTimeMillis()+": "+msg);
    }

    /** Initialize the GL context. */
    @Override
    public void init(GLAutoDrawable drawable) {
        log("init");

        // The tutorial I am working from uses 'GL' rather than 'GL2',
        // but I find that using 'GL' leads to syntax errors since
        // some of the methods I'm supposed to call are only defined
        // for 'GL2'.  I assume the tutorial is just out of date.
        GL2 gl = (GL2)drawable.getGL();

        // Load textures.  We have to wait until 'init' to do this because
        // it requires a GL context because the textures are loaded into
        // the graphics card's memory in a device- and mode-dependent
        // format.
        log("loading texture");
        try {
            InputStream stream = getClass().getResourceAsStream("textures/compass-rose.png");
            TextureData data = TextureIO.newTextureData(
                gl.getGLProfile(), stream, false /*mipmap*/, TextureIO.PNG);
            this.compassTexture = TextureIO.newTexture(data);
            log("loaded texture; mem="+this.compassTexture.getEstimatedMemorySize()+
                ", coords="+this.compassTexture.getImageTexCoords());
        }
        catch (IOException exc) {
            exc.printStackTrace();
            System.exit(2);
        }

        // Use a light blue background, full opacity.
        gl.glClearColor(0.8f, 0.9f, 1.0f, 1f);

        // I'm not sure about these, but they are familiar from when
        // I used OpenGL long ago.
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glOrtho(0, 1, 0, 1, -1, 1);
    }

    /** Release allocated resources associated with the GL context. */
    @Override
    public void dispose(GLAutoDrawable drawable) {
        log("dispose");
        GL2 gl = (GL2)drawable.getGL();
        this.compassTexture.destroy(gl);
        this.compassTexture = null;
    }

    /** Draw one frame to the screen. */
    @Override
    public void display(GLAutoDrawable drawable) {
        log("display");
        GL2 gl = (GL2)drawable.getGL();

        gl.glClear(GL.GL_COLOR_BUFFER_BIT);

        // Draw a colored triangle.
        {
            gl.glBegin(GL.GL_TRIANGLES);

            // Red in lower-left.
            gl.glColor3f(1, 0, 0);
            gl.glVertex3f(0.25f, 0.25f, 0);

            // Green in lower-right.
            gl.glColor3f(0, 1, 0);
            gl.glVertex3f(0.75f, 0.25f, 0);

            // Blue in upper-left.
            gl.glColor3f(0, 0, 1);
            gl.glVertex3f(0.25f, 0.75f, 0);

            gl.glEnd();
        }

        // Draw a textured triangle.
        {
            this.compassTexture.enable(gl);
            this.compassTexture.bind(gl);

            // Reset base color to white (otherwise, GL remembers the
            // blue color used for the final vertex of the colored
            // triangle, and the new triangle will be blue too!).
            gl.glColor3f(1,1,1);

            gl.glBegin(GL.GL_TRIANGLES);

            // Upper-right.
            gl.glTexCoord2f(1.0f, 1.0f);
            gl.glVertex3f(0.8f, 0.8f, 0);

            // Upper-left.
            gl.glTexCoord2f(0.0f, 1.0f);
            gl.glVertex3f(0.3f, 0.8f, 0);

            // Lower-right.
            gl.glTexCoord2f(1.0f, 0.0f);
            gl.glVertex3f(0.8f, 0.3f, 0);

            gl.glEnd();
        }

        gl.glFlush();
    }

    /** Called when the window is resized.  The superclass does
     * basic resize handling, namely adjusting the viewport to
     * fill the canvas, so we do not need to do anything more. */
    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        log("reshape");
    }

    public static void main(String[] args)
    {
        (new EarthShape()).setVisible(true);
    }
}
