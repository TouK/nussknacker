import com.typesafe.sbt.packager.SettingsHelper
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerUsername
import sbt.Keys._
import sbt.{Def, _}
import sbtassembly.AssemblyPlugin.autoImport.assembly
import sbtassembly.MergeStrategy
import ReleaseTransformations._
import pl.project13.scala.sbt.JmhPlugin
import pl.project13.scala.sbt.JmhPlugin._

import scala.util.Try
import scala.xml.Elem
import scala.xml.transform.{RewriteRule, RuleTransformer}

val scala211 = "2.11.12"
// Warning: Flink doesn't work correctly with 2.12.11
// Warning: 2.12.13 + crossVersion break sbt-scoverage: https://github.com/scoverage/sbt-scoverage/issues/319
val scala212 = "2.12.10"
lazy val supportedScalaVersions = List(scala212, scala211)

// Silencer must be compatible with exact scala version - see compatibility matrix: https://search.maven.org/search?q=silencer-plugin
// Silencer 1.7.x require Scala 2.12.11 (see warning above)
// Silencer (and all '@silent' annotations) can be removed after we drop support for Scala 2.11
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

val standaloneManagementPort = propOrEnv("standaloneManagementPort", "8070").toInt
val standaloneProcessesPort = propOrEnv("standaloneProcessesPort", "8080").toInt
val standaloneDockerPackageName = propOrEnv("standaloneDockerPackageName", "nussknacker-standalone-app")

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
  case PathList(ps@_*) if ps.last == "NumberUtils.class" => MergeStrategy.first //TODO: shade Spring EL?
  case PathList("org", "apache", "commons", "logging", _ @ _*) => MergeStrategy.first //TODO: shade Spring EL?
  case PathList(ps@_*) if ps.last == "io.netty.versions.properties" => MergeStrategy.first //Netty has buildTime here, which is different for different modules :/
  case PathList("com", "sun", "el", _ @ _*) => MergeStrategy.first //Some legacy batik stuff
  case PathList("org", "w3c", "dom", "events", _ @ _*) => MergeStrategy.first //Some legacy batik stuff
  case x => MergeStrategy.defaultMergeStrategy(x)
}

def standaloneMergeStrategy: String => MergeStrategy = {
  case PathList(ps@_*) if ps.last == "NumberUtils.class" => MergeStrategy.first //TODO: shade Spring EL?
  case PathList("org", "apache", "commons", "logging", _ @ _*) => MergeStrategy.first //TODO: shade Spring EL?
  case PathList(ps@_*) if ps.last == "io.netty.versions.properties" => MergeStrategy.first //Netty has buildTime here, which is different for different modules :/
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

def forScalaVersion[T](version: String, default: T, specific: ((Int, Int), T)*): T = {
  CrossVersion.partialVersion(version).flatMap { case (k, v) =>
    specific.toMap.get(k.toInt, v.toInt)
  }.getOrElse(default)
}

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
        "-target:jvm-1.8"
        // switch to release option after removing scala 2.11 support (not available on scala 2.11 compiler)
//        "-release",
//        "8"
      ),
      javacOptions := Seq(
        "-Xlint:deprecation",
        "-Xlint:unchecked",
        // Using --release flag (available only on jdk >= 9) instead of -source -target to avoid usage of api from newer java version
        "--release",
        "8",
        //we use it e.g. to provide consistent behaviour wrt extracting parameter names from scala and java
        "-parameters"
      ),
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
        "org.apache.commons" % "commons-lang3" % commonsLangV,

        //we force circe version here, because sttp has 0.12.1 for scala 2.12, we don't want it ATM
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
      )
    )

val akkaV = "2.5.21" //same version as in Flink
val flinkV = "1.11.2"
val avroV = "1.9.2" // for java time logical types conversions purpose
//we should use max(version used by confluent, version used by flink), https://docs.confluent.io/platform/current/installation/versions-interoperability.html - confluent version reference
//however, we stick to 2.4.1, as it's last version supported by scala 2.11 (we use kafka server in tests...)
val kafkaV = "2.4.1"
val kafkaServerV = "2.4.1"
val springV = "5.1.19.RELEASE"
val scalaTestV = "3.0.8"
val scalaCheckV = "1.14.0"
val logbackV = "1.1.3"
val argonautV = "6.2.1"
val circeV = "0.11.2"
val circeJava8V = "0.11.1"
val jwtCirceV = "4.0.0"
val jacksonV = "2.9.2"
val catsV = "1.5.0"
val scalaParsersV = "1.0.4"
val slf4jV = "1.7.30"
val scalaLoggingV = "3.9.2"
val scalaCompatV = "0.9.1"
val ficusV = "1.4.7"
val configV = "1.4.1"
val commonsLangV = "3.3.2"
val commonsTextV = "1.8"
val commonsIOV = "2.4"
//we want to use 5.x for standalone metrics to have tags, however dropwizard development kind of freezed. Maybe we should consider micrometer?
//In Flink metrics we use bundled dropwizard metrics v. 3.x
val dropWizardV = "5.0.0-rc3"
val scalaCollectionsCompatV = "2.3.2"
val testcontainersScalaV = "0.39.3"
val nettyV = "4.1.48.Final"

