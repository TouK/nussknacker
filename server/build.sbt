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

val espEngineV = "0.1-SNAPSHOT"
val akkaHttpArgonautV = "1.8.0"
val akkaV = "2.4.8"
val slf4jV = "1.7.21"
val logbackV = "1.1.3"
val scalaTestV = "3.0.0-M15"
val slickV = "3.1.1"
val hsqldbV = "2.3.4"
val flywayV = "4.0.3"
val akkaHttpCcorsV = "0.1.4"

libraryDependencies ++= {
  Seq(
    "de.heikoseeberger" %% "akka-http-argonaut" % akkaHttpArgonautV,
    "ch.megard" %% "akka-http-cors" % akkaHttpCcorsV,
    "pl.touk.esp" %% "esp-management" % espEngineV changing()
      //fixme tutaj nie powinnismy nawet probowac resolvowac esp-process-sample, trzeba poprawic konfiguracje testow it
      exclude("pl.touk.esp", "esp-process-sample_2.11") exclude("com.jayway.awaitility", "awaitility-scala")
      //a tutaj mamy dwie wersje jsr305 we flinku i assembly sie pluje...
      exclude("com.google.code.findbugs", "jsr305"),
    "pl.touk.esp" %% "esp-interpreter" % espEngineV changing(),

    //to musimy podac explicite, zeby wymusic odpowiednia wersje dla flinka
    "com.typesafe.akka" %% "akka-remote" % akkaV,
    "com.typesafe.akka" %% "akka-slf4j" % akkaV,
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