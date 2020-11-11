import com.typesafe.sbt.packager.SettingsHelper
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerUsername
import sbt.Keys._
import sbt.{Def, _}
import sbtassembly.AssemblyPlugin.autoImport.assembly
import sbtassembly.MergeStrategy
import ReleaseTransformations._

import scala.util.Try

val scala211 = "2.11.12"
// Warning: Flink doesn't work correctly with 2.12.11
val scala212 = "2.12.10"
lazy val supportedScalaVersions = List(scala212, scala211)

// Silencer must be compatible with exact scala version - see compatibility matrix: https://search.maven.org/search?q=silencer-plugin
// Silencer 1.7.x require Scala 2.12.11 (see warning above)
val silencerV_2_12 = "1.6.0"
val silencerV = "1.7.0"

//by default we include flink and scala, we want to be able to disable this behaviour for performance reasons
val includeFlinkAndScala = Option(System.getProperty("includeFlinkAndScala", "true")).exists(_.toBoolean)

val flinkScope = if (includeFlinkAndScala) "compile" else "provided"
val nexusUrlFromProps = Option(System.getProperty("nexusUrl"))
//TODO: this is pretty clunky, but works so far for our case...
val nexusHostFromProps = nexusUrlFromProps.map(_.replaceAll("http[s]?://", "").replaceAll("[:/].*", ""))

//Docker release configuration
val dockerTagName = Option(System.getProperty("dockerTagName"))
val dockerPort = System.getProperty("dockerPort", "8080").toInt
val dockerUserName = Some(System.getProperty("dockerUserName", "touk"))
val dockerPackageName = System.getProperty("dockerPackageName", "nussknacker")
val dockerUpLatestFromProp = Option(System.getProperty("dockerUpLatest")).flatMap(p => Try(p.toBoolean).toOption)
val addDevModel = System.getProperty("addDevModel", "false").toBoolean

// `publishArtifact := false` should be enough to keep sbt from publishing root module,
// unfortunately it does not work, so we resort to hack by publishing root module to Resolver.defaultLocal
//publishArtifact := false
publishTo := Some(Resolver.defaultLocal)
crossScalaVersions := Nil

//have some problems with force() - e.g. with forcing circe version in httpUtils...
ThisBuild / useCoursier := false

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  publishTo := {
    nexusUrlFromProps.map { url =>
      (if (isSnapshot.value) "snapshots" else "releases") at url
    }.orElse {
      val defaultNexusUrl = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at defaultNexusUrl + "content/repositories/snapshots")
      else
        sonatypePublishToBundle.value
    }
  },
  publishArtifact in Test := false,
  //We don't put scm information here, it will be added by release plugin and if scm provided here is different than the one from scm
  //we'll end up with two scm sections and invalid pom...
  pomExtra in Global := {
      <developers>
        <developer>
          <id>TouK</id>
          <name>TouK</name>
          <url>https://touk.pl</url>
        </developer>
      </developers>
  },
  organization := "pl.touk.nussknacker",
  homepage := Some(url(s"https://github.com/touk/nussknacker")),
  credentials := nexusHostFromProps.map(host => Credentials("Sonatype Nexus Repository Manager",
    host, System.getProperty("nexusUser", "touk"), System.getProperty("nexusPassword"))
    // otherwise ~/.sbt/1.0/sonatype.sbt will be used
  ).toSeq
)

/**
  * TODO: figure how how to handle JDK modules
  */
def nussknackerMergeStrategy: String => MergeStrategy = {
  case PathList(ps@_*) if ps.last == "module-info.class" => MergeStrategy.first //after confluent bump up to 5.5
  case PathList(ps@_*) if ps.last == "NumberUtils.class" => MergeStrategy.first
  case PathList(ps@_*) if ps.last == "io.netty.versions.properties" => MergeStrategy.first
  case PathList(ps@_*) if ps.last == "libnetty_transport_native_kqueue_x86_64.jnilib" => MergeStrategy.first
  case PathList("org", "w3c", "dom", "events", xs @ _*) => MergeStrategy.first
  case PathList("org", "apache", "commons", "logging", xs @ _*) => MergeStrategy.first
  case PathList("javax", "validation", xs @ _*) => MergeStrategy.first //after confluent bump up to 5.5
  case PathList("javax", "el", xs @ _*) => MergeStrategy.first //after confluent bump up to 5.5
  case PathList("akka", xs @ _*) => MergeStrategy.last
  case x => MergeStrategy.defaultMergeStrategy(x)
}

lazy val SlowTests = config("slow") extend Test

