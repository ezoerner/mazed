package mazed.graph.model

import scala.util.Random

trait ChooseStrategy {
  /** Choose the next index < ceil, where 0 represents the oldest cell and
    * ceil - 1 represents the newest.
    */
  def nextIndex(ceil: Int): Int
}

case class ChooseStrategy(randomWeight: Float, newestWeight: Float, middleWeight: Float, oldestWeight: Float)
  extends ChooseStrategy {

  override def nextIndex(ceil: Int): Int = {
    val totalWeight = randomWeight + newestWeight + middleWeight + oldestWeight
    val rnd = Random.nextFloat() * totalWeight

  }
}
