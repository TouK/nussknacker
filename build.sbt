import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import net.virtualvoid.sbt.graph.Plugin._
import sbt.Credentials
import sbt.Keys._

val scalaV = "2.11.8"

val toukNexusGroups = "http://nexus.touk.pl/nexus/content/groups/"
val toukNexusRepositories = "http://nexus.touk.pl/nexus/content/repositories/"

credentials in ThisBuild += Credentials("Sonatype Nexus Repository Manager", "nexus.touk.pl", "deployment", "deployment123")

publishTo in ThisBuild := {
  if (isSnapshot.value)
    Some("snapshots" at toukNexusRepositories + "snapshots")
  else
    Some("releases"  at toukNexusRepositories + "public")
}

val commonSettings =
  graphSettings ++
  Seq(
    organization  := "pl.touk.esp",
    scalaVersion  := scalaV,
    resolvers ++= Seq(
      "local" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
      "touk repo" at "http://nexus.touk.pl/nexus/content/groups/public",
      "touk snapshots" at "http://nexus.touk.pl/nexus/content/groups/public-snapshots"
//      "flink.release-staging" at "https://repository.apache.org/content/repositories/orgapacheflink-1098" //remove when flink 1.1.0 released
    ),
    scalacOptions := Seq(
      "-unchecked",
      "-deprecation",
      "-encoding", "utf8",
      "-Xfatal-warnings",
      "-feature",
      "-language:postfixOps",
      "-language:existentials",
      "-target:jvm-1.8"
    ),
    javacOptions := Seq(
      //"-Xss:4M" // to avoid SOF
    ),
    sources in (Compile, doc) := Seq.empty,
    publishArtifact in (Compile, packageDoc) := false
  )

val flinkV = "1.0.3"
val kafkaV = "0.9.0.1"
val springV = "4.3.1.RELEASE"
val scalaTestV = "3.0.0-M15"
val logbackV = "1.1.3"
val argonautShapelessV = "1.2.0-M1"
val argonautMajorV = "6.2"
val argonautV = s"$argonautMajorV-M3"
val catsV = "0.6.1"
val scalaParsersV = "1.0.4"
val dispatchV = "0.11.3"
val slf4jV = "1.7.21"

lazy val process = (project in file("process")).
  settings(commonSettings).
  settings(
    name := "esp-process",
    fork := true, // without this there is Class org.apache.kafka.*.ByteArrayDeserializer could not be found
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).
  dependsOn(interpreter)

lazy val interpreter = (project in file("interpreter")).
  settings(commonSettings).
  settings(
    name := "esp-interpreter",
    libraryDependencies ++= {
      Seq(
        "org.slf4j" % "slf4j-api" % slf4jV,
        "org.springframework" % "spring-expression" % springV,
        "com.github.alexarchambault" %% s"argonaut-shapeless_$argonautMajorV" % argonautShapelessV,
        "org.typelevel" %% "cats-core" % catsV,
        "ch.qos.logback" % "logback-classic" % logbackV % "test",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).
  dependsOn(util)

lazy val kafka = (project in file("kafka")).
  settings(commonSettings).
  settings(
    name := "esp-kafka",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-connector-kafka-0.9" % flinkV,
        "org.apache.kafka" %% "kafka" % kafkaV % "test",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).
  dependsOn(api % "compile->compile;provided->provided")

lazy val kafkaTestUtil = (project in file("kafka-test-util")).
  settings(commonSettings).
  settings(
    name := "esp-kafka-test-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.kafka" %% "kafka" % kafkaV,
        "org.scalatest" %% "scalatest" % scalaTestV
      )
    }
  )

lazy val util = (project in file("util")).
  settings(commonSettings).
  settings(
    name := "esp-util",
    libraryDependencies ++= {
      Seq(
        "net.databinder.dispatch" %% "dispatch-core" % dispatchV % "optional",
        "org.scala-lang.modules" %% "scala-parser-combinators" % scalaParsersV, // scalaxb deps
        "io.argonaut" %% "argonaut" % argonautV,
        "org.apache.flink" %% "flink-streaming-java" % flinkV % "provided"
      )
    }
  ).
  dependsOn(api)


lazy val api = (project in file("api")).
  settings(commonSettings).
  settings(
    name := "esp-api",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-java" % flinkV % "provided"
      )
    }
  )

publishArtifact := false

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
