// EarthShape.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
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

    /** Initial value of 'adjustOrientationDegrees', and the value to
      * which it is reset when a new square is created. */
    private static final float DEFAULT_ADJUST_ORIENTATION_DEGREES = 1.0f;

    /** Do not let 'adjustOrientationDegrees' go below this value.  Below
      * this value is pointless because the precision of the variance is
      * not high enough to discriminate among the choices. */
    private static final float MINIMUM_ADJUST_ORIENTATION_DEGREES = 1e-7f;

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

    /** Set of stars that are enabled. */
    private LinkedHashMap<String, Boolean> enabledStars = new LinkedHashMap<String, Boolean>();

    // ---- Interactive surface construction state ----
    /** The square we will build upon when the next square is added.
      * This may be null. */
    private SurfaceSquare activeSquare;

    /** When adjusting the orientation of the active square, this
      * is how many degrees to rotate at once. */
    private float adjustOrientationDegrees = DEFAULT_ADJUST_ORIENTATION_DEGREES;

    /** The calculated recommended rotation command, or null if the
      * recommendation was to zoom in or deviation is zero.  This is
      * only valid after a call to 'setInfoPanel'. */
    private RotationCommand recommendedRotationCommand = null;

    /** When analyzing the solution space, use this many points of
      * rotation on each side of 0, for each axis.  Note that the
      * algorithm is cubic in this parameter. */
    private int solutionAnalysisPointsPerSide = 10;

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

    /** Menu item to toggle 'EarthMapCanvas.invertHorizontalCameraMovement. */
    private JCheckBoxMenuItem invertHorizontalCameraMovementCBItem;

    /** Menu item to toggle 'EarthMapCanvas.invertVerticalCameraMovement. */
    private JCheckBoxMenuItem invertVerticalCameraMovementCBItem;

    // ---------- Methods ----------
    public EarthShape()
    {
        super("Earth Shape");
        this.setName("EarthShape (JFrame)");

        this.setLayout(new BorderLayout());

        // Exit the app when window close button is pressed (AWT boilerplate).
        this.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                EarthShape.this.dispose();
            }
        });

        for (StarCatalog sc : this.starCatalog) {
            // Initially all stars are enabled.
            this.enabledStars.put(sc.name, true);
        }

        this.setSize(950, 800);
        this.setLocationByPlatform(true);

        this.setupJOGL();

        this.buildEarthSurfaceFromStarData();

        this.buildMenuBar();

        // Status bar on bottom.
        this.statusLabel = new JLabel();
        this.statusLabel.setName("statusLabel");
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

    private void showAboutBox()
    {
        String about =
            "Earth Shape\n"+
            "Copyright 2017 Scott McPeak\n"+
            "\n"+
            "Published under the 2-clause BSD license.\n"+
            "See copyright.txt for details.\n";

        JOptionPane.showMessageDialog(this, about, "About", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showKeyBindings()
    {
        String bindings =
            "Left click in 3D view to enter FPS controls mode.\n"+
            "Esc - Leave FPS mode.\n"+
            "W/A/S/D - Move camera horizontally when in FPS mode.\n"+
            "Space/Z - Move camera up or down in FPS mode.\n"+
            "Left click on square in FPS mode to make it active.\n"+
            "\n"+
            "T - Reconstruct Earth from star data.\n"+
            "\n"+
            "C - Toggle compass or Earth texture.\n"+
            "P - Toggle star rays for active square.\n"+
            "G - Move camera to active square's center.\n"+
            "H - Build complete Earth using assumed sphere.\n"+
            "R - Build Earth using assumed sphere and random walk.\n"+
            "\n"+
            "U/O - Roll active square left or right.\n"+
            "I/K - Pitch active square down or up.\n"+
            "J/L - Yaw active square left or right.\n"+
            "-/= - Decrease or increase adjustment amount.\n"+
            "; (semicolon) - Make recommended active square adjustment.\n"+
            "/ (slash) - Automatically orient active square.\n"+
            "\' (apostrophe) - Analyze rotation solution space for active square.\n"+
            "\n"+
            "N - Start a new surface.\n"+
            "M - Add a square adjacent to the active square.\n"+
            "Ctrl+M - Add a square to the East and automatically adjust it.\n"+
            ", (comma) - Move to previous active square.\n"+
            ". (period) - Move to next active square.\n"+
            "Delete - Delete active square.\n"+
            "";
        JOptionPane.showMessageDialog(this, bindings, "Bindings", JOptionPane.INFORMATION_MESSAGE);
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
        //   g - Go to selected square's center
        //   h - Build spherical Earth
        //   i - Pitch active square down
        //   j - Yaw active square left
        //   k - Pitch active square up
        //   l - Yaw active square right
        //   m - Add adjacent square to surface
        //   n - Build new surface
        //   o - Roll active square right
        //   p - Toggle star rays for active square
        //   q
        //   r - Build with random walk
        //   s - Move camera backward
        //   t - Build full Earth with star data
        //   u - Roll active square left
        //   v
        //   w - Move camera forward
        //   x
        //   y
        //   z - Move camera down
        //   - - Decrease adjustment amount
        //   = - Increase adjustment amount
        //   ; - One recommended rotation adjustment
        //   ' - Analyze solution space
        //   , - Select previous square
        //   . - Select next square
        //   / - Automatically orient active square
        //   Space  - Move camera up
        //   Delete - Delete active square
        //   Enter  - enter FPS mode
        //   Esc    - leave FPS mode

        menuBar.add(this.buildFileMenu());
        menuBar.add(this.buildDrawMenu());
        menuBar.add(this.buildBuildMenu());
        menuBar.add(this.buildSelectMenu());
        menuBar.add(this.buildEditMenu());
        menuBar.add(this.buildNavigateMenu());
        menuBar.add(this.buildHelpMenu());
    }

    private JMenu buildFileMenu()
    {
        JMenu fileMenu = new JMenu("File");
        addMenuItem(fileMenu, "Choose enabled stars", null, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                EarthShape.this.chooseEnabledStars();
            }
        });
        addMenuItem(fileMenu, "Exit", null, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                EarthShape.log("exit menu item invoked");
                EarthShape.this.dispose();
            }
        });
        return fileMenu;
    }

    private JMenu buildDrawMenu()
    {
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
        addMenuItem(drawMenu, "Turn off all star rays", null,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.turnOffAllStarRays();
                }
            });
        return drawMenu;
    }

    private JMenu buildBuildMenu()
    {
        JMenu buildMenu = new JMenu("Build");
        addMenuItem(buildMenu, "Build Earth using star data and no assumed shape",
            KeyStroke.getKeyStroke('t'),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.buildEarthSurfaceFromStarData();
                }
            });
        addMenuItem(buildMenu, "Build complete Earth using assumed sphere",
            KeyStroke.getKeyStroke('h'),
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
        addMenuItem(buildMenu, "Create new square 9 degrees to the East and orient it automatically",
            KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.createAndAutomaticallyOrientSquare();
                }
            });
        return buildMenu;
    }

    private JMenu buildSelectMenu()
    {
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
        return selectMenu;
    }

    private JMenu buildEditMenu()
    {
        JMenu editMenu = new JMenu("Edit");
        for (RotationCommand rc : RotationCommand.values()) {
            this.addAdjustOrientationMenuItem(editMenu,
                rc.description, rc.key, rc.axis);
        }
        editMenu.addSeparator();
        addMenuItem(editMenu, "Double active square adjustment angle",
            KeyStroke.getKeyStroke('='),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.changeAdjustOrientationDegrees(2.0f);
                }
            });
        addMenuItem(editMenu, "Halve active square adjustment angle",
            KeyStroke.getKeyStroke('-'),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.changeAdjustOrientationDegrees(0.5f);
                }
            });
        addMenuItem(editMenu, "Reset active square adjustment angle to 1 degree",
            KeyStroke.getKeyStroke('1'),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.adjustOrientationDegrees = 1;
                    EarthShape.this.updateUIState();
                }
            });
        editMenu.addSeparator();
        addMenuItem(editMenu, "Analyze solution space",
            KeyStroke.getKeyStroke('\''),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.analyzeSolutionSpace();
                }
            });
        addMenuItem(editMenu, "Set solution analysis resolution...",
            null,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.setSolutionAnalysisResolution();
                }
            });
        editMenu.addSeparator();
        addMenuItem(editMenu, "Do one recommended rotation adjustment",
            KeyStroke.getKeyStroke(';'),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.applyRecommendedRotationCommand();
                }
            });
        addMenuItem(editMenu, "Automatically orient active square",
            KeyStroke.getKeyStroke('/'),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.automaticallyOrientActiveSquare();
                }
            });
        addMenuItem(editMenu, "Delete active square",
            KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.deleteActiveSquare();
                }
            });
        return editMenu;
    }

    private JMenu buildNavigateMenu()
    {
        JMenu navigateMenu = new JMenu("Navigate");
        addMenuItem(navigateMenu, "Control camera like a first-person shooter",
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.emCanvas.enterFPSMode();
                }
            });
        addMenuItem(navigateMenu, "Leave first-person shooter mode",
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.emCanvas.exitFPSMode();
                }
            });
        addMenuItem(navigateMenu, "Go to active square's center",
            KeyStroke.getKeyStroke('g'),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.goToActiveSquareCenter();
                }
            });
        this.invertHorizontalCameraMovementCBItem =
            addCBMenuItem(navigateMenu, "Invert horizontal camera movement", null,
                this.emCanvas.invertHorizontalCameraMovement,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.emCanvas.invertHorizontalCameraMovement =
                            !EarthShape.this.emCanvas.invertHorizontalCameraMovement;
                        EarthShape.this.updateUIState();
                    }
                });
        this.invertVerticalCameraMovementCBItem =
            addCBMenuItem(navigateMenu, "Invert vertical camera movement", null,
                this.emCanvas.invertVerticalCameraMovement,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.emCanvas.invertVerticalCameraMovement =
                            !EarthShape.this.emCanvas.invertVerticalCameraMovement;
                        EarthShape.this.updateUIState();
                    }
                });
        return navigateMenu;
    }

    private JMenu buildHelpMenu()
    {
        JMenu helpMenu = new JMenu("Help");
        addMenuItem(helpMenu, "Show all key bindings...", null,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.showKeyBindings();
                }
            });
        addMenuItem(helpMenu, "About...", null,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.showAboutBox();
                }
            });
        return helpMenu;
    }

    /** Add a menu item to adjust the orientation of the active square. */
    private void addAdjustOrientationMenuItem(
        JMenu menu, String description, char key, Vector3f axis)
    {
        addMenuItem(menu, description,
            KeyStroke.getKeyStroke(key),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.adjustActiveSquareOrientation(axis);
                }
            });
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

    /** Choose the set of stars to use.  This only affects new
      * squares. */
    private void chooseEnabledStars()
    {
        StarListDialog d = new StarListDialog(this, this.enabledStars);
        if (d.exec()) {
            this.enabledStars = d.stars;
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

    /** Create a square adjacent to 'old', positioned at the given latitude
      * and longitude, with orientation changed by 'rotation'.  If there is
      * no change, return null.  Even if not, do not add the square yet,
      * just return it. */
    private static SurfaceSquare createRotatedAdjacentSquare(
        SurfaceSquare old,
        float newLatitude,
        float newLongitude,
        Vector3f rotation)
    {
        // Normalize latitude and longitude.
        newLatitude = FloatUtil.clamp(newLatitude, -90, 90);
        newLongitude = FloatUtil.modulus2(newLongitude, -180, 180);

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

        // If we didn't move, return null.
        if (deltaLongitude == 0 && deltaLatitude == 0) {
            return null;
        }

        // Compute the new orientation vectors by rotating
        // the old ones by the given amount.
        Vector3f newNorth = old.north.rotateAA(rotation);
        Vector3f newUp = old.up.rotateAA(rotation);

        // Calculate headings, in degrees East of North, from
        // the old square to the new and vice-versa.
        float oldToNewHeading = FloatUtil.getLatLongPairHeading(
            old.latitude, old.longitude, newLatitude, newLongitude);
        float newToOldHeading = FloatUtil.getLatLongPairHeading(
            newLatitude, newLongitude, old.latitude, old.longitude);

        // For both old and new, calculate a unit vector for the
        // travel direction.  Both headings are negated due to the
        // right hand rule for rotation.  The new to old heading is
        // then flipped 180 since I want both to indicate the local
        // direction from old to new.
        Vector3f oldTravel = old.north.rotate(-oldToNewHeading, old.up);
        Vector3f newTravel = newNorth.rotate(-newToOldHeading + 180, newUp);

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
            rotation);

        return ret;
    }

    /** Add a square adjacent to 'old', positioned at the given latitude
      * and longitude, with orientation changed by 'rotation'.  If we
      * did not move, this returns the old square. */
    private SurfaceSquare addRotatedAdjacentSquare(
        SurfaceSquare old,
        float newLatitude,
        float newLongitude,
        Vector3f rotation)
    {
        SurfaceSquare ret = EarthShape.createRotatedAdjacentSquare(
            old, newLatitude, newLongitude, rotation);
        if (ret == null) {
            return old;        // Did not move.
        }
        else {
            this.emCanvas.addSurfaceSquare(ret);
        }
        return ret;
    }

    /** Add to 'square.starObs' all entries of 'starObs' that have
      * the same latitude and longitude, and also are at least
      * 20 degrees above the horizon. */
    private void addMatchingData(SurfaceSquare square, StarObservation[] starObs)
    {
        // For which stars do I have manual data?
        HashSet<String> manualStars = new HashSet<String>();
        for (StarObservation so : starObs) {
            if (square.latitude == so.latitude &&
                square.longitude == so.longitude &&
                this.qualifyingStarObservation(so))
            {
                manualStars.add(so.name);
                square.addObservation(so);
            }
        }

        // Synthesize observations for others.
        for (StarCatalog sc : this.starCatalog) {
            if (!manualStars.contains(sc.name)) {
                StarObservation so = sc.makeObservation(StarObservation.unixTimeOfManualData,
                    square.latitude, square.longitude);
                if (this.qualifyingStarObservation(so)) {
                    square.addObservation(so);
                }
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
                // not the nominal orientation that the star vectors have
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

    /** True if the given observation is available for use, meaning
      * it is high enough in the sky and is enabled. */
    private boolean qualifyingStarObservation(StarObservation so)
    {
        return so.elevation >= 20.0f &&
               this.enabledStars.containsKey(so.name) &&
               this.enabledStars.get(so.name) == true;
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
                this.qualifyingStarObservation(so))
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
            StarObservation so = sc.makeObservation(StarObservation.unixTimeOfManualData,
                latitude, longitude);
            if (this.qualifyingStarObservation(so)) {
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

    /** Get the unit ray, in world coordinates, from the center of 'square' to
      * the star recorded in 'so', which was observed at this square. */
    public static Vector3f rayToStar(SurfaceSquare square, StarObservation so)
    {
        // Ray to star in nominal, -Z facing, coordinates.
        Vector3f nominalRay =
            EarthShape.azimuthElevationToVector(so.azimuth, so.elevation);

        // Ray to star in world coordinates, taking into account
        // how the surface is rotated.
        Vector3f worldRay = nominalRay.rotateAA(square.rotationFromNominal);

        return worldRay;
    }

    /** Hold results of call to 'fitOfObservations'. */
    private static class ObservationStats {
        /** The total variance in star observation locations from the
          * indicated square to the observations of its base square as the
          * average square of the deviation angles.
          *
          * The reason for using a sum of squares approach is to penalize large
          * deviations and to ensure there is a unique "least deviated" point
          * (which need not exist when using a simple sum).  The reason for using
          * the average is to make it easier to judge "good" or "bad" fits,
          * regardless of the number of star observations in common.
          *
          * I use the term "variance" here because it is similar to the idea in
          * statistics, except here we are measuring differences between pairs of
          * observations, rather than between individual observations and the mean
          * of the set.  I'll then reserve "deviation", if I use it, to refer to
          * the square root of the variance, by analogy with "standard deviation".
          * */
        public float variance;

        /** Maximum separation between observations, in degrees. */
        public float maxSeparation;
    }

    /** Calculate variance and maximum separation for 'square'.  Returns
      * null if there is no base or there are no observations in common. */
    private static ObservationStats fitOfObservations(SurfaceSquare square)
    {
        if (square.baseSquare == null) {
            return null;
        }

        float sumOfSquares = 0;
        int numSamples = 0;
        float maxSeparation = 0;

        for (Map.Entry<String, StarObservation> entry : square.starObs.entrySet()) {
            StarObservation so = entry.getValue();

            // Ray to star in world coordinates.
            Vector3f starRay = EarthShape.rayToStar(square, so);

            // Calculate the deviation of this observation from that of
            // the base square.
            StarObservation baseObservation = square.baseSquare.findObservation(so.name);
            if (baseObservation != null) {
                // Get ray from base square to the base observation star
                // in world coordinates.
                Vector3f baseStarRay = EarthShape.rayToStar(square.baseSquare, baseObservation);

                // Visual separation angle between these rays.
                float sep = FloatUtil.acosDegf(starRay.dot(baseStarRay));
                if (sep > maxSeparation) {
                    maxSeparation = sep;
                }

                // Accumulate its square.
                sumOfSquares += sep * sep;
                numSamples++;
            }
        }

        if (numSamples == 0) {
            return null;
        }
        else {
            ObservationStats ret = new ObservationStats();
            ret.variance = sumOfSquares / numSamples;
            ret.maxSeparation = maxSeparation;
            return ret;
        }
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
    public void setActiveSquare(SurfaceSquare sq)
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

        // The new square should draw star rays if the old did.
        boolean drawStarRays = this.activeSquare.drawStarRays;

        LatLongDialog d = new LatLongDialog(this,
            this.activeSquare.latitude, this.activeSquare.longitude + 9);
        if (d.exec()) {
            // Add it initially with no rotation.  My plan is to add
            // the rotation interactively afterward.
            this.setActiveSquare(
                this.addRotatedAdjacentSquare(this.activeSquare,
                    d.finalLatitude, d.finalLongitude, new Vector3f(0,0,0)));
            this.activeSquare.drawStarRays = drawStarRays;

            // Reset the rotation angle after adding a square.
            this.adjustOrientationDegrees = DEFAULT_ADJUST_ORIENTATION_DEGREES;

            this.addMatchingData(this.activeSquare, this.manualStarObservations);
            this.emCanvas.redrawCanvas();
        }
    }

    /** If there is an active square, assume we just built it, and now
      * we want to adjust its orientation.  'axis' indicates the axis
      * about which to rotate, relative to the square's current orientation,
      * where -Z is North, +Y is up, and +X is east, and the angle is given
      * by 'this.adjustOrientationDegrees'. */
    private void adjustActiveSquareOrientation(Vector3f axis)
    {
        SurfaceSquare derived = this.activeSquare;
        if (derived == null) {
            ModalDialog.errorBox(this, "No active square.");
            return;
        }
        SurfaceSquare base = derived.baseSquare;
        if (base == null) {
            ModalDialog.errorBox(this, "The active square has no base square.");
            return;
        }

        // Rotate by 'adjustOrientationDegrees'.
        Vector3f angleAxis = axis.times(this.adjustOrientationDegrees);

        // Rotate the axis to align it with the square.
        angleAxis = angleAxis.rotateAA(derived.rotationFromNominal);

        // Now add that to the square's existing rotation relative
        // to its base square.
        angleAxis = Vector3f.composeRotations(derived.rotationFromBase, angleAxis);

        // Now, replace the active square.
        this.replaceWithNewRotation(base, derived, angleAxis);
    }

    /** Replace the square 'derived', with a new square that
      * is computed from 'base' by applying 'newRotation'.  Also
      * make the new square active. */
    private void replaceWithNewRotation(
        SurfaceSquare base, SurfaceSquare derived, Vector3f newRotation)
    {
        // Now, replace the active square with a new one created by
        // rotating from the same base by this new amount.
        this.emCanvas.removeSurfaceSquare(derived);
        this.setActiveSquare(
            this.addRotatedAdjacentSquare(base,
                derived.latitude, derived.longitude, newRotation));

        // Copy some other data from the derived square that we
        // are in the process of discarding.
        this.activeSquare.drawStarRays = derived.drawStarRays;
        this.activeSquare.starObs = derived.starObs;

        this.emCanvas.redrawCanvas();
    }

    /** Calculate what the variation of observations would be for
      * 'derived' if its orientation were adjusted by
      * 'angleAxis.degrees()' around 'angleAxis'.  Returns null if
      * the calculation cannot be done because of missing information. */
    private static ObservationStats fitOfAdjustedSquare(
        SurfaceSquare derived, Vector3f angleAxis)
    {
        // This part mirrors 'adjustActiveSquareOrientation'.
        SurfaceSquare base = derived.baseSquare;
        if (base == null) {
            return null;
        }
        angleAxis = angleAxis.rotateAA(derived.rotationFromNominal);
        angleAxis = Vector3f.composeRotations(derived.rotationFromBase, angleAxis);

        // Now, create a new square with this new rotation.
        SurfaceSquare newSquare =
            EarthShape.createRotatedAdjacentSquare(base,
                derived.latitude, derived.longitude, angleAxis);
        if (newSquare == null) {
            // If we do not move, use the original square's data.
            return EarthShape.fitOfObservations(derived);
        }

        // Copy the observation data since that is needed to calculate
        // the deviation.
        newSquare.starObs = derived.starObs;

        // Now calculate the new variance.
        return EarthShape.fitOfObservations(newSquare);
    }

    /** Like 'fitOfAdjustedSquare' except only retrieves the
      * variance.  This returns 40000 if the data is unavailable. */
    private static float varianceOfAdjustedSquare(
        SurfaceSquare derived, Vector3f angleAxis)
    {
        ObservationStats os = EarthShape.fitOfAdjustedSquare(derived, angleAxis);
        if (os == null) {
            // The variance should never be greater than 180 squared,
            // since that would be the worst possible fit for a star.
            return 40000;
        }
        else {
            return os.variance;
        }
    }


    /** Change 'adjustOrientationDegrees' by the given multiplier. */
    private void changeAdjustOrientationDegrees(float multiplier)
    {
        this.adjustOrientationDegrees *= multiplier;
        if (this.adjustOrientationDegrees < MINIMUM_ADJUST_ORIENTATION_DEGREES) {
            this.adjustOrientationDegrees = MINIMUM_ADJUST_ORIENTATION_DEGREES;
        }
        this.updateUIState();
    }

    /** Compute and apply a single step rotation command to improve
      * the variance of the active square. */
    private void applyRecommendedRotationCommand()
    {
        SurfaceSquare s = this.activeSquare;
        if (s == null) {
            ModalDialog.errorBox(this, "No active square.");
            return;
        }
        ObservationStats ostats = EarthShape.fitOfObservations(s);
        if (ostats == null) {
            ModalDialog.errorBox(this, "Not enough observational data available.");
            return;
        }
        if (ostats.variance == 0) {
            ModalDialog.errorBox(this, "Orientation is already optimal.");
            return;
        }

        // Hack: Call into the info routine to get the recommendation.
        this.setInfoPanel();
        if (this.recommendedRotationCommand == null) {
            if (this.adjustOrientationDegrees <= MINIMUM_ADJUST_ORIENTATION_DEGREES) {
                ModalDialog.errorBox(this, "Cannot further improve orientation.");
                return;
            }
            else {
                this.changeAdjustOrientationDegrees(0.5f);
            }
        }
        else {
            this.adjustActiveSquareOrientation(this.recommendedRotationCommand.axis);
        }

        this.emCanvas.redrawCanvas();
        this.updateUIState();
    }

    /** Delete the active square. */
    private void deleteActiveSquare()
    {
        if (this.activeSquare == null) {
            ModalDialog.errorBox(this, "No active square.");
            return;
        }

        this.emCanvas.removeSurfaceSquare(this.activeSquare);
        this.setActiveSquare(null);
    }

    /** Calculate and apply the optimal orientation for the active square. */
    private void automaticallyOrientActiveSquare()
    {
        SurfaceSquare derived = this.activeSquare;
        if (derived == null) {
            ModalDialog.errorBox(this, "No active square.");
            return;
        }
        SurfaceSquare base = derived.baseSquare;
        if (base == null) {
            ModalDialog.errorBox(this, "The active square has no base square.");
        }

        Vector3f rot = calcRequiredRotation(base, this.manualStarObservations,
            derived.latitude, derived.longitude);
        if (rot == null) {
            ModalDialog.errorBox(this, "Could not determine proper orientation!");
            return;
        }

        // Now, replace the active square.
        this.replaceWithNewRotation(base, derived, rot);
    }

    /** Make the next square in 'emCanvas.surfaceSquares' active. */
    private void selectNextSquare(boolean forward)
    {
        this.setActiveSquare(this.emCanvas.getNextSquare(this.activeSquare, forward));
    }

    /** Like hitting 'm', Enter, then '/'. */
    private void createAndAutomaticallyOrientSquare()
    {
        SurfaceSquare base = this.activeSquare;
        boolean drawStarRays = base.drawStarRays;
        this.setActiveSquare(
            this.addRotatedAdjacentSquare(this.activeSquare,
                base.latitude, base.longitude + 9, new Vector3f(0,0,0)));
        this.activeSquare.drawStarRays = drawStarRays;
        this.addMatchingData(this.activeSquare, this.manualStarObservations);

        this.automaticallyOrientActiveSquare();
    }

    /** Show the user what the local rotation space looks like. */
    private void analyzeSolutionSpace()
    {
        if (this.activeSquare == null) {
            ModalDialog.errorBox(this, "No active square.");
            return;
        }
        SurfaceSquare s = this.activeSquare;

        ObservationStats ostats = EarthShape.fitOfObservations(s);
        if (ostats == null) {
            ModalDialog.errorBox(this, "No observation fitness stats for the active square.");
            return;
        }

        // The computation can take a while, so show a wait cursor.
        Cursor prevCursor = this.emCanvas.getCursor();
        this.emCanvas.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        // Consider the effect of rotating various amounts on each axis.
        PlotData3D rollPitchYawPlotData = this.getThreeRotationAxisPlotData(s);

        // Restore the cursor.
        this.emCanvas.setCursor(prevCursor);

        // Plot them.
        RotationCubeDialog d = new RotationCubeDialog(this,
            ostats.variance,
            rollPitchYawPlotData);
        d.exec();
    }

    /** Get data for various rotation angles of all three axes. */
    private PlotData3D getThreeRotationAxisPlotData(SurfaceSquare s)
    {
        // Number of data points on each side of 0.
        int pointsPerSide = this.solutionAnalysisPointsPerSide;

        // Total number of data points per axis, including 0.
        int pointsPerAxis = pointsPerSide * 2 + 1;

        float[] wData = new float[pointsPerAxis * pointsPerAxis * pointsPerAxis];

        Vector3f xAxis = new Vector3f(0, 0, -1);    // Roll
        Vector3f yAxis = new Vector3f(1, 0, 0);     // Pitch
        Vector3f zAxis = new Vector3f(0, -1, 0);    // Yaw

        float xFirst = -pointsPerSide * this.adjustOrientationDegrees;
        float xLast = pointsPerSide * this.adjustOrientationDegrees;
        float yFirst = -pointsPerSide * this.adjustOrientationDegrees;
        float yLast = pointsPerSide * this.adjustOrientationDegrees;
        float zFirst = -pointsPerSide * this.adjustOrientationDegrees;
        float zLast = pointsPerSide * this.adjustOrientationDegrees;

        for (int zIndex=0; zIndex < pointsPerAxis; zIndex++) {
            for (int yIndex=0; yIndex < pointsPerAxis; yIndex++) {
                for (int xIndex=0; xIndex < pointsPerAxis; xIndex++) {
                    // Compose rotations about each axis: X then Y then Z.
                    //
                    // Note: Rotations don't commute, so the axes are not
                    // being treated perfectly symmetrically here, but this
                    // is still good for showing overall shape, and when
                    // we zoom in to small angles, the non-commutativity
                    // becomes insignificant.
                    Vector3f rotX = xAxis.times(this.adjustOrientationDegrees * (xIndex - pointsPerSide));
                    Vector3f rotY = yAxis.times(this.adjustOrientationDegrees * (yIndex - pointsPerSide));
                    Vector3f rotZ = zAxis.times(this.adjustOrientationDegrees * (zIndex - pointsPerSide));
                    Vector3f rot = Vector3f.composeRotations(
                        Vector3f.composeRotations(rotX, rotY), rotZ);

                    // Get variance after that adjustment.
                    wData[xIndex + pointsPerAxis * yIndex + pointsPerAxis * pointsPerAxis * zIndex] =
                        EarthShape.varianceOfAdjustedSquare(s, rot);
                }
            }
        }

        return new PlotData3D(
            xFirst, xLast,
            yFirst, yLast,
            zFirst, zLast,
            pointsPerAxis /*xValuesPerRow*/,
            pointsPerAxis /*yValuesPerColumn*/,
            wData);
    }

    /** Show a dialog to let the user change
      * 'solutionAnalysisPointsPerSide'. */
    private void setSolutionAnalysisResolution()
    {
        String choice = JOptionPane.showInputDialog(this,
            "Specify number of data points on each side of 0 to sample "+
                "when performing a solution analysis",
            (Integer)this.solutionAnalysisPointsPerSide);
        if (choice != null) {
            try {
                int c = Integer.valueOf(choice);
                if (c < 1) {
                    ModalDialog.errorBox(this, "The minimum number of points is 1.");
                }
                else if (c > 100) {
                    // At 100, it will take several minutes to complete.
                    ModalDialog.errorBox(this, "The maximum number of points is 100.");
                }
                else {
                    this.solutionAnalysisPointsPerSide = c;
                }
            }
            catch (NumberFormatException e) {
                ModalDialog.errorBox(this, "Invalid integer syntax: "+e.getMessage());
            }
        }
    }

    /** Make this window visible. */
    public void makeVisible()
    {
        this.setVisible(true);

        // It seems I can only set the focus once the window is
        // visible.  There is an example in the focus tutorial of
        // calling pack() first, but that resizes the window and
        // I don't want that.
        this.emCanvas.setFocusOnCanvas();
    }

    public static void main(String[] args)
    {
        (new EarthShape()).makeVisible();
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

    private void turnOffAllStarRays()
    {
        this.emCanvas.turnOffAllStarRays();
        this.emCanvas.redrawCanvas();
    }

    /** Move the camera to the center of the active square. */
    private void goToActiveSquareCenter()
    {
        if (this.activeSquare == null) {
            ModalDialog.errorBox(this, "No active square.");
        }
        else {
            this.emCanvas.cameraPosition = this.activeSquare.center;
            this.emCanvas.redrawCanvas();
            this.updateUIState();
        }
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

        this.invertHorizontalCameraMovementCBItem.setSelected(
            this.emCanvas.invertHorizontalCameraMovement);
        this.invertVerticalCameraMovementCBItem.setSelected(
            this.emCanvas.invertVerticalCameraMovement);
    }

    /** Update the contents of the info panel. */
    private void setInfoPanel()
    {
        StringBuilder sb = new StringBuilder();

        if (this.activeSquare == null) {
            sb.append("No active square.\n");
        }
        else {
            sb.append("Active square:\n");
            SurfaceSquare s = this.activeSquare;
            sb.append("  lat: "+s.latitude+"\n");
            sb.append("  lng: "+s.longitude+"\n");
            sb.append("  x: "+s.center.x()+"\n");
            sb.append("  y: "+s.center.y()+"\n");
            sb.append("  z: "+s.center.z()+"\n");
            sb.append("  rotx: "+s.rotationFromNominal.x()+"\n");
            sb.append("  roty: "+s.rotationFromNominal.y()+"\n");
            sb.append("  rotz: "+s.rotationFromNominal.z()+"\n");

            ObservationStats ostats = EarthShape.fitOfObservations(s);
            if (ostats == null) {
                sb.append("  No obs stats\n");
            }
            else {
                sb.append("  maxSep: "+ostats.maxSeparation+"\n");
                sb.append("  sqrtVar: "+(float)Math.sqrt(ostats.variance)+"\n");
                sb.append("  var: "+ostats.variance+"\n");

                // What do we recommend to improve the variance?  If it is
                // already zero, nothing.  Otherwise, start by thinking we
                // should decrease the rotation angle.
                char recommendation = (ostats.variance == 0? ' ' : '-');

                // What is the best rotation command, and what does it achieve?
                RotationCommand bestRC = null;
                float bestNewVariance = 0;

                // Print the effects of all the available rotations.
                sb.append("\n");
                for (RotationCommand rc : RotationCommand.values()) {
                    sb.append("  adj("+rc.key+"): ");
                    ObservationStats newStats = EarthShape.fitOfAdjustedSquare(s,
                        rc.axis.times(this.adjustOrientationDegrees));
                    if (newStats == null) {
                        sb.append("(none)\n");
                    }
                    else {
                        float newVariance = newStats.variance;
                        sb.append(""+newVariance+"\n");
                        if (newVariance < ostats.variance &&
                            (bestRC == null || newVariance < bestNewVariance))
                        {
                            bestRC = rc;
                            bestNewVariance = newVariance;
                        }
                    }
                }

                // Make a final recommendation.
                this.recommendedRotationCommand = bestRC;
                if (bestRC != null) {
                    recommendation = bestRC.key;
                }
                sb.append("  recommend: "+recommendation+"\n");
            }
        }

        sb.append("\n");
        sb.append("adjDeg: "+this.adjustOrientationDegrees+"\n");

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
