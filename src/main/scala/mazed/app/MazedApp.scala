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

import scala.collection.BitSet
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
          addEastWestWalls(mazeNode, wallThickness, bitSets, bitSet, i, y)
        // odd i, North/South walls
        case y ⇒
          bitSet foreach { x ⇒
            addNorthSouthWalls(mazeNode, wallThickness, bitSets, i, y, x)
          }
      }
    }
    rootNode.attachChild(mazeNode)
  }


  private def addEastWestWalls(
      mazeNode: Node,
      wallThickness: Float,
      bitSets: IndexedSeq[BitSet],
      thisBitSet: BitSet,
      i: Int,
      y: Int): Unit = {
    thisBitSet foreach { x ⇒
      val maybeRowNorth = if (i == 0) None else Some(bitSets(i - 1))
      val maybeRowSouth = if (i == bitSets.size - 1) None else Some(bitSets(i + 1))

      // shorten to WEST if forms a tee with two north/south walls
      val buttWest: Boolean = maybeRowNorth.exists { rowNorth ⇒
        maybeRowSouth.exists { rowSouth ⇒
          !(thisBitSet contains x - 1) && (rowNorth contains x) && (rowSouth contains x)
        }
      }

      val buttEast: Boolean = maybeRowNorth.exists { rowNorth ⇒
        maybeRowSouth.exists { rowSouth ⇒
          !(thisBitSet contains x + 1) && (rowNorth contains x + 1) && (rowSouth contains x + 1)
        }
      }

      // lengthen to east if there is no east/west wall to the east unless there is a tee to the east
      val wallEast: Boolean = thisBitSet contains x + 1
      val lengthenToEast: Boolean = !wallEast && !buttEast

      val baseBox = new Box(cellDim.x / 2, cellDim.y / 2, wallThickness / 2)

      // wall lengthened with no extra translation if no east-west wall to the east and no tee to east
      val wall1 = if (lengthenToEast) new Box(baseBox.xExtent + wallThickness / 2, baseBox.yExtent, baseBox.zExtent)
                  else baseBox
      val wall1Trans = new Vector3f(wall1.xExtent + x * cellDim.x, wall1.yExtent, wall1.zExtent + y * cellDim.z)

      // wall possibly also shortened to the west with extra x translation if forms a tee to west
      val wall2 = if (buttWest) new Box(wall1.xExtent - wallThickness / 2, wall1.yExtent, wall1.zExtent)
                  else wall1
      val wall2Trans = if (buttWest) new Vector3f(wall2.xExtent + x * cellDim.x + wallThickness, wall2.yExtent, wall2.zExtent + y * cellDim.z)
                       else wall1Trans

      addWall(wall2, wall2Trans, mazeNode)
    }
  }

  private def addNorthSouthWalls(
      mazeNode: Node,
      wallThickness: Float,
      bitSets: IndexedSeq[BitSet],
      i: Int,
      y: Int,
      x: Int): Unit = {
    // if there is an EastWest wall to the North, then butt against it (could be to north-east OR north-west)
    // ... unless it forms a tee coming from the east or west
    val rowNorth = bitSets(i - 1)
    val possibleTee = i > 1 && (bitSets(i - 2) contains x)
    val teeFromEast: Boolean = (rowNorth contains x) && !(rowNorth contains x - 1) && possibleTee
    val teeFromWest: Boolean = (rowNorth contains x - 1) && !(rowNorth contains x) && possibleTee
    val buttNorth = ((rowNorth contains x) || (rowNorth contains x - 1)) && !teeFromEast && !teeFromWest

    val baseBox = new Box(wallThickness / 2, cellDim.y / 2, cellDim.z / 2)

    val wall = if (buttNorth) new Box(baseBox.xExtent, baseBox.yExtent, baseBox.zExtent - wallThickness / 2)
               else baseBox

    val baseTrans = new Vector3f(wall.xExtent + x * cellDim.x, wall.yExtent, wall.zExtent + y * cellDim.z)
    // if butt north, then extra z-translation
    val translation = if (buttNorth) new Vector3f(baseTrans.x, baseTrans.y, baseTrans.z + wallThickness)
                      else baseTrans
    addWall(wall, translation, mazeNode)
  }


  private def addWall(box: Box, translation: Vector3f, node: Node) = {
    addBox(
      "wall",
      box,
      "maze.wall",
      translation = translation,
      node = node)
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
    addBox("Floor", floor, "maze.floor", translation)
  }

  private def addBox(
      name: String,
      box: Box,
      configPath: String,
      translation: Vector3f,
      node: Node = rootNode): Unit = {

    val geom: Geometry = new Geometry(name, box)
    val mat: Material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md")
    val cfg = new ConfigHelper(config.getConfig(configPath))
    cfg.getOptionalVector2f("textureScale") foreach { v2 ⇒
      box.scaleTextureCoordinates(v2)
    }
    cfg.getOptionalString("texture") foreach { t ⇒
      val tex = assetManager.loadTexture(t)
      tex.setWrap(WrapMode.Repeat)
      mat.setTexture("ColorMap", tex)
    }
    cfg.getOptionalColor("color") foreach (c ⇒ mat.setColor("Color", c))
    geom.setMaterial(mat)
    geom.setLocalTranslation(translation)
    node.attachChild(geom)
  }
}
