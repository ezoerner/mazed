package mazed.app

import com.jme3.app.Application
import com.jme3.app.state.{AbstractAppState, AppStateManager}
import com.jme3.collision.CollisionResults
import com.jme3.input.KeyInput._
import com.jme3.input.MouseInput._
import com.jme3.input.controls.{AnalogListener, KeyTrigger, MouseAxisTrigger}
import com.jme3.math.FastMath._
import com.jme3.math.Vector3f._
import com.jme3.math.{Quaternion, Ray, Vector2f, Vector3f}
import com.jme3.renderer.Camera
import com.jme3.scene.CameraNode
import com.jme3.scene.control.CameraControl.ControlDirection

class CameraControlState extends AbstractAppState with AnalogListener {
  private val RotateUp = "RotateUp"
  private val RotateDown = "RotateDown"
  private val MoveCameraIn = "MoveCameraIn"
  private val MoveCameraOut = "MoveCameraOut"

  private val cameraDirection = Configuration.initialCameraOffset.normalize

  private var app: MazedApp = _
  private lazy val cam = app.getCamera
  private lazy val inputManager = app.getInputManager

  private lazy val camNode: CameraNode = {
    val cameraNode = new CameraNode("CamNode", cam)
    cameraNode.setControlDir(ControlDirection.SpatialToCamera)
    cameraNode.setLocalTranslation(centerOfHeadLocal add Configuration.initialCameraOffset)
    val quat: Quaternion = new Quaternion
    quat.lookAt(Configuration.cameraLookAt, Vector3f.UNIT_Y)
    cameraNode.setLocalRotation(quat)
    cameraNode
  }

  // tracks the "target position" of the camera without rendering a ViewPort
  private lazy val (ghostCamNode, ghostCam): (CameraNode, Camera) = {
    val ghostCamera = cam.clone
    val cameraNode = new CameraNode("Ghost CamNode", ghostCamera)
    cameraNode.setControlDir(ControlDirection.SpatialToCamera)
    cameraNode.setLocalTranslation(centerOfHeadLocal add Configuration.initialCameraOffset)
    val quat: Quaternion = new Quaternion
    quat.lookAt(Configuration.cameraLookAt, Vector3f.UNIT_Y)
    cameraNode.setLocalRotation(quat)
    (cameraNode, ghostCamera)
  }


  private def playerFinalHeight: Float =
    Configuration.playerHeight * (if (app.player.isDucked) app.player.getDuckedFactor else 1f)

  private def centerOfHeadLocal = new Vector3f(0, playerFinalHeight * 0.9f, 0)

  private def lookAtTargetPosWorld = app.characterNode.localToWorld(centerOfHeadLocal)


  override def initialize(
    stateManager: AppStateManager,
    app: Application): Unit = {
    super.initialize(stateManager, app)
    this.app = app.asInstanceOf[MazedApp]
    this.app.characterNode.attachChild(camNode)
    this.app.characterNode.attachChild(ghostCamNode)
    initKeys()
  }

  override def update(tpf: Float): Unit = {
    super.update(tpf)
    adjustCamera()
  }

  // temporarily bring the camera in closer to the player if there's an obstruction in-between
  private def adjustCamera(): Unit = {
    val projectionZ = ghostCam.getViewToProjectionZ(cam.getFrustumNear)

    // unordered corners of the (cropped) near plane
    val frustumNearCorners = List(
      new Vector2f,
      new Vector2f(cam.getWidth, 0f),
      new Vector2f(0f, cam.getHeight),
      new Vector2f(cam.getWidth, cam.getHeight)) map { v2 ⇒
      ghostCam.getWorldCoordinates(v2, projectionZ)
    }

    val newCamPosition = handleCollisionZoom(
      ghostCam.getLocation,
      lookAtTargetPosWorld,
      Configuration.minCamDistance,
      frustumNearCorners)
    setCamWorldPosition(newCamPosition)
  }

  // returns a new camera position
  private def handleCollisionZoom(
    camPos: Vector3f,
    targetPos: Vector3f,
    minOffsetDist: Float,
    frustumNearCorners: List[Vector3f]): Vector3f = {

    val offsetDist = targetPos distance camPos
    val raycastLength = offsetDist - minOffsetDist
    val camOut = (targetPos subtract camPos).normalize
    if (raycastLength < 0f) {
      // camera is already too near the lookat target
      targetPos subtract (camOut mult Configuration.firstPersonCamDistance)
    } else {
      val nearestCamPos = targetPos subtract (camOut mult minOffsetDist)

      val minHitDistance = frustumNearCorners.foldLeft(raycastLength) { case (minSoFar, corner) ⇒
        val offsetToCorner = corner subtract camPos
        val rayStart = nearestCamPos add offsetToCorner
        val rayEnd = corner
        // a result between 0 and 1 indicates a hit along the hit segment
        val rayLength = rayEnd distance rayStart
        val ray = new Ray(rayStart, (rayEnd subtract rayStart).normalize)
        ray.setLimit(rayLength)
        val results = new CollisionResults
        app.sceneNode.collideWith(ray, results)
        if (results.size > 0) results.getClosestCollision.getDistance min minSoFar
        else rayLength min minSoFar
      }

      if (minHitDistance < raycastLength) {
        val newCamPos = nearestCamPos subtract (camOut mult minHitDistance)
        newCamPos
      }
      else {
        camPos
      }
    }
  }

  private def setCamWorldPosition(worldPosition: Vector3f): Unit = {
    camNode.setLocalTranslation(app.characterNode.worldToLocal(worldPosition, new Vector3f))
  }

  private def initKeys(): Unit = {
    inputManager.addMapping(RotateUp, new KeyTrigger(KEY_UP), new MouseAxisTrigger(AXIS_Y, false))
    inputManager.addMapping(RotateDown, new KeyTrigger(KEY_DOWN), new MouseAxisTrigger(AXIS_Y, true))
    inputManager.addMapping(MoveCameraIn, new MouseAxisTrigger(AXIS_WHEEL, true))
    inputManager.addMapping(MoveCameraOut, new MouseAxisTrigger(AXIS_WHEEL, false))
    inputManager.addListener(this, RotateUp,
      RotateDown,
      MoveCameraIn,
      MoveCameraOut)
  }

  override def onAnalog(name: String, value: Float, tpf: Float): Unit =
    name match {
      case RotateUp ⇒ rotateCameraVertically(-value)
      case RotateDown ⇒ rotateCameraVertically(value)
      case MoveCameraIn ⇒ moveGhostCameraOffset(-value)
      case MoveCameraOut ⇒ moveGhostCameraOffset(value)
      case _ ⇒
    }

  private def rotateCameraVertically(value: Float): Unit = {
    val rotation = new Quaternion().fromAngleAxis(PI * value * Configuration.lookVerticalSpeed, UNIT_X)
    val newRotation = rotation mult camNode.getLocalRotation
    camNode.setLocalRotation(newRotation)
    ghostCamNode.setLocalRotation(newRotation)
  }

  // updates the ghost camera, which is always along fixed cameraDirection from player model
  private def  moveGhostCameraOffset(value: Float): Unit = {
    val camOffset = ghostCamNode.getLocalTranslation subtract centerOfHeadLocal
    val d = (camOffset.length + value * Configuration.moveCameraSpeed) max 0 min Configuration.maxCamDistance
    val newDistance = if (d < Configuration.minCamDistance) Configuration.firstPersonCamDistance else d
    ghostCamNode.setLocalTranslation(centerOfHeadLocal add (cameraDirection mult newDistance))
  }
}
