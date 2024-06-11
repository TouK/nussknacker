import com.typesafe.sbt.packager.SettingsHelper
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerUsername
import pl.project13.scala.sbt.JmhPlugin
import pl.project13.scala.sbt.JmhPlugin._
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyPlugin.autoImport.assembly
import sbtassembly.MergeStrategy
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

import scala.language.postfixOps
import scala.sys.process._
import scala.util.Try
import scala.xml.Elem
import scala.xml.transform.{RewriteRule, RuleTransformer}

// Warning: Flink doesn't work correctly with 2.12.11
val scala212 = "2.12.10"
val scala213 = "2.13.12"

lazy val defaultScalaV = sys.env.get("NUSSKNACKER_SCALA_VERSION") match {
  case None | Some("2.13") => scala213
  case Some("2.12")        => scala212
  case Some(unsupported)   => throw new IllegalArgumentException(s"Nu doesn't support $unsupported Scala version")
}

lazy val supportedScalaVersions = List(scala212, scala213)

// Silencer must be compatible with exact scala version - see compatibility matrix: https://search.maven.org/search?q=silencer-plugin
// Silencer 1.7.x require Scala 2.12.11 (see warning above)
// Silencer (and all '@silent' annotations) can be removed after we can upgrade to 2.12.13...
// https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
lazy val silencerV      = "1.7.17"
lazy val silencerV_2_12 = "1.6.0"

//TODO: replace configuration by system properties with configuration via environment after removing travis scripts
//then we can change names to snake case, for "normal" env variables
def propOrEnv(name: String, default: String): String = propOrEnv(name).getOrElse(default)
def propOrEnv(name: String): Option[String]          = Option(System.getProperty(name)).orElse(sys.env.get(name))

//by default we include flink and scala, we want to be able to disable this behaviour for performance reasons
val includeFlinkAndScala = propOrEnv("includeFlinkAndScala", "true").toBoolean

val flinkScope         = if (includeFlinkAndScala) "compile" else "provided"
val nexusUrlFromProps  = propOrEnv("nexusUrl")
//TODO: this is pretty clunky, but works so far for our case...
val nexusHostFromProps = nexusUrlFromProps.map(_.replaceAll("http[s]?://", "").replaceAll("[:/].*", ""))

//Docker release configuration
val dockerTagName                = propOrEnv("dockerTagName")
val dockerPort                   = propOrEnv("dockerPort", "8080").toInt
val dockerUserName               = Option(propOrEnv("dockerUserName", "touk"))
val dockerPackageName            = propOrEnv("dockerPackageName", "nussknacker")
val dockerUpLatestFromProp       = propOrEnv("dockerUpLatest").flatMap(p => Try(p.toBoolean).toOption)
val dockerUpBranchLatestFromProp = propOrEnv("dockerUpBranchLatest", "true").toBoolean
val addDevArtifacts              = propOrEnv("addDevArtifacts", "false").toBoolean
val addManagerArtifacts          = propOrEnv("addManagerArtifacts", "false").toBoolean

val requestResponseManagementPort = propOrEnv("requestResponseManagementPort", "8070").toInt
val requestResponseProcessesPort  = propOrEnv("requestResponseProcessesPort", "8080").toInt

val liteEngineKafkaRuntimeDockerPackageName =
  propOrEnv("liteEngineKafkaRuntimeDockerPackageName", "nussknacker-lite-runtime-app")

// `publishArtifact := false` should be enough to keep sbt from publishing root module,
// unfortunately it does not work, so we resort to hack by publishing root module to Resolver.defaultLocal
//publishArtifact := false
publishTo          := Some(Resolver.defaultLocal)
crossScalaVersions := Nil

ThisBuild / isSnapshot := version(_ contains "-SNAPSHOT").value

lazy val publishSettings = Seq(
  publishMavenStyle             := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  publishTo                     := {
    nexusUrlFromProps
      .map { url =>
        (if (isSnapshot.value) "snapshots" else "releases") at url
      }
      .orElse {
        val defaultNexusUrl = "https://oss.sonatype.org/"
        if (isSnapshot.value)
          Some("snapshots" at defaultNexusUrl + "content/repositories/snapshots")
        else
          sonatypePublishToBundle.value
      }
  },
  Test / publishArtifact        := false,
  // We don't put scm information here, it will be added by release plugin and if scm provided here is different than the one from scm
  // we'll end up with two scm sections and invalid pom...
  pomExtra in Global            := {
    <developers>
      <developer>
        <id>TouK</id>
        <name>TouK</name>
        <url>https://touk.pl</url>
      </developer>
    </developers>
  },
  organization                  := "pl.touk.nussknacker",
  homepage                      := Some(url(s"https://github.com/touk/nussknacker")),
)

def defaultMergeStrategy: String => MergeStrategy = {
  // remove JPMS module descriptors (a proper soultion would be to merge them)
  case PathList(ps @ _*) if ps.last == "module-info.class"            => MergeStrategy.discard
  // we override Spring's class and we want to keep only our implementation
  case PathList(ps @ _*) if ps.last == "NumberUtils.class"            => MergeStrategy.first
  // merge Netty version information files
  case PathList(ps @ _*) if ps.last == "io.netty.versions.properties" => MergeStrategy.concat
  // due to swagger-parser dependencies having different schema definitions (json-schema-validator and json-schema-core)
  case PathList("draftv4", "schema")                                  => MergeStrategy.first
  case x                                                              => MergeStrategy.defaultMergeStrategy(x)
}

def designerMergeStrategy: String => MergeStrategy = {
  // https://tapir.softwaremill.com/en/latest/docs/openapi.html#using-swaggerui-with-sbt-assembly
  case PathList("META-INF", "maven", "org.webjars", "swagger-ui", "pom.properties") =>
    MergeStrategy.singleOrError
  case x                                                                            => defaultMergeStrategy(x)
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

def forScalaVersion[T](version: String)(provide: PartialFunction[(Int, Int), T]): T = {
  CrossVersion.partialVersion(version) match {
    case Some((major, minor)) if provide.isDefinedAt((major.toInt, minor.toInt)) =>
      provide((major.toInt, minor.toInt))
    case Some(_)                                                                 =>
      throw new IllegalArgumentException(s"Scala version $version is not handled")
    case None                                                                    =>
      throw new IllegalArgumentException(s"Invalid Scala version $version")
  }
}

lazy val commonSettings =
  publishSettings ++
    Seq(
      licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
      crossScalaVersions               := supportedScalaVersions,
      scalaVersion                     := defaultScalaV,
      resolvers ++= Seq(
        "confluent" at "https://packages.confluent.io/maven",
      ),
      // We ignore k8s tests to keep development setup low-dependency
      Test / testOptions ++= Seq(scalaTestReports, ignoreSlowTests, ignoreExternalDepsTests),
      addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.3" cross CrossVersion.full),
      libraryDependencies += compilerPlugin(
        "com.github.ghik" % "silencer-plugin" % forScalaVersion(scalaVersion.value) {
          case (2, 12) => silencerV_2_12
          case _       => silencerV
        } cross CrossVersion.full
      ),
      libraryDependencies ++= forScalaVersion(scalaVersion.value) {
        case (2, 12) => Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full))
        case _       => Seq()
      },
      scalacOptions                    := Seq(
        "-unchecked",
        "-deprecation",
        "-encoding",
        "utf8",
        "-Xfatal-warnings",
        "-feature",
        "-language:postfixOps",
        "-language:existentials",
        "-release",
        "11"
      ) ++ forScalaVersion(scalaVersion.value) {
        case (2, 12) =>
          Seq(
            "-Ypartial-unification",
            // We use jdk standard lib classes from java 11, but Scala 2.12 does not support target > 8 and
            // -release option has no influence on class version so we at least setup target to 8 and check java version
            // at the begining of our Apps
            "-target:jvm-1.8",
          )
        case (2, 13) =>
          Seq(
            "-Ymacro-annotations"
          )
      },
      Compile / compile / javacOptions := Seq(
        "-Xlint:deprecation",
        "-Xlint:unchecked",
        // Using --release flag (available only on jdk >= 9) instead of -source -target to avoid usage of api from newer java version
        "--release",
        "11",
        // we use it e.g. to provide consistent behaviour wrt extracting parameter names from scala and java
        "-parameters"
      ),
      // problem with scaladoc of api: https://github.com/scala/bug/issues/10134
      Compile / doc / scalacOptions -= "-Xfatal-warnings",
      libraryDependencies ++= Seq(
        "com.github.ghik" % "silencer-lib" % forScalaVersion(scalaVersion.value) {
          case (2, 12) => silencerV_2_12
          case _       => silencerV
        }                 % Provided cross CrossVersion.full
      ),
      // here we add dependencies that we want to have fixed across all modules
      dependencyOverrides ++= Seq(
        // currently Flink (1.11 -> https://github.com/apache/flink/blob/master/pom.xml#L128) uses 1.8.2 Avro version
        "org.apache.avro"    % "avro"          % avroV,
        "com.typesafe"       % "config"        % configV,
        "commons-io"         % "commons-io"    % flinkCommonsIOV,
        "org.apache.commons" % "commons-text"  % flinkCommonsTextV, // dependency of commons-lang3
        "org.apache.commons" % "commons-lang3" % flinkCommonsLang3V,
        "io.circe"          %% "circe-core"    % circeV,
        "io.circe"          %% "circe-parser"  % circeV,

        // Force akka-http and akka-stream versions to avoid bumping by akka-http-circe.
        "com.typesafe.akka"      %% "akka-http"          % akkaHttpV,
        "com.typesafe.akka"      %% "akka-http-testkit"  % akkaHttpV,
        "com.typesafe.akka"      %% "akka-stream"        % akkaV,
        "com.typesafe.akka"      %% "akka-testkit"       % akkaV,
        "org.scala-lang.modules" %% "scala-java8-compat" % scalaCompatV,

        // security features
        "org.scala-lang.modules" %% "scala-xml" % "2.1.0",

        // Our main kafka dependencies are Confluent (for avro) and Flink (Kafka connector)
        "org.apache.kafka"  % "kafka-clients"                % kafkaV,
        "org.apache.kafka" %% "kafka"                        % kafkaV,
        "io.netty"          % "netty-handler"                % nettyV,
        "io.netty"          % "netty-codec"                  % nettyV,
        "io.netty"          % "netty-codec-http"             % nettyV,
        "io.netty"          % "netty-codec-socks"            % nettyV,
        "io.netty"          % "netty-handler-proxy"          % nettyV,
        "io.netty"          % "netty-transport-native-epoll" % nettyV,

        // For async-http-client
        "com.typesafe.netty" % "netty-reactive-streams" % nettyReactiveStreamsV,

        // Jackson is used by: openapi, jwks-rsa, kafka-json-schema-provider
        "com.fasterxml.jackson.core"       % "jackson-annotations"            % jacksonV,
        "com.fasterxml.jackson.core"       % "jackson-core"                   % jacksonV,
        "com.fasterxml.jackson.core"       % "jackson-databind"               % jacksonV,
        "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor"        % jacksonV,
        "com.fasterxml.jackson.dataformat" % "jackson-dataformat-toml"        % jacksonV,
        "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml"        % jacksonV,
        "com.fasterxml.jackson.datatype"   % "jackson-datatype-guava"         % jacksonV,
        "com.fasterxml.jackson.datatype"   % "jackson-datatype-jdk8"          % jacksonV,
        "com.fasterxml.jackson.datatype"   % "jackson-datatype-joda"          % jacksonV,
        "com.fasterxml.jackson.datatype"   % "jackson-datatype-jsr310"        % jacksonV,
        "com.fasterxml.jackson.module"     % "jackson-module-parameter-names" % jacksonV,
        "com.fasterxml.jackson.module"    %% "jackson-module-scala"           % jacksonV,
        "io.dropwizard.metrics5"           % "metrics-core"                   % dropWizardV,
        "io.dropwizard.metrics5"           % "metrics-json"                   % dropWizardV,
        "org.slf4j"                        % "slf4j-api"                      % slf4jV
      )
    )

