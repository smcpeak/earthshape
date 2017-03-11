// EarthShape.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import com.jogamp.opengl.GLCapabilities;

import util.FloatUtil;
import util.Vector3f;
import util.swing.ModalDialog;

/** This application demonstrates a procedure for inferring the shape
  * of a surface (such as the Earth) based on the observed locations of
  * stars from various locations at a fixed point in time.
  *
  * This class, EarthShape, manages UI components external to the 3D
  * display, such as the menu and status bars.  It also contains all of
  * the code to construct the virtual 3D map using various algorithms.
  * The 3D display, along with its camera controls, is in EarthMapCanvas. */
public class EarthShape
    // This is a Swing window.
    extends JFrame
{
    // --------- Constants ----------
    /** AWT boilerplate generated serial ID. */
    private static final long serialVersionUID = 3903955302894393226L;

    /** Size of the first constructed square, in kilometers.  The
      * displayed size is then determined by the map scale, which
      * is normally 1 unit per 1000 km. */
    private static final float INITIAL_SQUARE_SIZE_KM = 1000;

    // ---------- Class variables ----------
    /** The thread that last issued a 'log' command.  This is used to
      * only log thread names when there is interleaving. */
    private static Thread lastLoggedThread = null;

    // ---------- Instance variables ----------
    // ---- Star Information ----
    /** Some star observations I manually gathered. */
    private StarObservation[] manualStarObservations = StarObservation.getManualObservations();

    /** Some star celestial coordinates, which can be used to derive
      * synthetic star observations. */
    private StarCatalog[] starCatalog = StarCatalog.makeCatalog();

    // ---- Interactive surface construction state ----
    /** The square we will build upon when the next square is added.
      * This may be null. */
    private SurfaceSquare activeSquare;

    // ---- Widgets ----
    /** Canvas showing the Earth surface built so far. */
    private EarthMapCanvas emCanvas;

    /** Widget to show various state variables such as camera position. */
    private JLabel statusLabel;

    /** Selected square info panel on right side. */
    private InfoPanel infoPanel;

    /** Menu item to toggle 'drawCompasses'. */
    private JCheckBoxMenuItem drawCompassesCBItem;

    /** Menu item to toggle 'drawSurfaceNormals'. */
    private JCheckBoxMenuItem drawSurfaceNormalsCBItem;

    /** Menu item to toggle 'drawCelestialNorth'. */
    private JCheckBoxMenuItem drawCelestialNorthCBItem;

    /** Menu item to toggle 'drawStarRays'. */
    private JCheckBoxMenuItem drawStarRaysCBItem;

    // ---------- Methods ----------
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

        //this.addKeyListener(this);

        this.setSize(950, 800);
        this.setLocationByPlatform(true);

        this.setupJOGL();

        this.buildEarthSurfaceFromStarData();

        this.buildMenuBar();

        // Status bar on bottom.
        this.statusLabel = new JLabel();
        this.add(this.statusLabel, BorderLayout.SOUTH);

        // Info panel on right.
        this.add(this.infoPanel = new InfoPanel(), BorderLayout.EAST);

        this.updateUIState();
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

        // Build the object that will show the surface.
        this.emCanvas = new EarthMapCanvas(this, caps);

        // Associate the canvas with 'this' window.
        this.add(this.emCanvas, BorderLayout.CENTER);
    }

    /** Build the menu bar and attach it to 'this'. */
    private void buildMenuBar()
    {
        JMenuBar menuBar = new JMenuBar();
        this.setJMenuBar(menuBar);

        // Used keys:
        //   a - Move camera left
        //   b
        //   c - Toggle compass
        //   d - Move camera right
        //   e
        //   f
        //   g
        //   h
        //   i
        //   j
        //   k
        //   l - Build spherical Earth
        //   m - Add adjacent square to surface
        //   n - Build new surface
        //   o
        //   p - Toggle star rays for active square
        //   q
        //   r - Build with random walk
        //   s - Move camera backward
        //   t - Build full Earth with star data
        //   u
        //   v
        //   w - Move camera forward
        //   x
        //   y
        //   z - Move camera down
        //   Space - Move camera up
        //   , - Select previous square
        //   . - Select next square

        JMenu fileMenu = new JMenu("File");
        addMenuItem(fileMenu, "Exit", null, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                EarthShape.log("exit menu item invoked");
                EarthShape.this.dispose();
            }
        });
        menuBar.add(fileMenu);

        JMenu drawMenu = new JMenu("Draw");
        this.drawCompassesCBItem =
            addCBMenuItem(drawMenu, "Draw squares with compasses", KeyStroke.getKeyStroke('c'),
                this.emCanvas.drawCompasses,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.toggleDrawCompasses();
                    }
                });
        this.drawSurfaceNormalsCBItem =
            addCBMenuItem(drawMenu, "Draw surface normals", null, this.emCanvas.drawSurfaceNormals,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.toggleDrawSurfaceNormals();
                    }
                });
        this.drawCelestialNorthCBItem =
            addCBMenuItem(drawMenu, "Draw celestial North", null, this.emCanvas.drawCelestialNorth,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.toggleDrawCelestialNorth();
                    }
                });
        this.drawStarRaysCBItem =
            addCBMenuItem(drawMenu, "Draw star rays for active square",
                KeyStroke.getKeyStroke('p'),
                this.activeSquareDrawsStarRays(),
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.toggleDrawStarRays();
                    }
                });
        menuBar.add(drawMenu);

        JMenu buildMenu = new JMenu("Build");
        addMenuItem(buildMenu, "Build Earth using star data and no assumed shape",
            KeyStroke.getKeyStroke('t'),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.buildEarthSurfaceFromStarData();
                }
            });
        addMenuItem(buildMenu, "Build complete Earth using assumed sphere",
            KeyStroke.getKeyStroke('l'),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.buildEarthSurfaceWithLatLong();
                }
            });
        addMenuItem(buildMenu, "Build partial Earth using assumed sphere and random walk",
            KeyStroke.getKeyStroke('r'),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.randomWalkEarthSurface();
                }
            });
        addMenuItem(buildMenu, "Start a new surface using star data",
            KeyStroke.getKeyStroke('n'),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.startNewSurface();
                }
            });
        addMenuItem(buildMenu, "Add another square to the surface",
            KeyStroke.getKeyStroke('m'),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.buildNextSquare();
                }
            });
        menuBar.add(buildMenu);

        JMenu selectMenu = new JMenu("Select");
        addMenuItem(selectMenu, "Select previous square",
            KeyStroke.getKeyStroke(','),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.selectNextSquare(false /*forward*/);
                }
            });
        addMenuItem(selectMenu, "Select next square",
            KeyStroke.getKeyStroke('.'),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.selectNextSquare(true /*forward*/);
                }
            });
        menuBar.add(selectMenu);
    }

    /** Make a new menu item and add it to 'menu' with the given
      * label and listener. */
    private static void addMenuItem(JMenu menu, String itemLabel, KeyStroke accelerator,
                                    ActionListener listener)
    {
        JMenuItem item = new JMenuItem(itemLabel);
        item.addActionListener(listener);
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        menu.add(item);
    }

    private static JCheckBoxMenuItem addCBMenuItem(JMenu menu, String itemLabel, KeyStroke accelerator,
                                                   boolean initState, ActionListener listener)
    {
        JCheckBoxMenuItem cbItem =
            new JCheckBoxMenuItem(itemLabel, initState);
        cbItem.addActionListener(listener);
        if (accelerator != null) {
            cbItem.setAccelerator(accelerator);
        }
        menu.add(cbItem);
        return cbItem;
    }

    /** Print a message to the console with a timestamp. */
    public static void log(String msg)
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

    /** Clear out the virtual map and any dependent state. */
    private void clearSurfaceSquares()
    {
        this.emCanvas.clearSurfaceSquares();
        this.activeSquare = null;
    }

    /** Build a portion of the Earth's surface.  Adds squares to
      * 'surfaceSquares'.  This works by iterating over latitude
      * and longitude pairs and assuming a spherical Earth. */
    public void buildEarthSurfaceWithLatLong()
    {
        log("building Earth");
        this.clearSurfaceSquares();

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
            startLongitude,
            null /*base*/, null /*midpoint*/,
            new Vector3f(0,0,0));
        this.emCanvas.addSurfaceSquare(startSquare);

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

        this.emCanvas.redrawCanvas();
        log("finished building Earth; nsquares="+this.emCanvas.numSurfaceSquares());
    }

    /** Build the surface by walking randomly from a starting location. */
    public void randomWalkEarthSurface()
    {
        log("building Earth by random walk");
        this.clearSurfaceSquares();

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
            startLongitude,
            null /*base*/, null /*midpoint*/,
            new Vector3f(0,0,0));
        this.emCanvas.addSurfaceSquare(startSquare);

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

        this.emCanvas.redrawCanvas();
        log("finished building Earth; nsquares="+this.emCanvas.numSurfaceSquares());
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
    public void buildEarthSurfaceFromStarData()
    {
        log("building Earth using star data");
        this.clearSurfaceSquares();

        StarObservation[] starObs = this.manualStarObservations;

        // Size of squares to build, in km.
        float sizeKm = 1000;

        SurfaceSquare firstSquare = null;

        // Work through the data in order, assuming that observations
        // for a given location are contiguous, and that the data appears
        // in a good walk order.
        float curLatitude = 0;
        float curLongitude = 0;
        SurfaceSquare curSquare = null;
        for (StarObservation so : starObs) {
            // Skip forward to next location.
            if (curSquare != null && so.latitude == curLatitude && so.longitude == curLongitude) {
                continue;
            }
            log("buildEarth: building lat="+so.latitude+" long="+so.longitude);

            if (curSquare == null) {
                // First square will be placed at the 3D origin with
                // its North pointed along the -Z axis.
                curSquare = new SurfaceSquare(
                    new Vector3f(0,0,0),      // center
                    new Vector3f(0,0,-1),     // north
                    new Vector3f(0,1,0),      // up
                    sizeKm,
                    so.latitude,
                    so.longitude,
                    null /*base*/, null /*midpoint*/,
                    new Vector3f(0,0,0));
                this.emCanvas.addSurfaceSquare(curSquare);
                firstSquare = curSquare;
            }
            else {
                // Calculate a rotation vector that will best align
                // the current square's star observations with the
                // next square's.
                Vector3f rot = calcRequiredRotation(curSquare, starObs,
                    so.latitude, so.longitude);
                if (rot == null) {
                    log("buildEarth: could not place next square!");
                    break;
                }

                // Make the new square from the old and the computed
                // change in orientation.
                curSquare =
                    addRotatedAdjacentSquare(curSquare, so.latitude, so.longitude, rot);
            }

            this.addMatchingData(curSquare, starObs);

            curLatitude = so.latitude;
            curLongitude = so.longitude;
        }

        // Go one step North from first square;
        {
            curSquare = firstSquare;
            float newLatitude = 38 + 9;
            float newLongitude = -122;

            log("buildEarth: building lat="+newLatitude+" long="+newLongitude);

            Vector3f rot = calcRequiredRotation(curSquare, starObs,
                newLatitude, newLongitude);
            if (rot == null) {
                log("buildEarth: could not place next square!");
            }

            curSquare =
                addRotatedAdjacentSquare(curSquare, newLatitude, newLongitude, rot);

            this.addMatchingData(curSquare, starObs);

            curLatitude = newLatitude;
            curLongitude = newLongitude;

        }

        // From here, keep exploring, relying on the synthetic catalog.
        this.buildLatitudeStrip(curSquare, +9, starObs);
        this.buildLatitudeStrip(curSquare, -9, starObs);
        this.buildLongitudeStrip(curSquare, +9, starObs);
        this.buildLongitudeStrip(curSquare, -9, starObs);

        this.emCanvas.redrawCanvas();
        log("buildEarth: finished using star data");
    }

    /** Build squares by going North or South from a starting square
      * until we add 20 or we can't add any more.  At each spot, also
      * build latitude strips in both directions. */
    private void buildLongitudeStrip(SurfaceSquare startSquare,
        float deltaLatitude, StarObservation[] starObs)
    {
        float curLatitude = startSquare.latitude;
        float curLongitude = startSquare.longitude;
        SurfaceSquare curSquare = startSquare;

        while (true) {
            float newLatitude = curLatitude + deltaLatitude;
            if (!( -90 < newLatitude && newLatitude < 90 )) {
                // Do not go past the poles.
                break;
            }
            float newLongitude = curLongitude;

            log("buildEarth: building lat="+newLatitude+" long="+newLongitude);

            Vector3f rot = calcRequiredRotation(curSquare, starObs,
                newLatitude, newLongitude);
            if (rot == null) {
                log("buildEarth: could not place next square!");
                break;
            }

            curSquare =
                addRotatedAdjacentSquare(curSquare, newLatitude, newLongitude, rot);

            this.addMatchingData(curSquare, starObs);

            curLatitude = newLatitude;
            curLongitude = newLongitude;

            // Also build strips in each direction.
            this.buildLatitudeStrip(curSquare, +9, starObs);
            this.buildLatitudeStrip(curSquare, -9, starObs);
        }
    }

    /** Build squares by going East or West from a starting square
      * until we add 20 or we can't add any more. */
    private void buildLatitudeStrip(SurfaceSquare startSquare,
        float deltaLongitude, StarObservation[] starObs)
    {
        float curLatitude = startSquare.latitude;
        float curLongitude = startSquare.longitude;
        SurfaceSquare curSquare = startSquare;

        for (int i=0; i < 20; i++) {
            float newLatitude = curLatitude;
            float newLongitude = FloatUtil.modulus2(curLongitude + deltaLongitude, -180, 180);
            log("buildEarth: building lat="+newLatitude+" long="+newLongitude);

            Vector3f rot = calcRequiredRotation(curSquare, starObs,
                newLatitude, newLongitude);
            if (rot == null) {
                log("buildEarth: could not place next square!");
                break;
            }

            curSquare =
                addRotatedAdjacentSquare(curSquare, newLatitude, newLongitude, rot);

            this.addMatchingData(curSquare, starObs);

            curLatitude = newLatitude;
            curLongitude = newLongitude;
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

        // Calculate the new square's center by going half the distance
        // according to the old orientation and then half the distance
        // according to the new orientation, in world coordinates.
        float halfDistWorld = distanceKm / 2.0f * EarthMapCanvas.SPACE_UNITS_PER_KM;
        Vector3f midPoint = old.center.plus(oldTravel.times(halfDistWorld));
        Vector3f newCenter = midPoint.plus(newTravel.times(halfDistWorld));

        // Make the new square and add it to the list.
        SurfaceSquare ret = new SurfaceSquare(
            newCenter, newNorth, newUp,
            old.sizeKm,
            newLatitude,
            newLongitude,
            old /*base*/,
            midPoint,
            Vector3f.composeRotations(old.rotationFromNominal, rotation));
        this.emCanvas.addSurfaceSquare(ret);

        return ret;
    }

    /** Add to 'square.starObs' all entries of 'starObs' that have
      * the same latitude and longitude. */
    private void addMatchingData(SurfaceSquare square, StarObservation[] starObs)
    {
        for (StarObservation so : starObs) {
            if (square.latitude == so.latitude &&
                square.longitude == so.longitude)
            {
                square.starObs.add(so);
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
        StarObservation[] starObs,
        float newLatitude,
        float newLongitude)
    {
        // Set of stars visible at the start and end squares and
        // above 20 degrees above the horizon.
        HashMap<String, Vector3f> startStars =
            getVisibleStars(starObs, startSquare.latitude, startSquare.longitude);
        HashMap<String, Vector3f> endStars =
            getVisibleStars(starObs, newLatitude, newLongitude);

        // Current best rotation and average difference.
        Vector3f currentRotation = new Vector3f(0,0,0);

        // Iteratively refine the current rotation by computing the
        // average correction rotation and applying it until that
        // correction drops below a certain threshold.
        for (int iterationCount = 0; iterationCount < 1000; iterationCount++) {
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

                // Both vectors must first be rotated the way the start
                // surface was rotated since its creation so that when
                // we compute the final required rotation, it can be
                // applied to the start surface in its existing orientation,
                // not the norminal orientation that the star vectors have
                // before I do this.
                startVector = startVector.rotateAA(startSquare.rotationFromNominal);
                endVector = endVector.rotateAA(startSquare.rotationFromNominal);

                // Calculate a difference rotation vector from the
                // rotated end vector to the start vector.  Rotating
                // the end star in one direction is like rotating
                // the start terrain in the opposite direction.
                Vector3f rot = endVector.rotateAA(currentRotation)
                                        .rotationToBecome(startVector);

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

    /** For every star in 'starObs' that matches 'latitude' and
      * 'longitude', and has an elevation of at least 20 degrees,
      * add it to a map from star name to azEl vector. */
    private HashMap<String, Vector3f> getVisibleStars(
        StarObservation[] starObs,
        float latitude,
        float longitude)
    {
        HashMap<String, Vector3f> ret = new HashMap<String, Vector3f>();

        // First use any manual data I have.
        for (StarObservation so : starObs) {
            if (so.latitude == latitude &&
                so.longitude == longitude &&
                so.elevation >= 20)
            {
                ret.put(so.name,
                    azimuthElevationToVector(so.azimuth, so.elevation));
            }
        }

        // Then use the synthetic data.
        for (StarCatalog sc : this.starCatalog) {
            if (ret.containsKey(sc.name)) {
                continue;
            }

            // Synthesize an observation.
            StarObservation so = sc.makeObservation(1488772800.0 /*2017-03-05 20:00 -08:00*/,
                latitude, longitude);
            if (so.elevation >= 20) {
                ret.put(so.name,
                    azimuthElevationToVector(so.azimuth, so.elevation));
            }
        }

        return ret;
    }

    /** Convert a pair of azimuth and elevation, in degrees, to a unit
      * vector that points in the same direction, in a coordinate system
      * where 0 degrees azimuth is -Z, East is +X, and up is +Y. */
    public static Vector3f azimuthElevationToVector(float azimuth, float elevation)
    {
        // Start by pointing a vector at 0 degrees azimuth (North).
        Vector3f v = new Vector3f(0, 0, -1);

        // Now rotate it up to align with elevation.
        v = v.rotate(elevation, new Vector3f(1, 0, 0));

        // Now rotate it East to align with azimuth.
        v = v.rotate(-azimuth, new Vector3f(0, 1, 0));

        return v;
    }

    /** Begin constructing a new surface using star data.  This just
      * places down the initial square to represent a user-specified
      * latitude and longitude.  The square is placed into 3D space
      * at a fixed location. */
    public void startNewSurface()
    {
        LatLongDialog d = new LatLongDialog(this, 38, -122);
        if (d.exec()) {
            log("starting new surface at lat="+d.finalLatitude+
                ", lng="+d.finalLongitude);
            this.clearSurfaceSquares();
            this.setActiveSquare(new SurfaceSquare(
                new Vector3f(0,0,0),      // center
                new Vector3f(0,0,-1),     // north
                new Vector3f(0,1,0),      // up
                INITIAL_SQUARE_SIZE_KM,
                d.finalLatitude,
                d.finalLongitude,
                null /*base*/, null /*midpoint*/,
                new Vector3f(0,0,0)));
            this.addMatchingData(this.activeSquare, this.manualStarObservations);
            this.emCanvas.addSurfaceSquare(this.activeSquare);
            this.emCanvas.redrawCanvas();
        }
    }

    /** Change which square is active. */
    private void setActiveSquare(SurfaceSquare sq)
    {
        if (this.activeSquare != null) {
            this.activeSquare.showAsActive = false;
        }
        this.activeSquare = sq;
        if (this.activeSquare != null) {
            this.activeSquare.showAsActive = true;
        }

        this.emCanvas.redrawCanvas();
        this.updateUIState();
    }

    /** Add another square to the surface by building one adjacent
      * to the active square. */
    private void buildNextSquare()
    {
        if (this.activeSquare == null) {
            ModalDialog.errorBox(this, "No square is active.");
            return;
        }

        LatLongDialog d = new LatLongDialog(this,
            this.activeSquare.latitude, this.activeSquare.longitude + 9);
        if (d.exec()) {
            // Add it initially with no rotation.  My plan is to add
            // the rotation interactively afterward.
            this.setActiveSquare(
                this.addRotatedAdjacentSquare(this.activeSquare,
                    d.finalLatitude, d.finalLongitude, new Vector3f(0,0,0)));

            this.addMatchingData(this.activeSquare, this.manualStarObservations);
            this.emCanvas.redrawCanvas();
        }
    }

    /** Make the next square in 'emCanvas.surfaceSquares' active. */
    private void selectNextSquare(boolean forward)
    {
        this.setActiveSquare(this.emCanvas.getNextSquare(this.activeSquare, forward));
    }

    public static void main(String[] args)
    {
        (new EarthShape()).setVisible(true);
    }

    /** Toggle the 'drawCompasses' flag, then update state and redraw. */
    public void toggleDrawCompasses()
    {
        log("toggleDrawCompasses");
        this.emCanvas.drawCompasses = !this.emCanvas.drawCompasses;
        this.updateUIState();
        this.emCanvas.redrawCanvas();
    }

    /** Toggle the 'drawSurfaceNormals' flag. */
    public void toggleDrawSurfaceNormals()
    {
        this.emCanvas.drawSurfaceNormals = !this.emCanvas.drawSurfaceNormals;
        this.updateUIState();
        this.emCanvas.redrawCanvas();
    }

    /** Toggle the 'drawCelestialNorth' flag. */
    public void toggleDrawCelestialNorth()
    {
        this.emCanvas.drawCelestialNorth = !this.emCanvas.drawCelestialNorth;
        this.updateUIState();
        this.emCanvas.redrawCanvas();
    }

    /** Toggle the 'drawStarRays' flag. */
    public void toggleDrawStarRays()
    {
        if (this.activeSquare == null) {
            ModalDialog.errorBox(this, "No square is active");
        }
        else {
            this.activeSquare.drawStarRays = !this.activeSquare.drawStarRays;
        }
        this.emCanvas.redrawCanvas();
    }

    /** Update all stateful UI elements. */
    public void updateUIState()
    {
        this.setStatusLabel();
        this.setMenuState();
        this.setInfoPanel();
    }

    /** Set the status label text to reflect other state variables.
      * This also updates the state of stateful menu items. */
    private void setStatusLabel()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(this.emCanvas.getStatusString());
        this.statusLabel.setText(sb.toString());
    }

    /** Set the state of stateful menu items. */
    private void setMenuState()
    {
        this.drawCompassesCBItem.setSelected(this.emCanvas.drawCompasses);
        this.drawSurfaceNormalsCBItem.setSelected(this.emCanvas.drawSurfaceNormals);
        this.drawCelestialNorthCBItem.setSelected(this.emCanvas.drawCelestialNorth);
        this.drawStarRaysCBItem.setSelected(this.activeSquareDrawsStarRays());
    }

    /** Update the contents of the info panel. */
    private void setInfoPanel()
    {
        StringBuilder sb = new StringBuilder();

        if (this.activeSquare == null) {
            sb.append("No active square.");
        }
        else {
            sb.append("Active square:\n");
            sb.append("  lat: "+this.activeSquare.latitude+"\n");
            sb.append("  lng: "+this.activeSquare.longitude+"\n");
        }

        this.infoPanel.text.setText(sb.toString());
    }

    /** True if there is an active square and it is drawing star rays. */
    private boolean activeSquareDrawsStarRays()
    {
        return this.activeSquare != null &&
               this.activeSquare.drawStarRays;
    }
}

// EOF
