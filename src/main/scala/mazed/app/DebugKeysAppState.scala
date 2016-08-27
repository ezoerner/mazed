package mazed.app

import java.lang

import com.jme3.app.Application
import com.jme3.app.state.{AbstractAppState, AppStateManager}
import com.jme3.input.KeyInput._
import com.jme3.input.controls.{ActionListener, KeyTrigger}
import com.jme3.math.{Quaternion, Vector3f}
import com.jme3.renderer.Camera
import com.jme3.util.BufferUtils
import com.typesafe.scalalogging.LazyLogging
import mazed.app.Util.eulerAnglesDeg

/** Registers a few keys that will dump debug information
  * to the console.
  */
object DebugKeysAppState {
  val InputMappingCameraPos: String = "SIMPLEAPP_CameraPos"
  val InputMappingMemory: String = "SIMPLEAPP_Memory"
}

class DebugKeysAppState() extends AbstractAppState with LazyLogging {
  import DebugKeysAppState._

  private var app: MazedApp = _
  private val keyListener = new DebugKeyListener

  private lazy val inputManager = app.getInputManager
  private lazy val camNode = app.camNode
  private lazy val sceneNode = app.sceneNode
  private lazy val characterNode = app.characterNode
  private lazy val player = app.player

  private def cameraOffset = app.cameraOffset
  private def playerFinalHeight = app.playerFinalHeight

  override def initialize(stateManager: AppStateManager, app: Application): Unit = {
    super.initialize(stateManager, app)
    this.app = app.asInstanceOf[MazedApp]
    inputManager.addMapping(InputMappingCameraPos, new KeyTrigger(KEY_C))
    inputManager.addMapping(InputMappingMemory, new KeyTrigger(KEY_M))
    inputManager.addListener(keyListener, InputMappingCameraPos, InputMappingMemory)
  }

  override def cleanup(): Unit = {
    super.cleanup()
    if (inputManager.hasMapping(InputMappingCameraPos)) {
      inputManager.deleteMapping(InputMappingCameraPos)
    }
    if (inputManager.hasMapping(InputMappingMemory)) {
      inputManager.deleteMapping(InputMappingMemory)
    }
    inputManager.removeListener(keyListener)
  }

  private class DebugKeyListener extends ActionListener {
    def onAction(name: String, value: Boolean, tpf: Float): Unit = {
      if (!value) return
      if (name == InputMappingCameraPos) {
        dumpSceneInfo()
      }
      else if (name == InputMappingMemory) {
        val stringBuilder = new lang.StringBuilder()
        BufferUtils.printCurrentDirectMemory(stringBuilder)
        logger.debug(stringBuilder.toString)
      }
    }
  }

  private def dumpSceneInfo(): Unit = {
    val cam: Camera = app.getCamera
    val loc: Vector3f = cam.getLocation
    val rot: Quaternion = cam.getRotation
    logger.debug(s"Camera Position: (${loc.x}, ${loc.y}, ${loc.z})")
    logger.debug(s"Camera Rotation: ${eulerAnglesDeg(rot)}")
    logger.debug(s"Camera Direction: ${cam.getDirection}")
    logger.debug(s"cam.setLocation(new Vector3f(${loc.x}f, ${loc.y}f, ${loc.z}f));")
    logger.debug(s"cam.setRotation(new Quaternion(${rot.getX}f, ${rot.getY}f, ${rot.getZ}f, ${rot.getW}f));")
    logger.debug("------------")
  }
}
