package mazed.app

import com.jme3.app.{SimpleApplication, StatsAppState}
import com.jme3.bullet.BulletAppState
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.control.{BetterCharacterControl, RigidBodyControl}
import com.jme3.bullet.util.CollisionShapeFactory
import com.jme3.input.KeyInput._
import com.jme3.input.MouseInput._
import com.jme3.input.controls.{ActionListener, AnalogListener, KeyTrigger, MouseAxisTrigger}
import com.jme3.light.{AmbientLight, DirectionalLight}
import com.jme3.math.FastMath.PI
import com.jme3.math.Vector3f.{UNIT_X, UNIT_Y, UNIT_Z}
import com.jme3.math._
import com.jme3.scene.Node
import com.jme3.system.{AppSettings, JmeContext, JmeSystem}
import com.jme3.util.SkyFactory
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.slf4j.bridge.SLF4JBridgeHandler

import scala.util.Random

object MazedApp {

  def main(args: Array[String]): Unit = {
    initLogging()
    val config = ConfigFactory.load()
    val seed = if (args.size > 0) Some(args(0).toLong) else None
    val rand = seed.fold[Random](Random)(new Random(_))

    val gameSettings = new AppSettings(true)

    val settingsDialogImage = config.getString("app.settingsDialogImage")
    gameSettings.setSettingsDialogImage(settingsDialogImage)

    val app = new MazedApp(rand, config)
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

class MazedApp(rand: Random, config: Config)
  extends SimpleApplication(new DebugKeysAppState, new CameraControlState)
          with ActionListener
          with AnalogListener
          with LazyLogging {
  private val StrafeLeft = "StrafeLeft"
  private val StrafeRight = "StrafeRight"
  private val WalkForward = "WalkForward"
  private val WalkBackward = "WalkBackward"
  private val RotateRight = "RotateRight"
  private val RotateLeft = "RotateLeft"
  private val Jump = "Jump"
  private val Duck = "Duck"

  private var leftStrafe = false
  private var rightStrafe = false
  private var forward = false
  private var backward = false

  private lazy val bulletAppState = new BulletAppState

  private[app] lazy val (player, characterNode): (BetterCharacterControl, Node) = {
    val mass = config.getDouble("player.mass").toFloat
    val charNode = new Node("character node")

    charNode.setLocalTranslation(Configuration.initialLocation)

    val physicsCharacter = new BetterCharacterControl(Configuration.playerRadius, Configuration.playerHeight, mass)
    charNode.addControl(physicsCharacter)
    bulletAppState.getPhysicsSpace.add(physicsCharacter)
    physicsCharacter.setGravity(Configuration.gravity)
    physicsCharacter.setViewDirection(Configuration.playerInitialLookAt)
    physicsCharacter.setJumpForce(Configuration.jumpForce)

    Configuration.maybeModel foreach { model ⇒
      val scale = config.getDouble("player.modelScale").toFloat
      val characterModel = assetManager.loadModel(model)
      characterModel.setLocalScale(scale)
      charNode.attachChild(characterModel)
    }
    (physicsCharacter, charNode)
  }


  private[app] lazy val sceneNode: Node = {
    val (mazeNode, floor) = new MazeSceneBuilder(config, assetManager).build
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
    stateManager.attach(bulletAppState)

    val debugBullet = config.getBoolean("app.debugBullet")
    bulletAppState.setDebugEnabled(debugBullet)

    val enableFpsText = config.getBoolean("app.enableFpsText")
    if (enableFpsText) {
      stateManager.attach(new StatsAppState)
    }

    initSky()
    initLight()
    rootNode.attachChild(sceneNode)
    rootNode.attachChild(characterNode)
    initKeys()
  }

  override def simpleUpdate(tpf: Float): Unit = {
    // Get current forward and left vectors of model by using its rotation
    // to rotate the unit vectors
    val modelForwardDir = characterNode.getWorldRotation.mult(UNIT_Z)
    val modelLeftDir = characterNode.getWorldRotation.mult(UNIT_X)

    val walkDirection = Vector3f.ZERO.clone
    if (leftStrafe) {
      walkDirection.addLocal(modelLeftDir mult Configuration.moveSidewaysSpeed)
    }
    if (rightStrafe) {
      walkDirection.addLocal(modelLeftDir.negate.mult(Configuration.moveSidewaysSpeed))
    }
    if (forward) {
      walkDirection.addLocal(modelForwardDir mult Configuration.moveForwardSpeed)
    }
    if (backward) {
      walkDirection.addLocal(modelForwardDir.negate mult Configuration.moveBackwardSpeed)
    }
    player.setWalkDirection(walkDirection)
  }

  override def onAction(binding: String, isPressed: Boolean, tpf: Float): Unit = {
    if (binding == StrafeLeft) {
      leftStrafe = isPressed
    }
    else if (binding == StrafeRight) {
      rightStrafe = isPressed
    }
    else if (binding == WalkForward) {
      forward = isPressed
    }
    else if (binding == WalkBackward) {
      backward = isPressed
    }
    else if (binding == Jump) {
      if (isPressed) {
        player.jump()
      }
    }
    else if (binding == Duck) {
      player.setDucked(isPressed)
    }
  }

  override def onAnalog(name: String, value: Float, tpf: Float): Unit =
    name match {
      case RotateLeft ⇒ rotatePlayerHorizontally(value)
      case RotateRight ⇒ rotatePlayerHorizontally(-value)
      case _ ⇒
    }

  private def rotatePlayerHorizontally(value: Float) = {
    val rotation = new Quaternion().fromAngleAxis(PI * value * Configuration.rotateSpeed, UNIT_Y)
    player.setViewDirection(rotation mult player.getViewDirection)
  }

  private def initKeys(): Unit = {
    inputManager.addMapping(StrafeLeft, new KeyTrigger(KEY_A))
    inputManager.addMapping(StrafeRight, new KeyTrigger(KEY_D))
    inputManager.addMapping(WalkForward, new KeyTrigger(KEY_W))
    inputManager.addMapping(WalkBackward, new KeyTrigger(KEY_S))
    inputManager.addMapping(Jump, new KeyTrigger(KEY_SPACE))
    inputManager.addMapping(Duck, new KeyTrigger(KEY_LSHIFT), new KeyTrigger(KEY_RSHIFT))
    inputManager.addMapping(RotateRight, new KeyTrigger(KEY_RIGHT), new MouseAxisTrigger(AXIS_X, false))
    inputManager.addMapping(RotateLeft, new KeyTrigger(KEY_LEFT), new MouseAxisTrigger(AXIS_X, true))

    inputManager
      .addListener(
        this,
        StrafeLeft,
        StrafeRight,
        WalkForward,
        WalkBackward,
        Jump,
        Duck,
        RotateLeft,
        RotateRight)
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