// Note: when updating check versions in 'flink*V' below, because some libraries must be fixed at versions provided
// by Flink, or jobs may fail in runtime when Flink is run with 'classloader.resolve-order: parent-first'.
// You can find versions provided by Flink in it's lib/flink-dist-*.jar/META-INF/DEPENDENCIES file.
val flinkV               = "1.18.1"
val flinkConnectorKafkaV = "3.1.0-1.18"
val flinkCommonsLang3V   = "3.12.0"
val flinkCommonsTextV    = "1.10.0"
val flinkCommonsIOV      = "2.11.0"
val avroV                = "1.11.3"
//we should use max(version used by confluent, version acceptable by flink), https://docs.confluent.io/platform/current/installation/versions-interoperability.html - confluent version reference
val kafkaV               = "3.6.2"
//TODO: Spring 5.3 has some problem with handling our PrimitiveOrWrappersPropertyAccessor
val springV              = "5.2.23.RELEASE"
val scalaTestV           = "3.2.18"
val scalaCheckV          = "1.17.1"
val scalaCheckVshort     = scalaCheckV.take(4).replace(".", "-")
val scalaTestPlusV       =
  "3.2.18.0" // has to match scalatest and scalacheck versions, see https://github.com/scalatest/scalatestplus-scalacheck/releases
// note: Logback 1.3 requires Slf4j 2.x, but Flink has Slf4j 1.7 on its classpath
val logbackV                = "1.2.13"
// this is used in cloud, official JsonEncoder uses different field layout
val logbackJsonV            = "0.1.5"
val betterFilesV            = "3.9.2"
val circeV                  = "0.14.10"
val circeGenericExtrasV     = "0.14.4"
val circeYamlV              = "0.15.2" // 0.15.3 drops Scala 2.12
val jwtCirceV               = "10.0.1"
val jacksonV                = "2.17.2"
val catsV                   = "2.12.0"
val catsEffectV             = "3.5.4"
val everitSchemaV           = "1.14.4"
val slf4jV                  = "1.7.36"
val scalaLoggingV           = "3.9.5"
val scalaCompatV            = "1.0.2"
val ficusV                  = "1.4.7"
val configV                 = "1.4.3"
//we want to use 5.x for lite metrics to have tags, however dropwizard development kind of freezed. Maybe we should consider micrometer?
//In Flink metrics we use bundled dropwizard metrics v. 3.x
// rc16+ depend on slf4j 2.x
val dropWizardV             = "5.0.0-rc15"
val scalaCollectionsCompatV = "2.12.0"
val testContainersScalaV    = "0.41.4"
val testContainersJavaV     = "1.20.1"
val nettyV                  = "4.1.113.Final"
val nettyReactiveStreamsV   = "2.0.12"

val akkaV                     = "2.6.20"
val akkaHttpV                 = "10.2.10"
val akkaManagementV           = "1.1.4"
val akkaHttpCirceV            = "1.39.2"
val slickV                    = "3.4.1"  // 3.5 drops Scala 2.12
val slickPgV                  = "0.21.1" // 0.22.2 uses Slick 3.5
val hikariCpV                 = "5.1.0"
val hsqldbV                   = "2.7.3"
val postgresV                 = "42.7.4"
// Flway 10 requires Java 17
val flywayV                   = "9.22.3"
val confluentV                = "7.5.1"
val azureKafkaSchemaRegistryV = "1.1.1"
val azureSchemaRegistryV      = "1.4.9"
val azureIdentityV            = "1.13.3"
val bcryptV                   = "0.10.2"
val cronParserV               = "9.1.6"  // 9.1.7+ requires JDK 16+
val javaxValidationApiV       = "2.0.1.Final"
val caffeineCacheV            = "3.1.8"
val sttpV                     = "3.9.8"
val tapirV                    = "1.10.8"
val openapiCirceYamlV         = "0.10.0"
//we use legacy version because this one supports Scala 2.12
val monocleV                  = "2.1.0"
val jmxPrometheusJavaagentV   = "1.0.1"
val wireMockV                 = "3.9.1"
val findBugsV                 = "3.0.2"
val enumeratumV               = "1.7.4"
val ujsonV                    = "4.0.1"
val igniteV                   = "2.10.0"

// depending on scala version one of this jar lays in Flink lib dir
def flinkLibScalaDeps(scalaVersion: String, configurations: Option[String] = None) = forScalaVersion(scalaVersion) {
  case (2, 12) =>
    Seq(
      "org.apache.flink" %% "flink-scala" % flinkV
    ) // we basically need only `org.apache.flink.runtime.types.FlinkScalaKryoInstantiator` from it...
  case (2, 13) =>
    Seq(
      "pl.touk" %% "flink-scala-2-13" % "1.1.1"
    ) // our tiny custom module with scala 2.13 `org.apache.flink.runtime.types.FlinkScalaKryoInstantiator` impl
}.map(m => configurations.map(m % _).getOrElse(m)).map(_ exclude ("com.esotericsoftware", "kryo-shaded"))

lazy val commonDockerSettings = {
  Seq(
    // designer should run on java11 since it may run Flink in-memory-cluster, which does not support newer java and we want to have same jre in both designer and lite-runner
    // to make analysis of problems with jre compatibility easier using testing mechanism and embedded server
    // TODO: we want to support jre17+ but before that flink must be compatible with jre17+ and we should handle opening of modules for spel reflectional access to java modules classes
    dockerBaseImage       := "eclipse-temurin:11-jre-jammy",
    dockerUsername        := dockerUserName,
    dockerUpdateLatest    := dockerUpLatestFromProp.getOrElse(!isSnapshot.value),
    dockerBuildxPlatforms := Seq("linux/amd64", "linux/arm64"), // not used in case of Docker/publishLocal
    dockerAliases         := {
      // https://docs.docker.com/engine/reference/commandline/tag/#extended-description
      def sanitize(str: String) = str.replaceAll("[^a-zA-Z0-9._-]", "_")

      val alias = dockerAlias.value

      val updateLatest       = if (dockerUpdateLatest.value) Some("latest") else None
      val updateBranchLatest = if (dockerUpBranchLatestFromProp) {
        // TODO: handle it more nicely, checkout actions in CI are not checking out actual branch
        // other option would be to reset source branch to checkout out commit
        val currentBranch = sys.env.getOrElse("GIT_SOURCE_BRANCH", git.gitCurrentBranch.value)
        Some(currentBranch + "-latest")
      } else {
        None
      }
      val dockerVersion      = Some(version.value)

      val tags                = List(dockerVersion, updateLatest, updateBranchLatest, dockerTagName).flatten
      val scalaSuffix         = s"_scala-${CrossVersion.binaryScalaVersion(scalaVersion.value)}"
      val tagsWithScalaSuffix = tags.map(t => s"$t$scalaSuffix")

      (tagsWithScalaSuffix ++ tags.filter(_ => scalaVersion.value == defaultScalaV))
        .map(tag => alias.withTag(Some(sanitize(tag))))
        .distinct
    }
  )
}

lazy val distDockerSettings = {
  val nussknackerDir = "/opt/nussknacker"

  commonDockerSettings ++ Seq(
    dockerEntrypoint                     := Seq(s"$nussknackerDir/bin/nussknacker-entrypoint.sh"),
    dockerExposedPorts                   := Seq(dockerPort),
    dockerEnvVars                        := Map(
      "HTTP_PORT" -> dockerPort.toString
    ),
    packageName                          := dockerPackageName,
    dockerLabels                         := Map(
      "version" -> version.value,
      "scala"   -> scalaVersion.value,
      "flink"   -> flinkV
    ),
    dockerExposedVolumes                 := Seq(s"$nussknackerDir/storage", s"$nussknackerDir/data"),
    Docker / defaultLinuxInstallLocation := nussknackerDir
  )
}

val publishAssemblySettings = List(
  Compile / assembly / artifact := {
    val art = (Compile / assembly / artifact).value
    art.withClassifier(Some("assembly"))
  },
  addArtifact(Compile / assembly / artifact, assembly)
)

def assemblySettings(
    assemblyName: String,
    includeScala: Boolean,
    filterProvidedDeps: Boolean = true
): List[Def.SettingsDefinition] = {
  // This work around need to be optional because for designer module it causes excluding of scala lib (because we has there other work around for Idea classpath and provided deps)
  val filterProvidedDepsSettingOpt = if (filterProvidedDeps) {
    Some(
      // For some reason problem described in https://github.com/sbt/sbt-assembly/issues/295 appears, workaround also works...
      assembly / fullClasspath := {
        val cp                   = (assembly / fullClasspath).value
        val providedDependencies = update.map(f => f.select(configurationFilter("provided"))).value

        cp filter { f =>
          !providedDependencies.contains(f.data)
        }
      }
    )
  } else {
    None
  }
  List(
    assembly / assemblyJarName       := assemblyName,
    assembly / assemblyOption        := (assembly / assemblyOption).value.withIncludeScala(includeScala).withLevel(Level.Info),
    assembly / assemblyMergeStrategy := defaultMergeStrategy
  ) ++ filterProvidedDepsSettingOpt
}

def assemblyNoScala(assemblyName: String): List[Def.SettingsDefinition] =
  assemblySettings(assemblyName, includeScala = false)

lazy val componentArtifacts = taskKey[List[(File, String)]]("component artifacts")

lazy val modelArtifacts = taskKey[List[(File, String)]]("model artifacts")

lazy val devArtifacts = taskKey[List[(File, String)]]("dev artifacts")

lazy val managerArtifacts = taskKey[List[(File, String)]]("manager artifacts")

def filterDevConfigArtifacts(files: Seq[(File, String)]) = {
  val devConfigFiles = Set("dev-tables-definition.sql", "dev-application.conf", "dev-oauth2-users.conf")
  files.filterNot { case (file, _) => devConfigFiles.contains(file.getName) }
}

