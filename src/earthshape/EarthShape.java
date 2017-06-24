// EarthShape.java
// See copyright.txt for license and terms of use.

package earthshape;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.GLCapabilities;

import util.FloatUtil;
import util.Vector3d;
import util.Vector3f;
import util.swing.ModalDialog;
import util.swing.MyJFrame;
import util.swing.MySwingWorker;
import util.swing.ProgressDialog;

import static util.swing.SwingUtil.log;

/** This application demonstrates a procedure for inferring the shape
  * of a surface (such as the Earth) based on the observed locations of
  * stars from various locations at a fixed point in time.
  *
  * This class, EarthShape, manages UI components external to the 3D
  * display, such as the menu and status bars.  It also contains all of
  * the code to construct the virtual 3D map using various algorithms.
  * The 3D display, along with its camera controls, is in EarthMapCanvas. */
public class EarthShape extends MyJFrame {
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

    // ---------- Instance variables ----------
    // ---- Observation Information ----
    /** The observations that will drive surface reconstruction.
      * By default, this will be data from the real world, but it
      * can be swapped out at the user's option. */
    public WorldObservations worldObservations = new RealWorldObservations();

    /** Set of stars that are enabled. */
    private LinkedHashMap<String, Boolean> enabledStars = new LinkedHashMap<String, Boolean>();

    // ---- Interactive surface construction state ----
    /** The square we will build upon when the next square is added.
      * This may be null. */
    private SurfaceSquare activeSquare;

    /** When adjusting the orientation of the active square, this
      * is how many degrees to rotate at once. */
    private float adjustOrientationDegrees = DEFAULT_ADJUST_ORIENTATION_DEGREES;

    // ---- Options ----
    /** When true, star observations are only compared by their
      * direction.  When false, we also consider the location of the
      * observer, which allows us to handle nearby objects. */
    public boolean assumeInfiniteStarDistance = false;

    /** When true, star observations are only compared by their
      * direction, and furthermore, only the elevation, ignoring
      * azimuth.  This is potentially interesting because, in
      * practice, it is difficult to accurately measure azimuth
      * with just a hand-held sextant. */
    public boolean onlyCompareElevations = false;

    /** When analyzing the solution space, use this many points of
      * rotation on each side of 0, for each axis.  Note that the
      * algorithm is cubic in this parameter. */
    private int solutionAnalysisPointsPerSide = 20;

    /** If the Sun's elevation is higher than this value, then
      * we cannot see any stars. */
    private float maximumSunElevation = -5;

    /** When true, take the Sun's elevation into account. */
    private boolean useSunElevation = true;

    /** When true, use the "new" orientation algorithm that
      * repeatedly applies the recommended command.  Otherwise,
      * use the older one based on average deviation.  The old
      * algorithm is faster, but slightly less accurate, and
      * does not mimic the process a user would use to manually
      * adjust a square's orientation. */
    private boolean newAutomaticOrientationAlgorithm = true;

    // ---- Widgets ----
    /** Canvas showing the Earth surface built so far. */
    private EarthMapCanvas emCanvas;

    /** Widget to show various state variables such as camera position. */
    private JLabel statusLabel;

    /** Selected square info panel on right side. */
    private InfoPanel infoPanel;

    // Menu items to toggle various options.
    private JCheckBoxMenuItem drawCoordinateAxesCBItem;
    private JCheckBoxMenuItem drawCrosshairCBItem;
    private JCheckBoxMenuItem drawWireframeSquaresCBItem;
    private JCheckBoxMenuItem drawCompassesCBItem;
    private JCheckBoxMenuItem drawSurfaceNormalsCBItem;
    private JCheckBoxMenuItem drawCelestialNorthCBItem;
    private JCheckBoxMenuItem drawStarRaysCBItem;
    private JCheckBoxMenuItem drawUnitStarRaysCBItem;
    private JCheckBoxMenuItem drawBaseSquareStarRaysCBItem;
    private JCheckBoxMenuItem drawTravelPathCBItem;
    private JCheckBoxMenuItem drawActiveSquareAtOriginCBItem;
    private JCheckBoxMenuItem useSunElevationCBItem;
    private JCheckBoxMenuItem invertHorizontalCameraMovementCBItem;
    private JCheckBoxMenuItem invertVerticalCameraMovementCBItem;
    private JCheckBoxMenuItem newAutomaticOrientationAlgorithmCBItem;
    private JCheckBoxMenuItem assumeInfiniteStarDistanceCBItem;
    private JCheckBoxMenuItem onlyCompareElevationsCBItem;
    private JCheckBoxMenuItem drawWorldWireframeCBItem;
    private JCheckBoxMenuItem drawWorldStarsCBItem;
    private JCheckBoxMenuItem drawSkyboxCBItem;

