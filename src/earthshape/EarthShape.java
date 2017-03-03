// EarthShape.java
// See copyright.txt for license and terms of use.

package earthshape;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Point3D;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
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
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
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

        // Use an ambient light source.
        //root.getChildren().add(new AmbientLight());

        // Try a point light source at initial camera position.
        PointLight light = new PointLight();
        light.setTranslateX(20);
        light.setTranslateY(20);
        light.setTranslateZ(-150);
        root.getChildren().add(light);

        // And another one?
        light = new PointLight();
        light.setTranslateX(0);
        light.setTranslateY(-200);
        light.setTranslateZ(0);
        root.getChildren().add(light);

        // Texture?
        Image img = new Image("earthshape/textures/compass-rose.png");

        // Material
        PhongMaterial mat = new PhongMaterial();
        mat.setDiffuseMap(img);

        // Box with texture applied.
        Box testBox = new Box(20, 20, 20);
        testBox.setMaterial(mat);
        //testBox.setMaterial(new PhongMaterial(Color.RED));
        //testBox.setDrawMode(DrawMode.LINE);
        testBox.setTranslateX(25);
        root.getChildren().add(testBox);

        // Rectangular face with texture applied.
        //    0--1
        //    | /|
        //    |/ |
        //    2--3
        TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_TEXCOORD);
        float scale = 20;
        float lx = -scale/2;
        float rx = scale/2;
        float ty = -scale/2;
        float by = scale/2;
        mesh.getPoints().addAll(lx,ty,0, rx,ty,0, lx,by,0, rx,by,0);
        mesh.getTexCoords().addAll(0,0, 1,0, 0,1, 1,1);
        mesh.getFaces().addAll(2,2, 1,1, 0,0,
                               3,3, 1,1, 2,2);
        mesh.getFaceSmoothingGroups().addAll(1, 1);
        MeshView square = new MeshView(mesh);
        //square.setScaleX(scale);
        //square.setScaleY(scale);
        square.setTranslateX(-50);
        square.setTranslateZ(-scale/2);
        square.setMaterial(mat);
        square.setCullFace(CullFace.NONE);
        root.getChildren().add(square);

        // Another square.
        mesh = new TriangleMesh(VertexFormat.POINT_TEXCOORD);
        scale = 1;
        lx = -scale/2;
        rx = scale/2;
        ty = -scale/2;
        by = scale/2;
        mesh.getPoints().addAll(lx,ty,0, rx,ty,0, lx,by,0, rx,by,0);
        mesh.getTexCoords().addAll(0,0, 1,0, 0,1, 1,1);
        mesh.getFaces().addAll(2,2, 1,1, 0,0,
                               3,3, 1,1, 2,2);
        mesh.getFaceSmoothingGroups().addAll(1, 1);
        square = new MeshView(mesh);
        scale = 20;
        square.setScaleX(scale);
        square.setScaleY(scale);
        square.setTranslateX(-25);
        square.setTranslateZ(-scale/2);
        square.setMaterial(mat);
        square.setCullFace(CullFace.NONE);
        root.getChildren().add(square);

        // Another box?
        TriangleMesh box2 = EarthShape.createMesh(20, 20, 20);
        MeshView box2v = new MeshView(box2);
        box2v.setMaterial(mat);
        //box2v.setTranslateX(-25);
        root.getChildren().add(box2v);

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


    static TriangleMesh createMesh(float w, float h, float d) {

        // NOTE: still create mesh for degenerated box
        float hw = w / 2f;
        float hh = h / 2f;
        float hd = d / 2f;

        float points[] = {
            -hw, -hh, -hd,
             hw, -hh, -hd,
             hw,  hh, -hd,
            -hw,  hh, -hd,
            -hw, -hh,  hd,
             hw, -hh,  hd,
             hw,  hh,  hd,
            -hw,  hh,  hd};

        float texCoords[] = {0, 0, 1, 0, 1, 1, 0, 1};

        int faceSmoothingGroups[] = {
            1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4
        };

        int faces[] = {
            0, 0, 2, 2, 1, 1,
            2, 2, 0, 0, 3, 3,
            1, 0, 6, 2, 5, 1,
            6, 2, 1, 0, 2, 3,
            5, 0, 7, 2, 4, 1,
            7, 2, 5, 0, 6, 3,
            4, 0, 3, 2, 0, 1,
            3, 2, 4, 0, 7, 3,
            3, 0, 6, 2, 2, 1,
            6, 2, 3, 0, 7, 3,
            4, 0, 1, 2, 5, 1,
            1, 2, 4, 0, 0, 3,
        };

        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().setAll(points);
        mesh.getTexCoords().setAll(texCoords);
        mesh.getFaces().setAll(faces);
        mesh.getFaceSmoothingGroups().setAll(faceSmoothingGroups);

        return mesh;
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
        //stackPane.getChildren().add(ss);     // remove labels for now
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