lazy val distribution: Project = sbt
  .Project("dist", file("nussknacker-dist"))
  .settings(commonSettings)
  .enablePlugins(JavaAgent, SbtNativePackager, JavaServerAppPackaging)
  .settings(
    managerArtifacts                         := {
      List(
        (flinkDeploymentManager / assembly).value        -> "managers/nussknacker-flink-manager.jar",
        (liteK8sDeploymentManager / assembly).value      -> "managers/lite-k8s-manager.jar",
        (liteEmbeddedDeploymentManager / assembly).value -> "managers/lite-embedded-manager.jar",
      )
    },
    componentArtifacts                       := {
      List(
        (flinkBaseComponents / assembly).value           -> "components/flink/flinkBase.jar",
        (flinkBaseUnboundedComponents / assembly).value  -> "components/flink/flinkBaseUnbounded.jar",
        (flinkKafkaComponents / assembly).value          -> "components/flink/flinkKafka.jar",
        (flinkTableApiComponents / assembly).value       -> "components/flink-table/flinkTable.jar",
        (liteBaseComponents / assembly).value            -> "components/lite/liteBase.jar",
        (liteKafkaComponents / assembly).value           -> "components/lite/liteKafka.jar",
        (liteRequestResponseComponents / assembly).value -> "components/lite/liteRequestResponse.jar",
        (openapiComponents / assembly).value             -> "components/common/openapi.jar",
        (sqlComponents / assembly).value                 -> "components/common/sql.jar",
      )
    },
    modelArtifacts                           := {
      List(
        (defaultModel / assembly).value  -> "model/defaultModel.jar",
        (flinkExecutor / assembly).value -> "model/flinkExecutor.jar",
      )
    },
    devArtifacts                             := {
      modelArtifacts.value ++ List(
        (flinkDevModel / assembly).value                  -> "model/devModel.jar",
        (flinkPeriodicDeploymentManager / assembly).value -> "managers/nussknacker-flink-periodic-manager.jar",
      )
    },
    Universal / packageName                  := ("nussknacker" + "-" + version.value),
    Universal / mappings                     := {
      val universalMappingsWithDevConfigFilter =
        if (addDevArtifacts) (Universal / mappings).value
        else filterDevConfigArtifacts((Universal / mappings).value)

      universalMappingsWithDevConfigFilter ++
        (managerArtifacts).value ++
        (componentArtifacts).value ++
        (if (addDevArtifacts)
           Seq((developmentTestsDeploymentManager / assembly).value -> "managers/development-tests-manager.jar")
         else Nil) ++
        (if (addDevArtifacts) (devArtifacts).value: @sbtUnchecked
         else (modelArtifacts).value: @sbtUnchecked) ++
        (flinkExecutor / additionalBundledArtifacts).value
    },
    Universal / packageZipTarball / mappings := {
      val universalMappingsWithDevConfigFilter =
        if (addDevArtifacts) (Universal / mappings).value
        else filterDevConfigArtifacts((Universal / mappings).value)
      // we don't want docker-* stuff in .tgz
      universalMappingsWithDevConfigFilter filterNot { case (file, _) =>
        file.getName.startsWith("docker-") || file.getName.contains("entrypoint.sh")
      }
    },
    publishArtifact                          := false,
    javaAgents += JavaAgent("io.prometheus.jmx" % "jmx_prometheus_javaagent" % jmxPrometheusJavaagentV % "dist"),
    SettingsHelper.makeDeploymentSettings(Universal, Universal / packageZipTarball, "tgz")
  )
  .settings(distDockerSettings)
  .dependsOn(designer)

def engine(name: String) = file(s"engine/$name")

def flink(name: String) = engine(s"flink/$name")

def lite(name: String) = engine(s"lite/$name")

def development(name: String) = engine(s"development/$name")

def component(name: String) = file(s"components/$name")

def utils(name: String) = file(s"utils/$name")

def itSettings() = {
  Defaults.itSettings ++ Seq(IntegrationTest / testOptions += scalaTestReports)
}

lazy val requestResponseRuntime = (project in lite("request-response/runtime"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-request-response-runtime",
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka" %% "akka-http"         % akkaHttpV,
        "com.typesafe.akka" %% "akka-stream"       % akkaV,
        "com.typesafe.akka" %% "akka-testkit"      % akkaV     % Test,
        "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % Test
      )
    }
  )
  .dependsOn(
    liteEngineRuntime,
    requestResponseComponentsApi,
    httpUtils                      % Provided,
    testUtils                      % Test,
    componentsUtils                % Test,
    requestResponseComponentsUtils % Test,
    liteBaseComponents             % Test,
    liteRequestResponseComponents  % Test
  )

lazy val flinkDeploymentManager = (project in flink("management"))
  .configs(IntegrationTest)
  .settings(commonSettings)
  .settings(itSettings())
  .settings(assemblyNoScala("nussknacker-flink-manager.jar"): _*)
  .settings(publishAssemblySettings: _*)
  .settings(
    name                                            := "nussknacker-flink-manager",
    IntegrationTest / Keys.test                     := (IntegrationTest / Keys.test)
      .dependsOn(
        flinkExecutor / Compile / assembly,
        flinkExecutor / prepareItLibs,
        flinkDevModel / Compile / assembly,
        flinkDevModelJava / Compile / assembly,
        flinkTableApiComponents / Compile / assembly,
        flinkBaseComponents / Compile / assembly,
        flinkBaseUnboundedComponents / Compile / assembly,
        flinkKafkaComponents / Compile / assembly,
      )
      .value,
    // flink cannot run tests and deployment concurrently
    IntegrationTest / parallelExecution             := false,
    libraryDependencies ++= {
      Seq(
        "org.typelevel"          %% "cats-core"                  % catsV          % Provided,
        "org.apache.flink"        % "flink-streaming-java"       % flinkV         % flinkScope
          excludeAll (
            ExclusionRule("log4j", "log4j"),
            ExclusionRule("org.slf4j", "slf4j-log4j12"),
            ExclusionRule("com.esotericsoftware", "kryo-shaded"),
          ),
        "org.apache.flink"        % "flink-statebackend-rocksdb" % flinkV         % flinkScope,
        "com.softwaremill.retry" %% "retry"                      % "0.3.6",
        "org.wiremock"            % "wiremock"                   % wireMockV      % Test,
        "org.scalatestplus"      %% "mockito-5-10"               % scalaTestPlusV % Test,
      ) ++ flinkLibScalaDeps(scalaVersion.value, Some(flinkScope))
    },
    // override scala-collection-compat from com.softwaremill.retry:retry
    dependencyOverrides += "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionsCompatV
  )
  .dependsOn(
    deploymentManagerApi % Provided,
    scenarioCompiler     % Provided,
    componentsApi        % Provided,
    httpUtils            % Provided,
    flinkScalaUtils      % Provided,
    flinkTestUtils       % IntegrationTest,
    kafkaTestUtils       % "it,test"
  )

lazy val flinkPeriodicDeploymentManager = (project in flink("management/periodic"))
  .settings(commonSettings)
  .settings(assemblyNoScala("nussknacker-flink-periodic-manager.jar"): _*)
  .settings(publishAssemblySettings: _*)
  .settings(
    name := "nussknacker-flink-periodic-manager",
    libraryDependencies ++= {
      Seq(
        "org.typelevel"       %% "cats-core"                       % catsV                % Provided,
        "com.typesafe.slick"  %% "slick"                           % slickV               % Provided,
        "com.typesafe.slick"  %% "slick-hikaricp"                  % slickV               % "provided, test",
        "com.github.tminglei" %% "slick-pg"                        % slickPgV,
        "org.hsqldb"           % "hsqldb"                          % hsqldbV              % Test,
        "org.flywaydb"         % "flyway-core"                     % flywayV              % Provided,
        "com.cronutils"        % "cron-utils"                      % cronParserV,
        "com.typesafe.akka"   %% "akka-actor"                      % akkaV,
        "com.typesafe.akka"   %% "akka-testkit"                    % akkaV                % Test,
        "com.dimafeng"        %% "testcontainers-scala-scalatest"  % testContainersScalaV % Test,
        "com.dimafeng"        %% "testcontainers-scala-postgresql" % testContainersScalaV % Test,
      )
    }
  )
  .dependsOn(
    flinkDeploymentManager,
    deploymentManagerApi % Provided,
    scenarioCompiler     % Provided,
    componentsApi        % Provided,
    httpUtils            % Provided,
    testUtils            % Test
  )

lazy val flinkMetricsDeferredReporter = (project in flink("metrics-deferred-reporter"))
  .settings(commonSettings)
  .settings(
    name       := "nussknacker-flink-metrics-deferred-reporter",
    crossPaths := false,
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" % "flink-streaming-java" % flinkV % Provided
      )
    },
  )

lazy val flinkDevModel = (project in flink("management/dev-model"))
  .settings(commonSettings)
  .settings(assemblyNoScala("devModel.jar"): _*)
  .settings(
    name := "nussknacker-flink-dev-model",
    libraryDependencies ++= {
      Seq(
        "com.cronutils"    % "cron-utils"           % cronParserV,
        "javax.validation" % "validation-api"       % javaxValidationApiV,
        "org.apache.flink" % "flink-streaming-java" % flinkV % Provided,
        "org.apache.flink" % "flink-runtime"        % flinkV % Compile classifier "tests"
      )
    }
  )
  .dependsOn(
    extensionsApi,
    commonComponents,
    flinkSchemedKafkaComponentsUtils,
    flinkComponentsUtils % Provided,
    // We use some components for testing with embedded engine, because of that we need dependency to this api
    // It has to be in the default, Compile scope because all components are eagerly loaded so it will be loaded also
    // on the Flink side where this library is missing
    liteComponentsApi,
    componentsUtils      % Provided,
    // TODO: NodeAdditionalInfoProvider & ComponentExtractor should probably be moved to API?
    scenarioCompiler     % Provided,
    flinkExecutor        % Test,
    flinkTestUtils       % Test,
    kafkaTestUtils       % Test
  )

lazy val flinkDevModelJava = (project in flink("management/dev-model-java"))
  .settings(commonSettings)
  .settings(assemblyNoScala("devModelJava.jar"): _*)
  .settings(
    name := "nussknacker-flink-dev-model-java",
    libraryDependencies ++= {
      Seq(
        "org.scala-lang.modules" %% "scala-java8-compat"   % scalaCompatV,
        "org.apache.flink"        % "flink-streaming-java" % flinkV % Provided
      )
    }
  )
  .dependsOn(
    extensionsApi,
    flinkComponentsUtils % Provided
  )

lazy val flinkTests = (project in flink("tests"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-flink-tests",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" % "flink-connector-base"       % flinkV               % Test,
        "org.apache.flink" % "flink-streaming-java"       % flinkV               % Test,
        "org.apache.flink" % "flink-statebackend-rocksdb" % flinkV               % Test,
        "org.apache.flink" % "flink-connector-kafka"      % flinkConnectorKafkaV % Test,
        "org.apache.flink" % "flink-json"                 % flinkV               % Test
      )
    }
  )
  .dependsOn(
    defaultModel                 % Test,
    flinkExecutor                % Test,
    flinkKafkaComponents         % Test,
    flinkBaseComponents          % Test,
    flinkBaseUnboundedComponents % Test,
    flinkTableApiComponents      % Test,
    flinkTestUtils               % Test,
    kafkaTestUtils               % Test,
    flinkComponentsTestkit       % Test,
    // for local development
    designer                     % Test,
    deploymentManagerApi         % Test
  )

lazy val defaultModel = (project in (file("defaultModel")))
  .settings(commonSettings)
  .settings(assemblyNoScala("defaultModel.jar"): _*)
  .settings(publishAssemblySettings: _*)
  .settings(
    name := "nussknacker-default-model",
    libraryDependencies ++= {
      Seq(
        "org.scalatest" %% "scalatest" % scalaTestV % Test
      )
    }
  )
  .dependsOn(defaultHelpers, extensionsApi % Provided)

lazy val flinkExecutor = (project in flink("executor"))
  .settings(commonSettings)
  .settings(itSettings())
  .settings(assemblyNoScala("flinkExecutor.jar"): _*)
  .settings(publishAssemblySettings: _*)
  .settings(
    name                        := "nussknacker-flink-executor",
    IntegrationTest / Keys.test := (IntegrationTest / Keys.test)
      .dependsOn(
        ThisScope / prepareItLibs
      )
      .value,
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" % "flink-streaming-java"       % flinkV % Provided,
        "org.apache.flink" % "flink-runtime"              % flinkV % Provided,
        "org.apache.flink" % "flink-statebackend-rocksdb" % flinkV % Provided,
        "org.apache.flink" % "flink-metrics-dropwizard"   % flinkV % Provided,
      )
    },
    prepareItLibs               := {
      val workTarget = (ThisScope / baseDirectory).value / "target" / "it-libs"
      val artifacts  = (ThisScope / additionalBundledArtifacts).value
      IO.copy(artifacts.map { case (source, target) => (source, workTarget / target) })
    },
    additionalBundledArtifacts  := {
      createClasspathBasedMapping(
        (Compile / managedClasspath).value,
        "org.apache.flink",
        "flink-metrics-dropwizard",
        "flink-dropwizard-metrics-deps/flink-metrics-dropwizard.jar"
      ) ++
        createClasspathBasedMapping(
          (Compile / managedClasspath).value,
          "io.dropwizard.metrics",
          "metrics-core",
          "flink-dropwizard-metrics-deps/dropwizard-metrics-core.jar"
        )
    }.toList,
  )
  .dependsOn(flinkComponentsUtils, scenarioCompiler, flinkExtensionsApi, flinkTestUtils % Test)

