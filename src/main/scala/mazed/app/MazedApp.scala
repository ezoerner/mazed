package mazed.app

import com.jme3.app.{DebugKeysAppState, SimpleApplication, StatsAppState}
import com.jme3.bullet.BulletAppState
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.control.{BetterCharacterControl, RigidBodyControl}
import com.jme3.bullet.util.CollisionShapeFactory
import com.jme3.input.KeyInput._
import com.jme3.input.MouseInput
import com.jme3.input.controls.{ActionListener, AnalogListener, KeyTrigger, MouseAxisTrigger}
import com.jme3.light.{AmbientLight, DirectionalLight}
import com.jme3.math._
import com.jme3.scene.control.CameraControl.ControlDirection
import com.jme3.scene.{CameraNode, Node}
import com.jme3.system.{AppSettings, JmeContext, JmeSystem}
import com.jme3.util.SkyFactory
import com.typesafe.config.{Config, ConfigFactory}
import mazed.state.LockedCamState
import org.slf4j.LoggerFactory
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
  extends SimpleApplication(new StatsAppState, new DebugKeysAppState) with ActionListener with AnalogListener {
  val logger = LoggerFactory.getLogger(this.getClass)

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
  private val normalGravity = new Vector3f(0, -9.81f, 0)

  private val viewDirection: Vector3f = configHelper.getVector3f("player.initialLookAt")

/*
  private var cameraStates: List[AppState] =  _
  private lazy val cameraStatesIterator = Iterator.continually(cameraStates).flatten
*/

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
    initPhysics()
    initSky()
    initLight()
    initScene()
    initPlayer()
    initCamera()
    initKeys()
  }

  /**
    * This is the main event loop--walking happens here.
    * We check in which direction the player is walking by interpreting
    * the camera direction forward (camDir) and to the side (camLeft).
    * The setWalkDirection() command is what lets a physics-controlled player walk.
    * We also make sure here that the camera moves with player.
    */
  override def simpleUpdate(tpf: Float): Unit = {

    // Get current forward and left vectors of model by using its rotation
    // to rotate the unit vectors
    val modelForwardDir = characterNode.getWorldRotation.mult(Vector3f.UNIT_Z)
    val modelLeftDir = characterNode.getWorldRotation.mult(Vector3f.UNIT_X)

    // TODO should we get the current direction from the camera or the player?
    //val camLeft = cam.getLeft.mult(moveSidewaysMult)

    val walkDirection = Vector3f.ZERO.clone
    if (leftStrafe) {
      walkDirection.addLocal(modelLeftDir.mult(moveSidewaysMult))
    }
    if (rightStrafe) {
      walkDirection.addLocal(modelLeftDir.negate.mult(moveSidewaysMult))
    }
    if (forward) {
      walkDirection.addLocal(modelForwardDir.mult(moveForwardMult))
    }
    if (backward) {
      walkDirection.addLocal(modelForwardDir.negate.mult(moveBackwardMult))
    }
    player.setWalkDirection(walkDirection)

/*
    if (!isThirdPersonView) {
      cam.setLocation(characterNode.getLocalTranslation.add(0, playerHeight, 0))
    }
*/

    fpsText.setText("Touch da ground = " + player.isOnGround)
  }

  def onAction(binding: String, isPressed: Boolean, tpf: Float): Unit = {
    if (binding == "Strafe Left") leftStrafe = isPressed
    else if (binding == "Strafe Right") rightStrafe = isPressed
    else if (binding == "Walk Forward") forward = isPressed
    else if (binding == "Walk Backward") backward = isPressed
    else if (binding == "Jump") if (isPressed) player.jump()
    else if (binding == "Duck") player.setDucked(isPressed)

    /*
        else if (binding == "Toggle Perspective") {
          if (isPressed) {
            isThirdPersonView = !isThirdPersonView
          }
          flyCam.setEnabled(!isThirdPersonView)
          camNode.setEnabled(isThirdPersonView)
        }
    */
  }

  def rotatePlayer(value: Float, axis: Vector3f) = {
    val rotationSpeed = 1f // make configurable

    val mat: Matrix3f = new Matrix3f
    mat.fromAngleNormalAxis(rotationSpeed * value, axis)

    val up: Vector3f = Vector3f.ZERO//cam.getUp
    val left: Vector3f = Vector3f.ZERO//cam.getLeft
    val dir: Vector3f = player.getViewDirection//cam.getDirection

//    mat.mult(up, up)
//    mat.mult(left, left)
    mat.mult(dir, dir)

    val q: Quaternion = new Quaternion
    q.fromAxes(left, up, dir)
    q.normalizeLocal

    val rotateL: Quaternion = new Quaternion().fromAngleAxis(FastMath.PI * value, Vector3f.UNIT_Y)
    rotateL.multLocal(viewDirection)

    player.setViewDirection(viewDirection) //q.getRotationColumn(2))

  }

  def onAnalog(name: String, value: Float, tpf: Float): Unit = {
    if (name == "Rotate Left") rotatePlayer(value, Vector3f.UNIT_Y)
    else if (name.equals("Rotate Right")) rotatePlayer(-value, Vector3f.UNIT_Y)
  }

  private def initPhysics() = {
    bulletAppState = new BulletAppState
    stateManager.attach(bulletAppState)
    val debugBullet = config.getBoolean("app.debugBullet")
    bulletAppState.setDebugEnabled(debugBullet)
  }

  private def initCamera(): Unit = {
/*
    val initialLocation = configHelper.getVector3f("player.initialLocation")
    val initialLookAt = configHelper.getVector3f("player.initialLookAt")
    // origin of player is at its bottom, translate the camera to the top of player
    cam.setLocation(initialLocation.add(0, playerHeight, 0))
    cam.lookAt(initialLookAt, Vector3f.UNIT_Y)
*/
    val camNode = new CameraNode("LockedCamNode", cam)
    camNode.setControlDir(ControlDirection.SpatialToCamera)
    // offset of camera from character
    // TODO make configurable
    camNode.setLocalTranslation(new Vector3f(0f, 2.8f, -6f))
    val quat: Quaternion = new Quaternion
    // These coordinates are local, the camNode is attached to the character node!
    quat.lookAt(Vector3f.UNIT_Z, Vector3f.UNIT_Y)
    camNode.setLocalRotation(quat)
    characterNode.attachChild(camNode)

    val lockedCamState = new LockedCamState(camNode)
    stateManager.attachAll(lockedCamState)
  }

  def initKeys(): Unit = {
    inputManager.addMapping("Strafe Left", new KeyTrigger(KEY_A))
    inputManager.addMapping("Strafe Right", new KeyTrigger(KEY_D))
    inputManager.addMapping("Walk Forward", new KeyTrigger(KEY_W))
    inputManager.addMapping("Walk Backward", new KeyTrigger(KEY_S))
    inputManager.addMapping("Jump", new KeyTrigger(KEY_SPACE))
    inputManager.addMapping("Duck", new KeyTrigger(KEY_LSHIFT), new KeyTrigger(KEY_RSHIFT))
    inputManager.addMapping("Rotate Right", new KeyTrigger(KEY_RIGHT), new MouseAxisTrigger(MouseInput.AXIS_X, false))
    inputManager.addMapping("Rotate Left", new KeyTrigger(KEY_LEFT), new MouseAxisTrigger(MouseInput.AXIS_X, true))

    inputManager.addMapping("Toggle Perspective", new KeyTrigger(KEY_F5))
    inputManager
      .addListener(
        this,
        "Strafe Left",
        "Strafe Right",
        "Walk Forward",
        "Walk Backward",
        "Jump",
        "Duck",
        "Toggle Perspective",
        "Rotate Left",
        "Rotate Right")
  }


  private def initSky(): Unit = {
    /*
        viewPort.setBackgroundColor(new ColorRGBA(0.7f, 0.8f, 1f, 1f))
    */
    rootNode.attachChild(
      SkyFactory.createSky(
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

    val radius = config.getDouble("player.radius").toFloat
    val mass = config.getDouble("player.mass").toFloat
    val initialLocation = configHelper.getVector3f("player.initialLocation")
    characterNode = new Node("character node")

    characterNode.setLocalTranslation(initialLocation)

    player = new BetterCharacterControl(radius, playerHeight, mass)
    characterNode.addControl(player)
    bulletAppState.getPhysicsSpace.add(player)
    player.setGravity(normalGravity)
    player.setViewDirection(viewDirection)
    //player.setWalkDirection(initialLookAt)

    val characterModel = assetManager.loadModel("Models/Jaime/Jaime.j3o")
    characterModel.setLocalScale(1.4f)
    characterNode.attachChild(characterModel)

    rootNode.attachChild(characterNode)
  }

  /*
  private def initializeLate(lateInit: ⇒ Unit): Unit = {
    stateManager.attach(
      new AbstractAppState {
        override def initialize(stateManager: AppStateManager, app: Application): Unit = {
          super.initialize(stateManager, app)
          lateInit
          stateManager.detach(this)
        }
      })
  }
*/

}
