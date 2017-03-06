// EarthShape.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.TextureIO;

import util.FloatUtil;
import util.Vector3f;

/** This application demonstrates a procedure for inferring the shape
  * of a surface (such as the Earth) based on the observed locations of
  * stars from various locations at a fixed point in time. */
public class EarthShape
    // For now at least, continue to use plain AWT.
    extends Frame
    // Listen for GL draw events.
    implements GLEventListener,
               // Also handle keyboard input.
               KeyListener,
               // And mouse input for the GL canvas.
               MouseListener, MouseMotionListener
{
    // AWT boilerplate.
    private static final long serialVersionUID = 3903955302894393226L;

    /** Compass rose texture.  This is only valid between 'init' and
      * 'dispose'. */
    private Texture compassTexture;

    /** The main GL canvas. */
    private GLCanvas glCanvas;

    /** Camera position in space. */
    private Vector3f cameraPosition = new Vector3f(1,1,2);

    /** Azimuth angle in which the camera is looking, in degrees
      * to the left of the -Z axis.  It is kept in [0,360), wrapping
      * as it gets to either extreme. */
    private float cameraAzimuthDegrees = 0;

    /** When the mouse moves one pixel horizontally, the camera
      * azimuth turns by this many degrees. */
    private static final float CAMERA_HORIZONTAL_SENSITIVITY = 0.5f;

    /** Camera pitch angle, in degrees.  In [-90,90], where +90 is
      * straight up. */
    private float cameraPitchDegrees = 0;

    /** When the mouse moves one pixel vertically, the camera pitch
      * angle changes by this many degrees. */
    private static final float CAMERA_VERTICAL_SENSITIVITY = 0.5f;

    /** Velocity of camera movement, in world coordinates.  The magnitude
      * is units per second. */
    private Vector3f cameraVelocity = new Vector3f(0,0,0);

    /** As the camera continues moving, it accelerates by this
      * amount with each additional key press event. */
    private static final float CAMERA_ACCELERATION = 4f;

    /** Max amount of friction acceleration to apply to camera
      * velocity, in units per second per second, when at least
      * one movement key is being held.  */
    private static final float MOVING_CAMERA_FRICTION = 2f;

    /** Max amount of friction acceleration to apply to camera
      * velocity, in units per second per second, when no
      * movement keys are being held.  */
    private static final float STATIONARY_CAMERA_FRICTION = 5f;

    /** Widget to show various state variables such as camera position. */
    private Label statusLabel = new Label();

    /** True when we are in "first person shooter" camera control
      * mode, where mouse movement looks around and the mouse is
      * kept inside the canvas window. */
    private boolean fpsCameraMode = false;

    /** "Robot" interface object, used to move the mouse in FPS mode. */
    private Robot robotObject;

    /** Blank cursor, used to hide the mouse cursor. */
    private Cursor blankCursor;

    /** Animator object for the GL canvas.  Valid between init and
      * dispose. */
    private Animator animator;

    /** The thread that last issued a 'log' command.  This is used to
      * only log thread names when there is interleaving. */
    private static Thread lastLoggedThread = null;

    /** The value of the millisecond timer the last time physics was
      * updated. */
    private long lastPhysicsUpdateMillis;

    /** For each possible movement direction, true if that direction's
      * key is held down. */
    private boolean[] moveKeys = new boolean[MoveDirection.values().length];

    public EarthShape()
    {
        super("Earth Shape");

        try {
            this.robotObject = new Robot();
        }
        catch (AWTException e) {
            e.printStackTrace();
            System.exit(2);
        }

        this.setLayout(new BorderLayout());

        // Exit the app when window close button is pressed (AWT boilerplate).
        this.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                EarthShape.this.dispose();
            }
        });

        //this.addKeyListener(this);

        this.setSize(800, 800);
        this.setLocationByPlatform(true);

        // Create a blank cursor so I can hide the cursor later.
        // http://stackoverflow.com/questions/1984071/how-to-hide-cursor-in-a-swing-application
        {
            BufferedImage cursorImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            this.blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
                cursorImg, new Point(0, 0), "blank cursor");
        }

        this.setupJOGL();

        this.lastPhysicsUpdateMillis = System.currentTimeMillis();

        this.glCanvas.addKeyListener(this);
        this.glCanvas.addMouseListener(this);
        this.glCanvas.addMouseMotionListener(this);

        this.statusLabel = new Label();
        this.setStatusLabel();
        this.add(this.statusLabel, BorderLayout.SOUTH);
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
        this.glCanvas = new GLCanvas(caps);
        this.glCanvas.addGLEventListener(this);

        // Associate the canvas with 'this' window.
        this.add(this.glCanvas, BorderLayout.CENTER);
    }

    /** Print a message to the console with a timestamp. */
    private static void log(String msg)
    {
        Thread t = Thread.currentThread();
        if (t != EarthShape.lastLoggedThread) {
            System.out.println(""+System.currentTimeMillis()+
                               " ["+t.getName()+"]"+
                               ": "+msg);
            EarthShape.lastLoggedThread = t;
        }
        else {
            System.out.println(""+System.currentTimeMillis()+
                               ": "+msg);
        }
    }

    /** Initialize the GL context. */
    @Override
    public void init(GLAutoDrawable drawable) {
        log("init");

        // The tutorial I am working from uses 'GL' rather than 'GL2',
        // but I find that using 'GL' leads to syntax errors since
        // some of the methods I'm supposed to call are only defined
        // for 'GL2'.  I assume the tutorial is just out of date.
        GL2 gl = drawable.getGL().getGL2();

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

        // Set up an animator to keep redrawing.  This is not started
        // until we enter "FPS" mode.
        this.animator = new Animator(drawable);

        // Use a light blue background.
        gl.glClearColor(0.8f, 0.9f, 1.0f, 0);
        //gl.glClearColor(0,0,0,0);

        // Enable lighting generally.
        gl.glEnable(GL2.GL_LIGHTING);

        // Enable light #0, which has some default properties that,
        // for the moment, seem to work adequately.
        gl.glEnable(GL2.GL_LIGHT0);

        // Position to try to get some more differentiation among
        // surfaces with different normals.
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION,
            new float[] {-0.5f, 1f, 0.5f, 0}, 0);

        // Increase the ambient intensity of that light.
        //gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_AMBIENT, new float[] {1,1,1,1}, 0);
        //
        // There was no need for that; the dim light was once again
        // a consequence of non-unit normal vectors.
    }

    /** Release allocated resources associated with the GL context. */
    @Override
    public void dispose(GLAutoDrawable drawable) {
        log("dispose");
        GL2 gl = drawable.getGL().getGL2();

        this.compassTexture.destroy(gl);
        this.compassTexture = null;

        this.animator.remove(drawable);
        this.animator.stop();
        this.animator = null;
    }

    /** Draw one frame to the screen. */
    @Override
    public void display(GLAutoDrawable drawable) {
        //log("display");

        // First, update object locations per physics rules.
        this.updatePhysics();

        GL2 gl = drawable.getGL().getGL2();

        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

        // Specify camera projection.
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();

        // Note: The combination of near clipping plane and the
        // left/right/top/bottom values defines the field of view.
        // If you move the clipping plane nearer the camera without
        // adjusting the edges, the FOV becomes larger!
        gl.glFrustum(-0.1, 0.1, -0.1, 0.1, 0.1, 100);

        // Rotate and position camera.  Effectively, these
        // transformations happen in the reverse order they are
        // written here; first we translate, then yaw, then
        // finally pitch.
        gl.glRotatef(-this.cameraPitchDegrees, +1, 0, 0);
        gl.glRotatef(-this.cameraAzimuthDegrees, 0, +1, 0);
        {
            Vector3f c = this.cameraPosition;
            gl.glTranslatef(-c.x(), -c.y(), -c.z());
        }

        // Enable depth test for hidden surface removal.
        gl.glEnable(GL.GL_DEPTH_TEST);

        // Enable automatic normal vector normalization so
        // I can freely scale my geometry without worrying about
        // denormalizing the normal vectors and messing up
        // the lighting calculations.
        gl.glEnable(GL2.GL_NORMALIZE);

        // Future matrix manipulations are for the model.
        gl.glMatrixMode(GL2.GL_MODELVIEW);

        // Make axis normals point toward +Y since my light is
        // above the scene.
        gl.glNormal3f(0,1,0);

        // X axis.
        {
            gl.glBegin(GL.GL_LINES);
            glMaterialColor3f(gl, 1,0,0);       // Red

            // Draw from origin to points at infinity.
            gl.glVertex4f(0,0,0,1);
            gl.glVertex4f(-1,0,0,0);
            gl.glVertex4f(0,0,0,1);
            gl.glVertex4f(+1,0,0,0);

            for (int i=-100; i < 100; i++) {
                if (i == 0) { continue; }
                float size = (i%10==0? 0.5f : 0.1f);
                gl.glVertex3f(i, size, 0);
                gl.glVertex3f(i, -size, 0);
                gl.glVertex3f(i, 0, size);
                gl.glVertex3f(i, 0, -size);
            }

            gl.glEnd();
        }

        // Y axis.
        {
            gl.glBegin(GL.GL_LINES);
            glMaterialColor3f(gl, 0,0.5f,0);    // Dark green

            gl.glVertex4f(0,0,0,1);
            gl.glVertex4f(0,-1,0,0);
            gl.glVertex4f(0,0,0,1);
            gl.glVertex4f(0,+1,0,0);

            for (int i=-100; i < 100; i++) {
                if (i == 0) { continue; }
                float size = (i%10==0? 0.5f : 0.1f);
                gl.glVertex3f(size, i, 0);
                gl.glVertex3f(-size, i, 0);
                gl.glVertex3f(0, i, size);
                gl.glVertex3f(0, i, -size);
            }

            gl.glEnd();
        }

        // Z axis.
        {
            gl.glBegin(GL.GL_LINES);
            glMaterialColor3f(gl, 0,0,1);       // Dark blue

            gl.glVertex4f(0,0,0,1);
            gl.glVertex4f(0,0,-1,0);
            gl.glVertex4f(0,0,0,1);
            gl.glVertex4f(0,0,+1,0);

            for (int i=-100; i < 100; i++) {
                if (i == 0) { continue; }
                float size = (i%10==0? 0.5f : 0.1f);
                gl.glVertex3f(0, size, i);
                gl.glVertex3f(0, -size, i);
                gl.glVertex3f(size, 0, i);
                gl.glVertex3f(-size, 0, i);
            }

            gl.glEnd();
        }

        // Scale everything that follows up by 2x.
        gl.glPushMatrix();
        gl.glScalef(2,2,2);
        //gl.glScalef(0.5f,0.5f,0.5f);

        // Draw a colored triangle.
        {
            gl.glBegin(GL.GL_TRIANGLES);
            gl.glNormal3f(0,0,1);

            // Red in lower-left.
            glMaterialColor3f(gl, 1, 0, 0);
            gl.glVertex3f(0.25f, 0.25f, 0);

            // Green in lower-right.
            glMaterialColor3f(gl, 0, 1, 0);
            gl.glVertex3f(0.75f, 0.25f, 0);

            // Blue in upper-left.
            glMaterialColor3f(gl, 0, 0, 1);
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
            glMaterialColor3f(gl, 1,1,1);

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

        gl.glPopMatrix();

        // Draw an initial surface rectangle.
        this.drawCompassRect(gl,
            1, 0, -2,
            1, 0, -1,
            2, 0, -2,
            2, 0, -1);

        // Draw another that curves down.
        this.drawCompassRect(gl,
            2, 0, -2,
            2, 0, -1,
            3, -0.2f, -2,
            3, -0.2f, -1);

        this.compassTexture.disable(gl);

        gl.glFlush();
    }

    /** Set the next vertex's color using glMaterial.  My intent is this
      * is sort of a replacement for glColor when using lighting. */
    private static void glMaterialColor3f(GL2 gl, float r, float g, float b)
    {
        gl.glMaterialfv(GL.GL_FRONT_AND_BACK, GL2.GL_AMBIENT_AND_DIFFUSE,
            new float[] { r,g,b,1 }, 0);
    }

    /** Draw a rectangle with the compass texture. */
    private void drawCompassRect(
        GL2 gl,
        float nwx, float nwy, float nwz,
        float swx, float swy, float swz,
        float nex, float ney, float nez,
        float sex, float sey, float sez)
    {
        gl.glBegin(GL.GL_TRIANGLE_STRIP);

        this.compassTexture.bind(gl);

        // Normal vector, based on just three vertices (since these
        // are supposed to be flat anyway).
        Vector3f nw = new Vector3f(nwx, nwy, nwz);
        Vector3f sw = new Vector3f(swx, swy, swz);
        Vector3f se = new Vector3f(sex, sey, sez);
        Vector3f normal = (se.minus(sw)).cross(nw.minus(sw));
        gl.glNormal3f(normal.x(), normal.y(), normal.z());

        // NW corner.
        gl.glTexCoord2f(0,1);
        gl.glVertex3f(nwx, nwy, nwz);

        // SW corner.
        gl.glTexCoord2f(0,0);
        gl.glVertex3f(swx, swy, swz);

        // NE corner.
        gl.glTexCoord2f(1,1);
        gl.glVertex3f(nex, ney, nez);

        // SE corner.
        gl.glTexCoord2f(1,0);
        gl.glVertex3f(sex, sey, sez);

        gl.glEnd();
    }

    /** Called when the window is resized.  The superclass does
      * basic resize handling, namely adjusting the viewport to
      * fill the canvas, so we do not need to do anything more. */
    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        //log("reshape");
    }

    /** Update state variables to reflect passage of time. */
    private void updatePhysics()
    {
        // Everything that happens should be scaled according to how
        // much real time has elapsed since the last physics update
        // so that the motion appears smooth even when the frames per
        // second varies.
        long newMillis = System.currentTimeMillis();
        float elapsedSeconds = (newMillis - this.lastPhysicsUpdateMillis) / 1000.0f;

        // Update camera velocity based on move keys.
        boolean moving = false;
        for (MoveDirection md : MoveDirection.values()) {
            if (this.moveKeys[md.ordinal()]) {
                moving = true;

                // Rotate the direction of 'md' according to the
                // current camera azimuth.
                Vector3f rd = md.direction.rotate(this.cameraAzimuthDegrees,
                    new Vector3f(0, +1, 0));

                // Scale it per camera acceleration and elapsed time.
                Vector3f srd = rd.times(CAMERA_ACCELERATION * elapsedSeconds);

                // Apply that to the camera velocity.
                this.cameraVelocity = this.cameraVelocity.plus(srd);
            }
        }

        // Update camera position.
        if (!this.cameraVelocity.isZero()){
            // Move camera.
            this.cameraPosition =
                this.cameraPosition.plus(this.cameraVelocity.times(elapsedSeconds));

            // Apply friction to the velocity.
            Vector3f v = this.cameraVelocity;
            float speed = (float)v.length();
            float maxFrictionAccel = moving?
                MOVING_CAMERA_FRICTION : STATIONARY_CAMERA_FRICTION;
            maxFrictionAccel *= elapsedSeconds;

            // Remove up to maxFrictionAccel speed.
            if (speed < maxFrictionAccel) {
                this.cameraVelocity = new Vector3f(0,0,0);
            }
            else {
                this.cameraVelocity =
                    v.minus(v.normalize().times(maxFrictionAccel));
            }
        }

        this.setStatusLabel();
        this.lastPhysicsUpdateMillis = newMillis;
    }

    public static void main(String[] args)
    {
        (new EarthShape()).setVisible(true);
    }

    /** Map from KeyEvent key code to corresponding movement
      * direction, or null if it does not correspond. */
    private static MoveDirection keyCodeToMoveDirection(int code)
    {
        switch (code) {
            case KeyEvent.VK_A:     return MoveDirection.MD_LEFT;
            case KeyEvent.VK_D:     return MoveDirection.MD_RIGHT;
            case KeyEvent.VK_SPACE: return MoveDirection.MD_UP;
            case KeyEvent.VK_Z:     return MoveDirection.MD_DOWN;
            case KeyEvent.VK_W:     return MoveDirection.MD_FORWARD;
            case KeyEvent.VK_S:     return MoveDirection.MD_BACKWARD;
            default:                return null;
        }
    }

    @Override
    public void keyPressed(KeyEvent ev) {
        //log("key pressed: "+ev);

        MoveDirection md = EarthShape.keyCodeToMoveDirection(ev.getKeyCode());
        if (md != null) {
            this.moveKeys[md.ordinal()] = true;
        }
    }

    /** Set the status label text to reflect other state variables. */
    private void setStatusLabel()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("camera="+this.cameraPosition);
        sb.append(", az="+this.cameraAzimuthDegrees);
        sb.append(", pch="+this.cameraPitchDegrees);
        if (this.fpsCameraMode) {
            sb.append(", FPS mode (click to exit)");
        }
        this.statusLabel.setText(sb.toString());
    }

    @Override
    public void keyReleased(KeyEvent ev) {
        MoveDirection md = EarthShape.keyCodeToMoveDirection(ev.getKeyCode());
        if (md != null) {
            this.moveKeys[md.ordinal()] = false;
        }
    }

    @Override
    public void keyTyped(KeyEvent ev) {}

    @Override
    public void mouseClicked(MouseEvent ev) {}

    @Override
    public void mouseEntered(MouseEvent ev) {}

    @Override
    public void mouseExited(MouseEvent ev) {}

    @Override
    public void mousePressed(MouseEvent ev) {
        log("mouse pressed: ev="+ev);
        if (ev.getButton() == MouseEvent.BUTTON1) {
            this.fpsCameraMode = !this.fpsCameraMode;

            this.setStatusLabel();

            if (this.fpsCameraMode) {
                // When transition into FPS mode, move the mouse
                // to the center and hide it.
                this.centerMouse(ev);
                ev.getComponent().setCursor(this.blankCursor);

                // Only run the animator thread in FPS mode.
                this.animator.start();
            }
            else {
                // Un-hide the mouse cursor.
                ev.getComponent().setCursor(null);

                // And stop the animator.
                this.animator.stop();
            }
        }
    }

    /** Move the mouse to the center of the component where the given
      * event originated, and also return that center point in screen
      * coordinates. */
    private Point centerMouse(MouseEvent ev)
    {
        // Calculate the screen coordinates of the center of the
        // clicked component.
        Point absComponentLoc = ev.getComponent().getLocationOnScreen();
        Dimension comDim = ev.getComponent().getSize();
        int cx = absComponentLoc.x + comDim.width/2;
        int cy = absComponentLoc.y + comDim.height/2;

        // Move the mouse to the center of the clicked component.
        robotObject.mouseMove(cx, cy);

        return new Point(cx, cy);
    }

    @Override
    public void mouseReleased(MouseEvent ev) {}

    @Override
    public void mouseDragged(MouseEvent ev) {}

    @Override
    public void mouseMoved(MouseEvent ev) {
        //log("mouse moved: ev="+ev);
        if (this.fpsCameraMode) {
            // Get the screen coordinate where mouse is now.
            Point mouseAbsLoc = ev.getLocationOnScreen();

            // Move the mouse to the center of the clicked component,
            // and get that location.
            Point c = this.centerMouse(ev);

            // Calculate delta.
            int dx = mouseAbsLoc.x - c.x;
            int dy = mouseAbsLoc.y - c.y;

            // The act of re-centering the mouse causes an extra
            // mouse movement event to fire with a zero delta.
            // Discard it as a minor optimization.
            if (dx == 0 && dy == 0) {
                return;
            }

            //log("FPS: dx="+dx+", dy="+dy);

            // Rotate the camera based on mouse movement.
            float newAz = this.cameraAzimuthDegrees - dx * CAMERA_HORIZONTAL_SENSITIVITY;

            // Normalize the azimuth to [0,360).
            this.cameraAzimuthDegrees = FloatUtil.modulus(newAz, 360);

            // Rotate the pitch.
            float newPitch = this.cameraPitchDegrees + dy * CAMERA_VERTICAL_SENSITIVITY;
            this.cameraPitchDegrees = FloatUtil.clamp(newPitch, -90, 90);

            this.setStatusLabel();
        }
    }
}
