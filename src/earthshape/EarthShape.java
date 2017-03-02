// EarthShape.java
// See copyright.txt for license and terms of use.

package earthshape;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;

/** This application demonstrates a procedure for inferring the shape
  * of a surface (such as the Earth) based on the observed locations of
  * stars from various locations at a fixed point in time. */
public class EarthShape extends Application {
    // Try holding a persistent Translate object so I can move
    // the camera.
    private Translate cameraTranslate = new Translate(0, 0, -50);

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
        PhongMaterial axisColor = new PhongMaterial(Color.RED);
        Box xaxis = new Box(100, 0.1, 0.1);
        xaxis.setMaterial(axisColor);
        root.getChildren().add(xaxis);

        // Tick marks along the X axis.
        for (int i=-50; i < 50; i++) {
            Box tick = new Box(0.1, 0.1, i%10==0? 3 : 1);
            tick.setTranslateX(i);
            tick.setMaterial(axisColor);
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
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setFarClip(1000);     // default is 100
        camera.getTransforms().addAll (
                new Rotate(-20, Rotate.Y_AXIS),
                new Rotate(-20, Rotate.X_AXIS),
                this.cameraTranslate);
        scene.setCamera(camera);

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

        // Wrap the stack pane in a full Scene.
        Scene fullScene = new Scene(stackPane, 1024, 768);

        // Specify keyboard handling for the scene.
        fullScene.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                // Very rudimentary WASD response.
                Translate t = EarthShape.this.cameraTranslate;
                switch (event.getCode()) {
                    case W:
                        t.setZ(t.getZ() + 1);
                        break;

                    case A:
                        t.setX(t.getX() - 1);
                        break;

                    case S:
                        t.setZ(t.getZ() - 1);
                        break;

                    case D:
                        t.setX(t.getX() + 1);
                        break;

                    default:
                        break;
                }
            }
        });


        primaryStage.setTitle("Earth Shape");
        primaryStage.setScene(fullScene);
        primaryStage.show();
    }

    public static void main(String[] args)
    {
        Application.launch(args);
    }
}