val akkaHttpV = "10.1.8"
val akkaHttpCirceV = "1.27.0"
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

lazy val commonDockerSettings = {
  Seq(
    //we use openjdk:11-jdk because openjdk:11-jdk-slim lacks /usr/local/openjdk-11/lib/libfontmanager.so file necessary during pdf export
    dockerBaseImage := "openjdk:11-jdk",
    dockerUsername := dockerUserName,
    dockerUpdateLatest := dockerUpLatestFromProp.getOrElse(!isSnapshot.value),
    dockerAliases := {
      //https://docs.docker.com/engine/reference/commandline/tag/#extended-description
      def sanitize(str: String) = str.replaceAll("[^a-zA-Z0-9.\\-_]", "_")
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
  val workingDir = "/opt/nussknacker"

  commonDockerSettings ++ Seq(
    dockerEntrypoint := Seq(s"$workingDir/bin/nussknacker-entrypoint.sh", dockerPort.toString),
    dockerExposedPorts := Seq(dockerPort),
    dockerEnvVars := Map(
      "AUTHENTICATION_METHOD" -> "BasicAuth",
      "AUTHENTICATION_USERS_FILE" -> "./conf/users.conf",
      "AUTHENTICATION_HEADERS_ACCEPT" -> "application/json",
      "OAUTH2_RESPONSE_TYPE" -> "code",
      "OAUTH2_GRANT_TYPE" -> "authorization_code",
      "OAUTH2_SCOPE" -> "read:user",
    ),
    packageName := dockerPackageName,
    dockerLabels := Map(
      "version" -> version.value,
      "scala" -> scalaVersion.value,
      "flink" -> flinkV
    ),
    dockerExposedVolumes := Seq(s"$workingDir/storage", s"$workingDir/data"),
    defaultLinuxInstallLocation in Docker := workingDir
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
  assemblyMergeStrategy in assembly := modelMergeStrategy,
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
        (assembly in Compile) in flinkDeploymentManager,
        (assembly in Compile) in engineStandalone,
        (assembly in Compile) in openapi,
        (assembly in Compile) in sql,
      ).value,
      mappings in Universal ++= Seq(
        (crossTarget in generic).value / "genericModel.jar" -> "model/genericModel.jar",
        (crossTarget in flinkDeploymentManager).value / "nussknacker-flink-manager.jar" -> "managers/nussknacker-flink-manager.jar",
        (crossTarget in engineStandalone).value / "nussknacker-standalone-manager.jar" -> "managers/nussknacker-standalone-manager.jar",
        (crossTarget in openapi).value / "openapi.jar" -> "components/openapi.jar",
        (crossTarget in sql).value / "sql.jar" -> "components/sql.jar"
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
    .settings(distDockerSettings)
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

def component(name: String) = file(s"engine/components/$name")

lazy val engineStandalone = (project in engine("standalone/engine")).
  configs(IntegrationTest).
  settings(commonSettings).
  settings(assemblySettings("nussknacker-standalone-manager.jar", includeScala = false): _*).
  settings(Defaults.itSettings).
  settings(
    name := "nussknacker-standalone-engine",
    libraryDependencies ++= {
      Seq(
        "io.dropwizard.metrics5" % "metrics-core" % dropWizardV)
    },
    Keys.test in IntegrationTest := (Keys.test in IntegrationTest).dependsOn(
      (assembly in Compile) in standaloneSample
    ).value,
  ).
  dependsOn(interpreter % "provided", standaloneApi, httpUtils % "provided", testUtil % "it,test", standaloneUtil % "test")

lazy val standaloneDockerSettings = {
  val workingDir = "/opt/nussknacker"

  commonDockerSettings ++ Seq(
    dockerEntrypoint := Seq(s"$workingDir/bin/nussknacker-standalone-entrypoint.sh"),
    dockerExposedPorts := Seq(
      standaloneProcessesPort,
      standaloneManagementPort
    ),
    dockerExposedVolumes := Seq(s"$workingDir/storage"),
    defaultLinuxInstallLocation in Docker := workingDir,
    packageName := standaloneDockerPackageName,
    dockerLabels := Map(
      "version" -> version.value,
      "scala" -> scalaVersion.value,
    )
  )
}

lazy val standaloneApp = (project in engine("standalone/app")).
  settings(commonSettings).
  settings(publishAssemblySettings: _*).
  enablePlugins(SbtNativePackager, JavaServerAppPackaging).
  settings(
    name := "nussknacker-standalone-app",
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = true, level = Level.Info),
    assemblyMergeStrategy in assembly := standaloneMergeStrategy,
    libraryDependencies ++= {
      Seq(
        "de.heikoseeberger" %% "akka-http-circe" % akkaHttpCirceV,
        "com.typesafe.akka" %% "akka-http" % akkaHttpV,
        "com.typesafe.akka" %% "akka-stream" % akkaV,
        "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test",
        "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
        "com.typesafe.akka" %% "akka-slf4j" % akkaV,
        "io.dropwizard.metrics5" % "metrics-influxdb" % dropWizardV,
        "ch.qos.logback" % "logback-classic" % logbackV
      )
    }
  ).
  settings(standaloneDockerSettings).
  dependsOn(engineStandalone, interpreter, httpUtils, testUtil % "test", standaloneUtil % "test")


lazy val flinkDeploymentManager = (project in engine("flink/management")).
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
        "org.apache.flink" %% "flink-statebackend-rocksdb" % flinkV % flinkScope,
        //TODO: move to testcontainers, e.g. https://ci.apache.org/projects/flink/flink-docs-master/api/java/org/apache/flink/tests/util/flink/FlinkContainer.html
        "com.whisk" %% "docker-testkit-scalatest" % "0.9.0" % "it,test",
        "com.whisk" %% "docker-testkit-impl-spotify" % "0.9.0" % "it,test"
      )
    }
  ).dependsOn(interpreter % "provided",
    api % "provided",
    queryableState,
    httpUtils % "provided",
    kafkaTestUtil % "it,test")