val slowTestsSettings =
  inConfig(SlowTests)(Defaults.testTasks) ++ Seq(
    testOptions in SlowTests := Seq(
      Tests.Argument(TestFrameworks.ScalaTest, "-n", "org.scalatest.tags.Slow"),
      scalaTestReports
    )
  )

val scalaTestReports = Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/surefire-reports", "-oFGD")
val ignoreSlowTests = Tests.Argument(TestFrameworks.ScalaTest, "-l", "org.scalatest.tags.Slow")

lazy val commonSettings =
  publishSettings ++
    Seq(
      test in assembly := {},
      licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
      crossScalaVersions := supportedScalaVersions,
      scalaVersion  := scala212,
      resolvers ++= Seq(
        "confluent" at "https://packages.confluent.io/maven"
      ),
      testOptions in Test ++= Seq(scalaTestReports, ignoreSlowTests),
      testOptions in IntegrationTest += scalaTestReports,
      addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),
      // We can't use addCompilerPlugin because it not support usage of scalaVersion.value
      libraryDependencies += compilerPlugin("com.github.ghik" % "silencer-plugin" % (CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 12)) => silencerV_2_12
        case _             => silencerV
      }) cross CrossVersion.full),
      scalacOptions := Seq(
        "-unchecked",
        "-deprecation",
        "-encoding", "utf8",
        "-Xfatal-warnings",
        "-feature",
        "-language:postfixOps",
        "-language:existentials",
        "-Ypartial-unification",
        //Flink image is available only for jdk8
        "-target:jvm-1.8"
      ),
      javacOptions := Seq(
        "-Xlint:deprecation",
        "-Xlint:unchecked",
        //Flink image is available only for jdk8
        "-source",
        "1.8",
        "-target",
        "1.8",
        //we use it e.g. to provide consistent behaviour wrt extracting parameter names from scala and java
        "-parameters"
      ),
      assemblyMergeStrategy in assembly := nussknackerMergeStrategy,
      coverageMinimum := 60,
      coverageFailOnMinimum := false,
      //problem with scaladoc of api: https://github.com/scala/bug/issues/10134
      scalacOptions in (Compile, doc) -= "-Xfatal-warnings",
      libraryDependencies ++= Seq(
        "com.github.ghik" % "silencer-lib" % (CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((2, 12)) => silencerV_2_12
          case _             => silencerV
        }) % Provided cross CrossVersion.full,
        "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionsCompatV
      ),
      //here we add dependencies that we want to have fixed across all modules
      dependencyOverrides ++= Seq(
        //currently Flink (1.11 -> https://github.com/apache/flink/blob/master/pom.xml#L128) uses 1.8.2 Avro version
        "org.apache.avro" % "avro" % avroV,
        "com.typesafe" % "config" % configV,
        //we stick to version in Flink to avoid nasty bugs in process runtime...
        //NOTE: xmlgraphics used in UI comes with v. old version...
        "commons-io" % "commons-io" % commonsIOV,
        //we stick to version in Flink to avoid nasty bugs in process runtime...
        //NOTE: commons-text (in api) uses 3.9...
        "org.apache.commons" % "commons-lang3" % commonsLangV
      )
    )

val forkSettings = Seq(
  fork := true,
  javaOptions := Seq(
    "-Xmx512M",
    "-XX:ReservedCodeCacheSize=128M",
    "-Xss4M",
    // to prevent travis OOM from killing java
    "-XX:MaxMetaspaceSize=515G"
  )
)

val akkaV = "2.5.21" //same version as in Flink
val flinkV = "1.11.2"
val avroV = "1.9.2" // for java time logical types conversions purpose
val kafkaV = "2.4.1"
val springV = "5.1.4.RELEASE"
val scalaTestV = "3.0.8"
val scalaCheckV = "1.14.0"
val logbackV = "1.1.3"
val argonautV = "6.2.1"
val circeV = "0.11.1"
val jwtCirceV = "4.0.0"
val jacksonV = "2.9.2"
val catsV = "1.5.0"
val scalaParsersV = "1.0.4"
val dispatchV = "1.0.1"
val slf4jV = "1.7.21"
val scalaLoggingV = "3.9.0"
val scalaCompatV = "0.9.0"
val ficusV = "1.4.1"
val configV = "1.4.0"
val commonsLangV = "3.3.2"
val commonsTextV = "1.8"
val commonsIOV = "2.4"
//we want to use 5.x for standalone metrics to have tags, however dropwizard development kind of freezed. Maybe we should consider micrometer?
//In Flink metrics we use bundled dropwizard metrics v. 3.x
val dropWizardV = "5.0.0-rc3"
val scalaCollectionsCompatV = "2.1.6"

