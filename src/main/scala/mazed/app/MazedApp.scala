package mazed.app

import com.jme3.app.{SimpleApplication, StatsAppState}
import com.jme3.bullet.BulletAppState
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.control.{BetterCharacterControl, RigidBodyControl}
import com.jme3.bullet.util.CollisionShapeFactory
import com.jme3.collision.CollisionResults
import com.jme3.input.KeyInput._
import com.jme3.input.MouseInput
import com.jme3.input.controls.{ActionListener, AnalogListener, KeyTrigger, MouseAxisTrigger}
import com.jme3.light.{AmbientLight, DirectionalLight}
import com.jme3.math.FastMath.PI
import com.jme3.math.Vector3f.{UNIT_X, UNIT_Y, UNIT_Z}
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
  extends SimpleApplication(new DebugKeysAppState)
          with ActionListener
          with AnalogListener
          with LazyLogging {
  private val StrafeLeft = "Strafe Left"
  private val StrafeRight = "Strafe Right"
  private val WalkForward = "Walk Forward"
  private val WalkBackward = "Walk Backward"
  private val RotateRight = "Rotate Right"
  private val RotateLeft = "Rotate Left"
  private val RotateUp = "Rotate Up"
  private val RotateDown = "Rotate Down"
  private val Jump = "Jump"
  private val Duck = "Duck"

  private val configHelper = new ConfigHelper(config)
  private val moveForwardSpeed = config.getDouble("player.moveForwardSpeed").toFloat
  private val moveSidewaysSpeed = config.getDouble("player.moveSidewaysSpeed").toFloat
  private val moveBackwardSpeed = config.getDouble("player.moveBackwardSpeed").toFloat
  private val lookVerticalSpeed = config.getDouble("player.lookVerticalSpeed").toFloat
  private val rotateSpeed = config.getDouble("player.rotateSpeed").toFloat
  private val playerHeight = config.getDouble("player.height").toFloat
  private val playerRadius = config.getDouble("player.radius").toFloat

  private var leftStrafe = false
  private var rightStrafe = false
  private var forward = false
  private var backward = false

  // offset of camera from character
  private[app] var cameraOffset = configHelper.getVector3f("player.cameraOffset")

  private[app] def playerFinalHeight: Float = {
    playerHeight * (if (player.isDucked) player.getDuckedFactor else 1f)
  }

  private lazy val bulletAppState = {
    val bulletState =  new BulletAppState
    bulletState
  }

  private[app] lazy val (player, characterNode): (BetterCharacterControl, Node) = {
    val mass = config.getDouble("player.mass").toFloat
    val initialLocation = configHelper.getVector3f("player.initialLocation")
    val jumpForce = configHelper.getVector3f("player.jumpForce")
    val charNode = new Node("character node")

    charNode.setLocalTranslation(initialLocation)

    val physicsCharacter = new BetterCharacterControl(playerRadius, playerHeight, mass)
    charNode.addControl(physicsCharacter)
    bulletAppState.getPhysicsSpace.add(physicsCharacter)
    val gravity = configHelper.getVector3f("player.gravity")
    physicsCharacter.setGravity(gravity)
    physicsCharacter.setViewDirection(configHelper.getVector3f("player.initialLookAt"))
    physicsCharacter.setJumpForce(jumpForce)

    val maybeModel = configHelper.getOptionalString("player.model")
    maybeModel foreach { model ⇒
      val scale = config.getDouble("player.modelScale").toFloat
      val characterModel = assetManager.loadModel(model)
      characterModel.setLocalScale(scale)
      charNode.attachChild(characterModel)
    }

    (physicsCharacter, charNode)
  }


  private[app] lazy val camNode: CameraNode = {
    val cameraNode = new CameraNode("CamNode", cam)
    cameraNode.setControlDir(ControlDirection.SpatialToCamera)
    cameraNode.setLocalTranslation(cameraOffset)
    val quat: Quaternion = new Quaternion
    val cameraLookAt = configHelper.getVector3f("player.cameraLookAt")
    quat.lookAt(cameraLookAt, Vector3f.UNIT_Y)
    cameraNode.setLocalRotation(quat)

    cameraNode
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
    characterNode.attachChild(camNode)
    initKeys()
  }

  var firstTime = true
  // temporarily bring the camera in closer to the player if there's an obstruction in-between
  private def adjustCamera(): Unit = {
    if (firstTime) { // the scene isn't full baked on the first frame
      firstTime = false
      return
    }

    var cameraAdjusted = false
    logger.debug(s"cameraOffset=$cameraOffset")
    logger.debug(s"characterPos=${characterNode.getWorldTranslation}")
    // use a 90% fudge factor to simulate distance from top of head to eyes
    val targetCameraPos = characterNode.localToWorld(cameraOffset add (0, playerFinalHeight * 0.9f, 0), new Vector3f)
    val topOfHead = characterNode.getWorldTranslation.add(0, playerFinalHeight * 0.9f, 0)
    if (topOfHead == targetCameraPos) {
      return // first person mode(?)
    }

    logger.debug(s"targetCameraPos=$targetCameraPos")
    logger.debug(s"topOfHead=$topOfHead")

    val distance = targetCameraPos distance topOfHead
    val directionTowardsCamera = targetCameraPos subtract topOfHead
    val normalizedDirection = directionTowardsCamera.normalize

    logger.debug(s"directionTowardsCamera=$directionTowardsCamera")

    if (distance <= playerRadius) {
      // first person view
      setCamWorldPosition(topOfHead)
      cameraAdjusted = true
    }
    else {
      val results = new CollisionResults
      val ray = new Ray(topOfHead, normalizedDirection)
      ray.setLimit(distance)
      logger.debug(s"ray=$ray; limit=$distance")
      sceneNode.collideWith(ray, results)
      val isCollision = results.size > 0
      if (isCollision) {
        val closest  = results.getClosestCollision
        val worldContactPoint = closest.getContactPoint
        logger.debug(s"worldContactPoint = $worldContactPoint")
        setCamWorldPosition(worldContactPoint)
        cameraAdjusted = true
      }
    }
    if (!cameraAdjusted) {
      setCamWorldPosition(targetCameraPos)
    }
    logger.debug(s"-----------")
  }

  /**
    * This is the main event loop--walking happens here.
    * We check in which direction the player is walking by interpreting
    * the camera direction forward (camDir) and to the side (camLeft).
    * The setWalkDirection() command is what lets a physics-controlled player walk.
    * We also make sure here that the camera moves with player.
    */
  override def simpleUpdate(tpf: Float): Unit = {
    adjustCamera()

    // Get current forward and left vectors of model by using its rotation
    // to rotate the unit vectors
    val modelForwardDir = characterNode.getWorldRotation.mult(UNIT_Z)
    val modelLeftDir = characterNode.getWorldRotation.mult(UNIT_X)

    val walkDirection = Vector3f.ZERO.clone
    if (leftStrafe) {
      walkDirection.addLocal(modelLeftDir mult moveSidewaysSpeed)
    }
    if (rightStrafe) {
      walkDirection.addLocal(modelLeftDir.negate.mult(moveSidewaysSpeed))
    }
    if (forward) {
      walkDirection.addLocal(modelForwardDir mult moveForwardSpeed)
    }
    if (backward) {
      walkDirection.addLocal(modelForwardDir.negate mult moveBackwardSpeed)
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
      case RotateLeft ⇒ rotatePlayer(value, UNIT_Y)
      case RotateRight ⇒ rotatePlayer(-value, UNIT_Y)
      case RotateUp ⇒ rotateCamera(-value, UNIT_X)
      case RotateDown ⇒ rotateCamera(value, UNIT_X)
      case _ ⇒
    }

  private def rotatePlayer(value: Float, axis: Vector3f) = {
    val rotation = new Quaternion().fromAngleAxis(PI * value * rotateSpeed, axis)
    player.setViewDirection(rotation mult player.getViewDirection)
  }


  def rotateCamera(value: Float, axis: Vector3f): Unit = {
    val rotation = new Quaternion().fromAngleAxis(PI * value * lookVerticalSpeed, axis)
    camNode.setLocalRotation(rotation mult camNode.getLocalRotation)
  }

  private def setCamWorldPosition(worldPosition: Vector3f): Unit = {
    camNode.setLocalTranslation(characterNode.worldToLocal(worldPosition, new Vector3f))
  }

  private def initKeys(): Unit = {
    inputManager.addMapping(StrafeLeft, new KeyTrigger(KEY_A))
    inputManager.addMapping(StrafeRight, new KeyTrigger(KEY_D))
    inputManager.addMapping(WalkForward, new KeyTrigger(KEY_W))
    inputManager.addMapping(WalkBackward, new KeyTrigger(KEY_S))
    inputManager.addMapping(Jump, new KeyTrigger(KEY_SPACE))
    inputManager.addMapping(Duck, new KeyTrigger(KEY_LSHIFT), new KeyTrigger(KEY_RSHIFT))
    inputManager.addMapping(RotateRight, new KeyTrigger(KEY_RIGHT), new MouseAxisTrigger(MouseInput.AXIS_X, false))
    inputManager.addMapping(RotateLeft, new KeyTrigger(KEY_LEFT), new MouseAxisTrigger(MouseInput.AXIS_X, true))
    inputManager.addMapping(RotateUp, new KeyTrigger(KEY_RIGHT), new MouseAxisTrigger(MouseInput.AXIS_Y, false))
    inputManager.addMapping(RotateDown, new KeyTrigger(KEY_LEFT), new MouseAxisTrigger(MouseInput.AXIS_Y, true))

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
        RotateRight,
        RotateUp,
        RotateDown)
  }


  private def initSky(): Unit = {
    val skyTexture = configHelper.getOptionalString("maze.sky.texture")
    val skyColor = configHelper.getOptionalColor("maze.sky.color")
    skyTexture foreach { tx ⇒
      rootNode.attachChild(SkyFactory.createSky(assetManager, tx, false))
    }
    skyColor foreach { color ⇒
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