lazy val flinkPeriodicDeploymentManager = (project in engine("flink/management/periodic")).
  settings(commonSettings).
  settings(assemblySettings("nussknacker-flink-periodic-manager.jar", includeScala = false): _*).
  settings(
    name := "nussknacker-flink-periodic-manager",
    libraryDependencies ++= {
      Seq(
        "org.typelevel" %% "cats-core" % catsV % "provided",
        "com.typesafe.slick" %% "slick" % slickV % "provided",
        "com.typesafe.slick" %% "slick-hikaricp" % slickV % "provided, test",
        "org.flywaydb" % "flyway-core" % flywayV % "provided",
        "com.cronutils" % "cron-utils" % cronParserV
      )
    }
  ).dependsOn(flinkDeploymentManager,
    interpreter % "provided",
    api % "provided",
    httpUtils % "provided",
    testUtil % "test")

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
  dependsOn(kafkaFlinkUtil, flinkModelUtil, avroFlinkUtil, interpreter,
    process % "runtime,test", flinkTestUtil % "test", kafkaTestUtil % "test")

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
  .dependsOn(process % "runtime,test", avroFlinkUtil, flinkModelUtil, flinkTestUtil % "test", kafkaTestUtil % "test",
    //for local development
    ui % "test")

lazy val process = (project in engine("flink/process")).
  settings(commonSettings).
  settings(
    name := "nussknacker-process",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" %% "flink-runtime" % flinkV % "provided",
        "org.apache.flink" %% "flink-statebackend-rocksdb" % flinkV % "provided"
      )
    }
  ).dependsOn(flinkUtil, interpreter, flinkTestUtil % "test")

lazy val interpreter = (project in engine("interpreter")).
  settings(commonSettings).
  settings(
    name := "nussknacker-interpreter",
    //We hit https://github.com/scala/bug/issues/7046 in strange places during doc generation.
    //Shortly, we should stop building for 2.11, for now we just skip scaladoc for 2.11 here...
    sources in doc in Compile :=
           forScalaVersion(scalaVersion.value, (sources in Compile).value, (2, 11) -> Nil),
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
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV,
        "org.apache.flink" %% "flink-runtime" % flinkV
      )
    },
    // To avoid Intellij message that jmh generated classes are shared between main and test
    classDirectory in Jmh := (classDirectory in Test).value,
    dependencyClasspath in Jmh := (dependencyClasspath in Test).value,
    generateJmhSourcesAndResources in Jmh := (generateJmhSourcesAndResources in Jmh).dependsOn(compile in Test).value,
  ).dependsOn(interpreter, avroFlinkUtil, flinkModelUtil, process, testUtil % "test")