val akkaHttpV = "10.1.8"
val akkaHttpCirceV = "1.27.0"
val slickV = "3.3.2"
val hsqldbV = "2.5.0"
val postgresV = "42.2.12"
val flywayV = "6.3.3"
val confluentV = "5.5.0"
val jbcryptV = "0.4"
val cronParserV = "3.1.1"
val javaxValidationApiV = "2.0.1.Final"
val caffeineCacheV = "2.8.2"

lazy val dockerSettings = {
  val workingDir = "/opt/nussknacker"

  Seq(
    dockerEntrypoint := Seq(s"$workingDir/bin/nussknacker-entrypoint.sh", dockerPort.toString),
    dockerExposedPorts := Seq(dockerPort),
    dockerExposedVolumes := Seq(s"$workingDir/storage", s"$workingDir/data"),
    defaultLinuxInstallLocation in Docker := workingDir,
    dockerBaseImage := "openjdk:8-jdk",
    dockerUsername := dockerUserName,
    packageName := dockerPackageName,
    dockerUpdateLatest := dockerUpLatestFromProp.getOrElse(!isSnapshot.value),
    dockerLabels := Map(
      "tag" -> dockerTagName.getOrElse(version.value),
      "version" -> version.value,
      "scala" -> scalaVersion.value,
      "flink" -> flinkV
    ),
    dockerEnvVars := Map(
      "AUTHENTICATION_METHOD" -> "BasicAuth",
      "AUTHENTICATION_USERS_FILE" -> "./conf/users.conf",
      "AUTHENTICATION_HEADERS_ACCEPT" -> "application/json",
      "OAUTH2_RESPONSE_TYPE" -> "code",
      "OAUTH2_GRANT_TYPE" -> "authorization_code",
      "OAUTH2_SCOPE" -> "read:user",
    ),
    version in Docker := dockerTagName.getOrElse(version.value)
  )
}

val publishAssemblySettings = List(
  artifact in (Compile, assembly) := {
    val art = (artifact in (Compile, assembly)).value
    art.withClassifier(Some("assembly"))
  }, addArtifact(artifact in (Compile, assembly), assembly)
)

def assemblySettings(assemblyName: String, includeScala: Boolean): List[Def.SettingsDefinition] = List(
  assemblyJarName in assembly := assemblyName,
  assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = includeScala, level = Level.Info),
  test in assembly := {}
)

def assemblySampleSettings(assemblyName: String): List[Def.SettingsDefinition]
  = assemblySettings(assemblyName, includeScala = false)

lazy val dist = {
  val module = sbt.Project("dist", file("nussknacker-dist"))
    .settings(commonSettings)
    .enablePlugins(SbtNativePackager, JavaServerAppPackaging)
    .settings(
      packageName in Universal := ("nussknacker" + "-" + version.value),
      Keys.compile in Compile := (Keys.compile in Compile).dependsOn(
        (assembly in Compile) in generic,
        (assembly in Compile) in demo,
        (assembly in Compile) in flinkProcessManager,
        (assembly in Compile) in engineStandalone
      ).value,
      mappings in Universal ++= Seq(
        (crossTarget in generic).value / "genericModel.jar" -> "model/genericModel.jar",
        (crossTarget in demo).value / s"demoModel.jar" -> "model/demoModel.jar",
        (crossTarget in flinkProcessManager).value / s"nussknacker-flink-manager.jar" -> "managers/nussknacker-flink-manager.jar",
        (crossTarget in engineStandalone).value / s"nussknacker-standalone-manager.jar" -> "managers/nussknacker-standalone-manager.jar"
      ),
      /* //FIXME: figure out how to filter out only for .tgz, not for docker
      mappings in Universal := {
        val universalMappings = (mappings in Universal).value
        //we don't want docker-* stuff in .tgz
        universalMappings filterNot { case (file, _) =>
          file.getName.startsWith("docker-") ||file.getName.contains("entrypoint.sh")
        }
      },*/
      publishArtifact := false,
      SettingsHelper.makeDeploymentSettings(Universal, packageZipTarball in Universal, "tgz")
    )
    .settings(dockerSettings)
    .dependsOn(ui)
  if (addDevModel) {
    module
      .settings(
        Keys.compile in Compile := (Keys.compile in Compile).dependsOn(
          (assembly in Compile) in flinkManagementSample,
          (assembly in Compile) in standaloneSample
        ).value,
        mappings in Universal += {
          val genericModel = (crossTarget in flinkManagementSample).value / "managementSample.jar"
          genericModel -> "model/managementSample.jar"
        },
        mappings in Universal += {
          val demoModel = (crossTarget in standaloneSample).value / s"standaloneSample.jar"
          demoModel -> "model/standaloneSample.jar"
        }
      )
  } else {
    module
  }
}

