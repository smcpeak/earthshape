// EarthMapCanvas.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.swing.JPanel;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;
import com.jogamp.opengl.util.awt.TextRenderer;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.TextureIO;

import util.FloatUtil;
import util.Matrix4f;
import util.Vector3f;
import util.Vector4f;

/** Widget to show a virtual 3D map of a reconstruction of the surface
  * of the Earth based on astronomical observation.  This is just the
  * GL-based canvas along with its mouse and keyboard support; the
  * other widgets, along with the surface construction algorithms, are
  * in EarthShape. */
public class EarthMapCanvas
    // This is a Swing panel object.
    extends JPanel
    // Listen for GL draw events.
    implements GLEventListener,
               // Handle keyboard and mouse input.
               KeyListener, MouseListener, MouseMotionListener
{
    // --------- Private constants ----------
    /** AWT boilerplate generated serial ID. */
    private static final long serialVersionUID = -4534243085229567690L;

    /** When the mouse moves one pixel horizontally, the camera
      * azimuth turns by this many degrees. */
    private static final float CAMERA_HORIZONTAL_SENSITIVITY = 0.5f;

    /** When the mouse moves one pixel vertically, the camera pitch
      * angle changes by this many degrees. */
    private static final float CAMERA_VERTICAL_SENSITIVITY = 0.5f;

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

    /** Desired distance between camera and front clipping plane. */
    private static final float FRONT_CLIP_DISTANCE = 0.01f;

    // --------- Public constants ----------
    /** Units in 3D space coordinates per km in surface being mapped. */
    public static final float SPACE_UNITS_PER_KM = 0.001f;

    // ---------- Instance variables ----------
    /** EarthShape application frame into which we are embedded.  That
      * frame provides some UI support, including the status bar. */
    EarthShape earthShapeFrame;

    // ---- Textures ----
    /** Compass rose texture.  This is only valid between 'init' and
      * 'dispose'. */
    private Texture compassTexture;

    /** Earth surface texture.  Valid between 'init' and 'dispose'.
      * The texture I plan to load, EarthMap.jpg, comes from
      * http://wennberg-wiki.caltech.edu/.  It is 2500x1250 and
      * is an equirectangular projection.  The top is 90N, bottom
      * is 90S, left is 180W, right is 180E. */
    private Texture earthMapTexture;

    /** Blank cursor, used to hide the mouse cursor. */
    private Cursor blankCursor;

    // ---- Camera position and motion ----
    /** Camera position in space. */
    private Vector3f cameraPosition = new Vector3f(1,1,2);

    /** Azimuth angle in which the camera is looking, in degrees
      * to the left of the -Z axis.  It is kept in [0,360), wrapping
      * as it gets to either extreme. */
    private float cameraAzimuthDegrees = 0;

    /** Camera pitch angle, in degrees.  In [-90,90], where +90 is
      * straight up. */
    private float cameraPitchDegrees = 0;

    /** Velocity of camera movement, in world coordinates.  The magnitude
      * is units per second. */
    private Vector3f cameraVelocity = new Vector3f(0,0,0);

    /** True when we are in "first person shooter" camera control
      * mode, where mouse movement looks around and the mouse is
      * kept inside the canvas window. */
    private boolean fpsCameraMode = false;

    /** The value of the millisecond timer the last time physics was
      * updated.  Currently, "physics" is only used as part of the
      * camera control system. */
    private long lastPhysicsUpdateMillis;

    // ---- Input device support ----
    /** "Robot" interface object, used to move the mouse in FPS mode. */
    private Robot robotObject;

    /** For each possible movement direction, true if that direction's
      * key is held down. */
    private boolean[] moveKeys = new boolean[MoveDirection.values().length];

    // ---- Draw options ----
    /** If true, draw surfaces using the abstract compass texture.
      * Otherwise, draw them using the EarthMap texture. */
    public boolean drawCompasses = true;

    /** If true, draw surface normals as short line segments. */
    public boolean drawSurfaceNormals = true;

    /** If true, draw celestial North vectors on each square. */
    public boolean drawCelestialNorth = false;

    // ---- GL canvas support ----
    /** The underlying GL canvas. */
    private GLCanvas glCanvas;

    /** Current aspect ratio: canvas width divided by canvas height
      * in pixels.  (Really, aspect ratio ought to reflect physical
      * size ratio, but I will assume pixels are square; fortunately,
      * they usually are.) */
    private float aspectRatio = 1.0f;

    /** Animator object for the GL canvas.  Valid between init and
      * dispose. */
    private Animator animator;

    /** Object to allow drawing text on top of the GL canvas.  Valid
      * between init and dispose. */
    private TextRenderer textRenderer;

    /** Labels to draw at various locations.  This is cleared at the
      * beginning of display(), so persistent labels must be re-added
      * each time we draw a new frame. */
    private ArrayList<CoordinateLabel> worldLabels = new ArrayList<CoordinateLabel>();

    // ---- Virtual 3D map we are building or have built ----
    /** Squares of the surface we have built. */
    private ArrayList<SurfaceSquare> surfaceSquares = new ArrayList<SurfaceSquare>();

    // ---------- Methods ----------
    public EarthMapCanvas(EarthShape earthShapeFrame_, GLCapabilities caps)
    {
        this.earthShapeFrame = earthShapeFrame_;

        try {
            this.robotObject = new Robot();
        }
        catch (AWTException e) {
            e.printStackTrace();
            System.exit(2);
        }

        // Make the GL canvas and specify that 'this' object will
        // handle its draw-related events.
        this.glCanvas = new GLCanvas(caps);
        this.glCanvas.addGLEventListener(this);

        // I intend this panel to be entirely occupied by the GL canvas,
        // but rather than inherit GL canvas, I'll add one as a child
        // and use BorderLayout to let it fill this panel.
        this.setLayout(new BorderLayout());
        this.add(this.glCanvas, BorderLayout.CENTER);

        this.lastPhysicsUpdateMillis = System.currentTimeMillis();

        this.glCanvas.addKeyListener(this);
        this.glCanvas.addMouseListener(this);
        this.glCanvas.addMouseMotionListener(this);

        // Create a blank cursor so I can hide the cursor later.
        // http://stackoverflow.com/questions/1984071/how-to-hide-cursor-in-a-swing-application
        {
            BufferedImage cursorImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            this.blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
                cursorImg, new Point(0, 0), "blank cursor");
        }
    }

    /** Print a message to the console with a timestamp. */
    private static void log(String msg)
    {
        EarthShape.log(msg);
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
        log("loading textures");
        this.compassTexture = loadTexture(gl, "textures/compass-rose.png", TextureIO.PNG);
        this.earthMapTexture = loadTexture(gl, "textures/EarthMap.jpg", TextureIO.JPG);

        // Set up an animator to keep redrawing.  This is not started
        // until we enter "FPS" mode.
        this.animator = new Animator(drawable);

        // Create the text renderer too.
        this.textRenderer = new TextRenderer(new Font("SansSerif", Font.BOLD, 16));

        // Use a dark background, like the night sky.
        gl.glClearColor(0, 0, 0.1f, 0);
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

    /** Load and return a texture of a given name and type.  This exits
      * the process if texture loading fails. */
    private Texture loadTexture(GL2 gl, String fileName, String fileType)
    {
        InputStream stream = getClass().getResourceAsStream(fileName);
        try {
            TextureData data = TextureIO.newTextureData(
                gl.getGLProfile(), stream, false /*mipmap*/, fileType);
            Texture ret = TextureIO.newTexture(data);
            log("loaded "+fileName+"; mem="+ret.getEstimatedMemorySize()+
                ", coords="+ret.getImageTexCoords());
            return ret;
        }
        catch (IOException e) {
            // For now at least, failure to load a texture is fatal.
            e.printStackTrace();
            System.exit(2);
            return null;
        }
        finally {
            try {
                stream.close();
            }
            catch (IOException e) {}
        }
    }

    /** Release allocated resources associated with the GL context. */
    @Override
    public void dispose(GLAutoDrawable drawable) {
        log("dispose");
        GL2 gl = drawable.getGL().getGL2();

        this.compassTexture.destroy(gl);
        this.compassTexture = null;

        this.earthMapTexture.destroy(gl);
        this.earthMapTexture = null;

        this.animator.remove(drawable);
        this.animator.stop();
        this.animator = null;

        this.textRenderer.dispose();
        this.textRenderer = null;
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
        gl.glFrustum(-FRONT_CLIP_DISTANCE * this.aspectRatio,     // left
                     FRONT_CLIP_DISTANCE * this.aspectRatio,      // right
                     -FRONT_CLIP_DISTANCE,                        // bottom
                     FRONT_CLIP_DISTANCE,                         // top
                     FRONT_CLIP_DISTANCE,                         // front clip
                     300);                                        // back clip

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

        // Throw away labels from the prior frame.
        this.worldLabels.clear();

        this.drawAxes(gl);

        this.drawEarthSurface(gl);

        this.drawLabels(drawable, gl);

        gl.glFlush();
    }

    /** Draw 'worldLabels'. */
    private void drawLabels(GLAutoDrawable drawable, GL2 gl)
    {
        // Get a matrix that projects from world coordinates to
        // abstract screen coordinates in [-1,1] x [-1,1].  This
        // must be done before we start drawing text because the
        // GL matrices are changed by the text renderer.
        Matrix4f worldToAbstractScreen;
        {
            Matrix4f view = getGlMatrix(gl, GL2.GL_MODELVIEW_MATRIX);
            Matrix4f projection = getGlMatrix(gl, GL2.GL_PROJECTION_MATRIX);
            worldToAbstractScreen = view.times(projection);
        }

        this.textRenderer.beginRendering(
            drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
        this.textRenderer.setColor(1, 0.3f, 1, 1);   // Dark pink

        for (CoordinateLabel cl : this.worldLabels) {
            this.drawTextAtWorld(drawable, worldToAbstractScreen, cl.label, cl.coordinate);
        }

        this.textRenderer.endRendering();
    }

    /** Get the GL matrix identified by 'pname'. */
    private static Matrix4f getGlMatrix(GL2 gl, int pname)
    {
        float[] entries = new float[16];
        gl.glGetFloatv(pname, entries, 0);
        return new Matrix4f(entries);
    }

    /** Draw some text on top of the GL canvas at a given world coordinate.
      * 'wtas' is the product of the modelview and projection matrices.
      * Note: The text will appear even if the point in question is hidden
      * behind an intervening opaque object. */
    private void drawTextAtWorld(GLAutoDrawable drawable, Matrix4f wtas,
                                 String text, Vector4f worldCoord)
    {
        // Transform to abstract screen coordinates.
        Vector4f as = Matrix4f.multiply(worldCoord, wtas);

        if (as.z() < 0) {
            // The point is behind the camera.
            return;
        }

        // Convert to pixel coordinates in the drawable space.
        int screenX = (int)((as.x() * 0.5 / as.w() + 0.5) * drawable.getSurfaceWidth());
        int screenY = (int)((as.y() * 0.5 / as.w() + 0.5) * drawable.getSurfaceHeight());

        // Put the label a few pixels away from the actual coordinate
        // so they don't overlap as much.
        this.textRenderer.draw(text, screenX+5, screenY+5);
    }

    private void drawAxes(GL2 gl)
    {
        // Use thicker lines so they will show up better if/when I
        // make a screenshot recording.
        gl.glLineWidth(2);

        // Make axis normals point toward +Y since my light is
        // above the scene.
        gl.glNormal3f(0,1,0);

        // The axes are not textured.
        gl.glDisable(GL.GL_TEXTURE_2D);

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

            // Draw a "+X" at the positive end for reference.
            gl.glPushMatrix();
            gl.glTranslatef(100, 10, 0);
            gl.glScalef(10, 10, 10);
            gl.glRotatef(-90, 0, 1, 0);
            gl.glTranslatef(-1, 0, 0);
            drawPlus(gl);
            gl.glTranslatef(1, 0, 0);
            drawX(gl);
            gl.glPopMatrix();

            // And a "-X" at the other end.
            gl.glPushMatrix();
            gl.glTranslatef(-100, 10, 0);
            gl.glScalef(10, 10, 10);
            gl.glRotatef(+90, 0, 1, 0);
            gl.glTranslatef(-1, 0, 0);
            drawMinus(gl);
            gl.glTranslatef(1, 0, 0);
            drawX(gl);
            gl.glPopMatrix();
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

            // Draw a "+Y" at the positive end for reference.
            gl.glPushMatrix();
            gl.glTranslatef(0, 100, 10);
            gl.glScalef(10, 10, 10);
            gl.glRotatef(90, 1, 0, 0);
            gl.glTranslatef(-1, 0, 0);
            drawPlus(gl);
            gl.glTranslatef(1, 0, 0);
            drawY(gl);
            gl.glPopMatrix();

            // And "-Y".
            gl.glPushMatrix();
            gl.glTranslatef(0, -100, 20);
            gl.glScalef(10, 10, 10);
            gl.glRotatef(-90, 1, 0, 0);
            gl.glTranslatef(-1, 0, 0);
            drawMinus(gl);
            gl.glTranslatef(1, 0, 0);
            drawY(gl);
            gl.glPopMatrix();
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

            // Draw a "+Z" at the positive end for reference.
            gl.glPushMatrix();
            gl.glTranslatef(0, 10, 100);
            gl.glScalef(10, 10, 10);
            gl.glRotatef(180, 0, 1, 0);
            gl.glTranslatef(-1, 0, 0);
            drawPlus(gl);
            gl.glTranslatef(1, 0, 0);
            drawZ(gl);
            gl.glPopMatrix();

            // And "-Z".
            gl.glPushMatrix();
            gl.glTranslatef(0, 10, -100);
            gl.glScalef(10, 10, 10);
            gl.glTranslatef(-1, 0, 0);
            drawMinus(gl);
            gl.glTranslatef(1, 0, 0);
            drawZ(gl);
            gl.glPopMatrix();
        }
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
        Vector3f nw,
        Vector3f sw,
        Vector3f ne,
        Vector3f se)
    {
        gl.glEnable(GL.GL_TEXTURE_2D);
        this.compassTexture.bind(gl);

        gl.glBegin(GL.GL_TRIANGLE_STRIP);

        glMaterialColor3f(gl, 1, 1, 1);

        // Normal vector, based on just three vertices (since these
        // are supposed to be flat anyway).
        Vector3f normal = (se.minus(sw)).cross(nw.minus(sw));
        gl.glNormal3f(normal.x(), normal.y(), normal.z());

        // NW corner.
        gl.glTexCoord2f(0,1);
        gl.glVertex3fv(nw.getArray(), 0);

        // SW corner.
        gl.glTexCoord2f(0,0);
        gl.glVertex3fv(sw.getArray(), 0);

        // NE corner.
        gl.glTexCoord2f(1,1);
        gl.glVertex3fv(ne.getArray(), 0);

        // SE corner.
        gl.glTexCoord2f(1,0);
        gl.glVertex3fv(se.getArray(), 0);

        gl.glEnd();
    }

    /** Draw a square with the earth map texture. */
    private void drawEarthMapRect(
        GL2 gl,
        Vector3f nw,
        Vector3f sw,
        Vector3f ne,
        Vector3f se,
        float centerLatitude,
        float centerLongitude,
        float sizeKm)
    {
        // Calculate the latitudes of the North and South edges, assuming
        // 111km per degree.  (222 is because I'm dividing in half in order
        // to offset in both directions.)
        float northLatitude = centerLatitude + (sizeKm / 222);
        float southLatitude = centerLatitude - (sizeKm / 222);

        // Calculate longitudes of the corners, assuming
        // 111km * cos(latitude) per degree.
        float neLongitude = (float)(centerLongitude + (sizeKm / (222 * FloatUtil.cosDegf(northLatitude))));
        float nwLongitude = (float)(centerLongitude - (sizeKm / (222 * FloatUtil.cosDegf(northLatitude))));
        float seLongitude = (float)(centerLongitude + (sizeKm / (222 * FloatUtil.cosDegf(southLatitude))));
        float swLongitude = (float)(centerLongitude - (sizeKm / (222 * FloatUtil.cosDegf(southLatitude))));

        gl.glEnable(GL.GL_TEXTURE_2D);
        this.earthMapTexture.bind(gl);

        gl.glBegin(GL.GL_TRIANGLE_STRIP);

        glMaterialColor3f(gl, 1, 1, 1);

        // Normal vector, based on just three vertices (since these
        // are supposed to be flat anyway).
        Vector3f normal = (se.minus(sw)).cross(nw.minus(sw));
        gl.glNormal3f(normal.x(), normal.y(), normal.z());

        // NW corner.
        gl.glTexCoord2f(long2tex(nwLongitude), lat2tex(northLatitude));
        gl.glVertex3fv(nw.getArray(), 0);

        // SW corner.
        gl.glTexCoord2f(long2tex(swLongitude), lat2tex(southLatitude));
        gl.glVertex3fv(sw.getArray(), 0);

        // NE corner.
        gl.glTexCoord2f(long2tex(neLongitude), lat2tex(northLatitude));
        gl.glVertex3fv(ne.getArray(), 0);

        // SE corner.
        gl.glTexCoord2f(long2tex(seLongitude), lat2tex(southLatitude));
        gl.glVertex3fv(se.getArray(), 0);

        gl.glEnd();
    }

    /** Convert a latitude to a 'v' texture coordinate for the Earth map. */
    private float lat2tex(float latitude)
    {
        // -90 maps to the bottom (0), +90 maps to the top (1).
        return (float)((latitude + 90) / 180);
    }

    /** Convert a longitude to a 'u' texture coordinate for the Earth map. */
    private float long2tex(float longitude)
    {
        // -180 maps to the left (0), +180 maps to the right (1).
        return (float)((longitude + 180) / 360);
    }

    /** Cause the GL canvas to redraw. */
    public void redrawCanvas()
    {
        // I want to be able to liberally call this, including before
        // things are fully initialized, so check for null.
        if (this.glCanvas != null) {
            this.glCanvas.display();
        }
    }

    /** Add a square to 'surfaceSquares'. */
    public void addSurfaceSquare(SurfaceSquare s)
    {
        this.surfaceSquares.add(s);
        //log("added square: "+s);
    }

    /** Remove a single surface square. */
    public void removeSurfaceSquare(SurfaceSquare s)
    {
        this.surfaceSquares.remove(s);

        // Go through the remaining squares and clean up any dangling
        // base references.
        for (SurfaceSquare r : this.surfaceSquares) {
            if (r.baseSquare == s) {
                r.baseSquare = null;
                r.baseMidpoint = null;
            }
        }
    }

    /** Remove all surface squares. */
    public void clearSurfaceSquares()
    {
        this.surfaceSquares.clear();
        log("cleared all surface squares");
    }

    /** Return current number of surface squares. */
    public int numSurfaceSquares()
    {
        return this.surfaceSquares.size();
    }

    /** Draw what is in 'surfaceSquares'. */
    private void drawEarthSurface(GL2 gl)
    {
        for (SurfaceSquare s : this.surfaceSquares) {
            this.drawSquare(gl, s);
        }
    }

    /** Draw one surface square. */
    private void drawSquare(GL2 gl, SurfaceSquare s)
    {
        Vector3f east = s.north.cross(s.up);

        // Size of the square in 3D coordinates.
        float squareSize = s.sizeKm * SPACE_UNITS_PER_KM;

        // Scale north and east by half of desired square size.
        Vector3f n = s.north.normalize().times(squareSize/2);
        Vector3f e = east.normalize().times(squareSize/2);

        Vector3f nw = s.center.plus(n).minus(e);
        Vector3f sw = s.center.minus(n).minus(e);
        Vector3f ne = s.center.plus(n).plus(e);
        Vector3f se = s.center.minus(n).plus(e);

        if (this.drawCompasses) {
            this.drawCompassRect(gl, nw, sw, ne, se);
        }
        else {
            this.drawEarthMapRect(gl, nw, sw, ne, se, s.latitude, s.longitude, s.sizeKm);
        }

        // Also draw a surface normal.
        if (this.drawSurfaceNormals) {
            gl.glDisable(GL.GL_TEXTURE_2D);
            gl.glBegin(GL.GL_LINES);
            glMaterialColor3f(gl, 0.5f, 0.5f, 0);    // Dark yellow
            gl.glNormal3f(0,1,0);
            gl.glVertex3fv(s.center.getArray(), 0);
            gl.glVertex3fv(s.center.plus(s.up).getArray(), 0);
            gl.glEnd();
        }

        // Draw a line to celestial North.
        if (this.drawCelestialNorth) {
            gl.glDisable(GL.GL_TEXTURE_2D);
            gl.glBegin(GL.GL_LINES);
            glMaterialColor3f(gl, 1, 0, 0);    // Red
            gl.glNormal3f(0,1,0);

            gl.glVertex3fv(s.center.getArray(), 0);

            Vector3f celestialNorth = s.north.rotate(s.latitude, east);
            gl.glVertex3fv(s.center.plus(celestialNorth.times(5)).getArray(), 0);

            gl.glEnd();
        }

        // Draw a box above the active square.
        if (s.showAsActive) {
            // A shorter version of "up".  I want to go a short distance so
            // the line is visible above the texture, but not so far
            // that the association with the square is unclear.
            Vector3f upShort = s.up.times(0.01f);

            gl.glDisable(GL.GL_TEXTURE_2D);
            gl.glBegin(GL.GL_LINE_LOOP);
            glMaterialColor3f(gl, 0, 1, 1);    // Cyan
            gl.glNormal3f(0,1,0);

            gl.glVertex3fv(nw.plus(upShort).getArray(), 0);
            gl.glVertex3fv(sw.plus(upShort).getArray(), 0);
            gl.glVertex3fv(se.plus(upShort).getArray(), 0);
            gl.glVertex3fv(ne.plus(upShort).getArray(), 0);

            gl.glEnd();

            // Also draw two line segments showing the translational
            // path from the base square.
            if (s.baseSquare != null && s.baseMidpoint != null) {
                gl.glBegin(GL.GL_LINE_STRIP);
                glMaterialColor3f(gl, 0, 1, 0);    // Green

                glVertex3(gl, s.baseSquare.center.plus(upShort));
                glVertex3(gl, s.baseMidpoint.plus(upShort));
                glVertex3(gl, s.center.plus(upShort));

                gl.glEnd();
            }
        }

        // Also draw rays to the stars observed here.
        if (s.drawStarRays) {
            for (StarObservation so : s.starObs) {
                // Limit ourselves to these three stars for the moment.
                if (!( so.name == "Polaris" ||
                       so.name == "Aldebaran" ||
                       so.name == "Procyon" )) {
                    continue;
                }

                // Bright line for rays at active square.
                float rayBrightness = (s.showAsActive? 1.0f : 0.5f);

                // Ray to star in nominal, -Z facing, coordinates.
                Vector3f nominalRay =
                    EarthShape.azimuthElevationToVector(so.azimuth, so.elevation);

                // Ray to star in map coordinates, taking into account
                // how the current surface is rotated.
                Vector3f localRay = nominalRay.rotateAA(s.rotationFromNominal);

                gl.glBegin(GL.GL_LINES);
                glMaterialColor3f(gl, rayBrightness, rayBrightness, rayBrightness);

                glVertex3(gl, s.center);

                // The observation is just a direction, so we draw
                // the ray as infinitely long (except it will be
                // clipped by the far clipping plane).  This does
                // not mean we are assuming the star is actually
                // infinitely far, just that it must be somewhere
                // along this line.
                Vector4f localRayDirection = new Vector4f(
                    localRay.x(), localRay.y(), localRay.z(), 0);
                gl.glVertex4fv(localRayDirection.getArray(), 0);

                // Also label this direction.
                this.worldLabels.add(new CoordinateLabel(localRayDirection, so.name));

                gl.glEnd();
            }
        }
    }

    /** Add a vertex from a 3D vector. */
    private static void glVertex3(GL2 gl, Vector3f pt)
    {
        gl.glVertex3fv(pt.getArray(), 0);
    }

    /** Draw a 2D "+" in the [0,1] box. */
    private void drawPlus(GL2 gl)
    {
        gl.glBegin(GL.GL_LINES);
        gl.glVertex2f(.2f, .5f);
        gl.glVertex2f(.8f, .5f);
        gl.glVertex2f(.5f, .2f);
        gl.glVertex2f(.5f, .8f);
        gl.glEnd();
    }

    /** Draw a 2D "-" in the [0,1] box. */
    private void drawMinus(GL2 gl)
    {
        gl.glBegin(GL.GL_LINES);
        gl.glVertex2f(.2f, .5f);
        gl.glVertex2f(.8f, .5f);
        gl.glEnd();
    }

    /** Draw a 2D "X" in the [0,1] box. */
    private void drawX(GL2 gl)
    {
        gl.glBegin(GL.GL_LINES);
        gl.glVertex2f(0f, 0f);
        gl.glVertex2f(1f, 1f);
        gl.glVertex2f(0f, 1f);
        gl.glVertex2f(1f, 0f);
        gl.glEnd();
    }

    /** Draw a 2D "Y" in the [0,1] box. */
    private void drawY(GL2 gl)
    {
        gl.glBegin(GL.GL_LINES);
        gl.glVertex2f(.5f, 0f);
        gl.glVertex2f(.5f, .5f);
        gl.glVertex2f(.5f, .5f);
        gl.glVertex2f(0f, 1f);
        gl.glVertex2f(.5f, .5f);
        gl.glVertex2f(1f, 1f);
        gl.glEnd();
    }

    /** Draw a 2D "Z" in the [0,1] box. */
    private void drawZ(GL2 gl)
    {
        gl.glBegin(GL.GL_LINE_STRIP);
        gl.glVertex2f(0f, 1f);
        gl.glVertex2f(1f, 1f);
        gl.glVertex2f(0f, 0f);
        gl.glVertex2f(1f, 0f);
        gl.glEnd();
    }

    /** Called when the window is resized.  The superclass does
      * basic resize handling, namely adjusting the viewport to
      * fill the canvas, so we do not need to do anything more. */
    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        //log("reshape");

        // Just keep track of the aspect ratio.  During display, we will
        // adjust the camera view in a corresponding way.
        this.aspectRatio = (float)width / (float)height;
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

        this.earthShapeFrame.updateUIState();
        this.lastPhysicsUpdateMillis = newMillis;
    }

    /** Get the square after 'sq' in 'surfaceSquares'.  This behaves like
      * the list has an extra 'null' element, corresponding to the state
      * where no square is active. */
    public SurfaceSquare getNextSquare(SurfaceSquare sq, boolean forward)
    {
        if (sq == null) {
            if (this.surfaceSquares.isEmpty()) {
                return null;
            }
            else if (forward) {
                return this.surfaceSquares.get(0);
            }
            else {
                return this.surfaceSquares.get(this.surfaceSquares.size()-1);
            }
        }
        else {
            int i = this.surfaceSquares.indexOf(sq);
            if (i < 0) {
                return null;
            }

            if (forward) {
                i++;
            }
            else {
                i--;
            }

            if (0 <= i && i < this.surfaceSquares.size()) {
                return this.surfaceSquares.get(i);
            }
            else {
                return null;
            }
        }
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

        MoveDirection md = EarthMapCanvas.keyCodeToMoveDirection(ev.getKeyCode());
        if (md != null) {
            this.moveKeys[md.ordinal()] = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent ev) {
        MoveDirection md = EarthMapCanvas.keyCodeToMoveDirection(ev.getKeyCode());
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

            this.earthShapeFrame.updateUIState();

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

                // Also kill any latent camera motion.
                this.cameraVelocity = new Vector3f(0,0,0);
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

            this.earthShapeFrame.updateUIState();
        }
    }

    /** Get a string that summarizes the state of the canvas. */
    public String getStatusString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("camera="+this.cameraPosition);
        sb.append(", az="+this.cameraAzimuthDegrees);
        sb.append(", pch="+this.cameraPitchDegrees);
        sb.append(", tex="+(this.drawCompasses? "compass" : "earth"));
        if (this.fpsCameraMode) {
            sb.append(", FPS mode (click to exit)");
        }
        return sb.toString();
    }
}

// EOF
