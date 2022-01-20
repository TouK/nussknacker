import com.typesafe.sbt.packager.SettingsHelper
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerUsername
import pl.project13.scala.sbt.JmhPlugin
import pl.project13.scala.sbt.JmhPlugin._
import scala.sys.process._
import sbt.Keys._
import sbt.{Def, _}
import sbtassembly.AssemblyPlugin.autoImport.assembly
import sbtassembly.MergeStrategy
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

import scala.language.postfixOps
import scala.util.Try
import scala.xml.Elem
import scala.xml.transform.{RewriteRule, RuleTransformer}

// Warning: Flink doesn't work correctly with 2.12.11
// Warning: 2.12.13 + crossVersion break sbt-scoverage: https://github.com/scoverage/sbt-scoverage/issues/319
val scala212 = "2.12.10"
lazy val supportedScalaVersions = List(scala212)

// Silencer must be compatible with exact scala version - see compatibility matrix: https://search.maven.org/search?q=silencer-plugin
// Silencer 1.7.x require Scala 2.12.11 (see warning above)
// Silencer (and all '@silent' annotations) can be removed after we can upgrade to 2.12.13...
// https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
val silencerV_2_12 = "1.6.0"
val silencerV = "1.7.0"

//TODO: replace configuration by system properties with configuration via environment after removing travis scripts
//then we can change names to snake case, for "normal" env variables
def propOrEnv(name: String, default: String): String = propOrEnv(name).getOrElse(default)
def propOrEnv(name: String): Option[String] = Option(System.getProperty(name)).orElse(sys.env.get(name))


//by default we include flink and scala, we want to be able to disable this behaviour for performance reasons
val includeFlinkAndScala = propOrEnv("includeFlinkAndScala", "true").toBoolean

val flinkScope = if (includeFlinkAndScala) "compile" else "provided"
val nexusUrlFromProps = propOrEnv("nexusUrl")
//TODO: this is pretty clunky, but works so far for our case...
val nexusHostFromProps = nexusUrlFromProps.map(_.replaceAll("http[s]?://", "").replaceAll("[:/].*", ""))

//Docker release configuration
val dockerTagName = propOrEnv("dockerTagName")
val dockerPort = propOrEnv("dockerPort", "8080").toInt
val dockerUserName = Option(propOrEnv("dockerUserName", "touk"))
val dockerPackageName = propOrEnv("dockerPackageName", "nussknacker")
val dockerUpLatestFromProp = propOrEnv("dockerUpLatest").flatMap(p => Try(p.toBoolean).toOption)
val addDevModel = propOrEnv("addDevModel", "false").toBoolean

val requestResponseManagementPort = propOrEnv("requestResponseManagementPort", "8070").toInt
val requestResponseProcessesPort = propOrEnv("requestResponseProcessesPort", "8080").toInt
val requestResponseDockerPackageName = propOrEnv("requestResponseDockerPackageName", "nussknacker-request-response-app")

val liteEngineKafkaRuntimeDockerPackageName = propOrEnv("liteEngineKafkaRuntimeDockerPackageName", "nussknacker-lite-kafka-runtime")

// `publishArtifact := false` should be enough to keep sbt from publishing root module,
// unfortunately it does not work, so we resort to hack by publishing root module to Resolver.defaultLocal
//publishArtifact := false
publishTo := Some(Resolver.defaultLocal)
crossScalaVersions := Nil

ThisBuild / isSnapshot := version(_ contains "-SNAPSHOT").value

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
  Test / publishArtifact := false,
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
    host, propOrEnv("nexusUser", "touk"), propOrEnv("nexusPassword", null))
    // otherwise ~/.sbt/1.0/sonatype.sbt will be used
  ).toSeq
)


def modelMergeStrategy: String => MergeStrategy = {
  case PathList(ps@_*) if ps.last == "module-info.class" => MergeStrategy.discard //TODO: we don't handle JDK9 modules well
  case PathList(ps@_*) if ps.last == "NumberUtils.class" => MergeStrategy.first //TODO: shade Spring EL?
  case PathList("org", "apache", "commons", "logging", _ @ _*) => MergeStrategy.first //TODO: shade Spring EL?
  case PathList(ps@_*) if ps.last == "io.netty.versions.properties" => MergeStrategy.first //Netty has buildTime here, which is different for different modules :/
  case x => MergeStrategy.defaultMergeStrategy(x)
}

def uiMergeStrategy: String => MergeStrategy = {
  case PathList(ps@_*) if ps.last == "module-info.class" => MergeStrategy.discard
  case PathList(ps@_*) if ps.last == "NumberUtils.class" => MergeStrategy.first //TODO: shade Spring EL?
  case PathList("org", "apache", "commons", "logging", _ @ _*) => MergeStrategy.first //TODO: shade Spring EL?
  case PathList(ps@_*) if ps.last == "io.netty.versions.properties" => MergeStrategy.first //Netty has buildTime here, which is different for different modules :/
  case PathList("com", "sun", "el", _ @ _*) => MergeStrategy.first //Some legacy batik stuff
  case PathList("org", "w3c", "dom", "events", _ @ _*) => MergeStrategy.first //Some legacy batik stuff
  case x => MergeStrategy.defaultMergeStrategy(x)
}

def requestResponseMergeStrategy: String => MergeStrategy = {
  case PathList(ps@_*) if ps.last == "NumberUtils.class" => MergeStrategy.first //TODO: shade Spring EL?
  case PathList("org", "apache", "commons", "logging", _ @ _*) => MergeStrategy.first //TODO: shade Spring EL?
  case PathList(ps@_*) if ps.last == "io.netty.versions.properties" => MergeStrategy.first //Netty has buildTime here, which is different for different modules :/
  case x => MergeStrategy.defaultMergeStrategy(x)
}

val scalaTestReports = Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/surefire-reports", "-oFGD")

lazy val SlowTests = config("slow") extend Test

val slowTestsSettings =
  inConfig(SlowTests)(Defaults.testTasks) ++ Seq(
    SlowTests / testOptions := Seq(
      Tests.Argument(TestFrameworks.ScalaTest, "-n", "org.scalatest.tags.Slow"),
      scalaTestReports
    )
  )

val ignoreSlowTests = Tests.Argument(TestFrameworks.ScalaTest, "-l", "org.scalatest.tags.Slow")

// This scope is for purpose of running integration tests that need external deps to work (like working k8s client setup)
lazy val ExternalDepsTests = config("externaldeps") extend Test

val externalDepsTestsSettings =
  inConfig(ExternalDepsTests)(Defaults.testTasks) ++ Seq(
    ExternalDepsTests / testOptions := Seq(
      // We use ready "Network" tag to avoid having some extra module only with this class
      Tests.Argument(TestFrameworks.ScalaTest, "-n", "org.scalatest.tags.Network"),
      scalaTestReports
    )
  )

val ignoreExternalDepsTests = Tests.Argument(TestFrameworks.ScalaTest, "-l", "org.scalatest.tags.Network")