def engine(name: String) = file(s"engine/$name")

lazy val engineStandalone = (project in engine("standalone/engine")).
  configs(IntegrationTest).
  settings(commonSettings).
  settings(assemblySettings("nussknacker-standalone-manager.jar", includeScala = false): _*).
  settings(Defaults.itSettings).
  settings(
    name := "nussknacker-standalone-engine",
    Keys.test in IntegrationTest := (Keys.test in IntegrationTest).dependsOn(
      (assembly in Compile) in standaloneSample
    ).value,
  ).
  dependsOn(interpreter % "provided", standaloneUtil, httpUtils % "provided", testUtil % "it,test")

lazy val standaloneApp = (project in engine("standalone/app")).
  settings(commonSettings).
  settings(publishAssemblySettings: _*).
  settings(
    name := "nussknacker-standalone-app",
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = true, level = Level.Info),
    libraryDependencies ++= {
      Seq(
        "de.heikoseeberger" %% "akka-http-circe" % akkaHttpCirceV,
        // Force akka-http and akka-stream versions to avoid bumping by akka-http-circe.
        "com.typesafe.akka" %% "akka-http" % akkaHttpV force(),
        "com.typesafe.akka" %% "akka-stream" % akkaV force(),
        "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test" force(),
        "com.typesafe.akka" %% "akka-testkit" % akkaV % "test" force(),
        "com.typesafe.akka" %% "akka-slf4j" % akkaV,
        "ch.qos.logback" % "logback-classic" % logbackV
      )
    }
  ).
  dependsOn(engineStandalone, interpreter, httpUtils, testUtil % "test")


lazy val flinkProcessManager = (project in engine("flink/management")).
  configs(IntegrationTest).
  settings(commonSettings).
  settings(Defaults.itSettings).
  settings(assemblySettings("nussknacker-flink-manager.jar", includeScala = false): _*).
  settings(
    name := "nussknacker-flink-manager",
    Keys.test in IntegrationTest := (Keys.test in IntegrationTest).dependsOn(
      (assembly in Compile) in flinkManagementSample,
      (assembly in Compile) in managementJavaSample
    ).value,

    //flink cannot run tests and deployment concurrently
    parallelExecution in IntegrationTest := false,
    libraryDependencies ++= {
      Seq(
        "org.typelevel" %% "cats-core" % catsV % "provided",
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % flinkScope
          excludeAll(
          ExclusionRule("log4j", "log4j"),
          ExclusionRule("org.slf4j", "slf4j-log4j12")
        ),
        "com.whisk" %% "docker-testkit-scalatest" % "0.9.0" % "it,test",
        "com.whisk" %% "docker-testkit-impl-spotify" % "0.9.0" % "it,test"
      )
    }
  ).dependsOn(interpreter % "provided",
    api % "provided",
    queryableState,
    httpUtils % "provided",
    kafkaTestUtil % "it,test")

lazy val standaloneSample = (project in engine("standalone/engine/sample")).
  settings(commonSettings).
  settings(assemblySampleSettings("standaloneSample.jar"): _*).
  settings(
    name := "nussknacker-standalone-sample"
  ).dependsOn(util, standaloneApi, standaloneUtil)


lazy val flinkManagementSample = (project in engine("flink/management/sample")).
  settings(commonSettings).
  settings(assemblySampleSettings("managementSample.jar"): _*).
  settings(
    name := "nussknacker-management-sample"  ,
    libraryDependencies ++= {
      Seq(
        "com.cronutils" % "cron-utils" % cronParserV,
        "javax.validation" % "validation-api" % javaxValidationApiV,
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" %% "flink-queryable-state-runtime" % flinkV % "test",
        "org.apache.flink" %% "flink-runtime" % flinkV % "compile" classifier "tests"
      )
    }
  ).
  // depends on interpreter because of SampleNodeAdditionalInfoProvider which takes NodeData as a parameter
  dependsOn(kafkaFlinkUtil, flinkModelUtil, interpreter, process % "runtime,test", flinkTestUtil % "test", kafkaTestUtil % "test")

