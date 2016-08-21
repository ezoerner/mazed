package mazed.app

import com.jme3.app.{DebugKeysAppState, SimpleApplication, StatsAppState}
import com.jme3.math.Vector3f
import com.jme3.util.SkyFactory
import com.typesafe.config.ConfigFactory
import mazed.camera.{CustomFlyByCamera, CustomFlyCamAppState}

import scala.util.Random

object MazedApp {

  def main(args: Array[String]): Unit = {
    val seed = if (args.size > 0) Some(args(0).toLong) else None
    val rand = seed.fold[Random](Random)(new Random(_))
    new MazedApp(rand).start()
  }
}

class MazedApp(rand: Random) extends SimpleApplication(new StatsAppState, new DebugKeysAppState) {
  val config = ConfigFactory.load()
  val configHelper = new ConfigHelper(config)
  val cellDim = configHelper.getVector3f("maze.cell")


  override def simpleInitApp(): Unit = {
    initCamera()
    addSky()
    val mazeSceneBuilder = new MazeSceneBuilder(config, assetManager)
    rootNode.attachChild(mazeSceneBuilder.build)
  }

  private def addSky() = {
    rootNode.attachChild(SkyFactory.createSky(
      assetManager, "Textures/Sky/Bright/BrightSky.dds", false))
  }

  private def initCamera() = {
    flyCam = new CustomFlyByCamera(cam)
    stateManager.attach(new CustomFlyCamAppState(flyCam))

    val cameraHeight = config.getDouble("camera.height").toFloat
    val moveSpeed = config.getDouble("camera.moveSpeed").toFloat
    cam.setLocation(new Vector3f(-9f, cameraHeight, cellDim.z / 2))
    cam.lookAt(new Vector3f(0f, cameraHeight, cellDim.z / 2), Vector3f.UNIT_Y)
    flyCam.setMoveSpeed(moveSpeed)
  }
}