def forScalaVersion[T](version: String, default: T, specific: ((Int, Int), T)*): T = {
  CrossVersion.partialVersion(version).flatMap { case (k, v) =>
    specific.toMap.get(k.toInt, v.toInt)
  }.getOrElse(default)
}

lazy val commonSettings =
  publishSettings ++
    Seq(
      assembly / test := {},
      licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
      crossScalaVersions := supportedScalaVersions,
      scalaVersion  := scala212,
      resolvers ++= Seq(
        "confluent" at "https://packages.confluent.io/maven"
      ),
      // We ignore k8s tests to keep development setup low-dependency
      Test / testOptions ++= Seq(scalaTestReports, ignoreSlowTests, ignoreExternalDepsTests),
      addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),
      addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
      // We can't use addCompilerPlugin because it not support usage of scalaVersion.value
      libraryDependencies += compilerPlugin("com.github.ghik" % "silencer-plugin" % forScalaVersion(scalaVersion.value,
        silencerV, (2, 12) -> silencerV_2_12) cross CrossVersion.full),
      scalacOptions := Seq(
        "-unchecked",
        "-deprecation",
        "-encoding", "utf8",
        "-Xfatal-warnings",
        "-feature",
        "-language:postfixOps",
        "-language:existentials",
        "-Ypartial-unification",
        // We use jdk standard lib classes from java 11, but Scala 2.12 does not support target > 8 and
        // -release option has no influence on class version so we at least setup target to 8 and check java version
        // at the begining of our Apps
        "-target:jvm-1.8",
        "-release",
        "11"
      ),
      javacOptions := Seq(
        "-Xlint:deprecation",
        "-Xlint:unchecked",
        // Using --release flag (available only on jdk >= 9) instead of -source -target to avoid usage of api from newer java version
        "--release",
        "11",
        //we use it e.g. to provide consistent behaviour wrt extracting parameter names from scala and java
        "-parameters"
      ),
      coverageMinimum := 60,
      coverageFailOnMinimum := false,
      //problem with scaladoc of api: https://github.com/scala/bug/issues/10134
      Compile /doc / scalacOptions -= "-Xfatal-warnings",
      libraryDependencies ++= Seq(
        "com.github.ghik" % "silencer-lib" % (CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((2, 12)) => silencerV_2_12
          case _             => silencerV
        }) % Provided cross CrossVersion.full
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
        "org.apache.commons" % "commons-lang3" % commonsLangV,

        "io.circe" %% "circe-core" % circeV,
        "io.circe" %% "circe-parser" % circeV,

        // Force akka-http and akka-stream versions to avoid bumping by akka-http-circe.
        "com.typesafe.akka" %% "akka-http" % akkaHttpV,
        "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV,
        "com.typesafe.akka" %% "akka-stream" % akkaV,
        "com.typesafe.akka" %% "akka-testkit" % akkaV,

        //Our main kafka dependencies are Confluent (for avro) and Flink (Kafka connector)
        "org.apache.kafka" % "kafka-clients" % kafkaV,
        "org.apache.kafka" %% "kafka" % kafkaV,

        "io.netty" % "netty-handler" % nettyV,
        "io.netty" % "netty-codec" % nettyV,
        "io.netty" % "netty-transport-native-epoll" % nettyV,

        // Jackson is used by openapi and jwks-rsa
        "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonV,
        "com.fasterxml.jackson.core" % "jackson-core" % jacksonV,
        "com.fasterxml.jackson.core" % "jackson-databind" % jacksonV,
       )
    )

val flinkV = "1.14.0"
val avroV = "1.9.2" // for java time logical types conversions purpose
//we should use max(version used by confluent, version used by flink), https://docs.confluent.io/platform/current/installation/versions-interoperability.html - confluent version reference
//however, we stick to 2.4.1, as it's last version supported by scala 2.11 (we use kafka server in tests...)
val kafkaV = "2.4.1"
val kafkaServerV = "2.4.1"
val springV = "5.1.19.RELEASE"
val scalaTestV = "3.0.8"
val scalaCheckV = "1.14.0"
val logbackV = "1.1.3"
val circeV = "0.14.1"
val jwtCirceV = "9.0.1"
val jacksonV = "2.11.3"
val catsV = "2.6.1"
val scalaParsersV = "1.0.4"
val everitSchemaV = "1.13.0"
val slf4jV = "1.7.30"
val scalaLoggingV = "3.9.2"
val scalaCompatV = "0.9.1"
val ficusV = "1.4.7"
val configV = "1.4.1"
val commonsLangV = "3.3.2"
val commonsTextV = "1.8"
val commonsIOV = "2.4"
//we want to use 5.x for lite metrics to have tags, however dropwizard development kind of freezed. Maybe we should consider micrometer?
//In Flink metrics we use bundled dropwizard metrics v. 3.x
val dropWizardV = "5.0.0-rc3"
val scalaCollectionsCompatV = "2.3.2"
val testcontainersScalaV = "0.39.12"
val nettyV = "4.1.48.Final"

val akkaV = "2.6.17"
val akkaHttpV = "10.2.7"
val akkaManagementV = "1.1.2"
val akkaHttpCirceV = "1.38.2"
val slickV = "3.3.3"
val hsqldbV = "2.5.1"
val postgresV = "42.2.19"
val flywayV = "6.3.3"
val confluentV = "5.5.4"
val jbcryptV = "0.4"
val cronParserV = "9.1.3"
val javaxValidationApiV = "2.0.1.Final"
val caffeineCacheV = "2.8.8"
val sttpV = "2.2.9"
//we use legacy version because this one supports Scala 2.12
val monocleV = "2.1.0"

lazy val commonDockerSettings = {
  Seq(
    dockerBaseImage := "openjdk:11-jdk-slim",
    dockerUsername := dockerUserName,
    dockerUpdateLatest := dockerUpLatestFromProp.getOrElse(!isSnapshot.value),
    dockerAliases := {
      //https://docs.docker.com/engine/reference/commandline/tag/#extended-description
      def sanitize(str: String) = str.replaceAll("[^a-zA-Z0-9._-]", "_")
      val alias = dockerAlias.value

      val updateLatest = if (dockerUpdateLatest.value) Some("latest") else None
      val dockerVersion = Some(version.value)
      //TODO: handle it more nicely, checkout actions in CI are not checking out actual branch
      //other option would be to reset source branch to checkout out commit
      val currentBranch = sys.env.getOrElse("GIT_SOURCE_BRANCH", git.gitCurrentBranch.value)
      val latestBranch = Some(currentBranch + "-latest")

      List(dockerVersion, updateLatest, latestBranch, dockerTagName)
        .flatten
        .map(tag => alias.withTag(Some(sanitize(tag))))
        .distinct
    }
  )
}