lazy val scenarioCompiler = (project in file("scenario-compiler"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-scenario-compiler",
    libraryDependencies ++= {
      Seq(
        "org.typelevel"          %% "cats-effect"                   % catsEffectV,
        "org.scala-lang.modules" %% "scala-java8-compat"            % scalaCompatV,
        "org.apache.avro"         % "avro"                          % avroV          % Test,
        "org.scalacheck"         %% "scalacheck"                    % scalaCheckV    % Test,
        "com.cronutils"           % "cron-utils"                    % cronParserV    % Test,
        "org.scalatestplus"      %% s"scalacheck-$scalaCheckVshort" % scalaTestPlusV % Test
      )
    }
  )
  .dependsOn(componentsUtils, utilsInternal, mathUtils, testUtils % Test)

lazy val benchmarks = (project in file("benchmarks"))
  .settings(commonSettings)
  .enablePlugins(JmhPlugin)
  .settings(
    name                                 := "nussknacker-benchmarks",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" % "flink-streaming-java"           % flinkV exclude ("com.esotericsoftware", "kryo-shaded"),
        "org.apache.flink" % "flink-runtime"                  % flinkV,
        "com.dimafeng"    %% "testcontainers-scala-scalatest" % testContainersScalaV % Test,
      )
    },
    Jmh / run / javaOptions ++= (
      if (System.getProperty("os.name").startsWith("Windows")) {
        // Allow long classpath on Windows, JMH requires that classpath and temp directory have common root path,
        // so we're always setting it in sbt's target directory (https://github.com/sbt/sbt-jmh/issues/241)
        Seq("-Djmh.separateClasspathJAR=true", "\"-Djava.io.tmpdir=" + target.value + "\"")
      } else
        Seq.empty
    ),
    // To avoid Intellij message that jmh generated classes are shared between main and test
    Jmh / classDirectory                 := (Test / classDirectory).value,
    Jmh / dependencyClasspath            := (Test / dependencyClasspath).value,
    Jmh / generateJmhSourcesAndResources := (Jmh / generateJmhSourcesAndResources).dependsOn(Test / compile).value,
  )
  .settings {
    // TODO: it'd be better to use scalaVersion here, but for some reason it's hard to disable existing task dynamically
    forScalaVersion(defaultScalaV) {
      case (2, 12) => doExecuteMainFromTestSources
      case (2, 13) => executeMainFromTestSourcesNotSupported
    }
  }
  .dependsOn(
    designer,
    extensionsApi,
    scenarioCompiler % "test->test;test->compile",
    flinkSchemedKafkaComponentsUtils,
    flinkExecutor,
    flinkBaseComponents,
    flinkBaseUnboundedComponents,
    testUtils        % Test
  )

lazy val doExecuteMainFromTestSources = Seq(
  (Test / runMain) := (Test / runMain)
    .dependsOn(distribution / Docker / publishLocal)
    .evaluated
)

lazy val executeMainFromTestSourcesNotSupported = Seq(
  (Test / runMain) := {
    streams.value.log.info(
      "E2E benchmarks are skipped for Scala 2.13 because Nu installation example is currently based on Scala 2.12"
    )
  }
)

lazy val kafkaUtils = (project in utils("kafka-utils"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-kafka-utils",
    libraryDependencies ++= {
      Seq(
        "org.apache.kafka" % "kafka-clients" % kafkaV
      )
    }
    // Depends on componentsApi because of dependency to NuExceptionInfo and NonTransientException -
    // lite kafka engine handles component exceptions in runtime part
  )
  .dependsOn(commonUtils % Provided, componentsApi % Provided)

lazy val kafkaComponentsUtils = (project in utils("kafka-components-utils"))
  .configs(IntegrationTest)
  .settings(commonSettings)
  .settings(itSettings())
  .settings(
    name := "nussknacker-kafka-components-utils",
    libraryDependencies ++= {
      Seq(
        "javax.validation" % "validation-api"                 % javaxValidationApiV,
        "com.dimafeng"    %% "testcontainers-scala-scalatest" % testContainersScalaV % IntegrationTest,
        "com.dimafeng"    %% "testcontainers-scala-kafka"     % testContainersScalaV % IntegrationTest
      )
    }
  )
  .dependsOn(kafkaUtils, componentsUtils % Provided, testUtils % "it, test")

lazy val schemedKafkaComponentsUtils = (project in utils("schemed-kafka-components-utils"))
  .configs(ExternalDepsTests)
  .settings(externalDepsTestsSettings)
  .settings(commonSettings)
  .settings(
    name := "nussknacker-schemed-kafka-components-utils",
    libraryDependencies ++= {
      Seq(
        "io.confluent"                  % "kafka-json-schema-provider"      % confluentV excludeAll (
          ExclusionRule("commons-logging", "commons-logging"),
          ExclusionRule("log4j", "log4j"),
          ExclusionRule("org.slf4j", "slf4j-log4j12"),
        ),
        "io.confluent"                  % "kafka-avro-serializer"           % confluentV excludeAll (
          ExclusionRule("commons-logging", "commons-logging"),
          ExclusionRule("log4j", "log4j"),
          ExclusionRule("org.slf4j", "slf4j-log4j12")
        ),
        "com.microsoft.azure"           % "azure-schemaregistry-kafka-avro" % azureKafkaSchemaRegistryV excludeAll (
          ExclusionRule("com.azure", "azure-core-http-netty")
        ),
        "com.azure"                     % "azure-data-schemaregistry"       % azureSchemaRegistryV excludeAll (
          ExclusionRule("com.azure", "azure-core-http-netty")
        ),
        "com.azure"                     % "azure-identity"                  % azureIdentityV excludeAll (
          ExclusionRule("com.azure", "azure-core-http-netty")
        ),
        // we use azure-core-http-okhttp instead of azure-core-http-netty to avoid netty version collisions
        // TODO: switch to jdk implementation after releasing it: https://github.com/Azure/azure-sdk-for-java/issues/27065
        "com.azure"                     % "azure-core-http-okhttp"          % "1.11.9",
        // it is workaround for missing VerifiableProperties class - see https://github.com/confluentinc/schema-registry/issues/553
        "org.apache.kafka"             %% "kafka"                           % kafkaV     % Provided excludeAll (
          ExclusionRule("log4j", "log4j"),
          ExclusionRule("org.slf4j", "slf4j-log4j12")
        ),
        "tech.allegro.schema.json2avro" % "converter"                       % "0.2.15",
        "org.scala-lang.modules"       %% "scala-collection-compat"         % scalaCollectionsCompatV,
        "org.scalatest"                %% "scalatest"                       % scalaTestV % Test
      )
    },
  )
  .dependsOn(
    componentsUtils  % Provided,
    kafkaComponentsUtils,
    scenarioCompiler % "test->test;test->compile",
    kafkaTestUtils   % Test,
    jsonUtils
  )

lazy val flinkSchemedKafkaComponentsUtils = (project in flink("schemed-kafka-components-utils"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-flink-schemed-kafka-components-utils",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" % "flink-streaming-java"  % flinkV               % Provided,
        "org.apache.flink" % "flink-avro"            % flinkV,
        "org.apache.flink" % "flink-connector-kafka" % flinkConnectorKafkaV % Test,
        "org.scalatest"   %% "scalatest"             % scalaTestV           % Test
      )
    }
  )
  .dependsOn(
    schemedKafkaComponentsUtils % "compile;test->test",
    flinkKafkaComponentsUtils,
    flinkExtensionsApi          % Provided,
    flinkComponentsUtils        % Provided,
    componentsUtils             % Provided,
    kafkaTestUtils              % Test,
    flinkTestUtils              % Test,
    flinkExecutor               % Test
  )

lazy val flinkKafkaComponentsUtils = (project in flink("kafka-components-utils"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-flink-kafka-components-utils",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" % "flink-connector-kafka" % flinkConnectorKafkaV,
        "org.apache.flink" % "flink-streaming-java"  % flinkV     % Provided,
        "org.scalatest"   %% "scalatest"             % scalaTestV % Test
      )
    }
  )
  .dependsOn(
    componentsApi        % Provided,
    kafkaComponentsUtils,
    flinkComponentsUtils % Provided,
    flinkExtensionsApi   % Provided,
    componentsUtils      % Provided,
    flinkExecutor        % Test,
    kafkaTestUtils       % Test,
    flinkTestUtils       % Test
  )