lazy val kafka = (project in engine("kafka")).
  configs(IntegrationTest).
  settings(commonSettings).
  settings(Defaults.itSettings).
  settings(
    name := "nussknacker-kafka",
    libraryDependencies ++= {
      Seq(
        "javax.validation" % "validation-api" % javaxValidationApiV,
        "org.apache.kafka" % "kafka-clients" % kafkaV,
        "com.dimafeng" %% "testcontainers-scala-scalatest" % testcontainersScalaV % "it",
        "com.dimafeng" %% "testcontainers-scala-kafka" % testcontainersScalaV % "it",
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
        "tech.allegro.schema.json2avro" % "converter" % "0.2.10",
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" % "flink-avro" % flinkV,
        "org.apache.flink" %% s"flink-connector-kafka" % flinkV % "test",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  )
  .dependsOn(kafkaFlinkUtil, interpreter, kafkaTestUtil % "test", flinkTestUtil % "test", process % "test")

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
  dependsOn(kafka, flinkUtil, process % "test", kafkaTestUtil % "test", flinkTestUtil % "test")

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
        "io.circe" %% "circe-java8" % circeJava8V,
        "org.apache.avro" % "avro" % avroV % Optional
      )
    }
  ).dependsOn(api, testUtil % "test")

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
  ).dependsOn(util, flinkApi, testUtil % "test")

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
        "org.apache.flink" %% "flink-test-utils" % flinkV  excludeAll (
          //we use logback in NK
          ExclusionRule("org.apache.logging.log4j", "log4j-slf4j-impl")
        ),
        "org.apache.flink" %% "flink-runtime" % flinkV % "compile" classifier "tests",
        "org.apache.flink" % "flink-metrics-dropwizard" % flinkV
      )
    }
  ).dependsOn(testUtil, queryableState, flinkUtil)

lazy val standaloneUtil = (project in engine("standalone/util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-standalone-util",
    libraryDependencies ++= {
      Seq(
        "io.dropwizard.metrics5" % "metrics-core" % dropWizardV,
        "io.dropwizard.metrics5" % "metrics-influxdb" % dropWizardV,
        "com.softwaremill.sttp.client" %% "core" % sttpV,
        "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % sttpV,
        //akka-http is only for StandaloneRequestResponseLogger
        "com.typesafe.akka" %% "akka-http" % akkaHttpV % "provided",
        "com.typesafe.akka" %% "akka-stream" % akkaV % "provided"
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
        "io.argonaut" %% "argonaut" % argonautV,
        "io.circe" %% "circe-parser" % circeV,
        "io.circe" %% "circe-generic" % circeV,
        "io.circe" %% "circe-generic-extras" % circeV,
        "io.circe" %% "circe-java8" % circeJava8V,
        "com.iheart" %% "ficus" % ficusV,
        "org.apache.commons" % "commons-lang3" % commonsLangV,
        "org.apache.commons" % "commons-text" % commonsTextV,
        "org.typelevel" %% "cats-core" % catsV,
        "org.typelevel" %% "cats-effect" % "1.1.0",
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "com.typesafe" % "config" % configV,
        "com.vdurmont" % "semver4j" % "3.1.0"
      )
    }
  ).dependsOn(testUtil % "test")

lazy val security = (project in engine("security")).
  configs(IntegrationTest).
  settings(commonSettings).
  settings(Defaults.itSettings).
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
        "com.pauldijou" %% "jwt-circe" % jwtCirceV,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "com.dimafeng" %% "testcontainers-scala-scalatest" % testcontainersScalaV % "it,test",
        "com.github.dasniko" % "testcontainers-keycloak" % "1.6.0" % "it,test"
      )
    }
  )
  .dependsOn(util, httpUtils, testUtil % "it,test")

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
  configs(IntegrationTest).
  settings(commonSettings).
  settings(Defaults.itSettings).
  settings(
    name := "nussknacker-process-reports",
    libraryDependencies ++= {
      Seq(
        "com.typesafe" % "config" % "1.3.0",
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "com.iheart" %% "ficus" % ficusV,
        "com.dimafeng" %% "testcontainers-scala-scalatest" % testcontainersScalaV % "it,test",
        "com.dimafeng" %% "testcontainers-scala-influxdb" % testcontainersScalaV % "it,test",
        "org.influxdb" % "influxdb-java" % "2.21" % "it,test"
      )
    }
  ).dependsOn(httpUtils, testUtil % "it,test")