lazy val distDockerSettings = {
  val nussknackerDir = "/opt/nussknacker"

  commonDockerSettings ++ Seq(
    //we use openjdk:11-jdk for designer because openjdk:11-jdk-slim lacks /usr/local/openjdk-11/lib/libfontmanager.so file necessary during pdf export
    dockerBaseImage := "openjdk:11-jdk",
    dockerEntrypoint := Seq(s"$nussknackerDir/bin/nussknacker-entrypoint.sh"),
    dockerExposedPorts := Seq(dockerPort),
    dockerEnvVars := Map(
      "HTTP_PORT" -> dockerPort.toString
    ),
    packageName := dockerPackageName,
    dockerLabels := Map(
      "version" -> version.value,
      "scala" -> scalaVersion.value,
      "flink" -> flinkV
    ),
    dockerExposedVolumes := Seq(s"$nussknackerDir/storage", s"$nussknackerDir/data"),
    Docker / defaultLinuxInstallLocation := nussknackerDir
  )
}

val publishAssemblySettings = List(
  Compile /assembly / artifact := {
    val art = (Compile / assembly / artifact).value
    art.withClassifier(Some("assembly"))
  }, addArtifact(Compile /assembly / artifact, assembly)
)

def assemblySettings(assemblyName: String, includeScala: Boolean): List[Def.SettingsDefinition] = List(
  assembly / assemblyJarName := assemblyName,
  assembly / assemblyOption := (assembly / assemblyOption).value.copy(includeScala = includeScala, level = Level.Info),
  assembly / assemblyMergeStrategy := modelMergeStrategy,
  assembly / test := {},
  //For some reason problem described in https://github.com/sbt/sbt-assembly/issues/295 appears, workaround also works...
  assembly / fullClasspath := {
    val cp = (assembly / fullClasspath).value
    val providedDependencies = update.map (f => f.select(configurationFilter("provided"))).value

    cp filter { f =>
      ! providedDependencies.contains(f.data)
    }
  }
)

def assemblyNoScala(assemblyName: String): List[Def.SettingsDefinition]
  = assemblySettings(assemblyName, includeScala = false)


lazy val componentArtifacts = taskKey[List[(File, String)]]("component artifacts")
componentArtifacts := {
  List(
    (flinkBaseComponents / assembly).value -> "components/flink/flinkBase.jar",
    (flinkKafkaComponents / assembly).value -> "components/flink/flinkKafka.jar",
    (liteBaseComponents / assembly).value -> "components/lite/liteBase.jar",
    (liteKafkaComponents / assembly).value -> "components/lite/liteKafka.jar",
    (liteRequestResponseComponents / assembly).value -> "components/lite/liteRequestResponse.jar",
    (openapiComponents / assembly).value -> "components/common/openapi.jar",
    (sqlComponents / assembly).value -> "components/common/sql.jar"
  )
}

lazy val modelArtifacts = taskKey[List[(File, String)]]("model artifacts")
modelArtifacts := {
  List(
    (defaultModel / assembly).value-> "model/defaultModel.jar",
    (flinkExecutor / assembly).value-> "model/flinkExecutor.jar",
  )
}

lazy val devModelArtifacts = taskKey[List[(File, String)]]("dev model artifacts")
devModelArtifacts := {
  modelArtifacts.value ++ List(
    (flinkDevModel / assembly).value -> "model/devModel.jar"
  )
}


lazy val dist = sbt.Project("dist", file("nussknacker-dist"))
  .settings(commonSettings)
  .enablePlugins(SbtNativePackager, JavaServerAppPackaging)
  .settings(
    Universal / packageName := ("nussknacker" + "-" + version.value),
    Universal / mappings ++= (Seq(
      (flinkDeploymentManager / assembly).value -> "managers/nussknacker-flink-manager.jar",
      (requestResponseRuntime / assembly).value -> "managers/nussknacker-request-response-manager.jar",
      (liteK8sDeploymentManager / assembly).value -> "managers/lite-k8s-manager.jar",
      (liteEmbeddedDeploymentManager / assembly).value -> "managers/lite-embedded-manager.jar")
      ++ (root / componentArtifacts).value
      ++ (if (addDevModel) (root / devModelArtifacts).value: @sbtUnchecked else (root / modelArtifacts).value: @sbtUnchecked)
    ),
    Universal / packageZipTarball / mappings := {
      val universalMappings = (Universal / mappings).value
      //we don't want docker-* stuff in .tgz
      universalMappings filterNot { case (file, _) =>
        file.getName.startsWith("docker-") ||file.getName.contains("entrypoint.sh")
      }
    },
    publishArtifact := false,
    SettingsHelper.makeDeploymentSettings(Universal, Universal / packageZipTarball, "tgz")
  )
  .settings(distDockerSettings)
  .dependsOn(ui)

def engine(name: String) = file(s"engine/$name")

def flink(name: String) = engine(s"flink/$name")

def lite(name: String) = engine(s"lite/$name")

def component(name: String) = file(s"components/$name")

def utils(name: String) = file(s"utils/$name")

def itSettings() = {
  Defaults.itSettings ++ Seq(IntegrationTest / testOptions += scalaTestReports)
}

lazy val requestResponseRuntime = (project in lite("request-response/runtime")).
  configs(IntegrationTest).
  settings(itSettings()).
  settings(commonSettings).
  settings(assemblyNoScala("nussknacker-request-response-manager.jar"): _*).
  settings(
    name := "nussknacker-request-response-runtime",
    IntegrationTest / Keys.test := (IntegrationTest / Keys.test).dependsOn(
      liteRequestResponseComponents / Compile / assembly,
      defaultModel / Compile / assembly,
    ).value,
  ).
  dependsOn(liteEngineRuntime, requestResponseApi, deploymentManagerApi, httpUtils % "provided", testUtil % "it,test", requestResponseUtil % "test", liteBaseComponents % "test")

lazy val requestResponseDockerSettings = {
  val workingDir = "/opt/nussknacker"

  commonDockerSettings ++ Seq(
    dockerEntrypoint := Seq(s"$workingDir/bin/nussknacker-request-response-entrypoint.sh"),
    dockerExposedPorts := Seq(
      requestResponseProcessesPort,
      requestResponseManagementPort
    ),
    dockerExposedVolumes := Seq(s"$workingDir/storage"),
    Docker / defaultLinuxInstallLocation := workingDir,
    packageName := requestResponseDockerPackageName,
    dockerLabels := Map(
      "version" -> version.value,
      "scala" -> scalaVersion.value,
    )
  )
}

lazy val requestResponseApp = (project in lite("request-response/app")).
  settings(commonSettings).
  settings(publishAssemblySettings: _*).
  enablePlugins(SbtNativePackager, JavaServerAppPackaging).
  settings(
    name := "nussknacker-request-response-app",
    assembly / assemblyOption := (assembly / assemblyOption).value.copy(includeScala = true, level = Level.Info),
    assembly / assemblyMergeStrategy := requestResponseMergeStrategy,
    libraryDependencies ++= {
      Seq(
        "de.heikoseeberger" %% "akka-http-circe" % akkaHttpCirceV,
        "com.typesafe.akka" %% "akka-http" % akkaHttpV,
        "com.typesafe.akka" %% "akka-stream" % akkaV,
        "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test",
        "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
        "com.typesafe.akka" %% "akka-slf4j" % akkaV,
        "ch.qos.logback" % "logback-classic" % logbackV
      )
    }
  ).
  settings(requestResponseDockerSettings).
  dependsOn(requestResponseRuntime, interpreter, testUtil % "test", requestResponseUtil % "test")


