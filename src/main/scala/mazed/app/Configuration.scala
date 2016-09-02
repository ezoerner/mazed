package mazed.app

import com.jme3.math.{ColorRGBA, Vector2f, Vector3f}
import com.typesafe.config.{Config, ConfigFactory}

object Configuration {
  private val config = ConfigFactory.load()

  val appDebugBullet = config.getBoolean("app.debugBullet")
  val appEnableFpsText = config.getBoolean("app.enableFpsText")
  val appSettingsDialogImage = config.getString("app.settingsDialogImage")

  val moveForwardSpeed = config.getDouble("player.moveForwardSpeed").toFloat
  val moveSidewaysSpeed = config.getDouble("player.moveSidewaysSpeed").toFloat
  val moveBackwardSpeed = config.getDouble("player.moveBackwardSpeed").toFloat
  val lookVerticalSpeed = config.getDouble("player.lookVerticalSpeed").toFloat
  val rotateSpeed = config.getDouble("player.rotateSpeed").toFloat
  val moveCameraSpeed = config.getDouble("player.camera.moveSpeed").toFloat
  val playerHeight = config.getDouble("player.height").toFloat
  val playerRadius = config.getDouble("player.radius").toFloat
  val playerMass = config.getDouble("player.mass").toFloat
  val playerModelScale = config.getDouble("player.modelScale").toFloat
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

  val mazeCellDim = getVector3f("maze.cell")
  val mazeWallThickness = config.getDouble("maze.wall.thickness").toFloat
  val mazeHeight = config.getInt("maze.height")
  val mazeWidth = config.getInt("maze.width")
  val mazeEntranceConfig = config.getConfig("maze.entrance")
  val mazeWallConfig = config.getConfig("maze.wall")
  val mazeFloorConfig = config.getConfig("maze.floor")
  val mazeFloorMargin = config.getDouble("maze.floor.margin").toFloat
  val mazeFloorSize = getVector3f("maze.floor.size")

  def getVector3f(path: String, cfg: Config = config): Vector3f = {
    val subConfig = cfg.getConfig(path)
    new Vector3f(
      subConfig.getDouble("x").toFloat,
      subConfig.getDouble("y").toFloat,
      subConfig.getDouble("z").toFloat)
  }

  def getVector2f(path: String, cfg: Config = config): Vector2f = {
    val subConfig = cfg.getConfig(path)
    new Vector2f(
      subConfig.getDouble("x").toFloat,
      subConfig.getDouble("y").toFloat)
  }

  def getColor(path: String, cfg: Config = config): ColorRGBA = {
    val subConfig = cfg.getConfig(path)
    new ColorRGBA(
      subConfig.getDouble("r").toFloat,
      subConfig.getDouble("g").toFloat,
      subConfig.getDouble("b").toFloat,
      subConfig.getDouble("a").toFloat)
  }

  def getOptionalColor(path: String, cfg: Config = config): Option[ColorRGBA] =
    if (cfg.hasPath(path)) Some(getColor(path, cfg))
    else None

  def getOptionalString(path: String, cfg: Config = config): Option[String] =
    if (cfg.hasPath(path)) Some(cfg.getString(path))
    else None

  def getOptionalVector2f(path: String, cfg: Config = config): Option[Vector2f] =
    if (cfg.hasPath(path)) Some(getVector2f(path, cfg))
    else None

  def getOptionalBoolean(path: String, cfg: Config = config) = {
    if (cfg.hasPath(path)) Some(cfg.getBoolean(path))
    else None
  }
}
