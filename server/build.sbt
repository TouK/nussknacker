import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import sbt.Keys._

val scalaV = "2.11.8"

organization  := "pl.touk.esp"

name := "esp-ui"

scalaVersion  := scalaV

resolvers ++= Seq(
  "local" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
  "touk repo" at "http://nexus.touk.pl/nexus/content/groups/public",
  "touk snapshots" at "http://nexus.touk.pl/nexus/content/groups/public-snapshots",
  Resolver.bintrayRepo("hseeberger", "maven")
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

javacOptions := Seq(
  "-Xss4M" // to avoid SOF
)

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

val espEngineV = "0.1-SNAPSHOT"
val akkaHttpArgonautV = "1.8.0"
val akkaV = "2.4.8"
val catsV = "0.6.1"
val slf4jV = "1.7.21"
val logbackV = "1.1.3"
val scalaTestV = "3.0.0-M15"

libraryDependencies ++= {
  Seq(
    "de.heikoseeberger" %% "akka-http-argonaut" % akkaHttpArgonautV,
    "pl.touk.esp" %% "esp-interpreter" % espEngineV,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaV % "test",
    "org.scalatest" %% "scalatest" % scalaTestV % "test"
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