    // ---------- Methods ----------
    public EarthShape()
    {
        super("EarthShape");
        this.setName("EarthShape (JFrame)");

        this.setLayout(new BorderLayout());
        this.setIcon();

        for (String starName : this.worldObservations.getAllStars()) {
            // Initially all stars are enabled.
            this.enabledStars.put(starName, true);
        }

        this.setSize(1150, 800);
        this.setLocationByPlatform(true);

        this.setupJOGL();

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

    /** Set the window icon to my application's icon. */
    private void setIcon()
    {
        try {
            URL url = this.getClass().getResource("globe-icon.png");
            Image img = Toolkit.getDefaultToolkit().createImage(url);
            this.setIconImage(img);
        }
        catch (Exception e) {
            System.err.println("Failed to set app icon: "+e.getMessage());
        }
    }

    private void showAboutBox()
    {
        String about =
            "EarthShape\n"+
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
            "1   - Reset adjustment amount to 1 degree.\n"+
            "-/= - Decrease or increase adjustment amount.\n"+
            "; (semicolon) - Make recommended active square adjustment.\n"+
            "/ (slash) - Automatically orient active square.\n"+
            "\' (apostrophe) - Analyze rotation solution space for active square.\n"+
            "\n"+
            "N - Start a new surface.\n"+
            "M - Add a square adjacent to the active square.\n"+
            "Ctrl+N/S/E/W - Add a square to the N/S/E/W and automatically adjust it.\n"+
            ", (comma) - Move to previous active square.\n"+
            ". (period) - Move to next active square.\n"+
            "Delete - Delete active square.\n"+
            "\n"+
            "0/PgUp/PgDn - Change animation state (not for general use)\n"+
            "";
        JOptionPane.showMessageDialog(this, bindings, "Bindings", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Build the menu bar and attach it to 'this'. */
    private void buildMenuBar()
    {
        // This ensures that the menu items do not appear underneath
        // the GL canvas.  Strangely, this problem appeared suddenly,
        // after I made a seemingly irrelevant change (putting a
        // scroll bar on the info panel).  But this call solves it,
        // so whatever.
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);

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
        //   0 - Reset animation state to 0
        //   1 - Reset adjustment amount to 1
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
        //   Ins    - canned commands
        //   PgUp   - Decrement animation state
        //   PgDn   - Increment animation state
        //   Ctrl+E - build and orient to the East
        //   Ctrl+W - build and orient to the West
        //   Ctrl+N - build and orient to the North
        //   Ctrl+S - build and orient to the South

        menuBar.add(this.buildFileMenu());
        menuBar.add(this.buildDrawMenu());
        menuBar.add(this.buildBuildMenu());
        menuBar.add(this.buildSelectMenu());
        menuBar.add(this.buildEditMenu());
        menuBar.add(this.buildNavigateMenu());
        menuBar.add(this.buildAnimateMenu());
        menuBar.add(this.buildOptionsMenu());
        menuBar.add(this.buildHelpMenu());
    }

    private JMenu buildFileMenu()
    {
        JMenu menu = new JMenu("File");

        addMenuItem(menu, "Use real world astronomical observations", null, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                EarthShape.this.changeObservations(new RealWorldObservations());
            }
        });

        menu.addSeparator();

        addMenuItem(menu, "Use model: spherical Earth with nearby stars", null, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                EarthShape.this.changeObservations(new CloseStarObservations());
            }
        });
        addMenuItem(menu, "Use model: azimuthal equidistant projection flat Earth", null, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                EarthShape.this.changeObservations(new AzimuthalEquidistantObservations());
            }
        });
        addMenuItem(menu, "Use model: bowl-shaped Earth", null, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                EarthShape.this.changeObservations(new BowlObservations());
            }
        });
        addMenuItem(menu, "Use model: saddle-shaped Earth", null, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                EarthShape.this.changeObservations(new SaddleObservations());
            }
        });

        menu.addSeparator();

        addMenuItem(menu, "Exit", null, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                EarthShape.this.dispose();
            }
        });

        return menu;
    }

    private JMenu buildDrawMenu()
    {
        JMenu drawMenu = new JMenu("Draw");
        this.drawCoordinateAxesCBItem =
            addCBMenuItem(drawMenu, "Draw X/Y/Z coordinate axes", null,
                this.emCanvas.drawAxes,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.toggleDrawAxes();
                    }
                });
        this.drawCrosshairCBItem =
            addCBMenuItem(drawMenu, "Draw crosshair when in FPS camera mode", null,
                this.emCanvas.drawCrosshair,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.toggleDrawCrosshair();
                    }
                });
        this.drawWireframeSquaresCBItem =
            addCBMenuItem(drawMenu, "Draw squares as wireframes with translucent squares", null,
                this.emCanvas.drawWireframeSquares,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.toggleDrawWireframeSquares();
                    }
                });
        this.drawCompassesCBItem =
            addCBMenuItem(drawMenu, "Draw squares with compass texture (vs. world map)",
                KeyStroke.getKeyStroke('c'),
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
        this.drawUnitStarRaysCBItem =
            addCBMenuItem(drawMenu, "Draw star rays as unit vectors", null,
                this.emCanvas.drawUnitStarRays,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.toggleDrawUnitStarRays();
                    }
                });
        this.drawBaseSquareStarRaysCBItem =
            addCBMenuItem(drawMenu, "Draw star rays for the base square too, on the active square", null,
                this.emCanvas.drawBaseSquareStarRays,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.toggleDrawBaseSquareStarRays();
                    }
                });
        this.drawTravelPathCBItem =
            addCBMenuItem(drawMenu, "Draw the travel path from base square to active square", null,
                this.emCanvas.drawTravelPath,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.toggleDrawTravelPath();
                    }
                });
        this.drawActiveSquareAtOriginCBItem =
            addCBMenuItem(drawMenu, "Draw the active square at the 3D coordinate origin", null,
                this.emCanvas.drawActiveSquareAtOrigin,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.toggleDrawActiveSquareAtOrigin();
                    }
                });
        this.drawWorldWireframeCBItem =
            addCBMenuItem(drawMenu, "Draw world wireframe (if one is in use)", null,
                this.emCanvas.drawWorldWireframe,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.toggleDrawWorldWireframe();
                    }
                });
        this.drawWorldStarsCBItem =
            addCBMenuItem(drawMenu, "Draw world stars (if in use)", null,
                this.emCanvas.drawWorldStars,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.toggleDrawWorldStars();
                    }
                });
        this.drawSkyboxCBItem =
            addCBMenuItem(drawMenu, "Draw skybox", null,
                this.emCanvas.drawSkybox,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.toggleDrawSkybox();
                    }
                });
        addMenuItem(drawMenu, "Set skybox distance...", null,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.setSkyboxDistance();
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
        addMenuItem(buildMenu, "Build Earth using active observational data or model",
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
                    EarthShape.this.buildSphericalEarthSurfaceWithLatLong();
                }
            });
        addMenuItem(buildMenu, "Build partial Earth using assumed sphere and random walk",
            KeyStroke.getKeyStroke('r'),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.buildSphericalEarthWithRandomWalk();
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

        buildMenu.addSeparator();

        addMenuItem(buildMenu, "Create new square 9 degrees to the East and orient it automatically",
            KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.createAndAutomaticallyOrientActiveSquare(
                        0 /*deltLatitude*/, +9 /*deltaLongitude*/);
                }
            });
        addMenuItem(buildMenu, "Create new square 9 degrees to the West and orient it automatically",
            KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.createAndAutomaticallyOrientActiveSquare(
                        0 /*deltLatitude*/, -9 /*deltaLongitude*/);
                }
            });
        addMenuItem(buildMenu, "Create new square 9 degrees to the North and orient it automatically",
            KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.createAndAutomaticallyOrientActiveSquare(
                        +9 /*deltLatitude*/, 0 /*deltaLongitude*/);
                }
            });
        addMenuItem(buildMenu, "Create new square 9 degrees to the South and orient it automatically",
            KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.createAndAutomaticallyOrientActiveSquare(
                        -9 /*deltLatitude*/, 0 /*deltaLongitude*/);
                }
            });

        buildMenu.addSeparator();

        addMenuItem(buildMenu, "Do some canned setup for debugging",
            KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.doCannedSetup();
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
        JMenu menu = new JMenu("Navigate");
        addMenuItem(menu, "Control camera like a first-person shooter",
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.emCanvas.enterFPSMode();
                }
            });
        addMenuItem(menu, "Leave first-person shooter mode",
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.emCanvas.exitFPSMode();
                }
            });
        addMenuItem(menu, "Go to active square's center",
            KeyStroke.getKeyStroke('g'),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.goToActiveSquareCenter();
                }
            });
        addMenuItem(menu, "Go to origin", null,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.moveCamera(new Vector3f(0,0,0));
                }
            });

        menu.addSeparator();

        addMenuItem(menu, "Curvature calculator...", null,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.showCurvatureDialog();
                }
            });


        return menu;
    }

    private JMenu buildAnimateMenu()
    {
        JMenu menu = new JMenu("Animate");

        addMenuItem(menu, "Reset to animation state 0",
            KeyStroke.getKeyStroke('0'),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.setAnimationState(0);
                }
            });
        addMenuItem(menu, "Increment animation state",
            KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.setAnimationState(+1);
                }
            });
        addMenuItem(menu, "Decrement animation state",
            KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0),
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.setAnimationState(-1);
                }
            });

        return menu;
    }

    private JMenu buildOptionsMenu()
    {
        JMenu menu = new JMenu("Options");

        addMenuItem(menu, "Choose enabled stars...", null, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                EarthShape.this.chooseEnabledStars();
            }
        });
        addMenuItem(menu, "Set maximum Sun elevation...",
            null,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    EarthShape.this.setMaximumSunElevation();
                }
            });
        this.useSunElevationCBItem =
            addCBMenuItem(menu, "Take Sun elevation into account", null,
                this.useSunElevation,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.useSunElevation =
                            !EarthShape.this.useSunElevation;
                        EarthShape.this.updateUIState();
                    }
                });

        menu.addSeparator();

        this.invertHorizontalCameraMovementCBItem =
            addCBMenuItem(menu, "Invert horizontal camera movement", null,
                this.emCanvas.invertHorizontalCameraMovement,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.emCanvas.invertHorizontalCameraMovement =
                            !EarthShape.this.emCanvas.invertHorizontalCameraMovement;
                        EarthShape.this.updateUIState();
                    }
                });
        this.invertVerticalCameraMovementCBItem =
            addCBMenuItem(menu, "Invert vertical camera movement", null,
                this.emCanvas.invertVerticalCameraMovement,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.emCanvas.invertVerticalCameraMovement =
                            !EarthShape.this.emCanvas.invertVerticalCameraMovement;
                        EarthShape.this.updateUIState();
                    }
                });

        menu.addSeparator();

        this.newAutomaticOrientationAlgorithmCBItem =
            addCBMenuItem(menu, "Use new automatic orientation algorithm", null,
                this.newAutomaticOrientationAlgorithm,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.newAutomaticOrientationAlgorithm =
                            !EarthShape.this.newAutomaticOrientationAlgorithm;
                        EarthShape.this.updateUIState();
                    }
                });
        this.assumeInfiniteStarDistanceCBItem =
            addCBMenuItem(menu, "Assume stars are infinitely far away", null,
                this.assumeInfiniteStarDistance,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.assumeInfiniteStarDistance =
                            !EarthShape.this.assumeInfiniteStarDistance;
                        EarthShape.this.updateAndRedraw();
                    }
                });
        this.onlyCompareElevationsCBItem =
            addCBMenuItem(menu, "Only compare star elevations", null,
                this.onlyCompareElevations,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        EarthShape.this.onlyCompareElevations =
                            !EarthShape.this.onlyCompareElevations;
                        EarthShape.this.updateAndRedraw();
                    }
                });

        return menu;
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

    /** Choose the set of stars to use.  This only affects new
      * squares. */
    private void chooseEnabledStars()
    {
        StarListDialog d = new StarListDialog(this, this.enabledStars);
        if (d.exec()) {
            this.enabledStars = d.stars;
            this.updateAndRedraw();
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
      * and longitude pairs and assuming a spherical Earth.  It
      * assumes the Earth is a sphere. */
    public void buildSphericalEarthSurfaceWithLatLong()
    {
        log("building spherical Earth");
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
            outer = this.addSphericallyAdjacentSquare(outer, latitude, startLongitude);

            // Inner loop: Walk East until we get back to
            // the same longitude.
            float longitude = startLongitude;
            float prevLongitude = longitude;
            SurfaceSquare inner = outer;
            while (true) {
                inner = this.addSphericallyAdjacentSquare(inner, latitude, longitude);
                if (prevLongitude < outer.longitude &&
                                    outer.longitude <= longitude) {
                    break;
                }
                prevLongitude = longitude;
                longitude = FloatUtil.modulus2f(longitude+9, -180, 180);
            }
        }

        // Outer loop 2: Walk South as far as we can.
        outer = startSquare;
        for (float latitude = startLatitude - 9;
             latitude > -90;
             latitude -= 9)
        {
            // Go North another step.
            outer = this.addSphericallyAdjacentSquare(outer, latitude, startLongitude);

            // Inner loop: Walk East until we get back to
            // the same longitude.
            float longitude = startLongitude;
            float prevLongitude = longitude;
            SurfaceSquare inner = outer;
            while (true) {
                inner = this.addSphericallyAdjacentSquare(inner, latitude, longitude);
                if (prevLongitude < outer.longitude &&
                                    outer.longitude <= longitude) {
                    break;
                }
                prevLongitude = longitude;
                longitude = FloatUtil.modulus2f(longitude+9, -180, 180);
            }
        }

        this.emCanvas.redrawCanvas();
        log("finished building Earth; nsquares="+this.emCanvas.numSurfaceSquares());
    }

    /** Build the surface by walking randomly from a starting location,
      * assuming a Earth is a sphere. */
    public void buildSphericalEarthWithRandomWalk()
    {
        log("building spherical Earth by random walk");
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
            square = this.addSphericallyAdjacentSquare(square,
                FloatUtil.clampf(square.latitude + deltaLatitude, -80, 80),
                FloatUtil.modulus2f(square.longitude + deltaLongitude, -180, 180));
        }

        this.emCanvas.redrawCanvas();
        log("finished building Earth; nsquares="+this.emCanvas.numSurfaceSquares());
    }

    /** Given square 'old', add an adjacent square at the given
      * latitude and longitude.  The relative orientation of the
      * new square will determined using the latitude and longitude,
      * assuming a spherical shape for the Earth.
      *
      * This is used by the routines that build the surface using
      * the sphere assumption, not those that use star observation
      * data. */
    private SurfaceSquare addSphericallyAdjacentSquare(
        SurfaceSquare old,
        float newLatitude,
        float newLongitude)
    {
        // Calculate local East for 'old'.
        Vector3f oldEast = old.north.cross(old.up).normalize();

        // Calculate celestial North for 'old', which is given by
        // the latitude plus geographic North.
        Vector3f celestialNorth =
            old.north.rotateDeg(old.latitude, oldEast);

        // Get lat/long deltas.
        float deltaLatitude = newLatitude - old.latitude;
        float deltaLongitude = FloatUtil.modulus2f(
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
        Cursor oldCursor = this.getCursor();
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            ProgressDialog<Void, Void> pd =
                new ProgressDialog<Void, Void>(this,
                    "Building Surface with model: "+this.worldObservations.getDescription(),
                    new BuildSurfaceTask(EarthShape.this));
            pd.exec();
        }
        finally {
            this.setCursor(oldCursor);
        }
        this.emCanvas.redrawCanvas();
    }

    /** Task to manage construction of surface.
      *
      * I have to use a class rather than a simple closure so I have
      * something to pass to the build routines so they can set the
      * status and progress as the algorithm runs. */
    private static class BuildSurfaceTask extends MySwingWorker<Void, Void>
    {
        private EarthShape earthShape;

        public BuildSurfaceTask(EarthShape earthShape_)
        {
            this.earthShape = earthShape_;
        }

        protected Void doTask() throws Exception
        {
            this.earthShape.buildEarthSurfaceFromStarDataInner(this);
            return null;
        }
    }

    /** Core of 'buildEarthSurfaceFromStarData', so I can more easily
      * wrap computation around it. */
    public void buildEarthSurfaceFromStarDataInner(BuildSurfaceTask task)
    {
        log("building Earth using star data: "+this.worldObservations.getDescription());
        this.clearSurfaceSquares();

        // Size of squares to build, in km.
        float sizeKm = 1000;

        // Start at approximately my location in SF, CA.  This is one of
        // the locations for which I have manual data, and when we build
        // the first latitude strip, that will pick up the other manual
        // data points.
        float latitude = 38;
        float longitude = -122;
        SurfaceSquare square = null;
        log("buildEarth: building first square at lat="+latitude+" long="+longitude);

        // First square will be placed at the 3D origin with
        // its North pointed along the -Z axis.
        square = new SurfaceSquare(
            new Vector3f(0,0,0),      // center
            new Vector3f(0,0,-1),     // north
            new Vector3f(0,1,0),      // up
            sizeKm,
            latitude,
            longitude,
            null /*base*/, null /*midpoint*/,
            new Vector3f(0,0,0));
        this.emCanvas.addSurfaceSquare(square);
        this.addMatchingData(square);

        // Go East and West.
        task.setStatus("Initial latitude strip at "+square.latitude);
        this.buildLatitudeStrip(square, +9);
        this.buildLatitudeStrip(square, -9);

        // Explore in all directions until all points on
        // the surface have been explored (to within 9 degrees).
        this.buildLongitudeStrip(square, +9, task);
        this.buildLongitudeStrip(square, -9, task);

        // Reset the adjustment angle.
        this.adjustOrientationDegrees = EarthShape.DEFAULT_ADJUST_ORIENTATION_DEGREES;

        log("buildEarth: finished using star data; nSquares="+this.emCanvas.numSurfaceSquares());
    }

    /** Build squares by going North or South from a starting square
      * until we add 20 or we can't add any more.  At each spot, also
      * build latitude strips in both directions. */
    private void buildLongitudeStrip(SurfaceSquare startSquare, float deltaLatitude,
        BuildSurfaceTask task)
    {
        float curLatitude = startSquare.latitude;
        float curLongitude = startSquare.longitude;
        SurfaceSquare curSquare = startSquare;

        if (task.isCancelled()) {
            // Bail now, rather than repeat the cancellation log message.
            return;
        }

        while (!task.isCancelled()) {
            float newLatitude = curLatitude + deltaLatitude;
            if (!( -90 < newLatitude && newLatitude < 90 )) {
                // Do not go past the poles.
                break;
            }
            float newLongitude = curLongitude;

            log("buildEarth: building lat="+newLatitude+" long="+newLongitude);

            // Report progress.
            task.setStatus("Latitude strip at "+newLatitude);
            {
                // +1 since we did one strip before the first call to
                // buildLongitudeStrip.
                float totalStrips = 180 / (float)Math.abs(deltaLatitude) + 1;
                float completedStrips;
                if (deltaLatitude > 0) {
                    completedStrips = (newLatitude - startSquare.latitude) / deltaLatitude + 1;
                }
                else {
                    completedStrips = (90 - newLatitude) / -deltaLatitude + 1;
                }
                float fraction = completedStrips / totalStrips;
                log("progress fraction: "+fraction);
                task.setProgressFraction(fraction);
            }

            curSquare = this.createAndAutomaticallyOrientSquare(curSquare,
                newLatitude, newLongitude);
            if (curSquare == null) {
                log("buildEarth: could not place next square!");
                break;
            }

            curLatitude = newLatitude;
            curLongitude = newLongitude;

            // Also build strips in each direction.
            this.buildLatitudeStrip(curSquare, +9);
            this.buildLatitudeStrip(curSquare, -9);
        }

        if (task.isCancelled()) {
            log("surface construction canceled");
        }
    }

    /** Build squares by going East or West from a starting square
      * until we add 20 or we can't add any more. */
    private void buildLatitudeStrip(SurfaceSquare startSquare, float deltaLongitude)
    {
        float curLatitude = startSquare.latitude;
        float curLongitude = startSquare.longitude;
        SurfaceSquare curSquare = startSquare;

        for (int i=0; i < 20; i++) {
            float newLatitude = curLatitude;
            float newLongitude = FloatUtil.modulus2f(curLongitude + deltaLongitude, -180, 180);
            log("buildEarth: building lat="+newLatitude+" long="+newLongitude);

            curSquare = this.createAndAutomaticallyOrientSquare(curSquare,
                newLatitude, newLongitude);
            if (curSquare == null) {
                log("buildEarth: could not place next square!");
                break;
            }

            curLatitude = newLatitude;
            curLongitude = newLongitude;
        }
    }

    /** Create a square adjacent to 'old', positioned at the given latitude
      * and longitude, with orientation changed by 'rotation'.  If there is
      * no change, return null.  Even if not, do not add the square yet,
      * just return it. */
    private SurfaceSquare createRotatedAdjacentSquare(
        SurfaceSquare old,
        float newLatitude,
        float newLongitude,
        Vector3f rotation)
    {
        // Normalize latitude and longitude.
        newLatitude = FloatUtil.clampf(newLatitude, -90, 90);
        newLongitude = FloatUtil.modulus2f(newLongitude, -180, 180);

        // If we didn't move, return null.
        if (old.latitude == newLatitude && old.longitude == newLongitude) {
            return null;
        }

        // Compute the new orientation vectors by rotating
        // the old ones by the given amount.
        Vector3f newNorth = old.north.rotateAADeg(rotation);
        Vector3f newUp = old.up.rotateAADeg(rotation);

        // Get observed travel details going to the new location.
        TravelObservation tobs = this.worldObservations.getTravelObservation(
            old.latitude, old.longitude, newLatitude, newLongitude);

        // For both old and new, calculate a unit vector for the
        // travel direction.  Both headings are negated due to the
        // right hand rule for rotation.  The new to old heading is
        // then flipped 180 since I want both to indicate the local
        // direction from old to new.
        Vector3f oldTravel = old.north.rotateDeg(-tobs.startToEndHeading, old.up);
        Vector3f newTravel = newNorth.rotateDeg(-tobs.endToStartHeading + 180, newUp);

        // Calculate the new square's center by going half the distance
        // according to the old orientation and then half the distance
        // according to the new orientation, in world coordinates.
        float halfDistWorld = tobs.distanceKm / 2.0f * EarthMapCanvas.SPACE_UNITS_PER_KM;
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
        SurfaceSquare ret = this.createRotatedAdjacentSquare(
            old, newLatitude, newLongitude, rotation);
        if (ret == null) {
            return old;        // Did not move.
        }
        else {
            this.emCanvas.addSurfaceSquare(ret);
        }
        return ret;
    }

    /** Get star observations for the given location, at the particular
      * point in time that I am using for everything. */
    private List<StarObservation> getStarObservationsFor(
        float latitude, float longitude)
    {
        return this.worldObservations.getStarObservations(
            StarObservation.unixTimeOfManualData, latitude, longitude);
    }

    /** Add to 'square.starObs' all entries of 'starObs' that have
      * the same latitude and longitude, and also are at least
      * 20 degrees above the horizon. */
    private void addMatchingData(SurfaceSquare square)
    {
        for (StarObservation so :
                 this.getStarObservationsFor(square.latitude, square.longitude)) {
            if (this.qualifyingStarObservation(so)) {
                square.addObservation(so);
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
        float newLatitude,
        float newLongitude)
    {
        // Set of stars visible at the start and end squares and
        // above 20 degrees above the horizon.
        HashMap<String, Vector3f> startStars =
            getVisibleStars(startSquare.latitude, startSquare.longitude);
        HashMap<String, Vector3f> endStars =
            getVisibleStars(newLatitude, newLongitude);

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
                startVector = startVector.rotateAADeg(startSquare.rotationFromNominal);
                endVector = endVector.rotateAADeg(startSquare.rotationFromNominal);

                // Calculate a difference rotation vector from the
                // rotated end vector to the start vector.  Rotating
                // the end star in one direction is like rotating
                // the start terrain in the opposite direction.
                Vector3f rot = endVector.rotateAADeg(currentRotation)
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
      * it is high enough in the sky, is enabled, and not obscured
      * by light from the Sun. */
    private boolean qualifyingStarObservation(StarObservation so)
    {
        if (this.sunIsTooHigh(so.latitude, so.longitude)) {
            return false;
        }

        return so.elevation >= 20.0f &&
               this.enabledStars.containsKey(so.name) &&
               this.enabledStars.get(so.name) == true;
    }

    /** Return true if, at StarObservation.unixTimeOfManualData, the
      * Sun is too high in the sky to see stars.  This depends on
      * the configurable parameter 'maximumSunElevation'. */
    private boolean sunIsTooHigh(float latitude, float longitude)
    {
        if (!this.useSunElevation) {
            return false;
        }

        StarObservation sun = this.worldObservations.getSunObservation(
            StarObservation.unixTimeOfManualData, latitude, longitude);
        if (sun == null) {
            return false;
        }

        return sun.elevation > this.maximumSunElevation;
    }

    /** For every visible star vislble at the specified coordinate
      * that has an elevation of at least 20 degrees,
      * add it to a map from star name to azEl vector. */
    private HashMap<String, Vector3f> getVisibleStars(
        float latitude,
        float longitude)
    {
        HashMap<String, Vector3f> ret = new HashMap<String, Vector3f>();

        for (StarObservation so :
                 this.getStarObservationsFor(latitude, longitude)) {
            if (this.qualifyingStarObservation(so)) {
                ret.put(so.name,
                    Vector3f.azimuthElevationToVector(so.azimuth, so.elevation));
            }
        }

        return ret;
    }

    /** Get the unit ray, in world coordinates, from the center of 'square' to
      * the star recorded in 'so', which was observed at this square. */
    public static Vector3f rayToStar(SurfaceSquare square, StarObservation so)
    {
        // Ray to star in nominal, -Z facing, coordinates.
        Vector3f nominalRay =
            Vector3f.azimuthElevationToVector(so.azimuth, so.elevation);

        // Ray to star in world coordinates, taking into account
        // how the surface is rotated.
        Vector3f worldRay = nominalRay.rotateAADeg(square.rotationFromNominal);

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
        public double variance;

        /** Maximum separation between observations, in degrees. */
        public double maxSeparation;

        /** Number of pairs of stars used in comparison. */
        public int numSamples;
    }

    /** Calculate variance and maximum separation for 'square'.  Returns
      * null if there is no base or there are no observations in common. */
    private ObservationStats fitOfObservations(SurfaceSquare square)
    {
        if (square.baseSquare == null) {
            return null;
        }

        double sumOfSquares = 0;
        int numSamples = 0;
        double maxSeparation = 0;

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
                double sep;
                if (this.assumeInfiniteStarDistance) {
                    sep = this.getStarRayDifference(
                        square.up, starRay, baseStarRay);
                }
                else {
                    sep = EarthShape.getModifiedClosestApproach(
                        square.center, starRay,
                        square.baseSquare.center, baseStarRay).separationAngleDegrees;
                }
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
            ret.numSamples = numSamples;
            return ret;
        }
    }

    /** Get closest approach, except with a modification to
      * smooth out the search space. */
    public static Vector3d.ClosestApproach getModifiedClosestApproach(
        Vector3f p1f, Vector3f u1f,
        Vector3f p2f, Vector3f u2f)
    {
        Vector3d p1 = new Vector3d(p1f);
        Vector3d u1 = new Vector3d(u1f);
        Vector3d p2 = new Vector3d(p2f);
        Vector3d u2 = new Vector3d(u2f);

        Vector3d.ClosestApproach ca = Vector3d.getClosestApproach(p1, u1, p2, u2);

        if (ca.line1Closest != null) {
            // Now, there is a problem if the closest approach is behind
            // either observer.  Not only does that not make logical sense,
            // but naively using the calculation will cause the search
            // space to be very lumpy, which creates local minima that my
            // hill-climbing algorithm gets trapped in.  So, we require
            // that the points on each observation line be at least one
            // unit away, which currently means 1000 km.  That smooths out
            // the search space so the hill climber will find its way to
            // the optimal solution more reliably.

            // How far along u1 is the closest approach?
            double m1 = ca.line1Closest.minus(p1).dot(u1);
            if (m1 < 1.0) {
                // That is unreasonably close.  Push the approach point
                // out to one unit away along u1.
                ca.line1Closest = p1.plus(u1);

                // Find the closest point on (p2,u2) to that point.
                ca.line2Closest = ca.line1Closest.closestPointOnLine(p2, u2);

                // Recalculate the separation angle to that point.
                ca.separationAngleDegrees = u1.separationAngleDegrees(ca.line2Closest.minus(p1));
            }

            // How far along u2?
            double m2 = ca.line2Closest.minus(p2).dot(u2);
            if (m2 < 1.0) {
                // Too close; push it.
                ca.line2Closest = p2.plus(u2);

                // What is closest on (p1,u1) to that?
                ca.line1Closest = ca.line2Closest.closestPointOnLine(p1, u1);

                // Re-check if that is too close to p1.
                if (ca.line1Closest.minus(p1).dot(u1) < 1.0) {
                    // Push it without changing line2Closest.
                    ca.line1Closest = p1.plus(u1);
                }

                // Recalculate the separation angle to that point.
                ca.separationAngleDegrees = u1.separationAngleDegrees(ca.line2Closest.minus(p1));
            }
        }

        return ca;
    }

    /** Get the difference between the two star rays, for a location
      * with given unit 'up' vector, in degrees.  This depends on the
      * option setting 'onlyCompareElevations'. */
    public double getStarRayDifference(
        Vector3f up,
        Vector3f ray1,
        Vector3f ray2)
    {
        if (this.onlyCompareElevations) {
            return EarthShape.getElevationDifference(up, ray1, ray2);
        }
        else {
            return ray1.separationAngleDegrees(ray2);
        }
    }

    /** Given two star observation rays at a location with the given
      * 'up' unit vector, return the difference in elevation between
      * them, ignoring azimuth, in degrees. */
    private static double getElevationDifference(
        Vector3f up,
        Vector3f ray1,
        Vector3f ray2)
    {
        double e1 = getElevation(up, ray1);
        double e2 = getElevation(up, ray2);
        return Math.abs(e1-e2);
    }

    /** Return the elevation of 'ray' at a location with unit 'up'
      * vector, in degrees. */
    private static double getElevation(Vector3f up, Vector3f ray)
    {
        // Decompose into vertical and horizontal components.
        Vector3f v = ray.projectOntoUnitVector(up);
        Vector3f h = ray.minus(v);

        // Get lengths, with vertical possibly negative if below
        // horizon.
        double vLen = ray.dot(up);
        double hLen = h.length();

        // Calculate corresponding angle.
        return FloatUtil.atan2Deg(vLen, hLen);
    }

    /** Begin constructing a new surface using star data.  This just
      * places down the initial square to represent a user-specified
      * latitude and longitude.  The square is placed into 3D space
      * at a fixed location. */
    public void startNewSurface()
    {
        LatLongDialog d = new LatLongDialog(this, 38, -122);
        if (d.exec()) {
            this.startNewSurfaceAt(d.finalLatitude, d.finalLongitude);
        }
    }

    /** Same as 'startNewSurface' but at a specified location. */
    public void startNewSurfaceAt(float latitude, float longitude)
    {
        log("starting new surface at lat="+latitude+", lng="+longitude);
        this.clearSurfaceSquares();
        this.setActiveSquare(new SurfaceSquare(
            new Vector3f(0,0,0),      // center
            new Vector3f(0,0,-1),     // north
            new Vector3f(0,1,0),      // up
            INITIAL_SQUARE_SIZE_KM,
            latitude,
            longitude,
            null /*base*/, null /*midpoint*/,
            new Vector3f(0,0,0)));
        this.addMatchingData(this.activeSquare);
        this.emCanvas.addSurfaceSquare(this.activeSquare);
        this.emCanvas.redrawCanvas();
    }

    /** Get the active square, or null if none. */
    public SurfaceSquare getActiveSquare()
    {
        return this.activeSquare;
    }

    /** Change which square is active, but do not trigger a redraw. */
    public void setActiveSquareNoRedraw(SurfaceSquare sq)
    {
        if (this.activeSquare != null) {
            this.activeSquare.showAsActive = false;
        }
        this.activeSquare = sq;
        if (this.activeSquare != null) {
            this.activeSquare.showAsActive = true;
        }
    }

    /** Change which square is active. */
    public void setActiveSquare(SurfaceSquare sq)
    {
        this.setActiveSquareNoRedraw(sq);
        this.updateAndRedraw();
    }

    /** Add another square to the surface by building one adjacent
      * to the active square. */
    private void buildNextSquare()
    {
        if (this.activeSquare == null) {
            this.errorBox("No square is active.");
            return;
        }

        LatLongDialog d = new LatLongDialog(this,
            this.activeSquare.latitude, this.activeSquare.longitude + 9);
        if (d.exec()) {
            this.buildNextSquareAt(d.finalLatitude, d.finalLongitude);
        }
    }

    /** Same as 'buildNextSquare' except at a specified location. */
    private void buildNextSquareAt(float latitude, float longitude)
    {
        // The new square should draw star rays if the old did.
        boolean drawStarRays = this.activeSquare.drawStarRays;

        // Add it initially with no rotation.  My plan is to add
        // the rotation interactively afterward.
        this.setActiveSquare(
            this.addRotatedAdjacentSquare(this.activeSquare,
                latitude, longitude, new Vector3f(0,0,0)));
        this.activeSquare.drawStarRays = drawStarRays;

        // Reset the rotation angle after adding a square.
        this.adjustOrientationDegrees = DEFAULT_ADJUST_ORIENTATION_DEGREES;

        this.addMatchingData(this.activeSquare);
        this.emCanvas.redrawCanvas();
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
            this.errorBox("No active square.");
            return;
        }
        SurfaceSquare base = derived.baseSquare;
        if (base == null) {
            this.errorBox("The active square has no base square.");
            return;
        }

        // Replace the active square.
        this.setActiveSquare(
            this.adjustDerivedSquareOrientation(axis, derived, this.adjustOrientationDegrees));

        this.emCanvas.redrawCanvas();
    }

    /** Adjust the orientation of 'derived' by 'adjustDegrees' around
      * 'axis', where 'axis' is relative to the square's current
      * orientation. */
    private SurfaceSquare adjustDerivedSquareOrientation(Vector3f axis,
        SurfaceSquare derived, float adjustDegrees)
    {
        SurfaceSquare base = derived.baseSquare;

        // Rotate by 'adjustOrientationDegrees'.
        Vector3f angleAxis = axis.times(adjustDegrees);

        // Rotate the axis to align it with the square.
        angleAxis = angleAxis.rotateAADeg(derived.rotationFromNominal);

        // Now add that to the square's existing rotation relative
        // to its base square.
        angleAxis = Vector3f.composeRotations(derived.rotationFromBase, angleAxis);

        // Now, replace it.
        return this.replaceWithNewRotation(base, derived, angleAxis);
    }

    /** Replace the square 'derived', with a new square that
      * is computed from 'base' by applying 'newRotation'.
      * Return the new square. */
    public SurfaceSquare replaceWithNewRotation(
        SurfaceSquare base, SurfaceSquare derived, Vector3f newRotation)
    {
        // Replace the derived square with a new one created by
        // rotating from the same base by this new amount.
        this.emCanvas.removeSurfaceSquare(derived);
        SurfaceSquare ret =
            this.addRotatedAdjacentSquare(base,
                derived.latitude, derived.longitude, newRotation);

        // Copy some other data from the derived square that we
        // are in the process of discarding.
        ret.drawStarRays = derived.drawStarRays;
        ret.starObs = derived.starObs;

        return ret;
    }

    /** Calculate what the variation of observations would be for
      * 'derived' if its orientation were adjusted by
      * 'angleAxis.degrees()' around 'angleAxis'.  Returns null if
      * the calculation cannot be done because of missing information. */
    private ObservationStats fitOfAdjustedSquare(
        SurfaceSquare derived, Vector3f angleAxis)
    {
        // This part mirrors 'adjustActiveSquareOrientation'.
        SurfaceSquare base = derived.baseSquare;
        if (base == null) {
            return null;
        }
        angleAxis = angleAxis.rotateAADeg(derived.rotationFromNominal);
        angleAxis = Vector3f.composeRotations(derived.rotationFromBase, angleAxis);

        // Now, create a new square with this new rotation.
        SurfaceSquare newSquare =
            this.createRotatedAdjacentSquare(base,
                derived.latitude, derived.longitude, angleAxis);
        if (newSquare == null) {
            // If we do not move, use the original square's data.
            return this.fitOfObservations(derived);
        }

        // Copy the observation data since that is needed to calculate
        // the deviation.
        newSquare.starObs = derived.starObs;

        // Now calculate the new variance.
        return this.fitOfObservations(newSquare);
    }

    /** Like 'fitOfAdjustedSquare' except only retrieves the
      * variance.  This returns 40000 if the data is unavailable. */
    private double varianceOfAdjustedSquare(
        SurfaceSquare derived, Vector3f angleAxis)
    {
        ObservationStats os = this.fitOfAdjustedSquare(derived, angleAxis);
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
            this.errorBox("No active square.");
            return;
        }
        ObservationStats ostats = this.fitOfObservations(s);
        if (ostats == null) {
            this.errorBox("Not enough observational data available.");
            return;
        }
        if (ostats.variance == 0) {
            this.errorBox("Orientation is already optimal.");
            return;
        }

        // Get the recommended rotation.
        VarianceAfterRotations var = this.getVarianceAfterRotations(s,
            this.adjustOrientationDegrees);
        if (var.bestRC == null) {
            if (this.adjustOrientationDegrees <= MINIMUM_ADJUST_ORIENTATION_DEGREES) {
                this.errorBox("Cannot further improve orientation.");
                return;
            }
            else {
                this.changeAdjustOrientationDegrees(0.5f);
            }
        }
        else {
            this.adjustActiveSquareOrientation(var.bestRC.axis);
        }

        this.updateAndRedraw();
    }

    /** Apply the recommended rotation to 's' until convergence.  Return
      * the improved square, or null if that is not possible due to
      * insufficient constraints. */
    private SurfaceSquare repeatedlyApplyRecommendedRotationCommand(SurfaceSquare s)
    {
        ObservationStats ostats = this.fitOfObservations(s);
        if (ostats == null || ostats.numSamples < 2) {
            return null;  // Underconstrained.
        }
        if (ostats.variance == 0) {
            return s;     // Already optimal.
        }

        // Rotation amount.  This will be gradually reduced.
        float adjustDegrees = 1.0f;

        // Iteration cap for safety.
        int iters = 0;

        // Iterate until the adjust amount is too small.
        while (adjustDegrees > MINIMUM_ADJUST_ORIENTATION_DEGREES) {
            // Get the recommended rotation.
            VarianceAfterRotations var = this.getVarianceAfterRotations(s, adjustDegrees);
            if (var == null) {
                return null;
            }
            if (var.underconstrained) {
                log("repeatedlyApply: solution is underconstrained, adjustDegrees="+ adjustDegrees);
                return s;
            }
            if (var.bestRC == null) {
                adjustDegrees = adjustDegrees * 0.5f;

                // Set the UI adjust degrees to what we came up with here so I
                // can easily see where it ended up.
                this.adjustOrientationDegrees = adjustDegrees;
            }
            else {
                s = this.adjustDerivedSquareOrientation(var.bestRC.axis, s, adjustDegrees);
            }

            if (++iters > 1000) {
                log("repeatedlyApply: exceeded iteration cap!");
                break;
            }
        }

        // Get the final variance.
        String finalVariance = "null";
        ostats = this.fitOfObservations(s);
        if (ostats != null) {
            finalVariance = ""+ostats.variance;
        }

        log("repeatedlyApply done: iters="+iters+" adj="+ adjustDegrees+
            " var="+finalVariance);
        return s;
    }

    /** Delete the active square. */
    private void deleteActiveSquare()
    {
        if (this.activeSquare == null) {
            this.errorBox("No active square.");
            return;
        }

        this.emCanvas.removeSurfaceSquare(this.activeSquare);
        this.setActiveSquare(null);
    }

    /** Calculate and apply the optimal orientation for the active square;
      * make its replacement active if we do replace it.. */
    private void automaticallyOrientActiveSquare()
    {
        SurfaceSquare derived = this.activeSquare;
        if (derived == null) {
            this.errorBox("No active square.");
            return;
        }
        SurfaceSquare base = derived.baseSquare;
        if (base == null) {
            this.errorBox("The active square has no base square.");
            return;
        }

        SurfaceSquare newDerived = automaticallyOrientSquare(derived);
        if (newDerived == null) {
            this.errorBox("Insufficient observations to determine proper orientation.");
        }
        else {
            this.setActiveSquare(newDerived);
        }

        this.updateAndRedraw();
    }

    /** Given a square 'derived' that is known to have a base square,
      * adjust and/or replace it with one with a better orientation,
      * and return the improved square.  Returns null if improvement
      * is not possible due to insufficient observational data. */
    private SurfaceSquare automaticallyOrientSquare(SurfaceSquare derived)
    {
        if (this.newAutomaticOrientationAlgorithm) {
            return this.repeatedlyApplyRecommendedRotationCommand(derived);
        }
        else {
            // Calculate the best rotation.
            Vector3f rot = calcRequiredRotation(derived.baseSquare,
                derived.latitude, derived.longitude);
            if (rot == null) {
                return null;
            }

            // Now, replace the active square.
            return this.replaceWithNewRotation(derived.baseSquare, derived, rot);
        }
    }

    /** Make the next square in 'emCanvas.surfaceSquares' active. */
    private void selectNextSquare(boolean forward)
    {
        this.setActiveSquare(this.emCanvas.getNextSquare(this.activeSquare, forward));
    }

    /** Build a square offset from the active square, set its orientation,
      * and make it active.  If we cannot make a new square, report that as
      * an error and leave the active square alone. */
    private void createAndAutomaticallyOrientActiveSquare(
        float deltaLatitude, float deltaLongitude)
    {
        SurfaceSquare base = this.activeSquare;
        if (base == null) {
            this.errorBox("There is no active square.");
            return;
        }
        SurfaceSquare newSquare = this.createAndAutomaticallyOrientSquare(
            base, base.latitude + deltaLatitude, base.longitude + deltaLongitude);
        if (newSquare == null) {
            ModalDialog.errorBox(this,
                "Cannot place new square since observational data does not uniquely determine its orientation.");
        }
        else {
            newSquare.drawStarRays = base.drawStarRays;
            this.setActiveSquare(newSquare);
        }
    }

    /** Build a square adjacent to the base square, set its orientation,
      * and return it.  Returns null and adds nothing if such a square
      * cannot be uniquely oriented. */
    private SurfaceSquare createAndAutomaticallyOrientSquare(SurfaceSquare base,
        float newLatitude, float newLongitude)
    {
        // Make a new adjacent square, initially with the same orientation
        // as the base square.
        SurfaceSquare newSquare =
            this.addRotatedAdjacentSquare(base,
                newLatitude,
                newLongitude,
                new Vector3f(0,0,0));
        if (base == newSquare) {
            return base;      // Did not move, no new square created.
        }
        this.addMatchingData(newSquare);

        // Now try to set its orientation to match observations.
        SurfaceSquare adjustedSquare = this.automaticallyOrientSquare(newSquare);
        if (adjustedSquare == null) {
            // No unique solution; remove the new square too.
            this.emCanvas.removeSurfaceSquare(newSquare);
        }

        return adjustedSquare;
    }

    /** Show the user what the local rotation space looks like by.
      * considering the effect of rotating various amounts on each axis. */
    private void analyzeSolutionSpace()
    {
        if (this.activeSquare == null) {
            this.errorBox("No active square.");
            return;
        }
        SurfaceSquare s = this.activeSquare;

        ObservationStats ostats = this.fitOfObservations(s);
        if (ostats == null) {
            this.errorBox("No observation fitness stats for the active square.");
            return;
        }

        // Prepare a task object in which to run the analysis.
        AnalysisTask task = new AnalysisTask(this, s);

        // Show a progress dialog while this run.
        ProgressDialog<PlotData3D, Void> progressDialog =
            new ProgressDialog<PlotData3D, Void>(this,
                "Analyzing rotations of active square...", task);

        // Run the dialog and the task.
        if (progressDialog.exec()) {
            // Retrieve the computed data.
            PlotData3D rollPitchYawPlotData;
            try {
                rollPitchYawPlotData = task.get();
            }
            catch (Exception e) {
                String msg = "Internal error: solution space analysis failed: "+e.getMessage();
                log(msg);
                e.printStackTrace();
                this.errorBox(msg);
                return;
            }

            // Plot results.
            RotationCubeDialog d = new RotationCubeDialog(this,
                (float)ostats.variance,
                rollPitchYawPlotData);
            d.exec();
        }
        else {
            log("Analysis canceled.");
        }
    }

    /** Task to analyze the solution space near a square, which can take a
      * while if 'solutionAnalysisPointsPerSide' is high. */
    private static class AnalysisTask extends MySwingWorker<PlotData3D, Void> {
        /** Enclosing EarthShape instance. */
        private EarthShape earthShape;

        /** Square whose solution will be analyzed. */
        private SurfaceSquare square;

        public AnalysisTask(
            EarthShape earthShape_,
            SurfaceSquare square_)
        {
            this.earthShape = earthShape_;
            this.square = square_;
        }

        @Override
        protected PlotData3D doTask() throws Exception
        {
            return this.earthShape.getThreeRotationAxisPlotData(this.square, this);
        }
    }

    /** Get data for various rotation angles of all three axes.
     *
      * This runs in a worker thread.  However, I haven't bothered
      * to synchronize access since the user shouldn't be able to
      * do anything while this is happening (although they can...),
      * and most of the shared data is immutable. */
    private PlotData3D getThreeRotationAxisPlotData(SurfaceSquare s, AnalysisTask task)
    {
        // Number of data points on each side of 0.
        int pointsPerSide = this.solutionAnalysisPointsPerSide;

        // Total number of data points per axis, including 0.
        int pointsPerAxis = pointsPerSide * 2 + 1;

        // Complete search space cube.
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
            if (task.isCancelled()) {
                log("analysis canceled");
                return null;     // Bail out.
            }
            task.setProgressFraction(zIndex / (float)pointsPerAxis);
            task.setStatus("Analyzing plane "+(zIndex+1)+" of "+pointsPerAxis);

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
                        (float)this.varianceOfAdjustedSquare(s, rot);
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
                    this.errorBox("The minimum number of points is 1.");
                }
                else if (c > 100) {
                    // At 100, it will take about a minute to complete.
                    this.errorBox("The maximum number of points is 100.");
                }
                else {
                    this.solutionAnalysisPointsPerSide = c;
                }
            }
            catch (NumberFormatException e) {
                this.errorBox("Invalid integer syntax: "+e.getMessage());
            }
        }
    }

    /** Prompt the user for a floating-point value.  Returns null if the
      * user cancels or enters an invalid value.  In the latter case, an
      * error box has already been shown. */
    private Float floatInputDialog(String label, float curValue)
    {
        String choice = JOptionPane.showInputDialog(this, label, (Float)curValue);
        if (choice != null) {
            try {
                return Float.valueOf(choice);
            }
            catch (NumberFormatException e) {
                this.errorBox("Invalid float syntax: "+e.getMessage());
                return null;
            }
        }
        else {
            return null;
        }
    }

    /** Let the user specify a new maximum Sun elevation. */
    private void setMaximumSunElevation()
    {
        Float newValue = this.floatInputDialog(
            "Specify maximum elevation of the Sun in degrees "+
                "above the horizon (otherwise, stars are not visible)",
            this.maximumSunElevation);
        if (newValue != null) {
            this.maximumSunElevation = newValue;
        }
    }

    /** Let the user specify the distance to the skybox. */
    private void setSkyboxDistance()
    {
        Float newValue = this.floatInputDialog(
            "Specify distance in 3D space units (each of which usually "+
                "represents 1000km of surface) to the skybox.\n"+
                "A value of 0 means the skybox is infinitely far away.",
            this.emCanvas.skyboxDistance);
        if (newValue != null) {
            if (newValue < 0) {
                this.errorBox("The skybox distance must be non-negative.");
            }
            else {
                this.emCanvas.skyboxDistance = newValue;
                this.updateAndRedraw();
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
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                (new EarthShape()).makeVisible();
            }
        });
    }

    public void toggleDrawAxes()
    {
        this.emCanvas.drawAxes = !this.emCanvas.drawAxes;
        this.updateAndRedraw();
    }

    public void toggleDrawCrosshair()
    {
        this.emCanvas.drawCrosshair = !this.emCanvas.drawCrosshair;
        this.updateAndRedraw();
    }

    /** Toggle the 'drawWireframeSquares' flag. */
    public void toggleDrawWireframeSquares()
    {
        this.emCanvas.drawWireframeSquares = !this.emCanvas.drawWireframeSquares;
        this.updateAndRedraw();
    }

    /** Toggle the 'drawCompasses' flag, then update state and redraw. */
    public void toggleDrawCompasses()
    {
        log("toggleDrawCompasses");

        // The compass flag is ignored when wireframe is true, so if
        // we are toggling compass, also clear wireframe.
        this.emCanvas.drawWireframeSquares = false;

        this.emCanvas.drawCompasses = !this.emCanvas.drawCompasses;
        this.updateAndRedraw();
    }

    /** Toggle the 'drawSurfaceNormals' flag. */
    public void toggleDrawSurfaceNormals()
    {
        this.emCanvas.drawSurfaceNormals = !this.emCanvas.drawSurfaceNormals;
        this.updateAndRedraw();
    }

    /** Toggle the 'drawCelestialNorth' flag. */
    public void toggleDrawCelestialNorth()
    {
        this.emCanvas.drawCelestialNorth = !this.emCanvas.drawCelestialNorth;
        this.updateAndRedraw();
    }

    /** Toggle the 'drawStarRays' flag. */
    public void toggleDrawStarRays()
    {
        if (this.activeSquare == null) {
            this.errorBox("No square is active");
        }
        else {
            this.activeSquare.drawStarRays = !this.activeSquare.drawStarRays;
        }
        this.emCanvas.redrawCanvas();
    }

    private void toggleDrawUnitStarRays()
    {
        this.emCanvas.drawUnitStarRays = !this.emCanvas.drawUnitStarRays;
        this.updateAndRedraw();
    }

    private void toggleDrawBaseSquareStarRays()
    {
        this.emCanvas.drawBaseSquareStarRays = !this.emCanvas.drawBaseSquareStarRays;
        this.updateAndRedraw();
    }

    private void toggleDrawTravelPath()
    {
        this.emCanvas.drawTravelPath  = !this.emCanvas.drawTravelPath;
        this.updateAndRedraw();
    }

    private void toggleDrawActiveSquareAtOrigin()
    {
        this.emCanvas.drawActiveSquareAtOrigin = !this.emCanvas.drawActiveSquareAtOrigin;
        this.updateAndRedraw();
    }

    private void toggleDrawWorldWireframe()
    {
        this.emCanvas.drawWorldWireframe = !this.emCanvas.drawWorldWireframe;
        this.updateAndRedraw();
    }

    private void toggleDrawWorldStars()
    {
        this.emCanvas.drawWorldStars = !this.emCanvas.drawWorldStars;
        this.updateAndRedraw();
    }

    private void toggleDrawSkybox()
    {
        this.emCanvas.drawSkybox = !this.emCanvas.drawSkybox;
        this.updateAndRedraw();
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
            this.errorBox("No active square.");
        }
        else {
            this.moveCamera(this.activeSquare.center);
        }
    }

    /** Place the camera at the specified location. */
    private void moveCamera(Vector3f loc)
    {
        this.emCanvas.cameraPosition = loc;
        this.updateAndRedraw();
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
        sb.append(", model="+this.worldObservations.getDescription());
        this.statusLabel.setText(sb.toString());
    }

    /** Set the state of stateful menu items. */
    private void setMenuState()
    {
        this.drawCoordinateAxesCBItem.setSelected(this.emCanvas.drawAxes);
        this.drawCrosshairCBItem.setSelected(this.emCanvas.drawCrosshair);
        this.drawWireframeSquaresCBItem.setSelected(this.emCanvas.drawWireframeSquares);
        this.drawCompassesCBItem.setSelected(this.emCanvas.drawCompasses);
        this.drawSurfaceNormalsCBItem.setSelected(this.emCanvas.drawSurfaceNormals);
        this.drawCelestialNorthCBItem.setSelected(this.emCanvas.drawCelestialNorth);
        this.drawStarRaysCBItem.setSelected(this.activeSquareDrawsStarRays());
        this.drawUnitStarRaysCBItem.setSelected(this.emCanvas.drawUnitStarRays);
        this.drawBaseSquareStarRaysCBItem.setSelected(this.emCanvas.drawBaseSquareStarRays);
        this.drawTravelPathCBItem.setSelected(this.emCanvas.drawTravelPath);
        this.drawActiveSquareAtOriginCBItem.setSelected(this.emCanvas.drawActiveSquareAtOrigin);
        this.drawWorldWireframeCBItem.setSelected(this.emCanvas.drawWorldWireframe);
        this.drawWorldStarsCBItem.setSelected(this.emCanvas.drawWorldStars);
        this.drawSkyboxCBItem.setSelected(this.emCanvas.drawSkybox);

        this.useSunElevationCBItem.setSelected(this.useSunElevation);
        this.invertHorizontalCameraMovementCBItem.setSelected(
            this.emCanvas.invertHorizontalCameraMovement);
        this.invertVerticalCameraMovementCBItem.setSelected(
            this.emCanvas.invertVerticalCameraMovement);

        this.newAutomaticOrientationAlgorithmCBItem.setSelected(
            this.newAutomaticOrientationAlgorithm);
        this.assumeInfiniteStarDistanceCBItem.setSelected(
            this.assumeInfiniteStarDistance);
        this.onlyCompareElevationsCBItem.setSelected(
            this.onlyCompareElevations);
    }

    /** Update the contents of the info panel. */
    private void setInfoPanel()
    {
        StringBuilder sb = new StringBuilder();

        if (this.emCanvas.activeSquareAnimationState != 0) {
            sb.append("Animation state: "+this.emCanvas.activeSquareAnimationState+"\n");
        }

        if (this.activeSquare == null) {
            sb.append("No active square.\n");
        }
        else {
            sb.append("Active square:\n");
            SurfaceSquare s = this.activeSquare;
            sb.append("  lat/lng: ("+s.latitude+","+s.longitude+")\n");
            sb.append("  pos: "+s.center+"\n");
            sb.append("  rot: "+s.rotationFromNominal+"\n");

            ObservationStats ostats = this.fitOfObservations(s);
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
                VarianceAfterRotations var = this.getVarianceAfterRotations(s,
                    this.adjustOrientationDegrees);

                // Print the effects of all the available rotations.
                sb.append("\n");
                for (RotationCommand rc : RotationCommand.values()) {
                    sb.append("  adj("+rc.key+"): ");
                    Double newVariance = var.rcToVariance.get(rc);
                    if (newVariance == null) {
                        sb.append("(none)\n");
                    }
                    else {
                        sb.append(""+newVariance+"\n");
                    }
                }

                // Make a final recommendation.
                if (var.bestRC != null) {
                    recommendation = var.bestRC.key;
                }
                sb.append("  recommend: "+recommendation+"\n");
            }
        }

        sb.append("\n");
        sb.append("adjDeg: "+this.adjustOrientationDegrees+"\n");

        // Compute average curvature from base.
        if (this.activeSquare != null && this.activeSquare.baseSquare != null) {
            sb.append("\n");
            sb.append("Base at: ("+this.activeSquare.baseSquare.latitude+
                      ","+this.activeSquare.baseSquare.longitude+")\n");

            CurvatureCalculator cc = this.computeAverageCurvature(this.activeSquare);
            double normalCurvatureDegPer1000km =
                FloatUtil.radiansToDegrees(cc.normalCurvature*1000);
            sb.append("Normal curvature: "+(float)normalCurvatureDegPer1000km+" deg per 1000 km\n");

            if (cc.normalCurvature != 0) {
                sb.append("Radius: "+(float)(1/cc.normalCurvature)+" km\n");
            }
            else {
                sb.append("Radius: Infinite\n");
            }

            sb.append("Geodesic curvature: "+(float)(cc.geodesicCurvature*1000.0)+" deg per 1000 km\n");
            sb.append("Geodesic torsion: "+(float)(cc.geodesicTorsion*1000.0)+" deg per 1000 km\n");
        }

        // Also show star observation data.
        if (this.activeSquare != null) {
            sb.append("\n");
            sb.append("Visible stars (az, el):\n");

            // Iterate over stars in name order.
            TreeSet<String> stars = new TreeSet<String>(this.activeSquare.starObs.keySet());
            for (String starName : stars) {
                StarObservation so = this.activeSquare.starObs.get(starName);
                sb.append("  "+so.name+": "+so.azimuth+", "+so.elevation+"\n");
            }
        }

        this.infoPanel.setText(sb.toString());
    }

    /** Compute the average curvature on a path from the base square
      * of 's' to 's'. */
    private CurvatureCalculator computeAverageCurvature(SurfaceSquare s)
    {
        // Travel distance and heading.
        TravelObservation tobs = this.worldObservations.getTravelObservation(
            s.baseSquare.latitude, s.baseSquare.longitude,
            s.latitude, s.longitude);

        // Unit travel vector in base square coordinate system.
        Vector3f startTravel = Vector3f.headingToVector((float)tobs.startToEndHeading);
        startTravel = startTravel.rotateAADeg(s.baseSquare.rotationFromNominal);

        // And at derived square.
        Vector3f endTravel = Vector3f.headingToVector((float)tobs.endToStartHeading + 180);
        endTravel = endTravel.rotateAADeg(s.rotationFromNominal);

        // Calculate curvature and twist.
        CurvatureCalculator c = new CurvatureCalculator();
        c.distanceKm = tobs.distanceKm;
        c.computeFromNormals(s.baseSquare.up, s.up, startTravel, endTravel);
        return c;
    }

    /** Result of call to 'getVarianceAfterRotations'. */
    private static class VarianceAfterRotations {
        /** Variance produced by each rotation.  The value can be null,
          * meaning the rotation produces a situation where we can't
          * measure the variance (e.g., because not enough stars are
          * above the horizon). */
        public HashMap<RotationCommand, Double> rcToVariance = new HashMap<RotationCommand, Double>();

        /** Which rotation command produces the greatest improvement
          * in variance, if any. */
        public RotationCommand bestRC = null;

        /** If true, the solution space is underconstrained, meaning
          * the best orientation is not unique. */
        public boolean underconstrained = false;
    }

    /** Perform a trial rotation in each direction and record the
      * resulting variance, plus a decision about which is best, if any.
      * This returns null if we do not have enough data to measure
      * the fitness of the square's orientation. */
    private VarianceAfterRotations getVarianceAfterRotations(SurfaceSquare s,
        float adjustDegrees)
    {
        // Get variance if no rotation is performed.  We only recommend
        // a rotation if it improves on this.
        ObservationStats ostats = this.fitOfObservations(s);
        if (ostats == null) {
            return null;
        }

        VarianceAfterRotations ret = new VarianceAfterRotations();

        // Variance achieved by the best rotation command, if there is one.
        double bestNewVariance = 0;

        // Get the effects of all the available rotations.
        for (RotationCommand rc : RotationCommand.values()) {
            ObservationStats newStats = this.fitOfAdjustedSquare(s,
                rc.axis.times(adjustDegrees));
            if (newStats == null || newStats.numSamples < 2) {
                ret.rcToVariance.put(rc, null);
            }
            else {
                double newVariance = newStats.variance;
                ret.rcToVariance.put(rc, newVariance);

                if (ostats.variance == 0 && newVariance == 0) {
                    // The current orientation is ideal, but here
                    // is a rotation that keeps it ideal.  That
                    // must mean that the solution space is under-
                    // constrained.
                    //
                    // Note: This is an unnecessarily strong condition for
                    // being underconstrained.  It requires that we
                    // find a zero in the objective function, and
                    // furthermore that the solution space be parallel
                    // to one of the three local rotation axes.  I have
                    // some ideas for more robust detection of underconstraint,
                    // but haven't tried to implement them yet.  For now I
                    // will rely on manual inspection of the rotation cube
                    // analysis dialog.
                    ret.underconstrained = true;
                }

                if (newVariance < ostats.variance &&
                    (ret.bestRC == null || newVariance < bestNewVariance))
                {
                    ret.bestRC = rc;
                    bestNewVariance = newVariance;
                }
            }
        }

        return ret;
    }

    /** True if there is an active square and it is drawing star rays. */
    private boolean activeSquareDrawsStarRays()
    {
        return this.activeSquare != null &&
               this.activeSquare.drawStarRays;
    }

    /** Replace the current observations with a new source and clear
      * the virtual map. */
    private void changeObservations(WorldObservations obs)
    {
        this.clearSurfaceSquares();

        this.worldObservations = obs;

        // Enable all stars in the new model.
        this.enabledStars.clear();
        for (String starName : this.worldObservations.getAllStars()) {
            this.enabledStars.put(starName, true);
        }

        this.updateAndRedraw();
    }

    /** Refresh all the UI elements and the map canvas. */
    private void updateAndRedraw()
    {
        this.updateUIState();
        this.emCanvas.redrawCanvas();
    }

    /** Return true if the named star is enabled. */
    public boolean isStarEnabled(String starName)
    {
        return this.enabledStars.containsKey(starName) &&
               this.enabledStars.get(starName).equals(true);
    }

    /** Do some initial steps so I do not have to do them manually each
      * time I start the program when I'm working on a certain feature.
      * The exact setup here will vary over time as I work on different
      * things; it is only meant for use while testing or debugging. */
    private void doCannedSetup()
    {
        // Disable all stars except for Betelgeuse and Dubhe.
        this.enabledStars.clear();
        for (String starName : this.worldObservations.getAllStars()) {
            boolean en = (starName.equals("Betelgeuse") || starName.equals("Dubhe"));
            this.enabledStars.put(starName, en);
        }

        // Build first square in SF as usual.
        this.startNewSurfaceAt(38, -122);

        // Build next near Washington, DC.
        this.buildNextSquareAt(38, -77);

        // The plan is to align with just two stars, so we need this.
        this.assumeInfiniteStarDistance = true;

        // Position the camera to see DC square.
        if (this.emCanvas.drawActiveSquareAtOrigin) {
            // This is a canned command in the middle of a session.
            // Do not reposition the camera.
        }
        else {
            this.emCanvas.drawActiveSquareAtOrigin = true;
//            this.emCanvas.cameraPosition = new Vector3f(-0.19f, 0.56f, 1.20f);
//            this.emCanvas.cameraAzimuthDegrees = 0.0f;
//            this.emCanvas.cameraPitchDegrees = -11.0f;
            this.emCanvas.cameraPosition = new Vector3f(-0.89f, 0.52f, -1.06f);
            this.emCanvas.cameraAzimuthDegrees = 214.0f;
            this.emCanvas.cameraPitchDegrees = 1.0f;
        }

        // Use wireframes for squares, no world wireframe, but add surface normals.
        this.emCanvas.drawWireframeSquares = true;
        this.emCanvas.drawWorldWireframe = false;
        this.emCanvas.drawSurfaceNormals = true;
        this.emCanvas.drawTravelPath = false;

        // Show its star rays, and those at SF, as unit vectors.
        this.activeSquare.drawStarRays = true;
        this.emCanvas.drawBaseSquareStarRays = true;
        this.emCanvas.drawUnitStarRays = true;

        // Reset the animation.
        this.emCanvas.activeSquareAnimationState = 0;

        this.updateAndRedraw();
    }

    /** Set the animation state either to 0, or to an amount
      * offset by 's'. */
    private void setAnimationState(int s)
    {
        if (s == 0) {
            this.emCanvas.activeSquareAnimationState = 0;
        }
        else {
            this.emCanvas.activeSquareAnimationState += s;
        }
        this.updateAndRedraw();
    }

    // ------------------------------- Animation --------------------------------
    /** When animation begins, this is the rotation of the active
      * square relative to its base. */
    private Vector3f animatedRotationStartRotation;

    /** Angle through which to rotate the active square. */
    private double animatedRotationAngle;

    /** Axis about which to rotate the active square. */
    private Vector3f animatedRotationAxis;

    /** Seconds the animation should take to complete. */
    private float animatedRotationSeconds;

    /** How many seconds have elapsed since we started animating.
      * This is clamped to 'animatedRotationSeconds', and when
      * it is equal, the animation is complete. */
    private float animatedRotationElapsed;

    /** Start a new rotation animation of the active square by
      * 'angle' degrees about 'axis' for 'seconds'. */
    public void beginAnimatedRotation(double angle, Vector3f axis, float seconds)
    {
        if (this.activeSquare != null &&
            this.activeSquare.baseSquare != null)
        {
            log("starting rotation animation");
            this.animatedRotationStartRotation = this.activeSquare.rotationFromBase;
            this.animatedRotationAngle = angle;
            this.animatedRotationAxis = axis;
            this.animatedRotationSeconds = seconds;
            this.animatedRotationElapsed = 0;
        }
    }

    /** If animating, advance to the next frame, based on 'deltaSeconds'
      * having elapsed since the last animated frame.
      *
      * This should *not* trigger a redraw, since that will cause this
      * function to be called again during the same frame. */
    public void nextAnimatedRotationFrame(float deltaSeconds)
    {
        if (this.animatedRotationElapsed < this.animatedRotationSeconds &&
            this.activeSquare != null &&
            this.activeSquare.baseSquare != null)
        {
            this.animatedRotationElapsed = FloatUtil.clampf(
                this.animatedRotationElapsed + deltaSeconds, 0, this.animatedRotationSeconds);

            // How much do we want the square to be rotated,
            // relative to its orientation at the start of
            // the animation?
            double rotFraction = this.animatedRotationElapsed / this.animatedRotationSeconds;
            Vector3f partialRot = this.animatedRotationAxis.timesd(
                this.animatedRotationAngle * rotFraction);

            // Compose with the original orientation.
            Vector3f newRotationFromBase =
                Vector3f.composeRotations(this.animatedRotationStartRotation, partialRot);

            SurfaceSquare s = replaceWithNewRotation(
                this.activeSquare.baseSquare, this.activeSquare, newRotationFromBase);
            this.setActiveSquareNoRedraw(s);

            if (this.animatedRotationElapsed >= this.animatedRotationSeconds) {
                log("rotation animation finished; normal: "+s.up);
            }
        }
    }

    /** Do any "physics" world updates.  This is invoked prior to
      * rendering a frame in the GL canvas. */
    public void updatePhysics(float elapsedSeconds)
    {
        this.nextAnimatedRotationFrame(elapsedSeconds);
    }

    /** Get list of stars for which both squares have observations. */
    private List<String> getCommonStars(SurfaceSquare s1, SurfaceSquare s2)
    {
        ArrayList<String> ret = new ArrayList<String>();
        for (Map.Entry<String, StarObservation> entry : s1.starObs.entrySet()) {
            if (s2.findObservation(entry.getKey()) != null) {
                ret.add(entry.getKey());
            }
        }
        return ret;
    }

    private void showCurvatureDialog()
    {
        // Default initial values.
        CurvatureCalculator c = CurvatureCalculator.getDubheSirius();

        // Try to populate 'c' with values from the current square.
        if (this.activeSquare != null && this.activeSquare.baseSquare != null) {
            SurfaceSquare start = this.activeSquare.baseSquare;
            SurfaceSquare end = this.activeSquare;
            List<String> common = getCommonStars(start, end);
            if (common.size() >= 2) {
                // When Dubhe and Betelgeuse are the only ones, I want
                // Dubhe first so the calculation agrees with the ad-hoc
                // animation, and doing them in this order accomplishes
                // that.
                String A = common.get(1);
                String B = common.get(0);
                log("initializing curvature dialog with "+A+" and "+B);
                c.start_A_az = start.findObservation(A).azimuth;
                c.start_A_el = start.findObservation(A).elevation;
                c.start_B_az = start.findObservation(B).azimuth;
                c.start_B_el = start.findObservation(B).elevation;
                c.end_A_az = end.findObservation(A).azimuth;
                c.end_A_el = end.findObservation(A).elevation;
                c.end_B_az = end.findObservation(B).azimuth;
                c.end_B_el = end.findObservation(B).elevation;
                c.setTravelByLatLong(start.latitude, start.longitude, end.latitude, end.longitude);
            }
        }

        // Run the dialog.
        (new CurvatureCalculatorDialog(EarthShape.this, c)).exec();
    }
}

// EOF