lazy val flinkDeploymentManager = (project in flink("management")).
  configs(IntegrationTest).
  settings(commonSettings).
  settings(itSettings()).
  settings(assemblyNoScala("nussknacker-flink-manager.jar"): _*).
  settings(
    name := "nussknacker-flink-manager",
    IntegrationTest / Keys.test := (IntegrationTest / Keys.test).dependsOn(
      flinkExecutor / Compile / assembly,
      flinkDevModel / Compile / assembly,
      flinkDevModelJava / Compile / assembly,
      flinkBaseComponents / Compile / assembly,
      flinkKafkaComponents / Compile /assembly
    ).value,
    //flink cannot run tests and deployment concurrently
    IntegrationTest / parallelExecution := false,
    libraryDependencies ++= {
      Seq(
        "org.typelevel" %% "cats-core" % catsV % "provided",
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % flinkScope
          excludeAll(
          ExclusionRule("log4j", "log4j"),
          ExclusionRule("org.slf4j", "slf4j-log4j12")
        ),
        "org.apache.flink" %% "flink-statebackend-rocksdb" % flinkV % flinkScope,
        "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % sttpV % "it,test",
        "com.dimafeng" %% "testcontainers-scala-scalatest" % testcontainersScalaV % "it,test",
        "com.dimafeng" %% "testcontainers-scala-kafka" % testcontainersScalaV % "it,test",
        //dependencies below are just for QueryableStateTest
        "org.apache.flink" % "flink-queryable-state-runtime" % flinkV % "test",
      )
    }
  ).dependsOn(deploymentManagerApi % "provided",
    api % "provided",
    httpUtils % "provided",
    kafkaTestUtil % "it,test")

lazy val flinkPeriodicDeploymentManager = (project in flink("management/periodic")).
  settings(commonSettings).
  settings(assemblyNoScala("nussknacker-flink-periodic-manager.jar"): _*).
  settings(
    name := "nussknacker-flink-periodic-manager",
    libraryDependencies ++= {
      Seq(
        "org.typelevel" %% "cats-core" % catsV % "provided",
        "com.typesafe.slick" %% "slick" % slickV % "provided",
        "com.typesafe.slick" %% "slick-hikaricp" % slickV % "provided, test",
        "org.hsqldb" % "hsqldb" % hsqldbV % "test",
        "org.flywaydb" % "flyway-core" % flywayV % "provided",
        "com.cronutils" % "cron-utils" % cronParserV,
        "com.typesafe.akka" %% "akka-actor" % akkaV,
      )
    }
  ).dependsOn(flinkDeploymentManager,
    deploymentManagerApi % "provided",
    api % "provided",
    httpUtils % "provided",
    testUtil % "test")

lazy val flinkDevModel = (project in flink("management/dev-model")).
  settings(commonSettings).
  settings(assemblyNoScala("devModel.jar"): _*).
  settings(
    name := "nussknacker-flink-dev-model"  ,
    libraryDependencies ++= {
      Seq(
        "com.cronutils" % "cron-utils" % cronParserV,
        "javax.validation" % "validation-api" % javaxValidationApiV,
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" % "flink-queryable-state-runtime" % flinkV % "test",
        "org.apache.flink" % "flink-runtime" % flinkV % "compile" classifier "tests"
      )
    }
  ).
  dependsOn(avroFlinkUtil,
    flinkUtil % Provided,
    //TODO: NodeAdditionalInfoProvider & ComponentExtractor should probably be moved to API?
    interpreter % "provided",
    flinkExecutor % "test",
    flinkTestUtil % "test",
    kafkaTestUtil % "test")

lazy val flinkDevModelJava = (project in flink("management/dev-model-java")).
  settings(commonSettings).
  settings(assemblyNoScala("devModelJava.jar"): _*).
  settings(
    name := "nussknacker-flink-dev-model-java",
    libraryDependencies ++= {
      Seq(
        "org.scala-lang.modules" %% "scala-java8-compat" % scalaCompatV,
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided"
      )
    }
  ).dependsOn(flinkUtil % "provided", flinkExecutor % "runtime")

lazy val flinkTests = (project in flink("tests")).
  settings(commonSettings).
  settings(
    name := "nussknacker-flink-tests",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" %% "flink-statebackend-rocksdb" % flinkV % "provided"
      )
    })
  .dependsOn(defaultModel % "test",
    flinkExecutor % "test",
    flinkKafkaComponents % "test",
    flinkBaseComponents % "test",
    flinkTestUtil % "test",
    kafkaTestUtil % "test",
    //for local development
    ui % "test",
    deploymentManagerApi % "test")

lazy val defaultModel = (project in (file("defaultModel"))).
  settings(commonSettings).
  settings(assemblyNoScala("defaultModel.jar"): _*).
  settings(publishAssemblySettings: _*).
  settings(
    name := "nussknacker-default-model"
  )
  .dependsOn(modelUtil, interpreter % "provided")

lazy val flinkExecutor = (project in flink("executor")).
  settings(commonSettings).
  settings(assemblyNoScala("flinkExecutor.jar"): _*).
  settings(publishAssemblySettings: _*).
  settings(
    name := "nussknacker-flink-executor",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" % "flink-runtime" % flinkV % "provided",
        "org.apache.flink" %% "flink-statebackend-rocksdb" % flinkV % "provided"
      )
    }
  ).dependsOn(flinkUtil, interpreter, flinkTestUtil % "test")

lazy val interpreter = (project in file("interpreter")).
  settings(commonSettings).
  settings(
    name := "nussknacker-interpreter",
    libraryDependencies ++= {
      Seq(
        "org.springframework" % "spring-expression" % springV,
        //needed by scala-compiler for spring-expression...
        "com.google.code.findbugs" % "jsr305" % "3.0.2",
        "org.scala-lang.modules" %% "scala-java8-compat" % scalaCompatV,
        "org.apache.avro" % "avro" % avroV % "test",
        "org.scalacheck" %% "scalacheck" % scalaCheckV % "test",
        "com.cronutils" % "cron-utils" % cronParserV % "test"
      )
    }
  ).
  dependsOn(util, testUtil % "test")

lazy val benchmarks = (project in file("benchmarks")).
  settings(commonSettings).
  enablePlugins(JmhPlugin).
  settings(
    name := "nussknacker-benchmarks",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV,
        "org.apache.flink" % "flink-runtime" % flinkV
      )
    },
    // To avoid Intellij message that jmh generated classes are shared between main and test
    Jmh / classDirectory := (Test / classDirectory).value,
    Jmh / dependencyClasspath := (Test / dependencyClasspath).value,
    Jmh / generateJmhSourcesAndResources := (Jmh / generateJmhSourcesAndResources).dependsOn(Test / compile).value,
  ).dependsOn(interpreter, avroFlinkUtil, flinkExecutor, flinkBaseComponents, testUtil % "test")


