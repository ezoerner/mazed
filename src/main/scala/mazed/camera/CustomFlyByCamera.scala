package mazed.camera

import java.util.concurrent.TimeUnit.{NANOSECONDS, SECONDS}

import com.jme3.input.controls.KeyTrigger
import com.jme3.input.{FlyByCamera, InputManager, KeyInput}
import com.jme3.math.Vector3f
import com.jme3.renderer.Camera

class CustomFlyByCamera(c: Camera) extends FlyByCamera(c) {
  val doublePressNanos = NANOSECONDS.convert(1, SECONDS)
  var flyingEnabled = false
  var lastSpaceNanos = 0L
  moveSpeed = 0.2f

  override def registerWithInput(inputManager: InputManager): Unit = {
    super.registerWithInput(inputManager)
    inputManager.deleteTrigger("FLYCAM_Rise", new KeyTrigger(KeyInput.KEY_Q))
    inputManager.deleteTrigger("FLYCAM_Lower", new KeyTrigger(KeyInput.KEY_Z))

    inputManager.deleteMapping("FLYCAM_ZoomIn")
    inputManager.deleteMapping("FLYCAM_ZoomOut")

    inputManager.addMapping("FLYCAM_Rise", new KeyTrigger(KeyInput.KEY_SPACE))
    inputManager.addMapping("FLYCAM_Lower", new KeyTrigger(KeyInput.KEY_LSHIFT))
  }

  override def moveCamera(value: Float, sideways: Boolean): Unit = {
    val vel: Vector3f = new Vector3f
    val pos: Vector3f = cam.getLocation.clone

    if (sideways) cam.getLeft(vel)
    else cam.getDirection(vel)

    // disallow vertical (y) movement with moveCamera operation
    // and use full x/y magnitude
    vel.setY(0f)
    vel.normalizeLocal()
    vel.multLocal(value * moveSpeed)

    if (motionAllowed != null) motionAllowed.checkMotionAllowed(pos, vel)
    else pos.addLocal(vel)

    cam.setLocation(pos)
  }

  override def riseCamera(value: Float): Unit = {
    val vel: Vector3f = new Vector3f(0, value * moveSpeed, 0)
    val pos: Vector3f = cam.getLocation.clone

    if (motionAllowed != null) motionAllowed.checkMotionAllowed(pos, vel)
    else pos.addLocal(vel)

    // TODO replace this constraint with collision detection to prevent moving through floor
    pos.setY(pos.y max 2f)

    cam.setLocation(pos)
  }


  override def onAnalog(name: String, value: Float, tpf: Float): Unit = {
    if (name == "FLYCAM_Rise") {
      if (flyingEnabled) {
        super.onAnalog(name, value, tpf)
      }
    }
    else {
      super.onAnalog(name, value, tpf)
    }
  }

  override def onAction(name: String, isPressed: Boolean, tpf: Float): Unit = {
    super.onAction(name, isPressed, tpf)
    if (enabled) {
      // if space bar is double-pressed then toggle flying
      if (name == "FLYCAM_Rise" && !isPressed) {
        val now = System.nanoTime
        if (now - lastSpaceNanos < doublePressNanos) {
          flyingEnabled = !flyingEnabled
          println(s"flying enabled = $flyingEnabled")
          lastSpaceNanos = 0L
        } else {
          lastSpaceNanos = now
        }
      }
    }
  }
}
