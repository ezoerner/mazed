package mazed.camera
import com.jme3.app.Application
import com.jme3.app.state.{AbstractAppState, AppStateManager}
import com.jme3.input.FlyByCamera

class CustomFlyCamAppState(flyCam: FlyByCamera) extends AbstractAppState {
  private var app: Application = _

  def getCamera: FlyByCamera = flyCam

  override def initialize(stateManager: AppStateManager, app: Application): Unit = {
    super.initialize(stateManager, app)
    this.app = app
    if (app.getInputManager != null) {
      flyCam.registerWithInput(app.getInputManager)
    }
  }

  override def setEnabled(enabled: Boolean): Unit = {
    super.setEnabled(enabled)
    flyCam.setEnabled(enabled)
  }

  override def cleanup(): Unit = {
    super.cleanup()
    if (app.getInputManager != null) flyCam.unregisterInput()
  }
}