lazy val kafkaUtil = (project in utils("kafka-util")).
  configs(IntegrationTest).
  settings(commonSettings).
  settings(itSettings()).
  settings(
    name := "nussknacker-kafka-util",
    libraryDependencies ++= {
      Seq(
        "javax.validation" % "validation-api" % javaxValidationApiV,
        "org.apache.kafka" % "kafka-clients" % kafkaV,
        "com.dimafeng" %% "testcontainers-scala-scalatest" % testcontainersScalaV % "it",
        "com.dimafeng" %% "testcontainers-scala-kafka" % testcontainersScalaV % "it",
        "org.scalatest" %% "scalatest" % scalaTestV % "it, test"
      )
    }
  ).dependsOn(util % Provided)

lazy val avroUtil = (project in utils("avro-util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-avro-util",
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
        "tech.allegro.schema.json2avro" % "converter" % "0.2.10",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).dependsOn(kafkaUtil, interpreter % Provided, kafkaTestUtil % "test")

lazy val avroFlinkUtil = (project in flink("avro-util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-flink-avro-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" % "flink-avro" % flinkV,
        "org.apache.flink" %% s"flink-connector-kafka" % flinkV % "test",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  )
  .dependsOn(avroUtil, flinkKafkaUtil, flinkUtil % Provided, interpreter % Provided, kafkaTestUtil % "test", flinkTestUtil % "test", flinkExecutor % "test")

lazy val flinkKafkaUtil = (project in flink("kafka-util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-flink-kafka-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-connector-kafka" % flinkV,
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).
  dependsOn(api % Provided, kafkaUtil, flinkUtil % Provided, flinkExecutor % "test", kafkaTestUtil % "test", flinkTestUtil % "test")

lazy val kafkaTestUtil = (project in utils("kafka-test-util")).
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
  .dependsOn(testUtil, kafkaUtil, util % Provided)

lazy val util = (project in utils("util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-util",
    libraryDependencies ++= {
      Seq(
        "org.springframework" % "spring-core" % springV,
        "com.github.ben-manes.caffeine" % "caffeine" % caffeineCacheV,
        "org.scala-lang.modules" %% "scala-java8-compat" % scalaCompatV,
        "com.iheart" %% "ficus" % ficusV
      )
    }
  ).dependsOn(api, testUtil % "test")

lazy val modelUtil = (project in utils("model-util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-model-util"
  ).dependsOn(util % Provided, testUtil % "test", interpreter % "test")

lazy val testUtil = (project in utils("test-util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-test-util",
    libraryDependencies ++= {
      Seq(
        "org.scalatest" %% "scalatest" % scalaTestV,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "com.typesafe" % "config" % configV,
        "ch.qos.logback" % "logback-classic" % logbackV
      )
    }
  )

lazy val flinkUtil = (project in flink("util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-flink-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" % "flink-metrics-dropwizard" % flinkV,
      )
    }
  ).dependsOn(util, flinkApi, testUtil % "test")

lazy val flinkTestUtil = (project in flink("test-util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-flink-test-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        //intellij has some problems with provided...
        "org.apache.flink" %% "flink-statebackend-rocksdb" % flinkV,
        "org.apache.flink" %% "flink-test-utils" % flinkV  excludeAll (
          //we use logback in NK
          ExclusionRule("org.apache.logging.log4j", "log4j-slf4j-impl")
        ),
        "org.apache.flink" % "flink-runtime" % flinkV % "compile" classifier "tests",
        "org.apache.flink" % "flink-metrics-dropwizard" % flinkV
      )
    }
  ).dependsOn(testUtil, flinkUtil, interpreter)

lazy val requestResponseUtil = (project in lite("request-response/util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-request-response-util"
  ).dependsOn(util % Provided, requestResponseApi % Provided, testUtil % "test")


lazy val requestResponseApi = (project in lite("request-response/api")).
  settings(commonSettings).
  settings(
    name := "nussknacker-request-response-api"
  ).dependsOn(liteEngineApi)

lazy val liteEngineApi = (project in lite("api")).
  settings(commonSettings).
  settings(
    name := "nussknacker-lite-api",
  ).dependsOn(api)

lazy val liteBaseComponents = (project in lite("components/base")).
  settings(commonSettings).
  settings(assemblyNoScala("liteBase.jar"): _*).
  settings(
    name := "nussknacker-lite-base-components",
  ).dependsOn(liteEngineApi % "provided", util % Provided)

lazy val liteKafkaComponents = (project in lite("components/kafka")).
  settings(commonSettings).
  settings(assemblyNoScala("liteKafka.jar"): _*).
  settings(
    name := "nussknacker-lite-kafka-components",
    //TODO: avroUtil brings kafkaUtil to assembly, which is superfluous, as we already have it in engine...
  ).dependsOn(liteEngineKafkaApi % Provided, liteEngineApi % Provided, util % Provided, avroUtil)

lazy val liteRequestResponseComponents = (project in lite("components/request-response")).
  settings(commonSettings).
  settings(assemblyNoScala("liteRequestResponse.jar"): _*).
  settings(
    name := "nussknacker-lite-request-response-components",
  ).dependsOn(requestResponseApi % "provided", liteEngineApi % "provided", requestResponseUtil)



lazy val liteEngineRuntime = (project in lite("runtime")).
  settings(commonSettings).
  settings(
    name := "nussknacker-lite-runtime",
    libraryDependencies ++= {
      Seq(
        "io.dropwizard.metrics5" % "metrics-core" % dropWizardV,
        "io.dropwizard.metrics5" % "metrics-influxdb" % dropWizardV,
        "com.softwaremill.sttp.client" %% "core" % sttpV,
        "ch.qos.logback" % "logback-classic" % logbackV,
      )
    },
  ).dependsOn(liteEngineApi, interpreter, testUtil % "test")

lazy val liteEngineKafkaIntegrationTest: Project = (project in lite("kafka/integration-test")).
  configs(IntegrationTest).
  settings(itSettings()).
  settings(commonSettings).
  settings(
    name := "nussknacker-lite-kafka-integration-test",
    IntegrationTest / Keys.test := (IntegrationTest / Keys.test).dependsOn(
      liteEngineKafkaRuntime / Universal / stage,
      liteEngineKafkaRuntime / Docker / publishLocal
    ).value,
    libraryDependencies ++= Seq(
      "commons-io" % "commons-io" % commonsIOV,
      "com.dimafeng" %% "testcontainers-scala-scalatest" % testcontainersScalaV % "it",
      "com.dimafeng" %% "testcontainers-scala-kafka" % testcontainersScalaV % "it",
      "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % sttpV % "it"
    )
  ).dependsOn(interpreter % "it", avroUtil % "it", testUtil % "it", kafkaTestUtil % "it", httpUtils % "it")

lazy val liteEngineKafkaApi = (project in lite("kafka/api")).
  settings(commonSettings).
  settings(
    name := "nussknacker-lite-kafka-api",
    libraryDependencies ++= Seq(
      "org.apache.kafka" % "kafka-clients" % kafkaV
    )
  ).dependsOn(liteEngineApi)

lazy val liteEngineKafkaRuntimeDockerSettings = {
  val workingDir = "/opt/nussknacker"

  commonDockerSettings ++ Seq(
    dockerEntrypoint := Seq(s"$workingDir/bin/nu-kafka-engine-entrypoint.sh"),
    Docker / defaultLinuxInstallLocation := workingDir,
    packageName := liteEngineKafkaRuntimeDockerPackageName,
    dockerLabels := Map(
      "version" -> version.value,
      "scala" -> scalaVersion.value,
    )
  )
}

lazy val liteEngineKafkaRuntime: Project = (project in lite("kafka/runtime")).
  settings(commonSettings).
  settings(liteEngineKafkaRuntimeDockerSettings).
  enablePlugins(SbtNativePackager, JavaServerAppPackaging).
  settings(
    name := "nussknacker-lite-kafka-runtime",
    Universal / mappings ++= Seq(
      (defaultModel / assembly).value -> "model/defaultModel.jar",
      (liteBaseComponents / assembly).value -> "components/lite/liteBase.jar",
      (liteKafkaComponents / assembly).value -> "components/lite/liteKafka.jar",
      (openapiComponents / assembly).value -> "components/common/openapi.jar",
      (sqlComponents / assembly).value -> "components/common/sql.jar"
    ),
    libraryDependencies ++= Seq(
      "commons-io" % "commons-io" % commonsIOV,
      "com.lightbend.akka.management" %% "akka-management" % akkaManagementV,
      // must be explicit version because otherwise ManifestInfo.checkSameVersion reports error
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV
    )
  ).dependsOn(liteEngineRuntime, liteEngineKafkaApi, kafkaUtil, testUtil % "test", kafkaTestUtil % "test", liteBaseComponents % "test")

lazy val liteEmbeddedDeploymentManager = (project in lite("embeddedDeploymentManager")).
  configs(IntegrationTest).
  settings(itSettings()).
  enablePlugins().
  settings(commonSettings).
  settings(assemblyNoScala("lite-embedded-manager.jar"): _*).

  settings(
    name := "nussknacker-lite-embedded-deploymentManager",
  ).dependsOn(liteEngineKafkaRuntime, deploymentManagerApi % "provided", liteKafkaComponents % "test", testUtil % "test", kafkaTestUtil % "test")

lazy val buildAndImportRuntimeImageToK3d = taskKey[Unit]("Import runtime image into k3d cluster")

// TODO: 1) publish assembly in dist 2) developer setup for staging/NussknackerApp from Idea
lazy val liteK8sDeploymentManager = (project in lite("k8sDeploymentManager")).
  configs(ExternalDepsTests).
  settings(externalDepsTestsSettings).
  enablePlugins().
  settings(commonSettings).
  settings(assemblyNoScala("lite-k8s-manager.jar"): _*).
  settings(
    name := "nussknacker-lite-k8s-deploymentManager",
    libraryDependencies ++= {
      Seq(
        "io.skuber" %% "skuber" % "2.6.2",
        "com.github.julien-truffaut" %% "monocle-core" % monocleV,
        "com.github.julien-truffaut" %%  "monocle-macro" % monocleV
      )
    },
    buildAndImportRuntimeImageToK3d := {
      (liteEngineKafkaRuntime / Docker / publishLocal).value
      "k3d" #&& s"k3d image import touk/nussknacker-lite-kafka-runtime:${version.value}" #|| "echo 'No k3d installed!'" !
    },
    ExternalDepsTests / Keys.test := (ExternalDepsTests / Keys.test).dependsOn(
      buildAndImportRuntimeImageToK3d
    ).value
  ).dependsOn(
    liteEngineKafkaRuntime, // for tests purpose
    deploymentManagerApi % "provided", testUtil % "test")

lazy val api = (project in file("api")).
  settings(commonSettings).
  enablePlugins(BuildInfoPlugin).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version),
    buildInfoKeys ++= Seq[BuildInfoKey](
      "buildTime" -> java.time.LocalDateTime.now().toString,
      "gitCommit" -> git.gitHeadCommit.value.getOrElse("")
    ),
    buildInfoPackage := "pl.touk.nussknacker.engine.version",
    buildInfoOptions ++= Seq(BuildInfoOption.ToMap)
  ).
  settings(
    name := "nussknacker-api",
    libraryDependencies ++= {
      Seq(
        "io.circe" %% "circe-parser" % circeV,
        "io.circe" %% "circe-generic" % circeV,
        "io.circe" %% "circe-generic-extras" % circeV,
        "com.github.erosb" % "everit-json-schema" % everitSchemaV,
        "com.iheart" %% "ficus" % ficusV,
        "org.apache.commons" % "commons-lang3" % commonsLangV,
        "org.apache.commons" % "commons-text" % commonsTextV,
        "org.typelevel" %% "cats-core" % catsV,
        "org.typelevel" %% "cats-effect" % "2.5.3",
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "com.typesafe" % "config" % configV,
        "com.vdurmont" % "semver4j" % "3.1.0",
        "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionsCompatV,
        "javax.validation" % "validation-api" % javaxValidationApiV
      )
    }
  ).dependsOn(testUtil % "test")

lazy val security = (project in file("security")).
  configs(IntegrationTest).
  settings(commonSettings).
  settings(itSettings()).
  settings(
    name := "nussknacker-security",
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka" %% "akka-http" % akkaHttpV,
        "com.typesafe.akka" %% "akka-stream" % akkaV,
        "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test",
        "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
        "de.heikoseeberger" %% "akka-http-circe" % akkaHttpCirceV,
        "com.typesafe" % "config" % configV ,
        "org.mindrot" % "jbcrypt" % jbcryptV,
        //Packages below are only for plugin providers purpose
        "io.circe" %% "circe-core" % circeV,
        "com.github.jwt-scala" %% "jwt-circe" % jwtCirceV,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "com.auth0" % "jwks-rsa" % "0.19.0", // a tool library for reading a remote JWK store, not an Auth0 service dependency
        "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % sttpV % "it,test",
        "com.dimafeng" %% "testcontainers-scala-scalatest" % testcontainersScalaV % "it,test",
        "com.github.dasniko" % "testcontainers-keycloak" % "1.6.0" % "it,test"
      )
    }
  )
  .dependsOn(util, httpUtils, testUtil % "it,test")

lazy val flinkApi = (project in flink("api")).
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

lazy val processReports = (project in file("ui/processReports")).
  configs(IntegrationTest).
  settings(commonSettings).
  settings(itSettings()).
  settings(
    name := "nussknacker-process-reports",
    libraryDependencies ++= {
      Seq(
        "com.typesafe" % "config" % "1.3.0",
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "com.iheart" %% "ficus" % ficusV,
        "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % sttpV % "it,test",
        "com.dimafeng" %% "testcontainers-scala-scalatest" % testcontainersScalaV % "it,test",
        "com.dimafeng" %% "testcontainers-scala-influxdb" % testcontainersScalaV % "it,test",
        "org.influxdb" % "influxdb-java" % "2.21" % "it,test"
      )
    }
  ).dependsOn(httpUtils, util, testUtil % "it,test")

lazy val httpUtils = (project in utils("httpUtils")).
  settings(commonSettings).
  settings(
    name := "nussknacker-http-utils",
    libraryDependencies ++= {
      Seq(
        "com.softwaremill.sttp.client" %% "core" % sttpV,
        "com.softwaremill.sttp.client" %% "json-common" % sttpV,
        //we copy code as we use newer circe
        //"com.softwaremill.sttp.client" %% "circe" % sttpV
      )
    }
  ).dependsOn(api % Provided, testUtil % "test")

val swaggerParserV = "2.0.20"
val swaggerIntegrationV = "2.1.3"

lazy val openapiComponents = (project in component("openapi")).
    configs(IntegrationTest).
    settings(itSettings()).
    settings(commonSettings).
    settings(assemblyNoScala("openapi.jar"): _*).
    settings(publishAssemblySettings: _*).
    settings(
      name := "nussknacker-openapi",
      libraryDependencies ++= Seq(
        "io.swagger.parser.v3" % "swagger-parser" % swaggerParserV excludeAll(
          ExclusionRule(organization = "javax.mail"),
          ExclusionRule(organization = "javax.validation"),
          ExclusionRule(organization = "jakarta.activation"),
          ExclusionRule(organization = "jakarta.validation")
        ),
        "io.swagger.core.v3" % "swagger-integration" % swaggerIntegrationV  excludeAll(
          ExclusionRule(organization = "jakarta.activation"),
          ExclusionRule(organization = "jakarta.validation")
        ),
        "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % sttpV  excludeAll(
          ExclusionRule(organization = "com.sun.activation", name = "javax.activation"),
        ),
        "io.netty" % "netty-transport-native-epoll" % nettyV,
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % Provided,
        "org.scalatest" %% "scalatest" % scalaTestV %  "it,test"
      ),
    ).dependsOn(api % Provided, util % Provided, httpUtils, flinkExecutor % "it,test", requestResponseRuntime % "it,test", requestResponseUtil % "it,test", flinkTestUtil % "it,test", kafkaTestUtil % "it,test")

