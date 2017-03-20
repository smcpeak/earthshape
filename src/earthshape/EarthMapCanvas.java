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
import java.util.Map;

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
import util.Matrix3f;
import util.Matrix4f;
import util.Vector3d;
import util.Vector3f;
import util.Vector4f;

import static util.swing.SwingUtil.log;

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

    /** Desired distance between camera and far clipping plane. */
    private static final float BACK_CLIP_DISTANCE = 300.0f;

    /** Crosshair radius, as a fraction of the vertical height
      * of the window. */
    private static final float CROSSHAIR_RADIUS = 0.05f;

    // --------- Public constants ----------
    /** Units in 3D space coordinates per km in surface being mapped. */
    public static final float SPACE_UNITS_PER_KM = 0.001f;

    // ---------- Instance variables ----------
    /** EarthShape application frame into which we are embedded.  That
      * frame provides some UI support, including the status bar. */
    private EarthShape earthShapeFrame;

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
    public Vector3f cameraPosition = new Vector3f(1,1,2);

    /** Azimuth angle in which the camera is looking, in degrees
      * to the left of the -Z axis.  It is kept in [0,360), wrapping
      * as it gets to either extreme. */
    public float cameraAzimuthDegrees = 0;

    /** Camera pitch angle, in degrees.  In [-90,90], where +90 is
      * straight up. */
    public float cameraPitchDegrees = 0;

    /** Velocity of camera movement, in world coordinates.  The magnitude
      * is units per second. */
    private Vector3f cameraVelocity = new Vector3f(0,0,0);

    /** True when we are in "first person shooter" camera control
      * mode, where mouse movement looks around and the mouse is
      * kept inside the canvas window. */
    private boolean fpsCameraMode = false;

    /** True to ignore the next mouse movement in FPS mode. */
    private boolean ignoreOneMouseMove = false;

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

    /** When false, moving the mouse right yaws the camera view
      * direction to the right.  When true, the reverse. */
    public boolean invertHorizontalCameraMovement = false;

    /** When false, moving the mouse up pitches the camera view
      * direction up.  When true, the reverse.  This defaults to
      * true since that's what I am used to. */
    public boolean invertVerticalCameraMovement = true;

    // ---- Draw options ----
    /** If true, draw surfaces as simple wireframes.  This takes
      * precedence over 'drawCompasses'. */
    public boolean drawWireframeSquares = false;

    /** If true, draw surfaces using the abstract compass texture.
      * Otherwise, draw them using the EarthMap texture. */
    public boolean drawCompasses = true;

    /** If true, draw surface normals as short line segments. */
    public boolean drawSurfaceNormals = false;

    /** If true, draw celestial North vectors on each square. */
    public boolean drawCelestialNorth = false;

    /** If true, draw rays to stars as unit vectors. */
    public boolean drawUnitStarRays = false;

    /** If true, then for the active square, also draw star
      * observation lines for its base square. */
    public boolean drawBaseSquareStarRays = false;

    /** If true, place the active square at the center of the
      * 3D coordinate system.
      *
      * Note: At the moment, this has some bugs.  For example,
      * stars reported by the model are drawn in the wrong place,
      * and selecting a square by clicking on it does not work
      * properly. */
    public boolean drawActiveSquareAtOrigin = false;

    /** Which of several frames, of an ad-hoc animation I am
      * creating, to draw.  0 means do not draw anything extra. */
    public int activeSquareAnimationState = 0;

    /** The animation state during the previous drawn frame.  This
      * is used to limit certain log messages to once per animation
      * state instead of once per frame. */
    private int lastAnimationStateLogged = 0;

    /** If true, draw the active world model if we are using one. */
    public boolean drawWorldWireframe = true;

    /** If true, draw the active world stars if we are using them. */
    public boolean drawWorldStars = true;

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
        this.setName("EarthMapCanvas (JPanel)");
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
        this.glCanvas.setName("GLCanvas");
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

    /** Put the focus on the GLCanvas inside this panel.  It is a
      * little weird to put the focus there, since the panel is what
      * responds to the key events, but this is what I came up with
      * after some trial and error.  I might be doing something wrong
      * somewhere.  (This is called by EarthShape right after the
      * window becomes visible.) */
    public void setFocusOnCanvas()
    {
        this.glCanvas.requestFocusInWindow();
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

        // Use a dark gray background.
        gl.glClearColor(0.2f, 0.2f, 0.2f, 0);

        // Enable lighting generally.
        gl.glEnable(GL2.GL_LIGHTING);

        // Enable light #0, which has some default properties that,
        // for the moment, seem to work adequately.
        gl.glEnable(GL2.GL_LIGHT0);

        // Position to try to get some more differentiation among
        // surfaces with different normals.
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION,
            new float[] {0.5f, 1f, 0.5f, 0}, 0);

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
                     BACK_CLIP_DISTANCE);                         // back clip

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

        // Save matrix in case we translate to the active square.
        gl.glPushMatrix();

        if (this.drawActiveSquareAtOrigin &&
            this.earthShapeFrame.getActiveSquare() != null)
        {
            // Translate the coordinate system so the active square is
            // at the origin.  This does not work perfectly at the moment,
            // but is good enough to use for a specific demonstration I
            // have in mind.
            Vector3f c = this.earthShapeFrame.getActiveSquare().center;
            gl.glTranslatef(-c.x(), -c.y(), -c.z());
        }

        this.doDrawWorldModel(gl);

        this.drawEarthSurface(gl);

        this.drawLabels(drawable, gl);

        gl.glPopMatrix();

        if (this.fpsCameraMode) {
            this.drawCrosshair(gl);
        }

        gl.glFlush();

        this.lastAnimationStateLogged = this.activeSquareAnimationState;
    }

    /** Draw a crosshair in the middle of the canvas. */
    private void drawCrosshair(GL2 gl)
    {
        gl.glPushAttrib(GL2.GL_ALL_ATTRIB_BITS);
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPushMatrix();

        gl.glLoadIdentity();
        gl.glOrtho(-this.aspectRatio, this.aspectRatio, -1, 1, -1, 1);

        gl.glLineWidth(1);
        gl.glDisable(GL.GL_TEXTURE_2D);
        gl.glNormal3f(0,1,0);
        glMaterialColor3f(gl, 1,0,0);       // Red

        gl.glBegin(GL.GL_LINES);

        gl.glVertex2f(-CROSSHAIR_RADIUS, 0);
        gl.glVertex2f(+CROSSHAIR_RADIUS, 0);
        gl.glVertex2f(0, -CROSSHAIR_RADIUS);
        gl.glVertex2f(0, +CROSSHAIR_RADIUS);

        gl.glEnd();

        gl.glPopMatrix();
        gl.glPopAttrib();
    }

    /** Draw 'worldLabels'. */
    private void drawLabels(GLAutoDrawable drawable, GL2 gl)
    {
        // Get a matrix that projects from 3D world coordinates to
        // abstract 2D screen coordinates in [-1,1] x [-1,1].  This
        // must be done before we start drawing text because the
        // GL matrices are changed by the text renderer.  We need
        // this to place labels properly, since they are associated
        // with 3D coordinates.
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

    /** Draw a wireframe of the current world model, if we are using one
      * and the associated option is enabled. */
    private void doDrawWorldModel(GL2 gl)
    {
        if ((this.drawWorldWireframe || this.drawWorldStars) &&
            this.earthShapeFrame.worldObservations.hasModelPoints())
        {
            WorldObservations wo = this.earthShapeFrame.worldObservations;

            gl.glLineWidth(2);
            gl.glNormal3f(0, 1, 0);
            gl.glDisable(GL.GL_TEXTURE_2D);

            gl.glPushMatrix();

            // Position of camera in transformed coordinate system.
            Vector3f transformedCamera = this.cameraPosition;

            // Get the square in original manifold coordinates
            // corresponding to the first reconstruction square.
            if (!this.surfaceSquares.isEmpty()) {
                SurfaceSquare firstPlaced = this.surfaceSquares.get(0);
                SurfaceSquare rootSquare =
                    wo.getModelSquare(firstPlaced.latitude, firstPlaced.longitude);
                rootSquare.sizeKm = firstPlaced.sizeKm;

                // Push a transformation matrix that will align the
                // drawn world model with the world we will start
                // building, assuming it begins at 'rootSquare'.
                double angle = rootSquare.rotationFromNominal.length();
                Vector3d u = rootSquare.rotationFromNominal.normalizeAsVector3d();
                gl.glRotated(-angle, u.x(), u.y(), u.z());
                Vector3f c = rootSquare.center.times(-1);
                gl.glTranslatef(c.x(), c.y(), c.z());

                // Reverse the transformation's effects on the camera
                // position.
                transformedCamera = transformedCamera.rotateDeg(angle, u.toVector3f());
                transformedCamera = transformedCamera.minus(c);

                // Do the same for my desired surface normal.
                Vector3f transformedNormal = new Vector3f(0, 1, 0);
                transformedNormal = transformedNormal.rotateDeg(angle, u.toVector3f());
                gl.glNormal3fv(transformedNormal.getArray(), 0);

                // Draw a box around that square.
                if (this.drawWorldWireframe) {
                    glMaterialColor3f(gl, 0,1,0);       // Green
                    this.drawActiveBoxAround(gl, rootSquare, 0.02f);
                }
            }

            if (this.drawWorldWireframe) {
                this.doDrawWorldWireframe(gl, wo);
            }

            if (this.drawWorldStars) {
                this.doDrawWorldStars(gl, wo, transformedCamera);
            }

            gl.glPopMatrix();
        }
    }

    /** Draw the world model in 'mo'. */
    private void doDrawWorldWireframe(GL2 gl, WorldObservations wo)
    {
        for (int longitude = -180; longitude <= 180; longitude += 30) {
            for (int latitude = -90; latitude <= 90; latitude += 30) {
                Vector3f pt = wo.getModelPt(latitude, longitude);
                if (longitude > -180) {
                    if (latitude == 0) {
                        // Draw the equator in white.
                        glMaterialColor3f(gl, 1,1,1);
                    }
                    else {
                        glMaterialColor3f(gl, 0,1,0);       // Green
                    }

                    gl.glBegin(GL.GL_LINES);
                    glVertex3f(gl, pt);
                    glVertex3f(gl, wo.getModelPt(latitude, longitude - 30));
                    gl.glEnd();
                }
                if (latitude > -90) {
                    if (longitude == 0) {
                        // Draw the prime meridian in white too.
                        glMaterialColor3f(gl, 1,1,1);
                    }
                    else {
                        glMaterialColor3f(gl, 0,1,0);       // Green
                    }

                    gl.glBegin(GL.GL_LINES);
                    glVertex3f(gl, pt);
                    glVertex3f(gl, wo.getModelPt(latitude - 30, longitude));
                    gl.glEnd();

                    // Minor note: Usually the -180 longitude line is
                    // the same as the 180 longitude line, meaning that
                    // line will get drawn twice.
                }
            }
        }
    }

    private void doDrawWorldStars(GL2 gl, WorldObservations wo,
        Vector3f transformedCamera)
    {
        // When a star is far away, draw it as being this far from
        // the camera so it is not too small, nor beyond the back
        // clipping plane.
        final float starFarDistance = 20;

        // Draw indicators around the stars.
        for (Map.Entry<String, Vector4f> e : wo.getModelStarMap().entrySet()) {
            String starName = e.getKey();
            Vector4f pt4 = e.getValue();
            Vector3f pt3 = pt4.slice3();

            // Skip stars that are disabled.
            if (!this.earthShapeFrame.isStarEnabled(starName)) {
                continue;
            }

            // Distance from camera to the star (when finite).
            double starDistance = transformedCamera.minus(pt3).length();

            if (pt4.w() == 0) {
                // Point at infinity.  Add the camera's position,
                // and push it out far enough to appear smallish.
                glMaterialColor3f(gl, 1,0,0);       // Red
                pt3 = transformedCamera.plus(pt3.normalize().times(starFarDistance));
            }
            else if (starDistance > starFarDistance) {
                // Star is far away.  Treat it similarly to one at
                // infinity.
                glMaterialColor3f(gl, 1,0.5f,0);    // Orange

                // Get direction from camera to the star.
                pt3 = pt3.minus(transformedCamera);

                // Then put it a fixed distance in that direction.
                pt3 = transformedCamera.plus(pt3.normalize().times(20));
            }
            else {
                glMaterialColor3f(gl, 1,1,0);       // Yellow
            }

            // Draw a symbol at the star's location.
            gl.glPushMatrix();
            gl.glTranslatef(pt3.x(), pt3.y(), pt3.z());
            EarthMapCanvas.drawOctahedron(gl, 0.1f /*radius*/);
            gl.glPopMatrix();

            // Get the current transform matrix from GL.
            float[] entries = new float[16];
            gl.glGetFloatv(GL2.GL_MODELVIEW_MATRIX, entries, 0);
            Matrix4f m = new Matrix4f(entries);
            Vector4f transformedPt4 = Matrix4f.multiply(pt4, m);

            // Label the star.
            this.worldLabels.add(new CoordinateLabel(transformedPt4, starName));
        }
    }

    /** Draw an octahedron at the origin with the given radius,
      * the distance from the center to a corner. */
    private static void drawOctahedron(GL2 gl, float radius)
    {
        // Vectors from center to each corner.
        Vector3f top = new Vector3f(0, radius, 0);
        Vector3f bot = new Vector3f(0, -radius, 0);
        Vector3f l = new Vector3f(-radius, 0, 0);
        Vector3f r = new Vector3f(+radius, 0, 0);
        Vector3f f = new Vector3f(0, 0, +radius);
        Vector3f b = new Vector3f(0, 0, -radius);

        // Top to each side corner.
        gl.glBegin(GL.GL_LINES);
        glVertex3f2(gl, top, l);
        glVertex3f2(gl, top, r);
        glVertex3f2(gl, top, f);
        glVertex3f2(gl, top, b);
        gl.glEnd();

        // Bottom to each side corner.
        gl.glBegin(GL.GL_LINES);
        glVertex3f2(gl, bot, l);
        glVertex3f2(gl, bot, r);
        glVertex3f2(gl, bot, f);
        glVertex3f2(gl, bot, b);
        gl.glEnd();

        // Connect the side corners.
        gl.glBegin(GL.GL_LINE_LOOP);
        glVertex3f(gl, l);
        glVertex3f(gl, f);
        glVertex3f(gl, r);
        glVertex3f(gl, b);
        gl.glEnd();
    }

    /** Set the next vertex's color using glMaterial.  My intent is this
      * is sort of a replacement for glColor when using lighting. */
    private static void glMaterialColor3f(GL2 gl, float r, float g, float b)
    {
        gl.glMaterialfv(GL.GL_FRONT_AND_BACK, GL2.GL_AMBIENT_AND_DIFFUSE,
            new float[] { r,g,b,1 }, 0);
    }

    private static void glMaterialColor4f(GL2 gl, float r, float g, float b, float a)
    {
        gl.glMaterialfv(GL.GL_FRONT_AND_BACK, GL2.GL_AMBIENT_AND_DIFFUSE,
            new float[] { r,g,b,a }, 0);
    }

    /** Draw a rectangle as a wireframe. */
    private void drawWireframeSquare(
        GL2 gl,
        Vector3f nw,
        Vector3f sw,
        Vector3f ne,
        Vector3f se)
    {
        gl.glPushAttrib(GL2.GL_ALL_ATTRIB_BITS);
        gl.glDisable(GL.GL_TEXTURE_2D);

        // Dark green with some transparency.
        glMaterialColor4f(gl, 0, 0.5f, 0, 0.2f);
        gl.glNormal3f(0, 1, 0);

        // Draw an opaque bounding square (blending is
        // not yet enabled here, so the alpha is ignored).
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3fv(nw.getArray(), 0);
        gl.glVertex3fv(sw.getArray(), 0);
        gl.glVertex3fv(se.getArray(), 0);
        gl.glVertex3fv(ne.getArray(), 0);
        gl.glEnd();

        // Now enable blending.
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        // Draw a translucent green square.
        gl.glBegin(GL.GL_TRIANGLE_STRIP);
        gl.glVertex3fv(nw.getArray(), 0);
        gl.glVertex3fv(sw.getArray(), 0);
        gl.glVertex3fv(ne.getArray(), 0);
        gl.glVertex3fv(se.getArray(), 0);
        gl.glEnd();

        gl.glPopAttrib();
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

        if (this.drawWireframeSquares) {
            // Don't actually draw anything yet.  The square uses
            // the GL "blend" feature to simulate translucency,
            // which requires that I draw the opaque elements first.
        }
        else if (this.drawCompasses) {
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

            Vector3f celestialNorth = s.north.rotateDeg(s.latitude, east);
            gl.glVertex3fv(s.center.plus(celestialNorth.times(5)).getArray(), 0);

            gl.glEnd();
        }

        // Draw a box above the active square.
        if (s.showAsActive) {
            glMaterialColor3f(gl, 0, 1, 1);    // Cyan
            this.drawActiveBoxAround(gl, s, 0.01f);
        }

        // Also draw rays to the stars observed here.
        if (s.drawStarRays) {
            this.drawStarRays(gl, s, s.center);

            if (this.drawBaseSquareStarRays && s.showAsActive && s.baseSquare != null) {
                this.drawStarRays(gl, s.baseSquare, s.center);

                this.drawActiveSquareAnimationState(gl, s);
            }
        }

        // Finally, draw the translucent square, if enabled.
        //
        // Note: I'm not really doing this right, since I do not
        // globally order my draw commands in the way needed to
        // get translucency for everything.  Currently, my goal
        // is just to make it look right for one square.
        if (this.drawWireframeSquares) {
            this.drawWireframeSquare(gl, nw, sw, ne, se);
        }
    }

    /** Draw a shallow rectangle around the indicated box using
      * the current material color and surface normal. */
    private void drawActiveBoxAround(GL2 gl, SurfaceSquare s, float height)
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

        // A shorter version of "up".  I want to go a short distance so
        // the line is visible above the texture, but not so far
        // that the association with the square is unclear.
        Vector3f upShort = s.up.times(height);

        gl.glDisable(GL.GL_TEXTURE_2D);
        gl.glBegin(GL.GL_LINE_LOOP);

        gl.glVertex3fv(nw.plus(upShort).getArray(), 0);
        gl.glVertex3fv(sw.plus(upShort).getArray(), 0);
        gl.glVertex3fv(se.plus(upShort).getArray(), 0);
        gl.glVertex3fv(ne.plus(upShort).getArray(), 0);

        gl.glEnd();

        // Draw another square below it so I can see when it is
        // selected and the camera is below the square.
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3fv(nw.minus(upShort).getArray(), 0);
        gl.glVertex3fv(sw.minus(upShort).getArray(), 0);
        gl.glVertex3fv(se.minus(upShort).getArray(), 0);
        gl.glVertex3fv(ne.minus(upShort).getArray(), 0);
        gl.glEnd();

        // Also draw two line segments showing the translational
        // path from the base square.
        if (s.baseSquare != null && s.baseMidpoint != null) {
            gl.glBegin(GL.GL_LINE_STRIP);
            glMaterialColor3f(gl, 0, 1, 0);    // Green

            glVertex3f(gl, s.baseSquare.center.plus(upShort));
            glVertex3f(gl, s.baseMidpoint.plus(upShort));
            glVertex3f(gl, s.center.plus(upShort));

            gl.glEnd();
        }
    }

    /** Draw rays from the center of 's' to each of its associated
      * star observations.  'starRaysOrigin' says where to draw the
      * base of each ray.  Normally it is 's.center', but it is
      * instead the center of a derived square when we are drawing
      * a base square's observation on the derived square. */
    private void drawStarRays(GL2 gl, SurfaceSquare s, Vector3f starRaysOrigin)
    {
        gl.glDisable(GL.GL_TEXTURE_2D);
        gl.glNormal3f(0,1,0);

        for (Map.Entry<String, StarObservation> entry : s.starObs.entrySet()) {
            StarObservation so = entry.getValue();

            // Bright line for rays at active square.
            float rayBrightness = (s.showAsActive? 1.0f : 0.4f);

            // Ray to star in world coordinates.
            Vector3f starRay = EarthShape.rayToStar(s, so);

            gl.glBegin(GL.GL_LINES);
            glMaterialColor3f(gl, rayBrightness, rayBrightness, rayBrightness);

            glVertex3f(gl, starRaysOrigin);

            // The observation is just a direction, so we draw
            // the ray as infinitely long (except it will be
            // clipped by the far clipping plane).  This does
            // not mean we are assuming the star is actually
            // infinitely far, just that it must be somewhere
            // along this line.
            Vector4f starRayDirection = new Vector4f(
                starRay.x(), starRay.y(), starRay.z(), 0);

            if (this.drawUnitStarRays) {
                // Draw the star ray as a unit vector.
                glVertex3f(gl, starRaysOrigin.plus(starRay));
            }
            else {
                // Draw it as a line to a point at infinity.
                gl.glVertex4fv(starRayDirection.getArray(), 0);
            }

            gl.glEnd();

            if (this.drawUnitStarRays) {
                this.drawDottedLineToXZPlane(gl, starRaysOrigin.plus(starRay));
            }

            String starLabel = so.name;

            // Calculate the deviation of this observation from that of
            // the base square.
            if (s.showAsActive && s.baseSquare != null) {
                StarObservation baseObservation = s.baseSquare.findObservation(so.name);
                if (baseObservation != null) {
                    // Get ray from base square to the base observation star
                    // in world coordinates.
                    Vector3f baseStarRay = EarthShape.rayToStar(s.baseSquare, baseObservation);

                    // Get the visual separation angle.  This is a float
                    // in order to avoid cluttering the 3D display with
                    // too many digits.
                    float sep;
                    if (this.earthShapeFrame.assumeInfiniteStarDistance) {
                        // Angle between *rays*.
                        sep = (float)starRay.separationAngleDegrees(baseStarRay);
                    }
                    else {
                        // Get info about visual separation of *lines*.
                        Vector3d.ClosestApproach ca = EarthShape.getModifiedClosestApproach(
                            s.center, starRay,
                            s.baseSquare.center, baseStarRay);
                        sep = (float)ca.separationAngleDegrees;

                        if (ca.line1Closest != null && ca.line2Closest != null) {
                            // Draw a connecting line showing the location of
                            // minimum separation.
                            glMaterialColor3f(gl, 1, 0, 0);     // Red.
                            gl.glBegin(GL.GL_LINES);
                            glVertex3d(gl, ca.line1Closest);
                            glVertex3d(gl, ca.line2Closest);
                            gl.glEnd();
                        }
                    }

                    // Add separation angle to the label.
                    starLabel += " ("+sep+")";
                }
            }

            // Label the star for the active square.
            if (s.showAsActive) {
                this.worldLabels.add(new CoordinateLabel(starRayDirection, starLabel));
            }
        }
    }

    /** Draw a thin dotted line from 'pt' to the XZ plane. */
    private void drawDottedLineToXZPlane(GL2 gl, Vector3f pt)
    {
        gl.glPushAttrib(GL2.GL_ALL_ATTRIB_BITS);
        gl.glLineWidth(1);
        gl.glEnable(GL2.GL_LINE_STIPPLE);
        gl.glLineStipple(1, (short)0xF000);

        // What Y coordinate is on the XZ plane?
        float yCoord = 0;
        if (this.drawActiveSquareAtOrigin &&
            this.earthShapeFrame.getActiveSquare() != null)
        {
            // Hack: We translated the coordinate system so the XZ plane
            // intersects this square's center, so use its Y coordinate.
            yCoord = this.earthShapeFrame.getActiveSquare().center.y();
        }

        gl.glBegin(GL.GL_LINES);
        glVertex3f(gl, pt);
        glVertex3f(gl, new Vector3f(pt.x(), yCoord, pt.z()));
        gl.glEnd();

        gl.glPopAttrib();
    }

    /** Draw one of a number scenes for an animation I am creating,
      * depending on the 'activeSquareAnimationState'. */
    private void drawActiveSquareAnimationState(GL2 gl, SurfaceSquare square)
    {
        SurfaceSquare base = square.baseSquare;
        if (base == null) {
            return;     // Defensive.
        }

        int state = this.activeSquareAnimationState;
        if (state == 0) {
            // Draw nothing.
        }

        // Compute and draw the cross product of this square's
        // Dubhe observation and that of the base square.
        Vector3f derivedDubhe =
            EarthShape.rayToStar(square, square.starObs.get("Dubhe"));
        Vector3f baseDubhe =
            EarthShape.rayToStar(base, base.starObs.get("Dubhe"));
        Vector3f derivedCrossBaseDubhe = derivedDubhe.cross(baseDubhe);
        if (state == 1) {
            this.drawRayFromSquare(gl, square, derivedCrossBaseDubhe, 1, 0, 0);
            logOnce("derivedCrossBaseDubhe: "+derivedCrossBaseDubhe);
        }

        // Compute rot1, an angle and axis to rotate 'derivedDubhe' on top
        // of 'baseDubhe'.
        double derivedCrossBaseDubheLen = derivedCrossBaseDubhe.length();
        double rot1Angle = FloatUtil.asinDeg(derivedCrossBaseDubheLen);
        if (state == 2) {
            logOnce("derivedCrossBaseDubheLen: "+derivedCrossBaseDubheLen);
            logOnce("rot1Angle: "+rot1Angle);
        }

        Vector3f rot1Axis = new Vector3f(1, 0, 0);   // Harmless for degenerate case.
        if (derivedCrossBaseDubheLen != 0) {
            rot1Axis = derivedCrossBaseDubhe.times((float)(1/derivedCrossBaseDubheLen));
        }

        if (state == 2) {
            logOnce("rot1Axis: "+rot1Axis);
            this.drawRayFromSquare(gl, square, rot1Axis, 1, 0, 0);

            glMaterialColor3f(gl, 1, 0, 0);
            this.drawAngleArc(gl, square.center, derivedDubhe, baseDubhe, 0.5f);
        }

        Matrix3f rot1 = Matrix3f.rotateDeg(rot1Angle, rot1Axis);
        if (state == 2) {
            logOnce("rot1: "+rot1);
        }

        if (state == 3) {
            this.earthShapeFrame.beginAnimatedRotation(rot1Angle, rot1Axis, 3 /*sec*/);
            this.activeSquareAnimationState = 4;
        }

        if (state == 4) {
            // Animating.
        }

        if (state == 5) {
            // Draw a unit circle in the plane perpendicular to 'baseDubhe'.
            // This is the plane in which we will rotate to align Sirius.
            glMaterialColor3f(gl, 0, 0, 1);
            this.drawPerpendiculuarCircle(gl, square.center, baseDubhe, 1.0f, 80);
        }
    }

    /** Draw an arc centered at 'center', with 'radius', that goes
      * from direction 'dir1' to 'dir2'. */
    private void drawAngleArc(GL2 gl, Vector3f center, Vector3f dir1, Vector3f dir2, float radius)
    {
        // Ensure both inputs are normal.
        dir1 = dir1.normalize();
        dir2 = dir2.normalize();

        // Compute axis and angle to rotate 'dir1' to 'dir2'.
        Vector3f axis = dir1.cross(dir2);
        if (axis.isZero()) {
            return;
        }
        double degrees = FloatUtil.asinDeg(axis.length());
        axis = axis.normalize();

        // Draw a 10-segment arc of 'radius' from 'dir1' to 'dir2'.
        gl.glBegin(GL.GL_LINE_STRIP);
        Vector3f dir = dir1.times(radius);
        for (int i=0; i <= 10; i++) {
            glVertex3f(gl, center.plus(dir.rotateDeg(degrees * i / 10.0, axis)));
        }
        gl.glEnd();
    }

    /** Log a message just once per animation state, to avoid spamming
      * the console with the same information over and over. */
    private void logOnce(String msg)
    {
        if (this.lastAnimationStateLogged == this.activeSquareAnimationState) {
            // Do nothing, we already logged this message.
        }
        else {
            log(msg);
        }
    }

    /** Draw 'ray' from the center of 's' in given color. */
    private void drawRayFromSquare(GL2 gl, SurfaceSquare s, Vector3f ray,
                                   float r, float g, float b)
    {
        glMaterialColor3f(gl, r, g, b);

        gl.glBegin(GL.GL_LINES);
        glVertex3f(gl, s.center);
        glVertex3f(gl, s.center.plus(ray));
        gl.glEnd();

        this.drawDottedLineToXZPlane(gl, s.center.plus(ray));
    }

    /** Draw a circle with 'radius' and 'center', in a plane
      * perpendicular to 'axis', which should be a unit vector. */
    private void drawPerpendiculuarCircle(GL2 gl, Vector3f center, Vector3f axis,
                                          float radius, int numSegments)
    {
        // Obtain a vector that is orthogonal to 'axis'.
        Vector3f orthogonal = axis.orthogonalVector();

        // Sweep it around the axis.
        gl.glBegin(GL.GL_LINE_LOOP);
        for (int i=0; i < numSegments; i++) {
            double angle = i * 360.0 / numSegments;
            glVertex3f(gl, center.plus(orthogonal.rotateDeg(angle, axis)));
        }
        gl.glEnd();
    }

    /** Add a vertex from a Vector3f. */
    private static void glVertex3f(GL2 gl, Vector3f pt)
    {
        gl.glVertex3fv(pt.getArray(), 0);
    }

    /** Add two vertices from Vector3fs. */
    private static void glVertex3f2(GL2 gl, Vector3f p1, Vector3f p2)
    {
        glVertex3f(gl, p1);
        glVertex3f(gl, p2);
    }

    /** Add a vertex from a Vector3d. */
    private static void glVertex3d(GL2 gl, Vector3d pt)
    {
        gl.glVertex3dv(pt.getArray(), 0);
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
        this.lastPhysicsUpdateMillis = newMillis;

        // Update camera velocity based on move keys.
        boolean moving = false;
        for (MoveDirection md : MoveDirection.values()) {
            if (this.moveKeys[md.ordinal()]) {
                moving = true;

                // Rotate the direction of 'md' according to the
                // current camera azimuth.
                Vector3f rd = md.direction.rotateDeg(this.cameraAzimuthDegrees,
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

        this.earthShapeFrame.updatePhysics(elapsedSeconds);

        this.earthShapeFrame.updateUIState();
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

    public void turnOffAllStarRays()
    {
        for (SurfaceSquare s : this.surfaceSquares) {
            s.drawStarRays = false;
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
            return;
        }
    }

    /** Begin controlling camera like a first person shooter. */
    public void enterFPSMode()
    {
        this.fpsCameraMode = true;

        // When transition into FPS mode, move the mouse
        // to the center and hide it.
        this.centerMouse();
        this.setCursor(this.blankCursor);

        // Only run the animator thread in FPS mode.
        this.animator.start();

        this.earthShapeFrame.updateUIState();
    }

    /** Leave the first-person-shooter camera movement mode. */
    public void exitFPSMode()
    {
        this.fpsCameraMode = false;

        // Un-hide the mouse cursor.
        this.setCursor(null);

        // And stop the animator.
        this.animator.stop();

        // Also kill any latent camera motion.
        this.cameraVelocity = new Vector3f(0,0,0);

        this.earthShapeFrame.updateUIState();
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
    public void mouseEntered(MouseEvent ev) {
        //log("mouse entered");
        if (this.fpsCameraMode) {
            // Ignore the next mouse move since it is coming
            // from another window.
            this.ignoreOneMouseMove = true;
        }
    }

    @Override
    public void mouseExited(MouseEvent ev) {
        //log("mouse exited");
    }

    @Override
    public void mousePressed(MouseEvent ev) {
        log("mouse pressed: ev="+ev);
        if (ev.getButton() == MouseEvent.BUTTON1) {
            if (!this.fpsCameraMode) {
                this.enterFPSMode();
                return;
            }

            this.earthShapeFrame.setActiveSquare(this.rayCastToSquare());
        }
    }

    /** Return the surface square that the camera is looking at. */
    private SurfaceSquare rayCastToSquare()
    {
        Vector3f look = this.cameraLookVector();

        // Closest intersecting square so far, and its distance.
        SurfaceSquare bestSquare = null;
        float bestDistance = 0;

        // Check all squares to see which ones intersect the look
        // vector, and of those, which is closest.
        for (SurfaceSquare s : this.surfaceSquares) {
            // Camera to that surface's center.  (For calculating 'toPlane',
            // any point on the plane will do, since they will all yield
            // the same projection onto the surface normal.)
            Vector3f toCenter = s.center.minus(this.cameraPosition);

            // Camera to closest point on surface plane.
            Vector3f toPlane = toCenter.projectOntoUnitVector(s.up);

            // Camera to closest point, but only as far as one unit
            // as look vector goes.
            Vector3f unitLookToPlane = look.projectOntoUnitVector(s.up);
            if (unitLookToPlane.dot(toPlane) < 1e-10f) {
                // Surface is close to parallel, or is behind camera.
                continue;
            }

            // Number of look vectors needed to reach the plane.
            float lookMultiple = (float)(toPlane.length() / unitLookToPlane.length());
            if (lookMultiple < FRONT_CLIP_DISTANCE) {
                // Square is behind the clipping plane.
                continue;
            }

            // Camera to intersection point along look vector.
            Vector3f cameraToIntersection = look.times(lookMultiple);

            // Intersection point of look vector and plane.
            Vector3f intersection = this.cameraPosition.plus(cameraToIntersection);

            // From square center to that intersection.
            Vector3f c2i = intersection.minus(s.center);

            // Square radius in world units.
            float radius = s.sizeKm * SPACE_UNITS_PER_KM / 2.0f;

            // Check if distance along square's North or East exceeds the
            // square's radius.
            if (Math.abs(c2i.dot(s.north)) > radius) {
                continue;
            }
            Vector3f east = s.north.cross(s.up);
            if (Math.abs(c2i.dot(east)) > radius) {
                continue;
            }

            // New best?
            float distance = (float)cameraToIntersection.length();
            if (bestSquare == null || distance < bestDistance) {
                bestSquare = s;
                bestDistance = distance;
            }
        }

        return bestSquare;
    }

    /** Get the camera's look direction as a unit vector. */
    private Vector3f cameraLookVector()
    {
        // Start looking down the -Z axis.
        Vector3f look = new Vector3f(0, 0, -1);

        // Yaw.
        Vector3f up = new Vector3f(0, 1, 0);
        look = look.rotateDeg(this.cameraAzimuthDegrees, up);

        // Pitch around a vector pointing to the right from the camera.
        Vector3f right = look.cross(up);
        look = look.rotateDeg(this.cameraPitchDegrees, right);

        return look;
    }

    /** Move the mouse to the center of the component where the given
      * event originated, and also return that center point in screen
      * coordinates. */
    private Point centerMouse()
    {
        // Calculate the screen coordinates of the center of the
        // clicked component.
        Point absComponentLoc = this.getLocationOnScreen();
        Dimension comDim = this.getSize();
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
            if (!this.animator.isAnimating()) {
                // For reasons I do not understand, if I am in FPS
                // mode, then press "T" to rebuild the surface, when
                // it completes, the animator has stopped, even though
                // I did not tell it to.  It seems to suffice to simply
                // start it again.
                log("restarting animator after dialog close");
                this.animator.start();
            }

            // Get the screen coordinate where mouse is now.
            Point mouseAbsLoc = ev.getLocationOnScreen();

            // Move the mouse to the center of the clicked component,
            // and get that location.
            Point c = this.centerMouse();

            if (this.ignoreOneMouseMove) {
                // The movement here is due to coming from another
                // window, so do not change the camera angle.
                this.ignoreOneMouseMove = false;
                return;
            }

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
            if (this.invertHorizontalCameraMovement) {
                dx = -dx;
            }
            float newAz = this.cameraAzimuthDegrees - dx * CAMERA_HORIZONTAL_SENSITIVITY;

            // Normalize the azimuth to [0,360).
            this.cameraAzimuthDegrees = FloatUtil.modulusf(newAz, 360);

            // Rotate the pitch.
            if (this.invertVerticalCameraMovement) {
                dy = -dy;
            }
            float newPitch = this.cameraPitchDegrees - dy * CAMERA_VERTICAL_SENSITIVITY;
            this.cameraPitchDegrees = FloatUtil.clampf(newPitch, -90, 90);

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
            sb.append(", FPS mode (ESC to exit)");
        }
        return sb.toString();
    }
}

// EOF
