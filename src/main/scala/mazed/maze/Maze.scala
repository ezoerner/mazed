package mazed.maze

import mazed.maze.Dir._
import mazed.maze.WeightsFactory.makeWeights

import scala.annotation.tailrec
import scala.collection.BitSet
import scala.util.Random

private object Dir {
  val Unknown = Dir(0x00)
  val North = Dir(0x01)
  val South = Dir(0x02)
  val East = Dir(0x04)
  val West = Dir(0x08)
}

/** A Dir indicates a direction that is open */
case class Dir(asInt: Int) extends AnyVal {

  def +(that: Dir): Dir = Dir(this.asInt | that.asInt)
  def unary_- = this match {
    case East ⇒ West
    case West ⇒ East
    case North ⇒ South
    case South ⇒ North
    case d ⇒ sys.error(s"unsupported direction in unary - : $d")
  }

  def includes(dir: Dir): Boolean = (this.asInt & dir.asInt) != 0
}

case class Cell(asXY: (Int, Int)) extends AnyVal {
  def x = asXY._1
  def y = asXY._2
  def step(dir: Dir): Cell = dir match {
    case East ⇒ Cell((x + 1, y))
    case West ⇒ Cell((x - 1, y))
    case North ⇒ Cell((x, y - 1))
    case South ⇒ Cell((x, y + 1))
    case d ⇒ sys.error("unsupported direction in step: $d")
  }
}

object Maze {

  def generate(
    height: Int,
    width: Int,
    strategy: ChooseStrategy = NewestRandom5050,
    rand: Random = Random): Maze = {

      implicit class IndexedSeqOps[A](seq: IndexedSeq[A]) {
        def removeAt(index: Int): IndexedSeq[A] = (seq take index) ++ (seq takeRight (seq.size - index - 1))
      }

      @tailrec
      def go(active: IndexedSeq[Cell], grid: Maze): Maze = {
        if (active.isEmpty) {
          grid
        }
        else {
          val index = strategy.nextIndex(active.size)
          val cell = active(index)

          val maybeUnknownDir: Option[Dir] = rand shuffle List(North, South, East, West) find { dir ⇒
            val neighbor = cell step dir
            (grid inBounds neighbor) && grid(neighbor) == Unknown
          }

          // use of pattern matching here instead of fold or map/getOrElse provides tail-recursion
          maybeUnknownDir match {
            case None ⇒ go(active removeAt index, grid)
            case Some(dir) ⇒
              val neighbor = cell step dir
              val dirHere = grid(cell)
              val hereUpdated = grid.updated(cell, dirHere + dir)
              val neighborUpdated = hereUpdated.updated(neighbor, -dir)
              go(active :+ neighbor, neighborUpdated)
          }
        }
      }

    val randomCell = Cell((rand nextInt width, rand nextInt height))
    val active = IndexedSeq(randomCell)
    val grid = Maze(Vector.fill[Dir](height, width)(Unknown))
    val maze = go(active, grid)
    // fix up entrance and exit
    val entrance = Cell((0,0))
    val exit = Cell((width - 1, height - 1))
    maze.updated(entrance, maze(entrance) + West).updated(exit, maze(exit) + East)
  }

  def main(args: Array[String]): Unit = {
    val seed = if (args.size > 2) Some(args(2).toLong) else None
    println(s"seed=$seed")
    val rand = seed.fold[Random](Random)(new Random(_))
    val strategy = ChooseStrategyImpl(makeWeights(newest = 50, random = 50), rand)
    val maze = generate(args(0).toInt, args(1).toInt, strategy, rand)
    println(maze.manifestAsString)
    maze.manifestAsBitSets.zipWithIndex foreach { case (bitSet, i) ⇒ println(s"i=$i; bitSet=$bitSet")}
  }
}

/** Each cell in a Grid represents an intersection of any combination
  * of the four directions.
  */
