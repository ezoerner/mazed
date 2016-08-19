package mazed.graph.model

import mazed.graph.model.Dir._

import scala.util.Random

private object Dir {
  val Unknown = Dir(0x00)
  val North = Dir(0x01)
  val South = Dir(0x02)
  val East = Dir(0x04)
  val West = Dir(0x08)
}

private case class Dir(asInt: Int) extends AnyVal {

  def +(that: Dir): Dir = Dir(this.asInt | that.asInt)
  def -(that: Dir): Dir = this + (-that)
  def unary_- = this match {
    case East ⇒ West
    case West ⇒ East
    case North ⇒ South
    case South ⇒ North
  }

  def includes(dir: Dir): Boolean = (this.asInt & dir.asInt) != 0
}

private case class Cell(asXY: (Int, Int)) extends AnyVal {
  def x = asXY._1
  def y = asXY._2
  def step(dir: Dir): Cell = dir match {
    case East ⇒ Cell((x + 1, y))
    case West ⇒ Cell((x - 1, y))
    case North ⇒ Cell((x + 1, y - 1))
    case South ⇒ Cell((x + 1, y + 1))
  }
}


object Maze {


  def generate(height: Int, width: Int, strategy: ChooseStrategy): Maze = {

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

          val maybeUnknownDir: Option[Dir] = Random.shuffle(List(North, South, East, West)).find { dir ⇒
            val neighbor = cell step dir
            (grid inBounds neighbor) && grid(neighbor.x, neighbor.y) == Unknown
          }

          maybeUnknownDir.fold[Maze](go(active removeAt index, grid)) { dir ⇒
            val neighbor = cell step dir
            val dirHere = grid(cell.x, cell.y)
            val hereUpdated = grid.updated(cell.x, cell.y, dirHere + dir)
            val neighborUpdated = hereUpdated.updated(neighbor.x, neighbor.y, -dir)
            go(active :+ neighbor, neighborUpdated)
          }
        }
      }

    val randomCell = Cell((Random.nextInt(width), Random.nextInt(height)))
    val active = IndexedSeq(randomCell)
    val grid = Maze(Vector.fill[Dir](height, width)(Unknown))
    go(active, grid)
  }
}

/** Each cell in a Grid represents an intersection of any combination
  * of the four directions.
  */
case class Maze(asVector: Vector[Vector[Dir]]) extends AnyVal {
  def apply(x: Int, y: Int): Dir = asVector(y)(x)
  def updated(x: Int, y: Int, dir: Dir): Maze = Maze(asVector.updated(y, asVector(y).updated(x, dir)))
  def width = asVector(0).size
  def height = asVector.size
  def inBounds(cell: Cell): Boolean = cell.x >= 0 && cell.y >= 0 && cell.x < width && cell.y < height
}
