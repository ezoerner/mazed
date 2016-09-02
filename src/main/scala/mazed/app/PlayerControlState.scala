package mazed.app

import com.jme3.app.Application
import com.jme3.app.state.{AbstractAppState, AppStateManager}
import com.jme3.bullet.control.BetterCharacterControl
import com.jme3.input.KeyInput._
import com.jme3.input.MouseInput._
import com.jme3.input.controls.{ActionListener, AnalogListener, KeyTrigger, MouseAxisTrigger}
import com.jme3.math.FastMath._
import com.jme3.math.Vector3f._
import com.jme3.math.{Quaternion, Vector3f}
import com.jme3.scene.Node

class PlayerControlState extends AbstractAppState with ActionListener with AnalogListener {
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

  private var app: MazedApp = _

  private[app] lazy val (player, characterNode): (BetterCharacterControl, Node) = {
    val mass = Configuration.playerMass
    val charNode = new Node("character node")

    charNode.setLocalTranslation(Configuration.initialLocation)

    val physicsCharacter = new BetterCharacterControl(Configuration.playerRadius, Configuration.playerHeight, mass)
    charNode.addControl(physicsCharacter)
    app.bulletAppState.getPhysicsSpace.add(physicsCharacter)
    physicsCharacter.setGravity(Configuration.gravity)
    physicsCharacter.setViewDirection(Configuration.playerInitialLookAt)
    physicsCharacter.setJumpForce(Configuration.jumpForce)

    Configuration.maybeModel foreach { model ⇒
      val characterModel = app.getAssetManager.loadModel(model)
      characterModel.setLocalScale(Configuration.playerModelScale)
      charNode.attachChild(characterModel)
    }
    (physicsCharacter, charNode)
  }

  override def initialize(
      stateManager: AppStateManager,
      app: Application): Unit = {
    super.initialize(stateManager, app)
    this.app = app.asInstanceOf[MazedApp]
    this.app.getRootNode.attachChild(characterNode)
    initKeys()
  }

  override def update(tpf: Float): Unit = {
    super.update(tpf)
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

  override def onAction(binding: String, isPressed: Boolean, tpf: Float): Unit =
    binding match {
      case StrafeLeft ⇒ leftStrafe = isPressed
      case StrafeRight ⇒ rightStrafe = isPressed
      case WalkForward ⇒ forward = isPressed
      case WalkBackward ⇒ backward = isPressed
      case Jump if isPressed ⇒ player.jump()
      case Jump ⇒ ()
      case Duck ⇒ player.setDucked(isPressed)
      case _ ⇒
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
    val inputManager = app.getInputManager
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

}