case class Maze(asVector: Vector[Vector[Dir]]) extends AnyVal {
  def width = asVector(0).size
  def height = asVector.size
  def entrance = Cell((0, 0))
  def exit = Cell((width - 1, height - 1))

  private def apply(cell: Cell): Dir = try asVector(cell.y)(cell.x) catch {
    case e: IndexOutOfBoundsException ⇒ throw new RuntimeException(s"${cell.x},${cell.y}", e)
  }
  private def updated(cell: Cell, dir: Dir): Maze = Maze(asVector.updated(cell.y, asVector(cell.y).updated(cell.x, dir)))
  private def inBounds(cell: Cell): Boolean = xInBounds(cell.x) && yInBounds(cell.y)
  private def yInBounds(y: Int): Boolean = y >= 0 && y < height
  private def xInBounds(x: Int): Boolean = x >= 0 && x < width

  /**
    * Each row is represented as a BitSet containing the x-values where a WALL is present.
    *
    * Even-indexed rows have horizontal walls (East-West). The range is 0 through width - 1 for these rows,
    * representing walls above (North) of the cells.
    *
    * Odd-indexed rows have vertical walls (North-South). The range is 0 through width for these rows,
    * representing the walls to the left (West) of the cells and including the right border.
    *
    * There are (height * 2 + 1) BitSets in the List (the "extra" row representing the bottom border)
    * the index being the possible wall to the North of a cell.
    *
    * This representation can be used to draw the maze in 2D or 3D.
    */
  def manifestAsBitSets: List[BitSet] = {
    // the top and bottom borders include horizontal walls at every x value
    val border = BitSet(0 until width: _*)

    (0 to height * 2).toList map { i ⇒
      i / 2 match {
        case _ if i == height * 2 || i == 0 ⇒ border
        case y if i % 2 == 0 ⇒
          val indexes = (0 until width) filterNot (x ⇒ this(Cell((x, y))) includes North)
          BitSet(indexes: _*)
        case y ⇒
          val indexes = (0 to width) filterNot {
            case x if x == width ⇒
              this(Cell((x, y)) step West) includes East
            case x ⇒
              this(Cell((x, y))) includes West
          }
          BitSet(indexes: _*)
      }
    }
  }

  def manifestAsCompactString: String = {

      def char2(dirHere: Dir, cellRight: Cell): String = {
        if (!(dirHere includes East)) "|"
        else if ((dirHere includes South) || (this (cellRight) includes South)) " " else "_"
      }

      def char1(dirHere: Dir): String = {
        if (dirHere includes South) " " else "_"
      }

    val builder = new StringBuilder()
    builder.append(" ")
    builder.append(List.fill(width * 2 - 1)('_').mkString)
    builder.append("\n")
    asVector.zipWithIndex foreach {
      case (row, y) ⇒
        builder.append("|")
        row.zipWithIndex foreach {
          case (dirHere: Dir, x) ⇒
            val cellRight = Cell((x, y)) step East
            builder.append(char1(dirHere))
            builder.append(char2(dirHere, cellRight))
        }
        builder.append("\n")
    }
    builder.toString
  }

  def manifestAsString: String = {
    gridAsStrings.mkString("\n")
  }

  private def gridAsStrings: List[String] = {
    (0 to height).toList flatMap rowAsStrings
  }

  // can be called with y from 0 to height inclusive to get a bottom border
  private def rowAsStrings(y: Int): List[String] = {
    val row = (0 until width).toList map (x => cellAsStrings(Cell((x, y))))
    val rightBorder = if (y >= exit.y) " " else "|"
    val newRow = row :+ List("+", rightBorder)
    // convert list of strings of size 2 into 2 lists of strings
    val listOf2Strings = newRow.transpose map (_.mkString)
    // Bottom row has all spaces so trim
    if (y == height) listOf2Strings take 1
    else listOf2Strings
  }

  private def cellAsStrings(cell: Cell): List[String] =
    // normalize to two Strings to allow transpose to work
    if (cell.y == height) List("+--", "   ")
    else List(
      if (this(cell) includes North) "+  " else "+--",
      if ((this(cell) includes West) || cell == entrance) "   " else "|  ")
}
