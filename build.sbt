import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import net.virtualvoid.sbt.graph.Plugin._

organization  := "pl.touk.rtm"
name := "rtm-engine"

scalaVersion  := "2.11.7"

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
  val akkaV = "2.4.7"
  val jacksonV = "2.4.2"
  val scalaTestV = "3.0.0-M15"
  val logbackV = "1.1.3"

  Seq(
    "org.springframework" % "spring-expression" % springV,
    "com.fasterxml.jackson.core" % "jackson-core" % jacksonV,
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonV,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaV,
    "ch.qos.logback" % "logback-classic" % logbackV,
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
