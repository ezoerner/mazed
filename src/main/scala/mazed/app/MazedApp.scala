package mazed.app

import com.jme3.app.{SimpleApplication, StatsAppState}
import com.jme3.bullet.BulletAppState
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.control.RigidBodyControl
import com.jme3.bullet.util.CollisionShapeFactory
import com.jme3.light.{AmbientLight, DirectionalLight}
import com.jme3.math._
import com.jme3.scene.Node
import com.jme3.system.{AppSettings, JmeContext, JmeSystem}
import com.jme3.util.SkyFactory
import com.typesafe.scalalogging.LazyLogging
import org.slf4j.bridge.SLF4JBridgeHandler

import scala.util.Random

object MazedApp {

  def main(args: Array[String]): Unit = {
    initLogging()

    val seed = if (args.size > 0) Some(args(0).toLong) else None
    val rand = seed.fold[Random](Random)(new Random(_))

    val gameSettings = new AppSettings(true)
    gameSettings.setSettingsDialogImage(Configuration.appSettingsDialogImage)

    val app = new MazedApp(rand)
    app.setSettings(gameSettings)
    app.start()
  }

  private def initLogging(): Unit = {
    // Optionally remove existing handlers attached to j.u.l root logger
    SLF4JBridgeHandler.removeHandlersForRootLogger() // (since SLF4J 1.6.5)

    // add SLF4JBridgeHandler to j.u.l's root logger, should be done once during
    // the initialization phase of your application
    SLF4JBridgeHandler.install()
  }
}

class MazedApp(rand: Random)
  extends SimpleApplication(new DebugKeysAppState, new BulletAppState, new PlayerControlState, new CameraControlState)
          with LazyLogging {

  private[app] lazy val bulletAppState = stateManager.getState(classOf[BulletAppState])

  private[app] lazy val sceneNode: Node = {
    val (mazeNode, floor) = new MazeSceneBuilder(assetManager, rand).build
    val scene = new Node()
    scene.attachChild(mazeNode)
    scene.attachChild(floor)

    // We set up collision detection for the scene by creating a
    // compound collision shape and a static RigidBodyControl with mass zero.
    val sceneShape: CollisionShape = CollisionShapeFactory.createMeshShape(scene)
    val landscape = new RigidBodyControl(sceneShape, 0)
    scene.addControl(landscape)
    bulletAppState.getPhysicsSpace.add(landscape)
    scene
  }

  private[app] lazy val characterNode = stateManager.getState(classOf[PlayerControlState]).characterNode
  private[app] lazy val player = stateManager.getState(classOf[PlayerControlState]).player

  // override unsatisfactory behavior of superclass:
  // load settings from "registry" even though we've set the settingsDialogImage
  override def start(): Unit = {
    if (!JmeSystem.showSettingsDialog(settings, true)) return
    //re-setting settings they can have been merged from the registry.
    setSettings(settings)
    start(JmeContext.Type.Display)
  }

  override def simpleInitApp(): Unit = {
    inputManager.setCursorVisible(false)
    bulletAppState.setDebugEnabled(Configuration.appDebugBullet)
    if (Configuration.appEnableFpsText) {
      stateManager.attach(new StatsAppState)
    }
    initSky()
    initLight()
    rootNode.attachChild(sceneNode)
  }

  private def initSky(): Unit = {
    Configuration.skyTexture foreach { tx ⇒
      rootNode.attachChild(SkyFactory.createSky(assetManager, tx, false))
    }
    Configuration.skyColor foreach { color ⇒
      viewPort.setBackgroundColor(color)
    }
  }

  private def initLight(): Unit = {
    // We add light so we see the scene
    val al: AmbientLight = new AmbientLight
    al.setColor(ColorRGBA.White.mult(1.3f))
    rootNode.addLight(al)
    val dl: DirectionalLight = new DirectionalLight
    dl.setColor(ColorRGBA.White)
    dl.setDirection(new Vector3f(2.8f, -2.8f, -2.8f).normalizeLocal)
    rootNode.addLight(dl)
  }
}
