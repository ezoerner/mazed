import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

name := "mazed"
scalaVersion := "2.11.8"
organization := "ezoerner"

resolvers += Resolver.bintrayRepo("jmonkeyengine", "com.jme3")

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.3.0") ++ jME3Dependencies ++ logging

mainClass in (Compile, run) := Some("mazed.app.MazedApp")
mainClass in assembly := Some("mazed.app.MazedApp")

GithubRelease.releaseAssets := findTargetFiles(baseDirectory.value, s"*-assembly-${version.value}.jar")

def findTargetFiles(base: File, pattern: String): Seq[File] = {
  val finder: PathFinder = (base / "target") ** pattern
  finder.get
}

lazy val jME3Dependencies = Seq(
  jME3("core"), // Core libraries needed for all jME3 projects
  jME3("desktop", Runtime), // Parts of the jME3 API that are only compatible with desktop renderers, needed for image loading on desktop
  jME3("lwjgl"), // Desktop renderer for jME3
  jME3("jbullet") // Physics support using jbullet (desktop only) Only jme3-jbullet OR jme3-bullet can be used
  //  jME3("blender"), // Blender file loader, only works on desktop renderers
  //  jME3("plugins", Runtime), // Loader plugins for OgreXML and jME-XML
  //  jME3("effects"), // Effects libraries for water and other post filters
  //  jME3("networking"), // jME3 networking libraries (aka spidermonkey)
  //  jME3("jogg"), // Loader for jogg audio files
  //  jME3("terrain"), // Terrain generation API
  //  jME3("niftygui") // NiftyGUI support for jME3
)

def jME3(name: String, config: Configuration = Compile) = "com.jme3" % s"jme3-$name" % "3.0.10" % config

lazy val logging = Seq(
  slf4j_api,
  logback,
  slf4j_jul_adapter,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0"
)

lazy val slf4j_api = slf4j("slf4j-api")
lazy val logback = "ch.qos.logback" % "logback-classic" % "1.1.7"
lazy val slf4j_jcl_adapter = slf4j("jcl-over-slf4j")
lazy val slf4j_log4j_adapter = slf4j("log4j-over-slf4j")
lazy val slf4j_jul_adapter = slf4j("jul-to-slf4j")

def slf4j(name: String) = "org.slf4j" % name % "1.7.21"


releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,                   // performs the initial git checks
  pushChanges,                            // also checks that an upstream branch is properly configured
  releaseStepTask(assembly),
  releaseStepTask(releaseOnGithub),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
