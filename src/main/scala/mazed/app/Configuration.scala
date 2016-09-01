package mazed.app

import com.jme3.math.{ColorRGBA, Vector2f, Vector3f}
import com.typesafe.config.ConfigFactory

object Configuration {
  val config = ConfigFactory.load()

  val moveForwardSpeed = config.getDouble("player.moveForwardSpeed").toFloat
  val moveSidewaysSpeed = config.getDouble("player.moveSidewaysSpeed").toFloat
  val moveBackwardSpeed = config.getDouble("player.moveBackwardSpeed").toFloat
  val lookVerticalSpeed = config.getDouble("player.lookVerticalSpeed").toFloat
  val rotateSpeed = config.getDouble("player.rotateSpeed").toFloat
  val moveCameraSpeed = config.getDouble("player.camera.moveSpeed").toFloat
  val playerHeight = config.getDouble("player.height").toFloat
  val playerRadius = config.getDouble("player.radius").toFloat
  val maxCamDistance =  config.getDouble("player.camera.maxDistance").toFloat
  val minCamDistance =  config.getDouble("player.camera.minDistance").toFloat
  val firstPersonCamDistance =  config.getDouble("player.camera.firstPersonDistance").toFloat
  val initialCameraOffset = getVector3f("player.camera.offset")
  val initialLocation = getVector3f("player.initialLocation")
  val jumpForce = getVector3f("player.jumpForce")
  val gravity = getVector3f("player.gravity")
  val playerInitialLookAt = getVector3f("player.initialLookAt")
  val maybeModel = getOptionalString("player.model")
  val cameraLookAt = getVector3f("player.camera.lookAt")
  val skyTexture = getOptionalString("maze.sky.texture")
  val skyColor = getOptionalColor("maze.sky.color")

  private def getVector3f(path: String): Vector3f = {
    val subConfig = config.getConfig(path)
    new Vector3f(
      subConfig.getDouble("x").toFloat,
      subConfig.getDouble("y").toFloat,
      subConfig.getDouble("z").toFloat)
  }

  private def getVector2f(path: String): Vector2f = {
    val subConfig = config.getConfig(path)
    new Vector2f(
      subConfig.getDouble("x").toFloat,
      subConfig.getDouble("y").toFloat)
  }

  private def getColor(path: String): ColorRGBA = {
    val subConfig = config.getConfig(path)
    new ColorRGBA(
      subConfig.getDouble("r").toFloat,
      subConfig.getDouble("g").toFloat,
      subConfig.getDouble("b").toFloat,
      subConfig.getDouble("a").toFloat)
  }

  private def getOptionalColor(path: String): Option[ColorRGBA] =
    if (config.hasPath(path)) Some(getColor(path))
    else None

  private def getOptionalString(path: String): Option[String] =
    if (config.hasPath(path)) Some(config.getString(path))
    else None

  private def getOptionalVector2f(path: String): Option[Vector2f] =
    if (config.hasPath(path)) Some(getVector2f(path))
    else None

  private def getOptionalBoolean(path: String) = {
    if (config.hasPath(path)) Some(config.getBoolean(path))
    else None
  }
}
