import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import net.virtualvoid.sbt.graph.Plugin._

organization  := "pl.touk.esp"
name := "esp-engine"

scalaVersion  := "2.11.8"

val toukNexusGroups = "http://nexus.touk.pl/nexus/content/groups/"
val toukNexusRepositories = "http://nexus.touk.pl/nexus/content/repositories/"

resolvers ++= Seq(
  "local" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
  "touk repo" at toukNexusGroups + "public"
)

credentials += Credentials("Sonatype Nexus Repository Manager", "nexus.touk.pl", "deployment", "deployment123")

publishTo := {
  if (isSnapshot.value)
    Some("snapshots" at toukNexusRepositories + "snapshots")
  else
    Some("releases"  at toukNexusRepositories + "public")
}

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
  val circeV = "0.5.0-M2"
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