lazy val managementJavaSample = (project in engine("flink/management/java_sample")).
  settings(commonSettings).
  settings(assemblySampleSettings("managementJavaSample.jar"): _*).
  settings(
    name := "nussknacker-management-java-sample"  ,
    libraryDependencies ++= {
      Seq(
        "org.scala-lang.modules" %% "scala-java8-compat" % scalaCompatV,
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided"
      )
    }
  ).dependsOn(flinkUtil, process % "runtime")

lazy val demo = (project in engine("demo")).
  settings(commonSettings).
  settings(forkSettings). // without this there are some classloading issues
  settings(assemblySampleSettings("demoModel.jar"): _*).
  settings(publishAssemblySettings: _*).
  settings(
    name := "nussknacker-demo",
    libraryDependencies ++= {
      Seq(
        "com.fasterxml.jackson.core" % "jackson-databind" % jacksonV,
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" %% "flink-statebackend-rocksdb" % flinkV % "provided",
        "org.scalatest" %% "scalatest" % scalaTestV % "test",
        "org.scala-lang.modules" %% "scala-java8-compat" % scalaCompatV,
        "ch.qos.logback" % "logback-classic" % logbackV % "test"
      )
    }
  )
  .dependsOn(process % "runtime,test", kafkaFlinkUtil, flinkModelUtil, kafkaTestUtil % "test", flinkTestUtil % "test")


lazy val generic = (project in engine("flink/generic")).
  settings(commonSettings).
  settings(assemblySampleSettings("genericModel.jar"): _*).
  settings(publishAssemblySettings: _*).
  settings(
    name := "nussknacker-generic-model",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" %% "flink-statebackend-rocksdb" % flinkV % "provided"
      )
    })
  .dependsOn(process % "runtime,test", avroFlinkUtil, flinkModelUtil, flinkTestUtil % "test", kafkaTestUtil % "test")

lazy val process = (project in engine("flink/process")).
  settings(commonSettings).
  settings(forkSettings). // without this there are some classloading issues
  settings(
    name := "nussknacker-process",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" %% "flink-runtime" % flinkV % "provided",
        "org.apache.flink" %% "flink-statebackend-rocksdb" % flinkV % "provided"
      )
    }
  ).dependsOn(flinkUtil, interpreter, kafka % "test", kafkaTestUtil % "test", kafkaFlinkUtil % "test", flinkTestUtil % "test")

lazy val interpreter = (project in engine("interpreter")).
  settings(commonSettings).
  settings(
    name := "nussknacker-interpreter",
    libraryDependencies ++= {
      Seq(
        "org.springframework" % "spring-expression" % springV,
        //needed by scala-compiler for spring-expression...
        "com.google.code.findbugs" % "jsr305" % "3.0.2",
        "javax.validation" % "validation-api" % javaxValidationApiV,
        "org.hsqldb" % "hsqldb" % hsqldbV,
        "org.scala-lang.modules" %% "scala-java8-compat" % scalaCompatV,
        "org.apache.avro" % "avro" % avroV % "test",
        "org.scalacheck" %% "scalacheck" % scalaCheckV % "test",
        "com.cronutils" % "cron-utils" % cronParserV % "test"
      )
    }
  ).
  dependsOn(util, testUtil % "test")

lazy val benchmarks = (project in engine("benchmarks")).
  settings(commonSettings).
  enablePlugins(JmhPlugin).
  settings(
    name := "nussknacker-benchmarks",
  ).dependsOn(interpreter)


