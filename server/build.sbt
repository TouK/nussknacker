import java.nio.file.{Files, StandardCopyOption}

import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import sbt.Keys._

import scala.util.control.NonFatal

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

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false
assemblyMergeStrategy in assembly := {
  case PathList(ps @ _*) if ps.last == "NumberUtils.class" => MergeStrategy.first
  case PathList("org", "w3c", "dom", "events",  xs @ _*) => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

val espEngineDefaultV = "0.1-SNAPSHOT"
val espEngineV = sys.props.getOrElse("espEngineToukVersion", espEngineDefaultV)
//mamy te wersje akki bo flink jej wymaga
val akkaV = "2.3.7"
val akkaHttpV = "2.0.3"

val logbackV = "1.1.3"
val scalaTestV = "3.0.0-M15"
val slickV = "3.2.0-M1" // wsparcie dla select for update jest od 3.2.0
val hsqldbV = "2.3.4"
val flywayV = "4.0.3"
val flinkV = "1.1.4"

//we want to be able to build
val includeFlinkAndScala = Option(System.getProperty("includeFlinkAndScala")).exists(_.toBoolean)

val flinkScope = if (includeFlinkAndScala) "compile" else "provided"

libraryDependencies ++= {
  Seq(
    "pl.touk.esp" %% "esp-management" % espEngineV changing(),
    "org.apache.flink" %% "flink-streaming-scala" % flinkV % flinkScope
    excludeAll(
        ExclusionRule("com.google.code.findbugs", "jsr305"),
        ExclusionRule("log4j", "log4j"),
        ExclusionRule("org.slf4j", "slf4j-log4j12")
      ),
    "org.apache.flink" %% "flink-clients" % flinkV % flinkScope
    //tutaj mamy dwie wersje jsr305 we flinku i assembly sie pluje...
    excludeAll(
      ExclusionRule("com.google.code.findbugs", "jsr305"),
      ExclusionRule("log4j", "log4j"),
      ExclusionRule("org.slf4j", "slf4j-log4j12")

    ),
    "pl.touk.esp" %% "esp-management-sample" % espEngineV % "test" changing() classifier "assembly",

    "com.typesafe.akka" %% "akka-http-experimental" % akkaHttpV force(),
    "com.typesafe.akka" %% "akka-http-testkit-experimental" % akkaHttpV % "test" force(),

    "ch.qos.logback" % "logback-core" % logbackV,
    "ch.qos.logback" % "logback-classic" % logbackV,
    "org.slf4j" % "log4j-over-slf4j" % "1.7.21",

    "pl.touk.esp" %% "esp-interpreter" % espEngineV changing(),
    "com.typesafe.slick" %% "slick" % slickV,
    "com.typesafe.slick" %% "slick-hikaricp" % slickV,
    "org.hsqldb" % "hsqldb" % hsqldbV,
    "org.flywaydb" % "flyway-core" % flywayV,
    "org.apache.xmlgraphics" % "fop" % "2.1",

    "com.typesafe.slick" %% "slick-testkit" % slickV % "test",
    "org.scalatest" %% "scalatest" % scalaTestV % "test"
  )
}

resourceGenerators in Test <+= Def.task {
  val destFile = target.value / "testJar.jar"
  target.value.mkdirs()

  val maybeFile = (dependencyClasspath in Test).value
    .map(_.data).find(_.name contains "esp-management-sample")

  println(s"esp-management-sample jar: $maybeFile")


  maybeFile.foreach { file =>
      Files.copy(file.toPath, destFile.toPath, StandardCopyOption.REPLACE_EXISTING)
  }
  maybeFile.map(_ => destFile).toSeq
}

assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = includeFlinkAndScala, level = Level.Debug)


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

parallelExecution in ThisBuild := false //bo testy korzystaja ze wspolnej bazy