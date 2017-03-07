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
import java.util.ArrayList;
import java.util.HashMap;

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

    /** Earth surface texture.  Valid between 'init' and 'dispose'.
      * The texture I plan to load, EarthMap.jpg, comes from
      * http://wennberg-wiki.caltech.edu/.  It is 2500x1250 and
      * is an equirectangular projection.  The top is 90N, bottom
      * is 90S, left is 180W, right is 180E. */
    private Texture earthMapTexture;

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

    /** Units in 3D space coordinates per km in surface being mapped. */
    private static final float SPACE_UNITS_PER_KM = 0.001f;

    /** Squares of the surface we have built. */
    private ArrayList<SurfaceSquare> surfaceSquares = new ArrayList<SurfaceSquare>();

    /** If true, draw surfaces using the abstract compass texture.
      * Otherwise, draw them using the EarthMap texture. */
    private boolean drawCompasses = true;

    /** Current aspect ratio: canvas width divided by canvas height
      * in pixels.  (Really, aspect ratio ought to reflect physical
      * size ratio, but I will assume pixels are square; fortunately,
      * they usually are.) */
    private float aspectRatio = 1.0f;

    /** Desired distance between camera and front clipping plane. */
    private static final float FRONT_CLIP_DISTANCE = 0.1f;

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

        //this.buildEarthSurfaceWithLatLong();
        //this.randomWalkEarthSurface();
        this.buildEarthSurfaceFromStarData();

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
        log("loading textures");
        this.compassTexture = loadTexture(gl, "textures/compass-rose.png", TextureIO.PNG);
        this.earthMapTexture = loadTexture(gl, "textures/EarthMap.jpg", TextureIO.JPG);

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

        // Use thicker lines so they will show up better if/when I
        // make a screenshot recording.
        gl.glLineWidth(2);

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
        }

        this.drawEarthSurface(gl);

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

    /** Build a portion of the Earth's surface.  Adds squares to
      * 'surfaceSquares'.  This works by iterating over latitude
      * and longitude pairs and assuming a spherical Earth. */
    private void buildEarthSurfaceWithLatLong()
    {
        log("building Earth");

        // Size of squares to build, in km.
        float sizeKm = 1000;

        // Start with an arbitrary square centered at the origin
        // the 3D space, and at SF, CA in the real world.
        float startLatitude = 38;     // 38N
        float startLongitude = -122;  // 122W
        SurfaceSquare startSquare = new SurfaceSquare(
            new Vector3f(0,0,0),      // center
            new Vector3f(0,0,-1),     // north
            new Vector3f(0,1,0),      // up
            sizeKm,
            startLatitude,
            startLongitude);
        this.addSurfaceSquare(startSquare);

        // Outer loop 1: Walk North as far as we can.
        SurfaceSquare outer = startSquare;
        for (float latitude = startLatitude;
             latitude < 90;
             latitude += 9)
        {
            // Go North another step.
            outer = this.addAdjacentSquare(outer, latitude, startLongitude);

            // Inner loop: Walk East until we get back to
            // the same longitude.
            float longitude = startLongitude;
            float prevLongitude = longitude;
            SurfaceSquare inner = outer;
            while (true) {
                inner = this.addAdjacentSquare(inner, latitude, longitude);
                if (prevLongitude < outer.longitude &&
                                    outer.longitude <= longitude) {
                    break;
                }
                prevLongitude = longitude;
                longitude = FloatUtil.modulus2(longitude+9, -180, 180);
            }
        }

        // Outer loop 2: Walk South as far as we can.
        outer = startSquare;
        for (float latitude = startLatitude - 9;
             latitude > -90;
             latitude -= 9)
        {
            // Go North another step.
            outer = this.addAdjacentSquare(outer, latitude, startLongitude);

            // Inner loop: Walk East until we get back to
            // the same longitude.
            float longitude = startLongitude;
            float prevLongitude = longitude;
            SurfaceSquare inner = outer;
            while (true) {
                inner = this.addAdjacentSquare(inner, latitude, longitude);
                if (prevLongitude < outer.longitude &&
                                    outer.longitude <= longitude) {
                    break;
                }
                prevLongitude = longitude;
                longitude = FloatUtil.modulus2(longitude+9, -180, 180);
            }
        }

        log("finished building Earth; nsquares="+this.surfaceSquares.size());
    }

    /** Build the surface by walking randomly from a starting location. */
    private void randomWalkEarthSurface()
    {
        log("building Earth by random walk");

        // Size of squares to build, in km.
        float sizeKm = 1000;

        // Start with an arbitrary square centered at the origin
        // the 3D space, and at SF, CA in the real world.
        float startLatitude = 38;     // 38N
        float startLongitude = -122;  // 122W
        SurfaceSquare startSquare = new SurfaceSquare(
            new Vector3f(0,0,0),      // center
            new Vector3f(0,0,-1),     // north
            new Vector3f(0,1,0),      // up
            sizeKm,
            startLatitude,
            startLongitude);
        this.addSurfaceSquare(startSquare);

        SurfaceSquare square = startSquare;
        for (int i=0; i < 1000; i++) {
            // Select a random change in latitude and longitude
            // of about 10 degrees.
            float deltaLatitude = (float)(Math.random() * 12 - 6);
            float deltaLongitude = (float)(Math.random() * 12 - 6);

            // Walk in that direction, keeping latitude and longitude
            // within their usual ranges.  Also stay away from the poles
            // since the rounding errors cause problems there.
            square = this.addAdjacentSquare(square,
                FloatUtil.clamp(square.latitude + deltaLatitude, -80, 80),
                FloatUtil.modulus2(square.longitude + deltaLongitude, -180, 180));
        }

        log("finished building Earth; nsquares="+this.surfaceSquares.size());
    }

    /** Given square 'old', add an adjacent square at the given
      * latitude and longitude.  The relative orientation of the
      * new square will determined using the latitude and longitude,
      * at this stage as a proxy for star observation data.  The size
      * will be the same as the old square.
      * This works best when the squares are nearby since this
      * procedure assumes the surface is locally flat. */
    private SurfaceSquare addAdjacentSquare(
        SurfaceSquare old,
        float newLatitude,
        float newLongitude)
    {
        // Calculate local East for 'old'.
        Vector3f oldEast = old.north.cross(old.up).normalize();

        // Calculate celestial North for 'old', which is given by
        // the latitude plus geographic North.
        Vector3f celestialNorth =
            old.north.rotate(old.latitude, oldEast);

        // Get lat/long deltas.
        float deltaLatitude = newLatitude - old.latitude;
        float deltaLongitude = FloatUtil.modulus2(
            newLongitude - old.longitude, -180, 180);

        // If we didn't move, just return the old square.
        if (deltaLongitude == 0 && deltaLatitude == 0) {
            return old;
        }

        // What we want now is to first rotate Northward
        // around local East to account for change in latitude, then
        // Eastward around celestial North for change in longitude.
        Vector3f firstRotation = oldEast.times(-deltaLatitude);
        Vector3f secondRotation = celestialNorth.times(deltaLongitude);

        // But then we want to express the composition of those as a
        // single rotation vector in order to call the general routine.
        Vector3f combined = Vector3f.composeRotations(firstRotation, secondRotation);

        // Now call into the general procedure for adding a square
        // given the proper relative orientation rotation.
        return addRotatedAdjacentSquare(old, newLatitude, newLongitude, combined);
    }

    /** Build a surface using star data rather than any presumed
      * size and shape. */
    private void buildEarthSurfaceFromStarData()
    {
        log("building Earth using star data");

        // Begin by grabbing the hardcoded star data.
        StarData[] starData = StarData.getHardcodedData();

        // Size of squares to build, in km.
        float sizeKm = 1000;

        // Work through the data in order, assuming that observations
        // for a given location are contiguous, and that the data appears
        // in a good walk order.
        float curLatitude = 0;
        float curLongitude = 0;
        SurfaceSquare curSquare = null;
        for (StarData sd : starData) {
            // Skip forward to next location.
            if (curSquare != null && sd.latitude == curLatitude && sd.longitude == curLongitude) {
                continue;
            }
            log("buildEarth: building lat="+sd.latitude+" long="+sd.longitude);

            if (curSquare == null) {
                // First square will be placed at the 3D origin with
                // its North pointed along the -Z axis.
                curSquare = new SurfaceSquare(
                    new Vector3f(0,0,0),      // center
                    new Vector3f(0,0,-1),     // north
                    new Vector3f(0,1,0),      // up
                    sizeKm,
                    sd.latitude,
                    sd.longitude);
                this.addSurfaceSquare(curSquare);
            }
            else {
                // Calculate a rotation vector that will best align
                // the current square's star observations with the
                // next square's.
                Vector3f rot = calcRequiredRotation(curSquare, starData, sd.latitude, sd.longitude);
                if (rot == null) {
                    log("buildEarth: could not place next square!");
                    return;    // give up
                }

                // Make the new square from the old and the computed
                // change in orientation.
                curSquare =
                    addRotatedAdjacentSquare(curSquare, sd.latitude, sd.longitude, rot);
            }

            this.addMatchingData(curSquare, starData);

            curLatitude = sd.latitude;
            curLongitude = sd.longitude;
        }
    }

    /** Add a square adjacent to 'old', positioned at the given latitude
      * and longitude, with orientation changed by 'rotation'. */
    private SurfaceSquare addRotatedAdjacentSquare(
        SurfaceSquare old,
        float newLatitude,
        float newLongitude,
        Vector3f rotation)
    {
        // Calculate local East for 'old'.
        Vector3f oldEast = old.north.cross(old.up).normalize();

        // Calculate the angle along the spherical Earth subtended
        // by the arc from 'old' to the new coordinates.
        float arcAngleDegrees = FloatUtil.sphericalSeparationAngle(
            old.longitude, old.latitude,
            newLongitude, newLatitude);

        // Calculate the distance along the surface that separates
        // these points by using the fact that there are 111 km per
        // degree of arc.
        float distanceKm = (float)(111.0 * arcAngleDegrees);

        // Get lat/long deltas.
        float deltaLatitude = newLatitude - old.latitude;
        float deltaLongitude = FloatUtil.modulus2(
            newLongitude - old.longitude, -180, 180);

        // If we didn't move, just return the old square.
        if (deltaLongitude == 0 && deltaLatitude == 0) {
            return old;
        }

        // Compute the new orientation vectors by rotating
        // the old ones by the given amount.
        Vector3f newNorth = old.north.rotateAA(rotation);
        Vector3f newUp = old.up.rotateAA(rotation);
        Vector3f newEast = newNorth.cross(newUp).normalize();

        // Calculate the average compass heading, in degrees North
        // of East, used to go from the old
        // location to the new.  This is rather crude because it
        // doesn't properly account for the fact that longitude
        // lines get closer together near the poles.
        float headingDegrees = FloatUtil.radiansToDegreesf(
            (float)Math.atan2(deltaLatitude, deltaLongitude));

        // For both old and new, calculate a unit vector for the
        // travel direction.  Then average them to approximate the
        // average travel direction.
        Vector3f oldTravel = oldEast.rotate(headingDegrees, old.up);
        Vector3f newTravel = newEast.rotate(headingDegrees, newUp);
        Vector3f avgTravel = oldTravel.plus(newTravel).normalize();

        // Calculate the new square's center as 'distance' units
        // along 'avgTravel'.
        Vector3f newCenter = old.center.plus(
            avgTravel.times(distanceKm * SPACE_UNITS_PER_KM));

        // Make the new square and add it to the list.
        SurfaceSquare ret = new SurfaceSquare(
            newCenter, newNorth, newUp,
            old.sizeKm,
            newLatitude,      // did not change
            newLongitude);
        this.addSurfaceSquare(ret);

        return ret;
    }

    /** Add to 'square.starData' all entries of 'starData' that have
      * the same latitude and longitude. */
    private void addMatchingData(SurfaceSquare square, StarData[] starData)
    {
        for (StarData sd : starData) {
            if (square.latitude == sd.latitude &&
                square.longitude == sd.longitude)
            {
                square.starData.add(sd);
            }
        }
    }

    /** Compare star data for 'startSquare' and for the given new
      * latitude and longitude.  Return a rotation vector that will
      * transform the orientation of 'startSquare' to match the
      * best surface for a new square at the new location.  The
      * vector's length is the amount of rotation in degrees.
      *
      * Returns null if there are not enough stars in common. */
    private Vector3f calcRequiredRotation(
        SurfaceSquare startSquare,
        StarData[] starData,
        float newLatitude,
        float newLongitude)
    {
        // Set of stars visible at the start and end squares and
        // above 20 degrees above the horizon.
        HashMap<String, Vector3f> startStars =
            getVisibleStars(starData, startSquare.latitude, startSquare.longitude);
        HashMap<String, Vector3f> endStars =
            getVisibleStars(starData, newLatitude, newLongitude);

        // Current best rotation and average difference.
        Vector3f currentRotation = new Vector3f(0,0,0);

        // Iteratively refine the current rotation by computing the
        // average correction rotation and applying it until that
        // correction drops below a certain threshold.
        for (int iterationCount = 0; iterationCount < 100; iterationCount++) {
            // Accumulate the vector sum of all the rotation difference
            // vectors as well as the max length.
            Vector3f diffSum = new Vector3f(0,0,0);
            float maxDiffLength = 0;
            int diffCount = 0;

            for (HashMap.Entry<String, Vector3f> e : startStars.entrySet()) {
                String starName = e.getKey();
                Vector3f startVector = e.getValue();

                Vector3f endVector = endStars.get(starName);
                if (endVector == null) {
                    continue;
                }

                // Calculate a difference rotation vector from the
                // rotated start vector to the end vector.
                Vector3f rot = startVector.rotateAA(currentRotation)
                                          .rotationToBecome(endVector);

                // Accumulate it.
                diffSum = diffSum.plus(rot);
                maxDiffLength = (float)Math.max(maxDiffLength, rot.length());
                diffCount++;
            }

            if (diffCount < 2) {
                log("reqRot: not enough stars");
                return null;
            }

            // Calculate the average correction rotation.
            Vector3f avgDiff = diffSum.times(1.0f / diffCount);

            // If the correction angle is small enough, stop.  For any set
            // of observations, we should be able to drive the average
            // difference arbitrarily close to zero (this is like finding
            // the centroid, except in spherical rather than flat space).
            // The real question is whether the *maximum* difference is
            // large enough to indicate that the data is inconsistent.
            if (avgDiff.length() < 0.001) {
                log("reqRot finished: iters="+iterationCount+
                    " avgDiffLen="+avgDiff.length()+
                    " maxDiffLength="+maxDiffLength+
                    " diffCount="+diffCount);
                if (maxDiffLength > 0.2) {
                    // For the data I am working with, I estimate it is
                    // accurate to within 0.2 degrees.  Consequently,
                    // there should not be a max difference that large.
                    log("reqRot: WARNING: maxDiffLength greater than 0.2");
                }
                return currentRotation;
            }

            // Otherwise, apply it to the current rotation and
            // iterate again.
            currentRotation = currentRotation.plus(avgDiff);
        }

        log("reqRot: hit iteration limit!");
        return currentRotation;
    }

    /** For every star in 'starData' that matches 'latitude' and
      * 'longitude', and has an elevation of at least 20 degrees,
      * add it to a map from star name to azEl vector. */
    private HashMap<String, Vector3f> getVisibleStars(
        StarData[] starData,
        float latitude,
        float longitude)
    {
        HashMap<String, Vector3f> ret = new HashMap<String, Vector3f>();

        for (StarData sd : starData) {
            if (sd.latitude == latitude &&
                sd.longitude == longitude &&
                sd.elevation >= 20)
            {
                ret.put(sd.name,
                    azimuthElevationToVector(sd.azimuth, sd.elevation));
            }
        }

        return ret;
    }

    /** Convert a pair of azimuth and elevation, in degrees, to a unit
      * vector that points in the same direction, in a coordinate system
      * where 0 degrees azimuth is -Z, East is +X, and up is +Y. */
    private Vector3f azimuthElevationToVector(float azimuth, float elevation)
    {
        // Start by pointing a vector at 0 degrees.
        Vector3f v = new Vector3f(0, 0, -1);

        // Now rotate it up to align with elevation.
        v = v.rotate(elevation, new Vector3f(1, 0, 0));

        // Now rotate it East to align with azimuth.
        v = v.rotate(azimuth, new Vector3f(0, 1, 0));

        return v;
    }

    /** Add a square to 'surfaceSquares'. */
    private void addSurfaceSquare(SurfaceSquare s)
    {
        this.surfaceSquares.add(s);
        log("added square: "+s);
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
        gl.glDisable(GL.GL_TEXTURE_2D);
        gl.glBegin(GL.GL_LINES);
        glMaterialColor3f(gl, 0.5f, 0.5f, 0);    // Dark yellow
        gl.glNormal3f(0,1,0);
        gl.glVertex3fv(s.center.getArray(), 0);
        gl.glVertex3fv(s.center.plus(s.up).getArray(), 0);
        gl.glEnd();
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

        switch (ev.getKeyCode()) {
            case KeyEvent.VK_C:
                this.drawCompasses = !this.drawCompasses;
                this.setStatusLabel();
                this.glCanvas.display();
                break;

            case KeyEvent.VK_L:
                this.surfaceSquares.clear();
                this.buildEarthSurfaceWithLatLong();
                this.glCanvas.display();
                break;

            case KeyEvent.VK_R:
                this.surfaceSquares.clear();
                this.randomWalkEarthSurface();
                this.glCanvas.display();
                break;

            case KeyEvent.VK_T:
                this.surfaceSquares.clear();
                this.buildEarthSurfaceFromStarData();
                this.glCanvas.display();
                break;

            default:
                break;
        }
    }

    /** Set the status label text to reflect other state variables. */
    private void setStatusLabel()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("camera="+this.cameraPosition);
        sb.append(", az="+this.cameraAzimuthDegrees);
        sb.append(", pch="+this.cameraPitchDegrees);
        sb.append(", tex="+(this.drawCompasses? "compass" : "earth"));
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
