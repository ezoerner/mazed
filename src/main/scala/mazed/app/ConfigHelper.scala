package mazed.app

import com.jme3.math.{ColorRGBA, Vector3f}
import com.typesafe.config.Config

class ConfigHelper(config: Config) {

  def getVector3f(path: String): Vector3f = {
    val subConfig = config.getConfig(path)
    new Vector3f(
      subConfig.getDouble("x").toFloat,
      subConfig.getDouble("y").toFloat,
      subConfig.getDouble("z").toFloat)
  }

  def getColor(path: String): ColorRGBA = {
    val subConfig = config.getConfig(path)
    new ColorRGBA(
      subConfig.getDouble("r").toFloat,
      subConfig.getDouble("g").toFloat,
      subConfig.getDouble("b").toFloat,
      subConfig.getDouble("a").toFloat)
  }
}
