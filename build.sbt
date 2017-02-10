import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import net.virtualvoid.sbt.graph.Plugin._
import sbt._
import sbt.Keys._

val scalaV = "2.11.8"

val toukNexusGroups = "http://nexus.touk.pl/nexus/content/groups/"
val toukNexusRepositories = "http://nexus.touk.pl/nexus/content/repositories/"

val defaultVersion = "0.1-SNAPSHOT"

version in ThisBuild := sys.props.getOrElse("espEngineToukVersion", defaultVersion)

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
      "touk snapshots" at "http://nexus.touk.pl/nexus/content/groups/public-snapshots",
      "spring milestone" at "https://repo.spring.io/milestone"
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
    sources in (Compile, doc) := Seq.empty,
    publishArtifact in (Compile, packageDoc) := false,
    assemblyMergeStrategy in assembly := {
      case PathList(ps @ _*) if ps.last == "NumberUtils.class" => MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )

//mamy te wersje akki bo flink jej wymaga
val akkaV = "2.3.7"
val flinkV = "1.1.4"
val kafkaV = "0.9.0.1"
val springV = "5.0.0.M1"
val scalaTestV = "3.0.0-M15"
val logbackV = "1.1.3"
val argonautShapelessV = "1.2.0-M1"
val argonautMajorV = "6.2"
val argonautV = s"$argonautMajorV-M3"
val catsV = "0.7.0"
val monocleV = "1.2.2"
val scalaParsersV = "1.0.4"
val dispatchV = "0.11.3"
val slf4jV = "1.7.21"
val scalaLoggingV = "3.4.0"
val ficusV = "1.2.6"
val configV = "1.3.0"

val perfTestSampleName = "esp-perf-test-sample"

lazy val perf_test = (project in file("perf-test")).
  configs(IntegrationTest). // po dodaniu własnej konfiguracji, IntellijIdea nie rozpoznaje zależności dla niej
  settings(commonSettings).
  settings(Defaults.itSettings).
  settings(
    name := "esp-perf-test",
    Keys.test in IntegrationTest <<= (Keys.test in IntegrationTest).dependsOn(
      publishLocal in (assembly in Compile) in perf_test_sample
    ),
    libraryDependencies ++= {
      Seq(
        "org.scalatest" %% "scalatest" % scalaTestV % "it,test",
        "org.slf4j" % "jul-to-slf4j" % slf4jV,
        "org.apache.flink" %% "flink-clients" % flinkV % "provided",
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "runtime", // na potrzeby optymalizacji procesów
        "com.iheart" %% "ficus" % ficusV
      )
    }
  ).
  dependsOn(management, interpreter, kafka, kafkaTestUtil)


lazy val perf_test_sample = (project in file("perf-test/sample")).
  settings(commonSettings).
  settings(
    name := perfTestSampleName,
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "com.iheart" %% "ficus" % ficusV
      )
    },
    artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.copy(`classifier` = Some("assembly"))
    }
  ).
  settings(addArtifact(artifact in (Compile, assembly), assembly)).
  dependsOn(flinkUtil, kafka, process % "runtime")

val managementSampleName = "esp-management-sample"

lazy val management = (project in file("management")).
  configs(IntegrationTest).
  settings(commonSettings).
  settings(Defaults.itSettings).
  settings(
    name := "esp-management",
    Keys.test in IntegrationTest <<= (Keys.test in IntegrationTest).dependsOn(
      publishLocal in (assembly in Compile) in management_sample
    ),
    //jest problem we flinku jesli sie naraz deployuje i puszcza testy :|
    parallelExecution in IntegrationTest := false,
    libraryDependencies ++= {
      Seq(
        "org.typelevel" %% "cats-core" % catsV,
        "org.apache.flink" %% "flink-clients" % flinkV % "provided",
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",

        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,

        // zależności dla konfiguracji "it" muszą być też dla "test", żeby nie trafiły do publikowanego poma
        "org.scalatest" %% "scalatest" % scalaTestV % "it,test"
      )
    }
  ).dependsOn(interpreter, kafkaTestUtil % "it,test")

lazy val management_sample = (project in file("management/sample")).
  settings(commonSettings).
  settings(
    name := managementSampleName,
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false, level = Level.Debug),
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    },
    artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.copy(`classifier` = Some("assembly"))
    }
  ).
  settings(addArtifact(artifact in (Compile, assembly), assembly)).
  dependsOn(flinkUtil, kafka, process % "runtime,test")

lazy val process = (project in file("process")).
  settings(commonSettings).
  settings(
    name := "esp-process",
    fork := true, // without this there is Class org.apache.kafka.*.ByteArrayDeserializer could not be found
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" %% "flink-statebackend-rocksdb" % flinkV,
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).dependsOn(flinkApi, flinkUtil, interpreter, kafka % "test", kafkaTestUtil % "test")

lazy val interpreter = (project in file("interpreter")).
  settings(commonSettings).
  settings(
    name := "esp-interpreter",
    libraryDependencies ++= {
      Seq(
        "org.springframework" % "spring-expression" % springV,
        "com.github.alexarchambault" %% s"argonaut-shapeless_$argonautMajorV" % argonautShapelessV,
        "ch.qos.logback" % "logback-classic" % logbackV % "test",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).
  enablePlugins(BuildInfoPlugin).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version),
    buildInfoKeys ++= Seq[BuildInfoKey] (
      "buildTime" -> java.time.LocalDateTime.now().toString,
      "gitCommit" -> git.gitHeadCommit.value.getOrElse("")
    ),
    buildInfoPackage := "pl.touk.esp.engine.version",
    buildInfoOptions ++= Seq(BuildInfoOption.ToMap)
  ).
  dependsOn(util)


lazy val interpreterAddons = (project in file("interpreter-addons")).
  settings(commonSettings).
  settings(
    name := "esp-interpreter-addons",
    libraryDependencies ++= {
      Seq(
        "com.github.julien-truffaut"  %%  "monocle-macro"  % monocleV,
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).dependsOn(interpreter)

lazy val kafka = (project in file("kafka")).
  settings(commonSettings).
  settings(
    name := "esp-kafka",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-connector-kafka-0.9" % flinkV,
        "org.apache.flink" %% "flink-streaming-java" % flinkV % "provided", // api dependency
        "org.apache.kafka" %% "kafka" % kafkaV % "test",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).
  dependsOn(flinkApi, util, kafkaTestUtil % "test")

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
        //TODO: przywrocic optional, kiedy uporzadkujemy zaleznosci w ui
        "net.databinder.dispatch" %% "dispatch-core" % dispatchV,// % "optional",
        "org.scala-lang.modules" %% "scala-parser-combinators" % scalaParsersV, // scalaxb deps
        "io.argonaut" %% "argonaut" % argonautV,
        "com.iheart" %% "ficus" % ficusV
      )
    }
  ).dependsOn(api)



lazy val flinkUtil = (project in file("flink-util")).
  settings(commonSettings).
  settings(
    name := "esp-flink-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" % "flink-metrics-dropwizard" % flinkV
      )
    }
  ).dependsOn(util, flinkApi)


lazy val api = (project in file("api")).
  settings(commonSettings).
  settings(
    name := "esp-api",
    libraryDependencies ++= {
      Seq(
        "org.typelevel" %% "cats-core" % catsV,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "com.typesafe" % "config" % configV

      )
    }
  )

lazy val flinkApi = (project in file("flink-api")).
  settings(commonSettings).
  settings(
    name := "esp-flink-api",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-java" % flinkV % "provided"
      )
    }
  ).dependsOn(api)

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

