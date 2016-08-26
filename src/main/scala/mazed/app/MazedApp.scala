package mazed.app

import com.jme3.app.{DebugKeysAppState, SimpleApplication, StatsAppState}
import com.jme3.bounding.BoundingVolume
import com.jme3.bullet.BulletAppState
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.control.{BetterCharacterControl, RigidBodyControl}
import com.jme3.bullet.util.CollisionShapeFactory
import com.jme3.collision.CollisionResults
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
  extends SimpleApplication(new StatsAppState, new DebugKeysAppState)
          with ActionListener
          with AnalogListener
          with LazyLogging {

  private val configHelper = new ConfigHelper(config)
  private val moveForwardMult = config.getDouble("player.moveForwardMult").toFloat
  private val moveSidewaysMult = config.getDouble("player.moveSidewaysMult").toFloat
  private val moveBackwardMult = config.getDouble("player.moveBackwardMult").toFloat
  private val playerHeight = config.getDouble("player.height").toFloat
  private val normalGravity = new Vector3f(0, -9.81f, 0)

  private var leftStrafe = false
  private var rightStrafe = false
  private var forward = false
  private var backward = false
  
  // offset of camera from character
  private var cameraOffset = configHelper.getVector3f("player.cameraOffset")

  private lazy val bulletAppState = {
    val bulletState =  new BulletAppState
    val debugBullet = config.getBoolean("app.debugBullet")
    bulletState.setDebugEnabled(debugBullet)
    bulletState
  }

  private lazy val (player, characterNode): (BetterCharacterControl, Node) = {
    val radius = config.getDouble("player.radius").toFloat
    val mass = config.getDouble("player.mass").toFloat
    val initialLocation = configHelper.getVector3f("player.initialLocation")
    val jumpForce = configHelper.getVector3f("player.jumpForce")
    val charNode = new Node("character node")

    charNode.setLocalTranslation(initialLocation)

    val physicsCharacter = new BetterCharacterControl(radius, playerHeight, mass)
    charNode.addControl(physicsCharacter)
    bulletAppState.getPhysicsSpace.add(physicsCharacter)
    physicsCharacter.setGravity(normalGravity)
    physicsCharacter.setViewDirection(configHelper.getVector3f("player.initialLookAt"))
    physicsCharacter.setJumpForce(jumpForce)

    val characterModel = assetManager.loadModel("Models/Jaime/Jaime.j3o")
    characterModel.setLocalScale(1.4f)
    charNode.attachChild(characterModel)

    (physicsCharacter, charNode)
  }


  private lazy val camNode: CameraNode = {
    val camera = new CameraNode("CamNode", cam)
    camera.setControlDir(ControlDirection.SpatialToCamera)
    camera.setLocalTranslation(cameraOffset)
    val quat: Quaternion = new Quaternion
    // These coordinates are local, the camNode is attached to the character node!
    quat.lookAt(Vector3f.UNIT_Z, Vector3f.UNIT_Y)
    camera.setLocalRotation(quat)
    camera
  }

  private lazy val sceneNode: Node = {
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
    initSky()
    initLight()
    rootNode.attachChild(sceneNode)
    rootNode.attachChild(characterNode)
    characterNode.attachChild(camNode)
    initKeys()
  }

  // work in progress
  private def adjustCameraOffset(): Unit = {
    // temporarily bring the camera in closer to the player if there's an obstruction in-between
    val cameraPos = camNode.getLocalTranslation
    logger.debug(s"camera position=$cameraPos; world pos=${camNode.getWorldTranslation}; cam world rot=${camNode.getWorldRotation}")
    val distance = cameraPos.length
    logger.debug(s"distance=$distance")
    if (distance > 0f) {
      val results = new CollisionResults
      val characterBound: BoundingVolume = characterNode.getWorldBound
      logger.debug(s"charPos=${characterNode.getLocalTranslation}; characterBound=$characterBound")
      val centerTop = characterBound.getCenter add new Vector3f(0, playerHeight / 2, 0)
      logger.debug(s"centerTop=$centerTop")
      val direction = camNode.getWorldTranslation subtract centerTop
      val ray = new Ray(centerTop, direction.normalize)
      ray.setLimit(direction.length)
      logger.debug(s"ray=$ray")
      sceneNode.getWorldBound.collideWith(ray, results)
      if (results.size() > 0) {
        val closest  = results.getClosestCollision
        val worldContactPoint = closest.getContactPoint
        logger.debug(s"closest contact point = $worldContactPoint")
        val newPosition = worldContactPoint subtract camNode.getWorldTranslation
        logger.debug(s"new camera position=$newPosition")
/*
        camNode.setLocalTranslation(newPosition)
*/
      } else {
/*
        camNode.setLocalTranslation(cameraOffset)
*/
      }
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
    adjustCameraOffset()

    // Get current forward and left vectors of model by using its rotation
    // to rotate the unit vectors
    val modelForwardDir = characterNode.getWorldRotation.mult(Vector3f.UNIT_Z)
    val modelLeftDir = characterNode.getWorldRotation.mult(Vector3f.UNIT_X)

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

    fpsText.setText("Ducked = " + player.isDucked)
  }

  def onAction(binding: String, isPressed: Boolean, tpf: Float): Unit = {
    if (binding == "Strafe Left") {
      leftStrafe = isPressed
    }
    else if (binding == "Strafe Right") {
      rightStrafe = isPressed
    }
    else if (binding == "Walk Forward") {
      forward = isPressed
    }
    else if (binding == "Walk Backward") {
      backward = isPressed
    }
    else if (binding == "Jump") {
      if (isPressed) {
        player.jump()
      }
    }
    else if (binding == "Duck") {
      player.setDucked(isPressed)
    }
  }

  def rotatePlayer(value: Float, axis: Vector3f) = {
    val rotationSpeed = 1f // make configurable
    val rotateL: Quaternion = new Quaternion().fromAngleAxis(
      FastMath.PI * value * rotationSpeed,
      Vector3f.UNIT_Y)
    player.setViewDirection(rotateL.mult(player.getViewDirection))

  }

  def onAnalog(name: String, value: Float, tpf: Float): Unit = {
    if (name == "Rotate Left") rotatePlayer(value, Vector3f.UNIT_Y)
    else if (name.equals("Rotate Right")) rotatePlayer(-value, Vector3f.UNIT_Y)
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

    inputManager
      .addListener(
        this,
        "Strafe Left",
        "Strafe Right",
        "Walk Forward",
        "Walk Backward",
        "Jump",
        "Duck",
        "Rotate Left",
        "Rotate Right")
  }


  private def initSky(): Unit = {
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
    dl.setDirection(new Vector3f(2.8f, -2.8f, -2.8f).normalizeLocal)
    rootNode.addLight(dl)
  }
}
