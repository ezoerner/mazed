package mazed.main

import com.jme3.app.SimpleApplication
import com.jme3.material.Material
import com.jme3.math.ColorRGBA.{Green, LightGray}
import com.jme3.math.{ColorRGBA, Quaternion, Vector3f}
import com.jme3.scene.Geometry
import com.jme3.scene.shape.Box
import mazed.maze.WeightsFactory.makeWeights
import mazed.maze.{ChooseStrategyImpl, Maze}

import scala.util.Random

object MazedApp {

  def main(args: Array[String]): Unit = {
    val seed = if (args.size > 0) Some(args(0).toLong) else None
    println(s"seed=$seed")
    val app = seed.fold(new MazedApp)(sd ⇒ new MazedApp(new Random(sd)))
    app.start()
  }
}

class MazedApp(rand: Random = Random) extends SimpleApplication {
  val FloorX = 200f
  val FloorThickness = 1f
  val FloorZ = 200f
  val CellY = 3.0f
  val CellX = 3.0f
  val CellZ = 3.0f
  val WallThickness = 0.1f
  val WallColor = Green mult 0.3f
  val MazeHeight = 20
  val MazeWidth = 20

  def setupCamera() = {
    cam.setLocation(new Vector3f(-9f, 2f, CellZ/2f))
    cam.setRotation(new Quaternion(3.457155E-4f, 0.70450205f, -3.4422107E-4f, 0.7097018f))
    flyCam.setMoveSpeed(20f)
  }

  override def simpleInitApp(): Unit = {
    setupCamera()

    val floor = new Box(FloorX/2, FloorThickness/2, FloorZ/2)
    addBox("Floor", floor, LightGray, new Vector3f(0, -FloorThickness/2, 0))

    val strategy = ChooseStrategyImpl(makeWeights(newest = 50, random = 50), rand)
    val maze = Maze.generate(MazeHeight, MazeWidth, strategy, rand)
    val bitSets = maze.manifestAsBitSets

    bitSets.zipWithIndex foreach { case (bitSet, i) ⇒
      println(s"i=$i; bitSet=$bitSet")
      i / 2 match {
          // even i, East/West walls
        case y if (i % 2) == 0 ⇒
          bitSet foreach { x ⇒
            val wall = new Box(CellX/2, CellY/2, WallThickness/2)
            addBox(
              "wall",
              wall,
              WallColor,
              new Vector3f(wall.xExtent + x * CellX, wall.yExtent, wall.zExtent + y * CellZ))
          }
          // odd i, North/South walls
        case y ⇒
          bitSet foreach { x ⇒
            val wall = new Box(WallThickness/2, CellY/2, CellZ/2)
            addBox(
              "wall",
              wall,
              WallColor,
              new Vector3f(wall.xExtent + x * CellX, wall.yExtent, wall.zExtent + y * CellZ)
            )
          }
      }
    }
  }

  def addBox(name: String, box: Box, color: ColorRGBA, localTranslation: Vector3f = Vector3f.ZERO): Unit = {
    val geom: Geometry = new Geometry(name, box)
    val mat: Material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md")
    mat.setColor("Color", color)
    geom.setMaterial(mat)
    geom.setLocalTranslation(localTranslation)
    rootNode.attachChild(geom)
  }
}
