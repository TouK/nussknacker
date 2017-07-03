resolvers ++= Seq(
  "sbt-plugin-releases" at "http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"
)

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.5")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.1")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.6.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.8.5")