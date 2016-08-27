package mazed.app

import com.jme3.math.FastMath._
import com.jme3.math.Quaternion

object Util {

  // (yaw/heading(y),roll/bank(z),pitch/attitude(x))
  def eulerAnglesDeg(q: Quaternion): (Float, Float, Float) = {
    val (x, y, z) = (0f, 0f, 0f)
    val floats = Array(x, y, z)
    q.toAngles(floats)
    (RAD_TO_DEG * floats(0), RAD_TO_DEG * floats(1), RAD_TO_DEG * floats(2))
  }
}
