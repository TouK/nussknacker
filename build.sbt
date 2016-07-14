import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import net.virtualvoid.sbt.graph.Plugin._

organization  := "pl.touk.rtm"
name := "rtm-engine"

scalaVersion  := "2.11.8"

resolvers ++= Seq(
  "publishTo" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
  "touk repo" at "http://nexus.touk.pl/nexus/content/groups/public"
)

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-encoding", "utf8",
  "-Xfatal-warnings",
  "-feature",
  "-language:postfixOps",
  "-language:existentials",
  "-target:jvm-1.8"
)

graphSettings

libraryDependencies ++= {
  val springV = "4.3.1.RELEASE"
  val scalaTestV = "3.0.0-M15"
  val logbackV = "1.1.3"
  val circeV = "0.4.1"
  val slf4jV = "1.7.21"

  Seq(
    "org.slf4j" % "slf4j-api" % slf4jV,
    "org.springframework" % "spring-expression" % springV,
    "ch.qos.logback" % "logback-classic" % logbackV % "test",
    "org.scalatest" %% "scalatest" % scalaTestV % "test",
    "io.circe" %% "circe-core" % circeV,
    "io.circe" %% "circe-generic" % circeV,
    "io.circe" %% "circe-parser" % circeV
  )
}

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,              // : ReleaseStep
  inquireVersions,                        // : ReleaseStep
  runTest,                                // : ReleaseStep
  setReleaseVersion,                      // : ReleaseStep
  commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
  tagRelease,                             // : ReleaseStep
  setNextVersion,                         // : ReleaseStep
  commitNextVersion,                      // : ReleaseStep
  pushChanges                             // : ReleaseStep, also checks that an upstream branch is properly configured
)
