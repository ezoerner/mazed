package mazed.app

import com.jme3.app.state.{AbstractAppState, AppStateManager}
import com.jme3.app.{Application, SimpleApplication}
import com.jme3.bullet.BulletAppState
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.control.{BetterCharacterControl, RigidBodyControl}
import com.jme3.bullet.util.CollisionShapeFactory
import com.jme3.input.KeyInput._
import com.jme3.input.controls.{ActionListener, KeyTrigger}
import com.jme3.light.{AmbientLight, DirectionalLight}
import com.jme3.math.{ColorRGBA, Vector3f}
import com.jme3.scene.{Geometry, Node}
import com.jme3.system.AppSettings
import com.jme3.util.SkyFactory
import com.typesafe.config.{Config, ConfigFactory}
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

class MazedApp(rand: Random, config: Config) extends SimpleApplication with ActionListener {
  private val configHelper = new ConfigHelper(config)
  private var bulletAppState: BulletAppState = _
  private var player: BetterCharacterControl = _
  private var characterNode: Node = _

  private var leftStrafe = false
  private var rightStrafe = false
  private var forward = false
  private var backward = false

  private val moveForwardMult = config.getDouble("player.moveForwardMult").toFloat
  private val moveSidewaysMult = config.getDouble("player.moveSidewaysMult").toFloat
  private val moveBackwardMult = config.getDouble("player.moveBackwardMult").toFloat
  private val playerHeight = config.getDouble("player.height").toFloat
  private val normalGravity = new Vector3f(0, -9.81f, 0) // Earth gravity in m/s2
  private var planet: Geometry = _

  override def simpleInitApp(): Unit = {
    initPhysics()
    initCamera()
    initSky()
    initLight()
    initScene()
    initPlayer()
    initializeLate {
      redefineKeys()
    }
  }

  /**
    * This is the main event loop--walking happens here.
    * We check in which direction the player is walking by interpreting
    * the camera direction forward (camDir) and to the side (camLeft).
    * The setWalkDirection() command is what lets a physics-controlled player walk.
    * We also make sure here that the camera moves with player.
    */
  override def simpleUpdate(tpf: Float): Unit = {
    // Apply planet gravity to character if close enough (see below)
    checkPlanetGravity()

    // Get current forward and left vectors of model by using its rotation
    // to rotate the unit vectors
//    val modelForwardDir = characterNode.getWorldRotation.mult(Vector3f.UNIT_Z)
//    val modelLeftDir = characterNode.getWorldRotation\.mult(Vector3f.UNIT_X)

    // TODO should we get the current direction from the camera or the player?
    val camLeft = cam.getLeft.mult(moveSidewaysMult)
    val walkDirection = Vector3f.ZERO.clone
    if (leftStrafe) walkDirection.addLocal(camLeft.mult(moveSidewaysMult))
    if (rightStrafe) walkDirection.addLocal(camLeft.negate.mult(moveSidewaysMult))
    if (forward) walkDirection.addLocal(cam.getDirection.mult(moveForwardMult))
    if (backward) walkDirection.addLocal(cam.getDirection.negate.mult(moveBackwardMult))
    player.setWalkDirection(walkDirection)
    cam.setLocation(characterNode.getLocalTranslation.add(0, playerHeight, 0))

    fpsText.setText("Touch da ground = " + player.isOnGround)

/*
    // ViewDirection is local to characters physics system!
    // The final world rotation depends on the gravity and on the state of
    // setApplyPhysicsLocal()
    if (leftRotate) {
      val rotateL: Quaternion = new Quaternion().fromAngleAxis(FastMath.PI * tpf, Vector3f.UNIT_Y)
      rotateL.multLocal(viewDirection)
    }
    else if (rightRotate) {
      val rotateR: Quaternion = new Quaternion().fromAngleAxis(-FastMath.PI * tpf, Vector3f.UNIT_Y)
      rotateR.multLocal(viewDirection)
    }
    physicsCharacter.setViewDirection(viewDirection)
    fpsText.setText("Touch da ground = " + physicsCharacter.isOnGround)
    if (!lockView) cam.lookAt(characterNode.getWorldTranslation.add(new Vector3f(0, 2, 0)), Vector3f.UNIT_Y)
*/
  }