lazy val sqlComponents = (project in component("sql")).
  configs(IntegrationTest).
  settings(itSettings()).
  settings(commonSettings).
  settings(assemblyNoScala("sql.jar"): _*).
  settings(publishAssemblySettings: _*).
  settings(
    name := "nussknacker-sql",
    libraryDependencies ++= Seq(
      "com.zaxxer" % "HikariCP" % "4.0.3",
//      It won't run on Java 16 as Hikari will fail while trying to load IgniteJdbcThinDriver https://issues.apache.org/jira/browse/IGNITE-14888
      "org.apache.ignite" % "ignite-core" % "2.10.0" % Provided,
      "org.apache.ignite" % "ignite-indexing" % "2.10.0" % Provided,
      "org.scalatest" %% "scalatest" % scalaTestV % "it,test",
      "org.hsqldb" % "hsqldb" % hsqldbV % "it,test",
    ),
  ).dependsOn(api % Provided, util % Provided, flinkExecutor % "test,it", requestResponseRuntime % "test,it", requestResponseUtil % "test,it", flinkTestUtil % "it,test", kafkaTestUtil % "it,test")

lazy val flinkBaseComponents = (project in flink("components/base")).
  configs(IntegrationTest).
  settings(itSettings()).
  settings(commonSettings).
  settings(assemblyNoScala("flinkBase.jar"): _*).
  settings(publishAssemblySettings: _*).
  settings(
    name := "nussknacker-flink-base-components",
    libraryDependencies ++= Seq(
      "org.apache.flink" %% "flink-streaming-scala" % flinkV % Provided,
      "org.scalatest" %% "scalatest" % scalaTestV % "it,test",
      "com.clearspring.analytics" % "stream" % "2.9.8" excludeAll (
        //It is used only in QDigest which we don't use, while it's >20MB in size...
          ExclusionRule("it.unimi.dsi", "fastutil"),
      )
    ),
  ).dependsOn(flinkUtil % Provided, flinkExecutor % "it, test", flinkTestUtil % "it,test", kafkaTestUtil % "it,test")