lazy val kafka = (project in engine("kafka")).
  settings(commonSettings).
  settings(
    name := "nussknacker-kafka",
    libraryDependencies ++= {
      Seq(
        "javax.validation" % "validation-api" % javaxValidationApiV,
        "org.apache.kafka" % "kafka-clients" % kafkaV,
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).
  dependsOn(util)


lazy val avroFlinkUtil = (project in engine("flink/avro-util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-avro-flink-util",
    libraryDependencies ++= {
      Seq(
        "io.confluent" % "kafka-avro-serializer" % confluentV  excludeAll (
          ExclusionRule("log4j", "log4j"),
          ExclusionRule("org.slf4j", "slf4j-log4j12")
        ),
        // it is workaround for missing VerifiableProperties class - see https://github.com/confluentinc/schema-registry/issues/553
        "org.apache.kafka" %% "kafka" % kafkaV % "provided" excludeAll (
          ExclusionRule("log4j", "log4j"),
          ExclusionRule("org.slf4j", "slf4j-log4j12")
        ),
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" % "flink-avro" % flinkV,
        "org.apache.flink" %% s"flink-connector-kafka" % flinkV % "test",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  )
  .dependsOn(kafkaFlinkUtil, process, kafkaTestUtil % "test", flinkTestUtil % "test")

lazy val kafkaFlinkUtil = (project in engine("flink/kafka-util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-kafka-flink-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% s"flink-connector-kafka" % flinkV,
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).
  dependsOn(kafka, flinkUtil, kafkaTestUtil % "test", flinkTestUtil % "test")

lazy val kafkaTestUtil = (project in engine("kafka-test-util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-kafka-test-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.kafka" %% "kafka" % kafkaV  excludeAll (
          ExclusionRule("log4j", "log4j"),
          ExclusionRule("org.slf4j", "slf4j-log4j12")
        ),
        "org.slf4j" % "log4j-over-slf4j" % slf4jV
      )
    }
  )
  .dependsOn(testUtil, kafka)

lazy val util = (project in engine("util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-util",
    libraryDependencies ++= {
      Seq(
        "org.springframework" % "spring-core" % springV,
        "com.github.ben-manes.caffeine" % "caffeine" % caffeineCacheV,
        "org.scala-lang.modules" %% "scala-java8-compat" % scalaCompatV,
        "com.iheart" %% "ficus" % ficusV,
        "io.circe" %% "circe-java8" % circeV,
        "org.apache.avro" % "avro" % avroV % Optional
      )
    }
  ).
  enablePlugins(BuildInfoPlugin).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version),
    buildInfoKeys ++= Seq[BuildInfoKey](
      "buildTime" -> java.time.LocalDateTime.now().toString,
      "gitCommit" -> git.gitHeadCommit.value.getOrElse("")
    ),
    buildInfoPackage := "pl.touk.nussknacker.engine.version",
    buildInfoOptions ++= Seq(BuildInfoOption.ToMap)
  )
  .dependsOn(api, testUtil % "test")

lazy val testUtil = (project in engine("test-util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-test-util",
    libraryDependencies ++= {
      Seq(
        "org.scalatest" %% "scalatest" % scalaTestV,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "ch.qos.logback" % "logback-classic" % logbackV
      )
    }
  )

lazy val flinkUtil = (project in engine("flink/util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-flink-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" % "flink-metrics-dropwizard" % flinkV,
        "com.clearspring.analytics" % "stream" % "2.9.8" excludeAll (
          //It is used only in QDigest which we don't use, while it's >20MB in size...
            ExclusionRule("it.unimi.dsi", "fastutil"),
         )
      )
    }
  ).dependsOn(util, flinkApi)

lazy val flinkModelUtil = (project in engine("flink/model-util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-model-flink-util",
    libraryDependencies ++= {
      Seq(
        "javax.validation" % "validation-api" % javaxValidationApiV,
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided"
      )
    }
  ).dependsOn(flinkUtil, flinkTestUtil % "test", process % "test")

lazy val flinkTestUtil = (project in engine("flink/test-util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-flink-test-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        //intellij has some problems with provided...
        "org.apache.flink" %% "flink-statebackend-rocksdb" % flinkV,
        "org.apache.flink" %% "flink-test-utils" % flinkV,
        "org.apache.flink" %% "flink-runtime" % flinkV % "compile" classifier "tests",
        "org.apache.flink" % "flink-metrics-dropwizard" % flinkV
      )
    }
  ).dependsOn(testUtil, queryableState)

lazy val standaloneUtil = (project in engine("standalone/util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-standalone-util",
    libraryDependencies ++= {
      Seq(

        "io.dropwizard.metrics5" % "metrics-core" % dropWizardV,
        //akka-http is only for StandaloneRequestResponseLogger
        "com.typesafe.akka" %% "akka-http" % akkaHttpV % "provided" force(),
        "com.typesafe.akka" %% "akka-stream" % akkaV % "provided" force()
      )
    }
  ).dependsOn(util, standaloneApi, testUtil % "test")


lazy val standaloneApi = (project in engine("standalone/api")).
  settings(commonSettings).
  settings(
    name := "nussknacker-standalone-api"
  ).dependsOn(api)


lazy val api = (project in engine("api")).
  settings(commonSettings).
  settings(
    name := "nussknacker-api",
    libraryDependencies ++= {
      Seq(
        "io.argonaut" %% "argonaut" % argonautV,
        "io.circe" %% "circe-parser" % circeV,
        "io.circe" %% "circe-generic" % circeV,
        "io.circe" %% "circe-generic-extras" % circeV,
        "io.circe" %% "circe-java8" % circeV,
        "com.iheart" %% "ficus" % ficusV,
        "org.apache.commons" % "commons-lang3" % commonsLangV,
        "org.apache.commons" % "commons-text" % commonsTextV,
        "org.typelevel" %% "cats-core" % catsV,
        "org.typelevel" %% "cats-effect" % "1.1.0",
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "com.typesafe" % "config" % configV
      )
    }
  ).dependsOn(testUtil % "test")

lazy val security = (project in engine("security")).
  settings(commonSettings).
  settings(
    name := "nussknacker-security",
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka" %% "akka-http" % akkaHttpV force(),
        "com.typesafe.akka" %% "akka-stream" % akkaV force(),
        "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test" force(),
        "de.heikoseeberger" %% "akka-http-circe" % akkaHttpCirceV,
        "com.typesafe.akka" %% "akka-stream" % akkaV force(),
        "com.typesafe" % "config" % configV ,
        "org.mindrot" % "jbcrypt" % jbcryptV,
        //Packages below are only for plugin providers purpose
        "io.circe" %% "circe-core" % circeV,
        "com.pauldijou" %% "jwt-circe" % jwtCirceV,
        "com.typesafe.akka" %% "akka-testkit" % akkaV % "test" force(),
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV
      )
    }
  )
  .dependsOn(util, httpUtils, testUtil % "test")

lazy val flinkApi = (project in engine("flink/api")).
  settings(commonSettings).
  settings(
    name := "nussknacker-flink-api",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-java" % flinkV % "provided",
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided"
      )
    }
  ).dependsOn(api)

lazy val processReports = (project in engine("processReports")).
  settings(commonSettings).
  settings(
    name := "nussknacker-process-reports",
    libraryDependencies ++= {
      Seq(
        "com.typesafe" % "config" % "1.3.0",
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "com.iheart" %% "ficus" % ficusV
      )
    }
  ).dependsOn(httpUtils, testUtil % "test")

lazy val httpUtils = (project in engine("httpUtils")).
  settings(commonSettings).
  settings(
    name := "nussknacker-http-utils",
    libraryDependencies ++= {
      val sttpV = "2.2.3"
      Seq(
        //we force circe version here, because sttp has 0.12.1 for scala 2.12, we don't want it ATM
        "io.circe" %% "circe-core" % circeV force(),
        "io.circe" %% "circe-parser" % circeV force(),
        "org.dispatchhttp" %% "dispatch-core" % dispatchV,
        "org.scala-lang.modules" %% "scala-parser-combinators" % scalaParsersV, // scalaxb deps
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "com.softwaremill.sttp.client" %% "core" % sttpV,
        "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % sttpV,
        "com.softwaremill.sttp.client" %% "circe" % sttpV
      )
    }
  ).dependsOn(api, testUtil % "test")

//osobny modul bo chcemy uzyc klienta do testowania w flinkManagementSample
lazy val queryableState = (project in engine("queryableState")).
  settings(commonSettings).
  settings(
    name := "nussknacker-queryable-state",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.scala-lang.modules" %% "scala-java8-compat" % scalaCompatV,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "com.typesafe" % "config" % configV
      )
    }
  ).dependsOn(api)



