package mazed.app

import com.jme3.app.state.{AbstractAppState, AppStateManager}
import com.jme3.app.{Application, SimpleApplication}
import com.jme3.bullet.BulletAppState
import com.jme3.bullet.collision.shapes.{CapsuleCollisionShape, CollisionShape}
import com.jme3.bullet.control.{CharacterControl, RigidBodyControl}
import com.jme3.bullet.util.CollisionShapeFactory
import com.jme3.input.KeyInput
import com.jme3.input.controls.{ActionListener, KeyTrigger}
import com.jme3.light.{AmbientLight, DirectionalLight}
import com.jme3.math.{ColorRGBA, Vector3f}
import com.jme3.system.AppSettings
import com.jme3.util.SkyFactory
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.bridge.SLF4JBridgeHandler

import scala.util.Random

object MazedApp {

  def main(args: Array[String]): Unit = {
    // Optionally remove existing handlers attached to j.u.l root logger
    SLF4JBridgeHandler.removeHandlersForRootLogger()  // (since SLF4J 1.6.5)

    // add SLF4JBridgeHandler to j.u.l's root logger, should be done once during
    // the initialization phase of your application
    SLF4JBridgeHandler.install()

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
}

class MazedApp(rand: Random, config: Config) extends SimpleApplication with ActionListener {
  private val configHelper = new ConfigHelper(config)
  private var bulletAppState: BulletAppState = null
  private var left: Boolean = false
  private var right: Boolean = false
  private var up: Boolean = false
  private var down: Boolean = false
  private var player: CharacterControl = null
  private val moveForwardMult = config.getDouble("player.moveForwardMult").toFloat
  private val moveSidewaysMult = config.getDouble("player.moveSidewaysMult").toFloat
  private val moveBackwardMult = config.getDouble("player.moveBackwardMult").toFloat


  override def simpleInitApp(): Unit = {
    initPhysics()
    initCamera()
    initSky()
    initLight()
    initScene()
    initPlayer()
    stateManager.attach(new AbstractAppState {
      override def initialize(stateManager: AppStateManager, app: Application): Unit = {
        super.initialize(stateManager, app)
        redefineKeys()
        stateManager.detach(this)
      }
    })
  }

  /**
    * This is the main event loop--walking happens here.
    * We check in which direction the player is walking by interpreting
    * the camera direction forward (camDir) and to the side (camLeft).
    * The setWalkDirection() command is what lets a physics-controlled player walk.
    * We also make sure here that the camera moves with player.
    */
  override def simpleUpdate(tpf: Float): Unit = {
    val camLeft = cam.getLeft.mult(moveSidewaysMult)
    val walkDirection = Vector3f.ZERO.clone
    if (left) walkDirection.addLocal(camLeft)
    if (right) walkDirection.addLocal(camLeft.negate)
    if (up) walkDirection.addLocal(cam.getDirection.mult(moveForwardMult))
    if (down) walkDirection.addLocal(cam.getDirection.mult(moveBackwardMult).negate)
    player.setWalkDirection(walkDirection)
    cam.setLocation(player.getPhysicsLocation)
  }

  def onAction(binding: String, isPressed: Boolean, tpf: Float): Unit = {
    if (binding == "Left") left = isPressed
    else if (binding == "Right") right = isPressed
    else if (binding == "Up") up = isPressed
    else if (binding == "Down") down = isPressed
    else if (binding == "Jump") if (isPressed) player.jump()
  }

  private def initPhysics() = {
    bulletAppState = new BulletAppState
    stateManager.attach(bulletAppState)
    //bulletAppState.getPhysicsSpace().enableDebug(assetManager);
  }

  /** These are our custom actions triggered by key presses.
    * We do not walk yet, we just keep track of the direction the user pressed. */
  private def initCamera(): Unit = {
    // We re-use the flyby camera for rotation, while positioning is handled by physics
    val initialLocation = configHelper.getVector3f("player.initialLocation")
    val initialLookAt = configHelper.getVector3f("player.initialLookAt")
    cam.setLocation(initialLocation)
    cam.lookAt(initialLookAt, Vector3f.UNIT_Y)
  }

  def redefineKeys(): Unit = {
    /** We over-write some navigational key mappings here, so we can
      * add physics-controlled walking and jumping: */
    inputManager.deleteMapping("FLYCAM_ZoomIn")
    inputManager.deleteMapping("FLYCAM_ZoomOut")
    inputManager.deleteMapping("FLYCAM_StrafeLeft")
    inputManager.deleteMapping("FLYCAM_StrafeRight")
    inputManager.deleteMapping("FLYCAM_Forward")
    inputManager.deleteMapping("FLYCAM_Backward")
    inputManager.deleteMapping("FLYCAM_Rise")
    inputManager.deleteMapping("FLYCAM_Lower")

    inputManager.addMapping("Left", new KeyTrigger(KeyInput.KEY_A))
    inputManager.addMapping("Right", new KeyTrigger(KeyInput.KEY_D))
    inputManager.addMapping("Up", new KeyTrigger(KeyInput.KEY_W))
    inputManager.addMapping("Down", new KeyTrigger(KeyInput.KEY_S))
    inputManager.addMapping("Jump", new KeyTrigger(KeyInput.KEY_SPACE))
    inputManager.addListener(this, "Left")
    inputManager.addListener(this, "Right")
    inputManager.addListener(this, "Up")
    inputManager.addListener(this, "Down")
    inputManager.addListener(this, "Jump")
  }


  private def initSky(): Unit = {
/*
    viewPort.setBackgroundColor(new ColorRGBA(0.7f, 0.8f, 1f, 1f))
*/
    rootNode.attachChild(SkyFactory.createSky(
      assetManager, "Textures/Sky/Bright/BrightSky.dds", false))
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

  private def initScene(): Unit = {
    val mazeNode = new MazeSceneBuilder(config, assetManager).build

    // We set up collision detection for the scene by creating a
    // compound collision shape and a static RigidBodyControl with mass zero.
    val sceneShape: CollisionShape = CollisionShapeFactory.createMeshShape(mazeNode)
    val landscape = new RigidBodyControl(sceneShape, 0)
    mazeNode.addControl(landscape)
    rootNode.attachChild(mazeNode)
    bulletAppState.getPhysicsSpace.add(landscape)
  }

  private def initPlayer(): Unit = {
    // We set up collision detection for the player by creating
    // a capsule collision shape and a CharacterControl.
    // The CharacterControl offers extra settings for
    // size, stepheight, jumping, falling, and gravity.
    // We also put the player in its starting position.
    val height = config.getDouble("player.height").toFloat
    val radius = config.getDouble("player.radius").toFloat
    val jumpSpeed = config.getDouble("player.jumpSpeed").toFloat
    val fallSpeed = config.getDouble("player.fallSpeed").toFloat
    val gravity = config.getDouble("player.gravity").toFloat
    val stepHeight = config.getDouble("player.stepHeight").toFloat
    val capsuleShape: CapsuleCollisionShape = new CapsuleCollisionShape(radius, height, 1)
    player = new CharacterControl(capsuleShape, stepHeight)
    player.setJumpSpeed(jumpSpeed)
    player.setFallSpeed(fallSpeed)
    player.setGravity(gravity)

    val initialLocation = configHelper.getVector3f("player.initialLocation")
    player.setPhysicsLocation(initialLocation)
    bulletAppState.getPhysicsSpace.add(player)
  }
}