lazy val flinkKafkaComponents = (project in flink("components/kafka")).
  settings(commonSettings).
  settings(assemblyNoScala("flinkKafka.jar"): _*).
  settings(publishAssemblySettings: _*).
  settings(
    name := "nussknacker-flink-kafka-components",
  ).dependsOn(flinkApi % Provided, flinkKafkaUtil, avroFlinkUtil, util % Provided)

lazy val copyUiDist = taskKey[Unit]("copy ui")
lazy val copyUiSubmodulesDist = taskKey[Unit]("copy ui submodules")

lazy val restmodel = (project in file("ui/restmodel"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-restmodel"
  )
  // TODO: remove dependency to deploymentManagerApi (StateStatus,ProcessActionType)
  .dependsOn(api, deploymentManagerApi, testUtil % "test")

lazy val listenerApi = (project in file("ui/listener-api"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-listener-api",
  )
  .dependsOn(restmodel, util, testUtil % "test")

lazy val deploymentManagerApi = (project in file("ui/deployment-manager-api"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-deployment-manager-api",
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka" %% "akka-actor" % akkaV,
        "com.softwaremill.sttp.client" %% "core" % sttpV
      )
    }
  )
  // TODO: remove dependency to interpreter
  .dependsOn(interpreter, testUtil % "test")

lazy val ui = (project in file("ui/server"))
  .configs(SlowTests)
  .settings(slowTestsSettings)
  .settings(commonSettings)
  .settings(assemblySettings("nussknacker-ui-assembly.jar", includeScala = includeFlinkAndScala): _*)
  .settings(publishAssemblySettings: _*)
  .settings(
    name := "nussknacker-ui",
    copyUiDist :=  {
      val feDistDirectory = file("ui/client/dist")
      val feDistFiles: Seq[File] = (feDistDirectory ** "*").get()
      IO.copy(feDistFiles pair Path.rebase(feDistDirectory, (compile / crossTarget).value / "classes" / "web" / "static"), CopyOptions.apply(overwrite = true, preserveLastModified = true, preserveExecutable = false))
    },
    copyUiSubmodulesDist :=  {
      val feSubmodulesDistDirectory = file("ui/submodules/dist")
      val feSubmodulesDistFiles: Seq[File] = (feSubmodulesDistDirectory ** "*").get()
      IO.copy(feSubmodulesDistFiles pair Path.rebase(feSubmodulesDistDirectory, (compile / crossTarget).value / "classes" / "web" / "submodules"), CopyOptions.apply(overwrite = true, preserveLastModified = true, preserveExecutable = false))
    },
    ThisBuild / parallelExecution := false,
    SlowTests / test := (SlowTests / test).dependsOn(
      flinkDevModel / Compile / assembly,
      flinkExecutor / Compile / assembly
    ).value,
    Test / test := (Test / test).dependsOn(
      flinkDevModel / Compile / assembly,
      flinkExecutor / Compile / assembly
    ).value,
    /*
      We depend on copyUiDist and copyUiSubmodulesDist in packageBin and assembly to be make sure fe files will be included in jar and fajar
      We abuse sbt a little bit, but we don't want to put webpack in generate resources phase, as it's long and it would
      make compilation v. long. This is not too nice, but so far only alternative is to put ui dists (copyUiDist, copyUiSubmodulesDist) outside sbt and
      use bash to control when it's done - and this can lead to bugs and edge cases (release, dist/docker, dist/tgz, assembly...)
     */
    Compile / packageBin := (Compile / packageBin).dependsOn(
      copyUiDist, copyUiSubmodulesDist
    ).value,
    assembly in ThisScope := (assembly in ThisScope).dependsOn(
      copyUiDist, copyUiSubmodulesDist
    ).value,
    assembly / assemblyMergeStrategy := uiMergeStrategy,
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka" %% "akka-http" % akkaHttpV,
        "com.typesafe.akka" %% "akka-stream" % akkaV,
        "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test",
        "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
        "de.heikoseeberger" %% "akka-http-circe" % akkaHttpCirceV,
        "com.softwaremill.sttp.client" %% "akka-http-backend" % sttpV,

        "ch.qos.logback" % "logback-core" % logbackV,
        "ch.qos.logback" % "logback-classic" % logbackV,
        "org.slf4j" % "log4j-over-slf4j" % slf4jV,
        "com.carrotsearch" % "java-sizeof" % "0.0.5",

        //It's needed by flinkDeploymentManager which has disabled includingScala
        "org.scala-lang" % "scala-compiler" % scalaVersion.value,
        "org.scala-lang" % "scala-reflect" % scalaVersion.value,

        "com.typesafe.slick" %% "slick" % slickV,
        "com.typesafe.slick" %% "slick-hikaricp" % slickV,
        "org.hsqldb" % "hsqldb" % hsqldbV,
        "org.postgresql" % "postgresql" % postgresV,
        "org.flywaydb" % "flyway-core" % flywayV,
        "org.apache.xmlgraphics" % "fop" % "2.3",


        "com.typesafe.slick" %% "slick-testkit" % slickV % "test",

        "com.dimafeng" %% "testcontainers-scala-scalatest" % testcontainersScalaV % "test",
        "com.dimafeng" %% "testcontainers-scala-postgresql" % testcontainersScalaV % "test",
      )
    }
  )
  .dependsOn(interpreter, processReports, security, listenerApi,
    testUtil % "test",
    //TODO: this is unfortunately needed to run without too much hassle in Intellij...
    //provided dependency of kafka is workaround for Idea, which is not able to handle test scope on module dependency
    //otherwise it is (wrongly) added to classpath when running UI from Idea
    flinkDeploymentManager % "provided" ,
    liteEmbeddedDeploymentManager % "provided" ,
    liteK8sDeploymentManager % "provided" ,
    kafkaUtil % "provided",
    avroUtil % "provided",
    requestResponseRuntime % "provided"
  )

