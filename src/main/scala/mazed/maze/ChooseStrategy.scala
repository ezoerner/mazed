package mazed.maze

import mazed.maze.WeightsFactory.makeWeights

import scala.annotation.tailrec
import scala.collection.SortedMap
import scala.util.Random

object RecursiveBacktracker extends ChooseStrategyImpl(makeWeights(newest = 1f))
object Prims extends ChooseStrategyImpl(makeWeights(random = 1))
object NewestRandom7525 extends ChooseStrategyImpl(makeWeights(newest = 75, random = 25))
object NewestRandom5050 extends ChooseStrategyImpl(makeWeights(newest = 50, random = 50))
object NewestRandom2575 extends ChooseStrategyImpl(makeWeights(newest = 25, random = 75))
object Oldest extends ChooseStrategyImpl(makeWeights(oldest = 1))
object NewestOldest5050 extends ChooseStrategyImpl(makeWeights(newest = 50, oldest = 50))
object OldestRandom5050 extends ChooseStrategyImpl(makeWeights(oldest = 50, random = 50))

trait ChooseStrategy {
  def weights: SortedMap[Symbol, Float]

  /** Choose the next index < ceil, where 0 represents the oldest cell and
    * ceil - 1 represents the newest.
    */
  def nextIndex(ceil: Int): Int = pick(Random.nextFloat() * weights.values.sum, weights) match {
    case 'random ⇒ Random.nextInt(ceil)
    case 'newest ⇒ ceil - 1
    case 'middle ⇒ ceil / 2
    case 'oldest ⇒ 0
  }


  @tailrec
  private def pick(lookup: Float, wmap: SortedMap[Symbol, Float]): Symbol = {
    val (firstSymbol, firstWeight) = wmap.head
    if (lookup < firstWeight) {
      firstSymbol
    }
    else {
      val nextLookup = lookup - firstWeight
      pick(nextLookup, wmap - firstSymbol)
    }
  }
}

case class ChooseStrategyImpl(override val weights: SortedMap[Symbol, Float]) extends ChooseStrategy

private object WeightsFactory {
  trait SymbolOrdering extends Ordering[Symbol] {
    def compare(x: Symbol, y: Symbol) = x.name.compareTo(y.name)
  }
  implicit object SymbolOrd extends SymbolOrdering

  def makeWeights(
      random: Float = 0f,
      newest: Float = 0f,
      middle: Float = 0f,
      oldest: Float = 0f): SortedMap[Symbol, Float] = {
    SortedMap('random → random, 'newest → newest, 'middle → middle, 'oldest → oldest)
  }
}


