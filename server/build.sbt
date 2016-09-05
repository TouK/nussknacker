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

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false
assemblyMergeStrategy in assembly := {
  case PathList(ps @ _*) if ps.last == "NumberUtils.class" => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

val espEngineV = "0.1-SNAPSHOT"
val akkaV = "2.4.8"
val logbackV = "1.1.3"
val scalaTestV = "3.0.0-M15"
val slickV = "3.2.0-M1" // wsparcie dla select for update jest od 3.2.0
val hsqldbV = "2.3.4"
val flywayV = "4.0.3"
val akkaHttpCcorsV = "0.1.4"

libraryDependencies ++= {
  Seq(
    "ch.megard" %% "akka-http-cors" % akkaHttpCcorsV,
    "pl.touk.esp" %% "esp-management" % espEngineV changing()
      //tutaj mamy dwie wersje jsr305 we flinku i assembly sie pluje...
      exclude("com.google.code.findbugs", "jsr305"),
    "pl.touk.esp" %% "esp-interpreter" % espEngineV changing(),

    "com.typesafe.slick" %% "slick" % slickV,
    "com.typesafe.slick" %% "slick-hikaricp" % slickV,
    "org.hsqldb" % "hsqldb" % hsqldbV,
    "org.flywaydb" % "flyway-core" % flywayV,

    "com.typesafe.akka" %% "akka-http-testkit" % akkaV % "test",
    "com.typesafe.slick" %% "slick-testkit" % slickV % "test",
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