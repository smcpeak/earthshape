// EarthShape.java
// See copyright.txt for license and terms of use.

package earthshape;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;

/** This application demonstrates a procedure for inferring the shape
  * of a surface (such as the Earth) based on the observed locations of
  * stars from various locations at a fixed point in time. */
public class EarthShape extends Application {
    // Try holding a persistent Translate object so I can move
    // the camera.
    private Translate cameraTranslate = new Translate(0, 0, -15);

    public Group createSceneObjects() throws Exception {

        // Texture?
        Image img = new Image("earthshape/textures/compass-rose.png");

        // Material
        PhongMaterial mat = new PhongMaterial();
        mat.setDiffuseMap(img);

        // Box
        Box testBox = new Box(5, 5, 5);
        testBox.setMaterial(mat);
        //testBox.setMaterial(new PhongMaterial(Color.RED));
        //testBox.setDrawMode(DrawMode.LINE);

        // X axis
        PhongMaterial axisColor = new PhongMaterial(Color.RED);
        Box xaxis = new Box(100, 0.1, 0.1);
        xaxis.setMaterial(axisColor);

        // Collect the objects together.
        Group root = new Group();
        root.getChildren().add(xaxis);
        root.getChildren().add(testBox);
        return root;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Create a scene containing the scene object graph.
        Scene scene = new Scene(createSceneObjects(), 1024, 768,
                                true /*depth buffer*/);
        scene.setFill(Color.ALICEBLUE);

        // Create and position camera
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.getTransforms().addAll (
                new Rotate(-20, Rotate.Y_AXIS),
                new Rotate(-20, Rotate.X_AXIS),
                this.cameraTranslate);
        scene.setCamera(camera);

        // Specify keyboard handling for the scene.
        scene.setOnKeyPressed(new EventHandler<KeyEvent>() {
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
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args)
    {
        Application.launch(args);
    }
}