  def onAction(binding: String, isPressed: Boolean, tpf: Float): Unit = {
    if (binding == "Strafe Left") leftStrafe = isPressed
    else if (binding == "Strafe Right") rightStrafe = isPressed
    else if (binding == "Walk Forward") forward = isPressed
    else if (binding == "Walk Backward") backward = isPressed
    else if (binding == "Jump") if (isPressed) player.jump()
  }

  private def checkPlanetGravity(): Unit = {
    val planetDist: Vector3f = planet.getWorldTranslation.subtract(characterNode.getWorldTranslation)
    if (planetDist.length < 24) player.setGravity(planetDist.normalizeLocal.multLocal(9.81f))
    else player.setGravity(normalGravity)
  }

  private def initPhysics() = {
    bulletAppState = new BulletAppState
    stateManager.attach(bulletAppState)
    // bulletAppState.setDebugEnabled(true)
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

    inputManager.addMapping("Strafe Left", new KeyTrigger(KEY_A))
    inputManager.addMapping("Strafe Right", new KeyTrigger(KEY_D))
    inputManager.addMapping("Walk Forward", new KeyTrigger(KEY_W))
    inputManager.addMapping("Walk Backward", new KeyTrigger(KEY_S))
    inputManager.addMapping("Jump", new KeyTrigger(KEY_SPACE))
    inputManager.addMapping("Duck", new KeyTrigger(KEY_LSHIFT), new KeyTrigger(KEY_RSHIFT))
    inputManager.addListener(this, "Strafe Left", "Strafe Right", "Walk Forward", "Walk Backward", "Jump", "Duck")
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
    // TODO what does this directional light do, is it needed with all unshaded materials?
    dl.setDirection(new Vector3f(2.8f, -2.8f, -2.8f).normalizeLocal)
    rootNode.addLight(dl)
  }

  private def initScene(): Unit = {
    val (mazeNode, floor) = new MazeSceneBuilder(config, assetManager).build
    planet = floor
    val sceneNode = new Node()
    sceneNode.attachChild(mazeNode)
    sceneNode.attachChild(floor)

    // We set up collision detection for the scene by creating a
    // compound collision shape and a static RigidBodyControl with mass zero.
    val sceneShape: CollisionShape = CollisionShapeFactory.createMeshShape(sceneNode)
    val landscape = new RigidBodyControl(sceneShape, 0)
    sceneNode.addControl(landscape)
    rootNode.attachChild(sceneNode)
    bulletAppState.getPhysicsSpace.add(landscape)
  }

  private def initPlayer(): Unit = {
    // We set up collision detection for the player by creating
    // a capsule collision shape and a CharacterControl.
    // The CharacterControl offers extra settings for
    // size, stepheight, jumping, falling, and gravity.
    // We also put the player in its starting position.

    val radius = config.getDouble("player.radius").toFloat
    val mass = config.getDouble("player.mass").toFloat
/*
    val jumpSpeed = config.getDouble("player.jumpSpeed").toFloat
    val fallSpeed = config.getDouble("player.fallSpeed").toFloat
    val gravity = config.getDouble("player.gravity").toFloat
*/

    val initialLocation = configHelper.getVector3f("player.initialLocation")
    characterNode = new Node("character node")
    characterNode.setLocalTranslation(initialLocation)

    player = new BetterCharacterControl(radius, playerHeight, mass)
    characterNode.addControl(player)
    rootNode.attachChild(characterNode)

    /*
        player.setJumpSpeed(jumpSpeed)
        player.setFallSpeed(fallSpeed)
        player.setGravity(gravity)
    */

    bulletAppState.getPhysicsSpace.add(player)
  }

  private def initializeLate(lateInit: ⇒Unit): Unit = {
    stateManager.attach(
      new AbstractAppState {
        override def initialize(stateManager: AppStateManager, app: Application): Unit = {
          super.initialize(stateManager, app)
          lateInit
          stateManager.detach(this)
        }
      })
  }
}
