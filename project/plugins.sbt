logLevel := Level.Warn

resolvers += "Era7 maven releases" at "https://s3-eu-west-1.amazonaws.com/releases.era7.com"

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")
addSbtPlugin("ohnosequences" % "sbt-github-release" % "0.3.0")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.3")