lazy val httpUtils = (project in engine("httpUtils")).
  settings(commonSettings).
  settings(
    name := "nussknacker-http-utils",
    libraryDependencies ++= {
      Seq(
        "io.circe" %% "circe-core" % circeV,
        "io.circe" %% "circe-parser" % circeV,
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


val swaggerParserV = "2.0.20"
val swaggerIntegrationV = "2.1.3"

lazy val openapi = (project in component("openapi")).
    configs(IntegrationTest).
    settings(commonSettings).
    settings(Defaults.itSettings).
    settings(commonSettings).
    settings(assemblySampleSettings("openapi.jar"): _*).
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
        "com.softwaremill.sttp.client" %% "circe" % sttpV excludeAll ExclusionRule(organization = "io.circe"),
        "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % sttpV  excludeAll(
          ExclusionRule(organization = "com.sun.activation", name = "javax.activation"),
        ),
        "io.netty" % "netty-transport-native-epoll" % nettyV,
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % Provided,
        "org.scalatest" %% "scalatest" % scalaTestV %  "it,test"
      ),
    ).dependsOn(api % Provided, process % Provided, engineStandalone % Provided, standaloneUtil % Provided, httpUtils % Provided, flinkTestUtil % "it,test", kafkaTestUtil % "it,test")

lazy val sql = (project in component("sql")).
  configs(IntegrationTest).
  settings(commonSettings).
  settings(Defaults.itSettings).
  settings(commonSettings).
  settings(assemblySampleSettings("sql.jar"): _*).
  settings(publishAssemblySettings: _*).
  settings(
    name := "nussknacker-sql",
    libraryDependencies ++= Seq(
      "com.zaxxer" % "HikariCP" % "4.0.3",
      "org.apache.flink" %% "flink-streaming-scala" % flinkV % Provided,
      "org.scalatest" %% "scalatest" % scalaTestV % "it,test"
    ),
  ).dependsOn(api % Provided, process % Provided, engineStandalone % Provided, standaloneUtil % Provided, httpUtils % Provided, flinkTestUtil % "it,test", kafkaTestUtil % "it,test")

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
      "io.circe" %% "circe-java8" % circeJava8V
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
      (assembly in Compile) in flinkManagementSample
    ).value,
    Keys.test in Test := (Keys.test in Test).dependsOn(
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
    assemblyMergeStrategy in assembly := uiMergeStrategy,
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
        "com.whisk" %% "docker-testkit-scalatest" % "0.9.8" % "test",
        "com.whisk" %% "docker-testkit-impl-spotify" % "0.9.8" % "test",
      )
    }
  )
  .dependsOn(interpreter, processReports, security, listenerApi,
    testUtil % "test",
    //TODO: this is unfortunatelly needed to run without too much hassle in Intellij...
    //provided dependency of kafka is workaround for Idea, which is not able to handle test scope on module dependency
    //otherwise it is (wrongly) added to classpath when running UI from Idea
    flinkDeploymentManager % "provided" ,
    kafka % "provided",
    engineStandalone % "provided"
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
      "org.apache.flink" %% "flink-runtime" % flinkV % "provided",
      "org.apache.flink" %% "flink-statebackend-rocksdb" % flinkV % "provided"
    ))
  ).dependsOn(modules.map(k => k:ClasspathDep[ProjectReference]):_*)

lazy val modules = List[ProjectReference](
  engineStandalone, standaloneApp, flinkDeploymentManager, flinkPeriodicDeploymentManager, standaloneSample, flinkManagementSample, managementJavaSample, generic,
  openapi, process, interpreter, benchmarks, kafka, avroFlinkUtil, kafkaFlinkUtil, kafkaTestUtil, util, testUtil, flinkUtil, flinkModelUtil,
  flinkTestUtil, standaloneUtil, standaloneApi, api, security, flinkApi, processReports, httpUtils, queryableState,
  restmodel, listenerApi, ui, sql
)
lazy val modulesWithBom: List[ProjectReference] = bom :: modules

lazy val root = (project in file("."))
  .aggregate(modulesWithBom: _*)
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

addCommandAlias("assemblySamples", ";flinkManagementSample/assembly;standaloneSample/assembly;generic/assembly")
addCommandAlias("assemblyDeploymentManagers", ";flinkDeploymentManager/assembly;engineStandalone/assembly")