/*
  We want to simplify dependency management in downstream projects using BOM pattern
  (https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#bill-of-materials-bom-poms)

  Sbt does not support this pattern by default. For publishing we use idea for https://stackoverflow.com/a/59810834
  To use BOM in sbt projects currently the easiest way is to use pomOnly() dependency and sbt-maven-resolver
  (hopefully some day https://github.com/coursier/coursier/issues/1390 will be resolved...)
 */
lazy val bom = (project in file("bom"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-bom",
    //we have to transform result pom to have pom packaging and move dependencies to dependencyManagement section
    pomPostProcess := { node: scala.xml.Node =>
      val rule: RewriteRule = new RewriteRule {
        override def transform(n: scala.xml.Node): scala.xml.NodeSeq = n match {
          case e: Elem if e != null && e.label == "packaging" =>
            <packaging>pom</packaging>
          case e: Elem if e != null && e.label == "dependencies" =>
            <dependencyManagement>{e}</dependencyManagement>
          case _ => n
        }
      }
      new RuleTransformer(rule).transform(node).head
    },
    /*
      TODO: do we want to include other dependencies (especially with 'provided' scope)?
      Maybe we need other BOM for ComponentProvider dependencies, which have more 'provided' dependencies
     */
    libraryDependencies ++= (dependencyOverrides.value ++ Seq(
      "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
      "org.apache.flink" %% "flink-streaming-java" % flinkV % "provided",
      "org.apache.flink" % "flink-runtime" % flinkV % "provided",
      "org.apache.flink" %% "flink-statebackend-rocksdb" % flinkV % "provided"
    ))
  ).dependsOn(modules.map(k => k:ClasspathDep[ProjectReference]):_*)

lazy val modules = List[ProjectReference](
  requestResponseRuntime, requestResponseRuntime, requestResponseApp, flinkDeploymentManager, flinkPeriodicDeploymentManager, flinkDevModel, flinkDevModelJava, defaultModel,
  openapiComponents, flinkExecutor, interpreter, benchmarks, kafkaUtil, avroFlinkUtil, flinkKafkaUtil, kafkaTestUtil, util, testUtil, flinkUtil, flinkTests, modelUtil,
  flinkTestUtil, requestResponseUtil, requestResponseApi, api, security, flinkApi, processReports, httpUtils,
  restmodel, listenerApi, deploymentManagerApi, ui, sqlComponents, avroUtil, flinkBaseComponents, flinkKafkaComponents,
  liteEngineApi, liteEngineRuntime, liteBaseComponents, liteEngineKafkaRuntime, liteEngineKafkaIntegrationTest, liteEmbeddedDeploymentManager, liteK8sDeploymentManager,
  liteRequestResponseComponents
)
lazy val modulesWithBom: List[ProjectReference] = bom :: modules

lazy val root = (project in file("."))
  .aggregate(modulesWithBom: _*)
  .settings(commonSettings)
  .settings(
    // crossScalaVersions must be set to Nil on the aggregating project
    releaseCrossBuild := true,
    publish / skip := true,
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
      releaseStepCommand("dist/Universal/packageZipTarball"),
      releaseStepCommand("liteEngineKafkaRuntime/Universal/packageZipTarball"),
      releaseStepCommand("dist/Docker/publish"),
      releaseStepCommand("requestResponseApp/Docker/publish"),
      releaseStepCommand("liteEngineKafkaRuntime/Docker/publish"),
      releaseStepCommand("sonatypeBundleRelease"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )

lazy val prepareDev = taskKey[Unit]("Prepare components and model for running from IDE")
prepareDev := {
  val workTarget = (ui / baseDirectory).value / "work"
  val artifacts = componentArtifacts.value ++ devModelArtifacts.value
  IO.copy(artifacts.map { case (source, target) => (source, workTarget / target) })
  (ui / copyUiDist).value
  (ui / copyUiSubmodulesDist).value
}