lazy val kafkaTestUtils = (project in utils("kafka-test-utils"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-kafka-test-utils",
    libraryDependencies ++= {
      Seq(
        "org.apache.kafka" %% "kafka"            % kafkaV excludeAll (
          ExclusionRule("log4j", "log4j"),
          ExclusionRule("org.slf4j", "slf4j-log4j12")
        ),
        "org.slf4j"         % "log4j-over-slf4j" % slf4jV
      )
    }
  )
  .dependsOn(testUtils, kafkaUtils, commonUtils % Provided)

// This module should be provided by one module - interpreter, because it is is the common module shared between designer and runtime
lazy val componentsUtils = (project in utils("components-utils"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-components-utils",
    libraryDependencies ++= forScalaVersion(scalaVersion.value) {
      case (2, 13) => Seq("org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4" % Test)
      case _       => Seq()
    }
  )
  .dependsOn(componentsApi, commonUtils, testUtils % Test)

//this should be only added in scope test - 'module % Test' or as dependency to another test module
lazy val componentsTestkit = (project in utils("components-testkit"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-components-testkit",
  )
  .dependsOn(componentsApi, scenarioApi, commonUtils, testUtils, scenarioCompiler)

//this should be only added in scope test - 'module % Test'
lazy val flinkComponentsTestkit = (project in utils("flink-components-testkit"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-flink-components-testkit",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" % "flink-streaming-java" % flinkV exclude ("com.esotericsoftware", "kryo-shaded"),
      )
    }
  )
  .dependsOn(
    componentsTestkit,
    flinkExecutor,
    flinkTestUtils,
    flinkBaseComponents,
    flinkBaseUnboundedComponents,
    defaultModel
  )

//this should be only added in scope test - 'module % Test'
lazy val liteComponentsTestkit = (project in utils("lite-components-testkit"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-lite-components-testkit",
  )
  .dependsOn(
    componentsTestkit,
    requestResponseRuntime,
    liteEngineRuntime,
    liteBaseComponents,
    liteKafkaComponents,
    liteRequestResponseComponents,
    defaultModel
  )

lazy val commonUtils = (project in utils("utils"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-utils",
    libraryDependencies ++= {
      Seq(
        "com.github.ben-manes.caffeine" % "caffeine"           % caffeineCacheV,
        "org.scala-lang.modules"       %% "scala-java8-compat" % scalaCompatV,
        "com.typesafe.scala-logging"   %% "scala-logging"      % scalaLoggingV,
        "commons-io"                    % "commons-io"         % flinkCommonsIOV,
        "org.slf4j"                     % "jul-to-slf4j"       % slf4jV,
        "com.iheart"                   %% "ficus"              % ficusV,
        "org.typelevel"                %% "cats-effect"        % catsEffectV,
      )
    }
  )
  .dependsOn(commonApi, componentsApi, testUtils % Test)

lazy val utilsInternal = (project in utils("utils-internal"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-utils-internal"
  )
  .dependsOn(commonUtils, extensionsApi, testUtils % Test)

// This module should be provided by one module - interpreter
lazy val mathUtils = (project in utils("math-utils"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-math-utils",
    libraryDependencies ++= Seq(
      "org.springframework" % "spring-expression" % springV,
    )
  )
  .dependsOn(componentsApi, testUtils % Test)

lazy val defaultHelpers = (project in utils("default-helpers"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-default-helpers"
  )
  .dependsOn(mathUtils, testUtils % Test, scenarioCompiler % "test->test;test->compile")

lazy val testUtils = (project in utils("test-utils"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-test-utils",
    libraryDependencies ++= {
      Seq(
        "com.github.pathikrit"          %% "better-files"              % betterFilesV,
        "org.scalatest"                 %% "scalatest"                 % scalaTestV,
        "com.typesafe.scala-logging"    %% "scala-logging"             % scalaLoggingV,
        "com.typesafe"                   % "config"                    % configV,
        "org.typelevel"                 %% "cats-core"                 % catsV,
        "ch.qos.logback"                 % "logback-classic"           % logbackV,
        "org.springframework"            % "spring-jcl"                % springV,
        "commons-io"                     % "commons-io"                % flinkCommonsIOV,
        "org.scala-lang.modules"        %% "scala-collection-compat"   % scalaCollectionsCompatV,
        "com.softwaremill.sttp.client3" %% "slf4j-backend"             % sttpV,
        "org.typelevel"                 %% "cats-effect"               % catsEffectV,
        "io.circe"                      %% "circe-parser"              % circeV,
        "org.testcontainers"             % "testcontainers"            % testContainersJavaV,
        "com.dimafeng"                  %% "testcontainers-scala-core" % testContainersScalaV,
        "com.lihaoyi"                   %% "ujson"                     % ujsonV,
        // This lib produces more descriptive errors during validation than everit
        "com.networknt"                  % "json-schema-validator"     % "1.5.1",
        "com.softwaremill.sttp.tapir"   %% "tapir-core"                % tapirV,
        "com.softwaremill.sttp.tapir"   %% "tapir-apispec-docs"        % tapirV,
        "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml"        % openapiCirceYamlV,
      ) ++ restAssuredDependency(scalaVersion.value)
    }
  )

// rest-assured is not cross compiled, so we have to use different versions
def restAssuredDependency(scalaVersion: String) = forScalaVersion(scalaVersion) {
  case (2, 12) => Seq("io.rest-assured" % "scala-support" % "4.0.0")
  case (2, 13) => Seq("io.rest-assured" % "scala-support" % "5.5.0")
}

lazy val jsonUtils = (project in utils("json-utils"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-json-utils",
    libraryDependencies ++= Seq(
      "io.swagger.parser.v3" % "swagger-parser"     % swaggerParserV excludeAll (
        ExclusionRule(organization = "commons-logging"),
        ExclusionRule(organization = "javax.mail"),
        ExclusionRule(organization = "javax.validation"),
        ExclusionRule(organization = "jakarta.activation"),
        ExclusionRule(organization = "jakarta.validation")
      ),
      "com.github.erosb"     % "everit-json-schema" % everitSchemaV exclude ("commons-logging", "commons-logging"),
    )
  )
  .dependsOn(componentsUtils % Provided, testUtils % Test)

// Similar to components-utils, this module should be provided by one module - flinkExecutor
lazy val flinkComponentsUtils = (project in flink("components-utils"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-flink-components-utils",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" % "flink-streaming-java" % flinkV % Provided,
      )
    }
  )
  .dependsOn(
    flinkComponentsApi,
    flinkExtensionsApi % Provided,
    mathUtils          % Provided,
    flinkScalaUtils,
    componentsUtils    % Provided,
    testUtils          % Test
  )

lazy val flinkScalaUtils = (project in flink("scala-utils"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-flink-scala-utils",
    libraryDependencies ++= {
      Seq(
        "org.scala-lang"          % "scala-reflect"           % scalaVersion.value,
        "org.apache.flink"        % "flink-streaming-java"    % flinkV     % Provided,
        "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionsCompatV,
        "org.scalatest"          %% "scalatest"               % scalaTestV % Test,
      ) ++ flinkLibScalaDeps(scalaVersion.value, Some("provided"))
    }
  )
  .dependsOn(testUtils % Test)

lazy val flinkTestUtils = (project in flink("test-utils"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-flink-test-utils",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" % "flink-streaming-java"           % flinkV % Provided,
        // intellij has some problems with provided...
        "org.apache.flink" % "flink-statebackend-rocksdb"     % flinkV,
        "org.apache.flink" % "flink-test-utils"               % flinkV excludeAll (
          // we use logback in NK
          ExclusionRule("org.apache.logging.log4j", "log4j-slf4j-impl")
        ),
        "org.apache.flink" % "flink-runtime"                  % flinkV % Compile classifier "tests",
        "org.apache.flink" % "flink-metrics-dropwizard"       % flinkV,
        "com.dimafeng"    %% "testcontainers-scala-scalatest" % testContainersScalaV,
        "com.dimafeng"    %% "testcontainers-scala-kafka"     % testContainersScalaV,
      ) ++ flinkLibScalaDeps(scalaVersion.value)
    }
  )
  .dependsOn(testUtils, flinkComponentsUtils, flinkExtensionsApi, componentsUtils, scenarioCompiler)

lazy val requestResponseComponentsUtils = (project in lite("request-response/components-utils"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-request-response-components-utils"
  )
  .dependsOn(componentsUtils % Provided, requestResponseComponentsApi % Provided, testUtils % Test)

lazy val requestResponseComponentsApi = (project in lite("request-response/components-api"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-request-response-components-api"
  )
  .dependsOn(liteComponentsApi, jsonUtils)

lazy val liteComponentsApi = (project in lite("components-api"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-lite-components-api",
  )
  .dependsOn(componentsApi)

lazy val liteBaseComponents = (project in lite("components/base"))
  .settings(commonSettings)
  .settings(assemblyNoScala("liteBase.jar"): _*)
  .settings(publishAssemblySettings: _*)
  .settings(
    name := "nussknacker-lite-base-components",
  )
  .dependsOn(
    commonComponents,
    liteComponentsApi % Provided,
    componentsUtils   % Provided,
    testUtils         % Test,
    liteEngineRuntime % Test
  )

lazy val liteKafkaComponents: Project = (project in lite("components/kafka"))
  .settings(commonSettings)
  .settings(assemblyNoScala("liteKafka.jar"): _*)
  .settings(publishAssemblySettings: _*)
  .settings(
    name := "nussknacker-lite-kafka-components"
    // TODO: avroUtils brings kafkaUtils to assembly, which is superfluous, as we already have it in engine...
  )
  .dependsOn(
    liteEngineKafkaComponentsApi % Provided,
    liteComponentsApi            % Provided,
    componentsUtils              % Provided,
    schemedKafkaComponentsUtils
  )

lazy val liteKafkaComponentsTests: Project = (project in lite("components/kafka-tests"))
  .configs(ExternalDepsTests)
  .settings(externalDepsTestsSettings)
  .settings(commonSettings)
  .settings(
    name := "nussknacker-lite-kafka-components-tests",
    libraryDependencies ++= {
      Seq(
        "org.scalacheck"    %% "scalacheck"                    % scalaCheckV    % Test,
        "org.scalatestplus" %% s"scalacheck-$scalaCheckVshort" % scalaTestPlusV % Test,
        "org.scalatestplus" %% "mockito-5-10"                  % scalaTestPlusV % Test,
      )
    },
  )
  .dependsOn(liteEngineKafkaComponentsApi % Test, componentsUtils % Test, liteComponentsTestkit % Test)

lazy val liteRequestResponseComponents = (project in lite("components/request-response"))
  .settings(commonSettings)
  .settings(assemblyNoScala("liteRequestResponse.jar"): _*)
  .settings(publishAssemblySettings: _*)
  .settings(
    name := "nussknacker-lite-request-response-components",
  )
  .dependsOn(
    requestResponseComponentsApi % Provided,
    liteComponentsApi            % Provided,
    componentsUtils              % Provided,
    jsonUtils,
    requestResponseComponentsUtils
  )

lazy val liteRequestResponseComponentsTests: Project = (project in lite("components/request-response-tests"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-lite-request-response-components-tests",
    libraryDependencies ++= {
      Seq(
        "org.scalacheck"    %% "scalacheck"                    % scalaCheckV    % Test,
        "org.scalatestplus" %% s"scalacheck-$scalaCheckVshort" % scalaTestPlusV % Test
      )
    },
  )
  .dependsOn(requestResponseComponentsApi % Test, componentsUtils % Test, liteComponentsTestkit % Test)

lazy val liteEngineRuntime = (project in lite("runtime"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-lite-runtime",
    libraryDependencies ++= {
      Seq(
        "io.dropwizard.metrics5"         % "metrics-core"         % dropWizardV,
        "io.dropwizard.metrics5"         % "metrics-influxdb"     % dropWizardV,
        "io.dropwizard.metrics5"         % "metrics-jmx"          % dropWizardV,
        "com.softwaremill.sttp.client3" %% "core"                 % sttpV,
        "ch.qos.logback"                 % "logback-classic"      % logbackV,
        "ch.qos.logback.contrib"         % "logback-json-classic" % logbackJsonV,
        "ch.qos.logback.contrib"         % "logback-jackson"      % logbackJsonV,
        "com.fasterxml.jackson.core"     % "jackson-databind"     % jacksonV,
        "com.typesafe.akka"             %% "akka-http"            % akkaHttpV
      )
    },
  )
  .dependsOn(liteComponentsApi, scenarioCompiler, testUtils % Test)

lazy val liteEngineKafkaIntegrationTest: Project = (project in lite("integration-test"))
  .configs(IntegrationTest)
  .settings(itSettings())
  .settings(commonSettings)
  .settings(
    name                        := "nussknacker-lite-runtime-app-integration-test",
    IntegrationTest / Keys.test := (IntegrationTest / Keys.test)
      .dependsOn(
        liteEngineRuntimeApp / Universal / stage,
        liteEngineRuntimeApp / Docker / publishLocal
      )
      .value,
    libraryDependencies ++= Seq(
      "com.dimafeng" %% "testcontainers-scala-scalatest" % testContainersScalaV % IntegrationTest,
      "com.dimafeng" %% "testcontainers-scala-kafka"     % testContainersScalaV % IntegrationTest
    )
  )
  .dependsOn(
    scenarioCompiler            % IntegrationTest,
    schemedKafkaComponentsUtils % IntegrationTest,
    testUtils                   % IntegrationTest,
    kafkaTestUtils              % IntegrationTest,
    httpUtils                   % IntegrationTest
  )

lazy val liteEngineKafkaComponentsApi = (project in lite("kafka/components-api"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-lite-kafka-components-api",
    libraryDependencies ++= Seq(
      "org.apache.kafka" % "kafka-clients" % kafkaV
    )
  )
  .dependsOn(liteComponentsApi)

lazy val liteEngineRuntimeAppDockerSettings = {
  val workingDir = "/opt/nussknacker"

  commonDockerSettings ++ Seq(
    dockerEntrypoint                     := Seq(s"$workingDir/bin/nu-engine-entrypoint.sh"),
    Docker / defaultLinuxInstallLocation := workingDir,
    packageName                          := liteEngineKafkaRuntimeDockerPackageName,
    dockerLabels                         := Map(
      "version" -> version.value,
      "scala"   -> scalaVersion.value,
    )
  )
}

lazy val liteEngineKafkaRuntime: Project = (project in lite("kafka/runtime"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-lite-kafka-runtime"
  )
  .dependsOn(
    liteEngineRuntime,
    liteEngineKafkaComponentsApi,
    kafkaUtils,
    testUtils          % Test,
    kafkaTestUtils     % Test,
    liteBaseComponents % Test
  )

lazy val liteEngineRuntimeApp: Project = (project in lite("runtime-app"))
  .settings(commonSettings)
  .settings(liteEngineRuntimeAppDockerSettings)
  .enablePlugins(JavaAgent, SbtNativePackager, JavaServerAppPackaging)
  .settings(
    name := "nussknacker-lite-runtime-app",
    Universal / mappings ++= Seq(
      (defaultModel / assembly).value                  -> "model/defaultModel.jar",
      (liteBaseComponents / assembly).value            -> "components/lite/liteBase.jar",
      (liteKafkaComponents / assembly).value           -> "components/lite/liteKafka.jar",
      (liteRequestResponseComponents / assembly).value -> "components/lite/liteRequestResponse.jar",
      (openapiComponents / assembly).value             -> "components/common/openapi.jar",
      (sqlComponents / assembly).value                 -> "components/common/sql.jar",
    ),
    javaAgents += JavaAgent("io.prometheus.jmx" % "jmx_prometheus_javaagent" % jmxPrometheusJavaagentV % "dist"),
    libraryDependencies ++= Seq(
      "commons-io"                     % "commons-io"           % flinkCommonsIOV,
      "com.lightbend.akka.management" %% "akka-management"      % akkaManagementV,
      // spray-json module is used by akka-management - must be explicit, same version as rest of akka-http because otherwise ManifestInfo.checkSameVersion reports error
      "com.typesafe.akka"             %% "akka-http-spray-json" % akkaHttpV,
      "com.typesafe.akka"             %% "akka-slf4j"           % akkaV,
      "com.typesafe.akka"             %% "akka-testkit"         % akkaV     % Test,
      "com.typesafe.akka"             %% "akka-http-testkit"    % akkaHttpV % Test,
    ),
  )
  .dependsOn(liteEngineKafkaRuntime, requestResponseRuntime)

lazy val liteEmbeddedDeploymentManager = (project in lite("embeddedDeploymentManager"))
  .enablePlugins()
  .settings(commonSettings)
  .settings(assemblyNoScala("lite-embedded-manager.jar"): _*)
  .settings(publishAssemblySettings: _*)
  .settings(
    name := "nussknacker-lite-embedded-deploymentManager",
  )
  .dependsOn(
    liteDeploymentManager,
    deploymentManagerApi          % Provided,
    liteEngineKafkaRuntime,
    requestResponseRuntime,
    liteKafkaComponents           % Test,
    liteRequestResponseComponents % Test,
    componentsUtils               % Test,
    testUtils                     % Test,
    kafkaTestUtils                % Test,
    schemedKafkaComponentsUtils   % "test->test"
  )

lazy val developmentTestsDeploymentManager = (project in development("deploymentManager"))
  .enablePlugins()
  .settings(commonSettings)
  .settings(assemblyNoScala("developmentTestsManager.jar"): _*)
  .settings(
    name := "nussknacker-development-tests-manager",
  )
  .dependsOn(
    deploymentManagerApi % Provided,
    flinkDeploymentManager, // for accessing Flink property config
    scenarioCompiler,       // for run tests on the Flink
    testUtils % Test
  )

lazy val developmentTestsDeployManagerArtifacts =
  taskKey[List[(File, String)]]("development tests deployment manager artifacts")

developmentTestsDeployManagerArtifacts := List(
  (developmentTestsDeploymentManager / assembly).value -> "managers/developmentTestsManager.jar"
)

lazy val buildAndImportRuntimeImageToK3d = taskKey[Unit]("Import runtime image into k3d cluster")

lazy val liteK8sDeploymentManager = (project in lite("k8sDeploymentManager"))
  .configs(ExternalDepsTests)
  .settings(externalDepsTestsSettings)
  .enablePlugins()
  .settings(commonSettings)
  .settings(assemblyNoScala("lite-k8s-manager.jar"): _*)
  .settings(publishAssemblySettings: _*)
  .settings(
    name                            := "nussknacker-lite-k8s-deploymentManager",
    libraryDependencies ++= {
      Seq(
        // From version 4.0.0 onwards, skuber uses pekko instead of akka, so we need to migrate to pekko first
        "io.github.hagay3"           %% "skuber"        % "3.2" exclude ("commons-logging", "commons-logging"),
        "com.github.julien-truffaut" %% "monocle-core"  % monocleV,
        "com.github.julien-truffaut" %% "monocle-macro" % monocleV,
        "com.typesafe.akka"          %% "akka-slf4j"    % akkaV     % Test,
        "org.wiremock"                % "wiremock"      % wireMockV % Test,
      )
    },
    buildAndImportRuntimeImageToK3d := {
      (liteEngineRuntimeApp / Docker / publishLocal).value
      "k3d --version" #&& s"k3d image import touk/nussknacker-lite-runtime-app:${version.value}_scala-${CrossVersion
          .binaryScalaVersion(scalaVersion.value)}" #|| "echo 'No k3d installed!'" !
    },
    ExternalDepsTests / Keys.test   := (ExternalDepsTests / Keys.test)
      .dependsOn(
        buildAndImportRuntimeImageToK3d
      )
      .value
  )
  .dependsOn(liteDeploymentManager, deploymentManagerApi % Provided, testUtils % Test)

lazy val liteDeploymentManager = (project in lite("deploymentManager"))
  .enablePlugins()
  .settings(commonSettings)
  .settings(
    name := "nussknacker-lite-deploymentManager"
  )
  .dependsOn(
    liteEngineKafkaRuntime,       // for tests mechanism purpose
    requestResponseComponentsApi, // for rr scenario properties
    deploymentManagerApi % Provided
  )

lazy val componentsApi = (project in file("components-api"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-components-api",
    libraryDependencies ++= {
      Seq(
        "org.apache.commons"             % "commons-text"                     % flinkCommonsTextV,
        "org.typelevel"                 %% "cats-core"                        % catsV,
        "com.beachape"                  %% "enumeratum"                       % enumeratumV,
        "com.typesafe.scala-logging"    %% "scala-logging"                    % scalaLoggingV,
        "com.typesafe"                   % "config"                           % configV,
        "org.semver4j"                   % "semver4j"                         % "5.4.0",
        "javax.validation"               % "validation-api"                   % javaxValidationApiV,
        "org.scala-lang.modules"        %% "scala-collection-compat"          % scalaCollectionsCompatV,
        "com.iheart"                    %% "ficus"                            % ficusV,
        "org.springframework"            % "spring-core"                      % springV,
        "org.springframework"            % "spring-expression"                % springV        % Test,
        "com.google.code.findbugs"       % "jsr305"                           % findBugsV,
        "com.softwaremill.sttp.client3" %% "async-http-client-backend-future" % sttpV,
        "org.scalatestplus"             %% s"scalacheck-$scalaCheckVshort"    % scalaTestPlusV % Test
      )
    }
  )
  .dependsOn(commonApi, testUtils % Test)

// TODO: split into runtime extensions and designer extensions
lazy val extensionsApi = (project in file("extensions-api"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-extensions-api",
    libraryDependencies ++= Seq(
      "org.springframework"      % "spring-expression" % springV,
      // needed by scala-compiler for spring-expression...
      "com.google.code.findbugs" % "jsr305"            % findBugsV,
    )
  )
  .dependsOn(testUtils % Test, componentsApi, scenarioApi)

lazy val commonApi = (project in file("common-api"))
  .settings(commonSettings)
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings)
  .settings(
    name := "nussknacker-common-api",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionsCompatV,
      "io.circe"               %% "circe-parser"            % circeV,
      "io.circe"               %% "circe-generic"           % circeV,
      "io.circe"               %% "circe-generic-extras"    % circeGenericExtrasV,
      "org.scalatest"          %% "scalatest"               % scalaTestV % Test
    )
  )

lazy val buildInfoSettings = Seq(
  buildInfoKeys    := Seq[BuildInfoKey](name, version),
  buildInfoKeys ++= Seq[BuildInfoKey](
    "buildTime" -> java.time.LocalDateTime.now().toString,
    "gitCommit" -> git.gitHeadCommit.value.getOrElse("")
  ),
  buildInfoPackage := "pl.touk.nussknacker.engine.version",
  buildInfoOptions ++= Seq(BuildInfoOption.ToMap)
)

lazy val scenarioApi = (project in file("scenario-api"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-scenario-api",
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-lang3" % flinkCommonsLang3V,
    )
  )
  .dependsOn(commonApi, testUtils % Test)

lazy val security = (project in file("security"))
  .configs(IntegrationTest)
  .settings(commonSettings)
  .settings(itSettings())
  .settings(
    name := "nussknacker-security",
    libraryDependencies ++= Seq(
      "com.typesafe.akka"           %% "akka-http"                      % akkaHttpV,
      "com.typesafe.akka"           %% "akka-stream"                    % akkaV,
      "com.typesafe.akka"           %% "akka-http-testkit"              % akkaHttpV            % Test,
      "com.typesafe.akka"           %% "akka-testkit"                   % akkaV                % Test,
      "de.heikoseeberger"           %% "akka-http-circe"                % akkaHttpCirceV,
      "com.typesafe"                 % "config"                         % configV,
      "at.favre.lib"                 % "bcrypt"                         % bcryptV,
      // Packages below are only for plugin providers purpose
      "io.circe"                    %% "circe-core"                     % circeV,
      "com.github.jwt-scala"        %% "jwt-circe"                      % jwtCirceV,
      "com.typesafe.scala-logging"  %% "scala-logging"                  % scalaLoggingV,
      "com.auth0"                    % "jwks-rsa"                       % "0.22.1", // a tool library for reading a remote JWK store, not an Auth0 service dependency
      "com.softwaremill.sttp.tapir" %% "tapir-core"                     % tapirV,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"               % tapirV,
      "com.dimafeng"                %% "testcontainers-scala-scalatest" % testContainersScalaV % "it,test",
      "com.github.dasniko"           % "testcontainers-keycloak"        % "3.4.0"              % "it,test" excludeAll (
        ExclusionRule("commons-logging", "commons-logging"),
        // we're using testcontainers-scala which requires a proper junit4 dependency
        ExclusionRule("io.quarkus", "quarkus-junit4-mock")
      )
    )
  )
  .dependsOn(utilsInternal, httpUtils, testUtils % "it,test")

lazy val flinkComponentsApi = (project in flink("components-api"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-flink-components-api",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" % "flink-streaming-java" % flinkV % Provided,
      )
    }
  )
  .dependsOn(componentsApi)

lazy val flinkExtensionsApi = (project in flink("extensions-api"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-flink-extensions-api",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" % "flink-streaming-java" % flinkV % Provided,
      )
    }
  )
  .dependsOn(flinkComponentsApi, extensionsApi)

lazy val processReports = (project in file("designer/processReports"))
  .configs(IntegrationTest)
  .settings(commonSettings)
  .settings(itSettings())
  .settings(
    name := "nussknacker-process-reports",
    libraryDependencies ++= {
      Seq(
        "com.dimafeng" %% "testcontainers-scala-scalatest" % testContainersScalaV % "it,test",
        "com.dimafeng" %% "testcontainers-scala-influxdb"  % testContainersScalaV % "it,test",
        "org.influxdb"  % "influxdb-java"                  % "2.24"               % "it,test"
      )
    }
  )
  .dependsOn(httpUtils, commonUtils, testUtils % "it,test")

lazy val httpUtils = (project in utils("http-utils"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-http-utils",
    libraryDependencies ++= {
      Seq(
        "com.softwaremill.sttp.client3" %% "core"        % sttpV,
        "com.softwaremill.sttp.client3" %% "json-common" % sttpV,
        "com.softwaremill.sttp.client3" %% "circe"       % sttpV,
      )
    }
  )
  .dependsOn(componentsApi % Provided, testUtils % Test)

val swaggerParserV      = "2.1.15"
val swaggerIntegrationV = "2.2.10"

lazy val openapiComponents = (project in component("openapi"))
  .configs(IntegrationTest)
  .settings(itSettings())
  .settings(commonSettings)
  .settings(assemblyNoScala("openapi.jar"): _*)
  .settings(publishAssemblySettings: _*)
  .settings(
    name := "nussknacker-openapi",
    libraryDependencies ++= Seq(
      "io.swagger.core.v3" % "swagger-integration"          % swaggerIntegrationV excludeAll (
        ExclusionRule(organization = "jakarta.activation"),
        ExclusionRule(organization = "jakarta.validation")
      ),
      "io.netty"           % "netty-transport-native-epoll" % nettyV,
      "org.apache.flink"   % "flink-streaming-java"         % flinkV     % Provided,
      "org.scalatest"     %% "scalatest"                    % scalaTestV % "it,test"
    ),
  )
  .dependsOn(
    componentsUtils                % Provided,
    jsonUtils                      % Provided,
    httpUtils,
    requestResponseComponentsUtils % "it,test",
    flinkComponentsTestkit         % "it,test"
  )

lazy val sqlComponents = (project in component("sql"))
  .settings(commonSettings)
  .settings(assemblyNoScala("sql.jar"): _*)
  .settings(publishAssemblySettings: _*)
  .settings(
    name := "nussknacker-sql",
    libraryDependencies ++= Seq(
      "com.zaxxer"        % "HikariCP"                        % hikariCpV,
      //      It won't run on Java 16 as Hikari will fail while trying to load IgniteJdbcThinDriver https://issues.apache.org/jira/browse/IGNITE-14888
      "org.apache.ignite" % "ignite-core"                     % igniteV              % Test,
      "org.apache.ignite" % "ignite-indexing"                 % igniteV              % Test,
      "org.postgresql"    % "postgresql"                      % postgresV            % Test,
      "org.scalatest"    %% "scalatest"                       % scalaTestV           % Test,
      "org.hsqldb"        % "hsqldb"                          % hsqldbV              % Test,
      "com.dimafeng"     %% "testcontainers-scala-scalatest"  % testContainersScalaV % Test,
      "com.dimafeng"     %% "testcontainers-scala-postgresql" % testContainersScalaV % Test,
    ),
  )
  .dependsOn(
    componentsUtils       % Provided,
    componentsApi         % Provided,
    commonUtils           % Provided,
    liteComponentsTestkit % Test
  )

lazy val commonComponents = (project in engine("common/components"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-common-components"
  )
  .dependsOn(
    componentsApi % Provided
  )

lazy val commonComponentsTests = (project in engine("common/components-tests"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-common-components-tests"
  )
  .dependsOn(
    commonComponents,
    liteComponentsTestkit  % Test,
    flinkComponentsTestkit % Test
  )

lazy val flinkBaseComponents = (project in flink("components/base"))
  .settings(commonSettings)
  .settings(assemblyNoScala("flinkBase.jar"): _*)
  .settings(publishAssemblySettings: _*)
  .settings(
    name := "nussknacker-flink-base-components",
    libraryDependencies ++= Seq(
      "org.apache.flink" % "flink-streaming-java" % flinkV % Provided
    )
  )
  .dependsOn(
    commonComponents,
    flinkComponentsUtils % Provided,
    componentsUtils      % Provided
  )

lazy val flinkBaseUnboundedComponents = (project in flink("components/base-unbounded"))
  .settings(commonSettings)
  .settings(assemblyNoScala("flinkBaseUnbounded.jar"): _*)
  .settings(publishAssemblySettings: _*)
  .settings(
    name := "nussknacker-flink-base-unbounded-components",
    libraryDependencies ++= Seq(
      "org.apache.flink"          % "flink-streaming-java" % flinkV % Provided,
      "com.clearspring.analytics" % "stream"               % "2.9.8"
      // It is used only in QDigest which we don't use, while it's >20MB in size...
        exclude ("it.unimi.dsi", "fastutil")
    )
  )
  .dependsOn(
    commonComponents,
    flinkComponentsUtils % Provided,
    componentsUtils      % Provided,
    mathUtils            % Provided
  )

lazy val flinkBaseComponentsTests = (project in flink("components/base-tests"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-flink-base-components-tests",
    libraryDependencies ++= Seq(
      "org.apache.flink" % "flink-connector-files" % flinkV % Test,
      "org.apache.flink" % "flink-csv"             % flinkV % Test,
      "org.apache.flink" % "flink-json"            % flinkV % Test
    )
  )
  .dependsOn(
    flinkComponentsTestkit  % Test,
    flinkTableApiComponents % Test
  )

lazy val flinkKafkaComponents = (project in flink("components/kafka"))
  .settings(commonSettings)
  .settings(assemblyNoScala("flinkKafka.jar"): _*)
  .settings(publishAssemblySettings: _*)
  .settings(
    name := "nussknacker-flink-kafka-components",
  )
  .dependsOn(
    flinkSchemedKafkaComponentsUtils,
    flinkComponentsApi % Provided,
    commonUtils        % Provided,
    componentsUtils    % Provided
  )

// TODO: check if any flink-table / connector / format dependencies' scope can be limited
lazy val flinkTableApiComponents = (project in flink("components/table"))
  .settings(commonSettings)
  .settings(assemblyNoScala("flinkTable.jar"): _*)
  .settings(publishAssemblySettings: _*)
  .settings(
    name := "nussknacker-flink-table-components",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" % "flink-table-api-java"        % flinkV,
        "org.apache.flink" % "flink-table-api-java-bridge" % flinkV,
        "org.apache.flink" % "flink-table-planner-loader"  % flinkV,
        "org.apache.flink" % "flink-table-runtime"         % flinkV,
        "org.apache.flink" % "flink-clients"               % flinkV,
        "org.apache.flink" % "flink-connector-files"       % flinkV, // needed for testing data generation
        "org.apache.flink" % "flink-json"                  % flinkV, // needed for testing data generation
      )
    }
  )
  .dependsOn(
    flinkComponentsApi   % Provided,
    componentsApi        % Provided,
    commonUtils          % Provided,
    componentsUtils      % Provided,
    flinkComponentsUtils % Provided,
    jsonUtils            % Provided,
    testUtils            % Test,
  )

lazy val copyClientDist = taskKey[Unit]("copy designer client")

lazy val additionalBundledArtifacts = taskKey[List[(File, String)]]("additional artifacts to include in the bundle")

lazy val prepareItLibs = taskKey[Unit]("Prepare jar libraries needed for integration tests")

lazy val restmodel = (project in file("designer/restmodel"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-restmodel",
    libraryDependencies ++= {
      Seq(
        "com.softwaremill.sttp.tapir" %% "tapir-core"       % tapirV,
        "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirV
      )
    }
  )
  .dependsOn(
    extensionsApi,
    security,
    commonApi % "test->test",
    testUtils % Test,
  )

lazy val listenerApi = (project in file("designer/listener-api"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-listener-api",
  )
  .dependsOn(extensionsApi)

lazy val deploymentManagerApi = (project in file("designer/deployment-manager-api"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-deployment-manager-api",
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka"             %% "akka-actor"   % akkaV,
        "com.softwaremill.sttp.client3" %% "core"         % sttpV,
        "com.github.ben-manes.caffeine"  % "caffeine"     % caffeineCacheV,
        "org.scalatestplus"             %% "mockito-5-10" % scalaTestPlusV % Test
      )
    }
  )
  .dependsOn(extensionsApi, testUtils % Test)

lazy val designer = (project in file("designer/server"))
  .configs(SlowTests)
  .enablePlugins(GenerateDesignerOpenApiPlugin)
  .settings(slowTestsSettings)
  .settings(commonSettings)
  .settings(
    assemblySettings(
      "nussknacker-designer-assembly.jar",
      includeScala = includeFlinkAndScala,
      filterProvidedDeps = false
    ): _*
  )
  .settings(publishAssemblySettings: _*)
  .settings(
    name                             := "nussknacker-designer",
    copyClientDist                   := {
      val feDistDirectory                  = file("designer/client/dist")
      val feDistFiles: Seq[File]           = (feDistDirectory ** "*").get()
      IO.copy(
        feDistFiles pair Path.rebase(feDistDirectory, (compile / crossTarget).value / "classes" / "web" / "static"),
        CopyOptions.apply(overwrite = true, preserveLastModified = true, preserveExecutable = false)
      )
      val feSubmodulesDistDirectory        = file("designer/submodules/dist")
      val feSubmodulesDistFiles: Seq[File] = (feSubmodulesDistDirectory ** "*").get()
      IO.copy(
        feSubmodulesDistFiles pair Path
          .rebase(feSubmodulesDistDirectory, (compile / crossTarget).value / "classes" / "web" / "submodules"),
        CopyOptions.apply(overwrite = true, preserveLastModified = true, preserveExecutable = false)
      )
    },
    ThisBuild / parallelExecution    := false,
    SlowTests / test                 := (SlowTests / test)
      .dependsOn(
        flinkDevModel / Compile / assembly,
        flinkExecutor / Compile / assembly
      )
      .value,
    Test / test                      := (Test / test)
      .dependsOn(
        defaultModel / Compile / assembly,
        flinkTableApiComponents / Compile / assembly,
        flinkDevModel / Compile / assembly,
        flinkExecutor / Compile / assembly,
        flinkExecutor / prepareItLibs
      )
      .value,
    /*
      We depend on copyClientDist in packageBin and assembly to be make sure fe files will be included in jar and fajar
      We abuse sbt a little bit, but we don't want to put webpack in generate resources phase, as it's long and it would
      make compilation v. long. This is not too nice, but so far only alternative is to put designer dists copyClientDist outside sbt and
      use bash to control when it's done - and this can lead to bugs and edge cases (release, dist/docker, dist/tgz, assembly...)
     */
    Compile / packageBin             := (Compile / packageBin).dependsOn(copyClientDist).value,
    ThisScope / assembly             := (ThisScope / assembly).dependsOn(copyClientDist).value,
    assembly / assemblyMergeStrategy := designerMergeStrategy,
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka"             %% "akka-http"            % akkaHttpV,
        "com.typesafe.akka"             %% "akka-slf4j"           % akkaV,
        "com.typesafe.akka"             %% "akka-stream"          % akkaV,
        "com.typesafe.akka"             %% "akka-http-testkit"    % akkaHttpV % Test,
        "com.typesafe.akka"             %% "akka-testkit"         % akkaV     % Test,
        "de.heikoseeberger"             %% "akka-http-circe"      % akkaHttpCirceV,
        "com.softwaremill.sttp.client3" %% "akka-http-backend"    % sttpV,
        "ch.qos.logback"                 % "logback-core"         % logbackV,
        "ch.qos.logback"                 % "logback-classic"      % logbackV,
        "ch.qos.logback.contrib"         % "logback-json-classic" % logbackJsonV,
        "ch.qos.logback.contrib"         % "logback-jackson"      % logbackJsonV,
        "com.fasterxml.jackson.core"     % "jackson-databind"     % jacksonV,
        "org.slf4j"                      % "log4j-over-slf4j"     % slf4jV,
        "com.carrotsearch"               % "java-sizeof"          % "0.0.5",
        "org.typelevel"                 %% "case-insensitive"     % "1.4.0",

        // It's needed by flinkDeploymentManager which has disabled includingScala
        "org.scala-lang"                 % "scala-compiler"                  % scalaVersion.value,
        "org.scala-lang"                 % "scala-reflect"                   % scalaVersion.value,
        "com.typesafe.slick"            %% "slick"                           % slickV,
        "com.typesafe.slick"            %% "slick-hikaricp"                  % slickV,
        "com.zaxxer"                     % "HikariCP"                        % hikariCpV,
        "org.hsqldb"                     % "hsqldb"                          % hsqldbV,
        "org.postgresql"                 % "postgresql"                      % postgresV,
        "org.flywaydb"                   % "flyway-core"                     % flywayV,
        "org.apache.xmlgraphics"         % "fop"                             % "2.9" exclude ("commons-logging", "commons-logging"),
        "com.beachape"                  %% "enumeratum-circe"                % enumeratumV,
        "tf.tofu"                       %% "derevo-circe"                    % "0.13.0",
        "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml"              % openapiCirceYamlV,
        "com.softwaremill.sttp.tapir"   %% "tapir-akka-http-server"          % tapirV,
        "com.softwaremill.sttp.tapir"   %% "tapir-core"                      % tapirV,
        "com.softwaremill.sttp.tapir"   %% "tapir-derevo"                    % tapirV,
        "com.softwaremill.sttp.tapir"   %% "tapir-enumeratum"                % tapirV,
        "com.softwaremill.sttp.tapir"   %% "tapir-json-circe"                % tapirV,
        "com.softwaremill.sttp.tapir"   %% "tapir-swagger-ui-bundle"         % tapirV,
        "io.circe"                      %% "circe-generic-extras"            % circeGenericExtrasV,
        "org.reflections"                % "reflections"                     % "0.10.2"             % Test,
        "com.github.pathikrit"          %% "better-files"                    % betterFilesV,
        "com.dimafeng"                  %% "testcontainers-scala-scalatest"  % testContainersScalaV % Test,
        "com.dimafeng"                  %% "testcontainers-scala-postgresql" % testContainersScalaV % Test,
        "org.scalatestplus"             %% "mockito-5-10"                    % scalaTestPlusV       % Test,
        "io.dropwizard.metrics5"         % "metrics-core"                    % dropWizardV,
        "io.dropwizard.metrics5"         % "metrics-jmx"                     % dropWizardV,
        "fr.davit"                      %% "akka-http-metrics-dropwizard-v5" % "1.7.1",
        "org.scalacheck"                %% "scalacheck"                      % scalaCheckV          % Test,
        "com.github.erosb"               % "everit-json-schema"              % everitSchemaV exclude ("commons-logging", "commons-logging"),
        "org.apache.flink"               % "flink-metrics-dropwizard"        % flinkV               % Test,
        "org.wiremock"                   % "wiremock"                        % wireMockV            % Test,
        "io.circe"                      %% "circe-yaml"                      % circeYamlV           % Test,
        "com.github.scopt"              %% "scopt"                           % "4.1.0"              % Test,
        "org.questdb"                    % "questdb"                         % "7.4.2",
      ) ++ forScalaVersion(scalaVersion.value) {
        case (2, 13) =>
          Seq(
            "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4",
            "org.scala-lang.modules" %% "scala-xml"                  % "2.3.0"
          )
        case _       => Seq()
      }
    }
  )
  .dependsOn(
    scenarioCompiler                  % "compile;test->test;test->compile",
    processReports,
    security,
    deploymentManagerApi,
    restmodel,
    listenerApi,
    defaultHelpers                    % Test,
    testUtils                         % Test,
    flinkTestUtils                    % Test,
    componentsApi                     % "test->test",
    // All DeploymentManager dependencies are added because they are needed to run NussknackerApp* with
    // dev-application.conf. Currently, we doesn't have a separate classpath for DMs like we have for components.
    // schemedKafkaComponentsUtils is added because loading the provided liteEmbeddedDeploymentManager causes
    // that are also load added their test dependencies on the classpath by the Idea. It causes that
    // UniversalKafkaSourceFactory is loaded from app classloader and GenericRecord which is defined in typesToExtract
    // is missing from this classloader
    flinkDeploymentManager            % Provided,
    liteEmbeddedDeploymentManager     % Provided,
    liteK8sDeploymentManager          % Provided,
    developmentTestsDeploymentManager % Provided,
    flinkPeriodicDeploymentManager    % Provided,
    schemedKafkaComponentsUtils       % Provided,
  )

lazy val e2eTests = (project in file("e2e-tests"))
  .settings(commonSettings)
  .configs(SlowTests)
  .settings(slowTestsSettings)
  .settings {
    // TODO: it'd be better to use scalaVersion here, but for some reason it's hard to disable existing task dynamically
    forScalaVersion(defaultScalaV) {
      case (2, 12) => doTest
      case (2, 13) => doNotTest
    }
  }
  .settings(
    libraryDependencies ++= {
      Seq(
        "com.github.pathikrit"       %% "better-files"                   % betterFilesV         % Test,
        "ch.qos.logback"              % "logback-classic"                % logbackV             % Test,
        "com.typesafe.scala-logging" %% "scala-logging"                  % scalaLoggingV        % Test,
        "org.scalatest"              %% "scalatest"                      % scalaTestV           % Test,
        "com.dimafeng"               %% "testcontainers-scala-scalatest" % testContainersScalaV % Test,
        "com.lihaoyi"                %% "ujson"                          % ujsonV               % Test,
      ) ++
        restAssuredDependency(scalaVersion.value)
    }
  )
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings)
  .dependsOn(testUtils % Test, scenarioApi % Test, designer % Test)

lazy val doTest = Seq(
  Test / testOptions += Tests.Setup { () =>
    streams.value.log.info("Building Nu Designer docker image from the sources for a sake of E2E tests")
    (distribution / Docker / publishLocal).value.a
  }
)

lazy val doNotTest = Seq(
  Test / test := {
    streams.value.log.info(
      "E2E tests are skipped for Scala 2.13 because Nu installation example is currently based on Scala 2.12"
    )
  }
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
    name           := "nussknacker-bom",
    // we have to transform result pom to have pom packaging and move dependencies to dependencyManagement section
    pomPostProcess := { node: scala.xml.Node =>
      val rule: RewriteRule = new RewriteRule {
        override def transform(n: scala.xml.Node): scala.xml.NodeSeq = n match {
          case e: Elem if e != null && e.label == "packaging"    =>
            <packaging>pom</packaging>
          case e: Elem if e != null && e.label == "dependencies" =>
            <dependencyManagement>
              {e}
            </dependencyManagement>
          case _                                                 => n
        }
      }
      new RuleTransformer(rule).transform(node).head
    },
    /*
      TODO: do we want to include other dependencies (especially with 'provided' scope)?
      Maybe we need other BOM for ComponentProvider dependencies, which have more 'provided' dependencies
     */
    libraryDependencies ++= (dependencyOverrides.value ++ Seq(
      "org.apache.flink" % "flink-streaming-java"       % flinkV % Provided,
      "org.apache.flink" % "flink-runtime"              % flinkV % Provided,
      "org.apache.flink" % "flink-statebackend-rocksdb" % flinkV % Provided
    ))
  )
  .dependsOn(modules.map(k => k: ClasspathDep[ProjectReference]): _*)

lazy val modules = List[ProjectReference](
  requestResponseRuntime,
  liteEngineRuntimeApp,
  flinkDeploymentManager,
  flinkPeriodicDeploymentManager,
  flinkDevModel,
  flinkDevModelJava,
  flinkTableApiComponents,
  defaultModel,
  openapiComponents,
  scenarioCompiler,
  benchmarks,
  kafkaUtils,
  kafkaComponentsUtils,
  kafkaTestUtils,
  componentsUtils,
  componentsTestkit,
  defaultHelpers,
  commonUtils,
  utilsInternal,
  testUtils,
  flinkExecutor,
  flinkSchemedKafkaComponentsUtils,
  flinkKafkaComponentsUtils,
  flinkComponentsUtils,
  flinkTests,
  flinkTestUtils,
  flinkComponentsApi,
  flinkExtensionsApi,
  flinkScalaUtils,
  flinkMetricsDeferredReporter,
  requestResponseComponentsUtils,
  requestResponseComponentsApi,
  componentsApi,
  extensionsApi,
  security,
  processReports,
  httpUtils,
  restmodel,
  listenerApi,
  deploymentManagerApi,
  designer,
  sqlComponents,
  schemedKafkaComponentsUtils,
  commonComponents,
  commonComponentsTests,
  flinkBaseComponents,
  flinkBaseComponentsTests,
  flinkBaseUnboundedComponents,
  flinkKafkaComponents,
  liteComponentsApi,
  liteEngineKafkaComponentsApi,
  liteEngineRuntime,
  liteBaseComponents,
  liteKafkaComponents,
  liteKafkaComponentsTests,
  liteEngineKafkaRuntime,
  liteEngineKafkaIntegrationTest,
  liteDeploymentManager,
  liteEmbeddedDeploymentManager,
  liteK8sDeploymentManager,
  liteRequestResponseComponents,
  liteRequestResponseComponentsTests,
  scenarioApi,
  commonApi,
  jsonUtils,
  liteComponentsTestkit,
  flinkComponentsTestkit,
  mathUtils,
  developmentTestsDeploymentManager
)

lazy val modulesToAggregate: List[ProjectReference] = bom :: modules

lazy val root = (project in file("."))
  .enablePlugins(FormatStagedScalaFilesPlugin)
  .aggregate(modulesToAggregate: _*)
  .aggregate(e2eTests)
  .settings(commonSettings)
  .settings(
    name              := "nussknacker",
    // crossScalaVersions must be set to Nil on the aggregating project
    releaseCrossBuild := true,
    publish / skip    := true,
    releaseProcess    := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      // dist can't be aggregates by root because it using root tasks so we need to add cleaning of it explicitly
      // TODO: replace root tasks by some local tasks
      releaseStepCommand("dist/clean"),
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
      releaseStepCommand("buildClient"),
      releaseStepCommandAndRemaining("+publishSigned"),
      releaseStepCommand("dist/Universal/packageZipTarball"),
      releaseStepCommandAndRemaining("+dist/Docker/publish"),
      releaseStepCommandAndRemaining("+liteEngineRuntimeApp/Docker/publish"),
      releaseStepCommand("sonatypeBundleRelease"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )

lazy val prepareDev = taskKey[Unit]("Prepare components and model for running from IDE")

prepareDev := {
  (flinkExecutor / prepareItLibs).value
  val workTarget = (designer / baseDirectory).value / "work"
  val artifacts  =
    (distribution / componentArtifacts).value ++ (distribution / devArtifacts).value ++ developmentTestsDeployManagerArtifacts.value ++
      Def
        .taskDyn(if (addManagerArtifacts) distribution / managerArtifacts else Def.task[List[(File, String)]](Nil))
        .value ++
      (flinkExecutor / additionalBundledArtifacts).value
  IO.copy(artifacts.map { case (source, target) => (source, workTarget / target) })
  (designer / copyClientDist).value
}

lazy val buildClient = taskKey[Unit]("Build client")

buildClient := {
  val s: TaskStreams = streams.value
  val buildResult    = ("./designer/buildClient.sh" !)
  if (buildResult == 0) {
    s.log.success("Frontend build success")
  } else {
    throw new IllegalStateException("Frontend build failed!")
  }
}

def createClasspathBasedMapping(
    classpath: Classpath,
    organizationName: String,
    packageName: String,
    targetFilename: String
): Option[(File, String)] = {
  classpath.toSet
    .find(attr =>
      attr
        .get(sbt.Keys.moduleID.key)
        .exists(moduleID =>
          moduleID.organization.equalsIgnoreCase(organizationName) && moduleID.name.equalsIgnoreCase(packageName)
        )
    )
    .map { attribute =>
      val file = attribute.data
      file -> targetFilename
    }
}
