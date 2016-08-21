package mazed.app

import com.jme3.app.{DebugKeysAppState, SimpleApplication, StatsAppState}
import com.jme3.material.Material
import com.jme3.math.Vector3f
import com.jme3.scene.shape.Box
import com.jme3.scene.{Geometry, Node}
import com.jme3.texture.Texture.WrapMode
import com.jme3.util.SkyFactory
import com.typesafe.config.ConfigFactory
import mazed.camera.{CustomFlyByCamera, CustomFlyCamAppState}
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

class MazedApp(rand: Random) extends SimpleApplication(new StatsAppState, new DebugKeysAppState) {
  val config = ConfigFactory.load()
  val configHelper = new ConfigHelper(config)
  val cellDim = configHelper.getVector3f("maze.cell")

  def addSky() = {
    rootNode.attachChild(SkyFactory.createSky(
      assetManager, "Textures/Sky/Bright/BrightSky.dds", false))
  }

  override def simpleInitApp(): Unit = {
    initCamera()
    addFloor()
    addSky()
    addMaze()
  }

  // adds maze with upper left corner at origin
  private def addMaze(): Unit = {
    val mazeNode = new Node()

      def addWall(box: Box, translation: Vector3f) = {
        addBox(
          "wall",
          box,
          "maze.wall",
          translation = translation,
          node = mazeNode)
      }

    val wallThickness = config.getDouble("maze.wall.thickness").toFloat

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
            addWall(wall, new Vector3f(wall.xExtent + x * cellDim.x, wall.yExtent, wall.zExtent + y * cellDim.z))
          }
        // odd i, North/South walls
        case y ⇒
          bitSet foreach { x ⇒
            val wall = new Box(wallThickness / 2, cellDim.y / 2, cellDim.z / 2)
            addWall(wall, new Vector3f(wall.xExtent + x * cellDim.x, wall.yExtent, wall.zExtent + y * cellDim.z))
          }
      }
    }
    rootNode.attachChild(mazeNode)
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

  private def addFloor(): Unit = {
    val margin = config.getDouble("maze.floor.margin").toFloat
    val floorDim = configHelper.getVector3f("maze.floor.size")
    val floor = new Box(floorDim.x / 2, floorDim.y / 2, floorDim.z / 2)
    val translation = new Vector3f(floorDim.x / 2 - margin, -floorDim.y / 2, floorDim.z / 2 - margin)
    addBox("Floor", floor, "maze.floor", translation = translation)
  }

  private def addBox(
      name: String,
      box: Box,
      configPath: String,
      translation: Vector3f = Vector3f.ZERO,
      node: Node = rootNode): Unit = {

    val geom: Geometry = new Geometry(name, box)
    val mat: Material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md")
    val cfg = new ConfigHelper(config.getConfig(configPath))
    cfg.getOptionalVector2f("textureScale") foreach { v2 ⇒
      box.scaleTextureCoordinates(v2)
    }
    cfg.getOptionalString("texture") foreach { t ⇒
      val tex = assetManager.loadTexture(t)
      tex.setWrap(WrapMode.MirroredRepeat)
      mat.setTexture("ColorMap", tex)
    }
    cfg.getOptionalColor("color") foreach (c ⇒ mat.setColor("Color", c))
    geom.setMaterial(mat)
    geom.setLocalTranslation(translation)
    node.attachChild(geom)
  }
}
