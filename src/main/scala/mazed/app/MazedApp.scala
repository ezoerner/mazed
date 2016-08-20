package mazed.app

import com.jme3.app.SimpleApplication
import com.jme3.material.Material
import com.jme3.math.ColorRGBA.LightGray
import com.jme3.math.{ColorRGBA, Vector3f}
import com.jme3.scene.Geometry
import com.jme3.scene.shape.Box
import com.typesafe.config.ConfigFactory
import mazed.maze.WeightsFactory.makeWeights
import mazed.maze.{ChooseStrategyImpl, Maze}

import scala.util.Random

object MazedApp {

  def main(args: Array[String]): Unit = {
    val seed = if (args.size > 0) Some(args(0).toLong) else None
    val rand = seed.fold[Random](Random)(new Random(_))
    new MazedApp(rand).start()
  }
}

class MazedApp(rand: Random) extends SimpleApplication {
  val config = ConfigFactory.load()
  val configHelper = new ConfigHelper(config)
  val cellDim = configHelper.getVector3f("maze.cell")

  override def simpleInitApp(): Unit = {
    adjustCamera()
    addFloor()
    addMaze()
    initKeys()
  }

  private def addMaze(): Unit = {
    val wallThickness = config.getDouble("maze.wall.thickness").toFloat
    val wallColor = configHelper.getColor("maze.wall.color")

    val strategy = ChooseStrategyImpl(makeWeights(newest = 50, random = 50), rand)
    val maze = Maze.generate(
      config.getInt("maze.height"),
      config.getInt("maze.width"),
      strategy,
      rand)
    val bitSets = maze.manifestAsBitSets

    bitSets.zipWithIndex foreach { case (bitSet, i) ⇒
      i / 2 match {
        // even i, East/West walls
        case y if (i % 2) == 0 ⇒
          bitSet foreach { x ⇒
            val wall = new Box(cellDim.x / 2, cellDim.y / 2, wallThickness / 2)
            addBox(
              "wall",
              wall,
              wallColor,
              new Vector3f(wall.xExtent + x * cellDim.x, wall.yExtent, wall.zExtent + y * cellDim.z))
          }
        // odd i, North/South walls
        case y ⇒
          bitSet foreach { x ⇒
            val wall = new Box(wallThickness / 2, cellDim.y / 2, cellDim.z / 2)
            addBox(
              "wall",
              wall,
              wallColor,
              new Vector3f(wall.xExtent + x * cellDim.x, wall.yExtent, wall.zExtent + y * cellDim.z)
            )
          }
      }
    }
  }

  private def adjustCamera() = {
    val cameraHeight = config.getDouble("camera.height").toFloat
    val moveSpeed = config.getDouble("camera.moveSpeed").toFloat
    cam.setLocation(new Vector3f(-9f, cameraHeight, cellDim.z / 2))
    cam.lookAt(new Vector3f(0f, cameraHeight, cellDim.z / 2), Vector3f.UNIT_Y)
    flyCam.setMoveSpeed(moveSpeed)
  }

  private def addFloor(): Unit = {
    val floorDim = configHelper.getVector3f("maze.floor")
    val floor = new Box(floorDim.x / 2, floorDim.y / 2, floorDim.z / 2)
    addBox("Floor", floor, LightGray, new Vector3f(0, -floorDim.y / 2, 0))
  }

  private def addBox(name: String, box: Box, color: ColorRGBA, localTranslation: Vector3f = Vector3f.ZERO): Unit = {
    val geom: Geometry = new Geometry(name, box)
    val mat: Material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md")
    mat.setColor("Color", color)
    geom.setMaterial(mat)
    geom.setLocalTranslation(localTranslation)
    rootNode.attachChild(geom)
  }

  private def initKeys(): Unit = {}

}
