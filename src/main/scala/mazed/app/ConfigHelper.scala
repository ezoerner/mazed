package mazed.app

import com.jme3.math.{ColorRGBA, Vector2f, Vector3f}
import com.typesafe.config.Config

class ConfigHelper(config: Config) {

  def getVector3f(path: String): Vector3f = {
    val subConfig = config.getConfig(path)
    new Vector3f(
      subConfig.getDouble("x").toFloat,
      subConfig.getDouble("y").toFloat,
      subConfig.getDouble("z").toFloat)
  }

  def getVector2f(path: String): Vector2f = {
    val subConfig = config.getConfig(path)
    new Vector2f(
      subConfig.getDouble("x").toFloat,
      subConfig.getDouble("y").toFloat)
  }

  def getColor(path: String): ColorRGBA = {
    val subConfig = config.getConfig(path)
    new ColorRGBA(
      subConfig.getDouble("r").toFloat,
      subConfig.getDouble("g").toFloat,
      subConfig.getDouble("b").toFloat,
      subConfig.getDouble("a").toFloat)
  }

  def getOptionalColor(path: String): Option[ColorRGBA] =
    if (config.hasPath(path)) Some(getColor(path))
    else None

  def getOptionalString(path: String): Option[String] =
    if (config.hasPath(path)) Some(config.getString(path))
    else None

  def getOptionalVector2f(path: String): Option[Vector2f] =
    if (config.hasPath(path)) Some(getVector2f(path))
    else None

}