lazy val buildUi = taskKey[Unit]("builds ui")

def runNpm(command: String, errorMessage: String, outputPath: File): Unit = {
  import sys.process.Process
  val path = Path.apply("ui/client").asFile
  println("Using path: " + path.getAbsolutePath)
  val result = Process(s"npm $command", path, "OUTPUT_PATH" -> outputPath.absolutePath)!;
  if (result != 0) throw new RuntimeException(errorMessage)
}

lazy val restmodel = (project in file("ui/restmodel"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-restmodel",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-java8" % circeV
    )
  )
  //interpreter needed for evaluatedparam etc
  .dependsOn(api, interpreter, testUtil % "test")

lazy val listenerApi = (project in file("ui/listener-api"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-listener-api",
  )
  //security needed for LoggedUser etc
  .dependsOn(restmodel, util, security, testUtil % "test")

lazy val ui = (project in file("ui/server"))
  .configs(SlowTests)
  .settings(slowTestsSettings)
  .settings(commonSettings)
  .settings(assemblySettings("nussknacker-ui-assembly.jar", includeScala = includeFlinkAndScala): _*)
  .settings(publishAssemblySettings: _*)
  .settings(
    name := "nussknacker-ui",
    buildUi :=  {
      runNpm("run build", "Client build failed", (crossTarget in compile).value)
    },
    parallelExecution in ThisBuild := false,
    Keys.test in SlowTests := (Keys.test in SlowTests).dependsOn(
      //TODO: maybe here there should be engine/demo??
      (assembly in Compile) in flinkManagementSample
    ).value,
    Keys.test in Test := (Keys.test in Test).dependsOn(
      //TODO: maybe here there should be engine/demo??
      (assembly in Compile) in flinkManagementSample
    ).value,
    /*
      We depend on buildUi in packageBin and assembly to be make sure fe files will be included in jar and fajar
      We abuse sbt a little bit, but we don't want to put webpack in generate resources phase, as it's long and it would
      make compilation v. long. This is not too nice, but so far only alternative is to put buildUi outside sbt and
      use bash to control when it's done - and this can lead to bugs and edge cases (release, dist/docker, dist/tgz, assembly...)
     */
    packageBin in Compile := (packageBin in Compile).dependsOn(
      buildUi
    ).value,
    assembly in ThisScope := (assembly in ThisScope).dependsOn(
      buildUi
    ).value,
    libraryDependencies ++= {
      Seq(
        // Force akka-http and akka-stream versions to avoid bumping by akka-http-circe.
        "com.typesafe.akka" %% "akka-http" % akkaHttpV force(),
        "com.typesafe.akka" %% "akka-stream" % akkaV force(),
        "de.heikoseeberger" %% "akka-http-circe" % akkaHttpCirceV,
        "ch.qos.logback" % "logback-core" % logbackV,
        "ch.qos.logback" % "logback-classic" % logbackV,
        "org.slf4j" % "log4j-over-slf4j" % slf4jV,
        "com.carrotsearch" % "java-sizeof" % "0.0.5",

        //It's needed by flinkProcessManager which has disabled includingScala
        "org.scala-lang" % "scala-compiler" % scalaVersion.value,
        "org.scala-lang" % "scala-reflect" % scalaVersion.value,

        "com.typesafe.slick" %% "slick" % slickV,
        "com.typesafe.slick" %% "slick-hikaricp" % slickV,
        "org.hsqldb" % "hsqldb" % hsqldbV,
        "org.postgresql" % "postgresql" % postgresV,
        "org.flywaydb" % "flyway-core" % flywayV,
        "org.apache.xmlgraphics" % "fop" % "2.3",
        "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test" force(),
        "com.typesafe.akka" %% "akka-testkit" % akkaV % "test" force(),

        "com.typesafe.slick" %% "slick-testkit" % slickV % "test",
        "com.whisk" %% "docker-testkit-scalatest" % "0.9.8" % "test",
        "com.whisk" %% "docker-testkit-impl-spotify" % "0.9.8" % "test"
      )
    }
  )
  .dependsOn(interpreter, processReports, security, listenerApi,
    testUtil % "test",
    //TODO: this is unfortunatelly needed to run without too much hassle in Intellij...
    //provided dependency of kafka is workaround for Idea, which is not able to handle test scope on module dependency
    //otherwise it is (wrongly) added to classpath when running UI from Idea
    flinkProcessManager % "provided" ,
    kafka % "provided",
    engineStandalone % "provided"
  )


lazy val root = (project in file("."))
  .aggregate(
    // TODO: get rid of this duplication
    engineStandalone, standaloneApp, flinkProcessManager, standaloneSample, flinkManagementSample, managementJavaSample, demo, generic,
    process, interpreter, benchmarks, kafka, avroFlinkUtil, kafkaFlinkUtil, kafkaTestUtil, util, testUtil, flinkUtil, flinkModelUtil,
    flinkTestUtil, standaloneUtil, standaloneApi, api, security, flinkApi, processReports, httpUtils, queryableState,
    restmodel, listenerApi, ui,
  )
  .settings(commonSettings)
  .settings(
    // crossScalaVersions must be set to Nil on the aggregating project
    releaseCrossBuild := true,
    skip in publish := true,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      ReleaseStep { st: State =>
        if (!st.get(ReleaseKeys.skipTests).getOrElse(false)) {
          releaseStepCommandAndRemaining("+test")(st)
        } else {
          st
        }
      },
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("+publishSigned"),
      releaseStepCommand("dist/universal:packageZipTarball"),
      releaseStepCommand("dist/docker:publish"),
      releaseStepCommand("sonatypeBundleRelease"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )

addCommandAlias("assemblySamples", ";flinkManagementSample/assembly;standaloneSample/assembly;demo/assembly;generic/assembly")
addCommandAlias("assemblyEngines", ";flinkProcessManager/assembly;engineStandalone/assembly")
