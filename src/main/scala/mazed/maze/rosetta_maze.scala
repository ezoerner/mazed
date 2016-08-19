package mazed.maze

import mazed.maze.MazeTypes._

import scala.collection.immutable.List
import scala.util.Random

/**
  * Code cleaned up from [[http://rosettacode.org/wiki/Maze_generation]].
  * Uses Recursive Backtracking algorithm, representing the maze as a
  * Grid with a Set of Doors
  */
object MazeTypes {
  case class Direction(dx: Int, dy: Int)

  case class Loc(x: Int, y: Int) {
    def +(that: Direction): Loc = Loc(x + that.dx, y + that.dy)
  }

  case class Door(from: Loc, to: Loc)

  val North = Direction(0,-1)
  val South = Direction(0,1)
  val West = Direction(-1,0)
  val East = Direction(1,0)
  val directions = Set(North, South, West, East)
}

object MazeBuilder {

  def shuffle[T](set: Set[T]): List[T] = Random.shuffle(set.toList)

  def buildImpl(current: Loc, grid: Grid): Grid = {
    var newgrid = grid.markVisited(current)
    val nbors = shuffle(grid.neighbors(current))
    nbors.foreach { n =>
      if (!newgrid.isVisited(n)) {
        newgrid = buildImpl(n, newgrid.markVisited(current).addDoor(Door(current, n)))
      }
    }
    newgrid
  }

  def build(width: Int, height: Int): Grid = {
    val exit = Loc(width-1, height-1)
    buildImpl(exit, new Grid(width, height, Set(), Set()))
  }

  def main(args: Array[String]): Unit = {
    val grid = MazeBuilder.build(args(0).toInt, args(1).toInt)
    print(grid.printGrid().mkString("\n"))
  }
}

class Grid(val width: Int, val height: Int, val doors: Set[Door], val visited: Set[Loc]) {

  def addDoor(door: Door): Grid =
    new Grid(width, height, doors + door, visited)

  def markVisited(loc: Loc): Grid =
    new Grid(width, height, doors, visited + loc)

  def isVisited(loc: Loc): Boolean =
    visited.contains(loc)

  def neighbors(current: Loc): Set[Loc] =
    directions.map(current + _).filter(inBounds) -- visited

  def printGrid(): List[String] = {
    (0 to height).toList.flatMap(printRow)
  }

  private def inBounds(loc: Loc): Boolean =
    loc.x >= 0 && loc.x < width && loc.y >= 0 && loc.y < height

  private def printRow(y: Int): List[String] = {
    val row = (0 until width).toList.map(x => printCell(Loc(x, y)))
    val rightSide = if (y >= height - 1) " " else "|"
    val newRow = row :+ List("+", rightSide)
    // convert list of strings of size 2 into 2 lists of strings
    val listOf2Strings = newRow.transpose.map(_.mkString)
    // Bottom row has all spaces so trim
    if (y == height) listOf2Strings take 1
    else listOf2Strings
  }

  private val entrance = Loc(0,0)

  private def printCell(loc: Loc): List[String] = {
    if (loc.y == height)
      List("+--", "   ")
    else List(
      if (openNorth(loc)) "+  " else "+--",
      if (openWest(loc) || loc == entrance) "   " else "|  ")
  }

  def openNorth(loc: Loc): Boolean = openInDirection(loc, North)

  def openWest(loc: Loc): Boolean = openInDirection(loc, West)

  private def openInDirection(loc: Loc, dir: Direction): Boolean =
    doors.contains(Door(loc, loc + dir)) || doors.contains(Door(loc + dir, loc))
}
