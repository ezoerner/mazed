package mazed.maze

import mazed.maze.Dir._

import scala.util.Random

private object Dir {
  val Unknown = Dir(0x00)
  val North = Dir(0x01)
  val South = Dir(0x02)
  val East = Dir(0x04)
  val West = Dir(0x08)
}

case class Dir(asInt: Int) extends AnyVal {

  def +(that: Dir): Dir = Dir(this.asInt | that.asInt)
  def -(that: Dir): Dir = this + (-that)
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

  def generate(height: Int, width: Int, strategy: ChooseStrategy = NewestRandom5050): Maze = {

      implicit class IndexedSeqOps[A](seq: IndexedSeq[A]) {
        def removeAt(index: Int): IndexedSeq[A] = (seq take index) ++ (seq takeRight (seq.size - index - 1))
      }

      def go(active: IndexedSeq[Cell], grid: Maze): Maze = {
        if (active.isEmpty) {
          grid
        }
        else {
          val index = strategy.nextIndex(active.size)
          val cell = active(index)

          val maybeUnknownDir: Option[Dir] = Random shuffle List(North, South, East, West) find { dir ⇒
            val neighbor = cell step dir
            (grid inBounds neighbor) && grid(neighbor) == Unknown
          }

          maybeUnknownDir.fold[Maze](go(active removeAt index, grid)) { dir ⇒
            val neighbor = cell step dir
            val dirHere = grid(cell)
            val hereUpdated = grid.updated(cell, dirHere + dir)
            val neighborUpdated = hereUpdated.updated(neighbor, -dir)
            go(active :+ neighbor, neighborUpdated)
          }
        }
      }

    val randomCell = Cell((Random nextInt width, Random nextInt height))
    val active = IndexedSeq(randomCell)
    val grid = Maze(Vector.fill[Dir](height, width)(Unknown))
    go(active, grid)
  }

  def main(args: Array[String]): Unit = {
    val maze = generate(args(0).toInt, args(1).toInt)
    println(maze.manifestAsString)
  }
}

/** Each cell in a Grid represents an intersection of any combination
  * of the four directions.
  */
case class Maze(asVector: Vector[Vector[Dir]]) extends AnyVal {
  def apply(cell: Cell): Dir = try asVector(cell.y)(cell.x) catch {
    case e: IndexOutOfBoundsException ⇒ throw new RuntimeException(s"${cell.x},${cell.y}", e)
  }
  def updated(cell: Cell, dir: Dir): Maze = Maze(asVector.updated(cell.y, asVector(cell.y).updated(cell.x, dir)))

  def width = asVector(0).size

  def height = asVector.size

  def inBounds(cell: Cell): Boolean = xInBounds(cell.x) && yInBounds(cell.y)
  def yInBounds(y: Int): Boolean = y >= 0 && y < height
  def xInBounds(x: Int): Boolean = x >= 0 && x < width

  def manifestAsString: String = {

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

  def manifestAsStringWithPossibleUnknowns: String = {
    val builder = new StringBuilder()
    builder.append(" ")
    builder.append(List.fill(width * 2 - 1)('_').mkString)
    builder.append("\n")
    asVector.zipWithIndex foreach {
      case (row, y) ⇒
        builder.append("|")
        row.zipWithIndex foreach {
          case (dirHere: Dir, x) ⇒
            val cellHere = Cell((x, y))
            val cellBelow = cellHere step South
            val cellRight = cellHere step East
            val cellBelowRight = cellBelow step East
            val noFloor1 = (dirHere == Unknown && yInBounds(cellBelow.y) && apply(cellBelow) == Unknown) ||
                           (dirHere includes South)
            builder.append(if (noFloor1) " " else "_")

            if (dirHere == Unknown && xInBounds(cellRight.x) && this(cellRight) == Unknown) {
              val noFloor2 = yInBounds(cellBelow.y) && (this(cellBelow) == Unknown || this(cellBelowRight) == Unknown)
              builder.append(if (noFloor2) " " else "_")
            }
            else if (dirHere includes East) {
              val noFloor2 = (dirHere + this(cellRight)) includes South
              builder.append(if (noFloor2) " " else "_")
            }
            else {
              builder.append("|")
            }
        }
        builder.append("\n")
    }
    builder.toString
  }
}
