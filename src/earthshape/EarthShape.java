// EarthShape.java
// See copyright.txt for license and terms of use.

package earthshape;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;

/** This application demonstrates a procedure for inferring the shape
  * of a surface (such as the Earth) based on the observed locations of
  * stars from various locations at a fixed point in time. */
public class EarthShape extends Application {
    // Try holding a persistent Translate object so I can move
    // the camera.
    private Transform cameraTransform;

    // Also need the camera itself so I can install an updated
    // transform.
    private PerspectiveCamera camera;

    // For tracking click+drag, the location of the most recent
    // mouse event.  This is in pixels where (0,0) is the top-left
    // corner of the window.
    private double oldMouseX, oldMouseY;



    /** Create and return a tree of objects to display. */
    public Parent createSceneObjects() throws Exception {

        // Group of objects to return.
        Group root = new Group();

        // Texture?
        Image img = new Image("earthshape/textures/compass-rose.png");

        // Material
        PhongMaterial mat = new PhongMaterial();
        mat.setDiffuseMap(img);

        // Box with texture applied.
        Box testBox = new Box(5, 5, 5);
        testBox.setMaterial(mat);
        //testBox.setMaterial(new PhongMaterial(Color.RED));
        //testBox.setDrawMode(DrawMode.LINE);
        //testBox.setTranslateZ(3);
        root.getChildren().add(testBox);

        // X axis
        PhongMaterial xaxisColor = new PhongMaterial(Color.RED);
        Box xaxis = new Box(100, 0.1, 0.1);
        xaxis.setMaterial(xaxisColor);
        root.getChildren().add(xaxis);

        // Y axis
        PhongMaterial yaxisColor = new PhongMaterial(Color.GREEN);
        Box yaxis = new Box(0.1, 100, 0.1);
        yaxis.setMaterial(yaxisColor);
        root.getChildren().add(yaxis);

        // Z axis
        PhongMaterial zaxisColor = new PhongMaterial(Color.BLUE);
        Box zaxis = new Box(0.1, 0.1, 100);
        zaxis.setMaterial(zaxisColor);
        root.getChildren().add(zaxis);

        // Tick marks along the axes.  The end that is missing its
        // final tick is the positive direction.
        for (int i=-50; i < 50; i++) {
            Box tick = new Box(0.1, 0.1, i%10==0? 3 : 1);
            tick.setTranslateX(i);
            tick.setMaterial(xaxisColor);
            root.getChildren().add(tick);

            tick = new Box(0.1, 0.1, i%10==0? 3 : 1);
            tick.setTranslateY(i);
            tick.setMaterial(yaxisColor);
            root.getChildren().add(tick);

            tick = new Box(i%10==0? 3 : 1, 0.1, 0.1);
            tick.setTranslateZ(i);
            tick.setMaterial(zaxisColor);
            root.getChildren().add(tick);
        }

        return root;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Create a sub-scene containing the scene object graph.
        SubScene scene = new SubScene(createSceneObjects(), 1024, 768,
                                      true /*depth buffer*/,
                                      SceneAntialiasing.BALANCED);
        scene.setFill(Color.ALICEBLUE);

        // Create and position camera
        this.camera = new PerspectiveCamera(true);
        this.camera.setFarClip(1000);     // default is 100
        this.cameraTransform = new Translate(20, 20, -120);
        this.camera.getTransforms().add(this.cameraTransform);
        scene.setCamera(this.camera);

        // Make a stack pane to hold the sub-scene.
        StackPane stackPane = new StackPane();
        stackPane.getChildren().add(scene);

        // Bind the sizes of the stack pane and sub-scene so when
        // the stack resizes, the sub-scene does too.
        scene.heightProperty().bind(stackPane.heightProperty());
        scene.widthProperty().bind(stackPane.widthProperty());

        // Add a group to hold labels that go above the 3D image.
        Group labelGroup = new Group();
        for (int i=0; i < 1000; i += 50) {
            labelGroup.getChildren().add(new Text(i, i, "("+i+","+i+")"));
        }

        // Add another sub-scene to hold the labels.  This seems
        // to be the way to get a useful coordinate system for
        // placing the labels.
        SubScene ss = new SubScene(labelGroup, 1024, 728);
        stackPane.getChildren().add(ss);
        ss.heightProperty().bind(stackPane.heightProperty());
        ss.widthProperty().bind(stackPane.widthProperty());

        // The labels should not intercept mouse presses.
        ss.setDisable(true);

        //ss.setTranslateX(100);
        //ss.setScaleX(2);

        // Wrap the stack pane in a full Scene.
        Scene fullScene = new Scene(stackPane, 1024, 768);

        // Specify keyboard handling for the scene.
        fullScene.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                // Very rudimentary WASD response.
                Transform dt = null;
                double speed = 10;
                if (event.isShiftDown()) {
                    speed *= 10;
                }
                switch (event.getCode()) {
                    case W:
                        dt = new Translate(0, 0, +speed);
                        break;

                    case A:
                        dt = new Translate(-speed, 0, 0);
                        break;

                    case S:
                        dt = new Translate(0, 0, -speed);
                        break;

                    case D:
                        dt = new Translate(+speed, 0, 0);
                        break;

                    case V:
                        dt = new Translate(0, +speed, 0);
                        break;

                    case F:
                        dt = new Translate(0, -speed, 0);
                        break;

                    case LEFT:
                        dt = new Rotate(+speed, new Point3D(0,-1,0));
                        break;

                    case RIGHT:
                        dt = new Rotate(-speed, new Point3D(0,-1,0));
                        break;

                    case UP:
                        dt = new Rotate(-speed, new Point3D(1,0,0));
                        break;

                    case DOWN:
                        dt = new Rotate(+speed, new Point3D(1,0,0));
                        break;

                    case DELETE:
                        dt = new Rotate(-speed, new Point3D(0,0,1));
                        break;

                    case PAGE_DOWN:
                        dt = new Rotate(+speed, new Point3D(0,0,1));
                        break;

                    case P:
                        Point2D p2 = scene.localToScreen(0,0,0);
                        System.out.println("p2: "+p2);
                        break;

                    default:
                        break;
                }
                if (dt != null) {
                    EarthShape.this.transformCamera(dt);
                }
            }
        });

        scene.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override public void handle(MouseEvent me) {
                EarthShape.this.oldMouseX = me.getSceneX();
                EarthShape.this.oldMouseY = me.getSceneY();
            }
        });
        scene.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override public void handle(MouseEvent me) {
                // Compute the delta from the previous value, and
                // then update that previous value for the next call.
                double x = me.getSceneX();
                double y = me.getSceneY();
                double dx = x - EarthShape.this.oldMouseX;
                double dy = y - EarthShape.this.oldMouseY;
                EarthShape.this.oldMouseX = x;
                EarthShape.this.oldMouseY = y;

                // Build a rotation transform where 'dx' is
                // rotation about Y axis and 'dy' is rotation
                // about X.
                Rotate ry = new Rotate(-dx/10, new Point3D(0,1,0));
                Rotate rx = new Rotate(dy/10, new Point3D(1,0,0));
                Transform dt = rx.createConcatenation(ry);
                EarthShape.this.transformCamera(dt);
            }
        });


        primaryStage.setTitle("Earth Shape");
        primaryStage.setScene(fullScene);
        primaryStage.show();

        // Experiment with coordinate transformation.
        Point3D p = scene.localToScene(0,0,0);
        System.out.println("p: "+p);
        p = camera.localToScene(0,0,0);
        System.out.println("p: "+p);

        Point2D p2 = scene.localToScreen(0,0,0);
        System.out.println("p2: "+p2);
    }

    /** Change the camera transformation by applying 'dt' to the end
      * of the current transform. */
    private void transformCamera(Transform dt)
    {
        EarthShape.this.cameraTransform =
            EarthShape.this.cameraTransform.createConcatenation(dt);

        // Replace the existing one with the new one.
        EarthShape.this.camera.getTransforms().clear();
        EarthShape.this.camera.getTransforms().add(
            EarthShape.this.cameraTransform);
    }

    public static void main(String[] args)
    {
        Application.launch(args);
    }
}
