package mazed.state

import com.jme3.app.Application
import com.jme3.app.state.{AbstractAppState, AppStateManager}
import com.jme3.scene.CameraNode

class LockedCamState(camNode: CameraNode) extends AbstractAppState {

  override def initialize(stateManager: AppStateManager, app: Application): Unit = {
    super.initialize(stateManager, app)
  }

  override def update(tpf: Float): Unit = super.update(tpf)

  override def setEnabled(enabled: Boolean): Unit = {
    super.setEnabled(enabled)
    camNode.setEnabled(enabled)
  }

  override def cleanup(): Unit = super.cleanup()
}
